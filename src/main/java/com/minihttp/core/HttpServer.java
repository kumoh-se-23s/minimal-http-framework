package com.minihttp.core;

import com.minihttp.config.ConnectionConfig;
import com.minihttp.config.ServerConfig;
import com.minihttp.core.connection.Connection;
import com.minihttp.core.connection.ReadBufferFactory;
import com.minihttp.core.handler.Handler;
import com.minihttp.core.listener.Listener;
import com.minihttp.core.register.ConnectionRegister;
import com.minihttp.core.register.Register;
import com.minihttp.core.response.PlainTextResponse;
import com.minihttp.core.utils.GenericObjectPool;
import com.minihttp.core.utils.Pool;
import com.minihttp.log.MiniHttpLogging;
import com.minihttp.packet.HttpPacketWriter;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.HttpStatus;
import lombok.NonNull;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


public final class HttpServer {
    private static final System.Logger LOGGER = MiniHttpLogging.logger(MiniHttpLogging.SERVER);

    private enum State {
        NEW,
        RUNNING,
        STOPPED
    }

    private final ServerConfig config;
    private final ConnectionConfig connectionConfig;
    private final Handler handler;

    private final List<Listener> listeners;
    private final ExecutorService connections;
    private final Semaphore connectionSlots;
    private final Pool<byte[]> readBuffers;
    private final Register register;

    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    public HttpServer(@NonNull ServerConfig config, @NonNull Handler handler) {
        this.config = config;
        this.connectionConfig = config.connectionConfig();
        this.handler = handler;

        this.listeners = config.listenerConfigs().stream()
                               .map(listenerConfig -> Listener.createListener(this::onAccept, listenerConfig))
                               .toList();
        this.connections = Executors.newVirtualThreadPerTaskExecutor();
        this.connectionSlots = new Semaphore(config.maxConnections());

        this.readBuffers = new GenericObjectPool<>(
                List.of(),
                config.maxConnections(),
                config.maxConnections(),
                () -> new ReadBufferFactory(config.readBufferSize())
        );
        this.register = new ConnectionRegister();
    }

    public void start() throws IOException {
        if (!this.state.compareAndSet(State.NEW, State.RUNNING)) {
            throw new IllegalStateException("Server cannot be started in state " + this.state.get());
        }

        try {
            for (Listener listener : this.listeners) {
                listener.bind();
            }
        } catch (IOException | RuntimeException exception) {
            this.listeners.forEach(Listener::close);
            this.connections.shutdownNow();
            this.state.set(State.STOPPED);
            throw exception;
        }

        this.listeners.forEach(Listener::startAccepting);
        for (Listener listener : this.listeners) {
            LOGGER.log(System.Logger.Level.INFO, "Listening on " + listener.boundAddress());
        }
    }

    public void stop() throws InterruptedException {
        State previousState = this.state.getAndSet(State.STOPPED);
        if (previousState == State.STOPPED) {
            return;
        }

        LOGGER.log(System.Logger.Level.INFO, "Shutting down (" + this.register.activeCount() + " open connections)");
        long startedAt = System.nanoTime();

        this.listeners.forEach(Listener::close);
        for (Listener listener : this.listeners) {
            listener.awaitTermination();
        }

        this.register.closeIdle();
        if (!this.register.awaitEmpty(this.config.shutdownGrace())) {
            LOGGER.log(System.Logger.Level.WARNING,
                       "Shutdown grace " + this.config.shutdownGrace() + " exceeded, force closing "
                               + this.register.activeCount() + " connections");
            this.register.forceCloseAll();
        }

        this.connections.shutdown();
        if (!this.connections.awaitTermination(this.config.shutdownGrace().toMillis(), TimeUnit.MILLISECONDS)) {
            this.connections.shutdownNow();
        }

        LOGGER.log(System.Logger.Level.INFO,
                   "Stopped in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt) + "ms");
    }

    private void onAccept(Socket socket) {
        if (this.state.get() != State.RUNNING) {
            HttpServer.closeQuietly(socket);
            return;
        }

        if (!this.connectionSlots.tryAcquire()) {
            LOGGER.log(System.Logger.Level.WARNING,
                       "Rejected " + socket.getRemoteSocketAddress() + " with 503: maxConnections("
                               + this.config.maxConnections() + ") reached");
            HttpServer.rejectBusy(socket);
            return;
        }

        Connection connection = Connection.createConnection(socket, this.handler, this.readBuffers, this.connectionConfig);
        this.register.register(connection);

        try {
            this.connections.execute(() -> this.serve(connection));

        } catch (RejectedExecutionException exception) {
            this.register.unregister(connection);
            this.connectionSlots.release();
            HttpServer.closeQuietly(socket);
        }
    }

    private void serve(Connection connection) {
        try {
            connection.run();
        } finally {
            this.register.unregister(connection);
            this.connectionSlots.release();
        }
    }

    private static void rejectBusy(Socket socket) {
        try (socket) {
            HttpResponse response = new PlainTextResponse(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE.reason());
            response.setCloseHeader();
            HttpPacketWriter.write(response, socket.getOutputStream());

        } catch (IOException ignored) {
            // 닫는 중이니 무시
        }
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 닫는 중이니 무시
        }
    }

    public List<Listener> listeners() {
        return this.listeners;
    }

    public ServerConfig config() {
        return this.config;
    }

    public int activeConnectionCount() {
        return this.register.activeCount();
    }
}
