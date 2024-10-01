package et.mahtem.service;

import et.mahtem.crypto.Hashing;
import et.mahtem.domain.AuditEvent;
import et.mahtem.repo.AuditEventRepository;
import et.mahtem.util.Actor;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tamper-evident log of staff actions. Every entry stores the hash of the one before it, so changing
 * or removing an old entry breaks the chain from that point on (see {@link #verifyChain}).
 */
@Service
public class AuditService {

    static final String GENESIS = "0".repeat(64);

    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    /** Joins the caller's transaction so an action and its audit entry commit or roll back together. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Actor actor, String action, String detail) {
        append(actor.organizationId(), actor.userId(), actor.name(), action, detail, actor.ip());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSystem(UUID organizationId, String action, String detail) {
        append(organizationId, null, "System", action, detail, null);
    }

    /** Appends an entry that happened earlier (data import). Entries must be added in time order. */
    @Transactional
    public void recordHistorical(UUID orgId, UUID actorId, String actorName, String action, String detail, Instant at) {
        append(orgId, actorId, actorName, action, detail, null, at);
    }

    private void append(UUID orgId, UUID actorId, String actorName, String action, String detail, String ip) {
        append(orgId, actorId, actorName, action, detail, ip, Instant.now());
    }

    private void append(UUID orgId, UUID actorId, String actorName, String action, String detail, String ip, Instant when) {
        events.lockOrganization(orgId); // serialise appends per organization
        AuditEvent last = events.findTopByOrganizationIdOrderBySeqDesc(orgId).orElse(null);
        long seq = last == null ? 1 : last.getSeq() + 1;
        String prev = last == null ? GENESIS : last.getHash();
        Instant at = when.truncatedTo(ChronoUnit.MILLIS);
        String hash = hash(prev, orgId, seq, actorName, action, detail, at);
        events.save(new AuditEvent(orgId, seq, actorId, actorName, action, truncate(detail, 500), ip, at, prev, hash));
    }

    public Page<AuditEvent> page(UUID orgId, int page, int size) {
        return events.findByOrganizationIdOrderBySeqDesc(orgId, PageRequest.of(page, size));
    }

    public record ChainStatus(boolean intact, long entries, Long brokenAtSeq) {}

    /** Recomputes every hash from the start of the organization's log. */
    @Transactional(readOnly = true)
    public ChainStatus verifyChain(UUID orgId) {
        List<AuditEvent> all = events.findByOrganizationIdOrderBySeqAsc(orgId);
        String prev = GENESIS;
        long expectedSeq = 1;
        for (AuditEvent e : all) {
            String expected = hash(prev, orgId, e.getSeq(), e.getActorName(), e.getAction(), e.getDetail(), e.getOccurredAt());
            if (e.getSeq() != expectedSeq || !e.getPrevHash().equals(prev) || !e.getHash().equals(expected)) {
                return new ChainStatus(false, all.size(), e.getSeq());
            }
            prev = e.getHash();
            expectedSeq++;
        }
        return new ChainStatus(true, all.size(), null);
    }

    private static String hash(String prev, UUID orgId, long seq, String actorName, String action, String detail, Instant at) {
        return Hashing.sha256Hex(String.join("\u001f", prev, orgId.toString(), Long.toString(seq), actorName, action,
                truncate(detail, 500), at.toString()));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
