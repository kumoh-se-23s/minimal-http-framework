package com.minihttp.core.listener;

import com.minihttp.config.ListenerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Timeout(5)
@DisplayName("Listener")
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class ListenerTest {
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    private final List<Listener> listeners = new ArrayList<>();
    private final BlockingQueue<Socket> accepted = new LinkedBlockingQueue<>();

    @AfterEach
    void closeAll() {
        for (Listener listener : this.listeners) {
            try {
                listener.close();
            } catch (RuntimeException ignored) {
                // close()가 아직 구현되지 않았거나 실패해도 다른 테스트에 영향을 주지 않게 한다
            }
        }
        for (Socket socket : this.accepted) {
            closeQuietly(socket);
        }
    }

    private Listener listener(int port, Consumer<Socket> onAccept) {
        Listener listener = Listener.createListener(onAccept, new ListenerConfig(new InetSocketAddress(LOOPBACK, port), 50));
        this.listeners.add(listener);
        return listener;
    }

    private Listener listener(int port) {
        return listener(port, this.accepted::add);
    }

    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0, 1, LOOPBACK)) {
            return probe.getLocalPort();
        }
    }

    private static Socket connect(int port) throws IOException {
        Socket client = new Socket(LOOPBACK, port);
        client.setSoTimeout(2000);
        return client;
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static void assertClosedByServer(Socket client) throws IOException {
        InputStream in = client.getInputStream();
        try {
            assertEquals(-1, in.read(), "서버가 소켓을 닫았다면 EOF(-1)가 와야 한다");
        } catch (SocketTimeoutException e) {
            fail("서버가 소켓을 닫지 않았다: 응답도 EOF도 오지 않음");
        } catch (SocketException e) {
            // RST로 끊긴 경우도 서버가 닫은 것이다
        }
    }

    private static void assertTerminatesSoon(Listener listener) {
        assertTimeoutPreemptively(Duration.ofSeconds(2), listener::awaitTermination,
                "close() 뒤에 accept 스레드가 끝나지 않는다");
    }

    @Nested
    @Order(1)
    @DisplayName("bind()")
    class Bind {

        @Test
        @DisplayName("설정한 포트에서 연결을 받을 준비가 된다")
        void startsListeningOnConfiguredPort() throws Exception {
            int port = freePort();
            listener(port).bind();

            // accept 전이라도 커널이 handshake를 끝내 주므로, bind만 되어 있으면 접속은 성공한다
            assertDoesNotThrow(() -> connect(port).close(), "bind() 이후인데 포트가 열려 있지 않다");
        }

        @Test
        @DisplayName("이미 사용 중인 포트면 BindException을 던진다")
        void failsWithBindExceptionWhenPortIsInUse() throws Exception {
            try (ServerSocket occupant = new ServerSocket(0, 1, LOOPBACK)) {
                Listener listener = listener(occupant.getLocalPort());

                assertThrows(BindException.class, listener::bind);
            }
        }

        @Test
        @DisplayName("실패한 뒤 포트가 비면 다시 bind할 수 있다")
        void canBeRetriedAfterFailure() throws Exception {
            int port;
            Listener listener;
            try (ServerSocket occupant = new ServerSocket(0, 1, LOOPBACK)) {
                port = occupant.getLocalPort();
                listener = listener(port);
                assertThrows(Exception.class, listener::bind);
            }

            assertDoesNotThrow(listener::bind, "실패한 bind 뒤에는 다시 시도할 수 있어야 한다");
            assertDoesNotThrow(() -> connect(port).close());
        }

        @Test
        @DisplayName("두 번 부르면 IllegalStateException을 던진다")
        void twiceIsRejected() throws Exception {
            Listener listener = listener(freePort());
            listener.bind();

            assertThrows(IllegalStateException.class, listener::bind);
        }
    }

    @Nested
    @Order(2)
    @DisplayName("boundAddress()")
    class BoundAddress {

        @Test
        @DisplayName("포트 0으로 열면 OS가 고른 실제 포트를 알려 준다")
        void reportsPortChosenByOsForPortZero() throws Exception {
            Listener listener = listener(0);
            listener.bind();

            int port = listener.boundAddress().getPort();
            assertTrue(port > 0, "포트 0으로 열면 OS가 고른 실제 포트가 나와야 한다");
            assertDoesNotThrow(() -> connect(port).close());
        }

        @Test
        @DisplayName("bind 전에 부르면 IllegalStateException을 던진다")
        void beforeBindIsRejected() {
            Listener listener = listener(0);

            assertThrows(IllegalStateException.class, listener::boundAddress);
        }
    }

    @Nested
    @Order(3)
    @DisplayName("close()")
    class Close {

        @Test
        @DisplayName("닫은 뒤에는 새 연결이 거절된다")
        void stopsListening() throws Exception {
            int port = freePort();
            Listener listener = listener(port);
            listener.bind();

            listener.close();

            assertThrows(ConnectException.class, () -> connect(port).close());
        }

        @Test
        @DisplayName("닫은 포트에 다시 bind할 수 있다")
        void portCanBeBoundAgain() throws Exception {
            int port = freePort();
            Listener first = listener(port);
            first.bind();
            first.close();

            assertDoesNotThrow(listener(port)::bind);
        }

        @Test
        @DisplayName("두 번 불러도 안전하다")
        void canBeCalledTwice() throws Exception {
            Listener listener = listener(0);
            listener.bind();

            listener.close();
            assertDoesNotThrow(listener::close);
        }

        @Test
        @DisplayName("bind 전에 불러도 안전하다")
        void beforeBindIsSafe() {
            assertDoesNotThrow(listener(0)::close);
        }
    }

    @Nested
    @Order(4)
    @DisplayName("startAccepting()")
    class StartAccepting {

        @Test
        @DisplayName("받은 소켓을 onAccept로 넘긴다")
        void passesAcceptedSocketToOnAccept() throws Exception {
            Listener listener = listener(0);
            listener.bind();
            listener.startAccepting();

            try (Socket client = connect(listener.boundAddress().getPort())) {
                Socket socket = accepted.poll(2, TimeUnit.SECONDS);

                assertNotNull(socket, "onAccept가 호출되지 않았다");
                assertEquals(client.getLocalPort(), ((InetSocketAddress) socket.getRemoteSocketAddress()).getPort(),
                        "onAccept로 넘어온 소켓이 이 클라이언트와 연결된 소켓이어야 한다");
            }
        }

        @Test
        @DisplayName("연결을 여러 개 받는다")
        void acceptsMultipleConnections() throws Exception {
            Listener listener = listener(0);
            listener.bind();
            listener.startAccepting();
            int port = listener.boundAddress().getPort();

            try (Socket a = connect(port); Socket b = connect(port); Socket c = connect(port)) {
                for (int i = 0; i < 3; i++) {
                    assertNotNull(accepted.poll(2, TimeUnit.SECONDS), (i + 1) + "번째 연결을 받지 못했다");
                }
            }
        }

        @Test
        @DisplayName("onAccept가 예외를 던지면 그 소켓을 닫고 계속 받는다")
        void keepsAcceptingAfterOnAcceptThrows() throws Exception {
            AtomicInteger calls = new AtomicInteger();
            Listener listener = listener(0, socket -> {
                if (calls.incrementAndGet() == 1) {
                    throw new IllegalStateException("첫 번째 연결에서 일부러 실패");
                }
                accepted.add(socket);
            });
            listener.bind();
            listener.startAccepting();
            int port = listener.boundAddress().getPort();

            try (Socket first = connect(port)) {
                assertClosedByServer(first);
            }
            try (Socket ignored = connect(port)) {
                assertNotNull(accepted.poll(2, TimeUnit.SECONDS), "onAccept가 한 번 실패한 뒤로 연결을 받지 못한다");
            }
        }

        @Test
        @DisplayName("bind 전에 부르면 IllegalStateException을 던진다")
        void beforeBindIsRejected() {
            Listener listener = listener(0);

            assertThrows(IllegalStateException.class, listener::startAccepting);
        }

        @Test
        @DisplayName("두 번 부르면 IllegalStateException을 던진다")
        void twiceIsRejected() throws Exception {
            Listener listener = listener(0);
            listener.bind();
            listener.startAccepting();

            assertThrows(IllegalStateException.class, listener::startAccepting);
        }

        @Test
        @DisplayName("close 뒤에 부르면 IllegalStateException을 던진다")
        void afterCloseIsRejected() throws Exception {
            Listener listener = listener(0);
            listener.bind();
            listener.close();

            assertThrows(IllegalStateException.class, listener::startAccepting);
        }
    }

    @Nested
    @Order(5)
    @DisplayName("awaitTermination()")
    class AwaitTermination {

        @Test
        @DisplayName("close하면 accept에서 멈춰 있던 스레드가 깨어나 끝난다")
        void closeWakesBlockedAcceptAndThreadTerminates() throws Exception {
            Listener listener = listener(0);
            listener.bind();
            listener.startAccepting();

            listener.close();

            assertTerminatesSoon(listener);
        }

        @Test
        @DisplayName("accept를 시작한 적이 없으면 바로 반환한다")
        void withoutStartReturnsImmediately() throws Exception {
            Listener listener = listener(0);
            listener.bind();
            listener.close();

            assertTerminatesSoon(listener);
        }
    }
}
