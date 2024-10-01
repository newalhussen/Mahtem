package et.mahtem.web;

import et.mahtem.domain.Credential;
import et.mahtem.domain.CredentialStatus;
import et.mahtem.domain.CredentialType;
import et.mahtem.domain.VerificationResult;
import et.mahtem.service.AuthService;
import et.mahtem.service.BatchService;
import et.mahtem.service.IssuanceService;
import et.mahtem.service.OrganizationService;
import et.mahtem.service.QueryService;
import et.mahtem.util.Actor;
import et.mahtem.util.ApiException;
import et.mahtem.web.dto.ConsoleDtos.ActionLog;
import et.mahtem.web.dto.ConsoleDtos.BatchPreview;
import et.mahtem.web.dto.ConsoleDtos.BatchRow;
import et.mahtem.web.dto.ConsoleDtos.CredentialFilter;
import et.mahtem.web.dto.ConsoleDtos.KeyRow;
import et.mahtem.web.dto.ConsoleDtos.Member;
import et.mahtem.web.dto.ConsoleDtos.OrgProfile;
import et.mahtem.web.dto.ConsoleDtos.Overview;
import et.mahtem.web.dto.ConsoleDtos.StatusCounts;
import et.mahtem.web.dto.ConsoleDtos.TemplateRow;
import et.mahtem.web.dto.ConsoleDtos.VerificationRow;
import et.mahtem.web.dto.CredentialDtos.CreateCredentialRequest;
import et.mahtem.web.dto.CredentialDtos.CredentialDetail;
import et.mahtem.web.dto.CredentialDtos.PageOf;
import et.mahtem.web.dto.CredentialDtos.RevokeRequest;
import et.mahtem.web.dto.OrgDtos.InviteRequest;
import et.mahtem.web.dto.OrgDtos.UpdateMemberRequest;
import et.mahtem.web.dto.OrgDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Everything the signed-in issuer console needs. The organization always comes from the session. */
@RestController
@RequestMapping("/api/org")
public class ConsoleController {

    private final QueryService query;
    private final IssuanceService issuance;
    private final OrganizationService org;
    private final BatchService batches;
    private final AuthService auth;

    public ConsoleController(QueryService query, IssuanceService issuance, OrganizationService org, BatchService batches, AuthService auth) {
        this.query = query;
        this.issuance = issuance;
        this.org = org;
        this.batches = batches;
        this.auth = auth;
    }

    /* ---- overview ---- */

    @GetMapping("/overview")
    public Overview overview(Actor actor) {
        return query.overview(actor);
    }

    /* ---- credentials ---- */

