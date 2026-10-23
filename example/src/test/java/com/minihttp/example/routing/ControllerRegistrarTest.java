package com.minihttp.example.routing;

import com.minihttp.example.http.HttpHeaders;
import com.minihttp.example.http.HttpMethod;
import com.minihttp.example.http.HttpRequest;
import com.minihttp.example.http.HttpResponse;
import com.minihttp.example.http.HttpStatus;
import com.minihttp.example.routing.annotation.Body;
import com.minihttp.example.routing.annotation.Delete;
import com.minihttp.example.routing.annotation.Get;
import com.minihttp.example.routing.annotation.Header;
import com.minihttp.example.routing.annotation.PathParam;
import com.minihttp.example.routing.annotation.Post;
import com.minihttp.example.routing.annotation.Query;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerRegistrarTest {
    enum Sort { ASC, DESC }

    public static class Api {
        @Get("/items/{id}")
        public String item(int id, Optional<Sort> sort, @Query(defaultValue = "10") int limit) {
            return id + ":" + sort.map(Enum::name).orElse("none") + ":" + limit;
        }

        @Get("/items/latest")
        public String latest() {
            return "latest";
        }

        @Get("/search")
        public String search(@Query("q") String keyword, boolean exact) {
            return keyword + ":" + exact;
        }

        @Get("/agent")
        public String agent(@Header String userAgent, @Header("X-Trace") Optional<String> trace) {
            return userAgent + ":" + trace.orElse("-");
        }

        @Post("/files/{name}")
        public HttpResponse upload(@PathParam("name") String fileName, @Body String content, HttpRequest request) {
            return HttpResponse.text(HttpStatus.CREATED, fileName + "=" + content + "@" + request.path());
        }

        @Delete("/items/{id}")
        public void remove(long id) {
        }

        @Get("/boom")
        public String boom() throws Exception {
            throw new IllegalStateException("boom");
        }

        public String notARoute() {
            return "";
        }
    }

    public static class UnknownPathParam {
        @Get("/a")
        public String a(@PathParam("id") String id) {
            return id;
        }
    }

    public static class UnsupportedType {
        @Get("/a")
        public String a(Object value) {
            return "";
        }
    }

    public static class UnsupportedReturn {
        @Get("/a")
        public Object a() {
            return "";
        }
    }

    private final Router router = new Router().register(new Api());

    @Test
    void infersPathAndQueryParamsAndConvertsTypes() throws Exception {
        assertEquals("7:DESC:3", body(route(HttpMethod.GET, "/items/7", Map.of("sort", "desc", "limit", "3"))));
        assertEquals("7:none:10", body(route(HttpMethod.GET, "/items/7", Map.of())));
        assertEquals("kim:true", body(route(HttpMethod.GET, "/search", Map.of("q", "kim", "exact", "TRUE"))));
    }

    @Test
    void literalSegmentWinsOverVariable() throws Exception {
        assertEquals("latest", body(route(HttpMethod.GET, "/items/latest", Map.of())));
    }

    @Test
    void invalidValueIsBadRequest() throws Exception {
        HttpResponse response = route(HttpMethod.GET, "/items/abc", Map.of());

        assertEquals(HttpStatus.BAD_REQUEST, response.status());
        assertEquals("Invalid path parameter 'id': expected int", body(response));
        assertEquals(HttpStatus.BAD_REQUEST, route(HttpMethod.GET, "/items/1", Map.of("sort", "up")).status());
        assertEquals(HttpStatus.BAD_REQUEST, route(HttpMethod.GET, "/search", Map.of("q", "a", "exact", "yes")).status());
    }

    @Test
    void missingRequiredParamIsBadRequest() throws Exception {
        HttpResponse response = route(HttpMethod.GET, "/search", Map.of("exact", "false"));

        assertEquals(HttpStatus.BAD_REQUEST, response.status());
        assertEquals("Missing query parameter: q", body(response));
    }

    @Test
    void readsHeadersByCamelCaseName() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("user-agent", "curl");

        assertEquals("curl:-", body(router.route(request(HttpMethod.GET, "/agent", Map.of(), headers, new byte[0]))));
    }

    @Test
    void injectsBodyAndRequest() throws Exception {
        HttpResponse response = router.route(request(HttpMethod.POST, "/files/a.txt", Map.of(), new HttpHeaders(),
                "안녕".getBytes(StandardCharsets.UTF_8)));

        assertEquals(HttpStatus.CREATED, response.status());
        assertEquals("a.txt=안녕@/files/a.txt", body(response));
    }

    @Test
    void voidReturnsNoContent() throws Exception {
        assertEquals(HttpStatus.NO_CONTENT, route(HttpMethod.DELETE, "/items/1", Map.of()).status());
    }

    @Test
    void handlerExceptionPropagates() {
        assertThrows(IllegalStateException.class, () -> route(HttpMethod.GET, "/boom", Map.of()));
    }

    @Test
    void methodNotAllowedStillWorks() throws Exception {
        HttpResponse response = route(HttpMethod.POST, "/items/1", Map.of());

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.status());
        assertTrue(response.headers().get("Allow").orElseThrow().contains("DELETE"));
    }

    @Test
    void rejectsInvalidControllersAtRegistration() {
        assertThrows(IllegalArgumentException.class, () -> new Router().register(new UnknownPathParam()));
        assertThrows(IllegalArgumentException.class, () -> new Router().register(new UnsupportedType()));
        assertThrows(IllegalArgumentException.class, () -> new Router().register(new UnsupportedReturn()));
    }

    private HttpResponse route(HttpMethod method, String path, Map<String, String> query) throws Exception {
        return router.route(request(method, path, query, new HttpHeaders(), new byte[0]));
    }

    private static HttpRequest request(HttpMethod method, String path, Map<String, String> query,
                                       HttpHeaders headers, byte[] body) {
        return new HttpRequest(method, path, new LinkedHashMap<>(query), "HTTP/1.1", headers, body, Map.of());
    }

    private static String body(HttpResponse response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }
}
