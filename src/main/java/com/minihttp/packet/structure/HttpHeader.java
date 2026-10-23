package com.minihttp.packet.structure;

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
}
