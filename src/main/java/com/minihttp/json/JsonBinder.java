package com.minihttp.json;

import com.minihttp.convert.ConversionException;
import com.minihttp.convert.ScalarConverter;
import com.minihttp.convert.Types;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link JsonParser}가 만든 객체 트리를 원하는 자바 타입으로 채운다. FastAPI에서 pydantic 모델 검증이 하는 자리.
 *
 * <ul>
 *   <li>일반 클래스: 기본 생성자로 만든 뒤 같은 이름의 필드에 값을 넣는다. (Lombok {@code @NoArgsConstructor} DTO)</li>
 *   <li>record: 컴포넌트 이름으로 값을 찾아 canonical 생성자를 호출한다.</li>
 *   <li>JSON에 없는 필드는 건드리지 않고, 모르는 키는 무시한다. (pydantic 기본 동작)</li>
 *   <li>{@code DataCollectionResponse<ProjectData>}처럼 타입 변수가 있는 필드도 실제 타입으로 바꿔서 채운다.</li>
 *   <li>실패한 필드가 있어도 끝까지 진행해서, 문제가 있는 필드를 모두 모아 한 번에 알려준다.</li>
 * </ul>
 */
public final class JsonBinder {
    private static final Map<Class<?>, Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    /** 에러를 기록한 뒤 감싸고 있는 컨테이너까지 되감기 위한 신호. 에러 내용은 {@link #errors}에 있다. */
    private static final class BindingFailure extends RuntimeException {
        private BindingFailure() {
            super(null, null, false, false);
        }
    }

    private final List<Object> location = new ArrayList<>();
    private final List<JsonError> errors = new ArrayList<>();

    private JsonBinder() {
    }

    public static Object bind(Object tree, Type targetType) {
        JsonBinder binder = new JsonBinder();
        Object result = null;

        try {
            result = binder.bindValue(tree, targetType, Map.of());
        } catch (BindingFailure ignored) {
            // 에러는 이미 errors에 들어 있다.
        }

        if (!binder.errors.isEmpty()) {
            throw new JsonException(binder.errors);
        }

        return result;
    }

    private Object bindValue(Object tree, Type targetType, Map<TypeVariable<?>, Type> typeBindings) {
        Type resolvedType = JsonBinder.resolveVariable(targetType, typeBindings);
        Class<?> rawType = Types.rawType(resolvedType);

        if (rawType == Optional.class) {
            return Optional.ofNullable(this.bindValue(tree, Types.typeArgument(resolvedType, 0), typeBindings));
        }

        if (tree == null) {
            if (rawType.isPrimitive()) {
                throw this.fail(JsonBinder.typeErrorName(rawType), JsonBinder.typeErrorMessage(rawType));
            }
            return null;
        }

        if (rawType == Object.class) {
            return tree;
        }

        if (rawType == byte[].class) {
            return this.bindBytes(tree);
        }

        if (ScalarConverter.supports(rawType)) {
            return this.bindScalar(tree, rawType);
        }

        if (Collection.class.isAssignableFrom(rawType)) {
            return this.bindCollection(tree, rawType, Types.typeArgument(resolvedType, 0), typeBindings);
        }

        if (rawType.isArray()) {
            return this.bindArray(tree, rawType, typeBindings);
        }

        if (Map.class.isAssignableFrom(rawType)) {
            return this.bindMap(tree, Types.typeArgument(resolvedType, 0), Types.typeArgument(resolvedType, 1), typeBindings);
        }

        if (rawType.isInterface() || Modifier.isAbstract(rawType.getModifiers()) || JsonWriter.isJdkType(rawType)) {
            throw new IllegalStateException("Cannot bind JSON to type " + resolvedType.getTypeName());
        }

        if (rawType.isRecord()) {
            return this.bindRecord(tree, rawType, JsonBinder.typeBindingsFor(resolvedType, typeBindings));
        }

        return this.bindObject(tree, rawType, JsonBinder.typeBindingsFor(resolvedType, typeBindings));
    }

    // ===================================================================== 단일 값

