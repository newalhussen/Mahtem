package et.mahtem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A staff member of an issuing organization. */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String fullName;

    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    private String inviteTokenHash;
    private Instant inviteExpiresAt;

    @Column(name = "totp_secret_enc")
    private byte[] totpSecretEnc;

    private boolean totpEnabled;
    private int failedAttempts;
    private Instant lockedUntil;
    private Instant lastLoginAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AppUser() {}

    public AppUser(UUID organizationId, String email, String fullName, Role role, UserStatus status) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.email = email.trim().toLowerCase();
        this.fullName = fullName;
        this.role = role;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public String getInviteTokenHash() { return inviteTokenHash; }
    public Instant getInviteExpiresAt() { return inviteExpiresAt; }
    public byte[] getTotpSecretEnc() { return totpSecretEnc; }
    public void setTotpSecretEnc(byte[] v) { this.totpSecretEnc = v; }
    public boolean isTotpEnabled() { return totpEnabled; }
    public void setTotpEnabled(boolean v) { this.totpEnabled = v; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setInvite(String tokenHash, Instant expiresAt) {
        this.inviteTokenHash = tokenHash;
        this.inviteExpiresAt = expiresAt;
    }

    public void clearInvite() {
        this.inviteTokenHash = null;
        this.inviteExpiresAt = null;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Count a failed password/2FA attempt; lock the account for 15 minutes after five. */
    public void recordFailure(Instant now) {
        failedAttempts++;
        if (failedAttempts >= 5) {
            lockedUntil = now.plusSeconds(15 * 60);
            failedAttempts = 0;
        }
    }

    public void recordSuccess(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }
}
