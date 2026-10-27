package com.minihttp.core.listener;

import com.minihttp.config.ListenerConfig;
import lombok.NonNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;


public final class Listener {
    private static final long MIN_BACKOFF_MILLIS = 10;
    private static final long MAX_BACKOFF_MILLIS = 1000;

    private enum State {
        NEW,
        BOUND,
        ACCEPTING,
        CLOSED
    }

    private final InetSocketAddress listenAddress;
    private final int backlog;
    private final Consumer<Socket> socketConsumer;

    private volatile ServerSocket listenerSocket;
    private volatile Thread acceptThread;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    private Listener(@NonNull Consumer<Socket> socketConsumer, @NonNull InetSocketAddress listenAddress, int backlog) {
        this.socketConsumer = socketConsumer;
        this.listenAddress = listenAddress;
        this.backlog = backlog;
    }

    public static Listener createListener(@NonNull Consumer<Socket> socketConsumer,
                                           @NonNull InetSocketAddress listenAddress,
                                           int backlog) {

        return new Listener(socketConsumer, listenAddress, backlog);
    }

    public static Listener createListener(@NonNull Consumer<Socket> socketConsumer, @NonNull ListenerConfig config) {
        return new Listener(socketConsumer, config.address(), config.backlog());
    }

    public synchronized void bind() throws IOException {
        if (this.state.get() != State.NEW) {
            throw new IllegalStateException("Listener cannot be bound in state " + this.state.get());
        }

        ServerSocket serverSocket = new ServerSocket();
        try {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(this.listenAddress, this.backlog);
            this.listenerSocket = serverSocket;

        } catch (IOException exception) {
            serverSocket.close();
            throw exception;
        }

        if (!this.stateCAS(State.NEW, State.BOUND)) {
            serverSocket.close();
            throw new IllegalStateException("Listener was closed while binding");
        }
    }

    public void startAccepting() {
        if (!this.stateCAS(State.BOUND, State.ACCEPTING)) {
            throw new IllegalStateException("Listener cannot start accepting in state " + this.state.get());
        }

        this.acceptThread = Thread.ofPlatform()
                                  .name("listener:[Port=" + this.listenerSocket.getLocalPort() + "]")
                                  .unstarted(this::acceptLoop);
        this.acceptThread.start();

//        try (ServerSocket listenerSocket = this.listenerSocket) {
//            this.acceptThread = Thread.ofPlatform()
//                    .name("listener:[Port=" + this.listenerSocket.getLocalPort() + "]")
//                    .unstarted(this::acceptLoop);
//            this.acceptThread.start();
//
//        } catch (IOException e) {
//            throw new IllegalStateException("Listener is not bound");
//        }
    }

    private void acceptLoop() {
        long backoffMillis = MIN_BACKOFF_MILLIS;

        while (!this.listenerSocket.isClosed() && !Thread.currentThread().isInterrupted()) {
            try {
                this.handOff(this.listenerSocket.accept());
                backoffMillis = MIN_BACKOFF_MILLIS;
            } catch (IOException exception) {
                if (!this.listenerSocket.isClosed()) {
                    backoffMillis = this.pause(backoffMillis);
                }
            }
        }
    }

    private void handOff(Socket connectionSocket) throws IOException {
        // 전용 소켓을 커넥션 컨슈머에게 전달.
        try {
            this.socketConsumer.accept(connectionSocket);
        } catch (RuntimeException exception) {
            connectionSocket.close();
        }
    }

    private long pause(long backoffMillis) {
        // 연결 실패시 Thread를 잠시 멈추고 backoff 만큼 대기 후 재시도를 위해 다시 제어권 전달.
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return Math.min(backoffMillis * 2, MAX_BACKOFF_MILLIS);
    }

    public InetSocketAddress boundAddress() {
        ServerSocket listenerSocket = this.listenerSocket;
        if (listenerSocket == null) {
            throw new IllegalStateException("Listener is not bound");
        }
        return (InetSocketAddress) listenerSocket.getLocalSocketAddress();
    }

    public void close() {
        if (this.state.getAndSet(State.CLOSED) != State.CLOSED && this.listenerSocket != null) {
            try {
                this.listenerSocket.close();
            } catch (IOException e) {
                // 닫을꺼니깐 알빠노 하기
            }
        }
    }

    public void awaitTermination() throws InterruptedException {
        if (this.acceptThread != null) {
            this.acceptThread.join();
        }
    }

    private boolean stateCAS(State expectedValue, State newValue) {
        return this.state.compareAndSet(expectedValue, newValue);
    }
}
