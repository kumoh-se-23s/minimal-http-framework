package com.minihttp.openapi;

import com.minihttp.config.OpenApiConfig;
import com.minihttp.convert.ScalarConverter;
import com.minihttp.convert.Types;
import com.minihttp.core.response.JsonResponse;
import com.minihttp.core.response.PlainTextResponse;
import com.minihttp.core.route.binding.Endpoint;
import com.minihttp.core.route.binding.ParameterSource;
import com.minihttp.core.route.binding.ParameterSpec;
import com.minihttp.json.JsonWriter;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.ContentType;
import com.minihttp.packet.protocol.HttpStatus;
import lombok.NonNull;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class OpenApiDocument {
    private static final String OPENAPI_VERSION = "3.1.0";
    private static final String VALIDATION_ERROR_RESPONSE = "ValidationErrorResponse";
    private static final String VALIDATION_ERROR_DETAIL = "ValidationErrorDetail";
    private static final String CUSTOM_RESPONSE_DESCRIPTION = "Response";
    private static final String VALIDATION_ERROR_DESCRIPTION = "Validation Error";
    private static final Set<ParameterSource> PARAMETER_SOURCES = EnumSet.of(
            ParameterSource.PATH, ParameterSource.QUERY, ParameterSource.HEADER);
    private static final Set<ParameterSource> VALIDATED_SOURCES = EnumSet.of(
            ParameterSource.PATH, ParameterSource.QUERY, ParameterSource.HEADER, ParameterSource.BODY);

    private final OpenApiConfig config;
    private final List<Endpoint> endpoints;
    private final boolean validationErrorsDocumented;
    private final SchemaGenerator schemaGenerator = SchemaGenerator.create(
            Set.of(VALIDATION_ERROR_RESPONSE, VALIDATION_ERROR_DETAIL));

    private boolean validationErrorUsed;

    private OpenApiDocument(OpenApiConfig config, List<Endpoint> endpoints, boolean validationErrorsDocumented) {
        this.config = config;
        this.endpoints = List.copyOf(endpoints);
        this.validationErrorsDocumented = validationErrorsDocumented;
    }

    public static OpenApiDocument create(@NonNull OpenApiConfig config,
                                         @NonNull List<Endpoint> endpoints,
                                         boolean validationErrorsDocumented) {
        return new OpenApiDocument(config, endpoints, validationErrorsDocumented);
    }

    public String toJson() {
        return JsonWriter.write(this.toTree());
    }

    private Map<String, Object> toTree() {
        this.collectSchemas();
        this.schemaGenerator.assignNames();

        Map<String, Object> paths = this.paths();
        Map<String, Object> schemas = this.schemaGenerator.components();

        if (this.validationErrorUsed) {
            schemas.put(VALIDATION_ERROR_RESPONSE, OpenApiDocument.validationErrorResponseSchema());
            schemas.put(VALIDATION_ERROR_DETAIL, OpenApiDocument.validationErrorDetailSchema());
        }

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("openapi", OPENAPI_VERSION);
        document.put("info", this.info());
        document.put("paths", paths);

        if (!schemas.isEmpty()) {
            document.put("components", Map.of("schemas", schemas));
        }

        return document;
    }

    private void collectSchemas() {
        for (Endpoint endpoint : this.endpoints) {
            for (ParameterSpec parameter : endpoint.parameters()) {
                if (VALIDATED_SOURCES.contains(parameter.source())) {
                    this.schemaGenerator.collect(parameter.valueType());
                }
            }

            if (!HttpResponse.class.isAssignableFrom(endpoint.handler().getReturnType())) {
                this.schemaGenerator.collect(endpoint.handler().getGenericReturnType());
            }
        }
    }

    private Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", this.config.title());
        info.put("version", this.config.version());
        this.config.description().ifPresent(description -> info.put("description", description));
        return info;
    }

    private Map<String, Object> paths() {
        Map<Endpoint, String> operationIds = OpenApiDocument.operationIds(this.endpoints);
        Map<String, Object> paths = new LinkedHashMap<>();

        for (Endpoint endpoint : this.endpoints) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pathItem = (Map<String, Object>) paths.computeIfAbsent(
                    endpoint.pathTemplate().path(), ignored -> new LinkedHashMap<String, Object>());
            pathItem.put(endpoint.httpMethod().name().toLowerCase(Locale.ROOT),
                         this.operation(endpoint, operationIds.get(endpoint)));
        }

        return paths;
    }

    private Map<String, Object> operation(Endpoint endpoint, String operationId) {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("tags", List.of(OpenApiDocument.routerName(endpoint)));
        operation.put("operationId", operationId);

        List<Map<String, Object>> parameters = endpoint.parameters().stream()
                                                       .filter(parameter -> PARAMETER_SOURCES.contains(parameter.source()))
                                                       .map(this::parameter)
                                                       .toList();
        if (!parameters.isEmpty()) {
            operation.put("parameters", parameters);
        }

        endpoint.parameters().stream()
                .filter(parameter -> parameter.source() == ParameterSource.BODY)
                .findFirst()
                .ifPresent(body -> operation.put("requestBody", this.requestBody(body)));

        operation.put("responses", this.responses(endpoint));
        return operation;
    }

    private Map<String, Object> parameter(ParameterSpec parameter) {
        Map<String, Object> schema = Types.isCollection(parameter.valueType())
                ? OpenApiDocument.arraySchema(this.schemaGenerator.schema(ResolvedType.of(parameter.valueType()).argument(0), false))
                : this.schemaGenerator.schema(parameter.valueType(), false);

        OpenApiDocument.defaultValueOf(parameter).ifPresent(defaultValue -> schema.put("default", defaultValue));

        Map<String, Object> parameterObject = new LinkedHashMap<>();
        parameterObject.put("name", parameter.name());
        parameterObject.put("in", parameter.source().locationName());
        parameterObject.put("required", parameter.source() == ParameterSource.PATH || parameter.required());
        parameterObject.put("schema", schema);
        return parameterObject;
    }

    private Map<String, Object> requestBody(ParameterSpec body) {
        Map<String, Object> content = body.rawValueType() == byte[].class
                ? OpenApiDocument.content(ContentType.APPLICATION_OCTET_STREAM, Map.of())
                : OpenApiDocument.content(ContentType.APPLICATION_JSON, this.schemaGenerator.schema(body.valueType(), body.optional()));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("required", !body.optional());
        requestBody.put("content", content);
        return requestBody;
    }

    private Map<String, Object> responses(Endpoint endpoint) {
        Map<String, Object> responses = new LinkedHashMap<>();
        Class<?> returnType = endpoint.handler().getReturnType();

        if (HttpResponse.class.isAssignableFrom(returnType)) {
            responses.put("default", OpenApiDocument.customResponse(returnType));
        } else {
            responses.put(String.valueOf(endpoint.status().code()), this.successResponse(endpoint));
        }

        boolean hasValidatedParameter = endpoint.parameters().stream()
                                                .anyMatch(parameter -> VALIDATED_SOURCES.contains(parameter.source()));
        if (this.validationErrorsDocumented && hasValidatedParameter) {
            this.validationErrorUsed = true;
            responses.put(String.valueOf(HttpStatus.UNPROCESSABLE_CONTENT.code()), OpenApiDocument.validationErrorResponse());
        }

        return responses;
    }

    private Map<String, Object> successResponse(Endpoint endpoint) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", endpoint.status().reason());

        if (endpoint.status().permitsBody()) {
            Map<String, Object> schema = this.schemaGenerator.schema(endpoint.handler().getGenericReturnType(), false);
            response.put("content", OpenApiDocument.content(ContentType.APPLICATION_JSON, schema));
        }

        return response;
    }

    private static Map<String, Object> customResponse(Class<?> returnType) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", CUSTOM_RESPONSE_DESCRIPTION);

        if (PlainTextResponse.class.isAssignableFrom(returnType)) {
            response.put("content", OpenApiDocument.content(ContentType.TEXT_PLAIN, Map.of("type", "string")));
        } else if (JsonResponse.class.isAssignableFrom(returnType)) {
            response.put("content", OpenApiDocument.content(ContentType.APPLICATION_JSON, Map.of()));
        }

        return response;
    }

    private static Map<String, Object> validationErrorResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", VALIDATION_ERROR_DESCRIPTION);
        response.put("content", OpenApiDocument.content(
                ContentType.APPLICATION_JSON, Map.of("$ref", SchemaGenerator.reference(VALIDATION_ERROR_RESPONSE))));
        return response;
    }

    private static Map<String, Object> validationErrorResponseSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("detail", OpenApiDocument.arraySchema(Map.of("$ref", SchemaGenerator.reference(VALIDATION_ERROR_DETAIL))));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    private static Map<String, Object> validationErrorDetailSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", Map.of("type", "string"));
        properties.put("loc", OpenApiDocument.arraySchema(
                Map.of("anyOf", List.of(Map.of("type", "string"), Map.of("type", "integer")))));
        properties.put("msg", Map.of("type", "string"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("type", "loc", "msg"));
        return schema;
    }

    private static Map<String, Object> arraySchema(Map<String, Object> itemSchema) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", itemSchema);
        return schema;
    }

    private static Map<String, Object> content(ContentType contentType, Map<String, Object> schema) {
        return Map.of(contentType.mimeType(), Map.of("schema", schema));
    }

    private static Optional<Object> defaultValueOf(ParameterSpec parameter) {
        if (parameter.defaultValue().isEmpty() || !ScalarConverter.supports(parameter.rawValueType())) {
            return Optional.empty();
        }

        try {
            return Optional.of(ScalarConverter.convert(parameter.defaultValue().get(), parameter.rawValueType()));

        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Map<Endpoint, String> operationIds(List<Endpoint> endpoints) {
        Map<String, Long> methodNameCounts = endpoints.stream()
                                                      .collect(Collectors.groupingBy(
                                                              endpoint -> endpoint.handler().getName(),
                                                              Collectors.counting()));

        Map<Endpoint, String> operationIds = new HashMap<>();
        Set<String> usedIds = new HashSet<>();

        for (Endpoint endpoint : endpoints) {
            String methodName = endpoint.handler().getName();
            String baseId = methodNameCounts.get(methodName) > 1
                    ? OpenApiDocument.routerName(endpoint) + "_" + methodName
                    : methodName;

            String operationId = baseId;
            for (int suffix = 2; !usedIds.add(operationId); suffix++) {
                operationId = baseId + "_" + suffix;
            }

            operationIds.put(endpoint, operationId);
        }

        return operationIds;
    }

    private static String routerName(Endpoint endpoint) {
        Class<?> routerType = endpoint.router().getClass();
        return routerType.getSimpleName().isEmpty() ? routerType.getName() : routerType.getSimpleName();
    }
}
