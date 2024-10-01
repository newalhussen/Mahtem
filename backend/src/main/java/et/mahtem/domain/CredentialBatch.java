package et.mahtem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A CSV upload of many credentials, optionally waiting for a second approver. */
@Entity
@Table(name = "credential_batch")
public class CredentialBatch {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private int totalRows;

    @Column(nullable = false)
    private int sealedRows;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchStatus status;

    @Column(nullable = false)
    private UUID createdBy;

    private UUID approvedBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant sealedAt;

    protected CredentialBatch() {}

    public CredentialBatch(UUID organizationId, String filename, int totalRows, UUID createdBy, BatchStatus status) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.filename = filename;
        this.totalRows = totalRows;
        this.createdBy = createdBy;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getFilename() { return filename; }
    public int getTotalRows() { return totalRows; }
    public int getSealedRows() { return sealedRows; }
    public BatchStatus getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSealedAt() { return sealedAt; }

    public void startSealing(UUID approver) {
        this.approvedBy = approver;
        this.status = BatchStatus.SEALING;
    }

    public void progress(int sealed) { this.sealedRows = sealed; }

    public void finish(BatchStatus finalStatus, Instant at) {
        this.status = finalStatus;
        this.sealedAt = at;
    }
}
