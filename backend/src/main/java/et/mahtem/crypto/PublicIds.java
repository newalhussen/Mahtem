package et.mahtem.crypto;

import java.security.SecureRandom;
import java.time.Year;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Credential IDs such as MHT-AAU-26-K7Q4-8TZ2: issuer code, two-digit year and 8 random characters
 * (about 40 bits). The alphabet drops 0/O, 1/I/L so an ID can be read aloud or typed from a printout.
 */
public final class PublicIds {

    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern FORMAT = Pattern.compile("^MHT-[A-Z0-9]{2,8}-\\d{2}-[A-Z0-9]{4}-[A-Z0-9]{4}$");

    private PublicIds() {}

    public static String next(String orgCode, Year year) {
        return "MHT-" + orgCode.toUpperCase(Locale.ROOT) + "-" + String.format("%02d", year.getValue() % 100) + "-" + chunk() + "-" + chunk();
    }

    /** Normalises what a person typed (case, spaces, en/em dashes) before looking it up. */
    public static String normalize(String input) {
        if (input == null) return "";
        return input.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s\\u2010-\\u2015\\u2212]+", "-");
    }

    public static boolean isWellFormed(String id) {
        return id != null && FORMAT.matcher(id).matches();
    }

    private static String chunk() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
