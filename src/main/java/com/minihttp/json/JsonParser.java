package com.minihttp.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RFC 8259 JSON 텍스트를 자바 기본 객체 트리로 파싱한다.
 *
 * <pre>
 *   object  → LinkedHashMap&lt;String, Object&gt;  (키 순서 유지, 중복 키는 마지막 값)
 *   array   → ArrayList&lt;Object&gt;
 *   string  → String
 *   number  → BigDecimal  (정밀도를 잃지 않게. 실제 타입 변환은 {@link JsonBinder}가 한다)
 *   true/false → Boolean,  null → null
 * </pre>
 */
public final class JsonParser {
    private static final int MAX_DEPTH = 512;

    private final String text;
    private int position = 0;
    private int depth = 0;

    private JsonParser(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        JsonParser parser = new JsonParser(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();

        if (parser.position != text.length()) {
            throw parser.failure("Unexpected trailing characters");
        }

        return value;
    }

    private Object readValue() {
        if (this.position >= this.text.length()) {
            throw this.failure("Unexpected end of input");
        }

        char current = this.text.charAt(this.position);
        return switch (current) {
            case '{' -> this.readObject();
            case '[' -> this.readArray();
            case '"' -> this.readString();
            case 't' -> this.readLiteral("true", Boolean.TRUE);
            case 'f' -> this.readLiteral("false", Boolean.FALSE);
            case 'n' -> this.readLiteral("null", null);
            default -> {
                if (current == '-' || JsonParser.isDigit(current)) {
                    yield this.readNumber();
                }
                throw this.failure("Unexpected character '" + current + "'");
            }
        };
    }

    private Map<String, Object> readObject() {
        this.enterNested();
        this.expect('{');
        Map<String, Object> object = new LinkedHashMap<>();

        this.skipWhitespace();
        if (this.consumeIf('}')) {
            this.leaveNested();
            return object;
        }

        do {
            this.skipWhitespace();
            if (this.position >= this.text.length() || this.text.charAt(this.position) != '"') {
                throw this.failure("Expected object key");
            }

            String key = this.readString();
            this.skipWhitespace();
            this.expect(':');
            this.skipWhitespace();
            object.put(key, this.readValue());
            this.skipWhitespace();
        } while (this.consumeIf(','));

        this.expect('}');
        this.leaveNested();
        return object;
    }

    private List<Object> readArray() {
        this.enterNested();
        this.expect('[');
        List<Object> array = new ArrayList<>();

        this.skipWhitespace();
        if (this.consumeIf(']')) {
            this.leaveNested();
            return array;
        }

        do {
            this.skipWhitespace();
            array.add(this.readValue());
            this.skipWhitespace();
        } while (this.consumeIf(','));

        this.expect(']');
        this.leaveNested();
        return array;
    }

    private String readString() {
        this.expect('"');
        StringBuilder builder = new StringBuilder();

        while (this.position < this.text.length()) {
            char current = this.text.charAt(this.position++);

            if (current == '"') {
                return builder.toString();
            }

            if (current == '\\') {
                builder.append(this.readEscape());
                continue;
            }

            // RFC 8259 7: 제어 문자는 반드시 이스케이프되어야 한다.
            if (current < 0x20) {
                throw this.failure("Control character in string");
            }

            builder.append(current);
        }

        throw this.failure("Unterminated string");
    }

    private char readEscape() {
        if (this.position >= this.text.length()) {
            throw this.failure("Unterminated escape sequence");
        }

        char escaped = this.text.charAt(this.position++);
        return switch (escaped) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'u' -> this.readUnicodeEscape();
            default -> throw this.failure("Invalid escape sequence '\\" + escaped + "'");
        };
    }

    private char readUnicodeEscape() {
        if (this.position + 4 > this.text.length()) {
            throw this.failure("Incomplete unicode escape");
        }

        String hexDigits = this.text.substring(this.position, this.position + 4);
        try {
            char unicodeChar = (char) Integer.parseInt(hexDigits, 16);
            this.position += 4;
            return unicodeChar;

        } catch (NumberFormatException exception) {
            throw this.failure("Invalid unicode escape '\\u" + hexDigits + "'");
        }
    }

    private BigDecimal readNumber() {
        int numberStart = this.position;

        this.consumeIf('-');

        // 정수부: 0 이거나 0이 아닌 숫자로 시작 (RFC 8259 6: 앞자리 0 금지)
        if (this.consumeIf('0')) {
            if (this.position < this.text.length() && JsonParser.isDigit(this.text.charAt(this.position))) {
                throw this.failure("Leading zeros are not allowed");
            }
        } else {
            this.readDigits();
        }

        if (this.consumeIf('.')) {
            this.readDigits();
        }

        if (this.consumeIf('e') || this.consumeIf('E')) {
            if (!this.consumeIf('+')) {
                this.consumeIf('-');
            }
            this.readDigits();
        }

        return new BigDecimal(this.text.substring(numberStart, this.position));
    }

    private void readDigits() {
        int digitStart = this.position;
        while (this.position < this.text.length() && JsonParser.isDigit(this.text.charAt(this.position))) {
            this.position++;
        }

        if (digitStart == this.position) {
            throw this.failure("Expected digit");
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!this.text.startsWith(literal, this.position)) {
            throw this.failure("Invalid literal");
        }

        this.position += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (this.position < this.text.length()) {
            char current = this.text.charAt(this.position);
            if (current != ' ' && current != '\t' && current != '\n' && current != '\r') {
                return;
            }
            this.position++;
        }
    }

    private void expect(char expected) {
        if (!this.consumeIf(expected)) {
            throw this.failure("Expected '" + expected + "'");
        }
    }

    private boolean consumeIf(char expected) {
        if (this.position < this.text.length() && this.text.charAt(this.position) == expected) {
            this.position++;
            return true;
        }
        return false;
    }

    private void enterNested() {
        // 깊게 중첩된 입력으로 스택이 터지는 것을 막는다.
        if (++this.depth > MAX_DEPTH) {
            throw this.failure("JSON nesting is too deep");
        }
    }

    private void leaveNested() {
        this.depth--;
    }

    private static boolean isDigit(char character) {
        return '0' <= character && character <= '9';
    }

    private JsonException failure(String reason) {
        return new JsonException("json_invalid", List.of(this.position), "JSON decode error: " + reason);
    }
}
