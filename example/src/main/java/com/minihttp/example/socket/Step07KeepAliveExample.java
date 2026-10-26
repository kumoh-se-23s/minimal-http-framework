package com.minihttp.example.socket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * keep-alive: TCP 연결 하나로 요청을 여러 번 주고받는다.
 *
 * <pre>
 *   클라이언트                         서버 (연결 하나를 맡은 스레드)
 *   connect  ───────────────────────▶ accept
 *   GET /a   ───────────────────────▶ 요청 #1 읽기 → 응답 (Connection: keep-alive)
 *            ◀─────────────────────── 200, Content-Length: 21
 *   POST /b  ───────────────────────▶ 요청 #2 읽기 → 응답 (Connection: keep-alive)
 *            ◀─────────────────────── 200
 *   GET /c, Connection: close ──────▶ 요청 #3 읽기 → 응답 (Connection: close)
 *            ◀─────────────────────── 200 → close()
 *   read() == -1
 * </pre>
 *
 * 연결을 재사용하려면 양쪽 모두 "메시지가 어디서 끝나는지" 정확히 알아야 한다.
 * 연결 종료(EOF)를 메시지 끝으로 쓸 수 없으니, 바디 길이는 반드시 Content-Length로 알려야 한다.
 * (Step03StreamFramingExample에서 본 framing이 여기서 필수가 된다.)
 *
 * <p>서버가 연결을 닫는 경우:
 * <ul>
 *   <li>클라이언트가 {@code Connection: close}를 보냄 (HTTP/1.1)</li>
 *   <li>HTTP/1.0인데 {@code Connection: keep-alive}가 없음 (1.0은 기본이 닫기)</li>
 *   <li>요청과 요청 사이에 일정 시간 아무것도 안 옴 (유휴 타임아웃)</li>
 *   <li>요청 형식이 깨짐 → 다음 요청이 어디서 시작하는지 알 수 없으니 400 응답 후 닫기</li>
 *   <li>연결 하나에 너무 많은 요청이 옴 (자원 독점 방지)</li>
 * </ul>
 */
public final class Step07KeepAliveExample {
    private static final int IDLE_TIMEOUT_MILLIS = 500;
    private static final int MAX_REQUESTS_PER_CONNECTION = 100;
    private static final int MAX_LINE_BYTES = 8 * 1024;
    private static final int MAX_BODY_BYTES = 1024 * 1024;

    /** 예제용 최소 요청. 헤더 이름은 대소문자를 구분하지 않는다(RFC 9110 5.1). */
    private record Request(String method, String target, String version, Map<String, String> headers, byte[] body) {
    }

    /** 클라이언트가 받은 응답. */
    private record Response(String statusLine, Map<String, String> headers, byte[] body) {
    }

