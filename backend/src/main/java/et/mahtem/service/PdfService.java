package et.mahtem.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfWriter;
import et.mahtem.domain.Signatory;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Renders the A4-landscape certificate from the Mahtem design: a perforated double frame, the seal mark,
 * Amharic and English names, signatories, a signed QR code, the credential ID and a microprint baseline.
 * Layout is expressed in the design's 1123 x 794 px grid and scaled onto the A4 page.
 */
@Service
public class PdfService {

    // Design tokens
    private static final Color PAPER = new Color(0xfb, 0xf9, 0xf7);
    private static final Color INK = new Color(0x20, 0x1e, 0x1d);
    private static final Color WINE = new Color(0x7c, 0x14, 0x05);
    private static final Color MUTED = new Color(0x60, 0x5d, 0x5d);
    private static final Color BODY = new Color(0x44, 0x41, 0x41);
    private static final Color RULE = new Color(0x9b, 0x97, 0x97);

    private static final float DW = 1123f, DH = 794f; // design size in px
    private static final float PW = PageSize.A4.getHeight(), PH = PageSize.A4.getWidth(); // 842 x 595 pt landscape
    private static final float K = PW / DW;

    /** Everything printed on a certificate. */
    public record CertificateData(
            String credentialId, String verifyUrl,
            String institution, String institutionAm, String department, String initials,
            String recipient, String recipientAm, String statement, String title, String details,
            String dateLabel, String conferred, String issued, List<Signatory> signatories, Instant issuedAt) {}

    private record Fonts(BaseFont regular, BaseFont semi, BaseFont extra, BaseFont mono, BaseFont script, BaseFont ethiopic, BaseFont ethiopicSemi) {}

