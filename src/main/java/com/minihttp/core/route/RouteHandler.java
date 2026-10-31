package com.minihttp.core.route;

import com.minihttp.core.handler.Handler;
import com.minihttp.core.route.annotation.Path;
import com.minihttp.core.route.annotation.Query;
import com.minihttp.core.route.binding.Endpoint;
import com.minihttp.core.route.exception.HttpStatusException;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.ContentType;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.DefaultHttpBody;
import com.minihttp.packet.structure.DefaultHttpHeader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class RouteHandler implements Handler {
    private final RouterResolver routerResolver;

    private RouteHandler(RouterResolver routerResolver) {
        this.routerResolver = routerResolver;
    }

    public static RouteHandler create(RouterResolver routerResolver) {
        return new RouteHandler(routerResolver);
    }

    @Override
    public HttpResponse handle(HttpRequest request) throws Exception {
        try {
            Endpoint endpoint = this.routerResolver.resolve(request);
            Object[] arguments = this.resolveArguments(endpoint, request);
            return this.invoke(endpoint, arguments);

        } catch (HttpStatusException exception) {
            return this.createErrorResponse(exception);
        }
    }

    private Object[] resolveArguments(Endpoint endpoint, HttpRequest request) {
        Parameter[] parameters = endpoint.handler().getParameters();
        Object[] arguments = new Object[parameters.length];

        for (int parameterIndex = 0; parameterIndex < parameters.length; parameterIndex++) {
            arguments[parameterIndex] = this.resolveArgument(endpoint, parameters[parameterIndex], request);
        }

        return arguments;
    }

    private Object resolveArgument(Endpoint endpoint, Parameter parameter, HttpRequest request) {
        if (parameter.isAnnotationPresent(Path.class)) {
            String value = request.pathParam(parameter.getName()).orElseThrow();
            return RouteHandler.convert(parameter, value);
        }

        if (parameter.isAnnotationPresent(Query.class)) {
            Optional<String> maybeValue = request.queryParam(parameter.getName());

            if (maybeValue.isEmpty()) {
                return RouteHandler.missingValue(parameter);
            }

            return RouteHandler.convert(parameter, maybeValue.get());
        }

        if (parameter.getType() == HttpRequest.class) {
            return request;
        }

        throw new IllegalStateException(
                "Unsupported parameter " + parameter.getName() + " in " + endpoint.handler());
    }

    private static Object missingValue(Parameter parameter) {
        if (parameter.getType().isPrimitive()) {
            throw new HttpStatusException(
                    HttpStatus.BAD_REQUEST, "Missing required query parameter: " + parameter.getName());
        }

        return null;
    }

    private static Object convert(Parameter parameter, String value) {
        Class<?> type = parameter.getType();

        try {
            if (type == String.class) {
                return value;
            }

            if (type == int.class || type == Integer.class) {
                return Integer.parseInt(value);
            }

            if (type == long.class || type == Long.class) {
                return Long.parseLong(value);
            }

            if (type == boolean.class || type == Boolean.class) {
                return RouteHandler.parseBoolean(parameter, value);
            }

        } catch (NumberFormatException exception) {
            throw new HttpStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid value for " + parameter.getName() + ": " + value);
        }

        throw new IllegalStateException("Unsupported parameter type " + type.getName() + " of " + parameter.getName());
    }

    private static boolean parseBoolean(Parameter parameter, String value) {
        if (value.equalsIgnoreCase("true")) {
            return true;
        }

        if (value.equalsIgnoreCase("false")) {
            return false;
        }

        throw new HttpStatusException(
                HttpStatus.BAD_REQUEST, "Invalid value for " + parameter.getName() + ": " + value);
    }

    private HttpResponse invoke(Endpoint endpoint, Object[] arguments) throws Exception {
        try {
            return (HttpResponse) endpoint.handler().invoke(endpoint.router(), arguments);

        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();

            if (cause instanceof Error error) {
                throw error;
            }

            throw (Exception) cause;
        }
    }

    private HttpResponse createErrorResponse(HttpStatusException exception) {
        HttpStatus status = exception.status();
        HttpResponse response = new HttpResponse(
                HttpVersion.HTTP_1_1,
                new DefaultHttpHeader(),
                new DefaultHttpBody(status.reason().getBytes(StandardCharsets.UTF_8)),
                status
        );
        response.contentType(ContentType.TEXT_PLAIN);
        exception.headers().forEach(response::addHeaderField);
        return response;
    }
}
