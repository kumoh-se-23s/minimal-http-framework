package com.minihttp.config;

import lombok.NonNull;

import java.time.Duration;

/**
 * 연결 하나에 적용되는 설정. {@link ServerConfig#connectionConfig()}로 만든다.
 *
 * @param maxRequestByConnection 연결 하나에서 처리할 최대 요청 수. 넘으면 keep-alive여도 닫는다.
 * @param idleTimeout            요청 사이에 이 시간 동안 아무것도 안 오면 연결을 닫는다
 */
public record ConnectionConfig(int maxRequestByConnection, @NonNull Duration idleTimeout) {

    public ConnectionConfig {
        if (maxRequestByConnection <= 0) {
            throw new IllegalArgumentException("maxRequestByConnection must be positive: " + maxRequestByConnection);
        }
        if (idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idleTimeout must be positive: " + idleTimeout);
        }
    }
}
