package com.minihttp.packet;

import com.minihttp.packet.structure.HttpHeader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * HTTP/1.1 200 OK\r\n
 * Content-Length: 5\r\n
 * \r\n
 * hello
 */
public class HttpPacketWriter {
    private static final String CRLF = "\r\n";

    public static void write(HttpResponse response, OutputStream outputStream) throws IOException {
        StringBuilder packetString = new StringBuilder(response.startLine());
        HttpHeader header = response.header();

        for (String headerKey : header.asMap().keySet()) {
            packetString.append(headerKey)
                        .append(": ")
                        .append(header.get(headerKey))
                        .append(CRLF);
        }

        packetString.append(CRLF); // header/body 구분자

        if (response.bodyAllowed()) {
            packetString.append(response.body().asString(StandardCharsets.UTF_8));
        }

        outputStream.write(packetString.toString().getBytes(StandardCharsets.ISO_8859_1));
        outputStream.flush();
    }

}
