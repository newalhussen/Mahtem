package et.mahtem.service;

import et.mahtem.config.MahtemProperties;
import et.mahtem.domain.AppUser;
import et.mahtem.domain.Credential;
import et.mahtem.domain.CredentialStatus;
import et.mahtem.domain.Organization;
import et.mahtem.domain.SigningKey;
import et.mahtem.domain.VerificationLog;
import et.mahtem.domain.VerificationResult;
import et.mahtem.repo.AppUserRepository;
import et.mahtem.repo.CredentialDocumentRepository;
import et.mahtem.repo.CredentialRepository;
import et.mahtem.repo.OrganizationRepository;
import et.mahtem.repo.VerificationLogRepository;
import et.mahtem.util.Actor;
import et.mahtem.util.ApiException;
import et.mahtem.web.dto.ConsoleDtos.ActionLog;
import et.mahtem.web.dto.ConsoleDtos.ActionRow;
import et.mahtem.web.dto.ConsoleDtos.Attention;
import et.mahtem.web.dto.ConsoleDtos.CredentialFilter;
import et.mahtem.web.dto.ConsoleDtos.DayCount;
import et.mahtem.web.dto.ConsoleDtos.Kpi;
import et.mahtem.web.dto.ConsoleDtos.Overview;
import et.mahtem.web.dto.ConsoleDtos.StatusCounts;
import et.mahtem.web.dto.ConsoleDtos.VerificationRow;
import et.mahtem.web.dto.CredentialDtos.CredentialDetail;
import et.mahtem.web.dto.CredentialDtos.CredentialSummary;
import et.mahtem.web.dto.CredentialDtos.HistoryItem;
import et.mahtem.web.dto.CredentialDtos.PageOf;
import et.mahtem.web.dto.CredentialDtos.Proof;
import et.mahtem.web.dto.CredentialDtos.Revocation;
import jakarta.persistence.criteria.Predicate;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read models for the issuer console. Every query is scoped to the caller's organization. */
@Service
@Transactional(readOnly = true)
public class QueryService {

    private static final ZoneId ADDIS = ZoneId.of("Africa/Addis_Ababa");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final CredentialRepository credentials;
    private final CredentialDocumentRepository documents;
    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final VerificationLogRepository logs;
    private final KeyService keys;
    private final AuditService audit;
    private final IssuanceService issuance;
    private final MahtemProperties props;

    public QueryService(CredentialRepository credentials, CredentialDocumentRepository documents,
                        OrganizationRepository organizations, AppUserRepository users, VerificationLogRepository logs,
                        KeyService keys, AuditService audit, IssuanceService issuance, MahtemProperties props) {
        this.credentials = credentials;
        this.documents = documents;
        this.organizations = organizations;
        this.users = users;
        this.logs = logs;
        this.keys = keys;
        this.audit = audit;
        this.issuance = issuance;
        this.props = props;
    }

    /* ----------------------------------------------------------------------------------- overview */

