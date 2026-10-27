package com.minihttp.core.register;

import com.minihttp.core.connection.Connection;

import java.time.Duration;

/** {@link Register}의 기본 구현. */
public final class ConnectionRegister implements Register {
    // TODO: 저장소는 ConcurrentHashMap.newKeySet() 정도면 충분하다.
    //       awaitEmpty는 lock + Condition, 또는 비었을 때 깨워 주는 장치가 필요하다.
    //  참고: Step08GracefulShutdownExample#drainConnections  (시간 제한을 두고 기다린 뒤, 넘으면 강제 종료)

    @Override
    public void register(Connection connection) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void unregister(Connection connection) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public int activeCount() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void closeIdle() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void forceCloseAll() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public boolean awaitEmpty(Duration timeout) throws InterruptedException {
        throw new UnsupportedOperationException("TODO");
    }
}
