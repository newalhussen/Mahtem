package et.mahtem.web.security;

import et.mahtem.config.MahtemProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sliding-window limiter per client IP for the unauthenticated endpoints that can be hammered:
 * public verification (ID guessing) and sign-in (password guessing). In-memory, so each API instance
 * limits independently; put a shared limiter at the edge for multi-instance deployments.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final int verifyLimit;
    private final int loginLimit;
    private final ApiErrorWriter errors;
    private volatile long lastSweep = System.currentTimeMillis();

    public RateLimitFilter(MahtemProperties props, ApiErrorWriter errors) {
        this.verifyLimit = props.rateLimit().verifyPerMinute();
        this.loginLimit = props.rateLimit().loginPerMinute();
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String path = req.getRequestURI();
        boolean verify = path.startsWith("/api/public/verify");
        boolean auth = path.equals("/api/auth/login") || path.equals("/api/auth/2fa") || path.equals("/api/auth/accept-invite");
        if ((verify || auth) && !allow(req.getRemoteAddr() + "|" + (verify ? "v" : "a"), verify ? verifyLimit : loginLimit)) {
            res.setHeader("Retry-After", "30");
            errors.write(res, 429, "RATE_LIMITED", "Too many requests. Please wait a moment and try again.");
            return;
        }
        chain.doFilter(req, res);
    }

    private boolean allow(String key, int limit) {
        long now = System.currentTimeMillis();
        sweep(now);
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) q.pollFirst();
            if (q.size() >= limit) return false;
            q.addLast(now);
            return true;
        }
    }

    /** Drops idle keys now and then so the map cannot grow without bound. */
    private void sweep(long now) {
        if (now - lastSweep < WINDOW_MS) return;
        lastSweep = now;
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || now - e.getValue().peekLast() > WINDOW_MS;
            }
        });
    }
}
