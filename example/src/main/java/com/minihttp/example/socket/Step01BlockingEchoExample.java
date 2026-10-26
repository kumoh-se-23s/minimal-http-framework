package com.minihttp.example.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 가장 기본적인 블로킹 소켓 흐름.
 *
 * <pre>
 *   서버                          클라이언트
 *   ServerSocket(0)  ── listen
 *   accept()  ◀──── 3-way handshake ──── new Socket(host, port)
 *   read()    ◀──────── "hello socket" ─── write()
 *   write()   ─────────── 같은 바이트 ───▶ readNBytes()
 *   read() == -1  ◀───────── FIN ──────── close()
 *   close()
 * </pre>
 *
 * 서버는 연결을 딱 하나만 받고, 클라이언트가 연결을 닫으면 끝난다.
 */
public final class Step01BlockingEchoExample {
    public static void main(String[] args) throws Exception {
        // 1) 서버 소켓을 연다. 여기까지 하면 OS는 이 포트로 오는 연결을 받기 시작한다.
        try (ServerSocket server = openServerSocket()) {
            int port = server.getLocalPort();
            System.out.println("[main] listening on port " + port);

            // 2) accept()는 연결이 올 때까지 스레드를 멈춘다(블로킹).
            //    같은 스레드에서 클라이언트도 돌려야 하니 서버 쪽은 별도 스레드로 뺀다.
            Thread serverThread = Thread.ofPlatform()
                    .name("echo-server")
                    .start(() -> serveOneConnection(server));

            // 3) 클라이언트로 접속해서 메시지를 보내고 echo를 받는다.
            runClient(port, "hello socket\n");

            // 4) 클라이언트가 연결을 닫았으니 서버 스레드도 곧 끝난다. 끝날 때까지 기다린다.
            serverThread.join();
        }
        // try-with-resources가 ServerSocket을 닫는다 → 포트 반환.
    }

    // ───────────────────────────── 서버 ─────────────────────────────

    /**
     * {@code new ServerSocket(port)}는 C의 socket() + bind() + listen()을 한 번에 한다.
     * <ul>
     *   <li>port 0: OS가 비어 있는 임시 포트를 골라 준다. 테스트에서 포트 충돌을 피할 때 쓴다.</li>
     *   <li>주소를 안 주면 모든 인터페이스(0.0.0.0)에 바인딩된다.
     *       로컬에서만 받으려면 {@code new ServerSocket(0, 50, InetAddress.getLoopbackAddress())}.</li>
     *   <li>backlog 기본값은 50. accept() 되기 전 커널 큐에서 기다릴 수 있는 연결 수다.</li>
     * </ul>
     */
    private static ServerSocket openServerSocket() throws IOException {
        return new ServerSocket(0);
    }

    /** 연결 하나를 받아서 echo하고 닫는다. */
    private static void serveOneConnection(ServerSocket server) {
        // accept()가 돌려주는 Socket은 "이 클라이언트와의 연결 하나"를 나타낸다.
        // ServerSocket은 계속 다른 연결을 받을 수 있고, 연결마다 새 Socket이 생긴다.
        try (Socket socket = server.accept()) {
            System.out.println("[server] accepted " + socket.getRemoteSocketAddress());
            echoUntilEof(socket.getInputStream(), socket.getOutputStream());
            System.out.println("[server] EOF, closing");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // try 블록을 나가면 socket.close() → 클라이언트 쪽에 FIN이 간다.
    }

    /**
     * 읽은 바이트를 그대로 되돌려 보낸다.
     *
     * <p>read(buf)의 반환값이 핵심이다.
     * <ul>
     *   <li>양수 n: 이번에 받은 바이트 수. 버퍼를 꽉 채울 때까지 기다리지 않고 "지금 도착한 만큼"만 돌려준다.
     *       그래서 클라이언트가 한 번에 보낸 데이터가 여러 번에 나눠 읽힐 수도 있다.</li>
     *   <li>-1: 상대가 출력 방향을 닫았다(FIN). 더 읽을 게 없다.</li>
     *   <li>데이터가 없으면 반환하지 않고 기다린다. 0을 돌려주는 일은 없다(버퍼 길이가 0이 아니라면).</li>
     * </ul>
     */
    private static void echoUntilEof(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) != -1) {
            System.out.println("[server] read " + n + " bytes");
            // buf 전체가 아니라 실제로 읽은 0..n 구간만 써야 한다.
            out.write(buf, 0, n);
        }
    }

    // ─────────────────────────── 클라이언트 ───────────────────────────

    private static void runClient(int port, String message) throws IOException {
        // new Socket(host, port)는 생성자 안에서 connect()까지 한다(TCP 3-way handshake).
        try (Socket client = new Socket("localhost", port)) {
            byte[] payload = message.getBytes(UTF_8);

            // 소켓 스트림은 바이트만 다룬다. 문자열은 인코딩을 정해서 바이트로 바꿔 보낸다.
            OutputStream out = client.getOutputStream();
            out.write(payload);
            // Socket의 OutputStream 자체는 버퍼가 없어서 flush가 사실상 no-op이다.
            // 하지만 BufferedOutputStream으로 감싸는 순간 필요해지므로 습관처럼 호출한다.
            out.flush();

            // 보낸 만큼 정확히 읽는다.
            // readAllBytes()를 쓰면 안 된다: 서버는 우리가 닫을 때까지 연결을 안 닫으므로 서로 기다리다 멈춘다.
            // (끝을 알리는 방법은 Step02HalfCloseExample 참고)
            byte[] echoed = client.getInputStream().readNBytes(payload.length);
            System.out.print("[client] received: " + new String(echoed, UTF_8));
        }
        // close() → 서버의 read()가 -1을 받는다.
    }
}
