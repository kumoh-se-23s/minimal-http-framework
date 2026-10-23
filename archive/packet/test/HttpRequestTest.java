package com.minihttp.packet;

import com.minihttp.packet.body.HttpBody;
import com.minihttp.packet.codec.FormUrlEncoded;
import com.minihttp.packet.header.HttpHeader;
import com.minihttp.packet.header.MutableHttpHeader;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpVersion;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRequestTest {

    private static HttpRequest request(HttpVersion version, HttpHeader header) {
        return new HttpRequest(HttpMethod.GET, "/", version, Map.of(), Map.of(), header, HttpBody.empty());
    }

    private static HttpHeader connection(String... values) {
        MutableHttpHeader header = new MutableHttpHeader();
        for (String value : values) {
            header.add("Connection", value);
        }
        return header;
    }

    @Test
    void keepAliveDefaultsByVersion() {
        assertTrue(request(HttpVersion.HTTP_1_1, HttpHeader.empty()).keepAlive());
        assertFalse(request(HttpVersion.HTTP_1_0, HttpHeader.empty()).keepAlive());
    }

    @Test
    void keepAliveReadsConnectionTokenList() {
        assertTrue(request(HttpVersion.HTTP_1_0, connection("Keep-Alive, Upgrade")).keepAlive());
        assertFalse(request(HttpVersion.HTTP_1_1, connection("Upgrade, close")).keepAlive());
        assertFalse(request(HttpVersion.HTTP_1_1, connection("keep-alive", "close")).keepAlive());
    }

    @Test
    void headerNamesAreCaseInsensitive() {
        MutableHttpHeader header = new MutableHttpHeader();
        header.add("content-type", "text/plain");
        HttpRequest req = request(HttpVersion.HTTP_1_1, header);

        assertEquals(Optional.of("text/plain"), req.header().get("Content-Type"));
        assertTrue(req.header().contains("CONTENT-TYPE"));
    }

    @Test
    void requestIsNotAffectedByLaterMutation() {
        MutableHttpHeader header = new MutableHttpHeader();
        header.set("X-A", "1");
        Map<String, List<String>> query = new HashMap<>();
        query.put("q", new java.util.ArrayList<>(List.of("a")));

        HttpRequest req = new HttpRequest(HttpMethod.GET, "/", HttpVersion.HTTP_1_1,
                query, Map.of(), header, HttpBody.empty());
        header.set("X-A", "2");
        query.get("q").add("b");

        assertEquals(Optional.of("1"), req.header().get("X-A"));
        assertEquals(List.of("a"), req.queryParams("q"));
        assertFalse(req.header() instanceof MutableHttpHeader);
    }

    @Test
    void multiValueQueryParams() {
        HttpRequest req = new HttpRequest(HttpMethod.GET, "/", HttpVersion.HTTP_1_1,
                FormUrlEncoded.decode("tag=a&tag=b&name=%ED%99%8D"), Map.of(), HttpHeader.empty(), HttpBody.empty());

        assertEquals(List.of("a", "b"), req.queryParams("tag"));
        assertEquals(Optional.of("a"), req.queryParam("tag"));
        assertEquals(Optional.of("홍"), req.queryParam("name"));
        assertEquals(List.of(), req.queryParams("missing"));
    }

    @Test
    void withPathParamsFillsRouteValues() {
        HttpRequest req = request(HttpVersion.HTTP_1_1, HttpHeader.empty())
                .withPathParams(Map.of("id", "42"));

        assertEquals(Optional.of("42"), req.pathParam("id"));
        assertEquals(Optional.empty(), req.pathParam("other"));
    }
}
