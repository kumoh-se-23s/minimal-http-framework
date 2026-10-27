package com.minihttp.core.routing.binding;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * 헤더, 쿼리, 경로에서 꺼낸 문자열을 파라미터 타입으로 바꾼다.
 * 변환에 실패하면 IllegalArgumentException을 던진다 (호출하는 쪽이 400으로 바꾼다).
 */
final class ValueConverters {
    private ValueConverters() {
    }

    /** 지원하지 않는 타입이면 empty. */
    static Optional<Function<String, Object>> forType(Class<?> type) {
        if (type == String.class) {
            return Optional.of(value -> value);
        }
        if (type == int.class || type == Integer.class) {
            return Optional.of(Integer::parseInt);
        }
        if (type == long.class || type == Long.class) {
            return Optional.of(Long::parseLong);
        }
        if (type == double.class || type == Double.class) {
            return Optional.of(Double::parseDouble);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Optional.of(ValueConverters::parseBoolean);
        }
        if (type.isEnum()) {
            return Optional.of(value -> parseEnum(type, value));
        }
        return Optional.empty();
    }

    // Boolean.parseBoolean은 "yes"도 false로 받아 준다. 오타를 400으로 알려 주려고 직접 검사한다
    private static Boolean parseBoolean(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(value);
        };
    }

    private static Object parseEnum(Class<?> type, String value) {
        return Arrays.stream(type.getEnumConstants())
                     .filter(constant -> ((Enum<?>) constant).name().equalsIgnoreCase(value))
                     .findFirst()
                     .orElseThrow(() -> new IllegalArgumentException(value));
    }
}
