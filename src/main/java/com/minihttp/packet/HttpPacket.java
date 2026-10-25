package com.minihttp.packet;

import com.minihttp.packet.protocol.ContentType;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.HttpBody;
import com.minihttp.packet.structure.HttpHeader;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public abstract class HttpPacket {
    private static final String CRLF = "\r\n";
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final String TRANSFER_ENCODING = "Transfer-Encoding";

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

    public HttpBody body() {
        return this.body;
    }

    public Optional<String> contentType() {
        return this.header.get("Content-Type");
    }

    public void contentType(@NonNull ContentType contentType) {
        this.header.set("Content-Type", contentType.headerValue());
    }

    protected abstract String startLine();

    protected boolean bodyAllowed() {
        return true;
    }

    protected boolean contentLengthRequired() {
        return true;
    }

    protected void validate() {
    }

    protected Map<String, String> generatedHeaders() {
        return Map.of();
    }

    private static void writeField(StringBuilder head, String name, String value) {
        head.append(name).append(": ").append(value).append(CRLF);
    }
}
