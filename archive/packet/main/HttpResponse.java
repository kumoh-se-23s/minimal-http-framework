package com.minihttp.packet;

import com.minihttp.packet.body.HttpBody;
import com.minihttp.packet.header.HttpHeader;
import com.minihttp.packet.header.MutableHttpHeader;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public record HttpResponse(
        @NonNull HttpVersion version,
        @NonNull HttpStatus status,
        @NonNull HttpHeader header,
        @NonNull HttpBody body
) implements HttpMessage {
    private static final String TEXT = "text/plain; charset=utf-8";
    private static final String JSON = "application/json; charset=utf-8";

    public HttpResponse {
        if (status == HttpStatus.NO_CONTENT && !body.isEmpty()) {
            throw new IllegalArgumentException("204 No Content must not have a body");
        }

        header = HttpHeader.copyOf(header);
    }

    public static HttpResponseBuilder builder(@NonNull HttpStatus status) {
        return new HttpResponseBuilder(status);
    }

    public static HttpResponse of(HttpStatus status) {
        return new HttpResponse(HttpVersion.HTTP_1_1, status, HttpHeader.empty(), HttpBody.empty());
    }

    public static HttpResponse of(HttpStatus status, byte[] body, String contentType) {
        return of(status).withBody(body, contentType);
    }

    public static HttpResponse text(HttpStatus status, String text) {
        return of(status, text.getBytes(StandardCharsets.UTF_8), TEXT);
    }

    public static HttpResponse json(HttpStatus status, String json) {
        return of(status, json.getBytes(StandardCharsets.UTF_8), JSON);
    }

    public static HttpResponse ok(String text) {
        return text(HttpStatus.OK, text);
    }

    public static HttpResponse noContent() {
        return of(HttpStatus.NO_CONTENT);
    }

    public static HttpResponse notFound() {
        return text(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reason());
    }

    public static HttpResponse error(HttpStatus status, String message) {
        return text(status, message);
    }

    public Optional<String> header(String name) {
        return header.get(name);
    }

    public Optional<String> contentType() {
        return header.get("Content-Type");
    }

    public HttpResponse withVersion(HttpVersion version) {
        return new HttpResponse(version, status, header, body);
    }

    public HttpResponse withStatus(HttpStatus status) {
        return new HttpResponse(version, status, header, body);
    }

    public HttpResponse withHeader(String name, String value) {
        return editHeader(h -> h.set(name, value));
    }

    public HttpResponse addHeader(String name, String value) {
        return editHeader(h -> h.add(name, value));
    }

    public HttpResponse withoutHeader(String name) {
        return editHeader(h -> h.remove(name));
    }

    public HttpResponse withBody(byte[] body, String contentType) {
        MutableHttpHeader edited = MutableHttpHeader.copyOf(header);
        edited.set("Content-Type", contentType);
        return new HttpResponse(version, status, edited, HttpBody.of(body));
    }

    private HttpResponse editHeader(Consumer<MutableHttpHeader> edit) {
        MutableHttpHeader edited = MutableHttpHeader.copyOf(header);
        edit.accept(edited);
        return new HttpResponse(version, status, edited, body);
    }
}
