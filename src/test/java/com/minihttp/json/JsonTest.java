package com.minihttp.json;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {
    enum ProjectState {
        PENDING,
        APPROVED
    }

    // fusion-project의 DTO 모양: 기본 생성자 + private 필드 + 상속
    abstract static class BaseData {
    }

    static class CreateProjectRequest extends BaseData {
        private LocalDateTime startDate;
        private String title;
        private BigDecimal purposeAmount;
        private byte[] imgFile;
        private Long categoryId;
        private List<ProjectState> states;
        private int priority = 7;
    }

    static class SingleDataResponse<D> extends BaseData {
        private D data;
    }

    static class DataCollectionResponse<D> extends BaseData {
        private Long amount;
        private Collection<D> datas;
    }

    static class ItemData {
        private Long id;
        private String name;

        ItemData() {
        }

        ItemData(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    record Point(int x, int y) {
    }

    record Range(int start, int end) {
        Range {
            if (start > end) {
                throw new IllegalArgumentException("start must not exceed end");
            }
        }
    }

    @Nested
    @DisplayName("JsonParser")
    class ParserTest {
        @Test
        void parsesAllValueKinds() {
            Object tree = JsonParser.parse("{\"text\":\"a\\n\\u00e9\",\"number\":-1.5e2,\"flag\":true,\"nothing\":null,\"list\":[1,2]}");

            Map<?, ?> object = (Map<?, ?>) tree;
            assertEquals("a\né", object.get("text"));
            assertEquals(0, new BigDecimal("-150").compareTo((BigDecimal) object.get("number")));
            assertEquals(Boolean.TRUE, object.get("flag"));
            assertTrue(object.containsKey("nothing"));
            assertNull(object.get("nothing"));
            assertEquals(List.of(BigDecimal.ONE, BigDecimal.TWO), object.get("list"));
        }

        @Test
        void rejectsMalformedJsonWithPosition() {
            JsonException exception = assertThrows(JsonException.class, () -> JsonParser.parse("{\"a\":1,}"));

            assertEquals("json_invalid", exception.errors().getFirst().errorType());
            assertEquals(List.of(7), exception.errors().getFirst().location());
        }

        @Test
        void rejectsTrailingGarbageAndLeadingZeros() {
            assertThrows(JsonException.class, () -> JsonParser.parse("[1] x"));
            assertThrows(JsonException.class, () -> JsonParser.parse("012"));
            assertThrows(JsonException.class, () -> JsonParser.parse("\"unterminated"));
        }

        @Test
        void rejectsTooDeepNesting() {
            String deepJson = "[".repeat(1000) + "]".repeat(1000);
            assertThrows(JsonException.class, () -> JsonParser.parse(deepJson));
        }
    }

    @Nested
    @DisplayName("JsonWriter")
    class WriterTest {
        @Test
        void writesFieldsOfPlainObjectsIncludingGenericsAndNesting() {
            DataCollectionResponse<ItemData> response = new DataCollectionResponse<>();
            response.amount = 2L;
            response.datas = List.of(new ItemData(1L, "사과"), new ItemData(2L, "a\"b"));

            assertEquals("{\"amount\":2,\"datas\":[{\"id\":1,\"name\":\"사과\"},{\"id\":2,\"name\":\"a\\\"b\"}]}",
                         JsonWriter.write(response));
        }

        @Test
        void writesJdkValuesInTheSameFormatAsToJsonString() {
            CreateProjectRequest request = new CreateProjectRequest();
            request.startDate = LocalDateTime.of(2026, 11, 13, 10, 30);
            request.purposeAmount = new BigDecimal("1000.50");
            request.imgFile = new byte[]{1, 2, 3};
            request.states = List.of(ProjectState.APPROVED);

            String json = JsonWriter.write(request);

            assertTrue(json.contains("\"startDate\":\"2026-11-13T10:30\""));
            assertTrue(json.contains("\"purposeAmount\":1000.50"));
            assertTrue(json.contains("\"imgFile\":\"AQID\""));
            assertTrue(json.contains("\"states\":[\"APPROVED\"]"));
            assertTrue(json.contains("\"title\":null"));
        }

        @Test
        void writesRecordsMapsOptionalsAndScalars() {
            assertEquals("{\"x\":1,\"y\":2}", JsonWriter.write(new Point(1, 2)));
            assertEquals("\"되냐?\"", JsonWriter.write("되냐?"));
            assertEquals("null", JsonWriter.write(null));
            assertEquals("null", JsonWriter.write(Optional.empty()));
            assertEquals("null", JsonWriter.write(Double.NaN));
            assertEquals("{\"key\":[true,false]}", JsonWriter.write(Map.of("key", List.of(true, false))));
        }

        @Test
        void rejectsCircularReferences() {
            List<Object> selfContaining = new ArrayList<>();
            selfContaining.add(selfContaining);

            assertThrows(IllegalArgumentException.class, () -> JsonWriter.write(selfContaining));
        }
    }

    @Nested
    @DisplayName("JsonBinder")
    class BinderTest {
        private final JsonMapper jsonMapper = DefaultJsonMapper.getInstance();

        @Test
        void bindsFusionStyleDto() {
            String json = "{\"startDate\":\"2026-11-13T10:30:00\",\"title\":\"펀딩\",\"purposeAmount\":1000.5,"
                    + "\"imgFile\":\"AQID\",\"categoryId\":3,\"states\":[\"PENDING\",\"APPROVED\"],\"unknown\":1}";

            CreateProjectRequest request = (CreateProjectRequest) this.jsonMapper.read(json, CreateProjectRequest.class);

            assertEquals(LocalDateTime.of(2026, 11, 13, 10, 30), request.startDate);
            assertEquals("펀딩", request.title);
            assertEquals(new BigDecimal("1000.5"), request.purposeAmount);
            assertArrayEquals(new byte[]{1, 2, 3}, request.imgFile);
            assertEquals(3L, request.categoryId);
            assertEquals(List.of(ProjectState.PENDING, ProjectState.APPROVED), request.states);
            assertEquals(7, request.priority, "JSON에 없는 필드는 기본값을 유지한다");
        }

        @Test
        void resolvesTypeVariablesOfGenericDto() throws NoSuchMethodException {
            var genericType = BinderTest.class.getDeclaredMethod("collectionOfItems").getGenericReturnType();

            DataCollectionResponse<?> response = (DataCollectionResponse<?>) this.jsonMapper.read(
                    "{\"amount\":1,\"datas\":[{\"id\":5,\"name\":\"사과\"}]}", genericType);

            ItemData item = (ItemData) response.datas.iterator().next();
            assertEquals(5L, item.id);
            assertEquals("사과", item.name);
        }

        @Test
        void bindsRecordsAndRunsTheirValidation() {
            assertEquals(new Point(1, 2), this.jsonMapper.read("{\"x\":1,\"y\":2}", Point.class));

            JsonException exception = assertThrows(JsonException.class,
                    () -> this.jsonMapper.read("{\"start\":5,\"end\":1}", Range.class));
            assertEquals("value_error", exception.errors().getFirst().errorType());
        }

        @Test
        void collectsEveryFieldErrorWithItsLocation() {
            JsonException exception = assertThrows(JsonException.class, () -> this.jsonMapper.read(
                    "{\"categoryId\":\"abc\",\"states\":[\"PENDING\",\"NOPE\"],\"purposeAmount\":true}",
                    CreateProjectRequest.class));

            // 에러 순서는 JSON 키 순서가 아니라 DTO 필드 선언 순서를 따른다.
            Map<List<Object>, String> errorTypeByLocation = exception.errors().stream()
                    .collect(Collectors.toMap(JsonError::location, JsonError::errorType));

            assertEquals(Map.of(
                    List.of("purposeAmount"), "decimal_type",
                    List.of("categoryId"), "int_parsing",
                    List.of("states", 1), "enum"
            ), errorTypeByLocation);
        }

        @Test
        void rejectsFractionForIntegerAndNullForPrimitive() {
            JsonException fraction = assertThrows(JsonException.class,
                    () -> this.jsonMapper.read("{\"x\":1.5,\"y\":null}", Point.class));

            assertEquals("int_from_float", fraction.errors().get(0).errorType());
            assertEquals("int_type", fraction.errors().get(1).errorType());
        }

        @SuppressWarnings("unused")
        private DataCollectionResponse<ItemData> collectionOfItems() {
            return null;
        }
    }
}
