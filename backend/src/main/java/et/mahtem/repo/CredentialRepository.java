package et.mahtem.repo;

import et.mahtem.domain.Credential;
import et.mahtem.domain.CredentialStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CredentialRepository extends JpaRepository<Credential, UUID>, JpaSpecificationExecutor<Credential> {

    Optional<Credential> findByPublicId(String publicId);

    Optional<Credential> findByPublicIdAndOrganizationId(String publicId, UUID organizationId);

    boolean existsByPublicId(String publicId);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndStatus(UUID organizationId, CredentialStatus status);

    List<Credential> findTop5ByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<Credential> findByBatchIdAndStatus(UUID batchId, CredentialStatus status);

    @Query("select distinct c.title from Credential c where c.organizationId = :org order by c.title")
    List<String> findDistinctTitles(@Param("org") UUID organizationId);

    @Query("select c.type, count(c) from Credential c where c.organizationId = :org and c.status <> et.mahtem.domain.CredentialStatus.PENDING group by c.type")
    List<Object[]> countSealedByType(@Param("org") UUID organizationId);
}
