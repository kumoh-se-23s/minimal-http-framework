package com.minihttp.example.server;

import com.minihttp.example.ExampleApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 실제 소켓으로 서버를 띄워서 확인한다. */
class HttpServerTest {
    private HttpServer server;
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @BeforeEach
    void start() throws IOException {
        server = new HttpServer(0, ExampleApp.createRouter());
        server.start();
    }

    @AfterEach
    void stop() throws IOException {
        server.close();
    }

    @Test
    void servesGetWithQuery() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/hello?name=%EA%B9%80")).build());

        assertEquals(200, response.statusCode());
        assertEquals("Hello, 김!", response.body());
    }

    @Test
    void echoesPostBody() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/echo"))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString("안녕하세요"))
                .build());

        assertEquals(200, response.statusCode());
        assertEquals("안녕하세요", response.body());
    }

    @Test
    void returnsJsonForPathParam() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/users/42")).build());

        assertEquals("{\"id\":42}", response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
    }

    @Test
    void returnsNotFound() throws Exception {
        assertEquals(404, send(HttpRequest.newBuilder(uri("/missing")).build()).statusCode());
    }

    @Test
    void handlesMultipleRequestsOnOneConnection() throws Exception {
        try (Socket socket = new Socket("localhost", server.port())) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET / HTTP/1.1\r\nHost: x\r\n\r\n"
                    + "GET /hello HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String raw = readAll(socket.getInputStream());

            assertTrue(raw.contains("Hello, minihttp!"));
            assertTrue(raw.contains("Hello, world!"));
            assertEquals(2, raw.split("HTTP/1.1 200 OK", -1).length - 1);
        }
    }

    @Test
    void respondsBadRequestToGarbage() throws Exception {
        try (Socket socket = new Socket("localhost", server.port())) {
            socket.getOutputStream().write("hello\r\n\r\n".getBytes(StandardCharsets.US_ASCII));

            assertTrue(readAll(socket.getInputStream()).startsWith("HTTP/1.1 400 Bad Request\r\n"));
        }
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + server.port() + path);
    }

    /** 서버가 Connection: close로 소켓을 닫을 때까지 읽는다. */
    private static String readAll(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
