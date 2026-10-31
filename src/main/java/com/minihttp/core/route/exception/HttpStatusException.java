package com.minihttp.core.route.exception;

import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpStatus;

import java.util.Map;

public class HttpStatusException extends RuntimeException {
    private final HttpStatus status;
    private final Map<HttpHeaderField, String> headers;

    public HttpStatusException(HttpStatus status, String message) {
        this(status, message, Map.of());
    }

    public HttpStatusException(HttpStatus status, String message, Map<HttpHeaderField, String> headers) {
        super(message, null, false, false);
        this.status = status;
        this.headers = Map.copyOf(headers);
    }

    public HttpStatus status() {
        return this.status;
    }

    public Map<HttpHeaderField, String> headers() {
        return this.headers;
    }
}
