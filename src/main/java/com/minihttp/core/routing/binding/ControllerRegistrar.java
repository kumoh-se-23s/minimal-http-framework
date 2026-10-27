package com.minihttp.core.routing.binding;

import com.minihttp.core.handler.Handler;
import com.minihttp.core.routing.RouteDefinition;
import com.minihttp.core.routing.annotation.Delete;
import com.minihttp.core.routing.annotation.Get;
import com.minihttp.core.routing.annotation.Patch;
import com.minihttp.core.routing.annotation.Post;
import com.minihttp.core.routing.annotation.Put;
import com.minihttp.core.routing.support.RoutingResponses;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpStatus;
import lombok.NonNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * 어노테이션이 붙은 메서드를 Handler로 감싸 {@link RouteDefinition} 목록으로 만든다.
 *
 * <p>리플렉션 분석(어노테이션, 파라미터 타입, 반환 타입)은 등록할 때 한 번만 한다.
 * 잘못된 선언은 요청이 올 때가 아니라 여기서 IllegalArgumentException으로 드러난다.
 *
 * <p>반환 타입: {@link HttpResponse}는 그대로, {@code String}은 200 text/plain, {@code void}는 204.
 */
public final class ControllerRegistrar {
    private record Mapping(HttpMethod method, String pattern) {
    }

    private ControllerRegistrar() {
    }

    public static List<RouteDefinition> scan(@NonNull Object controller) {
        // getMethods() 순서는 보장되지 않는다. 이름순으로 정렬해 에러 메시지가 실행마다 같게 한다
        List<RouteDefinition> definitions = new ArrayList<>();
        Arrays.stream(controller.getClass().getMethods())
              .sorted(Comparator.comparing(Method::getName))
              .forEach(method -> mappings(method).forEach(mapping -> definitions.add(
                      new RouteDefinition(mapping.method(), mapping.pattern(), handler(controller, method, mapping.pattern())))));
        return definitions;
    }

    static String describe(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    private static List<Mapping> mappings(Method method) {
        List<Mapping> mappings = new ArrayList<>();
        for (Annotation annotation : method.getAnnotations()) {
            switch (annotation) {
                case Get get -> mappings.add(new Mapping(HttpMethod.GET, get.value()));
                case Post post -> mappings.add(new Mapping(HttpMethod.POST, post.value()));
                case Put put -> mappings.add(new Mapping(HttpMethod.PUT, put.value()));
                case Patch patch -> mappings.add(new Mapping(HttpMethod.PATCH, patch.value()));
                case Delete delete -> mappings.add(new Mapping(HttpMethod.DELETE, delete.value()));
                default -> {
                }
            }
        }
        return mappings;
    }

    private static Handler handler(Object controller, Method method, String pattern) {
        List<ArgumentResolver> resolvers = ArgumentResolvers.forMethod(method, pattern);
        Function<Object, HttpResponse> responder = responder(method);
        method.setAccessible(true);

        return request -> {
            Object[] args;
            try {
                args = resolveAll(resolvers, request);
            } catch (BadParameterException exception) {
                return RoutingResponses.text(HttpStatus.BAD_REQUEST, exception.getMessage());
            }
            return responder.apply(invoke(controller, method, args));
        };
    }

    private static Object[] resolveAll(List<ArgumentResolver> resolvers, HttpRequest request) {
        Object[] args = new Object[resolvers.size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = resolvers.get(i).resolve(request);
        }
        return args;
    }

    private static Object invoke(Object controller, Method method, Object[] args) throws Exception {
        try {
            return method.invoke(controller, args);
        } catch (InvocationTargetException exception) {
            // 리플렉션 포장을 벗겨 컨트롤러가 던진 예외를 그대로 올린다 (Connection이 500으로 바꾼다)
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw exception;
        }
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
}
