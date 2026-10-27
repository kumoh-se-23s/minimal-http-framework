package com.minihttp.config;

import lombok.NonNull;

import java.net.InetSocketAddress;

/**
 * 리스너 하나(= bind 주소 하나)의 설정. {@link ServerConfig#listenerConfigs()}로 만든다.
 *
 * @param address bind할 주소. 포트 0이면 OS가 빈 포트를 고른다.
 * @param backlog accept() 되기 전 커널 큐에서 기다릴 수 있는 연결 수.
 */
public record ListenerConfig(@NonNull InetSocketAddress address, int backlog) {

    public ListenerConfig {
        if (backlog <= 0) {
            throw new IllegalArgumentException("backlog must be positive: " + backlog);
        }
    }
}
