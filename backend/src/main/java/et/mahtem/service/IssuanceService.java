package et.mahtem.service;

import et.mahtem.config.MahtemProperties;
import et.mahtem.crypto.CanonicalRecord;
import et.mahtem.crypto.Hashing;
import et.mahtem.crypto.PublicIds;
import et.mahtem.domain.AppUser;
import et.mahtem.domain.Credential;
import et.mahtem.domain.CredentialDocument;
import et.mahtem.domain.CredentialStatus;
import et.mahtem.domain.Organization;
import et.mahtem.domain.SigningKey;
import et.mahtem.repo.AppUserRepository;
import et.mahtem.repo.CredentialDocumentRepository;
import et.mahtem.repo.CredentialRepository;
import et.mahtem.repo.OrganizationRepository;
import et.mahtem.util.Actor;
import et.mahtem.util.ApiException;
import et.mahtem.web.dto.CredentialDtos.CreateCredentialRequest;
import et.mahtem.web.dto.CredentialDtos.IssueMode;
import et.mahtem.web.dto.CredentialDtos.RevokeRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating, sealing (signing) and revoking credentials. All rules about who may do what live here. */
@Service
public class IssuanceService {

    public static final Set<String> REVOKE_REASONS = Set.of(
            "Issued in error", "Superseded by a corrected credential", "Academic misconduct", "Requested by recipient", "Other");

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final CredentialRepository credentials;
    private final CredentialDocumentRepository documents;
    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final KeyService keyService;
    private final PdfService pdf;
    private final AuditService audit;
    private final Mailer mailer;
    private final MahtemProperties props;

    public IssuanceService(CredentialRepository credentials, CredentialDocumentRepository documents,
                           OrganizationRepository organizations, AppUserRepository users, KeyService keyService,
                           PdfService pdf, AuditService audit, Mailer mailer, MahtemProperties props) {
        this.credentials = credentials;
        this.documents = documents;
        this.organizations = organizations;
        this.users = users;
        this.keyService = keyService;
        this.pdf = pdf;
        this.audit = audit;
        this.mailer = mailer;
        this.props = props;
    }

    /* ------------------------------------------------------------------------------------ create */

    @Transactional
    public Credential create(Actor actor, CreateCredentialRequest r) {
        actor.require(et.mahtem.domain.Role.ADMIN, et.mahtem.domain.Role.ISSUER);
        Organization org = organization(actor);
        Credential c = newPending(org, actor.userId(), r, null);
        c.setApprovalRequired(r.mode() == IssueMode.REQUIRE_APPROVAL);
        credentials.save(c);
        audit.record(actor, "Credential created", c.getPublicId() + " · " + c.getRecipientName());
        if (r.mode() == IssueMode.SEAL) {
            seal(org, c, actor.userId());
            audit.record(actor, "Credential sealed", c.getPublicId() + " · " + c.getTitle());
            if (r.sendEmail()) notifyIssued(org, c);
        }
        return c;
    }

    /** Builds a pending record, rejecting duplicates. Used by single and bulk issuance. */
    Credential newPending(Organization org, UUID createdBy, CreateCredentialRequest r, UUID batchId) {
        return newPending(org, createdBy, r, batchId, null);
    }