    public byte[] render(CertificateData d) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 0, 0, 0, 0);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setCompressionLevel(9);
            doc.addTitle(d.title() + " — " + d.recipient());
            doc.addAuthor(d.institution());
            doc.addSubject("Mahtem credential " + d.credentialId());
            doc.addKeywords("Mahtem, credential, " + d.credentialId());
            doc.addCreator("Mahtem");
            doc.open();
            draw(writer.getDirectContent(), d, loadFonts());
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Could not render certificate PDF", e);
        }
    }

    /* ----------------------------------------------------------------------------------------- drawing */

    private void draw(PdfContentByte cb, CertificateData d, Fonts f) throws IOException {
        // Paper
        cb.setColorFill(PAPER);
        cb.rectangle(0, 0, PW, PH);
        cb.fill();

        // Double frame: strong outer rule, softer inner rule.
        strokeRect(cb, 22, 22, DW - 44, DH - 44, 2f, WINE, 1f);
        strokeRect(cb, 28, 28, DW - 56, DH - 56, 1f, WINE, 0.45f);

        final float left = 64, right = DW - 64, top = 58;

        // Header: initials block, institution, certificate number, seal mark.
        strokeRect(cb, left, top, 72, 72, 2f, INK, 1f);
        text(cb, f.extra, 20, d.initials(), left + 36, top + 36 + 7, INK, 0, Align.CENTER);

        float nx = left + 72 + 20;
        text(cb, f.extra, 24, d.institution().toUpperCase(), nx, top + 24, INK, 0.06f, Align.LEFT);
        if (notBlank(d.institutionAm())) text(cb, f.ethiopicSemi, 17, d.institutionAm(), nx, top + 50, INK, 0, Align.LEFT);
        if (notBlank(d.department())) text(cb, f.regular, 13, d.department(), nx, top + 72, MUTED, 0, Align.LEFT);

        float sealX = right - 64;
        drawSeal(cb, sealX, top);
        text(cb, f.regular, 10, "CERTIFICATE NO.", sealX - 14, top + 22, MUTED, 0.14f, Align.RIGHT);
        text(cb, f.mono, 14, d.credentialId(), sealX - 14, top + 42, INK, 0, Align.RIGHT);

        // Rule under the header.
        float ruleY = top + 72 + 26;
        fillRect(cb, left, ruleY, right - left, 2, INK);

        // Bottom grid geometry (needed first so the centre block can be centred between rule and grid).
        float gridTop = DH - 52 - 120;
        float unit = (right - left) / 5f;
        float c1 = left, c2 = c1 + unit, c3 = c2 + unit * 1.25f, c4 = c3 + unit * 1.25f;

        // Centre block: stacked lines vertically centred in the free space.
        List<String> statementLines = wrap(f.regular, 16, d.statement(), 640);
        float h1 = 23, hName = 62, hAm = notBlank(d.recipientAm()) ? 34 : 0;
        float hStmt = 14 + statementLines.size() * 23, hTitle = 37, hDetails = notBlank(d.details()) ? 25 : 0;
        int gaps = 3 + (hAm > 0 ? 1 : 0) + (hDetails > 0 ? 1 : 0);
        float total = h1 + hName + hAm + hStmt + hTitle + hDetails + gaps * 6;
        float y = ruleY + 2 + ((gridTop - ruleY - 2) - total) / 2;

        text(cb, f.regular, 16, "This is to certify that", left, y + 17, BODY, 0, Align.LEFT);
        y += h1 + 6;
        float nameSize = fit(f.extra, d.recipient(), 62, right - left);
        text(cb, f.extra, nameSize, d.recipient(), left, y + nameSize * 0.82f, INK, -0.025f, Align.LEFT);
        y += hName + 6;
        if (hAm > 0) {
            text(cb, f.ethiopic, 22, d.recipientAm(), left, y + 24, BODY, 0, Align.LEFT);
            y += hAm + 6;
        }
        y += 14;
        for (String line : statementLines) {
            text(cb, f.regular, 16, line, left, y + 17, BODY, 0, Align.LEFT);
            y += 23;
        }
        y += 6;
        float titleSize = fit(f.semi, d.title(), 32, right - left);
        text(cb, f.semi, titleSize, d.title(), left, y + titleSize * 0.85f, WINE, -0.01f, Align.LEFT);
        y += hTitle + 6;
        if (hDetails > 0) text(cb, f.semi, 16, d.details(), left, y + 17, INK, 0, Align.LEFT);

        // Bottom grid.
        fillRect(cb, left, gridTop, right - left, 2, INK);
        fillRect(cb, c2 - 0.5f, gridTop + 2, 1, 118, RULE);
        fillRect(cb, c3 - 0.5f, gridTop + 2, 1, 118, RULE);
        fillRect(cb, c4 - 0.5f, gridTop + 2, 1, 118, RULE);

        float cy = gridTop + 2 + 14;
        text(cb, f.regular, 10, d.dateLabel().toUpperCase(), c1, cy + 9, MUTED, 0.12f, Align.LEFT);
        text(cb, f.semi, 16, d.conferred(), c1, cy + 29, INK, 0, Align.LEFT);
        text(cb, f.regular, 10, "ISSUED", c1, cy + 52, MUTED, 0.12f, Align.LEFT);
        text(cb, f.semi, 16, d.issued(), c1, cy + 72, INK, 0, Align.LEFT);

        List<Signatory> sigs = d.signatories().size() > 2 ? d.signatories().subList(0, 2) : d.signatories();
        float[] sigX = {c2, c3};
        for (int i = 0; i < sigs.size(); i++) {
            float sx = sigX[i] + 16, sw = unit * 1.25f - 32;
            Signatory s = sigs.get(i);
            float script = fit(f.script, s.name(), 44, sw);
            text(cb, f.script, script, s.name(), sx, DH - 52 - 33 - 6 - 12 + 4, new Color(0x2d, 0x2b, 0x2b), 0, Align.LEFT);
            fillRect(cb, sx, DH - 52 - 33, sw, 1, INK);
            text(cb, f.semi, 13, shorten(f.semi, s.name(), 13, sw), sx, DH - 52 - 33 + 20, INK, 0, Align.LEFT);
            text(cb, f.regular, 11, shorten(f.regular, s.title(), 11, sw), sx, DH - 52 - 33 + 34, MUTED, 0, Align.LEFT);
        }

        // QR block.
        float qx = c4 + 16, qy = gridTop + 2 + 14;
        drawQr(cb, d.verifyUrl(), qx, qy, 104);
        float tx = qx + 104 + 14, tw = right - tx;
        text(cb, f.extra, 13, "Scan to verify", tx, qy + 13, INK, 0, Align.LEFT);
        float ty = qy + 31;
        for (String line : wrap(f.regular, 11, "Point a phone camera at the code. No app needed.", tw)) {
            text(cb, f.regular, 11, line, tx, ty, BODY, 0, Align.LEFT);
            ty += 15.4f;
        }
        ty += 4;
        for (String line : breakChars(f.mono, 10, d.verifyUrl().replaceFirst("^https?://", "").replaceFirst("[?].*$", ""), tw)) {
            text(cb, f.mono, 10, line, tx, ty + 8, WINE, 0, Align.LEFT);
            ty += 14;
        }

        // Microprint security line along the bottom edge.
        StringBuilder micro = new StringBuilder();
        String unitText = "MAHTEM SEALED RECORD · " + d.credentialId() + " · ";
        float microSize = 5.5f, spacing = 0.18f;
        float perUnit = f.mono.getWidthPoint(unitText, microSize * K) + unitText.length() * spacing * microSize * K;
        int repeats = (int) Math.ceil(((DW - 60) * K) / perUnit);
        for (int i = 0; i < repeats; i++) micro.append(unitText);
        cb.saveState();
        cb.rectangle(30 * K, 0, (DW - 60) * K, PH);
        cb.clip();
        cb.newPath();
        PdfGState gs = new PdfGState();
        gs.setFillOpacity(0.7f);
        cb.setGState(gs);
        text(cb, f.mono, microSize, micro.toString(), 30, DH - 31, WINE, spacing, Align.LEFT);
        cb.restoreState();
    }

    private void drawSeal(PdfContentByte cb, float x, float y) {
        // Dashed stamp edge
        cb.saveState();
        cb.setColorStroke(WINE);
        cb.setLineWidth(2 * K);
        cb.setLineDash(3 * K, 2.2f * K, 0);
        cb.rectangle((x + 1) * K, PH - (y + 63) * K, 62 * K, 62 * K);
        cb.stroke();
        cb.restoreState();
        // Solid block
        fillRect(cb, x + 8, y + 8, 48, 48, WINE);
        // M monogram in relief
        cb.saveState();
        cb.setColorStroke(PAPER);
        cb.setLineWidth(4 * K);
        cb.setLineJoin(PdfContentByte.LINE_JOIN_MITER);
        cb.moveTo((x + 20) * K, PH - (y + 44) * K);
        cb.lineTo((x + 20) * K, PH - (y + 21) * K);
        cb.lineTo((x + 32) * K, PH - (y + 34) * K);
        cb.lineTo((x + 44) * K, PH - (y + 21) * K);
        cb.lineTo((x + 44) * K, PH - (y + 44) * K);
        cb.stroke();
        cb.restoreState();
    }

    private void drawQr(PdfContentByte cb, String url, float x, float y, float size) {
        BitMatrix m;
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            m = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0, hints);
        } catch (com.google.zxing.WriterException e) {
            throw new IllegalStateException("QR encoding failed", e);
        }
        fillRect(cb, x, y, size, size, PAPER);
        float cell = size / m.getWidth();
        cb.saveState();
        cb.setColorFill(INK);
        for (int row = 0; row < m.getHeight(); row++) {
            for (int col = 0; col < m.getWidth(); col++) {
                if (m.get(col, row)) cb.rectangle((x + col * cell) * K, PH - (y + (row + 1) * cell) * K, cell * K + 0.15f, cell * K + 0.15f);
            }
        }
        cb.fill();
        cb.restoreState();
        strokeRect(cb, x, y, size, size, 1f, INK, 1f);
    }

    /* ----------------------------------------------------------------------------------------- helpers */

    private enum Align { LEFT, CENTER, RIGHT }

    /** Draws text with the baseline at design-pixel y. {@code trackingEm} is letter-spacing in em. */
    private void text(PdfContentByte cb, BaseFont font, float sizePx, String s, float x, float baselineY, Color color,
                      float trackingEm, Align align) {
        if (s == null || s.isEmpty()) return;
        float size = sizePx * K;
        float spacing = trackingEm * size;
        float w = font.getWidthPoint(s, size) + spacing * s.length();
        float px = x * K;
        if (align == Align.CENTER) px -= w / 2;
        if (align == Align.RIGHT) px -= w - spacing; // trailing spacing is not visible
        cb.saveState();
        cb.beginText();
        cb.setFontAndSize(font, size);
        cb.setColorFill(color);
        cb.setCharacterSpacing(spacing);
        cb.setTextMatrix(px, PH - baselineY * K);
        cb.showText(s);
        cb.endText();
        cb.restoreState();
    }

    private float fit(BaseFont font, String s, float maxSizePx, float maxWidthPx) {
        float size = maxSizePx;
        while (size > 12 && font.getWidthPoint(s, size * K) / K > maxWidthPx) size -= 1;
        return size;
    }

    private String shorten(BaseFont font, String s, float sizePx, float maxWidthPx) {
        if (s == null) return "";
        String out = s;
        while (out.length() > 1 && font.getWidthPoint(out, sizePx * K) / K > maxWidthPx) out = out.substring(0, out.length() - 1);
        return out.length() < s.length() ? out.stripTrailing() + "…" : out;
    }

    private List<String> wrap(BaseFont font, float sizePx, String s, float maxWidthPx) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.getWidthPoint(candidate, sizePx * K) / K > maxWidthPx && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private List<String> breakChars(BaseFont font, float sizePx, String s, float maxWidthPx) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (font.getWidthPoint(line.toString() + c, sizePx * K) / K > maxWidthPx && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            line.append(c);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private void fillRect(PdfContentByte cb, float x, float y, float w, float h, Color c) {
        cb.saveState();
        cb.setColorFill(c);
        cb.rectangle(x * K, PH - (y + h) * K, w * K, h * K);
        cb.fill();
        cb.restoreState();
    }

    /** Strokes inside the given box (like a CSS border), with optional opacity. */
    private void strokeRect(PdfContentByte cb, float x, float y, float w, float h, float widthPx, Color c, float opacity) {
        cb.saveState();
        PdfGState gs = new PdfGState();
        gs.setStrokeOpacity(opacity);
        cb.setGState(gs);
        cb.setColorStroke(c);
        cb.setLineWidth(widthPx * K);
        float half = widthPx / 2;
        cb.rectangle((x + half) * K, PH - (y + h - half) * K, (w - widthPx) * K, (h - widthPx) * K);
        cb.stroke();
        cb.restoreState();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private Fonts loadFonts() throws IOException {
        return new Fonts(
                font("Archivo-Regular.ttf"), font("Archivo-SemiBold.ttf"), font("Archivo-ExtraBold.ttf"),
                font("JetBrainsMono-SemiBold.ttf"), font("MrsSaintDelafield-Regular.ttf"),
                font("NotoSansEthiopic-Regular.ttf"), font("NotoSansEthiopic-SemiBold.ttf"));
    }

    private BaseFont font(String file) throws IOException {
        try (InputStream in = PdfService.class.getResourceAsStream("/fonts/" + file)) {
            if (in == null) throw new IOException("Missing font resource " + file);
            return BaseFont.createFont(file, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, in.readAllBytes(), null);
        } catch (DocumentException e) {
            throw new IOException("Could not load font " + file, e);
        }
    }
}
