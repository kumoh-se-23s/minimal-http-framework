package com.minihttp.packet;

import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.HttpBody;
import com.minihttp.packet.structure.HttpHeader;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public abstract sealed class HttpPacket permits HttpRequest, HttpResponse {
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

    public HttpBody body() {
        return this.body;
    }

    protected abstract String startLine();

    public byte[] encode() {
        StringBuilder head = new StringBuilder();
        head.append(startLine()).append(CRLF);
        for (String name : this.header.names()) {
            for (String value : this.header.getAll(name)) {
                head.append(name).append(": ").append(value).append(CRLF);
            }
        }
        head.append(CRLF);

        byte[] headBytes = head.toString().getBytes(StandardCharsets.ISO_8859_1);
        byte[] bodyBytes = this.body.bytes();
        byte[] message = Arrays.copyOf(headBytes, headBytes.length + bodyBytes.length);
        System.arraycopy(bodyBytes, 0, message, headBytes.length, bodyBytes.length);
        return message;
    }
}