    Credential newPending(Organization org, UUID createdBy, CreateCredentialRequest r, UUID batchId, String forcedPublicId) {
        String name = r.recipientName().trim();
        String title = r.title().trim();
        boolean duplicate = credentials.findAll((root, q, cb) -> cb.and(
                cb.equal(root.get("organizationId"), org.getId()),
                cb.equal(cb.lower(root.get("recipientName")), name.toLowerCase(Locale.ROOT)),
                cb.equal(cb.lower(root.get("title")), title.toLowerCase(Locale.ROOT)),
                cb.equal(root.get("conferredOn"), r.conferredOn()),
                cb.notEqual(root.get("status"), CredentialStatus.REVOKED))).stream().findAny().isPresent();
        if (duplicate) {
            throw ApiException.conflict("DUPLICATE", name + " already has a \"" + title + "\" credential for that date. Revoke it first if it must be replaced.");
        }
        Credential c = new Credential(org.getId(), forcedPublicId != null ? forcedPublicId : uniquePublicId(org), r.type(), title, name, r.conferredOn(), createdBy);
        c.setDetails(blankToNull(r.details()));
        c.setStatement(blankToNull(r.statement()));
        c.setRecipientNameAm(blankToNull(r.recipientNameAm()));
        c.setRecipientEmail(blankToNull(r.recipientEmail()) == null ? null : r.recipientEmail().trim().toLowerCase(Locale.ROOT));
        c.setRecipientRef(blankToNull(r.recipientRef()));
        c.setBatchId(batchId);
        return c;
    }

    /* ------------------------------------------------------------------------------------ seal */

    /** Seal a pending credential (a draft, or one waiting for approval). */
    @Transactional
    public Credential seal(Actor actor, String publicId) {
        Organization org = organization(actor);
        Credential c = credentials.findByPublicIdAndOrganizationId(publicId, actor.organizationId())
                .orElseThrow(() -> ApiException.notFound("Credential"));
        if (c.getStatus() != CredentialStatus.PENDING) {
            throw ApiException.conflict("NOT_PENDING", "This credential has already been sealed.");
        }
        if (c.isApprovalRequired()) {
            if (!actor.role().canApprove()) throw ApiException.forbidden("Only an Approver or Admin can approve this credential.");
            if (c.getCreatedBy().equals(actor.userId())) {
                throw ApiException.forbidden("A second person must approve this credential. You created it.");
            }
        } else if (!actor.role().canIssue()) {
            throw ApiException.forbidden("Your role (" + actor.role().name().toLowerCase() + ") cannot seal credentials.");
        }
        seal(org, c, actor.userId());
        audit.record(actor, c.isApprovalRequired() ? "Credential approved" : "Credential sealed", c.getPublicId() + " · " + c.getTitle());
        notifyIssued(org, c);
        return c;
    }

    /** Signs the canonical record, renders the PDF and stores both. The core of issuance. */
    @Transactional
    public void seal(Organization org, Credential c, UUID sealedBy) {
        seal(org, c, sealedBy, Instant.now());
    }

    /**
     * Imports a credential issued in the past (migrations, demo data): it is created and signed as of
     * {@code issuedAt}, so the signed record carries the historical date.
     */
    @Transactional
    public Credential importHistorical(Organization org, UUID userId, CreateCredentialRequest r, Instant issuedAt, boolean sealIt, String publicId) {
        Credential c = newPending(org, userId, r, null, publicId);
        c.setCreatedAt(issuedAt.minus(java.time.Duration.ofHours(20)));
        if (r.mode() == IssueMode.REQUIRE_APPROVAL) c.setApprovalRequired(true);
        if (sealIt) seal(org, c, userId, issuedAt);
        else credentials.save(c);
        return c;
    }

    @Transactional
    public void seal(Organization org, Credential c, UUID sealedBy, Instant when) {
        if (props.requireVerifiedDomain() && !org.isDomainVerified()) {
            throw ApiException.conflict("DOMAIN_UNVERIFIED", "Verify your organization's domain in Settings before sealing credentials.");
        }
        SigningKey key = keyService.activeKey(org);
        Instant issuedAt = when.truncatedTo(ChronoUnit.SECONDS);
        String payload = CanonicalRecord.build(c, org, key.getFingerprint(), issuedAt);
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        byte[] signature = keyService.sign(key, payloadBytes);
        String recordHash = Hashing.sha256Hex(payloadBytes);

        byte[] pdfBytes = pdf.render(certificateData(org, c, issuedAt));
        c.seal(issuedAt, sealedBy, key.getId(), payload, recordHash, signature, Hashing.sha256Hex(pdfBytes));
        credentials.save(c);
        documents.save(new CredentialDocument(c.getId(), pdfBytes));
    }

