package et.mahtem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An organization's Ed25519 signing key. Retired keys keep verifying everything they signed. */
@Entity
@Table(name = "signing_key")
public class SigningKey {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String algorithm = "Ed25519";

    @Column(nullable = false)
    private byte[] publicKey;

    @Column(name = "private_key_enc", nullable = false)
    private byte[] privateKeyEnc;

    @Column(nullable = false)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KeyStatus status;

    @Column(nullable = false)
    private LocalDate validFrom;

    @Column(nullable = false)
    private LocalDate validTo;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected SigningKey() {}

    public SigningKey(UUID id, UUID organizationId, String label, byte[] publicKey, byte[] privateKeyEnc,
                      String fingerprint, LocalDate validFrom, LocalDate validTo) {
        this.id = id;
        this.organizationId = organizationId;
        this.label = label;
        this.publicKey = publicKey;
        this.privateKeyEnc = privateKeyEnc;
        this.fingerprint = fingerprint;
        this.status = KeyStatus.ACTIVE;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getLabel() { return label; }
    public String getAlgorithm() { return algorithm; }
    public byte[] getPublicKey() { return publicKey; }
    public byte[] getPrivateKeyEnc() { return privateKeyEnc; }
    public String getFingerprint() { return fingerprint; }
    public KeyStatus getStatus() { return status; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public Instant getCreatedAt() { return createdAt; }

    public void retire(LocalDate today) {
        this.status = KeyStatus.RETIRED;
        if (validTo.isAfter(today)) this.validTo = today;
    }
}
