package com.minihttp.example.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * {@link Socket}과 {@link ServerSocket} API를 하나씩 눌러 보는 예제. Step01보다 먼저 본다.
 *
 * <pre>
 *   1) ServerSocket 열기          new ServerSocket(port, backlog, addr)
 *   2) 연결 전 Socket 상태        new Socket() → isBound / isConnected
 *   3) connect / accept          양쪽 소켓의 주소와 포트 비교
 *   4) 스트림으로 주고받기         getOutputStream().write → getInputStream().readNBytes
 *   5) 스트림을 닫으면 소켓도 닫힌다
 *   6) close 이후 상태와 예외       isClosed / isConnected, read → SocketException
 *   7) 아무도 안 듣는 포트에 접속   ConnectException
 * </pre>
 *
 * 각 단계는 메서드 하나다. 출력을 보면서 메서드를 위에서부터 읽으면 된다.
 */
public final class Step00SocketBasicsExample {
    public static void main(String[] args) throws Exception {
        // 1) 서버 소켓 열기
        try (ServerSocket server = openServerSocket()) {

            // 2) 아직 연결하지 않은 소켓의 상태
            Socket client = createUnconnectedSocket();

            // 3) 연결. accept()는 연결이 올 때까지 블로킹되니 다른 스레드에서 부른다.
            CompletableFuture<Socket> accepting = CompletableFuture.supplyAsync(() -> accept(server));
            client.connect(server.getLocalSocketAddress(), 1_000);
            Socket accepted = accepting.join();
            printEndpoints(client, accepted);

            // 4) 클라이언트 → 서버, 서버 → 클라이언트로 한 번씩 보내기
            exchange(client, accepted);

            // 5) 스트림을 닫으면 소켓 전체가 닫힌다
            closeStreamClosesSocket(accepted);

            // 6) 닫힌 소켓의 상태와, 닫힌 소켓을 쓰면 나는 예외
            client.close();
            showClosedSocket(client);
        }

        // 7) ServerSocket을 닫았으니 이제 그 포트에는 아무도 없다
        showConnectionRefused();
    }

    // ───────────────────────────── 1) 서버 소켓 ─────────────────────────────

    /**
     * 생성자 인자 세 개가 bind에 필요한 전부다.
     * <ul>
     *   <li>port 0: OS가 빈 임시 포트를 고른다. 실제 포트는 {@link ServerSocket#getLocalPort()}로 알아낸다.</li>
     *   <li>backlog: accept() 되기 전에 커널 큐에서 기다릴 수 있는 연결 수.</li>
     *   <li>bindAddr: 어느 인터페이스에서 받을지. null이면 모든 인터페이스(0.0.0.0)다.
     *       여기서는 루프백(127.0.0.1)에만 열어 외부에서 접속할 수 없게 한다.</li>
     * </ul>
     * 생성자가 반환된 시점에 이미 listen 상태다. 이때부터 OS가 이 포트로 오는 연결을 받아 큐에 쌓는다.
     */
    private static ServerSocket openServerSocket() throws IOException {
        ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        System.out.println("[1] server listening on " + server.getLocalSocketAddress()
                + " (isBound=" + server.isBound() + ")");
        return server;
    }

    // ─────────────────────────── 2) 연결 전 상태 ───────────────────────────

    /**
     * 인자 없는 생성자는 연결하지 않은 소켓을 만든다.
     * {@code new Socket(host, port)}는 만들자마자 connect까지 하는데, 그러면
     * connect 전에 옵션을 걸거나 connect 타임아웃을 줄 수 없다 (Step04, Step05).
     */
    private static Socket createUnconnectedSocket() {
        Socket socket = new Socket();
        System.out.println("[2] new Socket(): isBound=" + socket.isBound()
                + ", isConnected=" + socket.isConnected()
                + ", remote=" + socket.getRemoteSocketAddress());   // 연결 전이라 null
        return socket;
    }

    // ─────────────────────────── 3) connect / accept ───────────────────────────

