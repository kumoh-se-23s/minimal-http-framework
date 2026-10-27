package com.minihttp.core.routing.support;

import java.util.Arrays;
import java.util.List;

/**
 * 경로 패턴을 세그먼트 단위로 다룬다. 빈 세그먼트는 버리므로 {@code /users/}, {@code /users}, {@code //users}는 같다.
 * {@code {name}} 세그먼트는 경로 파라미터 자리다.
 */
public final class RoutePaths {
    private RoutePaths() {
    }

    public static List<String> segments(String path) {
        return Arrays.stream(path.split("/"))
                     .filter(segment -> !segment.isEmpty())
                     .toList();
    }

    public static boolean isVariable(String segment) {
        return segment.length() > 2 && segment.startsWith("{") && segment.endsWith("}");
    }

    public static String variableName(String segment) {
        return segment.substring(1, segment.length() - 1);
    }

    /** 변수 이름만 다르고 모양이 같으면 같은 라우트다 ({@code /users/{id}} == {@code /users/{name}}). */
    public static boolean sameShape(String leftPattern, String rightPattern) {
        List<String> left = segments(leftPattern);
        List<String> right = segments(rightPattern);
        if (left.size() != right.size()) {
            return false;
        }

        for (int i = 0; i < left.size(); i++) {
            String a = left.get(i);
            String b = right.get(i);
            boolean bothVariables = isVariable(a) && isVariable(b);
            if (!bothVariables && !a.equals(b)) {
                return false;
            }
        }
        return true;
    }

    /** {@code "/users/" → "/users"}, {@code "" → ""}. */
    public static String normalizePrefix(String prefix) {
        if (prefix.isEmpty()) {
            return prefix;
        }
        if (!prefix.startsWith("/")) {
            throw new IllegalArgumentException("prefix must start with '/': " + prefix);
        }

        String joined = String.join("/", segments(prefix));
        return joined.isEmpty() ? "" : "/" + joined;
    }

    /** {@code ("/api", "/users/{id}") → "/api/users/{id}"}, {@code ("/health", "/") → "/health"}. */
    public static String join(String prefix, String pattern) {
        return "/" + String.join("/", segments(prefix + "/" + pattern));
    }
}
