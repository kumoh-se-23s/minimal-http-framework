package com.minihttp.core.listener;

import com.minihttp.config.ListenerConfig;
import lombok.NonNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * bind 주소 하나를 맡는다. ServerSocket을 소유하고 accept 루프를 돌린다.
 *
 * <p>받은 소켓은 {@code onAccept}로 넘기기만 한다. HTTP도, 서버의 공유 자원(풀, 연결 수 제한)도 모른다.
 *
 * <p>참고 예제 (example 모듈 {@code com.minihttp.example.socket}):
 * <ul>
 *   <li>Step00SocketBasicsExample#openServerSocket — ServerSocket 생성자 인자(port, backlog, 주소)</li>
 *   <li>Step05SocketOptionsExample#configureServerSocket — bind 전에 걸어야 하는 옵션</li>
 *   <li>Step06ThreadPerConnectionExample#startAcceptor, #acceptLoop — accept 전용 스레드와 루프</li>
 *   <li>Step08GracefulShutdownExample#stopAccepting — close()로 accept를 깨워 루프 끝내기</li>
 * </ul>
 */
public final class Listener {
    private static final long MIN_BACKOFF_MILLIS = 10;
    private static final long MAX_BACKOFF_MILLIS = 1000;

    private enum State {
        NEW,
        BOUND,
        ACCEPTING,
        CLOSED
    }

    private final ListenerConfig config;
    private final Consumer<Socket> onAccept;

    private volatile ServerSocket listenerSocket;
    private volatile Thread acceptThread;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);


    public Listener(@NonNull ListenerConfig config, @NonNull Consumer<Socket> onAccept) {
        this.config = config;
        this.onAccept = onAccept;
    }

    public synchronized void bind() throws IOException {
        if (this.state.get() != State.NEW) {
            throw new IllegalStateException("Listener cannot be bound in state " + this.state.get());
        }

        ServerSocket serverSocket = new ServerSocket();
        try {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(this.config.address(), this.config.backlog());
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
            this.onAccept.accept(connectionSocket);
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

    public ListenerConfig config() {
        return this.config;
    }

    private boolean stateCAS(State expectedValue, State newValue) {
        return this.state.compareAndSet(expectedValue, newValue);
    }
}
