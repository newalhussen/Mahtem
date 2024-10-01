package et.mahtem.service;

import et.mahtem.domain.BatchStatus;
import et.mahtem.domain.Credential;
import et.mahtem.domain.CredentialBatch;
import et.mahtem.domain.CredentialStatus;
import et.mahtem.domain.Organization;
import et.mahtem.repo.CredentialBatchRepository;
import et.mahtem.repo.CredentialRepository;
import et.mahtem.repo.OrganizationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Seals a batch in the background. Each credential is signed in its own transaction, so a failure
 * affects one row, progress is visible while it runs, and a restart can pick up where it stopped.
 */
@Component
public class BatchSealer {

    private static final Logger log = LoggerFactory.getLogger(BatchSealer.class);

    private final CredentialBatchRepository batches;
    private final CredentialRepository credentials;
    private final OrganizationRepository organizations;
    private final IssuanceService issuance;
    private final AuditService audit;
    private final TaskExecutor executor;

    public BatchSealer(CredentialBatchRepository batches, CredentialRepository credentials, OrganizationRepository organizations,
                       IssuanceService issuance, AuditService audit,
                       @org.springframework.beans.factory.annotation.Qualifier("sealingExecutor") TaskExecutor executor) {
        this.batches = batches;
        this.credentials = credentials;
        this.organizations = organizations;
        this.issuance = issuance;
        this.audit = audit;
        this.executor = executor;
    }

    @Configuration
    static class ExecutorConfig {
        @Bean("sealingExecutor")
        TaskExecutor sealingExecutor() {
            ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
            ex.setCorePoolSize(2);
            ex.setMaxPoolSize(2);
            ex.setQueueCapacity(50);
            ex.setThreadNamePrefix("batch-seal-");
            return ex;
        }
    }

    @Async("sealingExecutor")
    public void seal(UUID batchId, UUID sealedBy) {
        sealNow(batchId, sealedBy);
    }

    private void sealNow(UUID batchId, UUID sealedBy) {
        CredentialBatch batch = batches.findById(batchId).orElse(null);
        if (batch == null) return;
        Organization org = organizations.findById(batch.getOrganizationId()).orElseThrow();
        List<Credential> pending = credentials.findByBatchIdAndStatus(batchId, CredentialStatus.PENDING);
        int done = batch.getTotalRows() - pending.size();
        int failed = 0;
        for (Credential c : pending) {
            try {
                issuance.seal(org, c, sealedBy);
                done++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Could not seal {} in batch {}: {}", c.getPublicId(), batchId, e.getMessage());
            }
            if ((done + failed) % 10 == 0) saveProgress(batchId, done);
        }
        CredentialBatch fresh = batches.findById(batchId).orElseThrow();
        fresh.progress(done);
        fresh.finish(failed == 0 ? BatchStatus.SEALED : BatchStatus.FAILED, Instant.now());
        batches.save(fresh);
        audit.recordSystem(org.getId(), failed == 0 ? "Batch sealed" : "Batch finished with errors",
                fresh.getFilename() + " · " + done + " sealed" + (failed > 0 ? ", " + failed + " failed" : ""));
    }

    private void saveProgress(UUID batchId, int done) {
        batches.findById(batchId).ifPresent(b -> {
            b.progress(done);
            batches.save(b);
        });
    }

    /** After a restart, continue any batch that was mid-way through sealing. */
    @EventListener(ApplicationReadyEvent.class)
    void resume() {
        for (CredentialBatch b : batches.findAll()) {
            if (b.getStatus() != BatchStatus.SEALING) continue;
            log.info("Resuming sealing of batch {}", b.getId());
            UUID by = b.getApprovedBy() != null ? b.getApprovedBy() : b.getCreatedBy();
            executor.execute(() -> sealNow(b.getId(), by));
        }
    }
}
