package com.minihttp.example.http;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 헤더 이름은 대소문자를 구분하지 않는다 (RFC 9110 5.1).
 */
public final class HttpHeaders {
    private final Map<String, String> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /** 같은 이름이 이미 있으면 ", "로 이어 붙인다. */
    public void add(String name, String value) {
        values.merge(name, value, (existing, added) -> existing + ", " + added);
    }

    public void set(String name, String value) {
        values.put(name, value);
    }

    public void remove(String name) {
        values.remove(name);
    }

    public Optional<String> get(String name) {
        return Optional.ofNullable(values.get(name));
    }

    public boolean contains(String name) {
        return values.containsKey(name);
    }

    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(values);
    }
}
