package com.minihttp.json;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 자바 객체를 JSON 텍스트로 쓴다. FastAPI가 반환값을 jsonable_encoder로 바꾸는 자리.
 *
 * <pre>
 *   null, Optional.empty()       → null
 *   String, Character            → "문자열"
 *   Boolean                      → true / false
 *   Number                       → 숫자 (NaN, Infinity는 JSON에 없으므로 null)
 *   Enum                         → "NAME"
 *   byte[]                       → "Base64 문자열"
 *   Map                          → {키: 값}
 *   Iterable, 배열               → [...]
 *   record                       → 컴포넌트 이름 기준 {...}
 *   java.* 의 그 밖의 타입        → "toString()"  (LocalDateTime, UUID 등은 ISO 형식이 된다)
 *   그 외 객체                   → 상위 클래스부터 모은 인스턴스 필드 {...}  (static, transient 제외)
 * </pre>
 */
public final class JsonWriter {
    private static final Map<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    private final StringBuilder output = new StringBuilder();

    // 순환 참조를 만나면 무한 재귀 대신 예외를 던지기 위해 지금 쓰고 있는 객체를 기억한다.
    private final Set<Object> writingObjects = Collections.newSetFromMap(new IdentityHashMap<>());

    private JsonWriter() {
    }

    public static String write(Object value) {
        JsonWriter writer = new JsonWriter();
        writer.writeValue(value);
        return writer.output.toString();
    }

    private void writeValue(Object value) {
        switch (value) {
            case null -> this.output.append("null");
            case Optional<?> optional -> this.writeValue(optional.orElse(null));
            case String string -> this.writeString(string);
            case Character character -> this.writeString(String.valueOf(character));
            case Boolean bool -> this.output.append(bool);
            case Number number -> this.writeNumber(number);
            case Enum<?> enumConstant -> this.writeString(enumConstant.name());
            case byte[] bytes -> this.writeString(Base64.getEncoder().encodeToString(bytes));
            default -> this.writeComposite(value);
        }
    }

    private void writeComposite(Object value) {
        Class<?> valueType = value.getClass();

        // 컨테이너가 아닌 JDK 타입(LocalDateTime, UUID, URI ...)은 내부 필드에 접근할 수 없으니 문자열로 쓴다.
        boolean isContainer = value instanceof Map<?, ?> || value instanceof Iterable<?> || valueType.isArray();
        if (!isContainer && JsonWriter.isJdkType(valueType)) {
            this.writeString(value.toString());
            return;
        }

        if (!this.writingObjects.add(value)) {
            throw new IllegalArgumentException("Circular reference detected while writing " + valueType.getName());
        }

        try {
            if (value instanceof Map<?, ?> map) {
                this.writeMap(map);
            } else if (value instanceof Iterable<?> iterable) {
                this.writeIterable(iterable);
            } else if (valueType.isArray()) {
                this.writeArray(value);
            } else if (valueType.isRecord()) {
                this.writeRecord(value);
            } else {
                this.writeFields(value);
            }
        } finally {
            this.writingObjects.remove(value);
        }
    }

    private void writeMap(Map<?, ?> map) {
        this.output.append('{');
        boolean isFirst = true;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!isFirst) {
                this.output.append(',');
            }
            isFirst = false;

            Object key = entry.getKey();
            this.writeString(key instanceof Enum<?> enumKey ? enumKey.name() : String.valueOf(key));
            this.output.append(':');
            this.writeValue(entry.getValue());
        }

        this.output.append('}');
    }

    private void writeIterable(Iterable<?> iterable) {
        this.output.append('[');
        boolean isFirst = true;

        for (Object element : iterable) {
            if (!isFirst) {
                this.output.append(',');
            }
            isFirst = false;
            this.writeValue(element);
        }

        this.output.append(']');
    }

    private void writeArray(Object array) {
        this.output.append('[');
        int arrayLength = Array.getLength(array);

        for (int elementIndex = 0; elementIndex < arrayLength; elementIndex++) {
            if (elementIndex > 0) {
                this.output.append(',');
            }
            this.writeValue(Array.get(array, elementIndex));
        }

        this.output.append(']');
    }

    private void writeRecord(Object record) {
        this.output.append('{');
        boolean isFirst = true;

        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (!isFirst) {
                this.output.append(',');
            }
            isFirst = false;

            this.writeString(component.getName());
            this.output.append(':');
            this.writeValue(JsonWriter.readComponent(record, component));
        }

        this.output.append('}');
    }

    private void writeFields(Object object) {
        this.output.append('{');
        boolean isFirst = true;

        for (Field field : JsonWriter.serializableFields(object.getClass())) {
            if (!isFirst) {
                this.output.append(',');
            }
            isFirst = false;

            this.writeString(field.getName());
            this.output.append(':');
            this.writeValue(JsonWriter.readField(object, field));
        }

        this.output.append('}');
    }

    private void writeNumber(Number number) {
        if (number instanceof Double doubleValue && (doubleValue.isNaN() || doubleValue.isInfinite())) {
            this.output.append("null");
            return;
        }

        if (number instanceof Float floatValue && (floatValue.isNaN() || floatValue.isInfinite())) {
            this.output.append("null");
            return;
        }

        if (number instanceof BigDecimal decimal) {
            this.output.append(decimal.toPlainString());
            return;
        }

        this.output.append(number);
    }

    private void writeString(String string) {
        this.output.append('"');

        for (int charIndex = 0; charIndex < string.length(); charIndex++) {
            char current = string.charAt(charIndex);
            switch (current) {
                case '"' -> this.output.append("\\\"");
                case '\\' -> this.output.append("\\\\");
                case '\n' -> this.output.append("\\n");
                case '\r' -> this.output.append("\\r");
                case '\t' -> this.output.append("\\t");
                case '\b' -> this.output.append("\\b");
                case '\f' -> this.output.append("\\f");
                default -> {
                    if (current < 0x20) {
                        this.output.append(String.format("\\u%04x", (int) current));
                    } else {
                        this.output.append(current);
                    }
                }
            }
        }

        this.output.append('"');
    }

    /** 상위 클래스의 필드가 먼저 오도록 모은다. */
    public static List<Field> serializableFields(Class<?> type) {
        return FIELD_CACHE.computeIfAbsent(type, JsonWriter::collectFields);
    }

    private static List<Field> collectFields(Class<?> type) {
        Deque<Class<?>> hierarchy = new ArrayDeque<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.push(current);
        }

        List<Field> fields = new ArrayList<>();
        for (Class<?> current : hierarchy) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                    continue;
                }

                field.setAccessible(true);
                fields.add(field);
            }
        }

        return List.copyOf(fields);
    }

    private static Object readField(Object object, Field field) {
        try {
            return field.get(object);

        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot read field " + field, exception);
        }
    }

    private static Object readComponent(Object record, RecordComponent component) {
        try {
            Method accessor = component.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(record);

        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Cannot read record component " + component, exception);
        }
    }

    public static boolean isJdkType(Class<?> type) {
        String packageName = type.getPackageName();
        return packageName.startsWith("java.") || packageName.startsWith("javax.") || packageName.startsWith("jdk.");
    }
}
