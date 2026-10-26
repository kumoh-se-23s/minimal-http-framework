package com.minihttp.example.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * TCP 연결은 양방향 파이프 두 개다. 한쪽 방향만 닫을 수 있다(half-close).
 *
 * <ul>
 *   <li>{@code shutdownOutput()}: "나는 더 안 보낸다"(FIN 전송). 읽기는 계속 가능.</li>
 *   <li>{@code close()}: 양방향 모두 닫고 소켓 자원을 반납.</li>
 * </ul>
 *
 * 메시지 길이를 미리 알리지 않아도 "보낼 걸 다 보냈다"를 상대에게 알릴 수 있다.
 * HTTP/1.0 시절 응답 바디의 끝을 "연결 종료"로 표시하던 방식과 같은 원리다.
 */
public final class Step02HalfCloseExample {
    public static void main(String[] args) throws Exception {
        // 포트를 인자로 받을 수 있다. tcpdump로 패킷을 볼 때 필터에 쓸 포트가 고정돼야 해서다.
        // (example/scripts/tcpdump-half-close.sh) 인자가 없으면 0 = OS가 빈 포트를 고른다.
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        try (ServerSocket server = new ServerSocket(port)) {
            // 1) 서버: 요청을 EOF까지 다 읽은 뒤에 응답한다.
            Thread serverThread = Thread.ofPlatform()
                    .name("half-close-server")
                    .start(() -> serveOnce(server));

            // 2) 클라이언트: 보내고 → 출력만 닫고 → 응답을 EOF까지 읽는다.
            try (Socket client = new Socket("localhost", server.getLocalPort())) {
                sendAndHalfClose(client, "길이를 미리 알려주지 않은 메시지");
                showWriteAfterShutdownFails(client);
                readResponseUntilEof(client);
            }
            serverThread.join();
        }
    }

    // ─────────────────────────── 클라이언트 ───────────────────────────

    private static void sendAndHalfClose(Socket client, String message) throws IOException {
        client.getOutputStream().write(message.getBytes(UTF_8));
        // 상대 read()가 -1을 받게 된다. 우리 쪽 입력 스트림은 그대로 열려 있다.
        client.shutdownOutput();
        System.out.println("[client] sent and shut down output. isOutputShutdown=" + client.isOutputShutdown()
                + ", isClosed=" + client.isClosed());
    }

    /** 출력을 닫은 뒤에 쓰려고 하면 예외가 난다. */
    private static void showWriteAfterShutdownFails(Socket client) {
        try {
            client.getOutputStream().write('x');
        } catch (IOException e) {
            // getOutputStream() 자체가 SocketException("Socket output is shutdown")을 던진다.
            System.out.println("[client] write after shutdownOutput -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void readResponseUntilEof(Socket client) throws IOException {
        // 서버가 close()하면 FIN이 오고 readAllBytes()가 반환된다.
        byte[] response = client.getInputStream().readAllBytes();
        System.out.println("[client] response: " + new String(response, UTF_8));
    }

    // ───────────────────────────── 서버 ─────────────────────────────

    private static void serveOnce(ServerSocket server) {
        try (Socket socket = server.accept()) {
            // readAllBytes()는 내부적으로 read()가 -1을 줄 때까지 반복한다.
            // 클라이언트가 shutdownOutput()을 안 했다면 여기서 영원히 기다렸을 것이다.
            InputStream in = socket.getInputStream();
            byte[] request = in.readAllBytes();
            System.out.println("[server] got EOF after " + request.length + " bytes");

            OutputStream out = socket.getOutputStream();
            String reply = "received " + request.length + " bytes: " + new String(request, UTF_8);
            out.write(reply.getBytes(UTF_8));
        } catch (SocketException e) {
            System.out.println("[server] socket error: " + e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // close → 클라이언트의 readAllBytes()가 끝난다.
    }
}
