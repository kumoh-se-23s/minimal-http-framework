package com.minihttp.packet.protocol;

public class HttpParseException extends RuntimeException {
    private final HttpStatus status;

    public HttpParseException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return this.status;
    }
}
