package com.minihttp.packet.header;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class MutableHttpHeader implements HttpHeader {
    private final Map<String, List<String>> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public static MutableHttpHeader copyOf(HttpHeader header) {
        MutableHttpHeader copy = new MutableHttpHeader();
        header.asMap().forEach((name, list) -> copy.values.put(name, new ArrayList<>(list)));
        return copy;
    }

    public void add(String name, String value) {
        values.computeIfAbsent(name, _ -> new ArrayList<>()).add(value);
    }

    public void set(String name, String value) {
        List<String> list = new ArrayList<>();
        list.add(value);
        values.put(name, list);
    }

    public void remove(String name) {
        values.remove(name);
    }

    @Override
    public Optional<String> get(String name) {
        return getAll(name).stream().findFirst();
    }

    @Override
    public List<String> getAll(String name) {
        return Collections.unmodifiableList(values.getOrDefault(name, List.of()));
    }

    @Override
    public boolean contains(String name) {
        return values.containsKey(name);
    }

    @Override
    public Map<String, List<String>> asMap() {
        return Collections.unmodifiableMap(values);
    }
}
