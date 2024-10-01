package et.mahtem.service;

import et.mahtem.domain.AppUser;
import et.mahtem.domain.BatchStatus;
import et.mahtem.domain.Credential;
import et.mahtem.domain.CredentialBatch;
import et.mahtem.domain.CredentialType;
import et.mahtem.domain.Organization;
import et.mahtem.repo.AppUserRepository;
import et.mahtem.repo.CredentialBatchRepository;
import et.mahtem.repo.CredentialRepository;
import et.mahtem.repo.OrganizationRepository;
import et.mahtem.util.Actor;
import et.mahtem.util.ApiException;
import et.mahtem.web.dto.ConsoleDtos.BatchPreview;
import et.mahtem.web.dto.ConsoleDtos.BatchRow;
import et.mahtem.web.dto.ConsoleDtos.RowError;
import et.mahtem.web.dto.CredentialDtos.CreateCredentialRequest;
import et.mahtem.web.dto.CredentialDtos.IssueMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

/** CSV bulk issuance: every row is validated before anything is created; sealing runs in the background. */
@Service
public class BatchService {

    public static final int MAX_ROWS = 1000;
    private static final List<String> TEMPLATE_HEADER = List.of("full_name", "full_name_am", "email", "reference", "type", "title", "details", "date_conferred");

    private final CredentialBatchRepository batches;
    private final CredentialRepository credentials;
    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final IssuanceService issuance;
    private final BatchSealer sealer;
    private final AuditService audit;

    public BatchService(CredentialBatchRepository batches, CredentialRepository credentials, OrganizationRepository organizations,
                        AppUserRepository users, IssuanceService issuance, BatchSealer sealer, AuditService audit) {
        this.batches = batches;
        this.credentials = credentials;
        this.organizations = organizations;
        this.users = users;
        this.issuance = issuance;
        this.sealer = sealer;
        this.audit = audit;
    }

    public String template() {
        return String.join(",", TEMPLATE_HEADER) + "\r\n"
                + "Selamawit Tesfaye Bekele,ሰላማዊት ተስፋዬ በቀለ,selamawit@example.com,UGR/1234/14,DEGREE,Bachelor of Science in Computer Science,With Distinction,2026-07-15\r\n";
    }

