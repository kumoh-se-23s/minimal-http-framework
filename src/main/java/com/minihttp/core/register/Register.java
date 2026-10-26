package com.minihttp.core.register;

import com.minihttp.core.connection.Connection;

import java.time.Duration;

/**
 * 지금 살아 있는 연결 목록. 서버 종료 때 쉬고 있는 연결을 바로 닫고,
 * 처리 중인 연결이 끝나기를 기다리는 데 쓴다 (Step08).
 *
 * <p>여러 연결 스레드가 동시에 등록·해제하므로 구현은 스레드 안전해야 한다.
 *
 * <p>참고 예제: Step08GracefulShutdownExample#drainConnections (example 모듈 {@code com.minihttp.example.socket}).
 * 거기서는 executor.awaitTermination이 "모든 연결이 끝날 때까지 기다리기"를 대신한다.
 * 여기서는 쉬는 연결만 골라 닫아야 해서 연결 목록을 직접 들고 있다.
 */
public interface Register {
    void register(Connection connection);

    void unregister(Connection connection);

    int activeCount();

    /** 다음 요청을 기다리며 쉬고 있는 연결을 모두 닫는다. */
    void closeIdle();

    /** 남은 연결을 상태와 상관없이 모두 닫는다. */
    void forceCloseAll();

    /**
     * 모든 연결이 해제될 때까지 기다린다.
     *
     * @return 시간 안에 모두 끝났으면 true
     */
    boolean awaitEmpty(Duration timeout) throws InterruptedException;
}
