package et.mahtem.service;

import et.mahtem.config.MahtemProperties;
import et.mahtem.crypto.Hashing;
import et.mahtem.crypto.KeyVault;
import et.mahtem.crypto.Totp;
import et.mahtem.domain.AppUser;
import et.mahtem.domain.Role;
import et.mahtem.domain.UserStatus;
import et.mahtem.repo.AppUserRepository;
import et.mahtem.util.Actor;
import et.mahtem.util.ApiException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-in, session tokens, two-step verification (TOTP) and invitations. */
@Service
public class AuthService {

    private static final String ISSUER = "mahtem";
    private static final String PURPOSE_CLAIM = "purpose";
    private static final long CHALLENGE_SECONDS = 300;
    private static final String BAD_CREDENTIALS = "That email and password don't match. Check them and try again.";

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final KeyVault vault;
    private final MahtemProperties props;
    private final AuditService audit;
    private final Mailer mailer;
    private final SecureRandom random = new SecureRandom();
    /** Compared against when the email is unknown, so response time does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, JwtEncoder jwtEncoder, JwtDecoder jwtDecoder,
                       KeyVault vault, MahtemProperties props, AuditService audit, Mailer mailer) {
        this.users = users;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.vault = vault;
        this.props = props;
        this.audit = audit;
        this.mailer = mailer;
        this.dummyHash = encoder.encode("mahtem-timing-equaliser");
    }

    /** Either a finished sign-in, or a challenge to be completed with a TOTP code. */
    public record LoginOutcome(AppUser user, String challenge) {
        public boolean needsTwoFactor() { return challenge != null; }
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginOutcome login(String email, String password, String ip) {
        Instant now = Instant.now();
        Optional<AppUser> found = users.findByEmailIgnoreCase(email.trim());
        AppUser user = found.orElse(null);
        boolean passwordOk = encoder.matches(password, user != null && user.getPasswordHash() != null ? user.getPasswordHash() : dummyHash);

        if (user == null || user.getStatus() != UserStatus.ACTIVE) throw badCredentials();
        if (user.isLocked(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOCKED", "Too many failed attempts. Try again in 15 minutes.");
        }
        if (!passwordOk) {
            user.recordFailure(now);
            users.save(user);
            throw badCredentials();
        }
        if (user.isTotpEnabled()) return new LoginOutcome(user, token(user, "2fa", CHALLENGE_SECONDS));
        user.recordSuccess(now);
        users.save(user);
        audit.record(Actor.of(user, ip), "Signed in", user.getEmail());
        return new LoginOutcome(user, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AppUser completeTwoFactor(String challenge, String code, String ip) {
        Jwt jwt = decode(challenge, "2fa");
        AppUser user = users.findById(UUID.fromString(jwt.getSubject())).orElseThrow(AuthService::badCredentials);
        Instant now = Instant.now();
        if (user.getStatus() != UserStatus.ACTIVE || !user.isTotpEnabled()) throw badCredentials();
        if (user.isLocked(now)) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOCKED", "Too many failed attempts. Try again in 15 minutes.");
        byte[] secret = vault.decrypt(user.getTotpSecretEnc(), totpAad(user.getId()));
        if (!Totp.verify(secret, code, now)) {
            user.recordFailure(now);
            users.save(user);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CODE", "That code is not right. Check your authenticator app and try again.",
                    java.util.Map.of("code", "That code is not right."));
        }
        user.recordSuccess(now);
        users.save(user);
        audit.record(Actor.of(user, ip), "Signed in", user.getEmail() + " (two-step)");
        return user;
    }

    /** A session token for the cookie. Role and status are re-read from the database on every request. */
    public String sessionToken(AppUser user) {
        return token(user, "session", props.security().sessionTtl().toSeconds());
    }

    /** Resolves the signed-in user from a session token, or empty if the token or account is no longer good. */
    @Transactional(readOnly = true)
    public Optional<AppUser> userForSession(String token) {
        try {
            Jwt jwt = decode(token, "session");
            return users.findById(UUID.fromString(jwt.getSubject())).filter(u -> u.getStatus() == UserStatus.ACTIVE);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /* ----------------------------------------------------------------------------- two-step setup */

    public record TotpSetup(String secret, String otpauthUri) {}

    @Transactional
    public TotpSetup startTotpSetup(Actor actor, String orgName) {
        AppUser user = require(actor);
        if (user.isTotpEnabled()) throw ApiException.conflict("TOTP_ENABLED", "Two-step verification is already on.");
        byte[] secret = Totp.newSecret();
        user.setTotpSecretEnc(vault.encrypt(secret, totpAad(user.getId())));
        users.save(user);
        return new TotpSetup(Totp.base32(secret), Totp.otpauthUri("Mahtem · " + orgName, user.getEmail(), secret));
    }

    @Transactional
    public void enableTotp(Actor actor, String code) {
        AppUser user = require(actor);
        if (user.getTotpSecretEnc() == null) throw ApiException.conflict("NO_SETUP", "Start the setup first.");
        if (!Totp.verify(vault.decrypt(user.getTotpSecretEnc(), totpAad(user.getId())), code, Instant.now())) {
            throw ApiException.invalid("That code is not right.", "code");
        }
        user.setTotpEnabled(true);
        users.save(user);
        audit.record(actor, "Two-step enabled", user.getEmail());
    }

    @Transactional
    public void disableTotp(Actor actor, String password, String code) {
        AppUser user = require(actor);
        if (!user.isTotpEnabled()) return;
        if (!encoder.matches(password, user.getPasswordHash())) throw ApiException.invalid("That password is not right.", "password");
        if (!Totp.verify(vault.decrypt(user.getTotpSecretEnc(), totpAad(user.getId())), code, Instant.now())) {
            throw ApiException.invalid("That code is not right.", "code");
        }
        user.setTotpEnabled(false);
        user.setTotpSecretEnc(null);
        users.save(user);
        audit.record(actor, "Two-step disabled", user.getEmail());
    }

    /* ---------------------------------------------------------------------------------- invitations */

    public record Invite(AppUser user, String token, String link) {}

    @Transactional
    public Invite invite(Actor actor, String email, String fullName, Role role) {
        actor.require(Role.ADMIN);
        if (users.findByEmailIgnoreCase(email.trim()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Someone with that email already has an account.",
                    java.util.Map.of("email", "Someone with that email already has an account."));
        }
        AppUser u = new AppUser(actor.organizationId(), email, fullName.trim(), role, UserStatus.INVITED);
        String token = randomToken();
        u.setInvite(Hashing.sha256Hex(token), Instant.now().plusSeconds(7 * 86_400L));
        users.save(u);
        String link = props.publicBaseUrl().replaceAll("/+$", "") + "/accept-invite?token=" + token;
        mailer.send(u.getEmail(), "You have been invited to Mahtem", "Hello " + u.getFullName() + ",\nSet your password here (valid 7 days): " + link);
        audit.record(actor, "Member invited", u.getEmail() + " as " + role.name().toLowerCase());
        return new Invite(u, token, link);
    }

    @Transactional
    public AppUser acceptInvite(String token, String password) {
        AppUser u = users.findByInviteTokenHash(Hashing.sha256Hex(token))
                .filter(x -> x.getStatus() == UserStatus.INVITED && x.getInviteExpiresAt() != null && x.getInviteExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.conflict("INVITE_INVALID", "This invitation link is invalid or has expired. Ask an admin for a new one."));
        PasswordPolicy.check(password);
        u.setPasswordHash(encoder.encode(password));
        u.setStatus(UserStatus.ACTIVE);
        u.clearInvite();
        u.recordSuccess(Instant.now());
        users.save(u);
        audit.record(Actor.of(u, null), "Invitation accepted", u.getEmail());
        return u;
    }

    public String hashPassword(String raw) {
        PasswordPolicy.check(raw);
        return encoder.encode(raw);
    }

    /* ------------------------------------------------------------------------------------- helpers */

    private AppUser require(Actor actor) {
        return users.findById(actor.userId()).orElseThrow(() -> ApiException.notFound("User"));
    }

    private String token(AppUser user, String purpose, long ttlSeconds) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER).subject(user.getId().toString()).issuedAt(now).expiresAt(now.plusSeconds(ttlSeconds))
                .claim(PURPOSE_CLAIM, purpose).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private Jwt decode(String token, String purpose) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            if (!purpose.equals(jwt.getClaimAsString(PURPOSE_CLAIM)) || !ISSUER.equals(jwt.getClaimAsString("iss"))) {
                throw ApiException.unauthorized("Your session is not valid. Sign in again.");
            }
            return jwt;
        } catch (JwtException e) {
            throw ApiException.unauthorized("Your session has expired. Sign in again.");
        }
    }

    private String randomToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String totpAad(UUID userId) {
        return "totp:" + userId;
    }

    private static ApiException badCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", BAD_CREDENTIALS);
    }
}
