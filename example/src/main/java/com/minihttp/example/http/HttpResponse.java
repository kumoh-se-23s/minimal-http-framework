package com.minihttp.example.http;

import java.nio.charset.StandardCharsets;

public final class HttpResponse {
    private static final byte[] EMPTY = new byte[0];

    private final HttpStatus status;
    private final HttpHeaders headers = new HttpHeaders();
    private final byte[] body;

    private HttpResponse(HttpStatus status, byte[] body) {
        this.status = status;
        this.body = body;
    }

    public static HttpResponse of(HttpStatus status) {
        return new HttpResponse(status, EMPTY);
    }

    public static HttpResponse of(HttpStatus status, byte[] body, String contentType) {
        return new HttpResponse(status, body).header("Content-Type", contentType);
    }

    public static HttpResponse text(HttpStatus status, String text) {
        return of(status, text.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");
    }

    public static HttpResponse ok(String text) {
        return text(HttpStatus.OK, text);
    }

    public static HttpResponse json(HttpStatus status, String json) {
        return of(status, json.getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8");
    }

    public HttpResponse header(String name, String value) {
        headers.set(name, value);
        return this;
    }

    public HttpStatus status() {
        return status;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public byte[] body() {
        return body;
    }
}
