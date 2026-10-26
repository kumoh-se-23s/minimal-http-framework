package com.minihttp.packet.structure;

import com.minihttp.packet.protocol.HttpHeaderField;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface HttpHeader {
    Optional<String> get(String name);

    List<String> getAll(String name);

    boolean contains(String name);

    Set<String> names();

    Map<String, List<String>> asMap();

    void add(String name, String value);

    void set(String name, String value);

    void remove(String name);

    default Optional<String> get(HttpHeaderField field) {
        return this.get(field.fieldName());
    }

    default List<String> getAll(HttpHeaderField field) {
        return this.getAll(field.fieldName());
    }

    default boolean contains(HttpHeaderField field) {
        return this.contains(field.fieldName());
    }

    default void add(HttpHeaderField field, String value) {
        this.add(field.fieldName(), value);
    }

    default void set(HttpHeaderField field, String value) {
        this.set(field.fieldName(), value);
    }

    default void remove(HttpHeaderField field) {
        this.remove(field.fieldName());
    }
}