    private Object bindScalar(Object tree, Class<?> rawType) {
        Class<?> wrappedType = Types.wrap(rawType);

        if (tree instanceof String text) {
            // pydantic처럼 문자열 필드에는 문자열만 받지만, 숫자/날짜/enum 필드에는 문자열 표기를 허용한다.
            try {
                return ScalarConverter.convert(text, wrappedType);
            } catch (ConversionException exception) {
                throw this.fail(exception.errorType(), exception.getMessage());
            }
        }

        if (tree instanceof BigDecimal number) {
            return this.bindNumber(number, wrappedType);
        }

        if (tree instanceof Boolean bool && wrappedType == Boolean.class) {
            return bool;
        }

        throw this.fail(JsonBinder.typeErrorName(wrappedType), JsonBinder.typeErrorMessage(wrappedType));
    }

    private Object bindNumber(BigDecimal number, Class<?> wrappedType) {
        try {
            if (wrappedType == Integer.class) {
                return number.intValueExact();
            }

            if (wrappedType == Long.class) {
                return number.longValueExact();
            }

            if (wrappedType == Short.class) {
                return number.shortValueExact();
            }

            if (wrappedType == Byte.class) {
                return number.byteValueExact();
            }

            if (wrappedType == BigInteger.class) {
                return number.toBigIntegerExact();
            }

        } catch (ArithmeticException exception) {
            if (number.stripTrailingZeros().scale() > 0) {
                throw this.fail("int_from_float", "Input should be a valid integer, got a number with a fractional part");
            }
            throw this.fail("int_parsing", "Input should be a valid integer, value is out of range");
        }

        if (wrappedType == Double.class) {
            return number.doubleValue();
        }

        if (wrappedType == Float.class) {
            return number.floatValue();
        }

        if (wrappedType == BigDecimal.class) {
            return number;
        }

        if (wrappedType == Boolean.class && (number.compareTo(BigDecimal.ZERO) == 0 || number.compareTo(BigDecimal.ONE) == 0)) {
            return number.compareTo(BigDecimal.ONE) == 0;
        }

        throw this.fail(JsonBinder.typeErrorName(wrappedType), JsonBinder.typeErrorMessage(wrappedType));
    }

