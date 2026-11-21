package com.minihttp.router;

import com.minihttp.core.response.PlainTextResponse;
import com.minihttp.core.route.BaseRouter;
import com.minihttp.core.route.annotation.Depends;
import com.minihttp.core.route.annotation.Get;
import com.minihttp.core.route.annotation.Post;
import com.minihttp.core.route.dependency.security.BasicSchema;
import com.minihttp.core.route.dependency.security.BearerSchema;
import com.minihttp.core.route.exception.HttpStatusException;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ExampleRouter extends BaseRouter {
    public record Greeting(String name, String message) {
    }

    public record CreateItemRequest(String title, int quantity, List<String> tags) {
    }

    public record AuthenticatedUser(String username, String role) {
    }

    public static final class ExampleBearerAuth extends BearerSchema<AuthenticatedUser> {
        private static final String VALID_TOKEN = "example-token";

        @Override
        protected AuthenticatedUser authenticate(String token) {
            if (!ExampleBearerAuth.VALID_TOKEN.equals(token)) {
                throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Invalid token",
                                              Map.of(HttpHeaderField.WWW_AUTHENTICATE, "Bearer"));
            }

            return new AuthenticatedUser("bearer-user", "USER");
        }
    }

    public static final class ExampleBasicAuth extends BasicSchema<AuthenticatedUser> {
        private static final String VALID_USERNAME = "admin";
        private static final String VALID_PASSWORD = "1234";

        @Override
        protected AuthenticatedUser authenticate(String username, String password) {
            if (!ExampleBasicAuth.VALID_USERNAME.equals(username) || !ExampleBasicAuth.VALID_PASSWORD.equals(password)) {
                throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Incorrect username or password",
                                              Map.of(HttpHeaderField.WWW_AUTHENTICATE, "Basic"));
            }

            return new AuthenticatedUser(username, "ADMIN");
        }
    }

    public ExampleRouter() {
        super("/example");
    }

    // GET /example/test → "되냐?"  (문자열도 JSON으로 나간다)
    @Get("/test")
    public String test() {
        return "되냐?";
    }

    // GET /example/hello?name=철수 → {"name":"철수","message":"내 이름은 철수이지롱"}
    @Get("/hello")
    public Greeting hello(String name) {
        return new Greeting(name, "내 이름은 " + name + "이지롱");
    }

    // GET /example/items/3?detail=true
    @Get("/items/{itemId}")
    public Map<String, Object> item(long itemId, Optional<Boolean> detail) {
        if (itemId <= 0) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Item not found");
        }
        return Map.of("itemId", itemId, "detail", detail.orElse(false));
    }

    // POST /example/items  {"title":"사과","quantity":3,"tags":["fruit"]} → 201
    @Post(value = "/items", status = HttpStatus.CREATED)
    public CreateItemRequest createItem(CreateItemRequest request) {
        return request;
    }

    // 직접 만든 응답은 그대로 나간다.
    @Get("/plain")
    public HttpResponse plain() {
        return new PlainTextResponse("plain text");
    }

    @Get("/secure/bearer")
    public AuthenticatedUser bearerMe(@Depends(ExampleBearerAuth.class) AuthenticatedUser user) {
        return user;
    }

    @Get("/secure/basic")
    public AuthenticatedUser basicMe(@Depends(ExampleBasicAuth.class) AuthenticatedUser user) {
        return user;
    }
}
