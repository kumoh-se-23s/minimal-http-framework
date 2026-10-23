package com.minihttp.example.routing;

import com.minihttp.example.http.HttpMethod;
import com.minihttp.example.http.HttpRequest;
import com.minihttp.example.http.HttpResponse;
import com.minihttp.example.http.HttpStatus;
import com.minihttp.example.routing.annotation.Body;
import com.minihttp.example.routing.annotation.Delete;
import com.minihttp.example.routing.annotation.Get;
import com.minihttp.example.routing.annotation.Header;
import com.minihttp.example.routing.annotation.Patch;
import com.minihttp.example.routing.annotation.PathParam;
import com.minihttp.example.routing.annotation.Post;
import com.minihttp.example.routing.annotation.Put;
import com.minihttp.example.routing.annotation.Query;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class ControllerRegistrar {
    private record Mapping(HttpMethod method, String pattern) {
    }

    private static final class BadParameterException extends RuntimeException {
        BadParameterException(String message) {
            super(message);
        }
    }

    private ControllerRegistrar() {
    }

    static void register(Router router, Object controller) {
        Method[] methods = controller.getClass().getMethods();
        Arrays.sort(methods, (a, b) -> a.getName().compareTo(b.getName()));
        for (Method method : methods) {
            for (Mapping mapping : mappings(method)) {
                router.add(mapping.method(), mapping.pattern(), handler(controller, method, mapping.pattern()));
            }
        }
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

    private static Handler handler(Object controller, Method method, String pattern) {
        Set<String> pathVariables = pathVariables(pattern);
        List<Function<HttpRequest, Object>> resolvers = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            resolvers.add(resolver(method, parameter, pathVariables));
        }
        Function<Object, HttpResponse> responder = responder(method);
        method.setAccessible(true);

        return request -> {
            Object[] args = new Object[resolvers.size()];
            try {
                for (int i = 0; i < args.length; i++) {
                    args[i] = resolvers.get(i).apply(request);
                }
            } catch (BadParameterException e) {
                return HttpResponse.text(HttpStatus.BAD_REQUEST, e.getMessage());
            }

            Object result;
            try {
                result = method.invoke(controller, args);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof Exception cause) {
                    throw cause;
                }
                if (e.getCause() instanceof Error error) {
                    throw error;
                }
                throw e;
            }
            return responder.apply(result);
        };
    }

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
            return value(method, parameter, "header", name, fallback, request -> request.header(name));
        }
        if (parameter.getAnnotation(Query.class) instanceof Query query) {
            String name = query.value().isEmpty() ? nameOf(method, parameter) : query.value();
            String fallback = query.defaultValue().equals(Query.NONE) ? null : query.defaultValue();
            return value(method, parameter, "query", name, fallback, request -> request.query(name));
        }

        PathParam pathParam = parameter.getAnnotation(PathParam.class);
        String name = pathParam == null || pathParam.value().isEmpty() ? nameOf(method, parameter) : pathParam.value();
        if (pathParam != null && !pathVariables.contains(name)) {
            throw new IllegalArgumentException(describe(method) + ": no {" + name + "} in route pattern");
        }
        if (pathVariables.contains(name)) {
            return value(method, parameter, "path", name, null,
                    request -> Optional.ofNullable(request.pathParams().get(name)));
        }
        return value(method, parameter, "query", name, null, request -> request.query(name));
    }

    private static Function<HttpRequest, Object> body(Method method, Parameter parameter) {
        if (parameter.getType() == byte[].class) {
            return HttpRequest::body;
        }
        if (parameter.getType() == String.class) {
            return HttpRequest::bodyAsString;
        }
        throw new IllegalArgumentException(describe(method) + ": @Body must be byte[] or String");
    }

    private static Function<HttpRequest, Object> value(Method method, Parameter parameter, String source, String name,
                                                       String fallback, Function<HttpRequest, Optional<String>> lookup) {
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
            } catch (IllegalArgumentException e) {
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
            return result -> HttpResponse.ok((String) result);
        }
        if (type == void.class) {
            return _ -> HttpResponse.of(HttpStatus.NO_CONTENT);
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

    private static String headerName(String parameterName) {
        return parameterName.replaceAll("([a-z0-9])([A-Z])", "$1-$2");
    }

    private static Set<String> pathVariables(String pattern) {
        return Arrays.stream(pattern.split("/"))
                .filter(segment -> segment.startsWith("{") && segment.endsWith("}"))
                .map(segment -> segment.substring(1, segment.length() - 1))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String describe(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }
}
