package com.minihttp.core.routing;

import com.minihttp.core.handler.Handler;
import com.minihttp.core.routing.annotation.Body;
import com.minihttp.core.routing.annotation.Delete;
import com.minihttp.core.routing.annotation.Get;
import com.minihttp.core.routing.annotation.PathParam;
import com.minihttp.core.routing.annotation.Post;
import com.minihttp.core.routing.annotation.Query;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.DefaultHttpBody;
import com.minihttp.packet.structure.DefaultHttpHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("BaseRouter")
class BaseRouterTest {

    private static HttpRequest request(HttpMethod method, String target) {
        return request(method, target, "");
    }

    private static HttpRequest request(HttpMethod method, String target, String body) {
        int question = target.indexOf('?');
        String path = question < 0 ? target : target.substring(0, question);

        Map<String, List<String>> query = new LinkedHashMap<>();
        if (question >= 0) {
            for (String pair : target.substring(question + 1).split("&")) {
                String[] keyValue = pair.split("=", 2);
                query.computeIfAbsent(keyValue[0], _ -> new ArrayList<>()).add(keyValue[1]);
            }
        }

        return new HttpRequest(HttpVersion.HTTP_1_1, new DefaultHttpHeader(),
                new DefaultHttpBody(body.getBytes(StandardCharsets.UTF_8)), method, path, query, Map.of());
    }

    private static String text(HttpResponse response) {
        return response.body().asString(StandardCharsets.UTF_8);
    }

    private static HttpResponse handle(Router router, HttpRequest request) throws Exception {
        return router.build().handle(request);
    }

    private static String get(Router router, String target) throws Exception {
        return text(handle(router, request(HttpMethod.GET, target)));
    }

    // ---------- 테스트용 라우터 ----------

    /** 자기 라우트 없이 include / register만 하는 라우터 */
    static final class EmptyRouter extends BaseRouter {
        EmptyRouter() {
        }

        EmptyRouter(String prefix) {
            super(prefix);
        }
    }

    static final class ItemRouter extends BaseRouter {
        ItemRouter() {
            super("/items");
        }

        @Get("/")
        public String list() {
            return "list";
        }

        @Post("/")
        public String create() {
            return "create";
        }

        // 고정 세그먼트가 많은 "/me"가 먼저 선언돼 있지 않아도 이겨야 한다
        @Get("/{id}")
        public String find(long id) {
            return "item " + id;
        }

        @Get("/me")
        public String mine() {
            return "mine";
        }

        @Delete("/{id}")
        public void remove(long id) {
        }
    }

    static final class UserRouter extends BaseRouter {
        private final String greeting;

        UserRouter(String greeting) {
            super("/users");
            this.greeting = greeting;
        }

        @Get("/{id}")
        public String find(long id) {
            return this.greeting + " " + id;
        }

        @Get("/search")
        public String search(@Query(defaultValue = "all") String keyword) {
            return keyword;
        }

        @Post("/")
        public String create(@Body String name) {
            return "created " + name;
        }
    }

    static final class HealthRouter extends BaseRouter {
        private final String status;

        HealthRouter(String status) {
            super("/health/");
            this.status = status;
        }

        @Get("/")
        public String health() {
            return this.status;
        }
    }

    static final class Application extends BaseRouter {
        Application() {
            super("/api");
            this.include(new UserRouter("hello"))
                .include(new HealthRouter("ok"));
        }

        @Get("/version")
        public String version() {
            return "1.0";
        }
    }

    public static class PingController {
        @Get("/ping")
        public String ping() {
            return "pong";
        }
    }

    @Nested
    @DisplayName("경로 매칭")
    class Matching {
        private final Router router = new ItemRouter();

        @Test
        @DisplayName("{name} 자리 값을 경로 파라미터로 넣어 준다")
        void fillsPathParams() throws Exception {
            assertEquals("item 42", get(this.router, "/items/42"));
        }

