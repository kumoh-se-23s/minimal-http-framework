package com.minihttp.json;

import java.lang.reflect.Type;

/** {@link JsonParser} + {@link JsonBinder} + {@link JsonWriter}로 만든 기본 {@link JsonMapper}. */
public final class DefaultJsonMapper implements JsonMapper {
    private static final DefaultJsonMapper INSTANCE = new DefaultJsonMapper();

    private DefaultJsonMapper() {
    }

    public static DefaultJsonMapper getInstance() {
        return INSTANCE;
    }

    @Override
    public String write(Object value) {
        return JsonWriter.write(value);
    }

    @Override
    public Object read(String json, Type targetType) {
        return JsonBinder.bind(JsonParser.parse(json), targetType);
    }
}
