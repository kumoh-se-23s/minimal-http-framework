package com.minihttp.packet.structure;

import java.nio.charset.Charset;

public interface HttpBody {
    byte[] bytes();

    int length();

    default boolean isEmpty() {
        return length() == 0;
    }

    String asString(Charset charset);

    void set(byte[] bytes);

    void append(byte[] bytes);

    void clear();
}
