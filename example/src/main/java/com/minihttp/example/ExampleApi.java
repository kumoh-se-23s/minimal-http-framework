package com.minihttp.example;

import com.minihttp.example.http.HttpResponse;
import com.minihttp.example.http.HttpStatus;
import com.minihttp.example.routing.annotation.Body;
import com.minihttp.example.routing.annotation.Get;
import com.minihttp.example.routing.annotation.Header;
import com.minihttp.example.routing.annotation.Post;
import com.minihttp.example.routing.annotation.Query;

import java.util.Optional;

public class ExampleApi {
    @Get("/")
    public String root() {
        return "Hello, minihttp!";
    }

    @Get("/hello")
    public String hello(@Query(defaultValue = "world") String name) {
        return "Hello, " + name + "!";
    }

    @Get("/add")
    public String add(int a, int b) {
        return String.valueOf(a + b);
    }

    @Get("/users/{id}")
    public HttpResponse user(long id) {
        return HttpResponse.json(HttpStatus.OK, "{\"id\":" + id + "}");
    }

    @Post("/echo")
    public HttpResponse echo(@Body byte[] body, @Header Optional<String> contentType) {
        return HttpResponse.of(HttpStatus.OK, body, contentType.orElse("application/octet-stream"));
    }
}
