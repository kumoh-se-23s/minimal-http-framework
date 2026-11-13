package com.minihttp.convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 문자열 하나를 단일 값 타입으로 바꾼다. path, query, header 값과 JSON 문자열 값이 함께 쓴다.
 *
 * <p>지원 타입: String, 숫자(원시/래퍼), boolean, char, BigDecimal, BigInteger, enum,
 * LocalDate, LocalDateTime, LocalTime, UUID
 */
public final class ScalarConverter {
    private static final Set<Class<?>> SUPPORTED_TYPES = Set.of(
            String.class, Character.class,
            Integer.class, Long.class, Short.class, Byte.class,
            Double.class, Float.class, Boolean.class,
            BigDecimal.class, BigInteger.class,
            LocalDate.class, LocalDateTime.class, LocalTime.class,
            UUID.class
    );

    private static final Set<String> TRUE_TEXTS = Set.of("true", "1", "yes", "on", "y", "t");
    private static final Set<String> FALSE_TEXTS = Set.of("false", "0", "no", "off", "n", "f");

    private ScalarConverter() {
    }

    public static boolean supports(Class<?> type) {
        Class<?> wrappedType = Types.wrap(type);
        return wrappedType.isEnum() || SUPPORTED_TYPES.contains(wrappedType);
    }

    public static Object convert(String text, Class<?> type) {
        Class<?> wrappedType = Types.wrap(type);

        if (wrappedType == String.class) {
            return text;
        }

        if (wrappedType.isEnum()) {
            return ScalarConverter.convertEnum(text, wrappedType);
        }

        try {
            return ScalarConverter.convertValue(text.strip(), wrappedType);

        } catch (IllegalArgumentException | DateTimeParseException exception) {
            // NumberFormatException, UUID.fromString의 실패도 IllegalArgumentException이다.
            throw ScalarConverter.parsingFailure(wrappedType, text);
        }
    }

    private static Object convertValue(String text, Class<?> type) {
        if (type == Integer.class) {
            return Integer.parseInt(text);
        }

        if (type == Long.class) {
            return Long.parseLong(text);
        }

        if (type == Short.class) {
            return Short.parseShort(text);
        }

        if (type == Byte.class) {
            return Byte.parseByte(text);
        }

        if (type == Double.class) {
            return Double.parseDouble(text);
        }

        if (type == Float.class) {
            return Float.parseFloat(text);
        }

        if (type == Boolean.class) {
            return ScalarConverter.parseBoolean(text);
        }

        if (type == Character.class) {
            return ScalarConverter.parseCharacter(text);
        }

        if (type == BigDecimal.class) {
            return new BigDecimal(text);
        }

        if (type == BigInteger.class) {
            return new BigInteger(text);
        }

        if (type == LocalDate.class) {
            return LocalDate.parse(text);
        }

        if (type == LocalDateTime.class) {
            return LocalDateTime.parse(text);
        }

        if (type == LocalTime.class) {
            return LocalTime.parse(text);
        }

        if (type == UUID.class) {
            return UUID.fromString(text);
        }

        throw new IllegalStateException("Unsupported scalar type: " + type.getName());
    }

    private static boolean parseBoolean(String text) {
        String lowerText = text.toLowerCase(Locale.ROOT);

        if (TRUE_TEXTS.contains(lowerText)) {
            return true;
        }

        if (FALSE_TEXTS.contains(lowerText)) {
            return false;
        }

        throw new ConversionException("bool_parsing", "Input should be a valid boolean, unable to interpret input");
    }

    private static char parseCharacter(String text) {
        if (text.length() != 1) {
            throw new ConversionException("string_type", "Input should be a single character");
        }

        return text.charAt(0);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convertEnum(String text, Class<?> enumType) {
        Class<? extends Enum> castedEnumType = (Class<? extends Enum>) enumType;

        for (Enum constant : castedEnumType.getEnumConstants()) {
            if (constant.name().equals(text)) {
                return constant;
            }
        }

        // 정확히 일치하는 이름이 없을 때만 대소문자를 무시하고 한 번 더 찾는다.
        for (Enum constant : castedEnumType.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(text)) {
                return constant;
            }
        }

        String expectedNames = Arrays.stream(castedEnumType.getEnumConstants())
                                     .map(constant -> "'" + ((Enum) constant).name() + "'")
                                     .collect(Collectors.joining(", "));
        throw new ConversionException("enum", "Input should be " + expectedNames);
    }

    private static ConversionException parsingFailure(Class<?> type, String text) {
        if (type == Integer.class || type == Long.class || type == Short.class
                || type == Byte.class || type == BigInteger.class) {
            return new ConversionException("int_parsing", "Input should be a valid integer, unable to parse string as an integer");
        }

        if (type == Double.class || type == Float.class) {
            return new ConversionException("float_parsing", "Input should be a valid number, unable to parse string as a number");
        }

        if (type == BigDecimal.class) {
            return new ConversionException("decimal_parsing", "Input should be a valid decimal");
        }

        if (type == LocalDate.class) {
            return new ConversionException("date_parsing", "Input should be a valid date in the format YYYY-MM-DD");
        }

        if (type == LocalDateTime.class) {
            return new ConversionException("datetime_parsing","Input should be a valid datetime in the format YYYY-MM-DDTHH:MM[:SS]");
        }

        if (type == LocalTime.class) {
            return new ConversionException("time_parsing", "Input should be a valid time in the format HH:MM[:SS]");
        }

        if (type == UUID.class) {
            return new ConversionException("uuid_parsing", "Input should be a valid UUID");
        }

        return new ConversionException("value_error", "Invalid value: " + text);
    }
}
