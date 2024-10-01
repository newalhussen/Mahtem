package et.mahtem.seed;

import et.mahtem.config.MahtemProperties;
import et.mahtem.crypto.Hashing;
import et.mahtem.domain.AppUser;
import et.mahtem.domain.Credential;
import et.mahtem.domain.CredentialType;
import et.mahtem.domain.Organization;
import et.mahtem.domain.Role;
import et.mahtem.domain.Signatory;
import et.mahtem.domain.UserStatus;
import et.mahtem.domain.VerificationLog;
import et.mahtem.domain.VerificationResult;
import et.mahtem.domain.VerifyMethod;
import et.mahtem.repo.AppUserRepository;
import et.mahtem.repo.CredentialRepository;
import et.mahtem.repo.OrganizationRepository;
import et.mahtem.repo.VerificationLogRepository;
import et.mahtem.service.AuditService;
import et.mahtem.service.IssuanceService;
import et.mahtem.service.KeyService;
import et.mahtem.web.dto.CredentialDtos.CreateCredentialRequest;
import et.mahtem.web.dto.CredentialDtos.IssueMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads four fictional-but-plausible Ethiopian issuers with signed credentials, a verification history and a
 * staff action log. Everything is created through the real services, so demo credentials are genuinely signed.
 * Runs only when {@code MAHTEM_SEED_DEMO=true} and the database is empty.
 */
