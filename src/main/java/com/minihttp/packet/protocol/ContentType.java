package com.minihttp.packet.protocol;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

public enum ContentType {
    TEXT_PLAIN("text/plain", StandardCharsets.UTF_8),

    APPLICATION_JSON("application/json", null),
    APPLICATION_FORM_URLENCODED("application/x-www-form-urlencoded", null),
    APPLICATION_OCTET_STREAM("application/octet-stream", null),
    MULTIPART_FORM_DATA("multipart/form-data", null),

    IMAGE_PNG("image/png", null),
    IMAGE_JPEG("image/jpeg", null);

    private final String mimeType;
    private final Charset charset;

    ContentType(String mimeType, Charset charset) {
        this.mimeType = mimeType;
        this.charset = charset;
    }

    public String mimeType() {
        return this.mimeType;
    }

    public Optional<Charset> charset() {
        return Optional.ofNullable(this.charset);
    }

    public String headerValue() {
        if (this.charset == null) {
            return this.mimeType;
        }
        return this.mimeType + "; charset=" + this.charset.name().toLowerCase(Locale.ROOT);
    }
}
