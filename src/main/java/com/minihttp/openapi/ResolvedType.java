package com.minihttp.openapi;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ResolvedType(Class<?> rawType, List<ResolvedType> arguments) {
    private static final ResolvedType OBJECT = new ResolvedType(Object.class, List.of());

    public ResolvedType {
        arguments = List.copyOf(arguments);
    }

    public static ResolvedType of(Type type) {
        return ResolvedType.of(type, Map.of());
    }

    public static ResolvedType of(Type type, Map<TypeVariable<?>, ResolvedType> bindings) {
        return switch (type) {
            case Class<?> clazz -> new ResolvedType(clazz, List.of());
            case ParameterizedType parameterizedType -> ResolvedType.ofParameterized(parameterizedType, bindings);
            case WildcardType wildcardType -> ResolvedType.of(wildcardType.getUpperBounds()[0], bindings);
            case TypeVariable<?> typeVariable -> bindings.getOrDefault(typeVariable, OBJECT);
            case GenericArrayType arrayType -> ResolvedType.ofArray(arrayType, bindings);
            default -> OBJECT;
        };
    }

    public ResolvedType argument(int argumentIndex) {
        if (argumentIndex >= this.arguments.size()) {
            return OBJECT;
        }

        return this.arguments.get(argumentIndex);
    }

    public Map<TypeVariable<?>, ResolvedType> typeBindings() {
        TypeVariable<?>[] typeParameters = this.rawType.getTypeParameters();
        Map<TypeVariable<?>, ResolvedType> bindings = new HashMap<>();

        for (int parameterIndex = 0; parameterIndex < typeParameters.length; parameterIndex++) {
            bindings.put(typeParameters[parameterIndex], this.argument(parameterIndex));
        }

        return bindings;
    }

    public String simpleName() {
        return this.name(false, "_", "_", "_");
    }

    public String qualifiedName() {
        return this.name(true, "_", "_", "_");
    }

    public String simpleTitle() {
        return this.name(false, "<", ", ", ">");
    }

    public String qualifiedTitle() {
        return this.name(true, "<", ", ", ">");
    }

    private String name(boolean qualified, String open, String separator, String close) {
        String baseName = ResolvedType.baseName(this.rawType, qualified);

        if (this.arguments.isEmpty()) {
            return baseName;
        }

        return baseName + open + this.arguments.stream()
                                              .map(argument -> argument.name(qualified, open, separator, close))
                                              .collect(Collectors.joining(separator)) + close;
    }

    private static String baseName(Class<?> type, boolean qualified) {
        if (type.isArray()) {
            return ResolvedType.baseName(type.getComponentType(), qualified) + "Array";
        }

        return qualified ? type.getName().replace('$', '.') : type.getSimpleName();
    }

    private static ResolvedType ofParameterized(ParameterizedType parameterizedType,
                                                Map<TypeVariable<?>, ResolvedType> bindings) {
        List<ResolvedType> arguments = new ArrayList<>();
        for (Type argument : parameterizedType.getActualTypeArguments()) {
            arguments.add(ResolvedType.of(argument, bindings));
        }

        return new ResolvedType((Class<?>) parameterizedType.getRawType(), arguments);
    }

    private static ResolvedType ofArray(GenericArrayType arrayType, Map<TypeVariable<?>, ResolvedType> bindings) {
        ResolvedType componentType = ResolvedType.of(arrayType.getGenericComponentType(), bindings);
        return new ResolvedType(componentType.rawType().arrayType(), List.of());
    }
}
