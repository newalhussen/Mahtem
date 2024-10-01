package et.mahtem.web.dto;

import et.mahtem.domain.CredentialType;
import et.mahtem.domain.VerificationResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What an employer sees. Contains nothing that is not printed on the certificate itself. */
public final class PublicDtos {

    private PublicDtos() {}

    public enum CheckState { PASS, FAIL, WARN }

    public record Check(String key, String label, CheckState state, String value, String note) {}

    public record PublicCredential(
            String id, CredentialType type, String typeLabel, String title, String details,
            String recipientName, String recipientNameAm, LocalDate conferredOn, String dateLabel, Instant issuedAt) {}

    public record PublicIssuer(String name, String nameAm, String initials, String department, String domain,
                               boolean domainVerified, String contact) {}

    public record PublicRevocation(Instant revokedAt, String reason, String note, String revokedBy) {}

    public record PublicDocument(boolean downloadable, String sha256) {}

    /** INVALID carries a machine-readable cause so the page can word it correctly. */
    public enum InvalidCause { NOT_FOUND, TAMPERED }

    public record VerificationResponse(
            VerificationResult status,
            Instant checkedAt,
            String receipt,
            String queriedId,
            PublicCredential credential,
            PublicIssuer issuer,
            List<Check> checks,
            PublicRevocation revocation,
            PublicDocument document,
            InvalidCause invalidCause) {}

    /** One masked row of the live ledger on the sign-in page. */
    public record LedgerEntry(Instant at, String id, VerificationResult result) {}
}
