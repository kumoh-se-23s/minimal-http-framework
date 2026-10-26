package com.minihttp.example.socket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.StandardSocketOptions;

/**
 * 서버 코어에서 자주 만지게 될 소켓 옵션들을 설정하고 값을 출력한다.
 * 타임아웃 관련 옵션은 {@link Step04TimeoutExample}에서 따로 다룬다.
 *
 * <p>옵션마다 "언제 설정해야 의미가 있는지"가 다르다는 점에 주의.
 * bind 전, connect 전에 설정해야 하는 것들이 있다.
 */
public final class Step05SocketOptionsExample {
    public static void main(String[] args) throws Exception {
        // 1) 서버 소켓: 옵션을 먼저 설정하고 나서 bind한다.
        //    그래서 포트를 받는 생성자 대신 인자 없는 생성자 + bind()를 쓴다.
        try (ServerSocket server = new ServerSocket()) {
            configureServerSocket(server);
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 100);
            printServerOptions(server);

            // 2) 클라이언트 소켓: 역시 connect 전에 설정해야 하는 옵션이 있다.
            try (Socket client = new Socket()) {
                configureClientBeforeConnect(client);
                client.connect(server.getLocalSocketAddress());
                configureClientAfterConnect(client);

                // 3) 서버가 accept한 소켓에도 옵션은 따로 걸어야 한다.
                //    (ServerSocket의 일부 옵션은 상속되지만, 연결 소켓 단위로 설정하는 게 명확하다.)
                try (Socket accepted = server.accept()) {
                    accepted.setTcpNoDelay(true);
                    printSocketOptions("client  ", client);
                    printSocketOptions("accepted", accepted);
                }
            }
        }
    }

    // ───────────────────────────── 서버 ─────────────────────────────

    private static void configureServerSocket(ServerSocket server) throws IOException {
        // SO_REUSEADDR: 서버를 재시작했을 때 이전 연결이 TIME_WAIT 상태로 남아 있어도
        // 같은 포트에 바로 bind할 수 있게 한다. 반드시 bind 전에 설정.
        server.setReuseAddress(true);

        // SO_RCVBUF: accept된 소켓의 수신 버퍼 크기 힌트.
        // 64KB를 넘기려면 TCP window scaling 협상 때문에 bind(=listen) 전에 정해야 한다.
        server.setReceiveBufferSize(64 * 1024);
    }

    private static void printServerOptions(ServerSocket server) throws IOException {
        System.out.println("── server socket");
        System.out.println("  bound to       = " + server.getLocalSocketAddress());
        System.out.println("  SO_REUSEADDR   = " + server.getReuseAddress());
        System.out.println("  SO_RCVBUF      = " + server.getReceiveBufferSize());
        // getOption/supportedOptions: 옵션을 공통 API로 다루는 방법(NIO 채널과 같은 방식).
        System.out.println("  supported      = " + server.supportedOptions());
    }

    // ─────────────────────────── 클라이언트 ───────────────────────────

    private static void configureClientBeforeConnect(Socket client) throws IOException {
        // 송수신 버퍼 크기는 OS에 주는 "힌트"다. OS가 최소/최대값에 맞춰 조정할 수 있다.
        client.setReceiveBufferSize(32 * 1024);
        client.setSendBufferSize(32 * 1024);
    }

    private static void configureClientAfterConnect(Socket client) throws IOException {
        // TCP_NODELAY: Nagle 알고리즘을 끈다.
        // Nagle은 작은 패킷을 모아서 보내 네트워크 효율을 높이지만, 그만큼 지연이 생긴다.
        // 요청/응답형 프로토콜(HTTP)은 응답을 빨리 내보내는 게 중요해서 보통 끈다.
        client.setTcpNoDelay(true);

        // SO_KEEPALIVE: 오랫동안 조용한 연결에 OS가 탐침 패킷을 보내 상대가 살아있는지 확인한다.
        // 기본 주기가 2시간 수준이라 HTTP keep-alive(애플리케이션 수준의 연결 재사용)와는 전혀 다른 기능이다.
        client.setKeepAlive(true);

        // SO_LINGER: close() 때 아직 못 보낸 데이터를 어떻게 할지.
        //   -1(기본): close()는 바로 반환하고 OS가 백그라운드로 마저 보낸다.
        //   0       : 즉시 RST를 보내 연결을 강제로 끊는다(남은 데이터 버림).
        //   n초     : 다 보낼 때까지 최대 n초 동안 close()가 블로킹된다.
        // 여기서는 기본값을 그대로 둔다.
    }

    private static void printSocketOptions(String label, Socket socket) throws IOException {
        System.out.println("── " + label);
        System.out.println("  local / remote = " + socket.getLocalSocketAddress() + " / " + socket.getRemoteSocketAddress());
        System.out.println("  TCP_NODELAY    = " + socket.getOption(StandardSocketOptions.TCP_NODELAY));
        System.out.println("  SO_KEEPALIVE   = " + socket.getOption(StandardSocketOptions.SO_KEEPALIVE));
        System.out.println("  SO_RCVBUF      = " + socket.getReceiveBufferSize());
        System.out.println("  SO_SNDBUF      = " + socket.getSendBufferSize());
        System.out.println("  SO_LINGER      = " + socket.getSoLinger());
        System.out.println("  SO_TIMEOUT     = " + socket.getSoTimeout() + " (0 = 무한 대기)");
    }
}
