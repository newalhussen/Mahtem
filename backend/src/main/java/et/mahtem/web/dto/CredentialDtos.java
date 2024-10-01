package et.mahtem.web.dto;

import et.mahtem.domain.CredentialStatus;
import et.mahtem.domain.CredentialType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for the issuer console API. */
public final class CredentialDtos {

    private CredentialDtos() {}

    /** SEAL signs now; DRAFT saves for later; REQUIRE_APPROVAL waits for a second person to seal. */
    public enum IssueMode { SEAL, DRAFT, REQUIRE_APPROVAL }

    public record CreateCredentialRequest(
            @NotNull(message = "Choose a credential type.") CredentialType type,
            @NotBlank(message = "Enter the credential title.") @Size(max = 240, message = "Keep the title under 240 characters.") String title,
            @Size(max = 240, message = "Keep this under 240 characters.") String details,
            @Size(max = 300, message = "Keep the wording under 300 characters.") String statement,
            @NotBlank(message = "Enter the recipient's full name.") @Size(max = 160, message = "That name is too long.") String recipientName,
            @Size(max = 160, message = "That name is too long.") String recipientNameAm,
            @Email(message = "Enter a valid email address.") @Size(max = 254) String recipientEmail,
            @Size(max = 80, message = "Keep this under 80 characters.") String recipientRef,
            @NotNull(message = "Enter the date.") @PastOrPresent(message = "The date cannot be in the future.") LocalDate conferredOn,
            @NotNull(message = "Choose how to issue.") IssueMode mode,
            boolean sendEmail) {}

    public record RevokeRequest(
            @NotBlank(message = "Choose a reason.") @Size(max = 120) String reason,
            @Size(max = 500, message = "Keep the note under 500 characters.") String note,
            boolean notifyRecipient,
            @NotBlank(message = "Type the confirmation code.") String confirm) {}

    public record CredentialSummary(
            String id, CredentialType type, String typeLabel, String title, String details,
            String recipientName, String recipientNameAm, String recipientEmail, String recipientRef,
            LocalDate conferredOn, Instant issuedAt, CredentialStatus status, boolean approvalRequired,
            long checks, UUID batchId) {}

    public record Proof(String signature, String keyLabel, String keyFingerprint, String recordHash, String pdfSha256,
                        Instant sealedAt, String sealedBy, String algorithm) {}

    public record Revocation(Instant revokedAt, String reason, String note, String revokedBy) {}

    public record HistoryItem(String title, String detail, Instant at, String kind) {}

    public record CredentialDetail(
            CredentialSummary summary, String statement, String dateLabel, String createdBy, Proof proof,
            Revocation revocation, List<HistoryItem> history, String verifyUrl, boolean canSeal, boolean canRevoke) {}

    public record PageOf<T>(List<T> items, long total, int page, int size) {}
}
