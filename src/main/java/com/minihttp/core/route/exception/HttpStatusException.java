package com.minihttp.core.route.exception;

import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpStatus;
import lombok.NonNull;

import java.util.Map;

/**
 * 던지면 그 상태 코드로 응답한다. FastAPI의 {@code HTTPException}.
 *
 * <pre>
 *   throw new HttpStatusException(HttpStatus.NOT_FOUND, "Project not found");
 *   → 404 {"detail":"Project not found"}
 * </pre>
 *
 * {@code detail}은 문자열이 아니어도 된다. JSON으로 쓸 수 있는 값이면 그대로 응답 body의 detail에 들어간다.
 */
public class HttpStatusException extends RuntimeException {
    private final HttpStatus status;
    private final Object detail;
    private final Map<HttpHeaderField, String> headers;

    public HttpStatusException(@NonNull HttpStatus status) {
        this(status, status.reason(), Map.of());
    }

    public HttpStatusException(@NonNull HttpStatus status, Object detail) {
        this(status, detail, Map.of());
    }

    public HttpStatusException(@NonNull HttpStatus status, Object detail, @NonNull Map<HttpHeaderField, String> headers) {
        super(String.valueOf(detail), null, false, false);
        this.status = status;
        this.detail = detail;
        this.headers = Map.copyOf(headers);
    }

    public HttpStatus status() {
        return this.status;
    }

    public Object detail() {
        return this.detail;
    }

    public Map<HttpHeaderField, String> headers() {
        return this.headers;
    }
}
