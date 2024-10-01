package et.mahtem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import et.mahtem.domain.AppUser;
import et.mahtem.domain.CredentialStatus;
import et.mahtem.domain.CredentialType;
import et.mahtem.domain.Role;
import et.mahtem.domain.VerificationResult;
import et.mahtem.domain.VerifyMethod;
import et.mahtem.repo.AppUserRepository;
import et.mahtem.service.AuditService;
import et.mahtem.service.IssuanceService;
import et.mahtem.service.QueryService;
import et.mahtem.service.VerificationService;
import et.mahtem.util.Actor;
import et.mahtem.util.ApiException;
import et.mahtem.web.dto.CredentialDtos.CreateCredentialRequest;
import et.mahtem.web.dto.CredentialDtos.IssueMode;
import et.mahtem.web.dto.CredentialDtos.RevokeRequest;
import et.mahtem.web.dto.PublicDtos.VerificationResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs the real services against a scratch PostgreSQL database ("mahtem_test") loaded with the demo data:
 * sealing, public verification, revocation, tamper detection, tenant isolation and role checks.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5433/mahtem_test",
        "mahtem.seed.demo-data=true",
        "mahtem.security.master-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "mahtem.security.jwt-secret=test-secret-test-secret-test-secret-123456",
        "mahtem.security.ip-salt=test-salt",
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LifecycleIT {

    static final String SEALED = "MHT-AAU-26-K7Q4-8TZ2";
    static final String REVOKED = "MHT-AAU-26-ST9B-62KY";
    static final String PENDING = "MHT-AAU-26-PH4K-9XRT";

    @Autowired IssuanceService issuance;
    @Autowired VerificationService verification;
    @Autowired QueryService query;
    @Autowired AuditService audit;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;

    private Actor actor(String email) {
        AppUser u = users.findByEmailIgnoreCase(email).orElseThrow();
        return Actor.of(u, "127.0.0.1");
    }

    private VerificationResponse check(String id) {
        return verification.verify(id, VerifyMethod.API, new VerificationService.Client("203.0.113.9", null));
    }

    private CreateCredentialRequest request(IssueMode mode) {
        return new CreateCredentialRequest(CredentialType.TRAINING, "Certificate in Testing", null, null, "Test Person", null, null, null,
                LocalDate.of(2026, 5, 1), mode, false);
    }

    @Test
    @Order(1)
    void sealedCredentialVerifiesAndRevokedOneSaysSo() {
        VerificationResponse ok = check(SEALED);
        assertThat(ok.status()).isEqualTo(VerificationResult.VALID);
        assertThat(ok.checks()).allMatch(c -> c.state().name().equals("PASS"));
        assertThat(ok.credential().recipientName()).isEqualTo("Selamawit Tesfaye Bekele");

        VerificationResponse revoked = check(REVOKED);
        assertThat(revoked.status()).isEqualTo(VerificationResult.REVOKED);
        assertThat(revoked.revocation().reason()).isEqualTo("Issued in error");
    }

    @Test
    @Order(2)
    void unknownMalformedAndPendingIdsAreNotVerified() {
        assertThat(check("MHT-AAU-26-K7Q4-8TZ9").status()).isEqualTo(VerificationResult.INVALID);
        assertThat(check("garbage").status()).isEqualTo(VerificationResult.INVALID);
        // an unsealed credential is indistinguishable from one that does not exist
        VerificationResponse pending = check(PENDING);
        assertThat(pending.status()).isEqualTo(VerificationResult.INVALID);
        assertThat(pending.credential()).isNull();
    }

    @Test
    @Order(3)
    void issueThenRevokeIsReflectedImmediately() {
        Actor hirut = actor("hirut.gebre@aau.edu.et");
        String id = issuance.create(hirut, request(IssueMode.SEAL)).getPublicId();
        assertThat(check(id).status()).isEqualTo(VerificationResult.VALID);

        String code = id.substring(id.lastIndexOf('-') + 1);
        assertThatThrownBy(() -> issuance.revoke(hirut, id, new RevokeRequest("Issued in error", null, false, "WRONG")))
                .isInstanceOf(ApiException.class);
        issuance.revoke(hirut, id, new RevokeRequest("Issued in error", "Test", false, code));
        assertThat(check(id).status()).isEqualTo(VerificationResult.REVOKED);
        assertThatThrownBy(() -> issuance.revoke(hirut, id, new RevokeRequest("Issued in error", null, false, code)))
                .as("revoking twice").isInstanceOf(RuntimeException.class);
    }

    @Test
    @Order(4)
    void databaseTamperingTurnsAValidCredentialInvalid() {
        Actor hirut = actor("hirut.gebre@aau.edu.et");
        String id = issuance.create(hirut, request(IssueMode.SEAL)).getPublicId();
        assertThat(check(id).status()).isEqualTo(VerificationResult.VALID);

        // someone with database access renames the recipient without being able to re-sign
        jdbc.update("update credential set recipient_name = 'Someone Else' where public_id = ?", id);
        VerificationResponse tampered = check(id);
        assertThat(tampered.status()).isEqualTo(VerificationResult.INVALID);
        assertThat(tampered.invalidCause().name()).isEqualTo("TAMPERED");
    }

    @Test
    @Order(5)
    void staffCannotSeeOrTouchAnotherOrganizationsCredentials() {
        Actor gondar = actor("tigist.worku@uog.edu.et");
        assertThatThrownBy(() -> query.detail(gondar, SEALED)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> issuance.revoke(gondar, SEALED, new RevokeRequest("Other", null, false, "8TZ2")))
                .isInstanceOf(ApiException.class);
        assertThat(query.list(gondar, new et.mahtem.web.dto.ConsoleDtos.CredentialFilter("Selamawit", null, null, null, null), 0, 20).items()).isEmpty();
    }

    @Test
    @Order(6)
    void viewersCannotIssueAndSecondApproverIsRequired() {
        Actor viewer = actor("selam.h@aau.edu.et");
        assertThatThrownBy(() -> issuance.create(viewer, request(IssueMode.SEAL))).isInstanceOf(ApiException.class);

        Actor bereket = actor("bereket.assefa@aau.edu.et");
        String id = issuance.create(bereket, request(IssueMode.REQUIRE_APPROVAL)).getPublicId();
        assertThat(check(id).status()).isEqualTo(VerificationResult.INVALID);
        assertThatThrownBy(() -> issuance.seal(bereket, id)).as("creator cannot approve their own").isInstanceOf(ApiException.class);

        Actor meron = actor("meron.abebe@aau.edu.et");
        issuance.seal(meron, id);
        assertThat(check(id).status()).isEqualTo(VerificationResult.VALID);
        assertThat(query.detail(meron, id).summary().status()).isEqualTo(CredentialStatus.VALID);
    }

    @Test
    @Order(7)
    void staffActionLogChainIsIntactAndDetectsEdits() {
        UUID org = actor("hirut.gebre@aau.edu.et").organizationId();
        assertThat(audit.verifyChain(org).intact()).isTrue();
        jdbc.update("update audit_event set detail = 'edited' where organization_id = ? and seq = 2", org);
        assertThat(audit.verifyChain(org).intact()).isFalse();
        assertThat(audit.verifyChain(org).brokenAtSeq()).isEqualTo(2L);
    }

    @Test
    @Order(8)
    void adminRoleHasIssuePermission() {
        assertThat(actor("hirut.gebre@aau.edu.et").role()).isEqualTo(Role.ADMIN);
        assertThat(Role.VIEWER.canIssue()).isFalse();
    }
}
