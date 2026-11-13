package com.minihttp.core.route.binding;

import com.minihttp.convert.ConversionException;
import com.minihttp.convert.ScalarConverter;
import com.minihttp.convert.Types;
import com.minihttp.core.route.dependency.DependencyRegistry;
import com.minihttp.core.route.exception.RequestValidationException;
import com.minihttp.core.route.exception.ValidationError;
import com.minihttp.json.JsonError;
import com.minihttp.json.JsonException;
import com.minihttp.json.JsonMapper;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.protocol.HttpHeaderField;
import lombok.NonNull;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 요청에서 엔드포인트 메서드의 인자 배열을 만든다.
 *
 * <p>FastAPI처럼 모든 파라미터를 끝까지 검사해서, 문제가 있는 값을 한 번에 모아 {@link RequestValidationException}(422)으로 던진다.
 */
public final class ArgumentBinder {
    private static final String MISSING_ERROR_TYPE = "missing";
    private static final String MISSING_MESSAGE = "Field required";

    private final DependencyRegistry dependencyRegistry;
    private final JsonMapper jsonMapper;

    private ArgumentBinder(DependencyRegistry dependencyRegistry, JsonMapper jsonMapper) {
        this.dependencyRegistry = dependencyRegistry;
        this.jsonMapper = jsonMapper;
    }

    public static ArgumentBinder create(@NonNull DependencyRegistry dependencyRegistry, @NonNull JsonMapper jsonMapper) {
        return new ArgumentBinder(dependencyRegistry, jsonMapper);
    }

    /**
     * @throws RequestValidationException 요청 값이 파라미터에 맞지 않을 때
     * @throws Exception                  Dependency가 던진 예외는 그대로 올려 보낸다 (401, 403 등)
     */
    public Object[] bind(Endpoint endpoint, HttpRequest request) throws Exception {
        List<ParameterSpec> parameters = endpoint.parameters();
        Object[] arguments = new Object[parameters.size()];
        List<ValidationError> errors = new ArrayList<>();

        for (int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
            ParameterSpec parameter = parameters.get(parameterIndex);
            arguments[parameterIndex] = this.bindArgument(parameter, request, errors);
        }

        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }

