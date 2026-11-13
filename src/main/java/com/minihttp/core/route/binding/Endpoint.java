package com.minihttp.core.route.binding;

import com.minihttp.convert.ScalarConverter;
import com.minihttp.core.route.BaseRouter;
import com.minihttp.core.route.RoutePaths;
import com.minihttp.core.route.annotation.HttpMapping;
import com.minihttp.core.route.template.PathTemplate;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpStatus;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record Endpoint(
        BaseRouter router,
        PathTemplate pathTemplate,
        HttpMethod httpMethod,
        Method handler,
        HttpStatus status,
        List<ParameterSpec> parameters
) {
    public Endpoint {
        parameters = List.copyOf(parameters);
    }

    public static Optional<Endpoint> from(BaseRouter router, Method handler) throws ReflectiveOperationException {
        for (Annotation annotation : handler.getAnnotations()) {
            HttpMapping httpMapping = annotation.annotationType().getAnnotation(HttpMapping.class);

            if (httpMapping == null) {
                continue;
            }

            String path = (String) annotation.annotationType().getMethod("value").invoke(annotation);
            HttpStatus status = (HttpStatus) annotation.annotationType().getMethod("status").invoke(annotation);
            PathTemplate pathTemplate = PathTemplate.create(RoutePaths.join(router.prefix(), path));

            handler.setAccessible(true);
            return Optional.of(new Endpoint(
                    router,
                    pathTemplate,
                    httpMapping.value(),
                    handler,
                    status,
                    Endpoint.readParameters(handler, pathTemplate)
            ));
        }

        return Optional.empty();
    }

    public String describe() {
        return this.httpMethod + " " + this.pathTemplate + " → "
                + this.handler.getDeclaringClass().getSimpleName() + "#" + this.handler.getName();
    }

    private static List<ParameterSpec> readParameters(Method handler, PathTemplate pathTemplate) {
        List<String> pathVariableNames = pathTemplate.variableNames();
        Set<String> uniquePathVariableNames = new HashSet<>(pathVariableNames);

        if (uniquePathVariableNames.size() != pathVariableNames.size()) {
            throw new IllegalStateException("Duplicate path variables in " + pathTemplate + " of " + handler);
        }

        List<ParameterSpec> parameters = new ArrayList<>();
        for (Parameter parameter : handler.getParameters()) {
            parameters.add(ParameterSpec.from(handler, parameter, uniquePathVariableNames));
        }

        Endpoint.validatePathParameters(handler, uniquePathVariableNames, parameters);
        Endpoint.validateSingleBody(handler, parameters);
        Endpoint.validateValueTypes(handler, parameters);
        return parameters;
    }

    private static void validatePathParameters(Method handler, Set<String> pathVariableNames, List<ParameterSpec> parameters) {
        Set<String> boundPathNames = new HashSet<>();

        for (ParameterSpec parameter : parameters) {
            if (parameter.source() != ParameterSource.PATH) {
                continue;
            }

            if (!pathVariableNames.contains(parameter.name())) {
                throw new IllegalStateException(
                        "Path parameter '" + parameter.name() + "' is not in the path " + pathVariableNames + " of " + handler);
            }

            if (!boundPathNames.add(parameter.name())) {
                throw new IllegalStateException("Path variable '" + parameter.name() + "' is bound twice in " + handler);
            }

            if (parameter.optional()) {
                throw new IllegalStateException(
                        "Path parameter '" + parameter.name() + "' cannot be Optional because it is always present in " + handler);
            }
        }

        if (!boundPathNames.equals(pathVariableNames)) {
            Set<String> unboundNames = new HashSet<>(pathVariableNames);
            unboundNames.removeAll(boundPathNames);
            throw new IllegalStateException("Path variables " + unboundNames + " have no matching parameter in " + handler);
        }
    }

    private static void validateSingleBody(Method handler, List<ParameterSpec> parameters) {
        long bodyCount = parameters.stream()
                                   .filter(parameter -> parameter.source() == ParameterSource.BODY)
                                   .count();

        if (bodyCount > 1) {
            throw new IllegalStateException("Only one body parameter is allowed, but " + bodyCount + " found in " + handler);
        }
    }

    private static void validateValueTypes(Method handler, List<ParameterSpec> parameters) {
        for (ParameterSpec parameter : parameters) {
            switch (parameter.source()) {
                case PATH -> {
                    if (!ScalarConverter.supports(parameter.rawValueType())) {
                        throw new IllegalStateException(
                                "Path parameter '" + parameter.name() + "' must be a single value type in " + handler);
                    }
                }
                case QUERY, HEADER -> {
                    if (!ParameterSpec.isQueryable(parameter.valueType())) {
                        throw new IllegalStateException(
                                "Parameter '" + parameter.name() + "' of type " + parameter.valueType().getTypeName()
                                        + " cannot be read from " + parameter.source().locationName() + " in " + handler);
                    }
                }
            }
        }
    }
}
