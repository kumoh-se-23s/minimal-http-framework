package com.minihttp.example.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpRequestParserTest {
    private final HttpRequestParser parser = new HttpRequestParser();

    @Test
    void parsesRequestLineQueryAndHeaders() throws IOException {
        HttpRequest request = parse("GET /hello%20world?name=%EA%B9%80&flag HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "X-Custom:   value  \r\n"
                + "\r\n");

        assertEquals(HttpMethod.GET, request.method());
        assertEquals("/hello world", request.path());
        assertEquals("김", request.query("name").orElseThrow());
        assertEquals("", request.query("flag").orElseThrow());
        assertEquals("HTTP/1.1", request.version());
        assertEquals("localhost", request.header("host").orElseThrow());
        assertEquals("value", request.header("X-CUSTOM").orElseThrow());
        assertEquals(0, request.body().length);
    }

    @Test
    void readsBodyByByteLength() throws IOException {
        byte[] body = "안녕".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = parse("POST /echo HTTP/1.1\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "\r\n"
                + "안녕");

        assertArrayEquals(body, request.body());
        assertEquals("안녕", request.bodyAsString());
    }

    @Test
    void parsesPipelinedRequestsFromSameStream() throws IOException {
        InputStream in = stream("POST /a HTTP/1.1\r\nContent-Length: 3\r\n\r\nabc"
                + "GET /b HTTP/1.1\r\n\r\n");

        assertEquals("abc", parser.parse(in).bodyAsString());
        assertEquals("/b", parser.parse(in).path());
        assertNull(parser.parse(in));
    }

    @Test
    void acceptsBareLineFeeds() throws IOException {
        HttpRequest request = parse("GET / HTTP/1.1\nHost: x\n\n");

        assertNotNull(request);
        assertEquals("x", request.header("Host").orElseThrow());
    }

    @Test
    void returnsNullWhenStreamIsEmpty() throws IOException {
        assertNull(parser.parse(stream("")));
    }

    @Test
    void keepsPlusSignInPathButDecodesItInQuery() throws IOException {
        HttpRequest request = parse("GET /a+b?q=c+d HTTP/1.1\r\n\r\n");

        assertEquals("/a+b", request.path());
        assertEquals("c d", request.query("q").orElseThrow());
    }

    @Test
    void rejectsMalformedRequestLine() {
        assertStatus(HttpStatus.BAD_REQUEST, "GET /\r\n\r\n");
    }

    @Test
    void rejectsUnknownMethod() {
        assertStatus(HttpStatus.NOT_IMPLEMENTED, "BREW /pot HTTP/1.1\r\n\r\n");
    }

    @Test
    void rejectsUnsupportedVersion() {
        assertStatus(HttpStatus.HTTP_VERSION_NOT_SUPPORTED, "GET / HTTP/2.0\r\n\r\n");
    }

    @Test
    void rejectsMalformedHeader() {
        assertStatus(HttpStatus.BAD_REQUEST, "GET / HTTP/1.1\r\nNoColon\r\n\r\n");
    }

    @Test
    void rejectsHeadersCutOff() {
        assertStatus(HttpStatus.BAD_REQUEST, "GET / HTTP/1.1\r\nHost: x\r\n");
    }

    @Test
    void rejectsBodyShorterThanContentLength() {
        assertStatus(HttpStatus.BAD_REQUEST, "POST / HTTP/1.1\r\nContent-Length: 10\r\n\r\nabc");
    }

    @Test
    void rejectsInvalidContentLength() {
        assertStatus(HttpStatus.BAD_REQUEST, "POST / HTTP/1.1\r\nContent-Length: -1\r\n\r\n");
    }

    @Test
    void rejectsTooLargeBody() {
        assertStatus(HttpStatus.PAYLOAD_TOO_LARGE, "POST / HTTP/1.1\r\nContent-Length: 999999999\r\n\r\n");
    }

    @Test
    void rejectsChunkedEncoding() {
        assertStatus(HttpStatus.NOT_IMPLEMENTED, "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n");
    }

    @Test
    void rejectsTooLongHeaderLine() {
        assertStatus(HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE,
                "GET / HTTP/1.1\r\nX: " + "a".repeat(10_000) + "\r\n\r\n");
    }

    @Test
    void rejectsMalformedPercentEncoding() {
        assertStatus(HttpStatus.BAD_REQUEST, "GET /%zz HTTP/1.1\r\n\r\n");
    }

    private HttpRequest parse(String raw) throws IOException {
        return parser.parse(stream(raw));
    }

    private void assertStatus(HttpStatus expected, String raw) {
        HttpParseException e = assertThrows(HttpParseException.class, () -> parse(raw));
        assertEquals(expected, e.status());
    }

    private static InputStream stream(String raw) {
        return new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8));
    }
}
