package com.minihttp.convert;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 리플렉션 {@link Type}을 다루는 공용 도구.
 */
public final class Types {
    private static final Map<Class<?>, Class<?>> WRAPPERS = Map.of(
            int.class, Integer.class,
            long.class, Long.class,
            short.class, Short.class,
            byte.class, Byte.class,
            double.class, Double.class,
            float.class, Float.class,
            boolean.class, Boolean.class,
            char.class, Character.class
    );

    private Types() {
    }

    /** {@code List<String>} → {@code List}, {@code T extends Number} → {@code Number}. */
    public static Class<?> rawType(Type type) {
        return switch (type) {
            case Class<?> clazz -> clazz;
            case ParameterizedType parameterizedType -> Types.rawType(parameterizedType.getRawType());
            case WildcardType wildcardType -> Types.rawType(wildcardType.getUpperBounds()[0]);
            case TypeVariable<?> typeVariable -> Types.rawType(typeVariable.getBounds()[0]);
            case GenericArrayType arrayType -> Types.rawType(arrayType.getGenericComponentType()).arrayType();
            default -> Object.class;
        };
    }

    /** 원시 타입이면 래퍼 타입으로, 아니면 그대로. */
    public static Class<?> wrap(Class<?> type) {
        return type.isPrimitive() ? WRAPPERS.get(type) : type;
    }

    /** 제네릭 타입의 n번째 타입 인자. 정보가 없으면 Object. */
    public static Type typeArgument(Type type, int argumentIndex) {
        if (type instanceof ParameterizedType parameterizedType) {
            return parameterizedType.getActualTypeArguments()[argumentIndex];
        }
        return Object.class;
    }

    public static boolean isOptional(Type type) {
        return Types.rawType(type) == Optional.class;
    }

    public static boolean isCollection(Type type) {
        return Collection.class.isAssignableFrom(Types.rawType(type));
    }

    /** 컬렉션 타입에 맞는 빈 인스턴스. 인터페이스면 대표 구현체를 고른다. */
    public static Collection<Object> newCollection(Class<?> collectionType) {
        if (SortedSet.class.isAssignableFrom(collectionType)) {
            return new TreeSet<>();
        }

        if (Set.class.isAssignableFrom(collectionType)) {
            return new LinkedHashSet<>();
        }

        if (collectionType.isAssignableFrom(ArrayList.class)) {
            return new ArrayList<>();
        }

        throw new IllegalArgumentException("Unsupported collection type: " + collectionType.getName());
    }
}