    public Overview overview(Actor actor) {
        UUID org = actor.organizationId();
        Instant now = Instant.now();
        Instant since30 = now.minus(Duration.ofDays(30));
        Instant since7 = now.minus(Duration.ofDays(7));

        long issuedYear = credentials.count(sealedSince(org, Instant.now().atZone(ADDIS).withDayOfYear(1).toLocalDate().atStartOfDay(ADDIS).toInstant()));
        long issued30 = credentials.count(sealedSince(org, since30));
        long checks30 = logs.countByOrganizationIdAndCheckedAtAfter(org, since30);
        long valid30 = logs.countByOrganizationIdAndResultAndCheckedAtAfter(org, VerificationResult.VALID, since30);
        long pending = credentials.countByOrganizationIdAndStatus(org, CredentialStatus.PENDING);
        long awaitingApproval = credentials.count((r, q, cb) -> cb.and(cb.equal(r.get("organizationId"), org),
                cb.equal(r.get("status"), CredentialStatus.PENDING), cb.isTrue(r.get("approvalRequired"))));
        long revoked = credentials.countByOrganizationIdAndStatus(org, CredentialStatus.REVOKED);
        Instant lastRevoked = credentials.findAll((r, q, cb) -> cb.and(cb.equal(r.get("organizationId"), org), cb.isNotNull(r.get("revokedAt"))),
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "revokedAt"))).stream().findFirst().map(Credential::getRevokedAt).orElse(null);

        List<Kpi> kpis = List.of(
                new Kpi("Credentials issued · " + Instant.now().atZone(ADDIS).getYear(), fmt(issuedYear), fmt(issued30) + " sealed in the last 30 days"),
                new Kpi("Verifications · 30 days", fmt(checks30), "from " + distinctLocations(org, since30) + " locations"),
                new Kpi("Awaiting signature", fmt(pending), awaitingApproval > 0 ? fmt(awaitingApproval) + " need a second approver" : "Drafts and pending approvals"),
                new Kpi("Revoked · all time", fmt(revoked), lastRevoked == null ? "None revoked" : "last on " + DAY.format(lastRevoked.atZone(ADDIS))));

        List<DayCount> series = fillDays(logs.dailyCounts(org, since30), since30);

        List<Attention> attention = new ArrayList<>();
        if (awaitingApproval > 0) {
            attention.add(new Attention(fmt(awaitingApproval) + (awaitingApproval == 1 ? " credential awaits approval" : " credentials await approval"),
                    "A second person must approve before they are sealed", "Review", "/console/credentials?status=PENDING", "alert"));
        } else if (pending > 0) {
            attention.add(new Attention(fmt(pending) + (pending == 1 ? " draft is unsealed" : " drafts are unsealed"),
                    "Drafts are not verifiable until sealed", "Open", "/console/credentials?status=PENDING", "neutral"));
        }
        long failed7 = logs.count((r, q, cb) -> cb.and(cb.equal(r.get("organizationId"), org), cb.equal(r.get("result"), VerificationResult.INVALID),
                cb.greaterThan(r.get("checkedAt"), since7)));
        if (failed7 > 0) {
            attention.add(new Attention(fmt(failed7) + " checks failed for unknown or altered IDs", "Possible forged certificates · last 7 days",
                    "Inspect", "/console/log?result=INVALID", "alert"));
        }
        Organization o = organizations.findById(org).orElseThrow();
        if (!o.isDomainVerified()) {
            attention.add(new Attention("Verify your domain " + o.getDomain(), "Credentials cannot be sealed until your domain is verified", "Verify", "/console/settings?tab=org", "alert"));
        }
        SigningKey key = keys.activeKey(o);
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), key.getValidTo());
        if (daysLeft <= 60) {
            attention.add(new Attention("Signing key rotates in " + Math.max(daysLeft, 0) + " days", "Schedule rotation before " + DAY.format(key.getValidTo()), "Plan", "/console/settings?tab=keys", daysLeft <= 14 ? "alert" : "neutral"));
        }
        AuditService.ChainStatus chain = audit.verifyChain(org);
        if (!chain.intact()) {
            attention.add(new Attention("Staff action log failed its integrity check", "Entry " + chain.brokenAtSeq() + " does not match its hash", "Inspect", "/console/log?tab=actions", "alert"));
        }

        Map<UUID, Long> counts = checkCounts(org);
        List<CredentialSummary> recent = credentials.findTop5ByOrganizationIdOrderByCreatedAtDesc(org).stream().map(c -> summary(c, counts)).toList();
        double pct = checks30 == 0 ? 100 : Math.round(valid30 * 1000.0 / checks30) / 10.0;
        return new Overview(kpis, series, checks30, pct, attention, recent);
    }

    /* ----------------------------------------------------------------------------------- credentials */

    public PageOf<CredentialSummary> list(Actor actor, CredentialFilter f, int page, int size) {
        Page<Credential> p = credentials.findAll(spec(actor.organizationId(), f), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, Long> counts = checkCounts(actor.organizationId());
        return new PageOf<>(p.getContent().stream().map(c -> summary(c, counts)).toList(), p.getTotalElements(), page, size);
    }

    public StatusCounts statusCounts(Actor actor) {
        UUID org = actor.organizationId();
        return new StatusCounts(credentials.countByOrganizationId(org),
                credentials.countByOrganizationIdAndStatus(org, CredentialStatus.VALID),
                credentials.countByOrganizationIdAndStatus(org, CredentialStatus.PENDING),
                credentials.countByOrganizationIdAndStatus(org, CredentialStatus.REVOKED));
    }

    public List<String> titles(Actor actor) {
        return credentials.findDistinctTitles(actor.organizationId());
    }

    public CredentialDetail detail(Actor actor, String publicId) {
        Credential c = find(actor, publicId);
        Organization org = organizations.findById(actor.organizationId()).orElseThrow();
        Map<UUID, String> names = names(Set.of(c.getCreatedBy()), c.getSealedBy(), c.getRevokedBy());

        Proof proof = null;
        if (c.getStatus() != CredentialStatus.PENDING) {
            SigningKey key = keys.get(c.getSigningKeyId());
            proof = new Proof(HexFormat.of().formatHex(c.getSignature()), key.getLabel(), KeyService.shortFingerprint(key.getFingerprint()),
                    c.getRecordHash(), c.getPdfSha256(), c.getIssuedAt(), names.get(c.getSealedBy()), key.getAlgorithm());
        }
        Revocation rev = c.getStatus() == CredentialStatus.REVOKED
                ? new Revocation(c.getRevokedAt(), c.getRevokeReason(), c.getRevokeNote(), names.get(c.getRevokedBy())) : null;

        List<HistoryItem> history = new ArrayList<>();
        if (rev != null) history.add(new HistoryItem("Revoked · " + rev.reason(), rev.revokedBy(), rev.revokedAt(), "revoked"));
        List<VerificationLog> recent = logs.findByCredentialIdOrderByCheckedAtDesc(c.getId(), PageRequest.of(0, 3));
        for (VerificationLog v : recent) {
            history.add(new HistoryItem("Verified by " + (v.getMethod() == et.mahtem.domain.VerifyMethod.QR ? "QR scan" : v.getMethod() == et.mahtem.domain.VerifyMethod.API ? "API" : "ID lookup"),
                    v.getLocation() == null ? "Unknown location" : v.getLocation(), v.getCheckedAt(), v.getResult() == VerificationResult.VALID ? "verified" : "failed"));
        }
        if (c.getStatus() != CredentialStatus.PENDING) {
            history.add(new HistoryItem("Sealed", names.get(c.getSealedBy()), c.getIssuedAt(), "sealed"));
        }
        history.add(new HistoryItem(c.getStatus() == CredentialStatus.PENDING && c.isApprovalRequired() ? "Created, awaiting approval" : "Created",
                names.get(c.getCreatedBy()), c.getCreatedAt(), "created"));
        history.sort(Comparator.comparing(HistoryItem::at).reversed());

        boolean sameUser = c.getCreatedBy().equals(actor.userId());
        boolean canSeal = c.getStatus() == CredentialStatus.PENDING
                && (c.isApprovalRequired() ? actor.role().canApprove() && !sameUser : actor.role().canIssue());
        boolean canRevoke = c.getStatus() == CredentialStatus.VALID && actor.role().canIssue();
        String statement = c.getStatement() != null ? c.getStatement() : c.getType().defaultStatement();
        return new CredentialDetail(summary(c, checkCounts(actor.organizationId())), statement, c.getType().dateLabel(),
                names.get(c.getCreatedBy()), proof, rev, history, issuance.verifyUrl(c.getPublicId()).replace("?s=qr", ""), canSeal, canRevoke);
    }

    /** The stored PDF, for staff of the issuing organization. */
    public byte[] pdf(Actor actor, String publicId) {
        Credential c = find(actor, publicId);
        if (c.getStatus() == CredentialStatus.PENDING) throw ApiException.conflict("NOT_SEALED", "Seal the credential to generate its certificate.");
        return documents.findById(c.getId()).orElseThrow(() -> ApiException.notFound("Certificate")).getPdf();
    }

    public String csv(Actor actor, CredentialFilter f) {
        List<Credential> rows = credentials.findAll(spec(actor.organizationId(), f), Sort.by(Sort.Direction.DESC, "createdAt"));
        StringBuilder sb = new StringBuilder("﻿Credential ID,Recipient,Email,Reference,Type,Title,Details,Date,Issued,Status\r\n");
        for (Credential c : rows) {
            sb.append(String.join(",",
                    cell(c.getPublicId()), cell(c.getRecipientName()), cell(c.getRecipientEmail()), cell(c.getRecipientRef()),
                    cell(c.getType().label()), cell(c.getTitle()), cell(c.getDetails()), cell(c.getConferredOn().toString()),
                    cell(c.getIssuedAt() == null ? "" : c.getIssuedAt().toString()), cell(c.getStatus().name()))).append("\r\n");
        }
        return sb.toString();
    }

    /* ----------------------------------------------------------------------------------- logs */

    public PageOf<VerificationRow> verifications(Actor actor, VerificationResult result, String idFragment, int days, int page, int size) {
        UUID org = actor.organizationId();
        Instant since = Instant.now().minus(Duration.ofDays(days));
        Specification<VerificationLog> spec = (r, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(r.get("organizationId"), org));
            p.add(cb.greaterThan(r.get("checkedAt"), since));
            if (result != null) p.add(cb.equal(r.get("result"), result));
            if (idFragment != null && !idFragment.isBlank()) p.add(cb.like(r.get("queriedId"), "%" + escapeLike(idFragment.trim().toUpperCase(Locale.ROOT)) + "%", '\\'));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<VerificationLog> pg = logs.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "checkedAt")));
        Map<UUID, Credential> byId = credentials.findAllById(pg.getContent().stream().map(VerificationLog::getCredentialId).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Credential::getId, c -> c));
        List<VerificationRow> rows = pg.getContent().stream().map(v -> {
            Credential c = v.getCredentialId() == null ? null : byId.get(v.getCredentialId());
            String who = c == null || v.getResult() == VerificationResult.INVALID ? null : abbreviate(c.getRecipientName());
            return new VerificationRow(v.getCheckedAt(), v.getResult(), v.getQueriedId(), who, v.getMethod(), v.getLocation(), v.getReceipt());
        }).toList();
        return new PageOf<>(rows, pg.getTotalElements(), page, size);
    }

    public ActionLog actions(Actor actor, int page, int size) {
        Page<et.mahtem.domain.AuditEvent> pg = audit.page(actor.organizationId(), page, size);
        AuditService.ChainStatus chain = audit.verifyChain(actor.organizationId());
        List<ActionRow> rows = pg.getContent().stream()
                .map(e -> new ActionRow(e.getSeq(), e.getOccurredAt(), e.getAction(), e.getDetail(), e.getActorName(), e.getIp())).toList();
        return new ActionLog(rows, pg.getTotalElements(), page, size, chain.intact(), chain.brokenAtSeq(), chain.entries());
    }

    /* ----------------------------------------------------------------------------------- helpers */

    private Credential find(Actor actor, String publicId) {
        return credentials.findByPublicIdAndOrganizationId(publicId.trim().toUpperCase(Locale.ROOT), actor.organizationId())
                .orElseThrow(() -> ApiException.notFound("Credential"));
    }

    private Specification<Credential> sealedSince(UUID org, Instant since) {
        return (r, q, cb) -> cb.and(cb.equal(r.get("organizationId"), org), cb.isNotNull(r.get("issuedAt")), cb.greaterThanOrEqualTo(r.get("issuedAt"), since));
    }

    private Specification<Credential> spec(UUID org, CredentialFilter f) {
        return (r, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(r.get("organizationId"), org));
            if (f.status() != null) p.add(cb.equal(r.get("status"), f.status()));
            if (f.type() != null) p.add(cb.equal(r.get("type"), f.type()));
            if (f.title() != null && !f.title().isBlank()) p.add(cb.equal(r.get("title"), f.title()));
            if (f.year() != null) p.add(cb.between(r.get("conferredOn"), LocalDate.of(f.year(), 1, 1), LocalDate.of(f.year(), 12, 31)));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + escapeLike(f.q().trim().toLowerCase(Locale.ROOT)) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(r.get("recipientName")), like, '\\'),
                        cb.like(cb.lower(r.get("publicId")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(r.get("recipientEmail"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(r.get("recipientRef"), "")), like, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private CredentialSummary summary(Credential c, Map<UUID, Long> counts) {
        return new CredentialSummary(c.getPublicId(), c.getType(), c.getType().label(), c.getTitle(), c.getDetails(), c.getRecipientName(),
                c.getRecipientNameAm(), c.getRecipientEmail(), c.getRecipientRef(), c.getConferredOn(), c.getIssuedAt(), c.getStatus(),
                c.isApprovalRequired(), counts.getOrDefault(c.getId(), 0L), c.getBatchId());
    }

    private Map<UUID, Long> checkCounts(UUID org) {
        Map<UUID, Long> m = new HashMap<>();
        for (Object[] row : logs.countsByCredential(org)) m.put((UUID) row[0], (Long) row[1]);
        return m;
    }

    private long distinctLocations(UUID org, Instant since) {
        return logs.findAll((r, q, cb) -> cb.and(cb.equal(r.get("organizationId"), org), cb.greaterThan(r.get("checkedAt"), since), cb.isNotNull(r.get("location"))))
                .stream().map(VerificationLog::getLocation).distinct().count();
    }

    private List<DayCount> fillDays(List<Object[]> rows, Instant since) {
        Map<LocalDate, long[]> byDay = new HashMap<>();
        for (Object[] r : rows) {
            LocalDate d = r[0] instanceof Date sd ? sd.toLocalDate() : (LocalDate) r[0];
            byDay.put(d, new long[]{((Number) r[1]).longValue(), ((Number) r[2]).longValue()});
        }
        LocalDate start = since.atZone(ADDIS).toLocalDate().plusDays(1);
        List<DayCount> out = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            LocalDate d = start.plusDays(i);
            long[] v = byDay.getOrDefault(d, new long[]{0, 0});
            out.add(new DayCount(d, v[0], v[1]));
        }
        return out;
    }

    private Map<UUID, String> names(Set<UUID> required, UUID... more) {
        Set<UUID> ids = new java.util.HashSet<>(required);
        for (UUID u : more) if (u != null) ids.add(u);
        return users.findAllById(ids).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));
    }

    private static String fmt(long n) {
        return String.format(Locale.ENGLISH, "%,d", n);
    }

    /** "Selamawit Tesfaye Bekele" -> "Selamawit T. Bekele" (first, middle initial, last). */
    static String abbreviate(String full) {
        String[] p = full.trim().split("\\s+");
        if (p.length < 3) return full;
        return p[0] + " " + p[1].charAt(0) + ". " + p[p.length - 1];
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** CSV cell with quoting and neutralisation of spreadsheet formula injection. */
    static String cell(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
