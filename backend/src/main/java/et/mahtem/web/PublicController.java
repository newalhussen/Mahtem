package et.mahtem.web;

import et.mahtem.domain.VerifyMethod;
import et.mahtem.service.VerificationService;
import et.mahtem.web.dto.PublicDtos.LedgerEntry;
import et.mahtem.web.dto.PublicDtos.VerificationResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated endpoints: this is what employers hit after scanning the QR code. */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final VerificationService verification;

    public PublicController(VerificationService verification) {
        this.verification = verification;
    }

    /** {@code via=qr} when the visitor arrived by scanning, otherwise an ID lookup. Every call is logged for the issuer. */
    @GetMapping("/verify/{id}")
    public ResponseEntity<VerificationResponse> verify(@PathVariable String id, @RequestParam(defaultValue = "lookup") String via, HttpServletRequest req) {
        VerifyMethod method = "qr".equalsIgnoreCase(via) ? VerifyMethod.QR : "api".equalsIgnoreCase(via) ? VerifyMethod.API : VerifyMethod.ID_LOOKUP;
        VerificationResponse body = verification.verify(id, method, new VerificationService.Client(req.getRemoteAddr(), location(req)));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping("/credentials/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String id) {
        byte[] bytes = verification.publicPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(id.toUpperCase(Locale.ROOT) + ".pdf").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(bytes);
    }

    @GetMapping("/ledger")
    public List<LedgerEntry> ledger() {
        return verification.ledger();
    }

    /** City and country as supplied by the hosting edge (Cloudflare / Vercel); null when not available. */
    private static String location(HttpServletRequest req) {
        String country = first(req.getHeader("CF-IPCountry"), req.getHeader("X-Vercel-IP-Country"));
        String city = req.getHeader("X-Vercel-IP-City");
        if (country == null || country.equals("XX")) return null;
        String c = city == null ? null : java.net.URLDecoder.decode(city, java.nio.charset.StandardCharsets.UTF_8);
        return c == null || c.isBlank() ? country : c + ", " + country;
    }

    private static String first(String a, String b) {
        return a != null && !a.isBlank() ? a : b != null && !b.isBlank() ? b : null;
    }
}
