package com.minihttp.packet;

import com.minihttp.packet.body.HttpBody;
import com.minihttp.packet.header.MutableHttpHeader;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import lombok.NonNull;

public final class HttpResponseBuilder {
    private final HttpStatus status;
    private HttpVersion version = HttpVersion.HTTP_1_1;
    private final MutableHttpHeader header = new MutableHttpHeader();
    private HttpBody body = HttpBody.empty();

    public HttpResponseBuilder(HttpStatus status) {
        this.status = status;
    }

    public HttpResponseBuilder version(@NonNull HttpVersion version) {
        this.version = version;
        return this;
    }

    public HttpResponseBuilder header(@NonNull String name, @NonNull String value) {
        header.add(name, value);
        return this;
    }

    public HttpResponseBuilder body(@NonNull HttpBody body) {
        this.body = body;
        return this;
    }

    public HttpResponseBuilder body(@NonNull byte[] body, @NonNull String contentType) {
        header.set("Content-Type", contentType);
        return body(HttpBody.of(body));
    }

    public HttpResponse build() {
        return new HttpResponse(version, status, header, body);
    }
}
