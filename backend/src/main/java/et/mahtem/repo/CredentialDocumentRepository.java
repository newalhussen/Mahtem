package et.mahtem.repo;

import et.mahtem.domain.CredentialDocument;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialDocumentRepository extends JpaRepository<CredentialDocument, UUID> {}
