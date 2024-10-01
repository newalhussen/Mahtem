package et.mahtem.web.security;

import et.mahtem.config.MahtemProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cross-site request forgery defence in depth: a browser always sends an Origin header on cross-site
 * form posts, so any state-changing request whose Origin is not an allowed frontend is refused.
 * (Requests without an Origin, such as curl or server-to-server calls, carry no browser cookies to abuse.)
 */
@Component
public class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");

    private final Set<String> allowed;
    private final ApiErrorWriter errors;

    public OriginCheckFilter(MahtemProperties props, ApiErrorWriter errors) {
        this.allowed = Set.copyOf(props.security().allowedOrigins());
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String origin = req.getHeader("Origin");
        if (!SAFE.contains(req.getMethod()) && origin != null && !allowed.contains(origin)) {
            errors.write(res, 403, "BAD_ORIGIN", "That request came from a site we do not trust.");
            return;
        }
        chain.doFilter(req, res);
    }
}