    @Transactional(readOnly = true)
    public List<BatchRow> list(Actor actor) {
        List<CredentialBatch> rows = batches.findTop20ByOrganizationIdOrderByCreatedAtDesc(actor.organizationId());
        Map<UUID, String> names = users.findAllById(rows.stream().map(CredentialBatch::getCreatedBy).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));
        return rows.stream().map(b -> row(b, names.get(b.getCreatedBy()))).toList();
    }

    @Transactional
    public BatchPreview upload(Actor actor, String filename, byte[] content, boolean requireApproval, CredentialType defaultType) {
        actor.require(et.mahtem.domain.Role.ADMIN, et.mahtem.domain.Role.ISSUER);
        Organization org = organizations.findById(actor.organizationId()).orElseThrow();
        List<List<String>> table = Csv.parse(new String(content, StandardCharsets.UTF_8));
        if (table.size() < 2) throw ApiException.invalid("The file has no data rows.", "file");
        if (table.size() - 1 > MAX_ROWS) throw ApiException.invalid("A batch can hold up to " + MAX_ROWS + " rows. Split the file and upload it in parts.", "file");

        Map<String, Integer> col = new java.util.HashMap<>();
        for (int i = 0; i < table.get(0).size(); i++) col.put(table.get(0).get(i).trim().toLowerCase(Locale.ROOT), i);
        for (String required : List.of("full_name", "date_conferred")) {
            if (!col.containsKey(required)) throw ApiException.invalid("Missing required column \"" + required + "\".", "file");
        }
        if (!col.containsKey("title") && !col.containsKey("degree")) throw ApiException.invalid("Missing required column \"title\".", "file");

        List<RowError> errors = new ArrayList<>();
        List<CreateCredentialRequest> requests = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int r = 1; r < table.size(); r++) {
            List<String> cells = table.get(r);
            if (cells.stream().allMatch(String::isBlank)) continue;
            int rowNo = r + 1;
            String name = get(cells, col, "full_name");
            String title = firstNonBlank(get(cells, col, "title"), get(cells, col, "degree"));
            String dateText = get(cells, col, "date_conferred");
            String email = get(cells, col, "email");
            String typeText = get(cells, col, "type");

            if (name.isBlank()) errors.add(new RowError(rowNo, "full_name", "Name is required."));
            else if (name.length() > 160) errors.add(new RowError(rowNo, "full_name", "Name is too long."));
            if (title.isBlank()) errors.add(new RowError(rowNo, "title", "Title is required."));
            else if (title.length() > 240) errors.add(new RowError(rowNo, "title", "Title is too long."));
            if (!email.isBlank() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")) errors.add(new RowError(rowNo, "email", "\"" + email + "\" is not a valid email."));
            CredentialType type = defaultType;
            if (!typeText.isBlank()) {
                try {
                    type = CredentialType.valueOf(typeText.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    errors.add(new RowError(rowNo, "type", "Unknown type \"" + typeText + "\". Use one of " + java.util.Arrays.toString(CredentialType.values()) + "."));
                }
            }
            LocalDate date = null;
            try {
                date = LocalDate.parse(dateText.trim());
                if (date.isAfter(LocalDate.now())) errors.add(new RowError(rowNo, "date_conferred", "The date cannot be in the future."));
            } catch (DateTimeParseException e) {
                errors.add(new RowError(rowNo, "date_conferred", "Use the format YYYY-MM-DD."));
            }
            if (!name.isBlank() && !title.isBlank() && date != null && !seen.add((name + "|" + title + "|" + date).toLowerCase(Locale.ROOT))) {
                errors.add(new RowError(rowNo, "full_name", "This row repeats an earlier row in the file."));
            }
            requests.add(new CreateCredentialRequest(type, title, blankNull(get(cells, col, "details", "honors")), null, name,
                    blankNull(get(cells, col, "full_name_am")), blankNull(email), blankNull(get(cells, col, "reference", "student_id")),
                    date == null ? LocalDate.now() : date, requireApproval ? IssueMode.REQUIRE_APPROVAL : IssueMode.SEAL, true));
        }
        if (requests.isEmpty()) throw ApiException.invalid("The file has no data rows.", "file");
        if (!errors.isEmpty()) return new BatchPreview(null, errors.stream().limit(100).toList(), requests.size() - (int) errors.stream().map(RowError::row).distinct().count());

        boolean seal = !requireApproval;
        CredentialBatch batch = batches.save(new CredentialBatch(org.getId(), safeName(filename), requests.size(), actor.userId(),
                seal ? BatchStatus.SEALING : BatchStatus.AWAITING_APPROVAL));
        for (CreateCredentialRequest req : requests) {
            Credential c = issuance.newPending(org, actor.userId(), req, batch.getId()); // rejects duplicates already in the database
            c.setApprovalRequired(requireApproval);
            credentials.save(c);
        }
        audit.record(actor, "Batch uploaded", batch.getFilename() + " · " + requests.size() + " rows" + (requireApproval ? " · awaiting approval" : ""));
        if (seal) afterCommit(() -> sealer.seal(batch.getId(), actor.userId()));
        return new BatchPreview(row(batch, actor.name()), List.of(), requests.size());
    }

    @Transactional
    public BatchRow approve(Actor actor, UUID batchId) {
        if (!actor.role().canApprove()) throw ApiException.forbidden("Only an Approver or Admin can approve a batch.");
        CredentialBatch b = batches.findByIdAndOrganizationId(batchId, actor.organizationId()).orElseThrow(() -> ApiException.notFound("Batch"));
        if (b.getStatus() != BatchStatus.AWAITING_APPROVAL) throw ApiException.conflict("NOT_AWAITING", "This batch is not waiting for approval.");
        if (b.getCreatedBy().equals(actor.userId())) throw ApiException.forbidden("A second person must approve this batch. You uploaded it.");
        b.startSealing(actor.userId());
        batches.save(b);
        audit.record(actor, "Batch approved", b.getFilename() + " · " + b.getTotalRows() + " credentials");
        afterCommit(() -> sealer.seal(b.getId(), actor.userId()));
        return row(b, names(b.getCreatedBy()));
    }

    private String names(UUID id) {
        return users.findById(id).map(AppUser::getFullName).orElse("Former staff member");
    }

    private BatchRow row(CredentialBatch b, String uploader) {
        return new BatchRow(b.getId(), b.getFilename(), b.getTotalRows(), b.getSealedRows(), b.getStatus().name(), uploader, b.getCreatedAt());
    }

    /** Start background work only after the transaction that created it has committed. */
    private void afterCommit(Runnable r) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                r.run();
            }
        });
    }

    private static String get(List<String> cells, Map<String, Integer> col, String... names) {
        for (String n : names) {
            Integer i = col.get(n);
            if (i != null && i < cells.size()) return cells.get(i).trim();
        }
        return "";
    }

    private static String firstNonBlank(String a, String b) {
        return a == null || a.isBlank() ? b : a;
    }

    private static String blankNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String safeName(String name) {
        String n = name == null ? "upload.csv" : name.replaceAll("[^A-Za-z0-9._ -]", "_");
        return n.length() > 120 ? n.substring(0, 120) : n;
    }
}
