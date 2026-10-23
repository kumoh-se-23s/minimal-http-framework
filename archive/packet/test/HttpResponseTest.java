package com.minihttp.packet;

import com.minihttp.packet.body.HttpBody;
import com.minihttp.packet.header.MutableHttpHeader;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpResponseTest {

    @Test
    void textSetsUtf8ContentType() {
        HttpResponse response = HttpResponse.ok("안녕");

        assertEquals(HttpStatus.OK, response.status());
        assertEquals(Optional.of("text/plain; charset=utf-8"), response.contentType());
        assertEquals("안녕", response.body().asString());
        assertEquals(6, response.body().length());
    }

    @Test
    void jsonSetsJsonContentType() {
        HttpResponse response = HttpResponse.json(HttpStatus.CREATED, "{\"id\":1}");

        assertEquals(HttpStatus.CREATED, response.status());
        assertEquals(Optional.of("application/json; charset=utf-8"), response.header("content-type"));
    }

    @Test
    void withersReturnNewInstances() {
        HttpResponse original = HttpResponse.ok("a");
        HttpResponse changed = original
                .withStatus(HttpStatus.ACCEPTED)
                .withHeader("X-A", "1")
                .addHeader("Set-Cookie", "a=1")
                .addHeader("Set-Cookie", "b=2");

        assertEquals(HttpStatus.OK, original.status());
        assertFalse(original.header().contains("X-A"));
        assertEquals(HttpStatus.ACCEPTED, changed.status());
        assertEquals(Optional.of("1"), changed.header("x-a"));
        assertEquals(List.of("a=1", "b=2"), changed.header().getAll("Set-Cookie"));
        assertFalse(changed.withoutHeader("X-A").header().contains("X-A"));
    }

    @Test
    void withBodyReplacesBodyAndContentType() {
        HttpResponse response = HttpResponse.ok("a").withBody(new byte[]{1, 2}, "application/octet-stream");

        assertEquals(Optional.of("application/octet-stream"), response.contentType());
        assertEquals(List.of("application/octet-stream"), response.header().getAll("Content-Type"));
        assertEquals(2, response.body().length());
    }

    @Test
    void headerIsSnapshotted() {
        MutableHttpHeader header = new MutableHttpHeader();
        header.set("X-A", "1");
        HttpResponse response = new HttpResponse(HttpVersion.HTTP_1_1, HttpStatus.OK, header, HttpBody.empty());
        header.set("X-A", "2");

        assertEquals(Optional.of("1"), response.header("X-A"));
    }

    @Test
    void noContentRejectsBody() {
        assertTrue(HttpResponse.noContent().body().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> HttpResponse.ok("a").withStatus(HttpStatus.NO_CONTENT));
    }

    @Test
    void notFoundUsesReasonPhrase() {
        HttpResponse response = HttpResponse.notFound();

        assertEquals(HttpStatus.NOT_FOUND, response.status());
        assertEquals("Not Found", response.body().asString());
    }
}
