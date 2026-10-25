package com.minihttp.packet;

import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.HttpBody;
import com.minihttp.packet.structure.HttpHeader;
import lombok.NonNull;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public class HttpResponse extends HttpPacket {
    private static final DateTimeFormatter IMF_FIXDATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

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

    @Override
    protected boolean bodyAllowed() {
        return this.status.permitsBody();
    }

    @Override
    protected boolean contentLengthRequired() {
        return this.bodyAllowed();
    }

    @Override
    protected void validate() {

    }

    @Override
    protected Map<String, String> generatedHeaders() {
        if (this.header.contains("Date")) {
            return Map.of();
        }
        return Map.of("Date", IMF_FIXDATE.format(Instant.now()));
    }
}
