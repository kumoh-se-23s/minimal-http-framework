package com.minihttp.packet.body;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

final class ByteArrayHttpBody implements HttpBody {
    static final ByteArrayHttpBody EMPTY = new ByteArrayHttpBody(new byte[0]);

    private final byte[] bytes;

    ByteArrayHttpBody(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public ByteBuffer raw() {
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public int length() {
        return bytes.length;
    }

    @Override
    public String asString(Charset charset) {
        return new String(bytes, charset);
    }
}