        @Test
        @DisplayName("고정 세그먼트가 더 많은 패턴이 선언 순서와 상관없이 이긴다")
        void prefersMoreLiteralSegments() throws Exception {
            assertEquals("mine", get(this.router, "/items/me"));
        }

        @Test
        @DisplayName("같은 경로라도 메서드에 따라 다른 메서드를 부른다")
        void dispatchesByMethod() throws Exception {
            assertEquals("list", get(this.router, "/items"));
            assertEquals("create", text(handle(this.router, request(HttpMethod.POST, "/items"))));
        }

        @Test
        @DisplayName("경로가 없으면 404, 메서드만 틀리면 405와 Allow")
        void notFoundAndMethodNotAllowed() throws Exception {
            assertEquals(HttpStatus.NOT_FOUND, handle(this.router, request(HttpMethod.GET, "/nope")).status());

            HttpResponse response = handle(this.router, request(HttpMethod.PUT, "/items"));
            assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.status());
            assertEquals(Optional.of("GET, POST, HEAD"), response.header().get(HttpHeaderField.ALLOW));
        }

        @Test
        @DisplayName("HEAD는 GET 라우트로 처리한다")
        void headFallsBackToGet() throws Exception {
            assertEquals(HttpStatus.OK, handle(this.router, request(HttpMethod.HEAD, "/items")).status());
        }

