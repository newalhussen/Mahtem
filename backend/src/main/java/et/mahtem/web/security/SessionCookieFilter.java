package et.mahtem.web.security;

import et.mahtem.domain.AppUser;
import et.mahtem.service.AuthService;
import et.mahtem.util.Actor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the session cookie, loads the user fresh from the database (so disabling an account or
 * changing a role applies immediately) and authenticates the request with an {@link Actor}.
 */
@Component
public class SessionCookieFilter extends OncePerRequestFilter {

    public static final String COOKIE = "mahtem_session";

    private final AuthService auth;

    public SessionCookieFilter(AuthService auth) {
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String token = cookie(req);
        if (token != null) {
            Optional<AppUser> user = auth.userForSession(token);
            user.ifPresent(u -> {
                Actor actor = Actor.of(u, req.getRemoteAddr());
                var authentication = new UsernamePasswordAuthenticationToken(actor, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        chain.doFilter(req, res);
    }

    private static String cookie(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (Cookie c : req.getCookies()) if (COOKIE.equals(c.getName())) return c.getValue();
        return null;
    }
}
