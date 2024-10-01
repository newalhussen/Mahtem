package et.mahtem.web.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes the standard {@code { error, code }} body from filters, where controller advice does not apply. */
@Component
public class ApiErrorWriter {

    private final JsonMapper mapper = JsonMapper.builder().build();

    public void write(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write(mapper.writeValueAsString(java.util.Map.of("error", message, "code", code)));
    }
}
