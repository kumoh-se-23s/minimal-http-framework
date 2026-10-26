package com.minihttp.config;

import lombok.NonNull;

import java.time.Duration;
import java.util.List;

/**
 * 서버 전체가 함께 쓰는 설정.
 *
 * @param listeners       열 주소 목록. 하나 이상.
 * @param maxConnections  모든 리스너를 합친 동시 연결 수 상한. 읽기 버퍼 풀의 최대 개수도 이 값과 맞춘다.
 * @param readBufferSize  연결마다 빌리는 읽기 버퍼 크기 (바이트)
 * @param idleTimeout     요청 사이에 이 시간 동안 아무것도 안 오면 연결을 닫는다
 * @param shutdownGrace   종료 시 처리 중인 연결을 기다려 줄 최대 시간
 */
public record ServerConfig(@NonNull List<ListenerConfig> listeners,
                           int maxConnections,
                           int readBufferSize,
                           @NonNull Duration idleTimeout,
                           @NonNull Duration shutdownGrace) {

    public ServerConfig {
        if (listeners.isEmpty()) {
            throw new IllegalArgumentException("at least one listener is required");
        }
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("maxConnections must be positive: " + maxConnections);
        }
        if (readBufferSize <= 0) {
            throw new IllegalArgumentException("readBufferSize must be positive: " + readBufferSize);
        }
        listeners = List.copyOf(listeners);
    }
}
