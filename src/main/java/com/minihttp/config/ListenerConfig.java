package com.minihttp.config;

import com.minihttp.packet.protocol.HttpVersion;
import lombok.NonNull;

import java.net.InetSocketAddress;

/**
 * 리스너 하나(= bind 주소 하나)의 설정. 주소마다 달라질 수 있는 것만 둔다.
 * TLS 같은 설정은 실제로 필요해질 때 여기에 추가한다.
 *
 * @param address bind할 주소. 포트 0이면 OS가 빈 포트를 고른다.
 * @param backlog accept() 되기 전 커널 큐에서 기다릴 수 있는 연결 수.
 *                참고: Step00SocketBasicsExample#openServerSocket, Step04TimeoutExample#connectWithTimeout
 *                (accept 전에도 handshake는 끝나고 연결이 큐에서 기다린다)
 */
public record ListenerConfig(@NonNull ConnectionConfig connectionConfig, @NonNull InetSocketAddress address, int backlog) {
    public static final int DEFAULT_BACKLOG = 50;

    public ListenerConfig {
        if (backlog <= 0) {
            throw new IllegalArgumentException("backlog must be positive: " + backlog);
        }
    }

    public static ListenerConfig of(int port) {
        return new ListenerConfig(
                new ConnectionConfig(32, 100, HttpVersion.HTTP_1_1),
                new InetSocketAddress(port),
                DEFAULT_BACKLOG
        );
    }
}
