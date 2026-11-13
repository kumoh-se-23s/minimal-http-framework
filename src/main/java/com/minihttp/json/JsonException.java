package com.minihttp.json;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JSON 파싱/바인딩 실패. FastAPI처럼 한 번에 여러 필드의 문제를 모아서 알려줄 수 있도록 에러 목록을 가진다.
 */
public class JsonException extends RuntimeException {
    private final List<JsonError> errors;

    public JsonException(List<JsonError> errors) {
        super(JsonException.describe(errors), null, false, false);
        this.errors = List.copyOf(errors);
    }

    public JsonException(String errorType, List<Object> location, String message) {
        this(List.of(new JsonError(errorType, location, message)));
    }

    public List<JsonError> errors() {
        return this.errors;
    }

    private static String describe(List<JsonError> errors) {
        return errors.stream()
                     .map(error -> error.location() + ": " + error.message())
                     .collect(Collectors.joining(", "));
    }
}
