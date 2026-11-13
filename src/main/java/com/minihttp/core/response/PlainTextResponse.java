package com.minihttp.core.response;

import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.ContentType;
import com.minihttp.packet.protocol.HttpStatus;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;

public class PlainTextResponse extends HttpResponse {

    public PlainTextResponse(@NonNull String text) {
        this(HttpStatus.OK, text);
    }

    public PlainTextResponse(@NonNull HttpStatus status, @NonNull String text) {
        super(status);
        this.body().set(text.getBytes(StandardCharsets.UTF_8));
        this.contentType(ContentType.TEXT_PLAIN);
    }
}
