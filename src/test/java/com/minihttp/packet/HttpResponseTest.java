package com.minihttp.packet;

import com.minihttp.packet.protocol.ContentType;
import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.DefaultHttpBody;
import com.minihttp.packet.structure.DefaultHttpHeader;
import com.minihttp.packet.structure.HttpHeader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpResponseTest {

    private static HttpResponse response(HttpStatus status, String body) {
        HttpHeader header = new DefaultHttpHeader();
        header.set(HttpHeaderField.DATE, "Fri, 23 Oct 2026 00:00:00 GMT");
        return new HttpResponse(HttpVersion.HTTP_1_1, header,
                new DefaultHttpBody(body.getBytes(StandardCharsets.UTF_8)), status);
    }

    private static String write(HttpResponse response) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpPacketWriter.write(response, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void writesStatusLineHeadersContentLengthAndBody() throws IOException {
        HttpResponse response = response(HttpStatus.OK, "안녕");
        response.contentType(ContentType.TEXT_PLAIN);

        assertEquals("HTTP/1.1 200 OK\r\n"
                        + "Date: Fri, 23 Oct 2026 00:00:00 GMT\r\n"
                        + "Content-Type: text/plain; charset=utf-8\r\n"
                        + "Server: minihttp\r\n"
                        + "Content-Length: 6\r\n"
                        + "\r\n"
                        + "안녕",
                write(response));
    }

    @Test
    void emptyBodyStillSendsZeroContentLength() throws IOException {
        assertTrue(write(response(HttpStatus.OK, "")).contains("Content-Length: 0\r\n"));
    }

    @Test
    void contentLengthFollowsActualBodyNotHeader() throws IOException {
        HttpResponse response = response(HttpStatus.OK, "abc");
        response.header().set(HttpHeaderField.CONTENT_LENGTH, "999");

        String written = write(response);

        assertTrue(written.contains("Content-Length: 3\r\n"));
        assertFalse(written.contains("999"));
    }

    @Test
    void noContentOmitsContentLengthAndBody() throws IOException {
        assertEquals("HTTP/1.1 204 No Content\r\nDate: Fri, 23 Oct 2026 00:00:00 GMT\r\nServer: minihttp\r\n\r\n",
                write(response(HttpStatus.NO_CONTENT, "")));

        HttpResponse withBody = response(HttpStatus.NO_CONTENT, "x");
        withBody.header().set(HttpHeaderField.CONTENT_LENGTH, "1");
        assertTrue(write(withBody).endsWith("Server: minihttp\r\n\r\n"));
    }

    @Test
    void keepsServerSetByHandler() throws IOException {
        HttpResponse response = response(HttpStatus.OK, "");
        response.header().set(HttpHeaderField.SERVER, "my-app");

        String written = write(response);

        assertTrue(written.contains("Server: my-app\r\n"));
        assertFalse(written.contains("minihttp"));
    }

    @Test
    void addsImfFixdateWhenDateIsMissing() throws IOException {
        HttpResponse response = new HttpResponse(HttpVersion.HTTP_1_1, new DefaultHttpHeader(),
                new DefaultHttpBody(), HttpStatus.OK);

        assertTrue(write(response)
                .matches("(?s).*\r\nDate: [A-Z][a-z]{2}, \\d{2} [A-Z][a-z]{2} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT\r\n.*"));
    }
}
