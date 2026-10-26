package com.minihttp.packet;

import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.DefaultHttpBody;
import com.minihttp.packet.structure.DefaultHttpHeader;
import com.minihttp.packet.structure.HttpHeader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRequestTest {

    private static HttpHeader host(String... connection) {
        HttpHeader header = new DefaultHttpHeader();
        header.set(HttpHeaderField.HOST, "localhost");
        for (String value : connection) {
            header.add(HttpHeaderField.CONNECTION, value);
        }
        return header;
    }

    private static HttpRequest request(HttpVersion version, HttpHeader header, HttpMethod method, String path,
                                       Map<String, List<String>> query, String body) {
        return new HttpRequest(version, header, new DefaultHttpBody(body.getBytes(StandardCharsets.UTF_8)),
                method, path, query, Map.of());
    }

    @Test
    void startLineHasMethodTargetAndVersion() {
        assertEquals("GET /users HTTP/1.1",
                request(HttpVersion.HTTP_1_1, host(), HttpMethod.GET, "/users", Map.of(), "").startLine());
    }

    @Test
    void contentLengthIsRequiredOnlyForMethodsWithBody() {
        assertFalse(request(HttpVersion.HTTP_1_1, host(), HttpMethod.GET, "/users", Map.of(), "").contentLengthRequired());
        assertTrue(request(HttpVersion.HTTP_1_1, host(), HttpMethod.POST, "/users", Map.of(), "").contentLengthRequired());
        assertTrue(request(HttpVersion.HTTP_1_1, host(), HttpMethod.PUT, "/users", Map.of(), "{}").contentLengthRequired());
        assertTrue(request(HttpVersion.HTTP_1_1, host(), HttpMethod.PATCH, "/users", Map.of(), "{}").contentLengthRequired());
    }

    @Test
    void queryKeepsOrderCaseAndMultipleValues() {
        Map<String, List<String>> query = new LinkedHashMap<>();
        query.put("z", List.of("1"));
        query.put("id", List.of("1"));
        query.put("ID", List.of("2"));
        query.put("tag", List.of("a", "b"));
        query.put("a b", List.of("c d"));
        HttpRequest request = request(HttpVersion.HTTP_1_1, host(), HttpMethod.GET, "/", query, "");

        assertEquals("GET /?z=1&id=1&ID=2&tag=a&tag=b&a+b=c+d HTTP/1.1", request.startLine());
        assertEquals(List.of("a", "b"), request.queryParams("tag"));
        assertEquals(Optional.of("1"), request.queryParam("id"));
        assertEquals(Optional.of("2"), request.queryParam("ID"));
    }

    @Test
    void pathIsPercentEncodedPerSegment() {
        HttpRequest request = request(HttpVersion.HTTP_1_1, host(), HttpMethod.GET, "/my files/김", Map.of(), "");

        assertEquals("GET /my%20files/%EA%B9%80 HTTP/1.1", request.startLine());
    }

    @Test
    void http11RequiresHost() {
        assertThrows(IllegalStateException.class, () ->
                request(HttpVersion.HTTP_1_1, new DefaultHttpHeader(), HttpMethod.GET, "/", Map.of(), "").validate());
        assertDoesNotThrow(() ->
                request(HttpVersion.HTTP_1_0, new DefaultHttpHeader(), HttpMethod.GET, "/", Map.of(), "").validate());
    }

    @Test
    void keepAliveFollowsConnectionHeaderThenVersion() {
        assertTrue(request(HttpVersion.HTTP_1_1, host(), HttpMethod.GET, "/", Map.of(), "").keepAlive());
        assertFalse(request(HttpVersion.HTTP_1_0, host(), HttpMethod.GET, "/", Map.of(), "").keepAlive());
        assertTrue(request(HttpVersion.HTTP_1_0, host("Keep-Alive, Upgrade"), HttpMethod.GET, "/", Map.of(), "").keepAlive());
        assertFalse(request(HttpVersion.HTTP_1_1, host("keep-alive", "close"), HttpMethod.GET, "/", Map.of(), "").keepAlive());
    }
}
