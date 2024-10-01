package et.mahtem.util;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** A failure the client should see. Rendered as {@code { error, code, fields }} by ApiExceptionHandler. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fields;

    public ApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields == null ? Map.of() : fields;
    }

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Map<String, String> fields() { return fields; }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " could not be found.");
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException invalid(String message, String field) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION", message, Map.of(field, message));
    }
}
