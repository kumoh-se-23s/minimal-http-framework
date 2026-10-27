package com.minihttp.core.routing.binding;

import com.minihttp.core.handler.Handler;
import com.minihttp.core.routing.BaseRouter;
import com.minihttp.core.routing.annotation.Body;
import com.minihttp.core.routing.annotation.Get;
import com.minihttp.core.routing.annotation.Header;
import com.minihttp.core.routing.annotation.PathParam;
import com.minihttp.core.routing.annotation.Post;
import com.minihttp.core.routing.annotation.Query;
import com.minihttp.core.routing.support.RoutingResponses;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.DefaultHttpBody;
import com.minihttp.packet.structure.DefaultHttpHeader;
import com.minihttp.packet.structure.HttpHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ControllerRegistrar (파라미터 바인딩)")
class ControllerRegistrarTest {

    private static HttpRequest request(HttpMethod method, String target) {
        return request(method, target, new DefaultHttpHeader(), "");
    }

    private static HttpRequest request(HttpMethod method, String target, HttpHeader header, String body) {
        int question = target.indexOf('?');
        String path = question < 0 ? target : target.substring(0, question);

        Map<String, List<String>> query = new LinkedHashMap<>();
        if (question >= 0) {
            for (String pair : target.substring(question + 1).split("&")) {
                String[] keyValue = pair.split("=", 2);
                query.computeIfAbsent(keyValue[0], _ -> new ArrayList<>()).add(keyValue[1]);
            }
        }

        return new HttpRequest(HttpVersion.HTTP_1_1, header, new DefaultHttpBody(body.getBytes(StandardCharsets.UTF_8)),
                method, path, query, Map.of());
    }

    private static String text(HttpResponse response) {
        return response.body().asString(StandardCharsets.UTF_8);
    }

    enum Color {
        RED, GREEN
    }

    static final class SampleRouter extends BaseRouter {
        @Get("/hello")
        public String hello(@Query(defaultValue = "world") String name) {
            return "Hello, " + name + "!";
        }

        @Get("/add")
        public String add(int a, int b) {
            return String.valueOf(a + b);
        }

        @Get("/users/{id}")
        public String user(long id) {
            return "user " + id;
        }

        @Get("/files/{file}")
        public String file(@PathParam("file") String name) {
            return name;
        }

        @Get("/search")
        public String search(@Query Optional<String> keyword) {
            return keyword.orElse("(none)");
        }

        @Get("/flags")
        public String flags(boolean enabled, Color color) {
            return enabled + " " + color;
        }

        @Get("/agent")
        public String agent(@Header String userAgent) {
            return userAgent;
        }

        @Post("/echo")
        public String echo(@Body String body, HttpRequest request) {
            return request.method() + " " + body;
        }

        @Post("/raw")
        public HttpResponse raw(@Body byte[] body) {
            return RoutingResponses.text(HttpStatus.CREATED, body.length + " bytes");
        }

        @Get("/boom")
        public String boom() {
            throw new IllegalStateException("boom");
        }
    }

    @Nested
    @DisplayName("값 채우기")
    class Binding {
        private final Handler handler = new SampleRouter().build();

        private String get(String target) throws Exception {
            return text(this.handler.handle(request(HttpMethod.GET, target)));
        }

        @Test
        @DisplayName("@Query 값을 넣고, 없으면 defaultValue를 쓴다")
        void queryWithDefault() throws Exception {
            assertEquals("Hello, kim!", this.get("/hello?name=kim"));
            assertEquals("Hello, world!", this.get("/hello"));
        }

        @Test
        @DisplayName("어노테이션 없는 파라미터는 쿼리에서 타입을 바꿔 넣는다")
        void plainParametersFromQuery() throws Exception {
            assertEquals("5", this.get("/add?a=2&b=3"));
        }

        @Test
        @DisplayName("이름이 경로 변수와 같은 파라미터는 경로에서 채운다")
        void pathVariableByName() throws Exception {
            assertEquals("user 42", this.get("/users/42"));
        }

        @Test
        @DisplayName("@PathParam에 이름을 주면 그 변수를 쓴다")
        void explicitPathParam() throws Exception {
            assertEquals("a.txt", this.get("/files/a.txt"));
        }

        @Test
        @DisplayName("Optional 파라미터는 값이 없으면 비어서 들어온다")
        void optional() throws Exception {
            assertEquals("(none)", this.get("/search"));
            assertEquals("java", this.get("/search?keyword=java"));
        }

