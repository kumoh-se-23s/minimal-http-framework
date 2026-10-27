package com.minihttp.core;

import com.minihttp.config.ListenerConfig;
import com.minihttp.config.ServerConfig;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.ContentType;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.DefaultHttpBody;
import com.minihttp.packet.structure.DefaultHttpHeader;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public final class HttpServerExampleMain {
    public static void main(String[] args) throws Exception {
        ServerConfig config = new ServerConfig(
                List.of(ListenerConfig.of(8080)),
                256,
                8192,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5)
        );

        HttpServerExample server = new HttpServerExample(config, request -> {
            String body = "hello from minihttp\n"
                    + request.method() + " " + request.path() + "\n"
                    + "thread: " + Thread.currentThread() + "\n";

            HttpResponse response = new HttpResponse(
                    HttpVersion.HTTP_1_1,
                    new DefaultHttpHeader(),
                    new DefaultHttpBody(body.getBytes(StandardCharsets.UTF_8)),
                    HttpStatus.OK
            );
            response.contentType(ContentType.TEXT_PLAIN);
            return Optional.of(response);
        });

        server.start();
        System.out.println("listening on " + server.listeners().getFirst().boundAddress());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
                System.out.println("stopped");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }));
    }
}
