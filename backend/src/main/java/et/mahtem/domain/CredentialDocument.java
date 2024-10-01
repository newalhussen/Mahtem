package et.mahtem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** The issued certificate PDF, stored exactly as generated at sealing so its fingerprint stays valid. */
@Entity
@Table(name = "credential_document")
public class CredentialDocument {

    @Id
    private UUID credentialId;

    @Column(nullable = false)
    private byte[] pdf;

    protected CredentialDocument() {}

    public CredentialDocument(UUID credentialId, byte[] pdf) {
        this.credentialId = credentialId;
        this.pdf = pdf;
    }

    public UUID getCredentialId() { return credentialId; }
    public byte[] getPdf() { return pdf; }
}