        @Test
        @DisplayName("boolean과 enum은 대소문자를 가리지 않는다")
        void booleanAndEnum() throws Exception {
            assertEquals("true GREEN", this.get("/flags?enabled=TRUE&color=green"));
        }

        @Test
        @DisplayName("@Header는 파라미터 이름을 헤더 이름으로 바꿔 찾는다")
        void header() throws Exception {
            HttpHeader header = new DefaultHttpHeader();
            header.set("User-Agent", "curl/8");

            assertEquals("curl/8", text(this.handler.handle(request(HttpMethod.GET, "/agent", header, ""))));
        }

        @Test
        @DisplayName("@Body와 HttpRequest 파라미터를 함께 받는다")
        void bodyAndRequest() throws Exception {
            HttpResponse response = this.handler.handle(request(HttpMethod.POST, "/echo", new DefaultHttpHeader(), "hi"));

            assertEquals("POST hi", text(response));
        }

        @Test
        @DisplayName("HttpResponse를 돌려주면 그대로 쓴다")
        void returnsResponseAsIs() throws Exception {
            HttpResponse response = this.handler.handle(request(HttpMethod.POST, "/raw", new DefaultHttpHeader(), "abc"));

            assertEquals(HttpStatus.CREATED, response.status());
            assertEquals("3 bytes", text(response));
        }
    }

    @Nested
    @DisplayName("잘못된 요청")
    class BadRequest {
        private final Handler handler = new SampleRouter().build();

        @Test
        @DisplayName("필수 값이 없으면 400")
        void missingValue() throws Exception {
            HttpResponse response = this.handler.handle(request(HttpMethod.GET, "/add?a=1"));

            assertEquals(HttpStatus.BAD_REQUEST, response.status());
            assertTrue(text(response).contains("b"));
        }

        @Test
        @DisplayName("타입이 틀리면 400")
        void invalidValue() throws Exception {
            assertEquals(HttpStatus.BAD_REQUEST, this.handler.handle(request(HttpMethod.GET, "/add?a=1&b=x")).status());
            assertEquals(HttpStatus.BAD_REQUEST, this.handler.handle(request(HttpMethod.GET, "/flags?enabled=yes&color=red")).status());
            assertEquals(HttpStatus.BAD_REQUEST, this.handler.handle(request(HttpMethod.GET, "/users/abc")).status());
        }

        @Test
        @DisplayName("컨트롤러가 던진 예외는 포장을 벗겨 그대로 올린다")
        void rethrowsControllerException() {
            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> this.handler.handle(request(HttpMethod.GET, "/boom")));

            assertEquals("boom", thrown.getMessage());
        }
    }

    static final class UnknownPathParamRouter extends BaseRouter {
        @Get("/users/{id}")
        public String user(@PathParam("userId") String userId) {
            return userId;
        }
    }

    static final class UnsupportedBodyRouter extends BaseRouter {
        @Post("/users")
        public String create(@Body Map<String, String> body) {
            return "created";
        }
    }

    static final class UnsupportedParameterRouter extends BaseRouter {
        @Get("/when")
        public String when(java.time.Instant at) {
            return at.toString();
        }
    }

    static final class UnsupportedReturnRouter extends BaseRouter {
        @Get("/number")
        public int number() {
            return 1;
        }
    }

    @Nested
    @DisplayName("잘못된 선언은 build할 때 실패한다")
    class InvalidDeclaration {

        @Test
        @DisplayName("패턴에 없는 경로 변수를 @PathParam으로 요구")
        void unknownPathParam() {
            assertThrows(IllegalArgumentException.class, () -> new UnknownPathParamRouter().build());
        }

        @Test
        @DisplayName("@Body가 byte[]나 String이 아님")
        void unsupportedBody() {
            assertThrows(IllegalArgumentException.class, () -> new UnsupportedBodyRouter().build());
        }

        @Test
        @DisplayName("변환할 수 없는 파라미터 타입")
        void unsupportedParameter() {
            assertThrows(IllegalArgumentException.class, () -> new UnsupportedParameterRouter().build());
        }

        @Test
        @DisplayName("지원하지 않는 반환 타입")
        void unsupportedReturn() {
            assertThrows(IllegalArgumentException.class, () -> new UnsupportedReturnRouter().build());
        }
    }
}
