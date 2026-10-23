package com.minihttp.packet.structure;

import lombok.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

public final class DefaultHttpBody implements HttpBody {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public DefaultHttpBody() {
    }

    public DefaultHttpBody(byte[] bytes) {
        this.buffer.writeBytes(bytes);
    }

    @Override
    public byte[] bytes() {
        return this.buffer.toByteArray();
    }

    @Override
    public int length() {
        return this.buffer.size();
    }

    @Override
    public String asString(@NonNull Charset charset) {
        return this.buffer.toString(charset);
    }

    @Override
    public void set(byte[] bytes) {
        this.buffer.reset();
        this.buffer.writeBytes(bytes);
    }

    @Override
    public void append(byte[] bytes) {
        this.buffer.writeBytes(bytes);
    }

    @Override
    public void clear() {
        this.buffer.reset();
    }
}
