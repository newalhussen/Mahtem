package et.mahtem.service;

import et.mahtem.config.MahtemProperties;
import et.mahtem.crypto.CanonicalRecord;
import et.mahtem.crypto.Ed25519;
import et.mahtem.crypto.Hashing;
import et.mahtem.crypto.PublicIds;
import et.mahtem.domain.Credential;
import et.mahtem.domain.CredentialStatus;
import et.mahtem.domain.Organization;
import et.mahtem.domain.SigningKey;
import et.mahtem.domain.VerificationLog;
import et.mahtem.domain.VerificationResult;
import et.mahtem.domain.VerifyMethod;
import et.mahtem.repo.CredentialDocumentRepository;
import et.mahtem.repo.CredentialRepository;
import et.mahtem.repo.OrganizationRepository;
import et.mahtem.repo.SigningKeyRepository;
import et.mahtem.repo.VerificationLogRepository;
import et.mahtem.util.ApiException;
import et.mahtem.web.dto.PublicDtos.Check;
import et.mahtem.web.dto.PublicDtos.CheckState;
import et.mahtem.web.dto.PublicDtos.InvalidCause;
import et.mahtem.web.dto.PublicDtos.LedgerEntry;
import et.mahtem.web.dto.PublicDtos.PublicCredential;
import et.mahtem.web.dto.PublicDtos.PublicDocument;
import et.mahtem.web.dto.PublicDtos.PublicIssuer;
import et.mahtem.web.dto.PublicDtos.PublicRevocation;
import et.mahtem.web.dto.PublicDtos.VerificationResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public verification. Every check recomputes the answer from the stored record: it never trusts a
 * stored "valid" flag. A credential is VALID only when the signed payload hashes to the recorded
 * hash, the Ed25519 signature checks out against the issuer's key, the visible fields still match the
 * signed payload, and the issuer has not revoked it.
 */
