package et.mahtem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A staff action, hash-chained to the previous one for the same organization. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private long seq;

    private UUID actorId;

    @Column(nullable = false)
    private String actorName;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String detail;

    private String ip;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String prevHash;

    @Column(nullable = false)
    private String hash;

    protected AuditEvent() {}

    public AuditEvent(UUID organizationId, long seq, UUID actorId, String actorName, String action, String detail,
                      String ip, Instant occurredAt, String prevHash, String hash) {
        this.organizationId = organizationId;
        this.seq = seq;
        this.actorId = actorId;
        this.actorName = actorName;
        this.action = action;
        this.detail = detail;
        this.ip = ip;
        this.occurredAt = occurredAt;
        this.prevHash = prevHash;
        this.hash = hash;
    }

    public Long getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public long getSeq() { return seq; }
    public UUID getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public String getIp() { return ip; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getPrevHash() { return prevHash; }
    public String getHash() { return hash; }
}
