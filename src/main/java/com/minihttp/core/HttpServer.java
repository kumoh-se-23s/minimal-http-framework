package com.minihttp.core;

import com.minihttp.config.ServerConfig;
import com.minihttp.core.handler.Handler;
import com.minihttp.core.listener.Listener;
import com.minihttp.core.register.Register;
import com.minihttp.core.utils.Pool;
import lombok.NonNull;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

/**
 * 메인 인스턴스. 설정된 주소마다 {@link Listener}를 열고, 서버 전체가 함께 쓰는 자원을 소유한다.
 *
 * <pre>
 *   HttpServer
 *    ├─ 공유 자원: connections(executor), connectionSlots, readBuffers, register, handler
 *    ├─ Listener ×N   accept → onAccept(socket)
 *    └─ Connection    연결마다 가상 스레드 하나 (keep-alive 루프)
 * </pre>
 *
 * <p>참고 예제 (example 모듈 {@code com.minihttp.example.socket}):
 * <ul>
 *   <li>Step06ThreadPerConnectionExample — acceptor 스레드 + 연결마다 가상 스레드 구조 전체</li>
 *   <li>Step08GracefulShutdownExample — stop()의 순서</li>
 * </ul>
 */
public final class HttpServer {
    private final ServerConfig config;
    private final Handler handler;

    // TODO: 아래 공유 자원은 생성자에서 config로 만든다.
    //  - listeners:        config.listeners()마다 new Listener(listenerConfig, this::onAccept)
    //  - connections:      Executors.newVirtualThreadPerTaskExecutor()
    //                      참고: Step06ThreadPerConnectionExample#main
    //  - connectionSlots:  new Semaphore(config.maxConnections())
    //  - readBuffers:      GenericObjectPool<byte[]> + ReadBufferFactory(config.readBufferSize())
    //                      최대 개수를 maxConnections와 맞춰야 한다. 지금 GenericObjectPool의 public 생성자로는
    //                      maxSize = size * 2로만 정해져서, maxSize를 받는 생성자가 필요하다.
    //  - register:         new ConnectionRegister()
    private List<Listener> listeners;
    private ExecutorService connections;
    private Semaphore connectionSlots;
    private Pool<byte[]> readBuffers;
    private Register register;

    public HttpServer(@NonNull ServerConfig config, @NonNull Handler handler) {
        this.config = config;
        this.handler = handler;
    }

    /**
     * 모든 리스너를 bind한 뒤 accept를 시작한다.
     * 하나라도 bind에 실패하면 이미 연 리스너도 닫고 예외를 던진다. 일부 포트만 열린 채로 시작하지 않는다.
     */
    public void start() throws IOException {
        // TODO: 1) 모든 listener.bind()  — 실패하면 연 것들 close() 후 예외
        //       2) 모든 listener.startAccepting()
        //  참고: Step06ThreadPerConnectionExample#main  (ServerSocket 열기 → acceptor 시작 순서)
        //  bind를 전부 먼저 끝내는 이유: 한 포트만 열린 채로 요청을 받기 시작하는 상황을 막기 위해.
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * 서버를 종료한다 (Step08 순서).
     * <ol>
     *   <li>모든 리스너 close → 새 연결 차단</li>
     *   <li>register.closeIdle() → 쉬고 있는 keep-alive 연결은 바로 닫기</li>
     *   <li>register.awaitEmpty(shutdownGrace) → 처리 중인 연결 기다리기</li>
     *   <li>시간 초과면 register.forceCloseAll()</li>
     *   <li>connections executor 종료</li>
     * </ol>
     */
    public void stop() throws InterruptedException {
        // TODO: 위 순서대로.
        //  참고: Step08GracefulShutdownExample#main               (전체 순서)
        //        Step08GracefulShutdownExample#stopAccepting      (1단계: listener.close() + awaitTermination())
        //        Step08GracefulShutdownExample#drainConnections   (3~5단계: 기다리고, 시간이 넘으면 강제 종료)
        //  Step08은 executor.awaitTermination으로 기다리지만, 여기서는 쉬는 keep-alive 연결이 있어서
        //  2단계(closeIdle)를 먼저 해야 한다. 안 그러면 쉬는 연결이 idleTimeout까지 종료를 붙잡는다.
        throw new UnsupportedOperationException("TODO");
    }

    /** 리스너가 소켓을 받을 때마다 호출된다. 리스너의 accept 스레드에서 돈다 → 오래 붙잡지 말 것. */
    private void onAccept(Socket socket) {
        // TODO: 1) connectionSlots.tryAcquire() 실패 → 503 보내고 닫기 (또는 그냥 닫기)
        //       2) new Connection(socket, handler, readBuffers, register, idleTimeout)
        //       3) register.register(connection)
        //       4) connections.submit(connection)  — 끝나면 connectionSlots.release()
        //  참고: Step06ThreadPerConnectionExample#acceptLoop  (여기서 직접 처리하지 말고 submit만)
        //        Step07KeepAliveExample#writeResponse          (503 응답을 쓸 때 형식)
        //  주의: submit이 실패하면(종료 중 RejectedExecutionException) 소켓을 닫고 슬롯과 등록을 되돌려야 한다.
        throw new UnsupportedOperationException("TODO");
    }

    /** 리스너 목록. 포트 0으로 띄웠을 때 실제 포트는 {@link Listener#boundAddress()}로 알 수 있다. */
    public List<Listener> listeners() {
        return this.listeners;
    }

    public ServerConfig config() {
        return this.config;
    }
}
