package com.minihttp.core.route.binding;

import com.minihttp.convert.ScalarConverter;
import com.minihttp.convert.Types;
import com.minihttp.core.route.annotation.AnnotationDefaults;
import com.minihttp.core.route.annotation.Body;
import com.minihttp.core.route.annotation.Depends;
import com.minihttp.core.route.annotation.Header;
import com.minihttp.core.route.annotation.Path;
import com.minihttp.core.route.annotation.Query;
import com.minihttp.core.route.dependency.Dependency;
import com.minihttp.packet.HttpRequest;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 엔드포인트 파라미터 하나를 어떻게 채울지. 서버 시작 시 한 번 계산해 두고, 요청마다는 이것만 보고 값을 만든다.
 *
 * <p>판별 순서 (FastAPI와 같은 규칙):
 * <ol>
 *   <li>{@code @Depends}, {@code @Path}, {@code @Query}, {@code @Header}, {@code @Body}가 붙어 있으면 그대로 따른다.</li>
 *   <li>타입이 {@link HttpRequest}면 요청 객체를 넘긴다.</li>
 *   <li>이름이 경로의 {@code {변수}}와 같으면 path 파라미터.</li>
 *   <li>단일 값 타입(String, 숫자, enum, 날짜 ...)이거나 그것의 List/Set이면 query 파라미터.</li>
 *   <li>그 밖의 타입(DTO, record, Map, List&lt;DTO&gt; ...)이면 JSON body.</li>
 * </ol>
 *
 * <p>query/header는 기본이 필수 값이고, 필수 값이 없으면 422. {@code required = false}, {@code Optional<T>},
 * {@code defaultValue} 중 하나면 선택 값이다.
 * 선택 값이 없으면 원시 타입은 그 타입의 기본값, 참조 타입은 null, {@code Optional<T>}은 empty가 들어간다.
 * path는 항상 필수, body는 {@code Optional<T>}이 아니면 필수다.
 *
 * @param name           요청에서 찾을 이름 (path 변수 이름, 쿼리 키, 헤더 이름). body/request/dependency면 파라미터 이름.
 * @param source         값을 가져올 곳
 * @param valueType      {@code Optional<T>}을 벗긴 값의 타입
 * @param optional       파라미터 타입이 {@code Optional<T>}인지
 * @param defaultValue   값이 없을 때 쓸 기본값 문자열. 없으면 empty.
 * @param required       값이 없을 때 422로 응답할지
 * @param dependencyType source가 DEPENDENCY일 때 Dependency 클래스
 */
