package com.minihttp.core.listener;

import com.minihttp.config.ListenerConfig;
import lombok.NonNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * {@link Listener}의 완성 예시. 같은 API를 가지고 있어 ListenerTest의 대상만 바꾸면 그대로 검증할 수 있다.
 *
 * <p>상태 전이: NEW → BOUND → ACCEPTING → CLOSED (close()는 어느 상태에서든 CLOSED로)
 */
public final class ListenerExample {
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

    public ListenerExample(@NonNull ListenerConfig config, @NonNull Consumer<Socket> onAccept) {
        this.config = config;
        this.onAccept = onAccept;
    }

    /**
     * get()으로 먼저 거르는 건 소켓을 만들기 전에 빨리 실패하기 위해서고,
     * 진짜 상태 확정은 마지막 CAS가 한다. 그 사이에 close()가 끼어들 수 있기 때문이다.
     */
    public synchronized void bind() throws IOException {
        if (this.state.get() != State.NEW) {
            throw new IllegalStateException("Listener cannot be bound in state " + this.state.get());
        }

        ServerSocket serverSocket = new ServerSocket();
        try {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(this.config.address(), this.config.backlog());
        } catch (IOException exception) {
            serverSocket.close();
            throw exception;
        }

        // 필드를 먼저 채워야 CAS 이후에 들어온 close()가 이 소켓을 닫을 수 있다
        this.listenerSocket = serverSocket;
        if (!this.stateCAS(State.NEW, State.BOUND)) {
            serverSocket.close();
            throw new IllegalStateException("Listener was closed while binding");
        }
    }

    /**
     * CAS를 먼저 하고 스레드를 시작한다. 그 사이에 close()가 오면
     * 스레드는 닫힌 소켓에서 accept하다 예외를 받고 스스로 끝난다.
     */
    public void startAccepting() {
        if (!this.stateCAS(State.BOUND, State.ACCEPTING)) {
            throw new IllegalStateException("Listener cannot start accepting in state " + this.state.get());
        }

        ServerSocket serverSocket = this.listenerSocket;
        // start 전에 필드에 넣어 둬야 awaitTermination()이 항상 이 스레드를 볼 수 있다
        Thread thread = Thread.ofPlatform()
                .name("listener-" + serverSocket.getLocalPort())
                .unstarted(() -> this.acceptLoop(serverSocket));
        this.acceptThread = thread;
        thread.start();
    }

    private void acceptLoop(ServerSocket serverSocket) {
        long backoffMillis = MIN_BACKOFF_MILLIS;

        while (!serverSocket.isClosed()) {
            Socket socket;
            try {
                socket = serverSocket.accept();
                backoffMillis = MIN_BACKOFF_MILLIS;
            } catch (IOException exception) {
                // close()가 깨운 것인지, 일시적인 오류(Too many open files 등)인지는 예외 타입이 아니라 소켓 상태로 구분한다
                if (serverSocket.isClosed()) {
                    return;
                }
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoffMillis = Math.min(backoffMillis * 2, MAX_BACKOFF_MILLIS);
                continue;
            }

            try {
                this.onAccept.accept(socket);
            } catch (RuntimeException exception) {
                // 넘겨받은 쪽이 실패했으면 이 소켓은 책임질 주인이 없다
                closeQuietly(socket);
            }
        }
    }

    /** 실제로 bind된 주소. 포트 0으로 열었을 때 OS가 고른 포트를 알 수 있다. */
    public InetSocketAddress boundAddress() {
        ServerSocket serverSocket = this.listenerSocket;
        if (serverSocket == null) {
            throw new IllegalStateException("Listener is not bound");
        }
        return (InetSocketAddress) serverSocket.getLocalSocketAddress();
    }

    /** 어느 상태에서 불러도 안전하다. 블로킹된 accept()는 SocketException으로 깨어난다. */
    public void close() {
        if (this.state.getAndSet(State.CLOSED) == State.CLOSED) {
            return;
        }

        ServerSocket serverSocket = this.listenerSocket;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // 닫는 중의 실패는 더 할 수 있는 게 없다
            }
        }
    }

    /** accept 스레드가 끝날 때까지 기다린다. 시작한 적이 없으면 바로 반환한다. */
    public void awaitTermination() throws InterruptedException {
        Thread thread = this.acceptThread;
        if (thread != null) {
            thread.join();
        }
    }

    public ListenerConfig config() {
        return this.config;
    }

    private boolean stateCAS(State expectedValue, State newValue) {
        return this.state.compareAndSet(expectedValue, newValue);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 닫는 중의 실패는 더 할 수 있는 게 없다
        }
    }
}
