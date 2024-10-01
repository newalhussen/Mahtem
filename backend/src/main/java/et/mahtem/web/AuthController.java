package et.mahtem.web;

import et.mahtem.config.MahtemProperties;
import et.mahtem.domain.AppUser;
import et.mahtem.service.AuthService;
import et.mahtem.service.OrganizationService;
import et.mahtem.util.Actor;
import et.mahtem.web.dto.ConsoleDtos.Me;
import et.mahtem.web.dto.OrgDtos.AcceptInviteRequest;
import et.mahtem.web.dto.OrgDtos.DisableTotpRequest;
import et.mahtem.web.dto.OrgDtos.EnableTotpRequest;
import et.mahtem.web.dto.OrgDtos.LoginRequest;
import et.mahtem.web.dto.OrgDtos.TwoFactorRequest;
import et.mahtem.web.security.SessionCookieFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final OrganizationService org;
    private final MahtemProperties props;

    public AuthController(AuthService auth, OrganizationService org, MahtemProperties props) {
        this.auth = auth;
        this.org = org;
        this.props = props;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest body, HttpServletRequest req, HttpServletResponse res) {
        AuthService.LoginOutcome out = auth.login(body.email(), body.password(), req.getRemoteAddr());
        if (out.needsTwoFactor()) return Map.of("status", "TWO_FACTOR_REQUIRED", "challenge", out.challenge());
        startSession(res, out.user());
        return Map.of("status", "SIGNED_IN");
    }

    @PostMapping("/2fa")
    public Map<String, Object> twoFactor(@Valid @RequestBody TwoFactorRequest body, HttpServletRequest req, HttpServletResponse res) {
        AppUser user = auth.completeTwoFactor(body.challenge(), body.code(), req.getRemoteAddr());
        startSession(res, user);
        return Map.of("status", "SIGNED_IN");
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
        return Map.of("ok", true);
    }

    /** The signed-in user, or {@code {user:null}} for visitors. The frontend calls this on every page. */
    @GetMapping("/me")
    public Map<String, Object> me() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> out = new HashMap<>();
        if (a != null && a.getPrincipal() instanceof Actor actor) {
            Me me = org.me(actor);
            out.put("user", me);
        } else {
            out.put("user", null);
        }
        return out;
    }

    @PostMapping("/accept-invite")
    public Map<String, Object> acceptInvite(@Valid @RequestBody AcceptInviteRequest body, HttpServletResponse res) {
        AppUser user = auth.acceptInvite(body.token(), body.password());
        startSession(res, user);
        return Map.of("status", "SIGNED_IN");
    }

    @PostMapping("/2fa/setup")
    public AuthService.TotpSetup setup(Actor actor) {
        return auth.startTotpSetup(actor, org.me(actor).organization().name());
    }

    @PostMapping("/2fa/enable")
    public Map<String, Object> enable(Actor actor, @Valid @RequestBody EnableTotpRequest body) {
        auth.enableTotp(actor, body.code());
        return Map.of("ok", true);
    }

    @PostMapping("/2fa/disable")
    public Map<String, Object> disable(Actor actor, @Valid @RequestBody DisableTotpRequest body) {
        auth.disableTotp(actor, body.password(), body.code());
        return Map.of("ok", true);
    }

    private void startSession(HttpServletResponse res, AppUser user) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(auth.sessionToken(user), props.security().sessionTtl()).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(SessionCookieFilter.COOKIE, value)
                .httpOnly(true).secure(props.security().cookieSecure()).sameSite("Lax").path("/").maxAge(maxAge).build();
    }
}