public record ParameterSpec(String name,
                            ParameterSource source,
                            Type valueType,
                            boolean optional,
                            Optional<String> defaultValue,
                            boolean required,
                            Class<? extends Dependency<?>> dependencyType) {

    public Class<?> rawValueType() {
        return Types.rawType(this.valueType);
    }

    /**
     * @param pathVariableNames 이 엔드포인트 경로의 {변수} 이름들
     * @throws IllegalStateException 파라미터 선언이 잘못됐을 때. 서버 시작을 막는다.
     */
    public static ParameterSpec from(Method handler, Parameter parameter, Set<String> pathVariableNames) {
        Type parameterType = parameter.getParameterizedType();
        boolean optional = Types.isOptional(parameterType);
        Type valueType = optional ? Types.typeArgument(parameterType, 0) : parameterType;

        if (parameter.isAnnotationPresent(Depends.class)) {
            return new ParameterSpec(parameter.getName(), ParameterSource.DEPENDENCY, valueType, optional,
                                     Optional.empty(), false, parameter.getAnnotation(Depends.class).value());
        }

        if (parameter.isAnnotationPresent(Path.class)) {
            String name = ParameterSpec.explicitOrParameterName(parameter.getAnnotation(Path.class).value(), handler, parameter);
            return ParameterSpec.simple(name, ParameterSource.PATH, valueType, optional, Optional.empty(), true);
        }

        if (parameter.isAnnotationPresent(Query.class)) {
            Query query = parameter.getAnnotation(Query.class);
            String name = ParameterSpec.explicitOrParameterName(query.value(), handler, parameter);
            Optional<String> defaultValue = ParameterSpec.defaultValueOf(query.defaultValue());
            boolean required = query.required() && !optional && defaultValue.isEmpty();
            return ParameterSpec.simple(name, ParameterSource.QUERY, valueType, optional, defaultValue, required);
        }

        if (parameter.isAnnotationPresent(Header.class)) {
            Header header = parameter.getAnnotation(Header.class);
            String name = header.value().isBlank()
                    ? ParameterSpec.toHeaderName(ParameterSpec.requireParameterName(handler, parameter))
                    : header.value();
            Optional<String> defaultValue = ParameterSpec.defaultValueOf(header.defaultValue());
            boolean required = header.required() && !optional && defaultValue.isEmpty();
            return ParameterSpec.simple(name, ParameterSource.HEADER, valueType, optional, defaultValue, required);
        }

        if (parameter.isAnnotationPresent(Body.class)) {
            return ParameterSpec.simple(parameter.getName(), ParameterSource.BODY, valueType, optional, Optional.empty(), !optional);
        }

        if (parameterType == HttpRequest.class) {
            return ParameterSpec.simple(parameter.getName(), ParameterSource.REQUEST, valueType, false, Optional.empty(), false);
        }

        if (ParameterSpec.isQueryable(valueType)) {
            String name = ParameterSpec.requireParameterName(handler, parameter);
            ParameterSource source = pathVariableNames.contains(name) ? ParameterSource.PATH : ParameterSource.QUERY;
            return ParameterSpec.simple(name, source, valueType, optional, Optional.empty(), source == ParameterSource.PATH || !optional);
        }

        return ParameterSpec.simple(parameter.getName(), ParameterSource.BODY, valueType, optional, Optional.empty(), !optional);
    }

    /** 쿼리/헤더 문자열로 표현할 수 있는 타입인지. 단일 값 타입이거나 그것의 컬렉션. */
    public static boolean isQueryable(Type type) {
        Class<?> rawType = Types.rawType(type);

        if (ScalarConverter.supports(rawType)) {
            return true;
        }

        return Types.isCollection(type) && ScalarConverter.supports(Types.rawType(Types.typeArgument(type, 0)));
    }

    private static ParameterSpec simple(String name,
                                        ParameterSource source,
                                        Type valueType,
                                        boolean optional,
                                        Optional<String> defaultValue,
                                        boolean required) {
        return new ParameterSpec(name, source, valueType, optional, defaultValue, required, null);
    }

    private static String explicitOrParameterName(String explicitName, Method handler, Parameter parameter) {
        return explicitName.isBlank() ? ParameterSpec.requireParameterName(handler, parameter) : explicitName;
    }

    /**
     * 컴파일할 때 {@code -parameters} 옵션이 없으면 파라미터 이름이 arg0, arg1이 되어 요청 값과 이어 줄 수 없다.
     * 조용히 엉뚱한 값을 찾는 대신 서버 시작을 막는다.
     */
    private static String requireParameterName(Method handler, Parameter parameter) {
        if (!parameter.isNamePresent()) {
            throw new IllegalStateException(
                    "Cannot read parameter names of " + handler + ". "
                            + "Compile with the '-parameters' option or give the name explicitly, e.g. @Query(\"limit\")");
        }

        return parameter.getName();
    }

    private static Optional<String> defaultValueOf(String annotationValue) {
        return AnnotationDefaults.NO_DEFAULT_VALUE.equals(annotationValue) ? Optional.empty() : Optional.of(annotationValue);
    }

    /** {@code userAgent → user-agent}, {@code xRequestId → x-request-id} */
    private static String toHeaderName(String parameterName) {
        StringBuilder headerName = new StringBuilder();

        for (int charIndex = 0; charIndex < parameterName.length(); charIndex++) {
            char current = parameterName.charAt(charIndex);

            if (Character.isUpperCase(current) && charIndex > 0) {
                headerName.append('-');
            }

            headerName.append(current == '_' ? '-' : Character.toLowerCase(current));
        }

        return headerName.toString().toLowerCase(Locale.ROOT);
    }
}
