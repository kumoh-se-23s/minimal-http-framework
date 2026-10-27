package com.minihttp.core;

import com.minihttp.config.ConnectionConfig;
import com.minihttp.config.ListenerConfig;
import com.minihttp.config.ServerConfig;
import com.minihttp.core.connection.Connection;
import com.minihttp.core.connection.ReadBufferFactory;
import com.minihttp.core.handler.Handler;
import com.minihttp.core.listener.ListenerExample;
import com.minihttp.core.register.Register;
import com.minihttp.core.utils.GenericObjectPool;
import com.minihttp.core.utils.Pool;
import lombok.NonNull;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class HttpServerExample {
    private final ServerConfig config;
    private final Handler handler;

    private final List<ListenerExample> listeners;
    private final ExecutorService connections;
    private final Semaphore connectionSlots;
    private final Pool<byte[]> readBuffers;
    private final Register register;

    public HttpServerExample(@NonNull ServerConfig config, @NonNull Handler handler) {
        this.config = config;
        this.handler = handler;

        this.listeners = config.listeners().stream()
                               .map(listenerConfig -> new ListenerExample(
                                       listenerConfig,
                                       socket -> this.onAccept(listenerConfig.connectionConfig(), socket)
                               ))
                               .toList();
        this.connections = Executors.newVirtualThreadPerTaskExecutor();
        this.connectionSlots = new Semaphore(config.maxConnections());
        this.readBuffers = new GenericObjectPool<>(
                List.of(),
                config.maxConnections(),
                () -> new ReadBufferFactory(config.readBufferSize())
        );
        this.register = new ConnectionRegisterExample();
    }

    public void start() throws IOException {
        try {
            for (ListenerExample listener : this.listeners) {
                listener.bind();
            }
        } catch (IOException | RuntimeException exception) {
            this.listeners.forEach(ListenerExample::close);
            throw exception;
        }

        this.listeners.forEach(ListenerExample::startAccepting);
    }

    public void stop() throws InterruptedException {
        this.listeners.forEach(ListenerExample::close);
        for (ListenerExample listener : this.listeners) {
            listener.awaitTermination();
        }

        this.register.closeIdle();
        if (!this.register.awaitEmpty(this.config.shutdownGrace())) {
            this.register.forceCloseAll();
        }

        this.connections.close();
    }

    private void onAccept(ConnectionConfig connectionConfig, Socket socket) {
        if (!this.connectionSlots.tryAcquire()) {
            closeQuietly(socket);
            return;
        }

        Connection connection = new Connection(
                connectionConfig,
                socket,
                this.handler,
                this.readBuffers,
                this.config.idleTimeout()
        );
        this.register.register(connection);

        try {
            this.connections.submit(() -> this.serve(connection));
        } catch (RejectedExecutionException exception) {
            this.register.unregister(connection);
            this.connectionSlots.release();
            closeQuietly(socket);
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

    public List<ListenerExample> listeners() {
        return this.listeners;
    }

    public ServerConfig config() {
        return this.config;
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static final class ConnectionRegisterExample implements Register {
        private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
        private final Object lock = new Object();

        @Override
        public void register(Connection connection) {
            this.connections.add(connection);
        }

        @Override
        public void unregister(Connection connection) {
            this.connections.remove(connection);
            synchronized (this.lock) {
                this.lock.notifyAll();
            }
        }

        @Override
        public int activeCount() {
            return this.connections.size();
        }

        @Override
        public void closeIdle() {
            this.connections.forEach(Connection::closeIfIdle);
        }

        @Override
        public void forceCloseAll() {
            this.connections.forEach(Connection::forceClose);
        }

        @Override
        public boolean awaitEmpty(Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            synchronized (this.lock) {
                while (!this.connections.isEmpty()) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return false;
                    }
                    TimeUnit.NANOSECONDS.timedWait(this.lock, remaining);
                }
                return true;
            }
        }
    }
}