    @GetMapping("/credentials")
    public PageOf<et.mahtem.web.dto.CredentialDtos.CredentialSummary> list(
            Actor actor, @RequestParam(required = false) String q, @RequestParam(required = false) CredentialStatus status,
            @RequestParam(required = false) CredentialType type, @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer year, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return query.list(actor, new CredentialFilter(q, status, type, title, year), Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/credentials/stats")
    public Map<String, Object> stats(Actor actor) {
        StatusCounts counts = query.statusCounts(actor);
        return Map.of("counts", counts, "titles", query.titles(actor));
    }

    @PostMapping("/credentials")
    public CredentialDetail create(Actor actor, @Valid @RequestBody CreateCredentialRequest body) {
        Credential c = issuance.create(actor, body);
        return query.detail(actor, c.getPublicId());
    }

    @GetMapping("/credentials/{id}")
    public CredentialDetail detail(Actor actor, @PathVariable String id) {
        return query.detail(actor, id);
    }

    @PostMapping("/credentials/{id}/seal")
    public CredentialDetail seal(Actor actor, @PathVariable String id) {
        return query.detail(actor, issuance.seal(actor, id.toUpperCase(Locale.ROOT)).getPublicId());
    }

    @PostMapping("/credentials/{id}/revoke")
    public CredentialDetail revoke(Actor actor, @PathVariable String id, @Valid @RequestBody RevokeRequest body) {
        return query.detail(actor, issuance.revoke(actor, id.toUpperCase(Locale.ROOT), body).getPublicId());
    }

    @GetMapping("/credentials/{id}/pdf")
    public ResponseEntity<byte[]> pdf(Actor actor, @PathVariable String id, @RequestParam(defaultValue = "false") boolean download) {
        byte[] bytes = query.pdf(actor, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.builder(download ? "attachment" : "inline").filename(id.toUpperCase(Locale.ROOT) + ".pdf").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(bytes);
    }

    @GetMapping("/credentials/export.csv")
    public ResponseEntity<byte[]> export(Actor actor, @RequestParam(required = false) String q, @RequestParam(required = false) CredentialStatus status,
                                         @RequestParam(required = false) CredentialType type, @RequestParam(required = false) String title,
                                         @RequestParam(required = false) Integer year) {
        String csv = query.csv(actor, new CredentialFilter(q, status, type, title, year));
        return csvResponse(csv, "credentials.csv");
    }

    /* ---- bulk ---- */

    @GetMapping("/batches")
    public List<BatchRow> batches(Actor actor) {
        return batches.list(actor);
    }

    @GetMapping("/batches/template.csv")
    public ResponseEntity<byte[]> template() {
        return csvResponse(batches.template(), "mahtem-credentials-template.csv");
    }

    @PostMapping("/batches")
    public BatchPreview upload(Actor actor, @RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean requireApproval,
                               @RequestParam(defaultValue = "DEGREE") CredentialType type) throws IOException {
        if (file.isEmpty()) throw ApiException.invalid("Choose a CSV file.", "file");
        String name = file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) throw ApiException.invalid("Upload a .csv file.", "file");
        return batches.upload(actor, name, file.getBytes(), requireApproval, type);
    }

    @PostMapping("/batches/{id}/approve")
    public BatchRow approve(Actor actor, @PathVariable UUID id) {
        return batches.approve(actor, id);
    }

    /* ---- logs ---- */

    @GetMapping("/log/verifications")
    public PageOf<VerificationRow> verifications(Actor actor, @RequestParam(required = false) VerificationResult result,
                                                 @RequestParam(required = false) String id, @RequestParam(defaultValue = "30") int days,
                                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return query.verifications(actor, result, id, Math.min(Math.max(days, 1), 3650), Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/log/actions")
    public ActionLog actions(Actor actor, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return query.actions(actor, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    /* ---- settings ---- */

    @GetMapping("/profile")
    public OrgProfile profile(Actor actor) {
        return org.profile(actor);
    }

    @PutMapping("/profile")
    public OrgProfile updateProfile(Actor actor, @Valid @RequestBody UpdateProfileRequest body) {
        return org.updateProfile(actor, body);
    }

    @PostMapping("/domain/verify")
    public OrgProfile verifyDomain(Actor actor) {
        return org.verifyDomain(actor);
    }

    @GetMapping("/keys")
    public List<KeyRow> keys(Actor actor) {
        return org.keys(actor);
    }

    @PostMapping("/keys/rotate")
    public List<KeyRow> rotate(Actor actor) {
        return org.rotateKey(actor);
    }

    @GetMapping("/team")
    public List<Member> team(Actor actor) {
        return org.team(actor);
    }

    @PostMapping("/team/invite")
    public Map<String, Object> invite(Actor actor, @Valid @RequestBody InviteRequest body) {
        AuthService.Invite invite = auth.invite(actor, body.email(), body.fullName(), body.role());
        return Map.of("link", invite.link());
    }

    @PatchMapping("/team/{id}")
    public Member updateMember(Actor actor, @PathVariable UUID id, @RequestBody UpdateMemberRequest body) {
        return org.updateMember(actor, id, body);
    }

    @GetMapping("/templates")
    public List<TemplateRow> templates(Actor actor) {
        return org.templates(actor);
    }

    private static ResponseEntity<byte[]> csvResponse(String csv, String filename) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
