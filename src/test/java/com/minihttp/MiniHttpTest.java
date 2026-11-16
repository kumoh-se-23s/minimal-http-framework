package com.minihttp;

import com.minihttp.core.handler.Handler;
import com.minihttp.core.middleware.CorsMiddleware;
import com.minihttp.core.response.JsonResponse;
import com.minihttp.core.route.BaseRouter;
import com.minihttp.core.route.annotation.Body;
import com.minihttp.core.route.annotation.Delete;
import com.minihttp.core.route.annotation.Depends;
import com.minihttp.core.route.annotation.Get;
import com.minihttp.core.route.annotation.Header;
import com.minihttp.core.route.annotation.Path;
import com.minihttp.core.route.annotation.Post;
import com.minihttp.core.route.annotation.Query;
import com.minihttp.core.route.dependency.Dependency;
import com.minihttp.core.route.exception.HttpStatusException;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniHttpTest {
    enum ProjectState {
        PENDING,
        APPROVED
    }

    static class CreateProjectRequest {
        private String title;
        private BigDecimal purposeAmount;
        private List<ProjectState> states;
    }

    record IdResponse(Long id) {
    }

    static class BusinessException extends RuntimeException {
        private final int stateCode;

        BusinessException(String message, int stateCode) {
            super(message);
            this.stateCode = stateCode;
        }

        String toJsonString() {
            return "{\"message\":\"" + this.getMessage() + "\",\"stateCode\":" + this.stateCode + "}";
        }
    }

    /** Authorization: Bearer user-{id} 형식의 가짜 토큰에서 사용자 id를 꺼낸다. */
    static final class CurrentUserId implements Dependency<Long> {
        private final String tokenPrefix;

        CurrentUserId(String tokenPrefix) {
            this.tokenPrefix = tokenPrefix;
        }

        @Override
        public Long resolve(HttpRequest request) {
            String authorization = request.header().get("Authorization")
                                          .orElseThrow(() -> new HttpStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
            return Long.parseLong(authorization.substring(this.tokenPrefix.length()));
        }
    }

    public static final class ProjectRouter extends BaseRouter {
        public ProjectRouter() {
            super("/projects/");
        }

        @Get
        public Map<String, Object> list(Optional<Integer> limit, @Query(defaultValue = "PENDING") ProjectState state) {
            return Map.of("limit", limit.orElse(-1), "state", state);
        }

        @Get("/me")
        public String mine() {
            return "static wins";
        }

        @Get("/{projectId}")
        public Map<String, Object> get(long projectId) {
            return Map.of("projectId", projectId);
        }

        @Get("/search/by-state")
        public List<ProjectState> search(List<ProjectState> states, LocalDate since) {
            return states;
        }

        @Post(status = HttpStatus.CREATED)
        public IdResponse create(@Depends(CurrentUserId.class) Long makerId, CreateProjectRequest request) {
            if (request.title == null) {
                throw new BusinessException("title required", 510);
            }
            return new IdResponse(makerId * 100 + request.states.size());
        }

        @Delete("/{projectId}")
        public void delete(@Path("projectId") Long id) {
        }

        @Post("/{projectId}/image")
        public Map<String, Integer> upload(long projectId, @Body byte[] image) {
            return Map.of("size", image.length);
        }

        @Get("/header")
        public String header(@Header String userAgent, @Header(value = "X-Trace", defaultValue = "none") String trace) {
            return userAgent + "/" + trace;
        }

        @Get("/filter")
        public String filter(Optional<String> keyword, Optional<Integer> page, Optional<List<String>> tags,
                             @Query(defaultValue = "") String sort) {
            return keyword.orElse("-") + "/" + page.orElse(-1) + "/" + tags.orElse(List.of()) + "/[" + sort + "]";
        }

        @Get("/echo")
        public String echo(String text) {
            return text;
        }

        @Get("/absent")
        public String absent(@Query(required = false) int page, @Query(required = false) boolean exact,
                             @Query(required = false) Integer size, @Query(required = false) String keyword,
                             @Query(required = false) List<String> tags, @Header(value = "X-Trace", required = false) String trace) {
            return page + "/" + exact + "/" + size + "/" + keyword + "/" + tags + "/" + trace;
        }

        @Get("/required")
        public String required(int page, @Header("X-Trace") String trace) {
            return page + "/" + trace;
        }

        @Get("/raw")
        public HttpResponse raw(HttpRequest request) {
            return JsonResponse.fromJsonText(HttpStatus.ACCEPTED, "{\"path\":\"" + request.path() + "\"}");
        }
    }

    private static Handler handler(BaseRouter... routers) {
        MiniHttp app = MiniHttp.create().dependency(new CurrentUserId("Bearer user-"));
        for (BaseRouter router : routers) {
            app.includeRouter(router);
        }
        return app.buildHandler();
    }

    private static HttpRequest request(HttpMethod method, String target) {
        return MiniHttpTest.request(method, target, Map.of(), "");
    }

    private static HttpRequest request(HttpMethod method, String target, Map<String, String> headers, String body) {
        int questionIndex = target.indexOf('?');
        String path = questionIndex < 0 ? target : target.substring(0, questionIndex);

        Map<String, List<String>> queryParams = new LinkedHashMap<>();
        if (questionIndex >= 0) {
            for (String queryPair : target.substring(questionIndex + 1).split("&")) {
                String[] keyAndValue = queryPair.split("=", 2);
                String value = keyAndValue.length > 1 ? keyAndValue[1] : "";
                queryParams.computeIfAbsent(keyAndValue[0], ignored -> new ArrayList<>()).add(value);
            }
        }

        HttpHeader header = new DefaultHttpHeader();
        header.set(HttpHeaderField.HOST, "localhost");
        headers.forEach(header::set);

        return new HttpRequest(HttpVersion.HTTP_1_1, header, new DefaultHttpBody(body.getBytes(StandardCharsets.UTF_8)),
                               method, path, queryParams, Map.of());
    }

    private static String bodyOf(HttpResponse response) {
        return response.body().asString(StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("라우팅")
    class RoutingTest {
        private final Handler handler = MiniHttpTest.handler(new ProjectRouter());

        @Test
        void appliesRouterPrefixAndRootPath() throws Exception {
            HttpResponse response = this.handler.handle(MiniHttpTest.request(HttpMethod.GET, "/projects?limit=5"));

            assertEquals(HttpStatus.OK, response.status());
            assertEquals("application/json", response.contentType().orElseThrow());
            assertTrue(MiniHttpTest.bodyOf(response).contains("\"limit\":5"));
            assertTrue(MiniHttpTest.bodyOf(response).contains("\"state\":\"PENDING\""));
        }

        @Test
        void staticPathWinsOverVariablePath() throws Exception {
            assertEquals("\"static wins\"", MiniHttpTest.bodyOf(this.handler.handle(MiniHttpTest.request(HttpMethod.GET, "/projects/me"))));
            assertEquals("{\"projectId\":42}", MiniHttpTest.bodyOf(this.handler.handle(MiniHttpTest.request(HttpMethod.GET, "/projects/42"))));
        }

        @Test
        void unknownPathIsFastApiStyle404() throws Exception {
            HttpResponse response = this.handler.handle(MiniHttpTest.request(HttpMethod.GET, "/nope"));

            assertEquals(HttpStatus.NOT_FOUND, response.status());
            assertEquals("{\"detail\":\"Not Found\"}", MiniHttpTest.bodyOf(response));
        }

        @Test
        void wrongMethodIs405WithAllowHeader() throws Exception {
            HttpResponse response = this.handler.handle(MiniHttpTest.request(HttpMethod.PUT, "/projects/1"));

            assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.status());
            assertEquals("{\"detail\":\"Method Not Allowed\"}", MiniHttpTest.bodyOf(response));
            String allowedMethods = response.header().get(HttpHeaderField.ALLOW).orElseThrow();
            assertTrue(allowedMethods.contains("GET") && allowedMethods.contains("DELETE"));
        }
    }

    @Nested
    @DisplayName("파라미터")
    class ParameterTest {
        private final Handler handler = MiniHttpTest.handler(new ProjectRouter());

        @Test
        void invalidPathValueIs422WithLocation() throws Exception {
            HttpResponse response = this.handler.handle(MiniHttpTest.request(HttpMethod.GET, "/projects/abc"));

            assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.status());
            assertEquals("{\"detail\":[{\"type\":\"int_parsing\",\"loc\":[\"path\",\"projectId\"],"
                                 + "\"msg\":\"Input should be a valid integer, unable to parse string as an integer\"}]}",
                         MiniHttpTest.bodyOf(response));
        }

        @Test
        void repeatedQueryBecomesListAndAllErrorsAreReported() throws Exception {
            HttpResponse okResponse = this.handler.handle(MiniHttpTest.request(
                    HttpMethod.GET, "/projects/search/by-state?states=PENDING&states=APPROVED&since=2026-01-01"));
            assertEquals("[\"PENDING\",\"APPROVED\"]", MiniHttpTest.bodyOf(okResponse));

            HttpResponse errorResponse = this.handler.handle(MiniHttpTest.request(
                    HttpMethod.GET, "/projects/search/by-state?states=PENDING&states=NOPE"));
            String errorBody = MiniHttpTest.bodyOf(errorResponse);
            assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, errorResponse.status());
            assertTrue(errorBody.contains("\"loc\":[\"query\",\"states\",1]"), errorBody);
            assertTrue(errorBody.contains("\"type\":\"missing\",\"loc\":[\"query\",\"since\"]"), errorBody);
        }

        @Test
        void emptyQueryValueIs422ForEveryType() throws Exception {
            String emptyValueError = "\"type\":\"string_too_short\",\"loc\":[\"query\",%s]";

            for (String target : List.of("/projects/echo?text=", "/projects/echo?text", "/projects/echo?text=a&text=")) {
                HttpResponse response = this.handler.handle(MiniHttpTest.request(HttpMethod.GET, target));
                String body = MiniHttpTest.bodyOf(response);
                assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.status(), target);
                assertTrue(body.contains(emptyValueError.formatted("\"text\"")), body);
            }

            HttpResponse optionalResponse = this.handler.handle(MiniHttpTest.request(
                    HttpMethod.GET, "/projects/filter?keyword=&page="));
            String optionalBody = MiniHttpTest.bodyOf(optionalResponse);
            assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, optionalResponse.status());
            assertTrue(optionalBody.contains(emptyValueError.formatted("\"keyword\"")), optionalBody);
            assertTrue(optionalBody.contains(emptyValueError.formatted("\"page\"")), optionalBody);

            HttpResponse listResponse = this.handler.handle(MiniHttpTest.request(
                    HttpMethod.GET, "/projects/filter?tags=a&tags=&tags="));
            String listBody = MiniHttpTest.bodyOf(listResponse);
            assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, listResponse.status());
            assertTrue(listBody.contains(emptyValueError.formatted("\"tags\",1")), listBody);
            assertTrue(listBody.contains(emptyValueError.formatted("\"tags\",2")), listBody);
        }

        @Test
        void absentQueryKeyAndEmptyDefaultValueStillWork() throws Exception {
            HttpResponse absentResponse = this.handler.handle(MiniHttpTest.request(HttpMethod.GET, "/projects/filter"));
            assertEquals("\"-/-1/[]/[]\"", MiniHttpTest.bodyOf(absentResponse));

            HttpResponse filledResponse = this.handler.handle(MiniHttpTest.request(
                    HttpMethod.GET, "/projects/filter?keyword= &page=2&tags=a&tags=b&sort=id"));
            assertEquals("\" /2/[a, b]/[id]\"", MiniHttpTest.bodyOf(filledResponse));
        }

        @Test
        void absentOptionalParameterIsTypeDefaultOrNull() throws Exception {
            HttpResponse response = this.handler.handle(MiniHttpTest.request(HttpMethod.GET, "/projects/absent"));

            assertEquals("\"0/false/null/null/null/null\"", MiniHttpTest.bodyOf(response));
        }

        @Test
        void absentRequiredParameterIs422() throws Exception {
            HttpResponse errorResponse = this.handler.handle(MiniHttpTest.request(HttpMethod.GET, "/projects/required"));
            String errorBody = MiniHttpTest.bodyOf(errorResponse);
            assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, errorResponse.status());
            assertTrue(errorBody.contains("\"type\":\"missing\",\"loc\":[\"query\",\"page\"]"), errorBody);
            assertTrue(errorBody.contains("\"type\":\"missing\",\"loc\":[\"header\",\"X-Trace\"]"), errorBody);

            HttpResponse okResponse = this.handler.handle(MiniHttpTest.request(
                    HttpMethod.GET, "/projects/required?page=3", Map.of("X-Trace", "abc"), ""));
            assertEquals("\"3/abc\"", MiniHttpTest.bodyOf(okResponse));
        }

        @Test
        void emptyHeaderValueIsStillAllowed() throws Exception {
            HttpResponse response = this.handler.handle(MiniHttpTest.request(
                    HttpMethod.GET, "/projects/header", Map.of("User-Agent", "curl", "X-Trace", ""), ""));

            assertEquals("\"curl/\"", MiniHttpTest.bodyOf(response));
        }

        @Test
        void headersUseKebabCaseNamesAndDefaults() throws Exception {
            HttpResponse response = this.handler.handle(MiniHttpTest.request(
                    HttpMethod.GET, "/projects/header", Map.of("User-Agent", "curl"), ""));

            assertEquals("\"curl/none\"", MiniHttpTest.bodyOf(response));
        }

        @Test
        void bodyDtoAndDependencyAreBoundAndStatusIsApplied() throws Exception {
            HttpResponse response = this.handler.handle(MiniHttpTest.request(
                    HttpMethod.POST, "/projects",
                    Map.of("Authorization", "Bearer user-7", "Content-Type", "application/json; charset=utf-8"),
                    "{\"title\":\"펀딩\",\"purposeAmount\":10.5,\"states\":[\"PENDING\",\"APPROVED\"]}"));

            assertEquals(HttpStatus.CREATED, response.status());
            assertEquals("{\"id\":702}", MiniHttpTest.bodyOf(response));
        }

        @Test
        void dependencyFailureBecomesItsStatus() throws Exception {
            HttpResponse response = this.handler.handle(MiniHttpTest.request(HttpMethod.POST, "/projects", Map.of(), "{}"));

            assertEquals(HttpStatus.UNAUTHORIZED, response.status());
            assertEquals("{\"detail\":\"Not authenticated\"}", MiniHttpTest.bodyOf(response));
        }

        @Test
        void brokenOrMissingBodyIs422() throws Exception {
            Map<String, String> authorized = Map.of("Authorization", "Bearer user-1");

            String brokenBody = MiniHttpTest.bodyOf(this.handler.handle(
                    MiniHttpTest.request(HttpMethod.POST, "/projects", authorized, "{\"title\":")));
            assertTrue(brokenBody.contains("\"type\":\"json_invalid\",\"loc\":[\"body\",9]"), brokenBody);

            String missingBody = MiniHttpTest.bodyOf(this.handler.handle(
                    MiniHttpTest.request(HttpMethod.POST, "/projects", authorized, "")));
            assertEquals("{\"detail\":[{\"type\":\"missing\",\"loc\":[\"body\"],\"msg\":\"Field required\"}]}", missingBody);

            String wrongField = MiniHttpTest.bodyOf(this.handler.handle(
                    MiniHttpTest.request(HttpMethod.POST, "/projects", authorized, "{\"states\":\"PENDING\"}")));
            assertTrue(wrongField.contains("\"type\":\"list_type\",\"loc\":[\"body\",\"states\"]"), wrongField);
        }

        @Test
        void byteArrayBodyIsPassedRaw() throws Exception {
            HttpResponse response = this.handler.handle(MiniHttpTest.request(
                    HttpMethod.POST, "/projects/3/image", Map.of("Content-Type", "image/png"), "PNGDATA"));

            assertEquals("{\"size\":7}", MiniHttpTest.bodyOf(response));
        }
    }

    @Nested
    @DisplayName("응답")
    class ResponseTest {
        private final Handler handler = MiniHttpTest.handler(new ProjectRouter());

        @Test
        void voidReturnsJsonNullLikeFastApi() throws Exception {
            HttpResponse response = this.handler.handle(MiniHttpTest.request(HttpMethod.DELETE, "/projects/1"));

            assertEquals(HttpStatus.OK, response.status());
            assertEquals("null", MiniHttpTest.bodyOf(response));
        }

        @Test
        void httpResponseIsReturnedAsIs() throws Exception {
            HttpResponse response = this.handler.handle(MiniHttpTest.request(HttpMethod.GET, "/projects/raw"));

            assertEquals(HttpStatus.ACCEPTED, response.status());
            assertEquals("{\"path\":\"/projects/raw\"}", MiniHttpTest.bodyOf(response));
        }
    }

    @Nested
    @DisplayName("예외 핸들러와 미들웨어")
    class PipelineTest {
        @Test
        void customExceptionHandlerReplacesDefault500() throws Exception {
            Handler handler = MiniHttp.create()
                                      .dependency(new CurrentUserId("Bearer user-"))
                                      .includeRouter(new ProjectRouter())
                                      .exceptionHandler(BusinessException.class, (request, exception) ->
                                              JsonResponse.fromJsonText(HttpStatus.BAD_REQUEST, exception.toJsonString()))
                                      .buildHandler();

            HttpResponse response = handler.handle(MiniHttpTest.request(
                    HttpMethod.POST, "/projects", Map.of("Authorization", "Bearer user-1"), "{\"states\":[]}"));

            assertEquals(HttpStatus.BAD_REQUEST, response.status());
            assertEquals("{\"message\":\"title required\",\"stateCode\":510}", MiniHttpTest.bodyOf(response));
        }

        @Test
        void unhandledExceptionIs500PlainText() throws Exception {
            HttpResponse response = MiniHttpTest.handler(new ProjectRouter()).handle(MiniHttpTest.request(
                    HttpMethod.POST, "/projects", Map.of("Authorization", "Bearer user-1"), "{\"states\":[]}"));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.status());
            assertEquals("Internal Server Error", MiniHttpTest.bodyOf(response));
        }

        @Test
        void lastAddedMiddlewareRunsOutermost() throws Exception {
            List<String> calls = new ArrayList<>();
            Handler handler = MiniHttp.create()
                                      .includeRouter(new ProjectRouter())
                                      .dependency(new CurrentUserId("Bearer user-"))
                                      .middleware((request, next) -> {
                                          calls.add("first");
                                          return next.handle(request);
                                      })
                                      .middleware((request, next) -> {
                                          calls.add("second");
                                          return next.handle(request);
                                      })
                                      .buildHandler();

            handler.handle(MiniHttpTest.request(HttpMethod.GET, "/projects/me"));

            assertEquals(List.of("second", "first"), calls);
        }

        @Test
        void corsAnswersPreflightAndDecoratesErrorResponses() throws Exception {
            Handler handler = MiniHttp.create()
                                      .includeRouter(new ProjectRouter())
                                      .dependency(new CurrentUserId("Bearer user-"))
                                      .middleware(CorsMiddleware.builder()
                                                                .allowOrigins("http://localhost:5173")
                                                                .allowAllMethods()
                                                                .allowAllHeaders()
                                                                .allowCredentials(true)
                                                                .build())
                                      .buildHandler();

            HttpResponse preflight = handler.handle(MiniHttpTest.request(HttpMethod.OPTIONS, "/projects", Map.of(
                    "Origin", "http://localhost:5173",
                    "Access-Control-Request-Method", "POST",
                    "Access-Control-Request-Headers", "authorization"), ""));
            assertEquals(HttpStatus.OK, preflight.status());
            assertEquals("http://localhost:5173", preflight.header().get(HttpHeaderField.ACCESS_CONTROL_ALLOW_ORIGIN).orElseThrow());
            assertEquals("authorization", preflight.header().get(HttpHeaderField.ACCESS_CONTROL_ALLOW_HEADERS).orElseThrow());
            assertEquals("true", preflight.header().get(HttpHeaderField.ACCESS_CONTROL_ALLOW_CREDENTIALS).orElseThrow());

            HttpResponse notFound = handler.handle(MiniHttpTest.request(
                    HttpMethod.GET, "/nope", Map.of("Origin", "http://localhost:5173"), ""));
            assertEquals(HttpStatus.NOT_FOUND, notFound.status());
            assertTrue(notFound.header().contains(HttpHeaderField.ACCESS_CONTROL_ALLOW_ORIGIN));

            HttpResponse foreign = handler.handle(MiniHttpTest.request(
                    HttpMethod.GET, "/projects/me", Map.of("Origin", "http://evil.example"), ""));
            assertFalse(foreign.header().contains(HttpHeaderField.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    @Nested
    @DisplayName("시작 시 검사")
    class StartupValidationTest {
        public static final class DuplicateRouter extends BaseRouter {
            public DuplicateRouter() {
                super("/projects");
            }

            @Get("/{otherId}")
            public String duplicate(long otherId) {
                return "";
            }
        }

        public static final class UnboundPathRouter extends BaseRouter {
            @Get("/items/{itemId}")
            public String item(long id) {
                return "";
            }
        }

        public static final class TwoBodiesRouter extends BaseRouter {
            @Post("/items")
            public String create(CreateProjectRequest first, CreateProjectRequest second) {
                return "";
            }
        }

        static final class NeedsArgument implements Dependency<String> {
            NeedsArgument(String value) {
            }

            @Override
            public String resolve(HttpRequest request) {
                return "";
            }
        }

        public static final class UnregisteredDependencyRouter extends BaseRouter {
            @Get("/items")
            public String items(@Depends(NeedsArgument.class) String value) {
                return value;
            }
        }

        public static final class WrongDependencyTypeRouter extends BaseRouter {
            @Get("/items")
            public String items(@Depends(CurrentUserId.class) String userId) {
                return userId;
            }
        }

        @Test
        void rejectsInvalidDeclarationsBeforeServing() {
            Set<BaseRouter> invalidSetups = Set.of(
                    new UnboundPathRouter(),
                    new TwoBodiesRouter(),
                    new UnregisteredDependencyRouter(),
                    new WrongDependencyTypeRouter()
            );

            for (BaseRouter invalidRouter : invalidSetups) {
                assertThrows(IllegalStateException.class, () -> MiniHttpTest.handler(invalidRouter),
                             invalidRouter.getClass().getSimpleName());
            }

            assertThrows(IllegalStateException.class, () -> MiniHttpTest.handler(new ProjectRouter(), new DuplicateRouter()));
        }
    }
}
