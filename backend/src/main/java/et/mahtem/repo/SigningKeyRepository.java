package et.mahtem.repo;

import et.mahtem.domain.KeyStatus;
import et.mahtem.domain.SigningKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningKeyRepository extends JpaRepository<SigningKey, UUID> {
    Optional<SigningKey> findByOrganizationIdAndStatus(UUID organizationId, KeyStatus status);

    List<SigningKey> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
