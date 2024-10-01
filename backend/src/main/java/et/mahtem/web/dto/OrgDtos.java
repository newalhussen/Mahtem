package et.mahtem.web.dto;

import et.mahtem.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Request bodies for organization settings, team and sign-in. */
public final class OrgDtos {

    private OrgDtos() {}

    public record SignatoryInput(
            @NotBlank(message = "Enter the signatory's name.") @Size(max = 80) String name,
            @NotBlank(message = "Enter the signatory's title.") @Size(max = 80) String title) {}

    public record UpdateProfileRequest(
            @NotBlank(message = "Enter the organization's name.") @Size(max = 200) String name,
            @Size(max = 200) String nameAm,
            @NotBlank(message = "Enter the initials.") @Size(max = 8) String initials,
            @Size(max = 200) String department,
            @Size(max = 200) String accreditationBody,
            @NotBlank @Size(max = 80) String country,
            @Email(message = "Enter a valid email address.") @Size(max = 200) String verifierContact,
            boolean showDetailsPublicly,
            boolean showRevocationReason,
            boolean allowPdfDownload,
            @Valid @Size(max = 2, message = "At most two signatories fit on a certificate.") List<SignatoryInput> signatories) {}

    public record InviteRequest(
            @NotBlank(message = "Enter an email address.") @Email(message = "Enter a valid email address.") @Size(max = 254) String email,
            @NotBlank(message = "Enter their name.") @Size(max = 160) String fullName,
            @NotNull(message = "Choose a role.") Role role) {}

    public record UpdateMemberRequest(Role role, Boolean disabled) {}

    public record LoginRequest(
            @NotBlank(message = "Enter your work email.") @Size(max = 254) String email,
            @NotBlank(message = "Enter your password.") @Size(max = 200) String password) {}

    public record TwoFactorRequest(
            @NotBlank String challenge,
            @NotBlank(message = "Enter the 6-digit code.") @Size(max = 8) String code) {}

    public record AcceptInviteRequest(
            @NotBlank String token,
            @NotBlank(message = "Choose a password.") @Size(max = 200) String password) {}

    public record EnableTotpRequest(@NotBlank(message = "Enter the 6-digit code.") @Size(max = 8) String code) {}

    public record DisableTotpRequest(
            @NotBlank(message = "Enter your password.") String password,
            @NotBlank(message = "Enter the 6-digit code.") @Size(max = 8) String code) {}
}
