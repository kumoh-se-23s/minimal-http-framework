package com.minihttp.core.route.binding;

import com.minihttp.core.route.annotation.HttpMapping;
import com.minihttp.core.route.annotation.Path;
import com.minihttp.core.route.annotation.Query;
import com.minihttp.core.route.template.PathTemplate;
import com.minihttp.packet.protocol.HttpMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;

public record Endpoint(
        PathTemplate pathTemplate,
        HttpMethod httpMethod,
        Map<String, Parameter> pathParamMap,
        Map<String, Parameter> queryParamMap,
        Method handler,
        Type handlerReturnType
) {
    public static Optional<Endpoint> from(Method handler) throws ReflectiveOperationException {
        for (Annotation meta : handler.getAnnotations()) {
            Optional<HttpMapping> maybeHttpMapping = Endpoint.getHttpMapping(meta);

            if (maybeHttpMapping.isEmpty()) {
                continue;
            }

            String path = meta.annotationType().getMethod("value").invoke(meta).toString();
            HttpMethod httpMethod = maybeHttpMapping.get().value();
            return Optional.of(
                    new Endpoint(
                            PathTemplate.create(path),
                            httpMethod,
                            Endpoint.getPathParamMap(path, handler),
                            Endpoint.getQueryParamMap(handler),
                            handler,
                            handler.getGenericReturnType()
                    )
            );
        }

        return Optional.empty();
    }

    private static Optional<HttpMapping> getHttpMapping(Annotation annotation) {
        return Optional.ofNullable(annotation.annotationType().getAnnotation(HttpMapping.class));
    }



    private static Map<String, Parameter> getPathParamMap(String path, Method handler) {
        List<String> pathVars = PathTemplate.extractPathVariableNames(path);
        Set<String> templateNames = new HashSet<>(pathVars);

        if (templateNames.size() != pathVars.size()) {
            throw new IllegalStateException("Duplicate path variables in " + path + " of " + handler);
        }

        Map<String, Parameter> pathParamMap = new HashMap<>();
        for (Parameter parameter : handler.getParameters()) {
            if (parameter.isAnnotationPresent(Path.class)) {
                pathParamMap.put(parameter.getName(), parameter);
            }
        }

        if (!templateNames.equals(pathParamMap.keySet())) {
            throw new IllegalStateException(
                    "Path variables " + templateNames + " do not match @PathParam parameters "
                            + pathParamMap.keySet() + " in " + handler);
        }

        return Map.copyOf(pathParamMap);
    }

    private static Map<String, Parameter> getQueryParamMap(Method handler) {
        Map<String, Parameter> queryParamMap = new HashMap<>();
        for (Parameter parameter : handler.getParameters()) {
            if (!parameter.isAnnotationPresent(Query.class)) {
                continue;
            }

            if (parameter.isAnnotationPresent(Path.class)) {
                throw new IllegalStateException(
                        "Parameter " + parameter.getName() + " cannot be both @PathParam and @QueryParam in " + handler);
            }

            queryParamMap.put(parameter.getName(), parameter);
        }

        return Map.copyOf(queryParamMap);
    }
}
