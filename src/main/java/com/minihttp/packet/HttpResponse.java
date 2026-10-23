package com.minihttp.packet;

import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.HttpBody;
import com.minihttp.packet.structure.HttpHeader;
import lombok.NonNull;

public final class HttpResponse extends HttpPacket {
    private HttpStatus status;

    public HttpResponse(@NonNull HttpVersion version,
                        @NonNull HttpHeader header,
                        @NonNull HttpBody body,
                        @NonNull HttpStatus status) {
        super(version, header, body);
        this.status = status;
    }

    public HttpStatus status() {
        return this.status;
    }

    public void status(@NonNull HttpStatus status) {
        this.status = status;
    }

    @Override
    protected String startLine() {
        return this.version.token() + " " + this.status.code() + " " + this.status.reason();
    }
}
