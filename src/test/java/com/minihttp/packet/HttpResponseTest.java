package com.minihttp.packet;

import com.minihttp.packet.protocol.ContentType;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.DefaultHttpBody;
import com.minihttp.packet.structure.DefaultHttpHeader;
import com.minihttp.packet.structure.HttpHeader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpResponseTest {

    private static HttpResponse response(HttpStatus status, String body) {
        HttpHeader header = new DefaultHttpHeader();
        header.set("Date", "Fri, 23 Oct 2026 00:00:00 GMT");
        return new HttpResponse(HttpVersion.HTTP_1_1, header,
                new DefaultHttpBody(body.getBytes(StandardCharsets.UTF_8)), status);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    void encodesStatusLineHeadersContentLengthAndBody() {
        HttpResponse response = response(HttpStatus.OK, "안녕");
        response.contentType(ContentType.TEXT_PLAIN);

        assertEquals("HTTP/1.1 200 OK\r\n"
                        + "Date: Fri, 23 Oct 2026 00:00:00 GMT\r\n"
                        + "Content-Type: text/plain; charset=utf-8\r\n"
                        + "Content-Length: 6\r\n"
                        + "\r\n"
                        + "안녕",
                text(response.encode()));
    }

    @Test
    void emptyBodyStillSendsZeroContentLength() {
        assertTrue(text(response(HttpStatus.OK, "").encode()).contains("Content-Length: 0\r\n"));
    }

    @Test
    void contentLengthFollowsActualBodyNotHeader() {
        HttpResponse response = response(HttpStatus.OK, "abc");
        response.header().set("Content-Length", "999");

        String encoded = text(response.encode());

        assertTrue(encoded.contains("Content-Length: 3\r\n"));
        assertFalse(encoded.contains("999"));
    }

    @Test
    void transferEncodingSuppressesContentLength() {
        HttpResponse response = response(HttpStatus.OK, "abc");
        response.header().set("Transfer-Encoding", "chunked");

        assertFalse(text(response.encode()).contains("Content-Length"));
    }

    @Test
    void noContentOmitsContentLengthAndRejectsBody() {
        assertEquals("HTTP/1.1 204 No Content\r\nDate: Fri, 23 Oct 2026 00:00:00 GMT\r\n\r\n",
                text(response(HttpStatus.NO_CONTENT, "").encode()));
        assertThrows(IllegalStateException.class, () -> response(HttpStatus.NO_CONTENT, "x").encode());
        assertThrows(IllegalStateException.class, () -> response(HttpStatus.NOT_MODIFIED, "x").encode());
    }

    @Test
    void headResponseKeepsContentLengthButDropsBody() {
        assertTrue(text(response(HttpStatus.OK, "abc").encodeForHead()).endsWith("Content-Length: 3\r\n\r\n"));
    }

    @Test
    void addsImfFixdateWhenDateIsMissing() {
        HttpResponse response = new HttpResponse(HttpVersion.HTTP_1_1, new DefaultHttpHeader(),
                new DefaultHttpBody(), HttpStatus.OK);

        assertTrue(text(response.encode())
                .matches("(?s).*\r\nDate: [A-Z][a-z]{2}, \\d{2} [A-Z][a-z]{2} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT\r\n.*"));
        assertFalse(response.header().contains("Date"));
    }
}
