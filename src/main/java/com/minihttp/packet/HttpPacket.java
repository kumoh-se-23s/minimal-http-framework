package com.minihttp.packet;

import com.minihttp.packet.protocol.ContentType;
import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.HttpBody;
import com.minihttp.packet.structure.HttpHeader;
import lombok.NonNull;

import java.util.Optional;

public abstract class HttpPacket {
    private static final String CRLF = "\r\n";

    protected HttpVersion version;
    protected final HttpHeader header;
    protected final HttpBody body;

    protected HttpPacket(@NonNull HttpVersion version, @NonNull HttpHeader header, @NonNull HttpBody body) {
        this.version = version;
        this.header = header;
        this.body = body;
    }

    public HttpVersion version() {
        return this.version;
    }

    public void version(@NonNull HttpVersion version) {
        this.version = version;
    }

    public HttpHeader header() {
        return this.header;
    }

    public void addHeaderField(HttpHeaderField field, String value) {
        this.header.add(field, value);
    }

    public void setHeaderFieldIfAbsent(HttpHeaderField field, String value) {
        if (!this.header.contains(field)) {
            this.header.set(field, value);
        }
    }

    public void removeHeaderField(HttpHeaderField field) {
        this.header.remove(field);
    }

    public void applyContentLength() {
        if (this.bodyAllowed()) {
            this.header.set(HttpHeaderField.CONTENT_LENGTH, String.valueOf(this.body.length()));
        } else {
            this.removeHeaderField(HttpHeaderField.CONTENT_LENGTH);
        }
    }

    public HttpBody body() {
        return this.body;
    }

    public Optional<String> contentType() {
        return this.header.get(HttpHeaderField.CONTENT_TYPE);
    }

    public void contentType(@NonNull ContentType contentType) {
        this.header.set(HttpHeaderField.CONTENT_TYPE, contentType.headerValue());
    }

    protected abstract String startLine();

    protected abstract boolean bodyAllowed();

    protected abstract boolean contentLengthRequired();

    protected abstract void validate();

    private static void writeField(StringBuilder head, String name, String value) {
        head.append(name).append(": ").append(value).append(CRLF);
    }
}
