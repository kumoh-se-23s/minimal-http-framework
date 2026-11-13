package com.minihttp.core.register;

import com.minihttp.core.connection.Connection;
import lombok.NonNull;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class ConnectionRegister implements Register {
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();

    // awaitEmpty()가 "비었다"는 신호를 기다리는 데 쓴다.
    private final ReentrantLock emptyLock = new ReentrantLock();
    private final Condition becameEmpty = this.emptyLock.newCondition();

    @Override
    public void register(@NonNull Connection connection) {
        this.connections.add(connection);
    }

    @Override
    public void unregister(@NonNull Connection connection) {
        if (!this.connections.remove(connection) || !this.connections.isEmpty()) {
            return;
        }

        this.emptyLock.lock();
        try {
            this.becameEmpty.signalAll();
        } finally {
            this.emptyLock.unlock();
        }
    }

    @Override
    public int activeCount() {
        return this.connections.size();
    }

    @Override
    public void closeIdle() {
        for (Connection connection : this.connections) {
            connection.closeIfIdle();
        }
    }

    @Override
    public void forceCloseAll() {
        for (Connection connection : this.connections) {
            connection.forceClose();
        }
    }

    @Override
    public boolean awaitEmpty(@NonNull Duration timeout) throws InterruptedException {
        long remainingNanos = timeout.toNanos();

        this.emptyLock.lock();
        try {
            // unregister()는 집합에서 뺀 뒤 락을 잡고 신호를 보내므로, 락 안에서 isEmpty()를 다시 보면 신호를 놓치지 않는다.
            while (!this.connections.isEmpty()) {
                if (remainingNanos <= 0) {
                    return false;
                }
                remainingNanos = this.becameEmpty.awaitNanos(remainingNanos);
            }
            return true;

        } finally {
            this.emptyLock.unlock();
        }
    }
}
