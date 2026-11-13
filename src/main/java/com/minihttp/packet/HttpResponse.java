package com.minihttp.packet;

import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.HttpBody;
import com.minihttp.packet.structure.DefaultHttpBody;
import com.minihttp.packet.structure.DefaultHttpHeader;
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

    public HttpResponse(@NonNull HttpStatus status) {
        this(HttpVersion.HTTP_1_1, new DefaultHttpHeader(), new DefaultHttpBody(), status);
    }

    public final HttpStatus status() {
        return this.status;
    }

    public final void status(@NonNull HttpStatus status) {
        this.status = status;
    }

    public final void setCloseHeader() {
        this.header.set(HttpHeaderField.CONNECTION, "close");
    }

    @Override
    protected final String startLine() {
        return this.version.token() + " " + this.status.code() + " " + this.status.reason();
    }

    @Override
    protected final boolean bodyAllowed() {
        return this.status.permitsBody();
    }

    @Override
    protected final boolean contentLengthRequired() {
        return this.bodyAllowed();
    }

    @Override
    protected final void validate() {

    }

    protected final Map<String, String> generatedHeaders() {
        if (this.header.contains(HttpHeaderField.DATE)) {
            return Map.of();
        }
        return Map.of(HttpHeaderField.DATE.fieldName(), IMF_FIXDATE.format(Instant.now()));
    }
}
