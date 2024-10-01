package et.mahtem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One public verification check. The IP address is stored only as a salted hash. */
@Entity
@Table(name = "verification_log")
public class VerificationLog {

    @Id
    private UUID id;

    private UUID credentialId;
    private UUID organizationId;

    @Column(nullable = false)
    private String queriedId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationResult result;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerifyMethod method;

    private String location;
    private String ipHash;

    @Column(nullable = false, unique = true)
    private String receipt;

    @Column(nullable = false)
    private Instant checkedAt;

    protected VerificationLog() {}

    public VerificationLog(UUID credentialId, UUID organizationId, String queriedId, VerificationResult result,
                           VerifyMethod method, String location, String ipHash, String receipt, Instant checkedAt) {
        this.id = UUID.randomUUID();
        this.credentialId = credentialId;
        this.organizationId = organizationId;
        this.queriedId = queriedId;
        this.result = result;
        this.method = method;
        this.location = location;
        this.ipHash = ipHash;
        this.receipt = receipt;
        this.checkedAt = checkedAt;
    }

    public UUID getId() { return id; }
    public UUID getCredentialId() { return credentialId; }
    public UUID getOrganizationId() { return organizationId; }
    public String getQueriedId() { return queriedId; }
    public VerificationResult getResult() { return result; }
    public VerifyMethod getMethod() { return method; }
    public String getLocation() { return location; }
    public String getReceipt() { return receipt; }
    public Instant getCheckedAt() { return checkedAt; }
}