    /** 메시지 형식이 깨졌다. 이 연결은 더 이상 믿고 쓸 수 없다. */
    private static final class MalformedMessageException extends IOException {
        MalformedMessageException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             ExecutorService connections = Executors.newVirtualThreadPerTaskExecutor()) {
            // 서버 구조는 Step06과 같다: accept 스레드 하나 + 연결마다 가상 스레드.
            Thread acceptor = Thread.ofPlatform().name("acceptor").start(() -> acceptLoop(server, connections));
            int port = server.getLocalPort();

            scenario1SequentialRequests(port);
            scenario2Pipelining(port);
            scenario3Http10(port);
            scenario4IdleTimeout(port);
            scenario5MalformedRequest(port);

            server.close();
            acceptor.join();
        }
    }

    // ─────────────────────────── 시나리오 (클라이언트) ───────────────────────────

    /** 연결 하나로 요청 세 개. 마지막 요청에 Connection: close를 붙여 끝낸다. */
    private static void scenario1SequentialRequests(int port) throws IOException {
        title("1. 연결 하나로 요청 여러 개");
        try (Socket socket = connect(port)) {
            // 클라이언트도 연결당 버퍼를 한 번만 만든다. 이유는 서버의 handle() 주석 참고.
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            send(out, "GET /a HTTP/1.1\r\nHost: localhost\r\n\r\n");
            printResponse(socket, readResponse(in));

            send(out, "POST /b HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nhello");
            printResponse(socket, readResponse(in));

            send(out, "GET /c HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            printResponse(socket, readResponse(in));

            printClosedByServer(in);
        }
    }

    /**
     * 파이프라이닝: 응답을 기다리지 않고 요청 두 개를 한 번에 보낸다.
     * 서버의 첫 read()가 두 요청을 한꺼번에 가져오므로, 첫 요청 뒤에 남은 바이트를 버리면 두 번째 요청이 사라진다.
     * 브라우저는 거의 안 쓰지만 서버는 처리할 수 있어야 하고, 응답은 요청 순서대로 보내야 한다(RFC 9112 9.3.2).
     */
    private static void scenario2Pipelining(int port) throws IOException {
        title("2. 파이프라이닝: 요청 두 개를 write 한 번에");
        try (Socket socket = connect(port)) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            send(socket.getOutputStream(),
                    "GET /p1 HTTP/1.1\r\nHost: localhost\r\n\r\n"
                            + "GET /p2 HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            printResponse(socket, readResponse(in));
            printResponse(socket, readResponse(in));
            printClosedByServer(in);
        }
    }

    /** HTTP/1.0은 기본이 "응답 후 닫기". keep-alive를 원하면 클라이언트가 명시해야 한다. */
    private static void scenario3Http10(int port) throws IOException {
        title("3. HTTP/1.0: 명시해야만 유지");
        try (Socket socket = connect(port)) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            send(out, "GET /old-keep HTTP/1.0\r\nConnection: keep-alive\r\n\r\n");
            printResponse(socket, readResponse(in));

            send(out, "GET /old HTTP/1.0\r\n\r\n");
            printResponse(socket, readResponse(in));

            printClosedByServer(in);
        }
    }

    /** 요청 하나 보내고 가만히 있으면 서버가 유휴 타임아웃으로 연결을 닫는다. */
    private static void scenario4IdleTimeout(int port) throws IOException, InterruptedException {
        title("4. 유휴 타임아웃 (" + IDLE_TIMEOUT_MILLIS + "ms)");
        try (Socket socket = connect(port)) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            send(socket.getOutputStream(), "GET /idle HTTP/1.1\r\nHost: localhost\r\n\r\n");
            printResponse(socket, readResponse(in));

            System.out.println("[client] 아무것도 안 보내고 기다리는 중...");
            Thread.sleep(IDLE_TIMEOUT_MILLIS + 300);
            printClosedByServer(in);
        }
    }

    /** 요청 줄이 깨져 있으면 400을 보내고 닫는다. 다음 요청의 시작 위치를 믿을 수 없기 때문이다. */
    private static void scenario5MalformedRequest(int port) throws IOException {
        title("5. 깨진 요청");
        try (Socket socket = connect(port)) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            send(socket.getOutputStream(), "HELLO\r\n\r\nGET /never HTTP/1.1\r\n\r\n");
            printResponse(socket, readResponse(in));
            printClosedByServer(in);
        }
    }

    // ─────────────────────────── 클라이언트 도우미 ───────────────────────────

    private static Socket connect(int port) throws IOException {
        Socket socket = new Socket("localhost", port);
        // 예제가 버그로 멈추지 않도록 클라이언트에도 read 타임아웃을 건다.
        socket.setSoTimeout(2_000);
        return socket;
    }

    private static void send(OutputStream out, String raw) throws IOException {
        out.write(raw.getBytes(US_ASCII));
        out.flush();
    }

    /**
     * 응답 하나를 읽는다. 클라이언트도 서버와 똑같이 framing을 해야 한다.
     * keep-alive 연결에서 readAllBytes()를 쓰면 서버가 연결을 안 닫으니 영원히(여기선 타임아웃까지) 기다린다.
     */
    private static Response readResponse(InputStream in) throws IOException {
        String statusLine = readLine(in);
        if (statusLine == null) {
            throw new EOFException("server closed before response");
        }
        Map<String, String> headers = readHeaders(in);
        return new Response(statusLine, headers, readBody(in, headers));
    }

    private static void printResponse(Socket socket, Response response) {
        // 로컬 포트가 같으면 같은 TCP 연결을 재사용하고 있다는 뜻이다.
        System.out.printf("[client:%d] %s | Connection: %s | %s%n",
                socket.getLocalPort(),
                response.statusLine(),
                response.headers().get("Connection"),
                new String(response.body(), UTF_8));
    }

    /** 서버가 연결을 닫았으면 read()가 -1을 돌려준다. */
    private static void printClosedByServer(InputStream in) throws IOException {
        System.out.println("[client] server closed connection: " + (in.read() == -1));
    }

    private static void title(String title) {
        System.out.println();
        System.out.println("── " + title);
    }

    // ─────────────────────────────── 서버 ───────────────────────────────

    private static void acceptLoop(ServerSocket server, ExecutorService connections) {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                connections.submit(() -> handle(socket));
            } catch (IOException e) {
                if (!server.isClosed()) {
                    System.err.println("accept failed: " + e);
                }
            }
        }
    }

    /**
     * 연결 하나를 맡아 "요청 읽기 → 응답 쓰기"를 반복한다. 이 루프가 keep-alive의 전부다.
     */
    private static void handle(Socket socket) {
        int peerPort = socket.getPort();
        try (socket) {
            // read 타임아웃 = 유휴 타임아웃. 요청 사이에 이 시간 동안 아무것도 안 오면 SocketTimeoutException.
            socket.setSoTimeout(IDLE_TIMEOUT_MILLIS);

            // ⭐ 버퍼 스트림은 요청마다가 아니라 "연결마다 한 번" 만든다.
            // BufferedInputStream은 소켓에서 넉넉히 읽어 와 내부에 쌓아 둔다.
            // 그 안에는 이번 요청 뒤에 이어진 바이트(파이프라이닝된 다음 요청)가 들어 있을 수 있다.
            // 요청마다 새로 감싸면 이전 버퍼에 남은 바이트를 잃어버린다.
            // Step03에서 직접 다룬 leftover를 여기서는 BufferedInputStream이 들고 있는 셈이다.
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());

            for (int count = 1; ; count++) {
                // 1) 요청 하나 읽기
                Request request;
                try {
                    request = readRequest(in);
                } catch (SocketTimeoutException e) {
                    serverLog(peerPort, "idle for " + IDLE_TIMEOUT_MILLIS + "ms, closing");
                    return;
                } catch (MalformedMessageException e) {
                    serverLog(peerPort, "malformed request (" + e.getMessage() + "), 400 and close");
                    writeResponse(out, "400 Bad Request", e.getMessage(), false);
                    return;
                }
                if (request == null) {
                    // 요청과 요청 사이에서 클라이언트가 깔끔하게 닫았다. 정상 종료.
                    serverLog(peerPort, "client closed connection");
                    return;
                }

                // 2) 이 응답 뒤에 연결을 유지할지 결정
                boolean keepAlive = shouldKeepAlive(request) && count < MAX_REQUESTS_PER_CONNECTION;
                serverLog(peerPort, "request #" + count + ": " + request.method() + " " + request.target()
                        + " " + request.version() + " → keepAlive=" + keepAlive);

                // 3) 응답 쓰기. 결정한 내용을 Connection 헤더로 클라이언트에게도 알린다.
                String body = request.method() + " " + request.target() + " (#" + count + " on this connection)"
                        + (request.body().length > 0 ? ", body=" + new String(request.body(), UTF_8) : "");
                writeResponse(out, "200 OK", body, keepAlive);

                // 4) 유지하지 않으면 루프를 나간다 → try-with-resources가 소켓을 닫는다.
                if (!keepAlive) {
                    serverLog(peerPort, "closing after response");
                    return;
                }
            }
        } catch (IOException e) {
            serverLog(peerPort, "I/O error: " + e.getMessage());
        }
    }

    /**
     * 요청 하나를 읽는다. 연결이 요청 시작 전에 닫혔으면 null.
     * 요청 도중에 닫히면 EOFException — 이건 정상 종료가 아니다.
     */
    private static Request readRequest(InputStream in) throws IOException {
        String requestLine = readLine(in);
        if (requestLine == null) {
            return null;
        }
        // request-line = method SP request-target SP HTTP-version (RFC 9112 3)
        String[] parts = requestLine.split(" ", -1);
        if (parts.length != 3 || !parts[2].startsWith("HTTP/")) {
            throw new MalformedMessageException("bad request line: " + requestLine);
        }
        Map<String, String> headers = readHeaders(in);
        return new Request(parts[0], parts[1], parts[2], headers, readBody(in, headers));
    }

    /**
     * 연결 유지 규칙 (RFC 9112 9.3).
     * <ul>
     *   <li>HTTP/1.1: 기본은 유지. {@code Connection: close}가 있으면 닫는다.</li>
     *   <li>HTTP/1.0: 기본은 닫기. {@code Connection: keep-alive}가 있을 때만 유지한다.</li>
     * </ul>
     * Connection 헤더 값은 쉼표로 구분된 토큰 목록이라 "keep-alive, Upgrade"처럼 올 수도 있다.
     */
    private static boolean shouldKeepAlive(Request request) {
        String connection = request.headers().getOrDefault("Connection", "");
        return switch (request.version()) {
            case "HTTP/1.1" -> !hasToken(connection, "close");
            case "HTTP/1.0" -> hasToken(connection, "keep-alive");
            default -> false;
        };
    }

    /**
     * 응답 하나를 쓴다.
     * <ul>
     *   <li>Content-Length: keep-alive에서는 이게 없으면 클라이언트가 바디 끝을 알 수 없다.</li>
     *   <li>Connection: 서버가 이 응답 뒤에 닫을지 클라이언트에게 알린다.
     *       HTTP/1.1에서 keep-alive는 기본값이라 생략해도 되지만, 예제에서는 보이도록 항상 붙인다.</li>
     *   <li>flush: 응답마다 해야 한다. 안 하면 BufferedOutputStream에 머물러 클라이언트는 응답을 못 받고,
     *       연결은 안 닫히니 아무도 그 사실을 모른 채 서로 기다린다.</li>
     * </ul>
     */
    private static void writeResponse(OutputStream out, String status, String body, boolean keepAlive) throws IOException {
        byte[] bodyBytes = body.getBytes(UTF_8);
        String head = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n"
                + "\r\n";
        out.write(head.getBytes(US_ASCII));
        out.write(bodyBytes);
        out.flush();
    }

    private static void serverLog(int peerPort, String message) {
        System.out.printf("  [server ↔ %d] %s%n", peerPort, message);
    }

    // ─────────────────────── 메시지 읽기 (서버·클라이언트 공용) ───────────────────────

    /**
     * CRLF로 끝나는 한 줄을 읽는다(CRLF는 빼고).
     * <ul>
     *   <li>첫 바이트도 못 읽고 EOF → null (메시지 사이의 정상 종료)</li>
     *   <li>줄 중간에 EOF → EOFException (메시지가 잘림)</li>
     * </ul>
     * BufferedInputStream 위에서 1바이트씩 읽는 건 메모리 접근이라 싸다.
     * 소켓에서 직접 1바이트씩 읽으면 바이트마다 시스템 콜이라 매우 느리다.
     */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                byte[] bytes = line.toByteArray();
                if (bytes.length == 0 || bytes[bytes.length - 1] != '\r') {
                    throw new MalformedMessageException("line must end with CRLF");
                }
                return new String(bytes, 0, bytes.length - 1, US_ASCII);
            }
            line.write(b);
            if (line.size() > MAX_LINE_BYTES) {
                throw new MalformedMessageException("line too long");
            }
        }
        if (line.size() == 0) {
            return null;
        }
        throw new EOFException("connection closed in the middle of a line");
    }

    /** 빈 줄이 나올 때까지 "이름: 값" 줄들을 읽는다. */
    private static Map<String, String> readHeaders(InputStream in) throws IOException {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        String line;
        while (!(line = requireLine(in)).isEmpty()) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new MalformedMessageException("bad header line: " + line);
            }
            headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }
        return headers;
    }

    /**
     * Content-Length만큼 바디를 읽는다. 없으면 바디 없음.
     * 실제 서버라면 Transfer-Encoding: chunked도 처리해야 한다(이 예제에선 생략).
     */
    private static byte[] readBody(InputStream in, Map<String, String> headers) throws IOException {
        String value = headers.get("Content-Length");
        if (value == null) {
            return new byte[0];
        }
        int length;
        try {
            length = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new MalformedMessageException("bad Content-Length: " + value);
        }
        if (length < 0 || length > MAX_BODY_BYTES) {
            throw new MalformedMessageException("Content-Length out of range: " + length);
        }
        // readNBytes는 length를 다 채우거나 EOF가 될 때까지 반복해서 읽는다.
        // 딱 length만큼만 읽으므로 그 뒤의 다음 요청은 버퍼에 그대로 남는다.
        byte[] body = in.readNBytes(length);
        if (body.length < length) {
            throw new EOFException("connection closed in the middle of body");
        }
        return body;
    }

    private static String requireLine(InputStream in) throws IOException {
        String line = readLine(in);
        if (line == null) {
            throw new EOFException("connection closed in the middle of headers");
        }
        return line;
    }

    private static boolean hasToken(String commaSeparated, String token) {
        for (String part : commaSeparated.split(",")) {
            if (part.trim().equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }
}
