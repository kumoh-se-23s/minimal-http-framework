package com.minihttp.example.routing_example;

import com.minihttp.core.handler.Handler;
import com.minihttp.example.routing_example.annotation.Body;
import com.minihttp.example.routing_example.annotation.Delete;
import com.minihttp.example.routing_example.annotation.Get;
import com.minihttp.example.routing_example.annotation.Header;
import com.minihttp.example.routing_example.annotation.Patch;
import com.minihttp.example.routing_example.annotation.PathParam;
import com.minihttp.example.routing_example.annotation.Post;
import com.minihttp.example.routing_example.annotation.Put;
import com.minihttp.example.routing_example.annotation.Query;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpStatus;
import lombok.NonNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 어노테이션이 붙은 메서드를 찾아 {@link Handler}로 감싼 {@link RouteDefinition} 목록을 만든다.
 *
 * <p>라우터에 직접 넣지 않고 목록만 돌려준다. 언제 넣을지는 {@link BaseRouter}가 정한다.
 * <ul>
 *   <li>평범한 컨트롤러({@link Router#register(Object)}) → 이미 생성이 끝났으니 바로 스캔</li>
 *   <li>BaseRouter 하위 클래스 자신 → 생성자가 끝난 뒤, 라우트가 처음 쓰일 때 스캔</li>
 * </ul>
 *
 * <h2>요청 하나가 메서드 호출이 되기까지</h2>
 * <pre>
 *   {@literal @}Get("/{id}")
 *   public String find(long id, {@literal @}Query(defaultValue = "false") boolean verbose)
 *
 *   등록 때 (한 번):  resolvers = [ path "id" → long 변환,  query "verbose" → boolean 변환 (없으면 "false") ]
 *                    responder = String → 200 text/plain
 *
 *   요청 때 (매번):   GET /users/42?verbose=true
 *                    args = [ 42L, true ]  →  method.invoke(router, args)  →  "..."  →  HttpResponse
 * </pre>
 *
 * <p>메서드 하나를 등록할 때 파라미터마다 "요청에서 값을 꺼내는 함수(resolver)"와 반환값을 응답으로 바꾸는
 * 함수(responder)를 미리 만들어 둔다. 요청이 올 때는 리플렉션 분석 없이 그 함수들만 돌린다.
 * 잘못된 선언(없는 경로 변수, 지원하지 않는 타입)은 요청 때가 아니라 등록 때 예외로 드러난다.
 *
 * <p>파라미터를 어디서 채우는지:
 * <ul>
 *   <li>{@link HttpRequest} 타입 → 요청 그대로</li>
 *   <li>{@link Body} → 본문 ({@code byte[]}, {@code String})</li>
 *   <li>{@link Header}, {@link Query} → 헤더, 쿼리</li>
 *   <li>{@link PathParam}, 또는 이름이 경로 변수와 같은 파라미터 → 경로 파라미터</li>
 *   <li>나머지 → 쿼리</li>
 * </ul>
 * 반환 타입은 {@link HttpResponse}(그대로), {@code String}(200 text/plain), {@code void}(204)만 받는다.
 *
 * <p>이름을 생략한 어노테이션은 파라미터 이름을 쓰므로 {@code -parameters}로 컴파일해야 한다.
 */
final class ControllerRegistrar {
    private record Mapping(HttpMethod method, String pattern) {
    }

    /**
     * 요청 값이 없거나 변환에 실패함 → 400으로 바뀐다.
     * 이건 클라이언트 잘못이라 Connection까지 올리지 않고 여기서 응답으로 바꾼다 (올리면 500이 된다).
     */
    private static final class BadParameterException extends RuntimeException {
        BadParameterException(String message) {
            super(message);
        }
    }

    private ControllerRegistrar() {
    }

    /**
     * controller의 public 메서드 중 라우팅 어노테이션이 붙은 것을 전부 RouteDefinition으로 바꾼다.
     * 잘못된 선언이 하나라도 있으면 IllegalArgumentException (서버 시작 전에 드러나게).
     */
    static List<RouteDefinition> scan(@NonNull Object controller) {
        // getMethods()의 순서는 보장되지 않는다. 중복 라우트 에러 메시지가 실행마다 달라지지 않게 정렬한다.
        // getMethods()는 상속받은 public 메서드도 주지만, BaseRouter의 메서드에는 어노테이션이 없어서 걸러진다.
        Method[] methods = controller.getClass().getMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));

        List<RouteDefinition> definitions = new ArrayList<>();
        for (Method method : methods) {
            for (Mapping mapping : mappings(method)) {
                definitions.add(new RouteDefinition(mapping.method(), mapping.pattern(),
                        handler(controller, method, mapping.pattern())));
            }
        }
        return definitions;
    }

    private static List<Mapping> mappings(Method method) {
        List<Mapping> mappings = new ArrayList<>();
        if (method.getAnnotation(Get.class) instanceof Get get) {
            mappings.add(new Mapping(HttpMethod.GET, get.value()));
        }
        if (method.getAnnotation(Post.class) instanceof Post post) {
            mappings.add(new Mapping(HttpMethod.POST, post.value()));
        }
        if (method.getAnnotation(Put.class) instanceof Put put) {
            mappings.add(new Mapping(HttpMethod.PUT, put.value()));
        }
        if (method.getAnnotation(Patch.class) instanceof Patch patch) {
            mappings.add(new Mapping(HttpMethod.PATCH, patch.value()));
        }
        if (method.getAnnotation(Delete.class) instanceof Delete delete) {
            mappings.add(new Mapping(HttpMethod.DELETE, delete.value()));
        }
        return mappings;
    }

    /**
     * 메서드 하나를 Handler 람다로 감싼다. 리플렉션 분석(어노테이션 읽기, 타입 확인)은 여기서 한 번만 하고,
     * 람다 안에서는 미리 만든 resolver/responder만 돌린다.
     */
    private static Handler handler(Object controller, Method method, String pattern) {
        Set<String> pathVariables = pathVariables(pattern);
        List<Function<HttpRequest, Object>> resolvers = Arrays.stream(method.getParameters())
                                                             .map(parameter -> resolver(method, parameter, pathVariables))
                                                             .toList();
        Function<Object, HttpResponse> responder = responder(method);
        method.setAccessible(true);

        return request -> {
            Object[] args = new Object[resolvers.size()];
            try {
                for (int i = 0; i < args.length; i++) {
                    args[i] = resolvers.get(i).apply(request);
                }
            } catch (BadParameterException exception) {
                return RoutingResponses.text(HttpStatus.BAD_REQUEST, exception.getMessage());
            }

            Object result;
            try {
                result = method.invoke(controller, args);
            } catch (InvocationTargetException exception) {
                // 리플렉션 포장을 벗겨서 컨트롤러가 던진 예외 그대로 Connection까지 올린다 (→ 500)
                if (exception.getCause() instanceof Exception cause) {
                    throw cause;
                }
                if (exception.getCause() instanceof Error error) {
                    throw error;
                }
                throw exception;
            }
            return responder.apply(result);
        };
    }

    /**
     * 파라미터 하나를 어디서 채울지 정한다. 위에서부터 먼저 맞는 규칙이 이긴다.
     * 마지막 두 규칙 때문에 {@code find(long id)}는 경로에서, {@code add(int a, int b)}는 쿼리에서 채워진다.
     */
    private static Function<HttpRequest, Object> resolver(Method method, Parameter parameter, Set<String> pathVariables) {
        if (parameter.getType() == HttpRequest.class) {
            return request -> request;
        }
        if (parameter.isAnnotationPresent(Body.class)) {
            return body(method, parameter);
        }
        if (parameter.getAnnotation(Header.class) instanceof Header header) {
            String name = header.value().isEmpty() ? headerName(nameOf(method, parameter)) : header.value();
            String fallback = header.defaultValue().equals(Header.NONE) ? null : header.defaultValue();
            return value(method, parameter, "header", name, fallback, request -> request.header().get(name));
        }
        if (parameter.getAnnotation(Query.class) instanceof Query query) {
            String name = query.value().isEmpty() ? nameOf(method, parameter) : query.value();
            String fallback = query.defaultValue().equals(Query.NONE) ? null : query.defaultValue();
            return value(method, parameter, "query", name, fallback, request -> request.queryParam(name));
        }

        PathParam pathParam = parameter.getAnnotation(PathParam.class);
        String name = pathParam == null || pathParam.value().isEmpty() ? nameOf(method, parameter) : pathParam.value();
        if (pathParam != null && !pathVariables.contains(name)) {
            throw new IllegalArgumentException(describe(method) + ": no {" + name + "} in route pattern");
        }
        if (pathVariables.contains(name)) {
            return value(method, parameter, "path", name, null, request -> request.pathParam(name));
        }
        return value(method, parameter, "query", name, null, request -> request.queryParam(name));
    }

    private static Function<HttpRequest, Object> body(Method method, Parameter parameter) {
        if (parameter.getType() == byte[].class) {
            return request -> request.body().bytes();
        }
        if (parameter.getType() == String.class) {
            return request -> request.body().asString(StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException(describe(method) + ": @Body must be byte[] or String");
    }

    /**
     * 문자열 값 하나(헤더, 쿼리, 경로)를 꺼내 파라미터 타입으로 바꾸는 resolver.
     * 순서: 값 꺼내기 → 없으면 defaultValue → 그래도 없으면 Optional.empty() 또는 400 → 타입 변환 (실패하면 400).
     */
    private static Function<HttpRequest, Object> value(Method method,
                                                       Parameter parameter,
                                                       String source,
                                                       String name,
                                                       String fallback,
                                                       Function<HttpRequest, Optional<String>> lookup) {

        boolean optional = parameter.getType() == Optional.class;
        Class<?> target = optional ? optionalElement(method, parameter) : parameter.getType();
        Function<String, Object> converter = converter(method, target);

        return request -> {
            Optional<String> raw = lookup.apply(request);
            if (raw.isEmpty() && fallback != null) {
                raw = Optional.of(fallback);
            }
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

    private static Function<String, Object> converter(Method method, Class<?> type) {
        if (type == String.class) {
            return value -> value;
        }
        if (type == int.class || type == Integer.class) {
            return Integer::parseInt;
        }
        if (type == long.class || type == Long.class) {
            return Long::parseLong;
        }
        if (type == double.class || type == Double.class) {
            return Double::parseDouble;
        }
        if (type == boolean.class || type == Boolean.class) {
            return value -> switch (value.toLowerCase(Locale.ROOT)) {
                case "true" -> true;
                case "false" -> false;
                default -> throw new IllegalArgumentException(value);
            };
        }
        if (type.isEnum()) {
            return value -> Arrays.stream(type.getEnumConstants())
                                  .filter(constant -> ((Enum<?>) constant).name().equalsIgnoreCase(value))
                                  .findFirst()
                                  .orElseThrow(() -> new IllegalArgumentException(value));
        }
        throw new IllegalArgumentException(describe(method) + ": unsupported parameter type " + type.getName());
    }

    private static Class<?> optionalElement(Method method, Parameter parameter) {
        if (parameter.getParameterizedType() instanceof ParameterizedType type
                && type.getActualTypeArguments()[0] instanceof Class<?> element) {
            return element;
        }
        throw new IllegalArgumentException(describe(method) + ": Optional parameter needs a concrete type argument");
    }

    private static Function<Object, HttpResponse> responder(Method method) {
        Class<?> type = method.getReturnType();
        if (type == HttpResponse.class) {
            return result -> (HttpResponse) result;
        }
        if (type == String.class) {
            return result -> RoutingResponses.text(HttpStatus.OK, (String) result);
        }
        if (type == void.class) {
            return _ -> RoutingResponses.empty(HttpStatus.NO_CONTENT);
        }
        throw new IllegalArgumentException(describe(method) + ": return type must be HttpResponse, String or void");
    }

    private static String nameOf(Method method, Parameter parameter) {
        if (!parameter.isNamePresent()) {
            throw new IllegalArgumentException(
                    describe(method) + ": compile with -parameters or give the annotation an explicit name");
        }
        return parameter.getName();
    }

    /** {@code contentType → content-Type}. 헤더 조회는 대소문자를 구분하지 않으므로 하이픈만 맞으면 된다. */
    private static String headerName(String parameterName) {
        return parameterName.replaceAll("([a-z0-9])([A-Z])", "$1-$2");
    }

    private static Set<String> pathVariables(String pattern) {
        return RoutePaths.segments(pattern).stream()
                         .filter(RoutePaths::isVariable)
                         .map(RoutePaths::variableName)
                         .collect(Collectors.toUnmodifiableSet());
    }

    private static String describe(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
}
