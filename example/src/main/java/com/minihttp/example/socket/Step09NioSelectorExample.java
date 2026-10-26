package com.minihttp.example.socket;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 논블로킹 채널 + Selector: 스레드 하나가 여러 연결의 I/O 이벤트를 돌아가며 처리한다.
 *
 * <pre>
 *   while (true) {
 *       selector.select();                 // 준비된 채널이 생길 때까지 대기
 *       for (key : selectedKeys) {
 *           if (acceptable) → accept 하고 OP_READ로 등록
 *           if (readable)   → 읽어서 echo, 다 못 쓰면 OP_WRITE로 전환
 *           if (writable)   → 남은 걸 마저 쓰고, 다 쓰면 OP_READ로 복귀
 *       }
 *   }
 * </pre>
 *
 * 블로킹 방식은 "기다림"을 스레드가 대신 해 주지만,
 * 논블로킹 방식은 "어디까지 읽었고 어디까지 썼는지"를 직접 들고 다녀야 한다(여기서는 ByteBuffer 하나).
 */
public final class Step09NioSelectorExample {
    private static final int CLIENTS = 3;

    public static void main(String[] args) throws Exception {
        try (Selector selector = Selector.open();
             ServerSocketChannel server = openServerChannel(selector)) {
            int port = ((InetSocketAddress) server.getLocalAddress()).getPort();

            // 1) 이벤트 루프는 스레드 하나. 모든 연결이 이 스레드에서 처리된다.
            Thread loop = Thread.ofPlatform().name("selector-loop").start(() -> eventLoop(selector));

            // 2) 클라이언트는 평범한 블로킹 소켓이어도 된다. 서버의 구현 방식은 상대에게 안 보인다.
            runClientsConcurrently(port);

            // 3) 종료: select()는 스레드가 인터럽트되면 즉시 돌아온다(selector.wakeup()과 같은 효과).
            loop.interrupt();
            loop.join();
        }
    }

    // ─────────────────────────── 서버 준비 ───────────────────────────

    private static ServerSocketChannel openServerChannel(Selector selector) throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress("localhost", 0));
        // Selector에 등록하려면 반드시 논블로킹이어야 한다(아니면 IllegalBlockingModeException).
        server.configureBlocking(false);
        // "새 연결이 들어오면 알려 줘"라고 등록한다.
        server.register(selector, SelectionKey.OP_ACCEPT);
        return server;
    }

    // ─────────────────────────── 이벤트 루프 ───────────────────────────

    private static void eventLoop(Selector selector) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // 등록된 채널 중 하나라도 준비될 때까지 블로킹. 반환값은 새로 준비된 키 개수.
                selector.select();

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    // selectedKeys는 Selector가 비워 주지 않는다. 직접 지우지 않으면
                    // 다음 select 후에도 이미 처리한 키가 또 나온다.
                    keys.remove();
                    dispatch(key);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void dispatch(SelectionKey key) throws IOException {
        // 앞선 처리에서 채널을 닫았으면 키가 무효가 된다. 그 상태에서 isXxx()를 부르면 예외.
        if (!key.isValid()) {
            return;
        }
        if (key.isAcceptable()) {
            handleAccept(key);
        } else if (key.isReadable()) {
            handleRead(key);
        } else if (key.isWritable()) {
            handleWrite(key);
        }
    }

    // ─────────────────────────── 핸들러 ───────────────────────────

    private static void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        // 논블로킹 accept는 받을 연결이 없으면 기다리지 않고 null을 돌려준다.
        SocketChannel channel = server.accept();
        if (channel == null) {
            return;
        }
        System.out.println("[server] accepted " + channel.getRemoteAddress() + " on " + Thread.currentThread().getName());

        channel.configureBlocking(false);
        // 연결별 상태는 attachment로 키에 붙여 둔다. 여기서는 echo할 데이터를 담을 버퍼 하나.
        channel.register(key.selector(), SelectionKey.OP_READ, ByteBuffer.allocate(1024));
    }

    /**
     * ByteBuffer 사용 규칙: 버퍼는 이벤트 사이에 항상 "채우기 모드"로 둔다.
     * <ul>
     *   <li>채우기 모드: position = 다음에 쓸 위치, limit = capacity</li>
     *   <li>flip(): 비우기 모드로. limit = 지금까지 채운 끝, position = 0</li>
     *   <li>compact(): 아직 안 비운 바이트를 맨 앞으로 옮기고 다시 채우기 모드로</li>
     * </ul>
     */
    private static void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buf = (ByteBuffer) key.attachment();

        // 논블로킹 read: 지금 커널 버퍼에 있는 만큼만 읽고 바로 돌아온다. 없으면 0.
        int n = channel.read(buf);
        if (n == -1) {
            // 상대가 출력을 닫았다. 단순화를 위해 바로 닫는다.
            // (아직 못 보낸 echo가 버퍼에 있다면 그것까지 보내고 닫는 게 정석이다.)
            close(key);
            return;
        }
        flushBuffer(key, channel, buf);
    }

    private static void handleWrite(SelectionKey key) throws IOException {
        flushBuffer(key, (SocketChannel) key.channel(), (ByteBuffer) key.attachment());
    }

    /**
     * 버퍼에 모인 데이터를 가능한 만큼 보낸다.
     * 논블로킹 write는 상대 수신 버퍼가 차 있으면 일부만 쓰고 돌아올 수 있다.
     * 다 못 썼으면 OP_WRITE로 바꿔서 "쓸 수 있게 되면 알려 줘"라고 한다.
     * (그동안 OP_READ를 끄면 더 읽어서 버퍼가 넘치는 일도 막아 준다. 일종의 back-pressure.)
     */
    private static void flushBuffer(SelectionKey key, SocketChannel channel, ByteBuffer buf) throws IOException {
        buf.flip();
        channel.write(buf);
        boolean pending = buf.hasRemaining();
        buf.compact();

        // OP_WRITE는 다 쓰고 나면 반드시 꺼야 한다. 켜 두면 소켓은 거의 항상 쓰기 가능이라
        // select()가 계속 즉시 반환되어 CPU를 100% 쓰게 된다.
        key.interestOps(pending ? SelectionKey.OP_WRITE : SelectionKey.OP_READ);
    }

    private static void close(SelectionKey key) throws IOException {
        key.cancel();         // Selector에서 등록 해제
        key.channel().close();
    }

    // ─────────────────────────── 클라이언트 ───────────────────────────

    private static void runClientsConcurrently(int port) {
        try (ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CLIENTS; i++) {
                int id = i;
                clients.submit(() -> talk(port, id));
            }
        }
    }

    private static void talk(int port, int id) {
        try (Socket socket = new Socket("localhost", port)) {
            socket.getOutputStream().write(("hello from client-" + id).getBytes(UTF_8));
            // 다 보냈다고 알려야 서버의 read가 -1을 받고 연결을 닫는다 → 우리의 readAllBytes가 끝난다.
            socket.shutdownOutput();
            System.out.println("[client-" + id + "] " + new String(socket.getInputStream().readAllBytes(), UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
