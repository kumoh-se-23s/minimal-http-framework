package com.minihttp.packet.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpVersionTest {

    @Test
    void parsesSupportedVersions() {
        assertEquals(HttpVersion.HTTP_1_0, HttpVersion.from("HTTP/1.0"));
        assertEquals(HttpVersion.HTTP_1_1, HttpVersion.from("HTTP/1.1"));
    }

    @Test
    void unsupportedVersionIsRejected() {
        HttpParseException e = assertThrows(HttpParseException.class, () -> HttpVersion.from("HTTP/2.0"));
        assertEquals(HttpStatus.HTTP_VERSION_NOT_SUPPORTED, e.status());
    }
}