        @Test
        @DisplayName("void 메서드는 204")
        void voidIsNoContent() throws Exception {
            assertEquals(HttpStatus.NO_CONTENT, handle(this.router, request(HttpMethod.DELETE, "/items/1")).status());
        }
    }

    @Nested
    @DisplayName("상속과 include")
    class Inheritance {

        @Test
        @DisplayName("생성자에서 채운 필드를 라우트 메서드에서 쓸 수 있다")
        void fieldsAreReadyForHandlers() throws Exception {
            assertEquals("hi 1", get(new UserRouter("hi"), "/users/1"));
        }

        @Test
        @DisplayName("prefix 끝의 /는 떼고, \"/\" 패턴은 prefix 자체와 맞는다")
        void slashPatternMatchesPrefix() throws Exception {
            assertEquals("ok", get(new HealthRouter("ok"), "/health"));
        }

        @Test
        @DisplayName("include하면 prefix가 단계마다 붙는다")
        void includeStacksPrefixes() throws Exception {
            Application app = new Application();

            assertEquals("hello 7", get(app, "/api/users/7"));
            assertEquals("all", get(app, "/api/users/search"));
            assertEquals("ok", get(app, "/api/health"));
            assertEquals("1.0", get(app, "/api/version"));
            assertEquals("created kim", text(handle(app, request(HttpMethod.POST, "/api/users", "kim"))));
            assertEquals(HttpStatus.NOT_FOUND, handle(app, request(HttpMethod.GET, "/users/7")).status());
        }

        @Test
        @DisplayName("routes()는 prefix가 붙은 전체 경로로 내보낸다")
        void routesExportFullPatterns() {
            List<String> patterns = new Application().routes().stream()
                                                      .map(route -> route.method() + " " + route.pattern())
                                                      .sorted()
                                                      .toList();

            assertEquals(List.of(
                    "GET /api/health",
                    "GET /api/users/search",
                    "GET /api/users/{id}",
                    "GET /api/version",
                    "POST /api/users"
            ), patterns);
        }

        @Test
        @DisplayName("Router가 아닌 평범한 컨트롤러는 register로 붙이고 prefix를 따른다")
        void registerPlainController() throws Exception {
            Router router = new EmptyRouter("/v1").register(new PingController());

            assertEquals("pong", get(router, "/v1/ping"));
        }
    }

    @Nested
    @DisplayName("build()")
    class Build {

        @Test
        @DisplayName("build한 Handler는 여러 스레드에서 동시에 써도 된다")
        void builtHandlerIsSafeToShare() throws Exception {
            Handler handler = new Application().build();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<String>> results = new ArrayList<>();
                for (int i = 0; i < 1000; i++) {
                    String target = "/api/users/" + i;
                    results.add(executor.submit(() -> text(handler.handle(request(HttpMethod.GET, target)))));
                }

                // 경로 파라미터는 요청마다 자기 HttpRequest에만 들어가므로 서로 섞이지 않는다
                for (int i = 0; i < results.size(); i++) {
                    assertEquals("hello " + i, results.get(i).get());
                }
            }
        }

        @Test
        @DisplayName("build를 여러 번 불러도 같은 라우트로 만든다")
        void buildCanBeCalledAgain() throws Exception {
            Application app = new Application();
            app.build();

            assertEquals("1.0", text(app.build().handle(request(HttpMethod.GET, "/api/version"))));
        }
    }

    // ---------- 잘못된 선언 ----------

    static final class DuplicateRouter extends BaseRouter {
        @Get("/users/{id}")
        public String byId(String id) {
            return id;
        }

        @Get("/users/{name}")
        public String byName(String name) {
            return name;
        }
    }

    static final class OtherUserRouter extends BaseRouter {
        OtherUserRouter() {
            super("/users");
        }

        @Get("/{name}")
        public String byName(String name) {
            return name;
        }
    }

    static final class BadPrefixRouter extends BaseRouter {
        BadPrefixRouter() {
            super("users");
        }
    }

    public static class UnknownPathParamApi {
        @Get("/users/{id}")
        public String user(@PathParam("userId") String userId) {
            return userId;
        }
    }

    @Nested
    @DisplayName("잘못된 사용")
    class Misuse {

        @Test
        @DisplayName("include된 라우터에는 라우트를 더 넣을 수 없다")
        void includedRouterIsFixed() {
            EmptyRouter child = new EmptyRouter("/child");
            new EmptyRouter().include(child);

            assertThrows(IllegalStateException.class, () -> child.register(new PingController()));
        }

        @Test
        @DisplayName("build한 뒤에는 라우트를 더 넣을 수 없다")
        void routerIsFixedAfterBuild() {
            EmptyRouter router = new EmptyRouter();
            router.build();

            assertThrows(IllegalStateException.class, () -> router.register(new PingController()));
        }

        @Test
        @DisplayName("한 클래스 안에서 변수 이름만 다른 같은 모양의 라우트는 예외")
        void duplicateInOneClass() {
            assertThrows(IllegalArgumentException.class, () -> new DuplicateRouter().build());
        }

        @Test
        @DisplayName("include로 합쳐진 라우트끼리 겹치면 예외")
        void duplicateAcrossIncludes() {
            EmptyRouter app = new EmptyRouter();
            app.include(new UserRouter("hi"));

            assertThrows(IllegalArgumentException.class, () -> app.include(new OtherUserRouter()));
        }

        @Test
        @DisplayName("Router를 register로 넘기면 include를 쓰라고 알려 준다")
        void registerRejectsRouter() {
            assertThrows(IllegalArgumentException.class, () -> new EmptyRouter().register(new UserRouter("hi")));
        }

        @Test
        @DisplayName("자기 자신을 include할 수 없다")
        void cannotIncludeItself() {
            EmptyRouter router = new EmptyRouter();

            assertThrows(IllegalArgumentException.class, () -> router.include(router));
        }

        @Test
        @DisplayName("prefix는 /로 시작해야 한다")
        void prefixMustStartWithSlash() {
            assertThrows(IllegalArgumentException.class, BadPrefixRouter::new);
        }

        @Test
        @DisplayName("패턴에 없는 경로 변수를 @PathParam으로 요구하면 등록할 때 실패한다")
        void unknownPathParam() {
            assertThrows(IllegalArgumentException.class, () -> new EmptyRouter().register(new UnknownPathParamApi()));
        }
    }
}