@Component
@ConditionalOnProperty(name = "mahtem.seed.demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Mahtem#2026";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final ZoneId ADDIS = ZoneId.of("Africa/Addis_Ababa");

    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final CredentialRepository credentials;
    private final VerificationLogRepository logs;
    private final IssuanceService issuance;
    private final KeyService keys;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final MahtemProperties props;
    private final Random rnd = new Random(2026);

    public DemoDataSeeder(OrganizationRepository organizations, AppUserRepository users, CredentialRepository credentials,
                          VerificationLogRepository logs, IssuanceService issuance, KeyService keys, AuditService audit,
                          PasswordEncoder encoder, MahtemProperties props) {
        this.organizations = organizations;
        this.users = users;
        this.credentials = credentials;
        this.logs = logs;
        this.issuance = issuance;
        this.keys = keys;
        this.audit = audit;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (organizations.count() > 0) {
            log.info("Demo seed skipped: the database already has organizations.");
            return;
        }
        log.info("Seeding demo data…");
        String hash = encoder.encode(DEMO_PASSWORD);

        Organization aau = org("AAU", "Addis Ababa University", "አዲስ አበባ ዩኒቨርሲቲ", "AAU", "College of Natural & Computational Sciences", "aau.edu.et",
                "Ministry of Education, FDRE", "registrar.verification@aau.edu.et", true,
                new Signatory("Dr. Meron Abebe", "University Registrar"), new Signatory("Prof. Tadesse Worku", "President"));
        AppUser hirut = user(aau, "hirut.gebre@aau.edu.et", "Hirut Gebre", Role.ADMIN, UserStatus.ACTIVE, hash);
        AppUser meron = user(aau, "meron.abebe@aau.edu.et", "Dr. Meron Abebe", Role.APPROVER, UserStatus.ACTIVE, hash);
        AppUser bereket = user(aau, "bereket.assefa@aau.edu.et", "Bereket Assefa", Role.ISSUER, UserStatus.ACTIVE, hash);
        user(aau, "selam.h@aau.edu.et", "Selam Haile", Role.VIEWER, UserStatus.ACTIVE, hash);
        users.save(invited(aau, "yared.tesfaye@aau.edu.et", "Yared Tesfaye", Role.ISSUER));

        Organization emi = org("EMI", "Ethiopian Management Institute", "የኢትዮጵያ ሥራ አመራር ኢንስቲትዩት", "EMI", "Centre for Leadership Development", "emi.edu.et",
                "Ministry of Education, FDRE", "certificates@emi.edu.et", true,
                new Signatory("Hana Tadesse", "Director of Training"), new Signatory("Dr. Girma Wolde", "Executive Director"));
        AppUser hana = user(emi, "hana.tadesse@emi.edu.et", "Hana Tadesse", Role.ADMIN, UserStatus.ACTIVE, hash);

        Organization uog = org("UOG", "University of Gondar", "ጎንደር ዩኒቨርሲቲ", "UoG", "Office of the Registrar", "uog.edu.et",
                "Ministry of Education, FDRE", "registrar@uog.edu.et", true,
                new Signatory("Dr. Tigist Worku", "University Registrar"), new Signatory("Prof. Abebe Kassa", "President"));
        uog.setAllowPdfDownload(false);
        organizations.save(uog);
        AppUser tigist = user(uog, "tigist.worku@uog.edu.et", "Tigist Worku", Role.ADMIN, UserStatus.ACTIVE, hash);

        Organization klp = org("KLP", "Kebena Logistics PLC", "ከበና ሎጂስቲክስ", "KLP", "Human Resources and Fleet Safety", "kebena.et",
                "Ethiopian Transport Authority", "hr@kebena.et", true,
                new Signatory("Rahel Mulugeta", "Head of Human Resources"), new Signatory("Eng. Samuel Desta", "Chief Executive Officer"));
        AppUser rahel = user(klp, "rahel.mulugeta@kebena.et", "Rahel Mulugeta", Role.ADMIN, UserStatus.ACTIVE, hash);

        Instant july22 = ZonedDateTime.of(2026, 7, 22, 9, 14, 3, 0, ADDIS).toInstant();
        List<Credential> sealed = new ArrayList<>();

        /* ---- Addis Ababa University: degrees (names and IDs follow the design) ---- */
        String[][] aauRows = {
                {"Selamawit Tesfaye Bekele", "ሰላማዊት ተስፋዬ በቀለ", "Bachelor of Science in Computer Science", "With Great Distinction · CGPA 3.81", "MHT-AAU-26-K7Q4-8TZ2"},
                {"Abel Girma Wolde", "አቤል ግርማ ወልዴ", "Bachelor of Science in Software Engineering", "With Distinction", "MHT-AAU-26-P2LM-07QE"},
                {"Hanna Mekonnen Desta", "ሐና መኮንን ደስታ", "Master of Science in Data Science", "", "MHT-AAU-26-D5TA-3XNH"},
                {"Yonas Bekele Tadesse", "ዮናስ በቀለ ታደሰ", "Bachelor of Science in Statistics", "With Distinction", "MHT-AAU-26-ST9B-62KY"},
                {"Liya Solomon Fikre", "ሊያ ሰሎሞን ፍቅሬ", "Master of Science in Mathematics", "", "MHT-AAU-26-MA2L-9QSF"},
                {"Biruk Tesfaye Gebremedhin", "ብሩክ ተስፋዬ ገብረመድኅን", "Bachelor of Science in Chemistry", "With Distinction", "MHT-AAU-26-CH7B-14GM"},
                {"Ruth Daniel Asfaw", "ሩት ዳንኤል አስፋው", "Master of Science in Computer Science", "", "MHT-AAU-26-CS3R-80DA"},
                {"Tsion Kebede Ayele", "ጽዮን ከበደ አየለ", "Doctor of Philosophy in Computational Science", "", "MHT-AAU-26-PD1T-55KA"},
                {"Eyerusalem Tadesse Worku", "ኢየሩሳሌም ታደሰ ወርቁ", "Bachelor of Science in Geology", "With Very Great Distinction", "MHT-AAU-26-GE6E-27TW"},
                {"Meseret Alemayehu Bogale", "መሰረት አለማየሁ ቦጋለ", "Bachelor of Science in Biology", "With Distinction", "MHT-AAU-26-BI5M-40AB"},
                {"Dawit Mulugeta Hailu", "ዳዊት ሙሉጌታ ኃይሉ", "Bachelor of Science in Physics", "With Distinction", null},
                {"Saron Berhanu Tilahun", "ሳሮን በርሃኑ ጥላሁን", "Bachelor of Science in Mathematics", "", null},
                {"Nardos Yohannes Assefa", "ናርዶስ ዮሐንስ አሰፋ", "Master of Science in Statistics", "", null},
                {"Fitsum Getachew Lemma", "ፍጹም ጌታቸው ለማ", "Bachelor of Science in Computer Science", "", null},
                {"Mahlet Zerihun Kebede", "ማህሌት ዘሪሁን ከበደ", "Bachelor of Science in Chemistry", "With Great Distinction", null},
                {"Henok Wondimu Abate", "ሄኖክ ወንድሙ አባተ", "Bachelor of Science in Software Engineering", "", null},
                {"Bethlehem Ayalew Negash", "ቤተልሔም አያሌው ነጋሽ", "Master of Science in Computer Science", "", null},
                {"Kaleb Mengistu Tsegaye", "ካሌብ መንግስቱ ጸጋዬ", "Bachelor of Science in Biology", "With Distinction", null},
                {"Rediet Fikadu Mamo", "ረድኤት ፍቅርአዲስ ማሞ", "Bachelor of Science in Geology", "", null},
                {"Abenezer Haile Mariam", "አብርሃም ኃይለ ማርያም", "Bachelor of Science in Physics", "", null},
                {"Selamawit Girma Teshome", "ሰላማዊት ግርማ ተሾመ", "Bachelor of Science in Statistics", "With Distinction", null},
                {"Yohannes Abera Desalegn", "ዮሐንስ አበራ ደሳለኝ", "Master of Science in Mathematics", "", null},
        };
        int i = 0;
        for (String[] r : aauRows) {
            Instant at = july22.plusSeconds(i * 41L);
            Credential c = issuance.importHistorical(aau, hirut.getId(), req(CredentialType.DEGREE, r[2], r[3], r[0], r[1], r[0].split(" ")[0].toLowerCase() + "." + r[0].split(" ")[1].toLowerCase() + "@aau.edu.et",
                    "UGR/" + (3000 + i * 97) + "/14", LocalDate.of(2026, 7, 15), IssueMode.SEAL), at, true, r[4]);
            sealed.add(c);
            i++;
        }
        // Yonas was revoked on 2 Sep (issued in error), as in the design.
        Credential yonas = credentials.findByPublicId("MHT-AAU-26-ST9B-62KY").orElseThrow();
        yonas.revoke(ZonedDateTime.of(2026, 9, 2, 11, 47, 0, 0, ADDIS).toInstant(), hirut.getId(), "Issued in error", "Replaced by a corrected credential. Contact the Registrar for the current record.");
        credentials.save(yonas);

        // Two credentials waiting for a second approver (Physics batch).
        pending(aau, bereket, "Kidus Haile Mariam", "ቅዱስ ኃይለ ማርያም", "Bachelor of Science in Physics", "With Great Distinction", "MHT-AAU-26-PH4K-9XRT");
        pending(aau, bereket, "Nahom Getachew Alemu", "ናሆም ጌታቸው አለሙ", "Bachelor of Science in Physics", "", "MHT-AAU-26-PH8N-3WQD");
        // And a draft the registrar saved for later.
        issuance.importHistorical(aau, hirut.getId(), req(CredentialType.DEGREE, "Master of Science in Physics", "", "Zelalem Tamiru Gebre", "ዘላለም ታምሩ ገብሬ", "zelalem.tamiru@aau.edu.et",
                "PGR/2210/15", LocalDate.of(2026, 7, 15), IssueMode.DRAFT), Instant.now().minusSeconds(3 * 86_400L), false, "MHT-AAU-26-MP2Z-6HKA");

        /* ---- Ethiopian Management Institute: training certificates and certifications ---- */
        Instant march = ZonedDateTime.of(2026, 4, 2, 10, 5, 0, 0, ADDIS).toInstant();
        String[][] emiRows = {
                {"Rahel Getnet Ayalew", "ራሔል ጌትነት አያሌው", "Certificate in Public Sector Project Management", "120 contact hours · Grade A", "MHT-EMI-26-PM08-3RGA", "TRAINING"},
                {"Mulugeta Bekele Haile", "ሙሉጌታ በቀለ ኃይሌ", "Certificate in Public Sector Project Management", "120 contact hours · Grade B", null, "TRAINING"},
                {"Almaz Desta Tefera", "አልማዝ ደስታ ተፈራ", "Certificate in Strategic Leadership", "80 contact hours · Grade A", null, "TRAINING"},
                {"Tewodros Alemu Getahun", "ቴዎድሮስ አለሙ ጌታሁን", "Certificate in Strategic Leadership", "80 contact hours · Grade B", null, "TRAINING"},
                {"Birtukan Hailu Mekuria", "ብርቱካን ኃይሉ መኩሪያ", "Certified Public Procurement Professional", "Level 2", null, "CERTIFICATION"},
                {"Getachew Wondwosen Lema", "ጌታቸው ወንድወሰን ለማ", "Certified Public Procurement Professional", "Level 1", null, "CERTIFICATION"},
                {"Meskerem Yilma Asres", "መስከረም ይልማ አስረስ", "Certificate in Financial Management for Managers", "60 contact hours · Grade A", null, "TRAINING"},
                {"Zerihun Teshome Kidane", "ዘሪሁን ተሾመ ኪዳኔ", "Certificate in Financial Management for Managers", "60 contact hours · Grade A", null, "TRAINING"},
        };
        i = 0;
        for (String[] r : emiRows) {
            sealed.add(issuance.importHistorical(emi, hana.getId(), req(CredentialType.valueOf(r[5]), r[2], r[3], r[0], r[1], null, "EMI/" + (410 + i), LocalDate.of(2026, 3, 28), IssueMode.SEAL),
                    march.plusSeconds(i * 53L), true, r[4]));
            i++;
        }

        /* ---- University of Gondar ---- */
        Instant aug18 = ZonedDateTime.of(2026, 8, 18, 14, 20, 0, 0, ADDIS).toInstant();
        Credential dawit = issuance.importHistorical(uog, tigist.getId(), req(CredentialType.DEGREE, "Master of Public Health (MPH)", "", "Dawit Alemu Haile", "ዳዊት አለሙ ኃይሌ", "dawit.alemu@uog.edu.et",
                "PGR/1187/15", LocalDate.of(2026, 8, 10), IssueMode.SEAL), aug18, true, "MHT-UOG-26-3MPH-41AC");
        dawit.revoke(ZonedDateTime.of(2026, 9, 2, 9, 30, 0, 0, ADDIS).toInstant(), tigist.getId(), "Issued in error",
                "Replaced by a corrected credential. Contact the Registrar for the current record.");
        credentials.save(dawit);
        sealed.add(dawit);
        String[][] uogRows = {
                {"Tsegaye Lemma Worku", "Doctor of Medicine (MD)", "With Distinction"},
                {"Helen Abraham Tsehay", "Bachelor of Science in Nursing", ""},
                {"Amanuel Kiros Gebre", "Bachelor of Science in Public Health", "With Great Distinction"},
        };
        i = 0;
        for (String[] r : uogRows) {
            sealed.add(issuance.importHistorical(uog, tigist.getId(), req(CredentialType.DEGREE, r[1], r[2], r[0], null, null, "UOG/" + (800 + i), LocalDate.of(2026, 8, 10), IssueMode.SEAL),
                    aug18.plusSeconds(60 + i * 37L), true, null));
            i++;
        }

        /* ---- Kebena Logistics: employment, award, certification, licence ---- */
        Instant jun = ZonedDateTime.of(2026, 6, 12, 8, 40, 0, 0, ADDIS).toInstant();
        sealed.add(issuance.importHistorical(klp, rahel.getId(), req(CredentialType.EMPLOYMENT, "Senior Fleet Operations Manager", "Full-time · Since 2022", "Samson Berhane Mitiku", "ሳምሶን ብርሃኔ ሚቲኩ", null,
                "KLP-E-0412", LocalDate.of(2022, 3, 1), IssueMode.SEAL), jun, true, null));
        sealed.add(issuance.importHistorical(klp, rahel.getId(), req(CredentialType.AWARD, "Driver of the Year 2025", "Zero incidents across 210,000 km", "Gizachew Tadele Wolde", "ግዛቸው ታደለ ወልዴ", null,
                "KLP-E-1180", LocalDate.of(2026, 1, 20), IssueMode.SEAL), jun.plusSeconds(90), true, null));
        sealed.add(issuance.importHistorical(klp, rahel.getId(), req(CredentialType.CERTIFICATION, "Hazardous Goods Transport Safety", "Valid for 3 years", "Eden Solomon Kassahun", "ኤደን ሰለሞን ካሳሁን", null,
                "KLP-E-0877", LocalDate.of(2026, 2, 14), IssueMode.SEAL), jun.plusSeconds(180), true, null));
        sealed.add(issuance.importHistorical(klp, rahel.getId(), req(CredentialType.LICENSE, "Heavy Vehicle Operator (Class C)", "Internal operating licence", "Abdi Mohammed Hassen", "አብዲ መሐመድ ሐሰን", null,
                "KLP-E-0951", LocalDate.of(2026, 4, 3), IssueMode.SEAL), jun.plusSeconds(270), true, null));

        for (Organization o : List.of(aau, emi, uog, klp)) keys.activeKey(o);
        history(aau, hirut, meron, bereket, july22);
        history(emi, hana, null, null, march);
        history(uog, tigist, null, null, aug18);
        history(klp, rahel, null, null, jun);
        verificationHistory(sealed);
        log.info("Demo data ready: 4 organizations, {} sealed credentials. Sign in as hirut.gebre@aau.edu.et / {}", sealed.size(), DEMO_PASSWORD);
    }

    /* ----------------------------------------------------------------------------------------------- helpers */

    private Organization org(String code, String name, String nameAm, String initials, String dept, String domain, String accreditation,
                             String contact, boolean pdfDownload, Signatory... signatories) {
        Organization o = new Organization(code, name, initials, domain, token());
        o.setNameAm(nameAm);
        o.setDepartment(dept);
        o.setAccreditationBody(accreditation);
        o.setVerifierContact(contact);
        o.setAllowPdfDownload(pdfDownload);
        o.setSignatories(List.of(signatories));
        o.markDomainVerified(Instant.now().minusSeconds(40 * 86_400L));
        return organizations.save(o);
    }

    private AppUser user(Organization org, String email, String name, Role role, UserStatus status, String hash) {
        AppUser u = new AppUser(org.getId(), email, name, role, status);
        u.setPasswordHash(hash);
        u.recordSuccess(Instant.now().minusSeconds(rnd.nextInt(3 * 86_400) + 600L));
        return users.save(u);
    }

    private AppUser invited(Organization org, String email, String name, Role role) {
        AppUser u = new AppUser(org.getId(), email, name, role, UserStatus.INVITED);
        u.setInvite(Hashing.sha256Hex("demo-invite-" + email), Instant.now().plusSeconds(7 * 86_400L));
        return u;
    }

    private CreateCredentialRequest req(CredentialType type, String title, String details, String name, String nameAm, String email, String ref,
                                        LocalDate date, IssueMode mode) {
        return new CreateCredentialRequest(type, title, details == null || details.isBlank() ? null : details, null, name, nameAm, email, ref, date, mode, false);
    }

    private void pending(Organization org, AppUser creator, String name, String nameAm, String title, String details, String id) {
        Credential c = issuance.importHistorical(org, creator.getId(),
                req(CredentialType.DEGREE, title, details, name, nameAm, name.split(" ")[0].toLowerCase() + "@aau.edu.et", "UGR/" + (4000 + rnd.nextInt(900)) + "/14",
                        LocalDate.of(2026, 7, 15), IssueMode.REQUIRE_APPROVAL), Instant.now().minusSeconds(3 * 86_400L), false, id);
        c.setCreatedAt(Instant.now().minusSeconds(3 * 86_400L));
        credentials.save(c);
    }

    private void history(Organization org, AppUser admin, AppUser approver, AppUser issuer, Instant sealedAt) {
        UUID orgId = org.getId();
        Instant start = Instant.now().minusSeconds(120 * 86_400L);
        audit.recordHistorical(orgId, null, "System", "Key rotated", org.getCode() + " signing key activated", start);
        audit.recordHistorical(orgId, admin.getId(), admin.getFullName(), "Domain verified", org.getDomain(), start.plusSeconds(3600));
        if (issuer != null) audit.recordHistorical(orgId, admin.getId(), admin.getFullName(), "Member invited", "selam.h@aau.edu.et as viewer", sealedAt.minusSeconds(60 * 86_400L));
        if (approver != null) audit.recordHistorical(orgId, approver.getId(), approver.getFullName(), "Batch approved", "Second approval", sealedAt.minusSeconds(60));
        audit.recordHistorical(orgId, admin.getId(), admin.getFullName(), "Batch sealed", "Class of 2026 · credentials sealed and emailed", sealedAt);
        if (org.getCode().equals("AAU")) {
            audit.recordHistorical(orgId, admin.getId(), admin.getFullName(), "Credential revoked", "MHT-AAU-26-ST9B-62KY · Issued in error",
                    ZonedDateTime.of(2026, 9, 2, 11, 47, 0, 0, ADDIS).toInstant());
            audit.recordHistorical(orgId, issuer.getId(), issuer.getFullName(), "Batch uploaded", "CNCS_Class2026_Physics.csv · 212 rows · awaiting approval",
                    Instant.now().minusSeconds(3 * 86_400L));
        }
    }

    private static final String[] PLACES = {"Addis Ababa, ET", "Addis Ababa, ET", "Addis Ababa, ET", "Hawassa, ET", "Mekelle, ET", "Nairobi, KE", "Dubai, AE",
            "Dublin, IE", "Frankfurt, DE", "Lagos, NG", "Toronto, CA", "London, GB", "Riyadh, SA"};

    /** About a month of public checks: mostly valid, a few revoked, and unknown or mistyped IDs. */
    private void verificationHistory(List<Credential> sealed) {
        Instant now = Instant.now();
        SecureRandom sr = new SecureRandom();
        for (Credential c : sealed) {
            int n = c.getRecipientName().length() % 7 + (c.getPublicId().contains("K7Q4") ? 14 : 1);
            for (int k = 0; k < n; k++) {
                Instant at = now.minusSeconds((long) (rnd.nextDouble() * rnd.nextDouble() * 30 * 86_400L));
                VerificationResult r = c.getRevokedAt() != null && at.isAfter(c.getRevokedAt()) ? VerificationResult.REVOKED : VerificationResult.VALID;
                logs.save(new VerificationLog(c.getId(), c.getOrganizationId(), c.getPublicId(), r, rnd.nextInt(100) < 72 ? VerifyMethod.QR : VerifyMethod.ID_LOOKUP,
                        PLACES[rnd.nextInt(PLACES.length)], Hashing.sha256Hex(props.security().ipSalt() + "|" + rnd.nextInt(60)), receipt(at, sr), at));
            }
        }
        // IDs that were never issued (forgeries or typos), attributed to the organization named in the ID.
        List<Organization> orgs = organizations.findAll();
        for (int k = 0; k < 14; k++) {
            Organization o = orgs.get(rnd.nextInt(orgs.size()));
            Instant at = now.minusSeconds((long) (rnd.nextDouble() * 20 * 86_400L));
            String bogus = "MHT-" + o.getCode() + "-26-" + randomChunk() + "-" + randomChunk();
            logs.save(new VerificationLog(null, o.getId(), bogus, VerificationResult.INVALID, VerifyMethod.QR, PLACES[rnd.nextInt(PLACES.length)],
                    Hashing.sha256Hex(props.security().ipSalt() + "|x" + k), receipt(at, sr), at));
        }
    }

    private String receipt(Instant at, SecureRandom sr) {
        byte[] b = new byte[3];
        sr.nextBytes(b);
        return "VR-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(at.atZone(ADDIS)) + "-" + HexFormat.of().withUpperCase().formatHex(b);
    }

    private String randomChunk() {
        String a = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append(a.charAt(rnd.nextInt(a.length())));
        return sb.toString();
    }

    private String token() {
        byte[] b = new byte[12];
        rnd.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
