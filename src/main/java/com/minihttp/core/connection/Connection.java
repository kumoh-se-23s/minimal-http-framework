package com.minihttp.core.connection;

import com.minihttp.config.ConnectionConfig;
import com.minihttp.core.handler.Handler;
import com.minihttp.core.utils.Pool;
import com.minihttp.packet.*;
import com.minihttp.packet.protocol.ContentType;
import com.minihttp.packet.protocol.HttpParseException;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.DefaultHttpBody;
import com.minihttp.packet.structure.DefaultHttpHeader;
import lombok.NonNull;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class Connection implements Runnable {
    private enum State {
        IDLE,
        BUSY,
        CLOSED
    }
    private final Socket socket;
    private final Handler handler;
    private final Pool<byte[]> bufferPool;
    private final Duration idleTimeout;
    private final int maxRequestByConnection;

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    private Connection(@NonNull Socket socket,
                       @NonNull Handler handler,
                       @NonNull Pool<byte[]> bufferPool,
                       @NonNull Duration idleTimeout,
                       int maxRequestByConnection) {

        this.socket = socket;
        this.handler = handler;
        this.bufferPool = bufferPool;
        this.idleTimeout = idleTimeout;
        this.maxRequestByConnection = maxRequestByConnection;
    }

    public static Connection createConnection(@NonNull Socket socket,
                                              @NonNull Handler handler,
                                              @NonNull Pool<byte[]> bufferPool,
                                              @NonNull Duration idleTimeout,
                                              int maxRequestByConnection) {

        return new Connection(socket, handler, bufferPool, idleTimeout, maxRequestByConnection);
    }

    public static Connection createConnection(@NonNull Socket socket,
                                              @NonNull Handler handler,
                                              @NonNull Pool<byte[]> bufferPool,
                                              @NonNull ConnectionConfig config) {
        return new Connection(socket, handler, bufferPool, config.idleTimeout(), config.maxRequestByConnection());
    }

    @Override
    public void run() {
        byte[] readBuffer = null;

        try (this.socket) {
            this.socket.setSoTimeout(Math.toIntExact(this.idleTimeout.toMillis()));
            this.socket.setTcpNoDelay(true);

            OutputStream outputStream = new BufferedOutputStream(this.socket.getOutputStream());

            Optional<byte[]> maybeBuffer = this.bufferPool.tryAcquire();
            if (maybeBuffer.isEmpty()) {
                HttpPacketWriter.write(
                        this.createErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, true),
                        outputStream
                );

                return;
            }
            readBuffer = maybeBuffer.get();

            HttpPacketReader packetReader = new HttpPacketReader(this.socket.getInputStream(), readBuffer);

            try {
                for (int handled = 0; handled < this.maxRequestByConnection; handled++) {
                    if (!packetReader.awaitRequest() || !this.stateCAS(State.IDLE, State.BUSY)) {
                        return;
                    }

                    HttpRequest request = packetReader.readPacket();
                    HttpPacketWriter.write(this.respond(request), outputStream);

                    if (!request.keepAlive() || !this.stateCAS(State.BUSY, State.IDLE)) {
                        return;
                    }
                }
            } catch (HttpParseException httpParseException) {
                HttpPacketWriter.write(
                        this.createErrorResponse(httpParseException.status(), true),
                        outputStream
                );
            }


        } catch (IOException e) {
            // 소켓이 닫히니깐 아무것도 안함

        } finally {
            this.state.set(State.CLOSED);
            if (readBuffer != null) {
                this.bufferPool.release(readBuffer);
            }
        }
    }

    private HttpResponse createErrorResponse(HttpStatus status, boolean withClose) {
        HttpResponse errorResponse = new HttpResponse(
                HttpVersion.HTTP_1_1,
                new DefaultHttpHeader(),
                new DefaultHttpBody(status.reason().getBytes(StandardCharsets.UTF_8)),
                status
        );
        if (withClose) {
            errorResponse.setCloseHeader();
        }
        errorResponse.contentType(ContentType.TEXT_PLAIN);
        return errorResponse;
    }

    private HttpResponse respond(HttpRequest request) {
        HttpResponse response;
        try {
            response = this.handler.handle(request);
            if (response == null) {
                throw new IllegalStateException("Handler returned null response!");
            }

        } catch (Exception e) {
            response = this.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, false);
        }

        if (!request.keepAlive()) {
            response.setCloseHeader();
        }

        return response;
    }

    public void closeIfIdle() {
        if (this.stateCAS(State.IDLE, State.CLOSED)) {
            this.closeSocketQuietly();
        }
    }

    public void forceClose() {
        if (this.state.getAndSet(State.CLOSED) != State.CLOSED) {
            this.closeSocketQuietly();
        }
    }

    public boolean isIdle() {
        return this.state.get() == State.IDLE;
    }

    private void closeSocketQuietly() {
        try {
            this.socket.close();
        } catch (IOException ignored) {
            // 닫는 중이니 무시
        }
    }

    private boolean stateCAS(State expected, State newValue) {
        return this.state.compareAndSet(expected, newValue);
    }
}