    PdfService.CertificateData certificateData(Organization org, Credential c, Instant issuedAt) {
        String statement = c.getStatement() != null ? c.getStatement() : c.getType().defaultStatement();
        return new PdfService.CertificateData(
                c.getPublicId(), verifyUrl(c.getPublicId()), org.getName(), org.getNameAm(), org.getDepartment(), org.getInitials(),
                c.getRecipientName(), c.getRecipientNameAm(), statement, c.getTitle(), c.getDetails(),
                c.getType().dateLabel(), LONG_DATE.format(c.getConferredOn()),
                LONG_DATE.format(issuedAt.atZone(java.time.ZoneId.of("Africa/Addis_Ababa")).toLocalDate()),
                org.getSignatories(), issuedAt);
    }

    public String verifyUrl(String publicId) {
        return props.publicBaseUrl().replaceAll("/+$", "") + "/verify/" + publicId + "?s=qr";
    }

    /* ------------------------------------------------------------------------------------ revoke */

    @Transactional
    public Credential revoke(Actor actor, String publicId, RevokeRequest r) {
        actor.require(et.mahtem.domain.Role.ADMIN, et.mahtem.domain.Role.ISSUER);
        Credential c = credentials.findByPublicIdAndOrganizationId(publicId, actor.organizationId())
                .orElseThrow(() -> ApiException.notFound("Credential"));
        if (c.getStatus() != CredentialStatus.VALID) {
            throw ApiException.conflict("NOT_REVOCABLE", c.getStatus() == CredentialStatus.REVOKED
                    ? "This credential is already revoked." : "Only sealed credentials can be revoked.");
        }
        if (!REVOKE_REASONS.contains(r.reason())) {
            throw ApiException.invalid("Choose one of the listed reasons.", "reason");
        }
        String code = publicId.substring(publicId.lastIndexOf('-') + 1);
        if (!code.equalsIgnoreCase(r.confirm().trim())) {
            throw ApiException.invalid("That does not match. Type " + code + " to confirm.", "confirm");
        }
        c.revoke(Instant.now(), actor.userId(), r.reason(), blankToNull(r.note()));
        credentials.save(c);
        audit.record(actor, "Credential revoked", c.getPublicId() + " · " + r.reason());
        if (r.notifyRecipient() && c.getRecipientEmail() != null) {
            Organization org = organization(actor);
            mailer.send(c.getRecipientEmail(), "Your credential has been revoked",
                    "Your " + c.getTitle() + " (" + c.getPublicId() + ") issued by " + org.getName() + " has been revoked. Reason: " + r.reason() + ".");
        }
        return c;
    }

    /* ------------------------------------------------------------------------------------ helpers */

    private void notifyIssued(Organization org, Credential c) {
        if (c.getRecipientEmail() == null) return;
        mailer.send(c.getRecipientEmail(), "Your " + c.getType().label().toLowerCase(Locale.ROOT) + " from " + org.getName(),
                "Congratulations " + c.getRecipientName() + ". Your credential " + c.getPublicId() + " has been sealed.\n"
                        + "Anyone can verify it at " + verifyUrl(c.getPublicId()));
    }

    private Organization organization(Actor actor) {
        return organizations.findById(actor.organizationId()).orElseThrow(() -> ApiException.notFound("Organization"));
    }

    private String uniquePublicId(Organization org) {
        for (int i = 0; i < 10; i++) {
            String id = PublicIds.next(org.getCode(), Year.now());
            if (!credentials.existsByPublicId(id)) return id;
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ID_EXHAUSTED", "Could not allocate a credential ID. Please try again.");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Names of people involved, for the detail page. */
    String displayName(UUID userId) {
        return userId == null ? "System" : users.findById(userId).map(AppUser::getFullName).orElse("Former staff member");
    }
}
