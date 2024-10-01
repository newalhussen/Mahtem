package et.mahtem.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed, validated application settings (see application.yml). */
@ConfigurationProperties(prefix = "mahtem")
public record MahtemProperties(
        String publicBaseUrl,
        Security security,
        RateLimit rateLimit,
        boolean requireVerifiedDomain,
        Seed seed) {

    public record Security(
            String masterKey,
            String jwtSecret,
            Duration sessionTtl,
            boolean cookieSecure,
            List<String> allowedOrigins,
            String ipSalt) {}

    public record RateLimit(int verifyPerMinute, int loginPerMinute) {}

    public record Seed(boolean demoData) {}

    /** Fails fast at startup if a secret is missing or too weak, instead of failing on first use. */
    public MahtemProperties {
        if (security == null || security.masterKey() == null || security.masterKey().isBlank()) {
            throw new IllegalStateException("MAHTEM_MASTER_KEY is required (32 random bytes, base64).");
        }
        if (security.jwtSecret() == null || security.jwtSecret().length() < 32) {
            throw new IllegalStateException("MAHTEM_JWT_SECRET is required and must be at least 32 characters.");
        }
        if (security.ipSalt() == null || security.ipSalt().length() < 8) {
            throw new IllegalStateException("MAHTEM_IP_SALT is required (8+ characters).");
        }
    }
}
