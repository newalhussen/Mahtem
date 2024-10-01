package et.mahtem.repo;

import et.mahtem.domain.AuditEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByOrganizationIdOrderBySeqDesc(UUID organizationId, Pageable pageable);

    List<AuditEvent> findByOrganizationIdOrderBySeqAsc(UUID organizationId);

    Optional<AuditEvent> findTopByOrganizationIdOrderBySeqDesc(UUID organizationId);

    /** Serialises appends per organization so the hash chain never forks. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o.id from Organization o where o.id = :org")
    Optional<UUID> lockOrganization(@Param("org") UUID organizationId);
}