@Service
public class VerificationService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter RECEIPT_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId ADDIS = ZoneId.of("Africa/Addis_Ababa");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CredentialRepository credentials;
    private final CredentialDocumentRepository documents;
    private final OrganizationRepository organizations;
    private final SigningKeyRepository keys;
    private final VerificationLogRepository logs;
    private final MahtemProperties props;

    public VerificationService(CredentialRepository credentials, CredentialDocumentRepository documents,
                               OrganizationRepository organizations, SigningKeyRepository keys,
                               VerificationLogRepository logs, MahtemProperties props) {
        this.credentials = credentials;
        this.documents = documents;
        this.organizations = organizations;
        this.keys = keys;
        this.logs = logs;
        this.props = props;
    }

    /** Who is asking. Only a salted hash of the address is ever stored. */
    public record Client(String ip, String location) {}

    @Transactional
    public VerificationResponse verify(String rawId, VerifyMethod method, Client client) {
        String id = PublicIds.normalize(rawId);
        Instant now = Instant.now();
        String receipt = "VR-" + RECEIPT_DAY.format(now.atZone(ADDIS)) + "-" + HexFormat.of().withUpperCase().formatHex(randomBytes(3));

        Optional<Credential> found = PublicIds.isWellFormed(id) ? credentials.findByPublicId(id) : Optional.empty();
        if (found.isEmpty() || found.get().getStatus() == CredentialStatus.PENDING) {
            // Attribute unknown IDs to the organization named in them, so issuers can spot forged certificates.
            Organization guess = guessOrganization(id);
            log(null, guess == null ? null : guess.getId(), id, VerificationResult.INVALID, method, client, receipt, now);
            return new VerificationResponse(VerificationResult.INVALID, now, receipt, safeEcho(id), null, null, List.of(), null, null, InvalidCause.NOT_FOUND);
        }

        Credential c = found.get();
        Organization org = organizations.findById(c.getOrganizationId()).orElseThrow();
        SigningKey key = keys.findById(c.getSigningKeyId()).orElse(null);

        boolean hashOk = c.getPayload() != null && Hashing.sha256Hex(c.getPayload()).equals(c.getRecordHash());
        boolean signatureOk = hashOk && key != null
                && Ed25519.verify(c.getPayload().getBytes(StandardCharsets.UTF_8), c.getSignature(), key.getPublicKey())
                && key.getOrganizationId().equals(org.getId());
        boolean integrityOk = signatureOk && CanonicalRecord.matches(c.getPayload(), c);

        if (!signatureOk || !integrityOk) {
            log(c.getId(), org.getId(), id, VerificationResult.INVALID, method, client, receipt, now);
            List<Check> checks = List.of(
                    new Check("signature", "Digital signature", signatureOk ? CheckState.PASS : CheckState.FAIL, signatureOk ? "Valid" : "Invalid",
                            signatureOk ? "Ed25519 signature is mathematically valid" : "The signature does not match the sealed record"),
                    new Check("integrity", "Record integrity", CheckState.FAIL, "Altered", "The record differs from what was sealed at issuance"));
            return new VerificationResponse(VerificationResult.INVALID, now, receipt, id, null, issuer(org), checks, null, null, InvalidCause.TAMPERED);
        }

        boolean revoked = c.getStatus() == CredentialStatus.REVOKED;
        VerificationResult result = revoked ? VerificationResult.REVOKED : VerificationResult.VALID;
        log(c.getId(), org.getId(), id, result, method, client, receipt, now);

        List<Check> checks = new ArrayList<>();
        String keyNote = "Ed25519 · signed by " + org.getCode() + " key " + KeyService.shortFingerprint(key.getFingerprint());
        if (revoked) {
            checks.add(new Check("revocation", "Revocation status", CheckState.FAIL, "Revoked", "Withdrawn by issuer on " + DAY.format(c.getRevokedAt().atZone(ADDIS))));
            checks.add(new Check("signature", "Digital signature", CheckState.PASS, "Valid", "The original record was genuinely issued"));
        } else {
            checks.add(new Check("signature", "Digital signature", CheckState.PASS, "Valid", keyNote));
            checks.add(new Check("revocation", "Revocation status", CheckState.PASS, "Not revoked", "Live check against the issuer registry"));
        }
        checks.add(org.isDomainVerified()
                ? new Check("issuer", "Issuer identity", CheckState.PASS, "Confirmed", "Domain " + org.getDomain() + " verified by Mahtem")
                : new Check("issuer", "Issuer identity", CheckState.WARN, "Not confirmed", "Domain " + org.getDomain() + " has not been verified"));
        if (!revoked) checks.add(new Check("integrity", "Record integrity", CheckState.PASS, "Intact", "Matches the record sealed at issuance"));

        boolean showDetails = org.isShowDetailsPublicly();
        PublicCredential pc = new PublicCredential(c.getPublicId(), c.getType(), c.getType().label(), c.getTitle(),
                showDetails ? c.getDetails() : null, c.getRecipientName(), c.getRecipientNameAm(), c.getConferredOn(),
                c.getType().dateLabel(), c.getIssuedAt());
        PublicRevocation pr = revoked
                ? new PublicRevocation(c.getRevokedAt(), org.isShowRevocationReason() ? c.getRevokeReason() : null,
                        org.isShowRevocationReason() ? c.getRevokeNote() : null, org.getName())
                : null;
        boolean hasPdf = c.getPdfSha256() != null && documents.existsById(c.getId());
        return new VerificationResponse(result, now, receipt, id, pc, issuer(org), checks, pr,
                new PublicDocument(hasPdf && org.isAllowPdfDownload() && !revoked, hasPdf ? c.getPdfSha256() : null), null);
    }

    /** The issued PDF, only if the issuer allows verifiers to download it. */
    @Transactional(readOnly = true)
    public byte[] publicPdf(String rawId) {
        Credential c = credentials.findByPublicId(PublicIds.normalize(rawId)).orElseThrow(() -> ApiException.notFound("Credential"));
        Organization org = organizations.findById(c.getOrganizationId()).orElseThrow();
        if (c.getStatus() != CredentialStatus.VALID || !org.isAllowPdfDownload()) {
            throw ApiException.notFound("Certificate");
        }
        return documents.findById(c.getId()).orElseThrow(() -> ApiException.notFound("Certificate")).getPdf();
    }

    /** Recent checks with the middle of each ID masked: shows activity without leaking valid IDs. */
    @Transactional(readOnly = true)
    public List<LedgerEntry> ledger() {
        return logs.findTop5ByOrderByCheckedAtDesc().stream()
                .map(l -> new LedgerEntry(l.getCheckedAt(), mask(l.getQueriedId()), l.getResult()))
                .toList();
    }

    /* ---------------------------------------------------------------------------------------- helpers */

    private PublicIssuer issuer(Organization org) {
        return new PublicIssuer(org.getName(), org.getNameAm(), org.getInitials(), org.getDepartment(), org.getDomain(),
                org.isDomainVerified(), org.getVerifierContact());
    }

    private Organization guessOrganization(String id) {
        String[] parts = id.split("-");
        return parts.length >= 2 && parts[0].equals("MHT") ? organizations.findByCode(parts[1]).orElse(null) : null;
    }

    private void log(java.util.UUID credentialId, java.util.UUID orgId, String id, VerificationResult result,
                     VerifyMethod method, Client client, String receipt, Instant at) {
        String ipHash = client.ip() == null ? null : Hashing.sha256Hex(props.security().ipSalt() + "|" + client.ip());
        logs.save(new VerificationLog(credentialId, orgId, safeEcho(id), result, method,
                client.location(), ipHash, receipt, at));
    }

    /** Only characters that can appear in a real ID are kept, so hostile input never reaches logs or pages. */
    static String safeEcho(String id) {
        String cleaned = id == null ? "" : id.replaceAll("[^A-Z0-9-]", "");
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    static String mask(String id) {
        String[] p = id.split("-");
        if (p.length != 5) return "MHT-••••-••-••••-••••";
        return p[0] + "-" + p[1] + "-" + p[2] + "-" + p[3].substring(0, Math.min(2, p[3].length())) + "••-••" + p[4].substring(Math.max(0, p[4].length() - 2));
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }
}
