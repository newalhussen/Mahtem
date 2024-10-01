package et.mahtem.repo;

import et.mahtem.domain.VerificationLog;
import et.mahtem.domain.VerificationResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationLogRepository extends JpaRepository<VerificationLog, UUID>, JpaSpecificationExecutor<VerificationLog> {

    long countByOrganizationIdAndCheckedAtAfter(UUID organizationId, Instant after);

    long countByOrganizationIdAndResultAndCheckedAtAfter(UUID organizationId, VerificationResult result, Instant after);

    long countByCredentialId(UUID credentialId);

    List<VerificationLog> findTop5ByOrderByCheckedAtDesc();

    List<VerificationLog> findByCredentialIdOrderByCheckedAtDesc(UUID credentialId, Pageable page);

    @Query(value = """
            select cast((checked_at at time zone 'Africa/Addis_Ababa') as date) as day,
                   count(*) filter (where result = 'VALID') as ok,
                   count(*) filter (where result <> 'VALID') as bad
            from verification_log
            where organization_id = :org and checked_at >= :since
            group by 1 order by 1
            """, nativeQuery = true)
    List<Object[]> dailyCounts(@Param("org") UUID organizationId, @Param("since") Instant since);

    @Query("select v.credentialId, count(v) from VerificationLog v where v.organizationId = :org and v.credentialId is not null group by v.credentialId")
    List<Object[]> countsByCredential(@Param("org") UUID organizationId);
}
