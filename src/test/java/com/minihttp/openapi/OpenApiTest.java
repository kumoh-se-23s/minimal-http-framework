package com.minihttp.openapi;

import com.minihttp.MiniHttp;
import com.minihttp.config.OpenApiConfig;
import com.minihttp.core.handler.Handler;
import com.minihttp.core.response.JsonResponse;
import com.minihttp.core.response.PlainTextResponse;
import com.minihttp.core.route.BaseRouter;
import com.minihttp.core.route.annotation.Body;
import com.minihttp.core.route.annotation.Depends;
import com.minihttp.core.route.annotation.Get;
import com.minihttp.core.route.annotation.Header;
import com.minihttp.core.route.annotation.Post;
import com.minihttp.core.route.annotation.Query;
import com.minihttp.core.route.dependency.security.BasicSchema;
import com.minihttp.core.route.dependency.security.BearerSchema;
import com.minihttp.core.route.exception.RequestValidationException;
import com.minihttp.json.JsonParser;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.DefaultHttpBody;
import com.minihttp.packet.structure.DefaultHttpHeader;
import com.minihttp.packet.structure.HttpHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiTest {
    enum Grade {
        LOW,
        HIGH
    }

    record Scalars(int count, long total, short small, byte tiny, BigInteger huge, double ratio, float weight,
                   BigDecimal price, boolean active, char initial, String name, Grade grade, byte[] image,
                   UUID id, LocalDate day, LocalDateTime at, LocalTime time, Integer boxed) {
    }

    record Item(String title) {
    }

    record Node(String label, Node child, List<Node> children) {
    }

    record Page<T>(List<T> items, int total) {
    }

    static class Holder {
        private String value;
        private transient String skipped;
        private static String shared;
    }

    static class Envelope<T> {
        private T payload;
    }

    static class TypedEnvelope extends Envelope<Item> {
        private int version;
    }

    record Loose(Object anything, Map<String, Object> attributes, Map<String, Integer> counts, Runnable task,
                 Number amount, Optional<String> note, int[] numbers) {
    }

    static final class Duplicates {
        record Item(int code) {
        }
    }

    record ValidationErrorResponse(String text) {
    }

    public static final class SchemaRouter extends BaseRouter {
        @Get("/scalars")
        public Scalars scalars() {
            return null;
        }

        @Get("/nodes")
        public Node nodes() {
            return null;
        }

        @Get("/pages")
        public Page<List<Item>> pages() {
            return null;
        }

        @Get("/holders")
        public Holder holder() {
            return null;
        }

        @Get("/envelopes")
        public TypedEnvelope envelope() {
            return null;
        }

        @Get("/loose")
        public Loose loose() {
            return null;
        }

        @Get("/maybe")
        public Optional<Item> maybe() {
            return Optional.empty();
        }
    }

    public static final class CollisionRouter extends BaseRouter {
        @Get("/items")
        public Item item() {
            return null;
        }

        @Get("/duplicate-items")
        public Duplicates.Item duplicateItem() {
            return null;
        }

        @Get("/reserved")
        public ValidationErrorResponse reserved() {
            return null;
        }
    }

    public static final class OperationRouter extends BaseRouter {
        public OperationRouter() {
            super("/operations");
        }

        @Get("/plain")
        public PlainTextResponse plain() {
            return new PlainTextResponse("plain");
        }

        @Get("/raw")
        public HttpResponse raw() {
            return new PlainTextResponse("raw");
        }

        @Get("/json")
        public JsonResponse json() {
            return new JsonResponse(Map.of());
        }

        @Get("/none")
        public String none() {
            return "none";
        }

        @Get("/request")
        public String request(HttpRequest request) {
            return request.path();
        }

        @Get("/search/{category}")
        public String search(String category, @Query(defaultValue = "10") int limit,
                             @Query(defaultValue = "HIGH") Grade grade, Optional<List<String>> tags,
                             @Header(value = "X-Trace", required = false) String trace) {
            return category;
        }

        @Post(value = "/items", status = HttpStatus.CREATED)
        public Item create(Item item) {
            return item;
        }

        @Post("/maybe")
        public Item maybeCreate(Optional<Item> item) {
            return null;
        }

        @Post("/upload")
        public int upload(@Body byte[] image) {
            return image.length;
        }

        @Post(value = "/empty", status = HttpStatus.NO_CONTENT)
        public void empty(int id) {
        }

        @Get("/list")
        public String list() {
            return "first";
        }
    }

    public static final class OtherRouter extends BaseRouter {
        public OtherRouter() {
            super("/other");
        }

        @Get("/list")
        public String list() {
            return "second";
        }
    }

    public static final class DocsRouter extends BaseRouter {
        @Get("/docs")
        public String docs() {
            return "mine";
        }
    }

    public static final class DocsPostRouter extends BaseRouter {
        @Post("/docs")
        public String docs() {
            return "mine";
        }
    }

    public static final class TokenAuth extends BearerSchema<String> {
        @Override
        protected String authenticate(String token) {
            return token;
        }
    }

    public static final class PasswordAuth extends BasicSchema<String> {
        @Override
        protected String authenticate(String username, String password) {
            return username;
        }
    }

    public static final class SecurityRouter extends BaseRouter {
        public SecurityRouter() {
            super("/secure");
        }

        @Get("/bearer")
        public String bearer(@Depends(TokenAuth.class) String token) {
            return token;
        }

        @Get("/both")
        public String both(@Depends(TokenAuth.class) String token, @Depends(PasswordAuth.class) String username) {
            return token + username;
        }

        @Get("/public")
        public String open() {
            return "open";
        }
    }

    private static Handler handler(MiniHttp app, BaseRouter... routers) {
        for (BaseRouter router : routers) {
            app.includeRouter(router);
        }
        return app.buildHandler();
    }

    private static HttpResponse get(Handler handler, String path) throws Exception {
        HttpHeader header = new DefaultHttpHeader();
        header.set(HttpHeaderField.HOST, "localhost");
        HttpRequest request = new HttpRequest(HttpVersion.HTTP_1_1, header, new DefaultHttpBody(new byte[0]),
                                              HttpMethod.GET, path, Map.of(), Map.of());
        return handler.handle(request);
    }

    private static Map<String, Object> spec(Handler handler) throws Exception {
        HttpResponse response = OpenApiTest.get(handler, "/openapi.json");
        assertEquals(HttpStatus.OK, response.status());
        return OpenApiTest.map(JsonParser.parse(response.body().asString(StandardCharsets.UTF_8)));
    }

    private static Map<String, Object> spec(BaseRouter... routers) throws Exception {
        return OpenApiTest.spec(OpenApiTest.handler(MiniHttp.create(), routers));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    private static Map<String, Object> at(Map<String, Object> tree, String... keys) {
        Map<String, Object> current = tree;
        for (String key : keys) {
            current = OpenApiTest.map(current.get(key));
        }
        return current;
    }

    private static Map<String, Object> component(Map<String, Object> spec, String name) {
        return OpenApiTest.at(spec, "components", "schemas", name);
    }

    private static Map<String, Object> property(Map<String, Object> spec, String componentName, String propertyName) {
        return OpenApiTest.at(OpenApiTest.component(spec, componentName), "properties", propertyName);
    }

    private static Map<String, Object> operation(Map<String, Object> spec, String path, String method) {
        return OpenApiTest.at(spec, "paths", path, method);
    }

    private static Map<String, Object> parameter(Map<String, Object> operation, String name) {
        return OpenApiTest.list(operation.get("parameters")).stream()
                          .map(OpenApiTest::map)
                          .filter(parameter -> parameter.get("name").equals(name))
                          .findFirst()
                          .orElseThrow();
    }

    @Nested
    @DisplayName("스키마")
    class SchemaTest {
        private final Map<String, Object> spec;

        SchemaTest() throws Exception {
            this.spec = OpenApiTest.spec(new SchemaRouter());
        }

        @Test
        void mapsScalarFormats() {
            assertEquals(Map.of("type", "integer", "format", "int32"), OpenApiTest.property(this.spec, "Scalars", "count"));
            assertEquals(Map.of("type", "integer", "format", "int64"), OpenApiTest.property(this.spec, "Scalars", "total"));
            assertEquals(Map.of("type", "integer", "format", "int32"), OpenApiTest.property(this.spec, "Scalars", "small"));
            assertEquals(Map.of("type", "integer", "format", "int32"), OpenApiTest.property(this.spec, "Scalars", "tiny"));
            assertEquals(List.of("integer", "null"), OpenApiTest.property(this.spec, "Scalars", "huge").get("type"));
            assertEquals(Map.of("type", "number", "format", "double"), OpenApiTest.property(this.spec, "Scalars", "ratio"));
            assertEquals(Map.of("type", "number", "format", "float"), OpenApiTest.property(this.spec, "Scalars", "weight"));
            assertEquals(List.of("number", "null"), OpenApiTest.property(this.spec, "Scalars", "price").get("type"));
            assertEquals(Map.of("type", "boolean"), OpenApiTest.property(this.spec, "Scalars", "active"));
            assertEquals(Map.of("type", "string", "minLength", BigDecimal.ONE, "maxLength", BigDecimal.ONE), OpenApiTest.property(this.spec, "Scalars", "initial"));
            assertEquals("base64", OpenApiTest.property(this.spec, "Scalars", "image").get("contentEncoding"));
            assertEquals("uuid", OpenApiTest.property(this.spec, "Scalars", "id").get("format"));
            assertEquals("date", OpenApiTest.property(this.spec, "Scalars", "day").get("format"));
            assertFalse(OpenApiTest.property(this.spec, "Scalars", "at").containsKey("format"));
            assertFalse(OpenApiTest.property(this.spec, "Scalars", "time").containsKey("format"));
        }

        @Test
        void referenceTypesAreNullableAndPrimitivesAreNot() {
            assertEquals(List.of("string", "null"), OpenApiTest.property(this.spec, "Scalars", "name").get("type"));
            assertEquals(List.of("integer", "null"), OpenApiTest.property(this.spec, "Scalars", "boxed").get("type"));
            assertEquals("integer", OpenApiTest.property(this.spec, "Scalars", "count").get("type"));
            assertEquals(Arrays.asList("LOW", "HIGH", null), OpenApiTest.property(this.spec, "Scalars", "grade").get("enum"));
        }

        @Test
        void componentsHaveNoRequired() {
            assertFalse(OpenApiTest.component(this.spec, "Scalars").containsKey("required"));
            assertEquals("object", OpenApiTest.component(this.spec, "Scalars").get("type"));
        }

        @Test
        void recursiveComponentUsesNullableReference() {
            Map<String, Object> child = OpenApiTest.property(this.spec, "Node", "child");
            assertEquals(List.of(Map.of("$ref", "#/components/schemas/Node"), Map.of("type", "null")), child.get("anyOf"));

            Map<String, Object> children = OpenApiTest.property(this.spec, "Node", "children");
            assertEquals(List.of("array", "null"), children.get("type"));
        }

        @Test
        void genericComponentIsNamedWithUnderscoresAndTitledWithBrackets() {
            Map<String, Object> page = OpenApiTest.component(this.spec, "Page_List_Item__");
            assertEquals("Page<List<Item>>", page.get("title"));

            Map<String, Object> items = OpenApiTest.property(this.spec, "Page_List_Item__", "items");
            Map<String, Object> innerList = OpenApiTest.map(items.get("items"));
            Map<String, Object> item = OpenApiTest.map(innerList.get("items"));
            assertEquals(List.of(Map.of("$ref", "#/components/schemas/Item"), Map.of("type", "null")), item.get("anyOf"));

            Map<String, Object> response = OpenApiTest.at(OpenApiTest.operation(this.spec, "/pages", "get"),
                                                          "responses", "200", "content", "application/json", "schema");
            assertEquals(Map.of("$ref", "#/components/schemas/Page_List_Item__"), response);
        }

        @Test
        void classFieldsFollowJsonWriterRules() {
            Map<String, Object> properties = OpenApiTest.at(OpenApiTest.component(this.spec, "Holder"), "properties");
            assertEquals(List.of("value"), List.copyOf(properties.keySet()));
        }

        @Test
        void inheritedGenericFieldsAreResolved() {
            Map<String, Object> payload = OpenApiTest.property(this.spec, "TypedEnvelope", "payload");
            assertEquals(List.of(Map.of("$ref", "#/components/schemas/Item"), Map.of("type", "null")), payload.get("anyOf"));
            assertEquals(Map.of("type", "integer", "format", "int32"), OpenApiTest.property(this.spec, "TypedEnvelope", "version"));
        }

        @Test
        void unknownTypesAreOpen() {
            assertEquals(Map.of(), OpenApiTest.property(this.spec, "Loose", "anything"));
            assertEquals(Map.of(), OpenApiTest.property(this.spec, "Loose", "task"));
            assertEquals(Map.of(), OpenApiTest.property(this.spec, "Loose", "attributes").get("additionalProperties"));
            assertEquals(List.of("integer", "null"),
                         OpenApiTest.map(OpenApiTest.property(this.spec, "Loose", "counts").get("additionalProperties")).get("type"));
            assertEquals(List.of("number", "null"), OpenApiTest.property(this.spec, "Loose", "amount").get("type"));
            assertEquals(List.of("string", "null"), OpenApiTest.property(this.spec, "Loose", "note").get("type"));
            assertEquals(Map.of("type", "integer", "format", "int32"),
                         OpenApiTest.property(this.spec, "Loose", "numbers").get("items"));
        }

        @Test
        void optionalResponseIsNullable() {
            Map<String, Object> schema = OpenApiTest.at(OpenApiTest.operation(this.spec, "/maybe", "get"),
                                                        "responses", "200", "content", "application/json", "schema");
            assertEquals(List.of(Map.of("$ref", "#/components/schemas/Item"), Map.of("type", "null")), schema.get("anyOf"));
        }
    }

    @Nested
    @DisplayName("컴포넌트 이름 충돌")
    class CollisionTest {
        @Test
        void collidingSimpleNamesUseQualifiedNames() throws Exception {
            Map<String, Object> schemas = OpenApiTest.at(OpenApiTest.spec(new CollisionRouter()), "components", "schemas");

            assertFalse(schemas.containsKey("Item"));
            assertTrue(schemas.containsKey("com.minihttp.openapi.OpenApiTest.Item"));
            assertTrue(schemas.containsKey("com.minihttp.openapi.OpenApiTest.Duplicates.Item"));
        }

        @Test
        void reservedNamesPushUserClassToQualifiedName() throws Exception {
            Map<String, Object> schemas = OpenApiTest.at(OpenApiTest.spec(new CollisionRouter()), "components", "schemas");

            assertTrue(schemas.containsKey("com.minihttp.openapi.OpenApiTest.ValidationErrorResponse"));
            assertFalse(schemas.containsKey("ValidationErrorResponse"));
        }
    }

    @Nested
    @DisplayName("오퍼레이션")
    class OperationTest {
        private final Map<String, Object> spec;

        OperationTest() throws Exception {
            this.spec = OpenApiTest.spec(new OperationRouter(), new OtherRouter());
        }

        @Test
        void usesRouterNameAsTagAndMethodNameAsOperationId() {
            Map<String, Object> operation = OpenApiTest.operation(this.spec, "/operations/none", "get");

            assertEquals(List.of("OperationRouter"), operation.get("tags"));
            assertEquals("none", operation.get("operationId"));
        }

        @Test
        void duplicatedOperationIdsAreQualifiedWithRouterName() {
            assertEquals("OperationRouter_list", OpenApiTest.operation(this.spec, "/operations/list", "get").get("operationId"));
            assertEquals("OtherRouter_list", OpenApiTest.operation(this.spec, "/other/list", "get").get("operationId"));
        }

        @Test
        void customResponsesUseDefault() {
            Map<String, Object> plain = OpenApiTest.at(OpenApiTest.operation(this.spec, "/operations/plain", "get"), "responses");
            assertEquals(List.of("default"), List.copyOf(plain.keySet()));
            assertEquals(Map.of("type", "string"), OpenApiTest.at(plain, "default", "content", "text/plain", "schema"));

            Map<String, Object> raw = OpenApiTest.at(OpenApiTest.operation(this.spec, "/operations/raw", "get"), "responses", "default");
            assertFalse(raw.containsKey("content"));

            Map<String, Object> json = OpenApiTest.at(OpenApiTest.operation(this.spec, "/operations/json", "get"), "responses", "default");
            assertEquals(Map.of(), OpenApiTest.at(json, "content", "application/json", "schema"));
        }

        @Test
        void validationErrorOnlyWhenParametersExist() {
            assertFalse(OpenApiTest.at(OpenApiTest.operation(this.spec, "/operations/none", "get"), "responses").containsKey("422"));
            assertFalse(OpenApiTest.at(OpenApiTest.operation(this.spec, "/operations/request", "get"), "responses").containsKey("422"));
            assertTrue(OpenApiTest.at(OpenApiTest.operation(this.spec, "/operations/items", "post"), "responses").containsKey("422"));
            assertTrue(OpenApiTest.at(this.spec, "components", "schemas").containsKey("ValidationErrorDetail"));
        }

        @Test
        void parametersCarryLocationRequiredAndDefault() {
            Map<String, Object> operation = OpenApiTest.operation(this.spec, "/operations/search/{category}", "get");

            Map<String, Object> category = OpenApiTest.parameter(operation, "category");
            assertEquals("path", category.get("in"));
            assertEquals(true, category.get("required"));

            Map<String, Object> limit = OpenApiTest.parameter(operation, "limit");
            assertEquals(false, limit.get("required"));
            assertEquals(Map.of("type", "integer", "format", "int32", "default", BigDecimal.TEN), limit.get("schema"));

            assertEquals("HIGH", OpenApiTest.map(OpenApiTest.parameter(operation, "grade").get("schema")).get("default"));

            Map<String, Object> tags = OpenApiTest.parameter(operation, "tags");
            assertEquals(Map.of("type", "array", "items", Map.of("type", "string")), tags.get("schema"));

            Map<String, Object> trace = OpenApiTest.parameter(operation, "X-Trace");
            assertEquals("header", trace.get("in"));
            assertEquals(false, trace.get("required"));
        }

        @Test
        void requestBodies() {
            Map<String, Object> create = OpenApiTest.at(OpenApiTest.operation(this.spec, "/operations/items", "post"), "requestBody");
            assertEquals(true, create.get("required"));
            assertEquals(Map.of("$ref", "#/components/schemas/Item"), OpenApiTest.at(create, "content", "application/json", "schema"));
            assertTrue(OpenApiTest.at(OpenApiTest.operation(this.spec, "/operations/items", "post"), "responses").containsKey("201"));

            Map<String, Object> maybe = OpenApiTest.at(OpenApiTest.operation(this.spec, "/operations/maybe", "post"), "requestBody");
            assertEquals(false, maybe.get("required"));

            Map<String, Object> upload = OpenApiTest.at(OpenApiTest.operation(this.spec, "/operations/upload", "post"), "requestBody");
            assertTrue(OpenApiTest.at(upload, "content").containsKey("application/octet-stream"));
        }

        @Test
        void noContentStatusHasNoBody() {
            Map<String, Object> response = OpenApiTest.at(OpenApiTest.operation(this.spec, "/operations/empty", "post"), "responses", "204");
            assertFalse(response.containsKey("content"));
        }
    }

    @Nested
    @DisplayName("보안 스키마")
    class SecurityTest {
        private final Map<String, Object> spec;

        SecurityTest() throws Exception {
            this.spec = OpenApiTest.spec(new SecurityRouter());
        }

        @Test
        void securitySchemesAreRegisteredInComponents() {
            Map<String, Object> securitySchemes = OpenApiTest.at(this.spec, "components", "securitySchemes");
            assertEquals(Map.of("type", "http", "scheme", "bearer"), securitySchemes.get("TokenAuth"));
            assertEquals(Map.of("type", "http", "scheme", "basic"), securitySchemes.get("PasswordAuth"));
        }

        @Test
        void operationsListRequiredSchemes() {
            assertEquals(List.of(Map.of("TokenAuth", List.of())),
                         OpenApiTest.operation(this.spec, "/secure/bearer", "get").get("security"));
            assertEquals(List.of(Map.of("TokenAuth", List.of(), "PasswordAuth", List.of())),
                         OpenApiTest.operation(this.spec, "/secure/both", "get").get("security"));
            assertFalse(OpenApiTest.operation(this.spec, "/secure/public", "get").containsKey("security"));
        }

        @Test
        void documentWithoutSecurityHasNoSecuritySchemes() throws Exception {
            Map<String, Object> spec = OpenApiTest.spec(new OperationRouter());
            assertFalse(OpenApiTest.map(spec.get("components")).containsKey("securitySchemes"));
        }
    }

    @Nested
    @DisplayName("설정과 제공")
    class ServingTest {
        @Test
        void customizedValidationHandlerRemovesValidationError() throws Exception {
            MiniHttp app = MiniHttp.create()
                                   .exceptionHandler(RequestValidationException.class,
                                                     (request, exception) -> new PlainTextResponse(HttpStatus.BAD_REQUEST, "bad"));
            Map<String, Object> spec = OpenApiTest.spec(OpenApiTest.handler(app, new OperationRouter()));

            assertFalse(OpenApiTest.at(OpenApiTest.operation(spec, "/operations/items", "post"), "responses").containsKey("422"));
            assertFalse(OpenApiTest.map(spec.get("components")).containsKey("ValidationErrorDetail"));
        }

        @Test
        void usesConfiguredInfoAndPaths() throws Exception {
            MiniHttp app = MiniHttp.create().openApi(OpenApiConfig.builder()
                                                                  .title("Shop <API>")
                                                                  .version("2.0.0")
                                                                  .description("shop")
                                                                  .specPath("/api/spec.json")
                                                                  .docsPath("/api/docs/")
                                                                  .build());
            Handler handler = OpenApiTest.handler(app, new OperationRouter());

            HttpResponse specResponse = OpenApiTest.get(handler, "/api/spec.json");
            Map<String, Object> info = OpenApiTest.map(OpenApiTest.map(JsonParser.parse(
                    specResponse.body().asString(StandardCharsets.UTF_8))).get("info"));
            assertEquals(Map.of("title", "Shop <API>", "version", "2.0.0", "description", "shop"), info);

            String html = OpenApiTest.get(handler, "/api/docs").body().asString(StandardCharsets.UTF_8);
            assertTrue(html.contains("<title>Shop &lt;API&gt;</title>"));
            assertTrue(html.contains("src=\"/api/docs/swagger-ui-bundle.js\""));
            assertTrue(html.contains("url: \"/api/spec.json\""));
            assertEquals(HttpStatus.NOT_FOUND, OpenApiTest.get(handler, "/openapi.json").status());
        }

        @Test
        void servesDocsAndAssetsWithContentTypes() throws Exception {
            Handler handler = OpenApiTest.handler(MiniHttp.create(), new OperationRouter());

            assertEquals("application/json", OpenApiTest.get(handler, "/openapi.json").contentType().orElseThrow());
            assertEquals("text/html; charset=utf-8", OpenApiTest.get(handler, "/docs").contentType().orElseThrow());
            assertEquals("text/javascript; charset=utf-8",
                         OpenApiTest.get(handler, "/docs/swagger-ui-bundle.js").contentType().orElseThrow());
            assertEquals("text/css; charset=utf-8", OpenApiTest.get(handler, "/docs/swagger-ui.css").contentType().orElseThrow());
            assertEquals("image/png", OpenApiTest.get(handler, "/docs/favicon-32x32.png").contentType().orElseThrow());
            assertEquals(HttpStatus.NOT_FOUND, OpenApiTest.get(handler, "/docs/unknown.js").status());
        }

        @Test
        void disabledConfigServesNothing() throws Exception {
            Handler handler = OpenApiTest.handler(MiniHttp.create().openApi(OpenApiConfig.disabled()), new OperationRouter());

            assertEquals(HttpStatus.NOT_FOUND, OpenApiTest.get(handler, "/openapi.json").status());
            assertEquals(HttpStatus.NOT_FOUND, OpenApiTest.get(handler, "/docs").status());
        }

        @Test
        void conflictingGetEndpointFailsAtStartup() {
            assertThrows(IllegalStateException.class, () -> OpenApiTest.handler(MiniHttp.create(), new DocsRouter()));
        }

        @Test
        void conflictingEndpointIsAllowedWhenDisabledOrNotGet() throws Exception {
            Handler disabled = OpenApiTest.handler(MiniHttp.create().openApi(OpenApiConfig.disabled()), new DocsRouter());
            assertEquals("\"mine\"", OpenApiTest.get(disabled, "/docs").body().asString(StandardCharsets.UTF_8));

            OpenApiTest.handler(MiniHttp.create(), new DocsPostRouter());
        }

        @Test
        void rejectsSameSpecAndDocsPath() {
            assertThrows(IllegalArgumentException.class,
                         () -> OpenApiConfig.builder().specPath("/docs").docsPath("/docs/").build());
        }
    }
}
