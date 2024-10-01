package et.mahtem.web.dto;

import et.mahtem.domain.CredentialStatus;
import et.mahtem.domain.CredentialType;
import et.mahtem.domain.Role;
import et.mahtem.domain.VerificationResult;
import et.mahtem.domain.VerifyMethod;
import et.mahtem.web.dto.CredentialDtos.CredentialSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read models for the issuer console: overview, logs, settings. */
public final class ConsoleDtos {

    private ConsoleDtos() {}

    public record Kpi(String label, String value, String note) {}

    public record DayCount(LocalDate day, long ok, long bad) {}

    /** {@code href} is a console route; {@code tone} drives the bullet colour (alert or neutral). */
    public record Attention(String title, String detail, String cta, String href, String tone) {}

    public record Overview(List<Kpi> kpis, List<DayCount> verifications, long checks30d, double verifiedPct,
                           List<Attention> attention, List<CredentialSummary> recent) {}

    public record VerificationRow(Instant at, VerificationResult result, String credentialId, String recipient,
                                  VerifyMethod method, String location, String receipt) {}

    public record ActionRow(long seq, Instant at, String action, String detail, String staff, String ip) {}

    public record ActionLog(List<ActionRow> items, long total, int page, int size, boolean chainIntact, Long brokenAtSeq,
                            long chainEntries) {}

    public record OrgProfile(
            String code, String name, String nameAm, String initials, String department, String accreditationBody,
            String country, String verifierContact, String domain, boolean domainVerified, Instant domainVerifiedAt,
            String domainTxtName, String domainTxtValue, boolean showDetailsPublicly, boolean showRevocationReason,
            boolean allowPdfDownload, List<et.mahtem.domain.Signatory> signatories) {}

    public record KeyRow(UUID id, String label, String fingerprint, String algorithm, LocalDate validFrom,
                         LocalDate validTo, String status) {}

    public record Member(UUID id, String name, String email, Role role, String status, boolean twoFactor, Instant lastActive) {}

    public record TemplateRow(CredentialType type, String label, long used) {}

    public record Me(UUID id, String name, String email, Role role, boolean twoFactor, OrgSummary organization) {}

    public record OrgSummary(String code, String name, String initials, String department, String domain,
                             boolean domainVerified, String activeKeyFingerprint, String activeKeyLabel,
                             LocalDate activeKeyValidTo, boolean canIssue, boolean canApprove) {}

    public record BatchRow(UUID id, String filename, int rows, int sealed, String status, String uploadedBy, Instant uploadedAt) {}

    public record RowError(int row, String field, String message) {}

    public record BatchPreview(BatchRow batch, List<RowError> errors, int validRows) {}

    public record StatusCounts(long total, long valid, long pending, long revoked) {}

    public record CredentialFilter(String q, CredentialStatus status, CredentialType type, String title, Integer year) {}
}
