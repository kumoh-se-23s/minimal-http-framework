package com.minihttp.example.socket;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * 블로킹 소켓의 세 가지 타임아웃.
 * <ol>
 *   <li>accept 타임아웃 — {@code ServerSocket.setSoTimeout}</li>
 *   <li>connect 타임아웃 — {@code Socket.connect(addr, timeout)}</li>
 *   <li>read 타임아웃 — {@code Socket.setSoTimeout}</li>
 * </ol>
 * 타임아웃이 없으면 상대가 아무것도 안 보내는 것만으로 스레드가 영원히 묶인다.
 * 서버에서 유휴 연결을 정리하는 기본 수단이 read 타임아웃이다.
 */
public final class Step04TimeoutExample {
    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            // 1) 아무도 접속하지 않을 때 accept()가 타임아웃되는 모습
            demoAcceptTimeout(server);

            // 2) 타임아웃을 걸고 접속
            try (Socket client = connectWithTimeout(server)) {
                // backlog에 들어와 있던 연결을 이제 꺼낸다.
                try (Socket accepted = server.accept()) {
                    // 3) 서버가 아무것도 안 보내면 클라이언트 read()가 타임아웃된다.
                    demoReadTimeout(client);

                    // 4) 타임아웃 후에도 연결은 멀쩡하다.
                    demoConnectionSurvivesTimeout(client, accepted);
                }
            }
        }
    }

    /**
     * ServerSocket에 SO_TIMEOUT을 걸면 accept()가 그 시간 안에 연결을 못 받을 때 예외를 던진다.
     * accept 루프에서 주기적으로 "종료 플래그"를 확인하고 싶을 때 쓸 수 있다.
     * (보통은 ServerSocket.close()로 accept를 깨우는 쪽이 더 간단하다 → Step08GracefulShutdownExample)
     */
    private static void demoAcceptTimeout(ServerSocket server) throws IOException {
        server.setSoTimeout(300);
        long start = System.nanoTime();
        try {
            server.accept();
        } catch (SocketTimeoutException e) {
            System.out.printf("[1] accept timed out after %dms%n", elapsedMillis(start));
        }
        // 다음 단계를 위해 무한 대기로 되돌린다.
        server.setSoTimeout(0);
    }

    /**
     * {@code new Socket(host, port)}는 connect 타임아웃을 지정할 방법이 없다(OS 기본값, 수십 초~수 분).
     * 인자 없는 생성자로 만들고 connect(addr, millis)를 직접 호출해야 한다.
     *
     * <p>connect 타임아웃은 상대가 패킷을 그냥 버릴 때(방화벽, 없는 호스트) 의미가 있다.
     * 열려 있지 않은 포트라면 상대 OS가 즉시 RST를 보내 ConnectException이 바로 난다.
     *
     * <p>여기서는 accept()를 아직 안 불렀지만 연결은 성공한다.
     * TCP handshake는 커널이 처리하고, 완료된 연결은 backlog 큐에서 accept()를 기다린다.
     */
    private static Socket connectWithTimeout(ServerSocket server) throws IOException {
        Socket client = new Socket();
        client.connect(server.getLocalSocketAddress(), 1_000);
        System.out.println("[2] connected to " + client.getRemoteSocketAddress() + " (before server called accept)");
        return client;
    }

    /**
     * SO_TIMEOUT은 "read() 한 번이 블로킹될 수 있는 최대 시간"이다.
     * 연결 전체의 수명이나 요청 하나를 다 받는 시간이 아니다.
     * 상대가 1바이트씩 299ms마다 보내면 300ms 타임아웃으로는 절대 안 끊긴다(slowloris 공격의 원리).
     */
    private static void demoReadTimeout(Socket client) throws IOException {
        client.setSoTimeout(500);
        long start = System.nanoTime();
        try {
            client.getInputStream().read();
        } catch (SocketTimeoutException e) {
            System.out.printf("[3] read timed out after %dms%n", elapsedMillis(start));
        }
    }

    /** SocketTimeoutException은 연결을 닫지 않는다. 다시 read()하면 이어서 받을 수 있다. */
    private static void demoConnectionSurvivesTimeout(Socket client, Socket accepted) throws IOException {
        accepted.getOutputStream().write('!');
        InputStream in = client.getInputStream();
        System.out.println("[4] after timeout: isClosed=" + client.isClosed() + ", read=" + (char) in.read());
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
