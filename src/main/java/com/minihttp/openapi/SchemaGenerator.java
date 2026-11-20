package com.minihttp.openapi;

import com.minihttp.convert.ScalarConverter;
import com.minihttp.convert.Types;
import com.minihttp.json.JsonWriter;
import lombok.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SchemaGenerator {
    private enum Kind {
        OPTIONAL,
        NULL,
        BYTES,
        SCALAR,
        MAP,
        ARRAY,
        NUMBER,
        ANY,
        STRING,
        COMPONENT
    }

    private record Property(String name, ResolvedType type) {
    }

    private record ComponentName(String key, Optional<String> title) {
    }

    private static final String REFERENCE_PREFIX = "#/components/schemas/";

    private final Set<String> reservedNames;
    private final Set<ResolvedType> componentTypes = new LinkedHashSet<>();
    private final Map<ResolvedType, ComponentName> componentNames = new HashMap<>();

    private SchemaGenerator(Set<String> reservedNames) {
        this.reservedNames = Set.copyOf(reservedNames);
    }

    public static SchemaGenerator create(@NonNull Set<String> reservedNames) {
        return new SchemaGenerator(reservedNames);
    }

    public static String reference(@NonNull String componentName) {
        return REFERENCE_PREFIX + componentName;
    }

    public void collect(@NonNull Type type) {
        this.collect(ResolvedType.of(type));
    }

    public void collect(@NonNull ResolvedType type) {
        switch (SchemaGenerator.kindOf(type.rawType())) {
            case OPTIONAL, MAP -> this.collect(SchemaGenerator.valueTypeOf(type));
            case ARRAY -> this.collect(SchemaGenerator.itemTypeOf(type));
            case COMPONENT -> this.collectComponent(type);
            default -> {
            }
        }
    }

    public void assignNames() {
        Map<String, List<ResolvedType>> typesBySimpleName = this.componentTypes.stream()
                                                                               .collect(Collectors.groupingBy(ResolvedType::simpleName));

        for (ResolvedType componentType : this.componentTypes) {
            String simpleName = componentType.simpleName();
            boolean isCollided = typesBySimpleName.get(simpleName).size() > 1 || this.reservedNames.contains(simpleName);
            String key = isCollided ? componentType.qualifiedName() : simpleName;
            Optional<String> title = componentType.arguments().isEmpty()
                    ? Optional.empty()
                    : Optional.of(isCollided ? componentType.qualifiedTitle() : componentType.simpleTitle());

            this.componentNames.put(componentType, new ComponentName(key, title));
        }
    }

    public Map<String, Object> schema(@NonNull Type type, boolean nullable) {
        return this.schema(ResolvedType.of(type), nullable);
    }

    public Map<String, Object> schema(@NonNull ResolvedType type, boolean nullable) {
        Map<String, Object> schema = new LinkedHashMap<>();

        switch (SchemaGenerator.kindOf(type.rawType())) {
            case OPTIONAL -> {
                return this.schema(SchemaGenerator.valueTypeOf(type), true);
            }
            case NULL -> schema.put("type", "null");
            case BYTES -> {
                schema.put("type", "string");
                schema.put("contentEncoding", "base64");
            }
            case SCALAR -> schema.putAll(SchemaGenerator.scalarSchema(type.rawType()));
            case MAP -> {
                schema.put("type", "object");
                schema.put("additionalProperties", this.nestedSchema(SchemaGenerator.valueTypeOf(type)));
            }
            case ARRAY -> {
                schema.put("type", "array");
                schema.put("items", this.nestedSchema(SchemaGenerator.itemTypeOf(type)));
            }
            case NUMBER -> schema.put("type", "number");
            case ANY -> {
                return schema;
            }
            case STRING -> schema.put("type", "string");
            case COMPONENT -> schema.put("$ref", SchemaGenerator.reference(this.componentNameOf(type).key()));
        }

        return nullable ? SchemaGenerator.toNullable(schema) : schema;
    }

    public Map<String, Object> components() {
        Map<String, Object> components = new TreeMap<>();

        for (ResolvedType componentType : this.componentTypes) {
            ComponentName componentName = this.componentNameOf(componentType);
            Map<String, Object> schema = new LinkedHashMap<>();
            componentName.title().ifPresent(title -> schema.put("title", title));
            schema.put("type", "object");

            Map<String, Object> properties = new LinkedHashMap<>();
            for (Property property : SchemaGenerator.propertiesOf(componentType)) {
                properties.put(property.name(), this.nestedSchema(property.type()));
            }

            if (!properties.isEmpty()) {
                schema.put("properties", properties);
            }

            components.put(componentName.key(), schema);
        }

        return new LinkedHashMap<>(components);
    }

    private void collectComponent(ResolvedType type) {
        if (!this.componentTypes.add(type)) {
            return;
        }

        for (Property property : SchemaGenerator.propertiesOf(type)) {
            this.collect(property.type());
        }
    }

    private Map<String, Object> nestedSchema(ResolvedType type) {
        return this.schema(type, !type.rawType().isPrimitive());
    }

    private ComponentName componentNameOf(ResolvedType type) {
        ComponentName componentName = this.componentNames.get(type);

        if (componentName == null) {
            throw new IllegalStateException("Schema type was not collected before naming: " + type.qualifiedTitle());
        }

        return componentName;
    }

    private static Kind kindOf(Class<?> rawType) {
        if (rawType == Optional.class) {
            return Kind.OPTIONAL;
        }

        if (rawType == void.class || rawType == Void.class) {
            return Kind.NULL;
        }

        if (rawType == byte[].class) {
            return Kind.BYTES;
        }

        if (ScalarConverter.supports(rawType)) {
            return Kind.SCALAR;
        }

        if (Map.class.isAssignableFrom(rawType)) {
            return Kind.MAP;
        }

        if (Iterable.class.isAssignableFrom(rawType) || rawType.isArray()) {
            return Kind.ARRAY;
        }

        if (Number.class.isAssignableFrom(rawType)) {
            return Kind.NUMBER;
        }

        if (rawType == Object.class || rawType.isInterface() || Modifier.isAbstract(rawType.getModifiers())) {
            return Kind.ANY;
        }

        if (JsonWriter.isJdkType(rawType)) {
            return Kind.STRING;
        }

        return Kind.COMPONENT;
    }

    private static ResolvedType valueTypeOf(ResolvedType type) {
        return Map.class.isAssignableFrom(type.rawType()) ? type.argument(1) : type.argument(0);
    }

    private static ResolvedType itemTypeOf(ResolvedType type) {
        if (type.rawType().isArray()) {
            return ResolvedType.of(type.rawType().getComponentType());
        }

        return type.argument(0);
    }

    private static Map<String, Object> scalarSchema(Class<?> rawType) {
        Class<?> wrappedType = Types.wrap(rawType);
        Map<String, Object> schema = new LinkedHashMap<>();

        if (wrappedType.isEnum()) {
            schema.put("type", "string");
            schema.put("enum", Arrays.stream(wrappedType.getEnumConstants())
                                     .map(constant -> ((Enum<?>) constant).name())
                                     .toList());
            return schema;
        }

        if (wrappedType == Integer.class || wrappedType == Short.class || wrappedType == Byte.class) {
            schema.put("type", "integer");
            schema.put("format", "int32");
        } else if (wrappedType == Long.class) {
            schema.put("type", "integer");
            schema.put("format", "int64");
        } else if (wrappedType == BigInteger.class) {
            schema.put("type", "integer");
        } else if (wrappedType == Double.class) {
            schema.put("type", "number");
            schema.put("format", "double");
        } else if (wrappedType == Float.class) {
            schema.put("type", "number");
            schema.put("format", "float");
        } else if (wrappedType == BigDecimal.class) {
            schema.put("type", "number");
        } else if (wrappedType == Boolean.class) {
            schema.put("type", "boolean");
        } else if (wrappedType == Character.class) {
            schema.put("type", "string");
            schema.put("minLength", 1);
            schema.put("maxLength", 1);
        } else if (wrappedType == UUID.class) {
            schema.put("type", "string");
            schema.put("format", "uuid");
        } else if (wrappedType == LocalDate.class) {
            schema.put("type", "string");
            schema.put("format", "date");
        } else {
            schema.put("type", "string");
        }

        return schema;
    }

    private static Map<String, Object> toNullable(Map<String, Object> schema) {
        if (schema.isEmpty() || "null".equals(schema.get("type"))) {
            return schema;
        }

        if (schema.containsKey("$ref")) {
            Map<String, Object> nullableSchema = new LinkedHashMap<>();
            nullableSchema.put("anyOf", List.of(schema, Map.of("type", "null")));
            return nullableSchema;
        }

        Map<String, Object> nullableSchema = new LinkedHashMap<>(schema);
        nullableSchema.put("type", List.of(schema.get("type"), "null"));

        if (schema.get("enum") instanceof List<?> enumValues) {
            List<Object> nullableValues = new ArrayList<>(enumValues);
            nullableValues.add(null);
            nullableSchema.put("enum", nullableValues);
        }

        return nullableSchema;
    }

    private static List<Property> propertiesOf(ResolvedType type) {
        Class<?> rawType = type.rawType();

        if (rawType.isRecord()) {
            Map<TypeVariable<?>, ResolvedType> bindings = type.typeBindings();
            return Arrays.stream(rawType.getRecordComponents())
                         .map(component -> SchemaGenerator.propertyOf(component, bindings))
                         .toList();
        }

        Map<TypeVariable<?>, ResolvedType> bindings = SchemaGenerator.hierarchyBindings(type);
        List<Property> properties = new ArrayList<>();
        for (Field field : JsonWriter.serializableFields(rawType)) {
            properties.add(new Property(field.getName(), ResolvedType.of(field.getGenericType(), bindings)));
        }

        return properties;
    }

    private static Property propertyOf(RecordComponent component, Map<TypeVariable<?>, ResolvedType> bindings) {
        return new Property(component.getName(), ResolvedType.of(component.getGenericType(), bindings));
    }

    private static Map<TypeVariable<?>, ResolvedType> hierarchyBindings(ResolvedType type) {
        Map<TypeVariable<?>, ResolvedType> bindings = new HashMap<>(type.typeBindings());

        for (Class<?> current = type.rawType(); current.getSuperclass() != null; current = current.getSuperclass()) {
            ResolvedType superType = ResolvedType.of(current.getGenericSuperclass(), bindings);
            bindings.putAll(superType.typeBindings());
        }

        return bindings;
    }
}
