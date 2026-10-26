package com.minihttp.example.socket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 여러 클라이언트를 동시에 처리하는 가장 단순한 구조: 연결 하나 = 스레드 하나.
 *
 * <pre>
 *   [acceptor 스레드]  accept() → submit ─┬─▶ [가상 스레드] handle(socket A)
 *                      accept() → submit ─┼─▶ [가상 스레드] handle(socket B)
 *                      accept() → submit ─┴─▶ [가상 스레드] handle(socket C)
 * </pre>
 *
 * 블로킹 read()는 스레드를 붙잡고 있지만, 가상 스레드는 블로킹되는 동안 OS 스레드를 놓아준다.
 * 그래서 연결이 수만 개여도 코드는 블로킹 스타일 그대로 쓸 수 있다.
 *
 * <p>프로토콜은 "한 줄 보내면 한 줄 받는다". 클라이언트가 "bye"를 보내면 연결을 끝낸다.
 */
public final class Step06ThreadPerConnectionExample {
    private static final int CLIENTS = 5;
    private static final int MESSAGES_PER_CLIENT = 2;

    public static void main(String[] args) throws Exception {
        // 연결 처리용 executor. 작업마다 새 가상 스레드를 만든다(풀링하지 않는다).
        try (ServerSocket server = new ServerSocket(0);
             ExecutorService connections = Executors.newVirtualThreadPerTaskExecutor()) {

            // 1) accept 루프 시작
            Thread acceptor = startAcceptor(server, connections);

            // 2) 클라이언트 여러 개를 동시에 돌리고 모두 끝날 때까지 기다린다.
            runClientsConcurrently(server.getLocalPort());

            // 3) 정리: ServerSocket을 닫으면 블로킹된 accept()가 SocketException으로 깨어난다.
            server.close();
            acceptor.join();
        }
        // connections.close()는 제출된 작업이 모두 끝날 때까지 기다린다.
    }

    // ───────────────────────────── 서버 ─────────────────────────────

    /**
     * accept 루프는 플랫폼 스레드 하나로 충분하다(HttpServer와 같은 구조).
     * 하는 일이 "받아서 넘기기"뿐이라 오래 붙잡지 않는다.
     */
    private static Thread startAcceptor(ServerSocket server, ExecutorService connections) {
        return Thread.ofPlatform().name("acceptor").start(() -> acceptLoop(server, connections));
    }

    private static void acceptLoop(ServerSocket server, ExecutorService connections) {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                // 여기서 직접 handle()을 부르면 이 연결이 끝날 때까지 다음 accept를 못 한다.
                connections.submit(() -> handle(socket));
            } catch (IOException e) {
                // 닫아서 깨어난 거라면 정상 종료. 그 외에는 진짜 오류.
                if (!server.isClosed()) {
                    System.err.println("accept failed: " + e);
                }
            }
        }
    }

    /** 연결 하나를 처리한다. 이 메서드는 연결마다 별도 가상 스레드에서 돈다. */
    private static void handle(Socket socket) {
        // socket도 try-with-resources에 넣어야 예외가 나도 확실히 닫힌다.
        try (socket;
             BufferedReader in = reader(socket);
             PrintWriter out = writer(socket)) {
            String line;
            // readLine(): '\n'(또는 "\r\n")이 올 때까지 모은다. 줄바꿈이 메시지 경계 역할을 한다.
            // null이면 상대가 연결을 닫은 것(EOF).
            while ((line = in.readLine()) != null) {
                if (line.equals("bye")) {
                    break;
                }
                out.println("echo(" + Thread.currentThread() + "): " + line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ─────────────────────────── 클라이언트 ───────────────────────────

    private static void runClientsConcurrently(int port) {
        // executor를 try-with-resources로 닫으면 모든 작업이 끝날 때까지 기다려 준다.
        try (ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CLIENTS; i++) {
                int id = i;
                clients.submit(() -> talk(port, id));
            }
        }
    }

    private static void talk(int port, int id) {
        try (Socket socket = new Socket("localhost", port);
             BufferedReader in = reader(socket);
             PrintWriter out = writer(socket)) {
            for (int i = 0; i < MESSAGES_PER_CLIENT; i++) {
                out.println("client-" + id + " msg-" + i);
                // 응답을 받을 때까지 기다린 뒤 다음 메시지를 보낸다(요청/응답 왕복).
                System.out.println(in.readLine());
            }
            out.println("bye");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ─────────────────────────── 공통 ───────────────────────────

    /** 바이트 스트림 → 문자 스트림. 인코딩은 항상 명시한다(기본값에 기대지 않기). */
    private static BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
    }

    /**
     * autoFlush=true: println()마다 flush한다.
     * 이걸 빼면 내용이 PrintWriter 버퍼에만 쌓이고 전송되지 않아 상대가 영원히 기다린다.
     */
    private static PrintWriter writer(Socket socket) throws IOException {
        return new PrintWriter(socket.getOutputStream(), true, UTF_8);
    }
}
