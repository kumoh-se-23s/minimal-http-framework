package com.minihttp.core.routing.binding;


import com.minihttp.core.routing.annotation.Body;
import com.minihttp.core.routing.annotation.Header;
import com.minihttp.core.routing.annotation.PathParam;
import com.minihttp.core.routing.annotation.Query;
import com.minihttp.core.routing.support.RoutePaths;
import com.minihttp.packet.HttpRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 메서드 파라미터마다 {@link ArgumentResolver}를 만든다. 위에서부터 먼저 맞는 규칙을 쓴다.
 * <ol>
 *   <li>{@link HttpRequest} 타입 → 요청 그대로</li>
 *   <li>{@link Body} → 본문 ({@code byte[]}, {@code String})</li>
 *   <li>{@link Header} → 헤더</li>
 *   <li>{@link Query} → 쿼리</li>
 *   <li>{@link PathParam}, 또는 이름이 경로 변수와 같은 파라미터 → 경로 파라미터</li>
 *   <li>나머지 → 쿼리</li>
 * </ol>
 *
 * <p>이름을 생략하면 파라미터 이름을 쓰므로 {@code -parameters}로 컴파일해야 한다.
 */
final class ArgumentResolvers {
    private ArgumentResolvers() {
    }

    static List<ArgumentResolver> forMethod(Method method, String pattern) {
        Set<String> pathVariables = RoutePaths.segments(pattern).stream()
                                              .filter(RoutePaths::isVariable)
                                              .map(RoutePaths::variableName)
                                              .collect(Collectors.toUnmodifiableSet());

        return Arrays.stream(method.getParameters())
                     .map(parameter -> forParameter(method, parameter, pathVariables))
                     .toList();
    }

    private static ArgumentResolver forParameter(Method method, Parameter parameter, Set<String> pathVariables) {
        if (parameter.getType() == HttpRequest.class) {
            return request -> request;
        }
        if (parameter.isAnnotationPresent(Body.class)) {
            return body(method, parameter);
        }
        if (parameter.getAnnotation(Header.class) instanceof Header header) {
            String name = header.value().isEmpty() ? headerName(nameOf(method, parameter)) : header.value();
            String fallback = header.defaultValue().equals(Header.NONE) ? null : header.defaultValue();
            return stringValue(method, parameter, "header", name, fallback, request -> request.header().get(name));
        }
        if (parameter.getAnnotation(Query.class) instanceof Query query) {
            String name = query.value().isEmpty() ? nameOf(method, parameter) : query.value();
            String fallback = query.defaultValue().equals(Query.NONE) ? null : query.defaultValue();
            return stringValue(method, parameter, "query", name, fallback, request -> request.queryParam(name));
        }
        return pathOrQuery(method, parameter, pathVariables);
    }

    private static ArgumentResolver pathOrQuery(Method method, Parameter parameter, Set<String> pathVariables) {
        PathParam pathParam = parameter.getAnnotation(PathParam.class);
        String name = pathParam == null || pathParam.value().isEmpty() ? nameOf(method, parameter) : pathParam.value();

        if (pathVariables.contains(name)) {
            return stringValue(method, parameter, "path", name, null, request -> request.pathParam(name));
        }
        if (pathParam != null) {
            throw new IllegalArgumentException(ControllerRegistrar.describe(method) + ": no {" + name + "} in route pattern");
        }
        return stringValue(method, parameter, "query", name, null, request -> request.queryParam(name));
    }

    private static ArgumentResolver body(Method method, Parameter parameter) {
        if (parameter.getType() == byte[].class) {
            return request -> request.body().bytes();
        }
        if (parameter.getType() == String.class) {
            return request -> request.body().asString(StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException(ControllerRegistrar.describe(method) + ": @Body must be byte[] or String");
    }

    /**
     * 문자열 값 하나를 꺼내 파라미터 타입으로 바꾼다.
     * 값 → 없으면 fallback → 그래도 없으면 Optional.empty() 또는 400 → 타입 변환 (실패하면 400).
     */
    private static ArgumentResolver stringValue(Method method,
                                                Parameter parameter,
                                                String source,
                                                String name,
                                                String fallback,
                                                Function<HttpRequest, Optional<String>> lookup) {

        boolean optional = parameter.getType() == Optional.class;
        Class<?> target = optional ? optionalElement(method, parameter) : parameter.getType();
        Function<String, Object> converter = ValueConverters.forType(target).orElseThrow(() -> new IllegalArgumentException(
                ControllerRegistrar.describe(method) + ": unsupported parameter type " + target.getName()));

        return request -> {
            Optional<String> raw = lookup.apply(request).or(() -> Optional.ofNullable(fallback));
            if (raw.isEmpty()) {
                if (optional) {
                    return Optional.empty();
                }
                throw new BadParameterException("Missing " + source + " parameter: " + name);
            }

            Object converted;
            try {
                converted = converter.apply(raw.get());
            } catch (IllegalArgumentException exception) {
                throw new BadParameterException(
                        "Invalid " + source + " parameter '" + name + "': expected " + target.getSimpleName());
            }
            return optional ? Optional.of(converted) : converted;
        };
    }

    private static Class<?> optionalElement(Method method, Parameter parameter) {
        if (parameter.getParameterizedType() instanceof ParameterizedType type
                && type.getActualTypeArguments()[0] instanceof Class<?> element) {
            return element;
        }
        throw new IllegalArgumentException(
                ControllerRegistrar.describe(method) + ": Optional parameter needs a concrete type argument");
    }

    private static String nameOf(Method method, Parameter parameter) {
        if (!parameter.isNamePresent()) {
            throw new IllegalArgumentException(ControllerRegistrar.describe(method)
                    + ": compile with -parameters or give the annotation an explicit name");
        }
        return parameter.getName();
    }

    /** {@code contentType → content-Type}. 헤더 조회는 대소문자를 구분하지 않아 하이픈만 맞으면 된다. */
    private static String headerName(String parameterName) {
        return parameterName.replaceAll("([a-z0-9])([A-Z])", "$1-$2");
    }
}
