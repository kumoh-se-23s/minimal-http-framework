package com.minihttp.packet.header;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

final class ImmutableHttpHeader implements HttpHeader {
    static final ImmutableHttpHeader EMPTY = new ImmutableHttpHeader(Map.of());

    private final Map<String, List<String>> values;

    ImmutableHttpHeader(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        source.forEach((name, list) -> copy.put(name, List.copyOf(list)));
        this.values = Collections.unmodifiableMap(copy);
    }

    @Override
    public Optional<String> get(String name) {
        return getAll(name).stream().findFirst();
    }

    @Override
    public List<String> getAll(String name) {
        return values.getOrDefault(name, List.of());
    }

    @Override
    public boolean contains(String name) {
        return values.containsKey(name);
    }

    @Override
    public Map<String, List<String>> asMap() {
        return values;
    }
}
