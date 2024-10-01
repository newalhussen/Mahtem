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

/**
 * A credential. PENDING records are drafts or awaiting a second approver and are invisible to public
 * verification. Sealing signs the canonical payload; the payload, hash and signature never change after.
 */
@Entity
@Table(name = "credential")
public class Credential {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String publicId;

    @Column(nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CredentialType type;

    @Column(nullable = false)
    private String title;

    private String details;
    private String statement;

    @Column(nullable = false)
    private String recipientName;

    private String recipientNameAm;
    private String recipientEmail;
    private String recipientRef;

    @Column(nullable = false)
    private LocalDate conferredOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CredentialStatus status;

    private boolean approvalRequired;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private UUID batchId;

    private Instant issuedAt;
    private UUID sealedBy;
    private UUID signingKeyId;

    @Column(columnDefinition = "text")
    private String payload;

    private String recordHash;
    private byte[] signature;
    private String pdfSha256;

    private Instant revokedAt;
    private String revokeReason;
    private String revokeNote;
    private UUID revokedBy;

    protected Credential() {}

    public Credential(UUID organizationId, String publicId, CredentialType type, String title, String recipientName,
                      LocalDate conferredOn, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.publicId = publicId;
        this.type = type;
        this.title = title;
        this.recipientName = recipientName;
        this.conferredOn = conferredOn;
        this.createdBy = createdBy;
        this.status = CredentialStatus.PENDING;
    }

    public UUID getId() { return id; }
    public String getPublicId() { return publicId; }
    public UUID getOrganizationId() { return organizationId; }
    public CredentialType getType() { return type; }
    public String getTitle() { return title; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }
    public String getRecipientName() { return recipientName; }
    public String getRecipientNameAm() { return recipientNameAm; }
    public void setRecipientNameAm(String v) { this.recipientNameAm = v; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String v) { this.recipientEmail = v; }
    public String getRecipientRef() { return recipientRef; }
    public void setRecipientRef(String v) { this.recipientRef = v; }
    public LocalDate getConferredOn() { return conferredOn; }
    public CredentialStatus getStatus() { return status; }
    public boolean isApprovalRequired() { return approvalRequired; }
    public void setApprovalRequired(boolean v) { this.approvalRequired = v; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    /** Only for importing credentials issued in the past (migrations, demo data). */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }
    public Instant getIssuedAt() { return issuedAt; }
    public UUID getSealedBy() { return sealedBy; }
    public UUID getSigningKeyId() { return signingKeyId; }
    public String getPayload() { return payload; }
    public String getRecordHash() { return recordHash; }
    public byte[] getSignature() { return signature; }
    public String getPdfSha256() { return pdfSha256; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public String getRevokeNote() { return revokeNote; }
    public UUID getRevokedBy() { return revokedBy; }

    /** Draft fields may only change while the record is still pending. */
    public void editDraft(CredentialType type, String title, String recipientName, LocalDate conferredOn) {
        if (status != CredentialStatus.PENDING) throw new IllegalStateException("Sealed credentials cannot be edited.");
        this.type = type;
        this.title = title;
        this.recipientName = recipientName;
        this.conferredOn = conferredOn;
    }

    public void seal(Instant issuedAt, UUID sealedBy, UUID signingKeyId, String payload, String recordHash,
                     byte[] signature, String pdfSha256) {
        if (status != CredentialStatus.PENDING) throw new IllegalStateException("Only pending credentials can be sealed.");
        this.issuedAt = issuedAt;
        this.sealedBy = sealedBy;
        this.signingKeyId = signingKeyId;
        this.payload = payload;
        this.recordHash = recordHash;
        this.signature = signature;
        this.pdfSha256 = pdfSha256;
        this.status = CredentialStatus.VALID;
    }

    public void revoke(Instant at, UUID by, String reason, String note) {
        if (status != CredentialStatus.VALID) throw new IllegalStateException("Only valid credentials can be revoked.");
        this.status = CredentialStatus.REVOKED;
        this.revokedAt = at;
        this.revokedBy = by;
        this.revokeReason = reason;
        this.revokeNote = note;
    }
}
