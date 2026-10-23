package com.minihttp.packet.structure;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultHttpHeaderTest {

    @Test
    void namesAreCaseInsensitiveAndKeepInsertionOrder() {
        HttpHeader header = new DefaultHttpHeader();
        header.add("Host", "a");
        header.add("Set-Cookie", "x=1");
        header.add("set-cookie", "y=2");

        assertEquals(List.of("Host", "Set-Cookie"), List.copyOf(header.names()));
        assertEquals(List.of("x=1", "y=2"), header.getAll("SET-COOKIE"));
        assertEquals(Optional.of("a"), header.get("host"));
    }

    @Test
    void rejectsCrLfInjection() {
        HttpHeader header = new DefaultHttpHeader();

        assertThrows(IllegalArgumentException.class, () -> header.set("X-A", "a\r\nSet-Cookie: hacked=1"));
        assertThrows(IllegalArgumentException.class, () -> header.add("X-A", "a\nb"));
        assertThrows(IllegalArgumentException.class, () -> header.add("X-A", "a\u0000b"));
    }

    @Test
    void rejectsInvalidNames() {
        HttpHeader header = new DefaultHttpHeader();

        assertThrows(IllegalArgumentException.class, () -> header.add("", "v"));
        assertThrows(IllegalArgumentException.class, () -> header.add("Bad Name", "v"));
        assertThrows(IllegalArgumentException.class, () -> header.add("X:A", "v"));
    }

    @Test
    void rejectsCharactersOutsideLatin1InsteadOfCorrupting() {
        HttpHeader header = new DefaultHttpHeader();

        assertThrows(IllegalArgumentException.class, () -> header.set("Content-Disposition", "filename=\"보고서.pdf\""));
        assertDoesNotThrow(() -> header.set("X-A", "tab\tand café"));
    }
}
