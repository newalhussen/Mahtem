package et.mahtem.crypto;

import et.mahtem.domain.Credential;
import et.mahtem.domain.Organization;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The exact bytes that get signed. The record is a JSON object with keys in sorted order and no
 * insignificant whitespace, so the same credential always serialises to the same string.
 * Contact details (the recipient's email) are deliberately left out: a verifier only ever sees
 * what is printed on the certificate.
 */
public final class CanonicalRecord {

    public static final int VERSION = 1;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private CanonicalRecord() {}

    public static String build(Credential c, Organization org, String keyFingerprint, Instant issuedAt) {
        Map<String, Object> issuer = new TreeMap<>();
        issuer.put("code", org.getCode());
        issuer.put("domain", org.getDomain());
        issuer.put("name", org.getName());

        Map<String, Object> recipient = new TreeMap<>();
        recipient.put("name", c.getRecipientName());
        recipient.put("nameAm", nullToEmpty(c.getRecipientNameAm()));
        recipient.put("ref", nullToEmpty(c.getRecipientRef()));

        Map<String, Object> record = new TreeMap<>();
        record.put("v", VERSION);
        record.put("id", c.getPublicId());
        record.put("issuer", issuer);
        record.put("keyFingerprint", keyFingerprint);
        record.put("type", c.getType().name());
        record.put("title", c.getTitle());
        record.put("details", nullToEmpty(c.getDetails()));
        record.put("statement", nullToEmpty(c.getStatement()));
        record.put("recipient", recipient);
        record.put("conferredOn", c.getConferredOn().toString());
        record.put("issuedAt", issuedAt.truncatedTo(ChronoUnit.SECONDS).toString());
        return MAPPER.writeValueAsString(record);
    }

    /**
     * Does the signed payload still describe the credential row? A database edit that changes a visible
     * field without re-signing is caught here even though the stored signature is still mathematically valid.
     */
    public static boolean matches(String payload, Credential c) {
        try {
            JsonNode n = MAPPER.readTree(payload);
            return c.getPublicId().equals(text(n, "id"))
                    && c.getType().name().equals(text(n, "type"))
                    && c.getTitle().equals(text(n, "title"))
                    && nullToEmpty(c.getDetails()).equals(text(n, "details"))
                    && nullToEmpty(c.getStatement()).equals(text(n, "statement"))
                    && c.getRecipientName().equals(n.path("recipient").path("name").asString(""))
                    && nullToEmpty(c.getRecipientNameAm()).equals(n.path("recipient").path("nameAm").asString(""))
                    && nullToEmpty(c.getRecipientRef()).equals(n.path("recipient").path("ref").asString(""))
                    && c.getConferredOn().toString().equals(text(n, "conferredOn"))
                    && c.getIssuedAt() != null
                    && c.getIssuedAt().truncatedTo(ChronoUnit.SECONDS).toString().equals(text(n, "issuedAt"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String text(JsonNode n, String field) {
        return n.path(field).asString("");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
