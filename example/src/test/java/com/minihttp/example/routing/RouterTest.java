package com.minihttp.example.routing;

import com.minihttp.example.http.HttpHeaders;
import com.minihttp.example.http.HttpMethod;
import com.minihttp.example.http.HttpRequest;
import com.minihttp.example.http.HttpResponse;
import com.minihttp.example.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouterTest {
    private final Router router = new Router()
            .get("/", request -> HttpResponse.ok("root"))
            .get("/users/{id}", request -> HttpResponse.ok("user " + request.pathParam("id")))
            .post("/users", request -> HttpResponse.of(HttpStatus.CREATED))
            .get("/users/{id}/posts/{postId}",
                    request -> HttpResponse.ok(request.pathParam("id") + "/" + request.pathParam("postId")));

    @Test
    void matchesStaticPath() throws Exception {
        assertEquals("root", body(route(HttpMethod.GET, "/")));
    }

    @Test
    void extractsPathParams() throws Exception {
        assertEquals("user 42", body(route(HttpMethod.GET, "/users/42")));
        assertEquals("1/2", body(route(HttpMethod.GET, "/users/1/posts/2")));
    }

    @Test
    void ignoresTrailingSlash() throws Exception {
        assertEquals("user 7", body(route(HttpMethod.GET, "/users/7/")));
    }

    @Test
    void returnsNotFoundForUnknownPath() throws Exception {
        assertEquals(HttpStatus.NOT_FOUND, route(HttpMethod.GET, "/nope").status());
    }

    @Test
    void returnsMethodNotAllowedWithAllowHeader() throws Exception {
        HttpResponse response = route(HttpMethod.DELETE, "/users/1");

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.status());
        assertEquals("GET", response.headers().get("Allow").orElseThrow());
    }

    @Test
    void headFallsBackToGet() throws Exception {
        assertEquals("root", body(route(HttpMethod.HEAD, "/")));
    }

    private HttpResponse route(HttpMethod method, String path) throws Exception {
        return router.route(new HttpRequest(method, path, Map.of(), "HTTP/1.1", new HttpHeaders(), new byte[0], Map.of()));
    }

    private static String body(HttpResponse response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }
}