    private byte[] bindBytes(Object tree) {
        if (!(tree instanceof String text)) {
            throw this.fail("bytes_type", "Input should be a valid Base64 string");
        }

        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException exception) {
            throw this.fail("base64_decode", "Base64 decoding error: " + exception.getMessage());
        }
    }

    // ===================================================================== 컨테이너

    private Collection<Object> bindCollection(Object tree,
                                              Class<?> collectionType,
                                              Type elementType,
                                              Map<TypeVariable<?>, Type> typeBindings) {
        if (!(tree instanceof List<?> elements)) {
            throw this.fail("list_type", "Input should be a valid list");
        }

        Collection<Object> collection = Types.newCollection(collectionType);
        for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
            Object element = this.bindChild(elementIndex, elements.get(elementIndex), elementType, typeBindings);
            collection.add(element);
        }

        this.throwIfChildFailed();
        return collection;
    }

    private Object bindArray(Object tree, Class<?> arrayType, Map<TypeVariable<?>, Type> typeBindings) {
        if (!(tree instanceof List<?> elements)) {
            throw this.fail("list_type", "Input should be a valid list");
        }

        Class<?> componentType = arrayType.getComponentType();
        Object array = Array.newInstance(componentType, elements.size());

        for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
            Object element = this.bindChild(elementIndex, elements.get(elementIndex), componentType, typeBindings);
            if (element != null || !componentType.isPrimitive()) {
                Array.set(array, elementIndex, element);
            }
        }

        this.throwIfChildFailed();
        return array;
    }

    private Map<Object, Object> bindMap(Object tree, Type keyType, Type valueType, Map<TypeVariable<?>, Type> typeBindings) {
        if (!(tree instanceof Map<?, ?> entries)) {
            throw this.fail("dict_type", "Input should be a valid dictionary");
        }

        Class<?> rawKeyType = Types.rawType(JsonBinder.resolveVariable(keyType, typeBindings));
        Map<Object, Object> map = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            String keyText = (String) entry.getKey();
            Object key = this.bindChild(keyText, keyText, rawKeyType == Object.class ? String.class : rawKeyType, typeBindings);
            Object value = this.bindChild(keyText, entry.getValue(), valueType, typeBindings);
            map.put(key, value);
        }

        this.throwIfChildFailed();
        return map;
    }

    // ===================================================================== 객체

    private Object bindObject(Object tree, Class<?> objectType, Map<TypeVariable<?>, Type> typeBindings) {
        if (!(tree instanceof Map<?, ?> fieldValues)) {
            throw this.fail("model_attributes_type", "Input should be a valid dictionary or object to extract fields from");
        }

        Object instance = JsonBinder.newInstance(objectType);

        for (Field field : JsonWriter.serializableFields(objectType)) {
            if (!fieldValues.containsKey(field.getName())) {
                continue;
            }

            Object value = this.bindChild(field.getName(), fieldValues.get(field.getName()), field.getGenericType(), typeBindings);
            if (value != null || !field.getType().isPrimitive()) {
                JsonBinder.writeField(instance, field, value);
            }
        }

        this.throwIfChildFailed();
        return instance;
    }

    private Object bindRecord(Object tree, Class<?> recordType, Map<TypeVariable<?>, Type> typeBindings) {
        if (!(tree instanceof Map<?, ?> componentValues)) {
            throw this.fail("model_attributes_type", "Input should be a valid dictionary or object to extract fields from");
        }

        RecordComponent[] components = recordType.getRecordComponents();
        Object[] arguments = new Object[components.length];
        Class<?>[] argumentTypes = new Class<?>[components.length];

        for (int componentIndex = 0; componentIndex < components.length; componentIndex++) {
            RecordComponent component = components[componentIndex];
            argumentTypes[componentIndex] = component.getType();

            Object value = componentValues.containsKey(component.getName())
                    ? this.bindChild(component.getName(), componentValues.get(component.getName()), component.getGenericType(), typeBindings)
                    : null;

            arguments[componentIndex] = value == null ? JsonBinder.defaultValue(component.getType()) : value;
        }

        this.throwIfChildFailed();
        return this.invokeConstructor(recordType, argumentTypes, arguments);
    }

    // ===================================================================== 에러 처리

    /** 하위 값 하나를 바인딩한다. 실패해도 에러만 기록하고 null을 돌려줘서 형제 값들을 계속 검사하게 한다. */
    private Object bindChild(Object locationKey, Object tree, Type targetType, Map<TypeVariable<?>, Type> typeBindings) {
        this.location.add(locationKey);
        try {
            return this.bindValue(tree, targetType, typeBindings);

        } catch (BindingFailure failure) {
            return null;

        } finally {
            this.location.removeLast();
        }
    }

    /** 하위 값 중 하나라도 실패했으면 이 컨테이너도 실패로 처리한다. */
    private void throwIfChildFailed() {
        if (!this.errors.isEmpty()) {
            throw new BindingFailure();
        }
    }

    private BindingFailure fail(String errorType, String message) {
        this.errors.add(new JsonError(errorType, this.location, message));
        return new BindingFailure();
    }

    private static String typeErrorName(Class<?> type) {
        Class<?> wrappedType = Types.wrap(type);

        if (wrappedType == String.class || wrappedType == Character.class) {
            return "string_type";
        }

        if (wrappedType == Boolean.class) {
            return "bool_type";
        }

        if (wrappedType == Integer.class || wrappedType == Long.class || wrappedType == Short.class
                || wrappedType == Byte.class || wrappedType == BigInteger.class) {
            return "int_type";
        }

        if (wrappedType == Double.class || wrappedType == Float.class) {
            return "float_type";
        }

        if (wrappedType == BigDecimal.class) {
            return "decimal_type";
        }

        if (wrappedType.isEnum()) {
            return "enum";
        }

        return "value_error";
    }

    private static String typeErrorMessage(Class<?> type) {
        return switch (JsonBinder.typeErrorName(type)) {
            case "string_type" -> "Input should be a valid string";
            case "bool_type" -> "Input should be a valid boolean";
            case "int_type" -> "Input should be a valid integer";
            case "float_type" -> "Input should be a valid number";
            case "decimal_type" -> "Input should be a valid decimal";
            case "enum" -> "Input should be a valid enum name";
            default -> "Input should be a valid " + Types.wrap(type).getSimpleName();
        };
    }

    // ===================================================================== 타입 변수

    private static Type resolveVariable(Type type, Map<TypeVariable<?>, Type> typeBindings) {
        Type resolvedType = type;

        while (resolvedType instanceof TypeVariable<?> typeVariable && typeBindings.containsKey(typeVariable)) {
            resolvedType = typeBindings.get(typeVariable);
        }

        if (resolvedType instanceof WildcardType wildcardType) {
            return wildcardType.getUpperBounds()[0];
        }

        return resolvedType;
    }

    /**
     * {@code DataCollectionResponse<ProjectData>}를 채울 때, 필드 타입에 나오는 {@code D}가 ProjectData임을 알 수 있도록
     * 클래스와 상위 클래스의 타입 변수 → 실제 타입 표를 만든다.
     */
    private static Map<TypeVariable<?>, Type> typeBindingsFor(Type type, Map<TypeVariable<?>, Type> outerBindings) {
        Map<TypeVariable<?>, Type> typeBindings = new HashMap<>();
        Class<?> rawType = Types.rawType(type);

        if (type instanceof ParameterizedType parameterizedType) {
            JsonBinder.putTypeArguments(typeBindings, rawType, parameterizedType, outerBindings);
        }

        for (Class<?> current = rawType; current != null && current != Object.class; current = current.getSuperclass()) {
            if (current.getGenericSuperclass() instanceof ParameterizedType superType) {
                JsonBinder.putTypeArguments(typeBindings, Types.rawType(superType), superType, typeBindings);
            }
        }

        return typeBindings;
    }

    private static void putTypeArguments(Map<TypeVariable<?>, Type> typeBindings,
                                         Class<?> rawType,
                                         ParameterizedType parameterizedType,
                                         Map<TypeVariable<?>, Type> argumentBindings) {
        TypeVariable<?>[] typeParameters = rawType.getTypeParameters();
        Type[] typeArguments = parameterizedType.getActualTypeArguments();

        for (int parameterIndex = 0; parameterIndex < typeParameters.length; parameterIndex++) {
            Type resolvedArgument = JsonBinder.resolveVariable(typeArguments[parameterIndex], argumentBindings);
            typeBindings.put(typeParameters[parameterIndex], resolvedArgument);
        }
    }

    // ===================================================================== 리플렉션

    private static Object newInstance(Class<?> objectType) {
        Constructor<?> constructor = CONSTRUCTOR_CACHE.computeIfAbsent(objectType, JsonBinder::findDefaultConstructor);

        try {
            return constructor.newInstance();

        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to create " + objectType.getName(), exception);
        }
    }

    private static Constructor<?> findDefaultConstructor(Class<?> objectType) {
        try {
            Constructor<?> constructor = objectType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor;

        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(
                    objectType.getName() + " needs a no-argument constructor to be bound from JSON", exception);
        }
    }

    private Object invokeConstructor(Class<?> recordType, Class<?>[] argumentTypes, Object[] arguments) {
        try {
            Constructor<?> constructor = recordType.getDeclaredConstructor(argumentTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);

        } catch (InvocationTargetException exception) {
            // record의 compact 생성자 검증 실패는 요청 값 문제로 본다. (pydantic의 validator 실패와 같은 자리)
            Throwable cause = exception.getCause();
            if (cause instanceof IllegalArgumentException || cause instanceof NullPointerException) {
                throw this.fail("value_error", "Value error, " + cause.getMessage());
            }
            throw new IllegalStateException("Failed to create " + recordType.getName(), exception);

        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create " + recordType.getName(), exception);
        }
    }

    private static void writeField(Object instance, Field field, Object value) {
        try {
            field.set(instance, value);

        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot write field " + field, exception);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }

        return Array.get(Array.newInstance(type, 1), 0);
    }
}
