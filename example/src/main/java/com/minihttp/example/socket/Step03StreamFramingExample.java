package com.minihttp.example.socket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * TCP는 바이트 스트림이라 "write 한 번 = read 한 번"이 아니다.
 * 하나의 메시지가 여러 read로 쪼개질 수도, 여러 메시지가 한 read에 붙어 올 수도 있다.
 * 그래서 프로토콜이 직접 메시지 경계를 정해야 한다. HTTP/1.1은 이렇게 정한다.
 * <ul>
 *   <li>헤더의 끝: 빈 줄, 즉 {@code \r\n\r\n}</li>
 *   <li>바디의 끝: {@code Content-Length} 바이트만큼 (또는 chunked)</li>
 * </ul>
 *
 * 이 예제의 클라이언트는 POST 요청을 일부러 잘게 쪼개 보낸다.
 * 서버는 (1) 헤더 끝을 찾을 때까지 모으고, (2) 헤더와 같이 딸려 온 바디 앞부분을 챙긴 뒤,
 * (3) 나머지 바디를 Content-Length만큼 더 읽는다.
 */
public final class Step03StreamFramingExample {
    private static final byte[] HEADER_END = {'\r', '\n', '\r', '\n'};
    private static final int MAX_HEADER_BYTES = 8 * 1024;

    /** 헤더 블록과, 같은 read에서 딸려 온 헤더 이후 바이트(바디 앞부분 또는 다음 요청). */
    private record HeaderBlock(byte[] head, byte[] leftover) {
    }

    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread serverThread = Thread.ofPlatform().name("framing-server").start(() -> serveOnce(server));

            try (Socket client = new Socket("localhost", server.getLocalPort())) {
                // 1) 요청을 조각내서 보낸다. 네 번째 조각에는 헤더 끝과 바디 앞부분 "hel"이 같이 들어 있다.
                sendInFragments(client,
                        "POST /ec", "ho HTTP/1.", "1\r\nHost: localhost\r\nContent-Le", "ngth: 5\r\n\r\nhel", "lo");

                // 2) 응답 출력
                System.out.println("[client] response:\n" + new String(client.getInputStream().readAllBytes(), US_ASCII));
            }
            serverThread.join();
        }
    }

    // ─────────────────────────── 클라이언트 ───────────────────────────

    private static void sendInFragments(Socket client, String... fragments) throws IOException, InterruptedException {
        // Nagle을 끄고 조각 사이에 잠깐 쉬어서 조각이 실제로 따로따로 도착하게 만든다.
        // (실제 네트워크에서는 이렇게 일부러 하지 않아도 언제든 쪼개지거나 합쳐질 수 있다.)
        client.setTcpNoDelay(true);
        OutputStream out = client.getOutputStream();
        for (String fragment : fragments) {
            out.write(fragment.getBytes(US_ASCII));
            out.flush();
            Thread.sleep(50);
        }
    }

    // ───────────────────────────── 서버 ─────────────────────────────

    private static void serveOnce(ServerSocket server) {
        try (Socket socket = server.accept()) {
            InputStream in = socket.getInputStream();

            // 1) 헤더 끝까지 모은다.
            HeaderBlock block = readHeaderBlock(in);
            String head = new String(block.head(), US_ASCII);
            System.out.println("[server] header block:\n  " + head.replace("\r\n", "\\r\\n\n  "));
            System.out.println("[server] leftover after header: \"" + new String(block.leftover(), US_ASCII) + "\"");

            // 2) 바디 = 남은 바이트 + 소켓에서 더 읽은 바이트
            int contentLength = parseContentLength(head);
            byte[] body = readBody(in, block.leftover(), contentLength);
            System.out.println("[server] body (" + body.length + " bytes): " + new String(body, US_ASCII));

            // 3) 응답
            writeResponse(socket.getOutputStream(), body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * read()로 받은 조각을 계속 이어 붙이면서 {@code \r\n\r\n}을 찾는다.
     *
     * <p>주의할 점 두 가지.
     * <ul>
     *   <li>경계 문자열 자체가 두 조각에 걸칠 수 있다("...\r\n" + "\r\n").
     *       그래서 이번 조각만 보지 말고 누적된 바이트 전체에서 찾는다.</li>
     *   <li>헤더 끝을 찾은 read가 그 뒤의 바이트까지 가져왔을 수 있다.
     *       이걸 버리면 바디(또는 keep-alive의 다음 요청)가 잘린다. 그래서 leftover로 돌려준다.</li>
     * </ul>
     * 상대가 끝없이 헤더를 보내 메모리를 채우는 걸 막으려고 크기 상한도 둔다.
     */
    private static HeaderBlock readHeaderBlock(InputStream in) throws IOException {
        ByteArrayOutputStream acc = new ByteArrayOutputStream();
        byte[] buf = new byte[256];
        int n;
        while ((n = in.read(buf)) != -1) {
            System.out.println("[server] read() returned " + n + " bytes: " + printable(buf, n));
            acc.write(buf, 0, n);

            byte[] bytes = acc.toByteArray();
            // 이전 조각 끝 3바이트 + 이번 조각 범위만 다시 보면 충분하다.
            int searchFrom = Math.max(0, bytes.length - n - (HEADER_END.length - 1));
            int end = indexOf(bytes, HEADER_END, searchFrom);
            if (end >= 0) {
                int headEnd = end + HEADER_END.length;
                return new HeaderBlock(
                        Arrays.copyOfRange(bytes, 0, headEnd),
                        Arrays.copyOfRange(bytes, headEnd, bytes.length));
            }
            if (bytes.length > MAX_HEADER_BYTES) {
                throw new IOException("header too large");
            }
        }
        throw new IOException("connection closed before header end");
    }

    /**
     * 이미 받은 바디 앞부분(leftover)을 먼저 쓰고, 모자라는 만큼만 소켓에서 더 읽는다.
     * readNBytes는 요청한 개수를 다 받거나 EOF가 될 때까지 read를 반복해 준다.
     */
    private static byte[] readBody(InputStream in, byte[] leftover, int contentLength) throws IOException {
        if (leftover.length >= contentLength) {
            // 바디가 이미 다 왔다. 그 뒤 바이트는 다음 요청 몫이다(이 예제에선 없음).
            return Arrays.copyOf(leftover, contentLength);
        }
        byte[] rest = in.readNBytes(contentLength - leftover.length);
        if (rest.length < contentLength - leftover.length) {
            throw new IOException("connection closed in the middle of body");
        }
        byte[] body = Arrays.copyOf(leftover, contentLength);
        System.arraycopy(rest, 0, body, leftover.length, rest.length);
        return body;
    }

    /** 예제용 아주 단순한 헤더 찾기. 실제 파서는 HttpRequestParser 참고. */
    private static int parseContentLength(String head) {
        for (String line : head.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                return Integer.parseInt(line.substring(colon + 1).trim());
            }
        }
        return 0;
    }

    private static void writeResponse(OutputStream out, byte[] body) throws IOException {
        // 응답에서도 Content-Length로 바디 길이를 알려 준다.
        // 여기서는 Connection: close로 응답 후 연결을 닫으니 클라이언트는 EOF로도 끝을 알 수 있다.
        String head = "HTTP/1.1 200 OK\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        out.write(head.getBytes(US_ASCII));
        out.write(body);
        out.flush();
    }

    // ─────────────────────────── 유틸 ───────────────────────────

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static String printable(byte[] buf, int n) {
        return "\"" + new String(buf, 0, n, US_ASCII).replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }
}
