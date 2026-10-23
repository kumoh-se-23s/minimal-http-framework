package com.minihttp.example.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpResponseWriterTest {
    private final HttpResponseWriter writer = new HttpResponseWriter();

    @Test
    void writesStatusLineHeadersAndBody() throws IOException {
        String raw = write(HttpResponse.ok("안녕"), true, true);

        assertTrue(raw.startsWith("HTTP/1.1 200 OK\r\n"));
        assertTrue(raw.contains("Content-Length: 6\r\n"), "Content-Length is in bytes");
        assertTrue(raw.contains("Content-Type: text/plain; charset=utf-8\r\n"));
        assertTrue(raw.contains("Connection: keep-alive\r\n"));
        assertTrue(raw.endsWith("\r\n\r\n안녕"));
    }

    @Test
    void omitsBodyButKeepsContentLengthForHead() throws IOException {
        String raw = write(HttpResponse.ok("hello"), false, false);

        assertTrue(raw.contains("Content-Length: 5\r\n"));
        assertTrue(raw.contains("Connection: close\r\n"));
        assertTrue(raw.endsWith("\r\n\r\n"));
    }

    @Test
    void noContentHasNoContentLength() throws IOException {
        String raw = write(HttpResponse.of(HttpStatus.NO_CONTENT), true, true);

        assertTrue(raw.startsWith("HTTP/1.1 204 No Content\r\n"));
        assertFalse(raw.contains("Content-Length"));
    }

    private String write(HttpResponse response, boolean keepAlive, boolean includeBody) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(response, out, keepAlive, includeBody);
        return out.toString(StandardCharsets.UTF_8);
    }
}
