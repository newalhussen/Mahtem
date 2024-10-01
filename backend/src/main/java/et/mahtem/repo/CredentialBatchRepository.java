package et.mahtem.repo;

import et.mahtem.domain.CredentialBatch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialBatchRepository extends JpaRepository<CredentialBatch, UUID> {
    List<CredentialBatch> findTop20ByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<CredentialBatch> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
