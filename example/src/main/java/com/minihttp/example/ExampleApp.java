package com.minihttp.example;

import com.minihttp.example.http.HttpResponse;
import com.minihttp.example.http.HttpStatus;
import com.minihttp.example.routing.Router;
import com.minihttp.example.server.HttpServer;

/**
 * 실행: ./gradlew :example:run  (포트 변경: ./gradlew :example:run --args=9090)
 *
 * <pre>
 * curl -v localhost:8080/
 * curl -v "localhost:8080/hello?name=kim"
 * curl -v localhost:8080/users/42
 * curl -v -X POST -d '안녕' localhost:8080/echo
 * </pre>
 */
public class ExampleApp {
    public static void main(String[] args) throws Exception {
        Router router = createRouter();
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;

        HttpServer server = new HttpServer(port, router);
        server.start();
        System.out.println("Listening on http://localhost:" + server.port());
        server.join();
    }

    public static Router createRouter() {
        return new Router()
                .get("/", request -> HttpResponse.ok("Hello, minihttp!"))
                .get("/hello", request -> HttpResponse.ok("Hello, " + request.query("name").orElse("world") + "!"))
                .get("/users/{id}", request -> {
                    String id = request.pathParam("id");
                    if (!id.matches("\\d+")) {
                        return HttpResponse.text(HttpStatus.BAD_REQUEST, "id must be a number");
                    }
                    return HttpResponse.json(HttpStatus.OK, "{\"id\":" + id + "}");
                })
                .post("/echo", request -> HttpResponse.of(
                        HttpStatus.OK,
                        request.body(),
                        request.header("Content-Type").orElse("application/octet-stream")));
    }
}