    /** accept()가 돌려주는 Socket은 "이 연결 하나"를 나타내는 새 소켓이다. ServerSocket은 계속 다음 연결을 받을 수 있다. */
    private static Socket accept(ServerSocket server) {
        try {
            return server.accept();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * TCP 연결 하나는 (내 주소, 내 포트, 상대 주소, 상대 포트) 네 개로 구분된다.
     * <ul>
     *   <li>클라이언트의 로컬 포트는 connect 때 OS가 고른 임시 포트다.</li>
     *   <li>서버 쪽 소켓의 로컬 포트는 ServerSocket과 같은 포트다. 연결이 여러 개여도 전부 같은 포트를 쓴다.
     *       그래도 상대 포트가 달라서 OS가 연결을 구분할 수 있다.</li>
     * </ul>
     * 그래서 한쪽의 getLocalPort()는 반대쪽의 getPort()와 같다.
     */
    private static void printEndpoints(Socket client, Socket accepted) {
        System.out.println("[3] connected");
        System.out.println("    client   local=" + client.getLocalSocketAddress()
                + "  remote=" + client.getRemoteSocketAddress());
        System.out.println("    accepted local=" + accepted.getLocalSocketAddress()
                + "  remote=" + accepted.getRemoteSocketAddress());
        System.out.println("    client.getLocalPort() == accepted.getPort() → "
                + (client.getLocalPort() == accepted.getPort()));
    }

    // ─────────────────────────── 4) 스트림 ───────────────────────────

    /**
     * 소켓에는 읽는 스트림과 쓰는 스트림이 하나씩 있다. 둘은 독립적이어서 동시에 써도 된다.
     * <ul>
     *   <li>스트림은 바이트만 다룬다. 문자열은 직접 인코딩·디코딩해야 하고, 문자셋은 항상 명시한다.</li>
     *   <li>write()가 반환됐다고 상대가 받은 건 아니다. OS 송신 버퍼에 들어갔을 뿐이다.</li>
     *   <li>read(buf)는 "지금 와 있는 만큼"만 돌려줄 수 있다. 정확히 n바이트가 필요하면 readNBytes(n).
     *       메시지 경계 문제는 Step03에서 따로 다룬다.</li>
     * </ul>
     */
    private static void exchange(Socket client, Socket accepted) throws IOException {
        byte[] ping = "ping".getBytes(UTF_8);
        client.getOutputStream().write(ping);
        byte[] received = accepted.getInputStream().readNBytes(ping.length);
        System.out.println("[4] server received \"" + new String(received, UTF_8) + "\"");

        byte[] pong = "pong".getBytes(UTF_8);
        accepted.getOutputStream().write(pong);
        received = client.getInputStream().readNBytes(pong.length);
        System.out.println("    client received \"" + new String(received, UTF_8) + "\"");
    }

    // ─────────────────────── 5) 스트림을 닫으면 소켓도 닫힌다 ───────────────────────

    /**
     * 자주 하는 실수. getInputStream()이나 getOutputStream()이 준 스트림을 close()하면
     * 그 방향만 닫히는 게 아니라 소켓 전체가 닫힌다.
     * 한 방향만 닫고 싶으면 shutdownInput() / shutdownOutput()을 쓴다 (Step02).
     *
     * <p>try-with-resources에 스트림을 넣으면 블록이 끝날 때 소켓도 같이 닫힌다는 뜻이기도 하다.
     */
    private static void closeStreamClosesSocket(Socket accepted) throws IOException {
        OutputStream out = accepted.getOutputStream();
        out.close();
        System.out.println("[5] after getOutputStream().close(): accepted.isClosed=" + accepted.isClosed());
    }

    // ─────────────────────────── 6) close 이후 ───────────────────────────

    /**
     * close() 이후의 상태.
     * <ul>
     *   <li>isClosed()는 true가 된다.</li>
     *   <li>isConnected()는 true 그대로다. "한 번이라도 연결된 적이 있는지"라서 close해도 바뀌지 않는다.
     *       상대가 연결을 끊었는지도 알려 주지 않는다. 그건 read()가 -1을 돌려줘야 안다.</li>
     *   <li>닫힌 소켓을 쓰려고 하면 SocketException이 난다. 여기서는 read()까지 가지도 않고
 *       getInputStream()에서 바로 "Socket is closed"로 실패한다.</li>
     * </ul>
     */
    private static void showClosedSocket(Socket client) {
        System.out.println("[6] after close(): isClosed=" + client.isClosed()
                + ", isConnected=" + client.isConnected());
        try {
            InputStream in = client.getInputStream();
            in.read();
        } catch (SocketException e) {
            System.out.println("    read on closed socket → " + e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ─────────────────────────── 7) 연결 거절 ───────────────────────────

    /**
     * 아무도 listen하지 않는 포트에 접속하면 상대 OS가 RST로 응답하고 ConnectException이 바로 난다.
     * 빈 포트를 하나 얻으려고 ServerSocket을 잠깐 열었다가 닫는다.
     */
    private static void showConnectionRefused() throws IOException {
        int port;
        try (ServerSocket temp = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            port = temp.getLocalPort();
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1_000);
            System.out.println("[7] unexpected: connected to " + port);
        } catch (ConnectException e) {
            System.out.println("[7] connect to closed port " + port + " → " + e);
        }
    }
}
