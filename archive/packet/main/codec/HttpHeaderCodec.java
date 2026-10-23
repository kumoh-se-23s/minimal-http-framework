package com.minihttp.packet.codec;

import com.minihttp.packet.header.HttpHeader;
import lombok.NonNull;

public class HttpHeaderCodec {
    public static String encode(@NonNull HttpHeader header) {
        StringBuilder headerString = new StringBuilder();
        headerString.append(header.)

        return headerString.toString();
    }

    public static HttpHeader decode(@NonNull String maybeHeaderString) {

    }
}
