package com.minihttp.example;

import com.minihttp.example.routing.Router;
import com.minihttp.example.server.HttpServer;

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
        return new Router().register(new ExampleApi());
    }
}