        return arguments;
    }

    private Object bindArgument(ParameterSpec parameter, HttpRequest request, List<ValidationError> errors) throws Exception {
        return switch (parameter.source()) {
            case REQUEST -> request;
            case DEPENDENCY -> this.wrapOptional(parameter, this.dependencyRegistry.get(parameter.dependencyType()).resolve(request));
            case PATH -> this.bindTextValues(parameter, request.pathParam(parameter.name()).stream().toList(), errors);
            case QUERY -> this.bindTextValues(parameter, request.queryParams(parameter.name()), errors);
            case HEADER -> this.bindTextValues(parameter, request.header().getAll(parameter.name()), errors);
            case BODY -> this.bindBody(parameter, request, errors);
        };
    }

    // ===================================================================== path, query, header

    /**
     * 같은 이름으로 여러 값이 올 수 있다 ({@code ?state=A&state=B}).
     * 컬렉션 파라미터는 전부 받고, 단일 값 파라미터는 마지막 값을 쓴다. (FastAPI/Starlette와 같음)
     */
    private Object bindTextValues(ParameterSpec parameter, List<String> textValues, List<ValidationError> errors) {
        List<String> effectiveValues = textValues;

        if (effectiveValues.isEmpty() && parameter.defaultValue().isPresent()) {
            effectiveValues = List.of(parameter.defaultValue().get());
        }

        if (effectiveValues.isEmpty()) {
            if (parameter.required()) {
                errors.add(ArgumentBinder.error(parameter, MISSING_ERROR_TYPE, MISSING_MESSAGE));
                return ArgumentBinder.placeholder(parameter);
            }
            return this.wrapOptional(parameter, null);
        }

        Type valueType = parameter.valueType();
        if (Types.isCollection(valueType)) {
            return this.wrapOptional(parameter, this.convertCollection(parameter, effectiveValues, errors));
        }

        try {
            return this.wrapOptional(parameter, ScalarConverter.convert(effectiveValues.getLast(), parameter.rawValueType()));

        } catch (ConversionException exception) {
            errors.add(ArgumentBinder.error(parameter, exception.errorType(), exception.getMessage()));
            return ArgumentBinder.placeholder(parameter);
        }
    }

    private Collection<Object> convertCollection(ParameterSpec parameter, List<String> textValues, List<ValidationError> errors) {
        Class<?> elementType = Types.rawType(Types.typeArgument(parameter.valueType(), 0));
        Collection<Object> collection = Types.newCollection(parameter.rawValueType());

        for (int valueIndex = 0; valueIndex < textValues.size(); valueIndex++) {
            try {
                collection.add(ScalarConverter.convert(textValues.get(valueIndex), elementType));

            } catch (ConversionException exception) {
                errors.add(new ValidationError(
                        exception.errorType(),
                        List.of(parameter.source().locationName(), parameter.name(), valueIndex),
                        exception.getMessage()
                ));
            }
        }

        return collection;
    }

    // ===================================================================== body

    private Object bindBody(ParameterSpec parameter, HttpRequest request, List<ValidationError> errors) {
        byte[] bodyBytes = request.body().bytes();

        if (parameter.rawValueType() == byte[].class) {
            return this.wrapOptional(parameter, bodyBytes);
        }

        if (bodyBytes.length == 0) {
            if (parameter.optional()) {
                return Optional.empty();
            }
            errors.add(new ValidationError(MISSING_ERROR_TYPE, List.of("body"), MISSING_MESSAGE));
            return ArgumentBinder.placeholder(parameter);
        }

        Optional<String> maybeContentType = request.header().get(HttpHeaderField.CONTENT_TYPE);
        if (maybeContentType.isPresent() && !ArgumentBinder.isJsonContentType(maybeContentType.get())) {
            errors.add(new ValidationError(
                    "model_attributes_type", List.of("body"), "Content-Type must be application/json"));
            return ArgumentBinder.placeholder(parameter);
        }

        try {
            String jsonText = new String(bodyBytes, StandardCharsets.UTF_8);
            return this.wrapOptional(parameter, this.jsonMapper.read(jsonText, parameter.valueType()));

        } catch (JsonException exception) {
            for (JsonError jsonError : exception.errors()) {
                List<Object> location = new ArrayList<>();
                location.add("body");
                location.addAll(jsonError.location());
                errors.add(new ValidationError(jsonError.errorType(), location, jsonError.message()));
            }
            return ArgumentBinder.placeholder(parameter);
        }
    }

    /** {@code application/json}, {@code application/json; charset=utf-8}, {@code application/problem+json} */
    private static boolean isJsonContentType(String contentType) {
        String mimeType = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return mimeType.equals("application/json") || (mimeType.startsWith("application/") && mimeType.endsWith("+json"));
    }

    // ===================================================================== 공통

    private Object wrapOptional(ParameterSpec parameter, Object value) {
        return parameter.optional() ? Optional.ofNullable(value) : value;
    }

    /**
     * 검증 실패한 자리에 넣어 둘 값. 어차피 메서드는 호출되지 않지만,
     * 원시 타입 자리에 null을 넣어 두면 나중에 실수로 호출됐을 때 원인을 찾기 어려우니 기본값을 넣는다.
     */
    private static Object placeholder(ParameterSpec parameter) {
        Class<?> rawType = parameter.rawValueType();

        if (!parameter.optional() && rawType.isPrimitive()) {
            return Array.get(Array.newInstance(rawType, 1), 0);
        }

        return parameter.optional() ? Optional.empty() : null;
    }

    private static ValidationError error(ParameterSpec parameter, String errorType, String message) {
        return new ValidationError(errorType, List.of(parameter.source().locationName(), parameter.name()), message);
    }
}
