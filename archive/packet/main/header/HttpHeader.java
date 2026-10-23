package com.minihttp.packet.header;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface HttpHeader {
    Optional<String> get(String name);

    List<String> getAll(String name);

    boolean contains(String name);

    Map<String, List<String>> asMap();

    static HttpHeader empty() {
        return ImmutableHttpHeader.EMPTY;
    }

    static HttpHeader copyOf(HttpHeader header) {
        if (header instanceof ImmutableHttpHeader immutable) {
            return immutable;
        }
        return new ImmutableHttpHeader(header.asMap());
    }
}
