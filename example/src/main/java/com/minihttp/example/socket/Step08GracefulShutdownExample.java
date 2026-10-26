package com.minihttp.example.socket;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 서버를 "깔끔하게" 끄는 순서.
 * <ol>
 *   <li>ServerSocket을 닫는다 → 블로킹된 accept()가 SocketException으로 깨어나고, 새 연결은 거절된다.</li>
 *   <li>executor.shutdown() → 새 작업은 안 받고, 이미 받은 연결은 계속 처리한다.</li>
 *   <li>awaitTermination으로 정해진 시간만큼 기다린다.</li>
 *   <li>그래도 안 끝나면 shutdownNow()로 인터럽트해서 강제로 끝낸다.</li>
 * </ol>
 * 1번을 빼먹으면 정리하는 도중에도 새 연결이 계속 들어온다.
 * 2~3번을 빼먹으면 응답을 보내던 연결이 중간에 잘린다.
 */
public final class Step08GracefulShutdownExample {
    private static final int HANDLER_DELAY_MILLIS = 500;
    private static final int SHUTDOWN_GRACE_SECONDS = 5;

    public static void main(String[] args) throws Exception {
        ServerSocket server = new ServerSocket(0);
        int port = server.getLocalPort();
        ExecutorService connections = Executors.newVirtualThreadPerTaskExecutor();
        Thread acceptor = Thread.ofPlatform().name("acceptor").start(() -> acceptLoop(server, connections));

        // 0) 느린 요청 하나를 보내 두고, 그 요청이 처리되는 도중에 종료를 시작한다.
        Thread slowClient = Thread.ofVirtual().start(() -> request(port));
        Thread.sleep(100);

        // 1) 새 연결 차단
        stopAccepting(server, acceptor);

        // 2) 정말 새 연결이 거절되는지 확인
        verifyNewConnectionRefused(port);

        // 3) 진행 중이던 연결이 끝날 때까지 기다리기
        drainConnections(connections);

        slowClient.join();
        System.out.println("[main] all connections finished");
    }

    // ─────────────────────────── 종료 단계 ───────────────────────────

    private static void stopAccepting(ServerSocket server, Thread acceptor) throws IOException, InterruptedException {
        System.out.println("[main] closing server socket");
        // 다른 스레드가 accept()에서 블로킹 중이어도 close()는 안전하다. 그 accept()가 예외로 깨어난다.
        // 주의: 커널 backlog에 들어와 있었지만 아직 accept 안 된 연결은 이때 RST로 끊긴다.
        server.close();
        acceptor.join();
    }

    private static void verifyNewConnectionRefused(int port) throws IOException {
        // 아무도 listen하지 않는 포트 → OS가 RST로 응답 → ConnectException
        try (Socket ignored = new Socket("localhost", port)) {
            System.out.println("[main] unexpected: connected after close");
        } catch (ConnectException e) {
            System.out.println("[main] new connection refused: " + e.getMessage());
        }
    }

    private static void drainConnections(ExecutorService connections) throws InterruptedException {
        connections.shutdown();
        if (connections.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
            System.out.println("[main] in-flight connections drained");
            return;
        }
        // 유예 시간이 지났다. 각 핸들러 스레드에 인터럽트를 건다.
        // 블로킹 소켓 read()는 플랫폼 스레드에서는 인터럽트에 반응하지 않지만,
        // 가상 스레드에서는 인터럽트되면 소켓을 닫고 예외로 깨어난다.
        System.out.println("[main] grace period over, forcing shutdown");
        connections.shutdownNow();
    }

    // ───────────────────────────── 서버 ─────────────────────────────

    private static void acceptLoop(ServerSocket server, ExecutorService connections) {
        while (true) {
            try {
                Socket socket = server.accept();
                connections.submit(() -> handleSlowly(socket));
            } catch (IOException e) {
                // close()로 깨어났다. 루프를 끝낸다.
                System.out.println("[acceptor] accept stopped: " + e.getMessage());
                return;
            }
        }
    }

    private static void handleSlowly(Socket socket) {
        try (socket) {
            Thread.sleep(HANDLER_DELAY_MILLIS); // 오래 걸리는 핸들러 흉내
            socket.getOutputStream().write("done".getBytes(UTF_8));
            System.out.println("[handler] response sent");
        } catch (InterruptedException e) {
            // shutdownNow()로 인터럽트됐다. 인터럽트 상태를 복구하고 조용히 끝낸다.
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ─────────────────────────── 클라이언트 ───────────────────────────

    private static void request(int port) {
        try (Socket socket = new Socket("localhost", port)) {
            // 서버가 응답하고 close할 때까지 기다린다.
            System.out.println("[client] got: " + new String(socket.getInputStream().readAllBytes(), UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
