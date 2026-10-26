package com.minihttp.packet;

import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.structure.HttpHeader;
import lombok.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * HTTP/1.1 200 OK\r\n
 * Content-Length: 5\r\n
 * \r\n
 * hello
 */
public class HttpPacketWriter {
    private static final String CRLF = "\r\n";
    public static void write(@NonNull HttpResponse response, @NonNull OutputStream outputStream) throws IOException {
        StringBuilder headerString = new StringBuilder(response.startLine() + CRLF);
        HttpHeader header = HttpPacketWriter.applyDefaultHeader(response);

        Map<String, List<String>> headerMap = header.asMap();

        headerMap.forEach(
                (key, values) -> values.forEach(
                        value -> headerString.append(key).append(": ").append(value).append(CRLF)
                )
        );

        headerString.append(CRLF); // header/body 구분자
        outputStream.write(headerString.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (response.bodyAllowed()) {
            outputStream.write(response.body().bytes());
        }
        outputStream.flush();
    }

    private static HttpHeader applyDefaultHeader(@NonNull HttpResponse response) {
        HttpHeader header = response.header();

        response.setHeaderFieldIfAbsent(HttpHeaderField.SERVER, "minihttp");

        response.generatedHeaders().forEach(header::set);

        response.applyContentLength();

        return header;
    }

}
