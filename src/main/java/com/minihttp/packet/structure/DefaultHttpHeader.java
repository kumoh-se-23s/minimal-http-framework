package com.minihttp.packet.structure;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class DefaultHttpHeader implements HttpHeader {
    private final Map<String, Field> fields = new LinkedHashMap<>();

    @Override
    public Optional<String> get(@NonNull String name) {
        return getAll(name).stream().findFirst();
    }

    @Override
    public List<String> getAll(@NonNull String name) {
        Field field = this.fields.get(key(name));
        return field == null ? List.of() : List.copyOf(field.values);
    }

    @Override
    public boolean contains(@NonNull String name) {
        return this.fields.containsKey(key(name));
    }

    @Override
    public Set<String> names() {
        return this.fields.values().stream()
                .map(field -> field.name)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), Collections::unmodifiableSet));
    }

    @Override
    public Map<String, List<String>> asMap() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        this.fields.values().forEach(field -> copy.put(field.name, List.copyOf(field.values)));
        return Collections.unmodifiableMap(copy);
    }

    @Override
    public void add(@NonNull String name, @NonNull String value) {
        this.fields.computeIfAbsent(key(name), _ -> new Field(name)).values.add(value);
    }

    @Override
    public void set(@NonNull String name, @NonNull String value) {
        Field field = new Field(name);
        field.values.add(value);
        this.fields.put(key(name), field);
    }

    @Override
    public void remove(@NonNull String name) {
        this.fields.remove(key(name));
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static final class Field {
        private final String name;
        private final List<String> values = new ArrayList<>();

        private Field(String name) {
            this.name = name;
        }
    }
}
