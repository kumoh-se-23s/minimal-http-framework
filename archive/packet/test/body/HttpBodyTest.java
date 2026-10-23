package com.minihttp.packet.body;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpBodyTest {

    @Test
    void bodyIsDefensivelyCopied() {
        byte[] source = "a=1".getBytes();
        HttpBody body = HttpBody.of(source);
        source[0] = 'z';
        body.bytes()[0] = 'z';

        assertEquals("a=1", body.asString());
        assertEquals(Map.of("a", List.of("1")), body.asForm());
        assertTrue(body.raw().isReadOnly());
    }

    @Test
    void emptyBytesReuseEmptyBody() {
        assertSame(HttpBody.empty(), HttpBody.of(new byte[0]));
        assertTrue(HttpBody.empty().isEmpty());
    }
}
