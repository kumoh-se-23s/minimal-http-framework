package com.minihttp.core.route;

import com.minihttp.core.handler.Handler;
import com.minihttp.core.response.JsonResponse;
import com.minihttp.core.route.binding.ArgumentBinder;
import com.minihttp.core.route.binding.Endpoint;
import com.minihttp.json.JsonMapper;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import lombok.NonNull;

import java.lang.reflect.InvocationTargetException;


public final class RouteHandler implements Handler {
    private final RouterResolver routerResolver;
    private final ArgumentBinder argumentBinder;
    private final JsonMapper jsonMapper;

    private RouteHandler(RouterResolver routerResolver, ArgumentBinder argumentBinder, JsonMapper jsonMapper) {
        this.routerResolver = routerResolver;
        this.argumentBinder = argumentBinder;
        this.jsonMapper = jsonMapper;
    }

    public static RouteHandler create(@NonNull RouterResolver routerResolver,
                                      @NonNull ArgumentBinder argumentBinder,
                                      @NonNull JsonMapper jsonMapper) {
        return new RouteHandler(routerResolver, argumentBinder, jsonMapper);
    }

    @Override
    public HttpResponse handle(HttpRequest request) throws Exception {
        Endpoint endpoint = this.routerResolver.resolve(request);
        Object[] arguments = this.argumentBinder.bind(endpoint, request);
        Object result = RouteHandler.invoke(endpoint, arguments);
        return this.render(endpoint, result);
    }

    private HttpResponse render(Endpoint endpoint, Object result) {
        if (result instanceof HttpResponse response) {
            return response;
        }

        return JsonResponse.fromJsonText(endpoint.status(), this.jsonMapper.write(result));
    }

    private static Object invoke(Endpoint endpoint, Object[] arguments) throws Exception {
        try {
            return endpoint.handler().invoke(endpoint.router(), arguments);

        } catch (InvocationTargetException exception) {
            // 리플렉션을 이용하여, 엔드포인트가 던진 예외 그대로 핸들러로 전달.
            Throwable cause = exception.getCause();

            if (cause instanceof Error error) {
                throw error;
            }

            throw (Exception) cause;
        }
    }
}
