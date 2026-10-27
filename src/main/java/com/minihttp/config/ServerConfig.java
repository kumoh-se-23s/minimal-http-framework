package com.minihttp.config;

import lombok.NonNull;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 서버 설정의 원본. {@link #builder()}로 만들고, 서버는 여기서 {@link ListenerConfig}와
 * {@link ConnectionConfig}를 잘라서 각 컴포넌트에 넘긴다.
 *
 * @param addresses              열 주소 목록. 하나 이상.
 * @param backlog                리스너마다 accept() 전에 커널 큐에서 기다릴 수 있는 연결 수
 * @param maxConnections         모든 리스너를 합친 동시 연결 수 상한. 읽기 버퍼 풀의 최대 개수도 이 값과 맞춘다.
 * @param readBufferSize         연결마다 빌리는 읽기 버퍼 크기 (바이트)
 * @param maxRequestByConnection 연결 하나에서 처리할 최대 요청 수
 * @param idleTimeout            요청 사이에 이 시간 동안 아무것도 안 오면 연결을 닫는다
 * @param shutdownGrace          종료 시 처리 중인 연결을 기다려 줄 최대 시간
 */
public record ServerConfig(@NonNull List<InetSocketAddress> addresses,
                           int backlog,
                           int maxConnections,
                           int readBufferSize,
                           int maxRequestByConnection,
                           @NonNull Duration idleTimeout,
                           @NonNull Duration shutdownGrace) {

    public ServerConfig {
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("at least one address is required");
        }
        if (backlog <= 0) {
            throw new IllegalArgumentException("backlog must be positive: " + backlog);
        }
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("maxConnections must be positive: " + maxConnections);
        }
        if (readBufferSize <= 0) {
            throw new IllegalArgumentException("readBufferSize must be positive: " + readBufferSize);
        }
        if (maxRequestByConnection <= 0) {
            throw new IllegalArgumentException("maxRequestByConnection must be positive: " + maxRequestByConnection);
        }
        if (idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idleTimeout must be positive: " + idleTimeout);
        }
        if (shutdownGrace.isNegative()) {
            throw new IllegalArgumentException("shutdownGrace must not be negative: " + shutdownGrace);
        }
        addresses = List.copyOf(addresses);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 주소마다 리스너 설정 하나. */
    public List<ListenerConfig> listenerConfigs() {
        return this.addresses.stream()
                             .map(address -> new ListenerConfig(address, this.backlog))
                             .toList();
    }

    /** 모든 연결이 함께 쓰는 연결 설정. */
    public ConnectionConfig connectionConfig() {
        return new ConnectionConfig(this.maxRequestByConnection, this.idleTimeout);
    }

    /** 값마다 기본값이 있어서, 바꿀 것만 지정하면 된다. 주소는 {@link #listen}으로 하나 이상 넣어야 한다. */
    public static final class Builder {
        private final List<InetSocketAddress> addresses = new ArrayList<>();
        private int backlog = 50;
        private int maxConnections = 256;
        private int readBufferSize = 8 * 1024;
        private int maxRequestByConnection = 32;
        private Duration idleTimeout = Duration.ofSeconds(30);
        private Duration shutdownGrace = Duration.ofSeconds(5);

        private Builder() {
        }

        public Builder listen(int port) {
            return this.listen(new InetSocketAddress(port));
        }

        public Builder listen(@NonNull InetSocketAddress address) {
            this.addresses.add(address);
            return this;
        }

        public Builder backlog(int backlog) {
            this.backlog = backlog;
            return this;
        }

        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public Builder readBufferSize(int readBufferSize) {
            this.readBufferSize = readBufferSize;
            return this;
        }

        public Builder maxRequestByConnection(int maxRequestByConnection) {
            this.maxRequestByConnection = maxRequestByConnection;
            return this;
        }

        public Builder idleTimeout(@NonNull Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        public Builder shutdownGrace(@NonNull Duration shutdownGrace) {
            this.shutdownGrace = shutdownGrace;
            return this;
        }

        public ServerConfig build() {
            return new ServerConfig(
                    this.addresses,
                    this.backlog,
                    this.maxConnections,
                    this.readBufferSize,
                    this.maxRequestByConnection,
                    this.idleTimeout,
                    this.shutdownGrace
            );
        }
    }
}
