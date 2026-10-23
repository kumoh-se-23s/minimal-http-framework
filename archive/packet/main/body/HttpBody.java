package com.minihttp.packet.body;

import com.minihttp.packet.codec.FormUrlEncoded;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public interface HttpBody {
    ByteBuffer raw();

    byte[] bytes();

    int length();

    default boolean isEmpty() {
        return length() == 0;
    }

    String asString(Charset charset);

    default String asString() {
        return asString(StandardCharsets.UTF_8);
    }

    default Map<String, List<String>> asForm() {
        return FormUrlEncoded.decode(asString());
    }

    static HttpBody empty() {
        return ByteArrayHttpBody.EMPTY;
    }

    static HttpBody of(byte[] bytes) {
        return bytes.length == 0 ? empty() : new ByteArrayHttpBody(bytes.clone());
    }
}
