package com.minihttp.example.routing_example;

import java.util.Arrays;
import java.util.List;

/**
 * 경로 패턴 문자열을 다루는 함수 모음. 만드는 쪽(BaseRouter), 처리하는 쪽(RoutingHandler),
 * 어노테이션 스캔(ControllerRegistrar)이 같은 규칙으로 경로를 자르도록 한곳에 둔다.
 *
 * <p>규칙: 경로는 {@code /}로 자른 세그먼트 목록으로 본다. 빈 세그먼트는 버리므로
 * {@code /users/}, {@code /users}, {@code //users}는 모두 {@code [users]}다.
 * {@code {name}} 모양의 세그먼트는 변수(경로 파라미터) 자리다.
 */
final class RoutePaths {
    private RoutePaths() {
    }

    static List<String> segments(String path) {
        return Arrays.stream(path.split("/"))
                     .filter(segment -> !segment.isEmpty())
                     .toList();
    }

    static boolean isVariable(String segment) {
        return segment.length() > 2 && segment.startsWith("{") && segment.endsWith("}");
    }

    static String variableName(String segment) {
        return segment.substring(1, segment.length() - 1);
    }

    /** 변수 이름만 다르고 모양이 같으면 같은 라우트로 본다 ({@code /users/{id}} == {@code /users/{name}}). */
    static boolean sameShape(String leftPattern, String rightPattern) {
        List<String> left = segments(leftPattern);
        List<String> right = segments(rightPattern);
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            String a = left.get(i);
            String b = right.get(i);
            if (isVariable(a) != isVariable(b) || (!isVariable(a) && !a.equals(b))) {
                return false;
            }
        }
        return true;
    }

    /** {@code "" → ""}, {@code "/users/" → "/users"}. {@code /}로 시작하지 않으면 예외. */
    static String normalizePrefix(String prefix) {
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
    static String join(String prefix, String pattern) {
        return "/" + String.join("/", segments(prefix + "/" + pattern));
    }
}
