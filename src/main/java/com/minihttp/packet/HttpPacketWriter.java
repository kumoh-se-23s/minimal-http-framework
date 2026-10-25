package com.minihttp.packet;

import com.minihttp.packet.structure.HttpHeader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HTTP/1.1 200 OK\r\n
 * Content-Length: 5\r\n
 * \r\n
 * hello
 */
public class HttpPacketWriter {
    private static final String CRLF = "\r\n";
    //TODO CRLF 검증 좀더 확실하게, Content-Length를 기재하도록 수정하기
    public static void write(HttpResponse response, OutputStream outputStream) throws IOException {
        StringBuilder headerString = new StringBuilder(response.startLine() + CRLF);
        HttpHeader header = response.header();
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

}
