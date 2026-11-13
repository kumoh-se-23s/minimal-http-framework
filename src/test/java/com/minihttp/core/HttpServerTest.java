package com.minihttp.core;

import com.minihttp.MiniHttp;
import com.minihttp.config.ServerConfig;
import com.minihttp.core.route.BaseRouter;
import com.minihttp.core.route.annotation.Get;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class HttpServerTest {
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    private HttpServer server;

    public static final class HelloRouter extends BaseRouter {
        private final CountDownLatch slowStarted;
        private final CountDownLatch releaseSlow;

        public HelloRouter(CountDownLatch slowStarted, CountDownLatch releaseSlow) {
            this.slowStarted = slowStarted;
            this.releaseSlow = releaseSlow;
        }

        @Get("/hello")
        public String hello(String name) {
            return "hello " + name;
        }

        @Get("/slow")
        public String slow() throws InterruptedException {
            this.slowStarted.countDown();
            this.releaseSlow.await(5, TimeUnit.SECONDS);
            return "done";
        }
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        if (this.server != null) {
            this.server.stop();
        }
    }

    private int startServer(int maxConnections, CountDownLatch slowStarted, CountDownLatch releaseSlow) throws IOException {
        ServerConfig config = ServerConfig.builder()
                                          .listen(new InetSocketAddress(LOOPBACK, 0))
                                          .maxConnections(maxConnections)
                                          .idleTimeout(Duration.ofSeconds(5))
                                          .shutdownGrace(Duration.ofSeconds(2))
                                          .build();

        this.server = MiniHttp.create()
                              .includeRouter(new HelloRouter(slowStarted, releaseSlow))
                              .start(config);
        return this.server.listeners().getFirst().boundAddress().getPort();
    }

    private int startServer() throws IOException {
        return this.startServer(8, new CountDownLatch(1), new CountDownLatch(0));
    }

    private static void send(Socket socket, String path) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        outputStream.flush();
    }

    /** 응답 하나를 Content-Length만큼 읽는다. keep-alive 연결에서 다음 응답과 섞이지 않게. */
    private static String readResponse(InputStream inputStream) throws IOException {
        ByteArrayOutputStream headBytes = new ByteArrayOutputStream();

        while (!headBytes.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n")) {
            int readByte = inputStream.read();
            if (readByte < 0) {
                return headBytes.toString(StandardCharsets.UTF_8);
            }
            headBytes.write(readByte);
        }

        String head = headBytes.toString(StandardCharsets.ISO_8859_1);
        int contentLength = head.lines()
                                .filter(line -> line.toLowerCase().startsWith("content-length:"))
                                .map(line -> Integer.parseInt(line.substring("content-length:".length()).strip()))
                                .findFirst()
                                .orElse(0);

        return head + new String(inputStream.readNBytes(contentLength), StandardCharsets.UTF_8);
    }

    @Test
    void servesJsonOverKeepAliveConnection() throws IOException {
        int port = this.startServer();

        try (Socket client = new Socket(LOOPBACK, port)) {
            HttpServerTest.send(client, "/hello?name=kim");
            String firstResponse = HttpServerTest.readResponse(client.getInputStream());

            HttpServerTest.send(client, "/nope");
            String secondResponse = HttpServerTest.readResponse(client.getInputStream());

            assertTrue(firstResponse.startsWith("HTTP/1.1 200 OK\r\n"), firstResponse);
            assertTrue(firstResponse.contains("Content-Type: application/json"), firstResponse);
            assertTrue(firstResponse.endsWith("\"hello kim\""), firstResponse);

            assertTrue(secondResponse.startsWith("HTTP/1.1 404 Not Found\r\n"), secondResponse);
            assertTrue(secondResponse.endsWith("{\"detail\":\"Not Found\"}"), secondResponse);
        }
    }

    @Test
    void rejectsWith503WhenAllSlotsAreTaken() throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        int port = this.startServer(1, slowStarted, releaseSlow);

        try (Socket busyClient = new Socket(LOOPBACK, port); Socket rejectedClient = new Socket(LOOPBACK, port)) {
            HttpServerTest.send(busyClient, "/slow");
            assertTrue(slowStarted.await(5, TimeUnit.SECONDS));

            String rejected = HttpServerTest.readResponse(rejectedClient.getInputStream());
            assertTrue(rejected.startsWith("HTTP/1.1 503 Service Unavailable\r\n"), rejected);
            assertTrue(rejected.contains("Connection: close"), rejected);

            releaseSlow.countDown();
            assertTrue(HttpServerTest.readResponse(busyClient.getInputStream()).endsWith("\"done\""));
        }
    }

    @Test
    void stopClosesIdleKeepAliveConnectionsWithoutWaitingForIdleTimeout() throws Exception {
        int port = this.startServer();

        try (Socket idleClient = new Socket(LOOPBACK, port)) {
            HttpServerTest.send(idleClient, "/hello?name=idle");
            HttpServerTest.readResponse(idleClient.getInputStream());

            long startedAt = System.nanoTime();
            this.server.stop();
            long stopMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertTrue(stopMillis < 1500, "stop() took " + stopMillis + "ms");
            assertEquals(-1, idleClient.getInputStream().read(), "서버가 쉬는 연결을 닫아야 한다");
            assertEquals(0, this.server.activeConnectionCount());
        }
    }
}
