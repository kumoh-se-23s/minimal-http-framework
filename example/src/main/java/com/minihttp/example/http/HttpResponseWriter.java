package com.minihttp.example.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 응답을 바이트로 직렬화한다. 줄바꿈은 반드시 CRLF.
 *
 * <pre>
 * HTTP/1.1 200 OK\r\n
 * Content-Length: 5\r\n
 * \r\n
 * hello
 * </pre>
 */
public final class HttpResponseWriter {
    private static final String CRLF = "\r\n";

    public void write(HttpResponse response, OutputStream out, boolean keepAlive, boolean includeBody)
            throws IOException {
        HttpStatus status = response.status();
        byte[] body = response.body();

        HttpHeaders headers = new HttpHeaders();
        response.headers().asMap().forEach(headers::set);
        headers.set("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)));
        headers.set("Server", "minihttp-example");
        headers.set("Connection", keepAlive ? "keep-alive" : "close");
        if (status == HttpStatus.NO_CONTENT) {
            headers.remove("Content-Length");
        } else {
            headers.set("Content-Length", String.valueOf(body.length));
        }

        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(status.code()).append(' ').append(status.reason()).append(CRLF);
        for (Map.Entry<String, String> header : headers.asMap().entrySet()) {
            head.append(header.getKey()).append(": ").append(header.getValue()).append(CRLF);
        }
        head.append(CRLF);

        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (includeBody && status != HttpStatus.NO_CONTENT) {
            out.write(body);
        }
        out.flush();
    }
}
