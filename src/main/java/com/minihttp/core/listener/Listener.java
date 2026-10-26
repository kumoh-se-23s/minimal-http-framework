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
    private enum State {
        NEW,
        BOUND,
        ACCEPTING,
        CLOSED
    }

    private final ListenerConfig config;
    private final Consumer<Socket> onAccept;

    private volatile ServerSocket listenerSocket;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);


    public Listener(@NonNull ListenerConfig config, @NonNull Consumer<Socket> onAccept) {
        this.config = config;
        this.onAccept = onAccept;
    }

    /**
     * ServerSocket을 만들고 bind한다. 아직 accept는 하지 않는다.
     * bind와 accept 시작을 나눠 두면, 서버가 모든 리스너의 bind 성공을 확인한 뒤에 받기 시작할 수 있다.
     *
     * @throws IOException 포트가 이미 사용 중이거나 권한이 없을 때
     */
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

    /** accept 루프를 전용 스레드에서 시작한다. bind() 이후에만 부를 수 있다. */
    public void startAccepting() {
        // TODO: 플랫폼 스레드 하나에서 accept 루프
        //  참고: Step06ThreadPerConnectionExample#startAcceptor  (전용 스레드 띄우기)
        //        Step06ThreadPerConnectionExample#acceptLoop     (닫혀서 난 예외와 진짜 오류 구분)
        //        Step07KeepAliveExample#acceptLoop               (같은 루프, 받은 소켓을 넘기기만)
        //  - accept() → onAccept.accept(socket)
        //  - ServerSocket이 닫혀서 난 예외면 루프 종료
        //  - 그 외 예외(Too many open files 등)는 잠깐 쉬었다가 다시 시도
        throw new UnsupportedOperationException("TODO");
    }

    /** 실제로 bind된 주소. 포트 0으로 열었을 때 OS가 고른 포트를 알 수 있다. */
    public InetSocketAddress boundAddress() {
//        this.listenerSocket.
        // TODO: (InetSocketAddress) serverSocket.getLocalSocketAddress()
        //  참고: Step00SocketBasicsExample#openServerSocket  (포트 0 → getLocalPort로 실제 포트 확인)
        throw new UnsupportedOperationException("TODO");
    }

    public void close() {
        if (this.state.getAndSet(State.CLOSED) != State.CLOSED) {
            try {
                this.listenerSocket.close();
            } catch (IOException e) {
                // 닫을꺼니깐 알빠노 하기
            }
        }
    }

    /** accept 스레드가 끝날 때까지 기다린다. */
    public void awaitTermination() throws InterruptedException {
        // TODO: acceptThread.join()
        //  참고: Step08GracefulShutdownExample#stopAccepting  (server.close() 뒤 acceptor.join())
        throw new UnsupportedOperationException("TODO");
    }

    public ListenerConfig config() {
        return this.config;
    }

    private boolean stateCAS(State expectedValue, State newValue) {
        return this.state.compareAndSet(expectedValue, newValue);
    }
}
