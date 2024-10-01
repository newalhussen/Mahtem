package et.mahtem.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 time-based one-time passwords (HMAC-SHA1, 30 s step, 6 digits), as used by authenticator apps. */
public final class Totp {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    public static byte[] newSecret() {
        byte[] secret = new byte[20];
        RANDOM.nextBytes(secret);
        return secret;
    }

    public static String code(byte[] secret, Instant at) {
        return code(secret, at.getEpochSecond() / STEP_SECONDS);
    }

    static String code(byte[] secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = h[h.length - 1] & 0x0f;
            int bin = ((h[offset] & 0x7f) << 24) | ((h[offset + 1] & 0xff) << 16) | ((h[offset + 2] & 0xff) << 8) | (h[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 unavailable", e);
        }
    }

    /** Accepts the current code and one step either side to tolerate clock drift. Constant-time compare. */
    public static boolean verify(byte[] secret, String submitted, Instant now) {
        if (submitted == null || !submitted.matches("\\d{6}")) return false;
        long counter = now.getEpochSecond() / STEP_SECONDS;
        boolean ok = false;
        for (long c = counter - 1; c <= counter + 1; c++) {
            ok |= MessageDigest.isEqual(code(secret, c).getBytes(StandardCharsets.US_ASCII), submitted.getBytes(StandardCharsets.US_ASCII));
        }
        return ok;
    }

    public static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    public static String otpauthUri(String issuer, String account, byte[] secret) {
        return "otpauth://totp/" + enc(issuer) + ":" + enc(account) + "?secret=" + base32(secret) + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
