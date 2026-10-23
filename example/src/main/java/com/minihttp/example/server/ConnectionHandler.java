package com.minihttp.example.server;

import com.minihttp.example.http.HttpMethod;
import com.minihttp.example.http.HttpParseException;
import com.minihttp.example.http.HttpRequest;
import com.minihttp.example.http.HttpRequestParser;
import com.minihttp.example.http.HttpResponse;
import com.minihttp.example.http.HttpResponseWriter;
import com.minihttp.example.http.HttpStatus;
import com.minihttp.example.routing.Router;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * 소켓 하나를 맡아 요청 → 응답을 반복한다 (keep-alive).
 * 클라이언트가 닫거나, Connection: close거나, 일정 시간 조용하면 끝낸다.
 */
final class ConnectionHandler implements Runnable {
    private static final System.Logger LOG = System.getLogger(ConnectionHandler.class.getName());
    private static final int IDLE_TIMEOUT_MILLIS = 30_000;

    private final Socket socket;
    private final Router router;
    private final HttpRequestParser parser = new HttpRequestParser();
    private final HttpResponseWriter writer = new HttpResponseWriter();

    ConnectionHandler(Socket socket, Router router) {
        this.socket = socket;
        this.router = router;
    }

    @Override
    public void run() {
        try (socket) {
            socket.setSoTimeout(IDLE_TIMEOUT_MILLIS);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());

            while (true) {
                HttpRequest request;
                try {
                    request = parser.parse(in);
                } catch (HttpParseException e) {
                    // 어디까지 읽었는지 모르니 이 연결은 더 쓸 수 없다.
                    writer.write(HttpResponse.text(e.status(), e.getMessage()), out, false, true);
                    return;
                }
                if (request == null) {
                    return;
                }

                HttpResponse response = dispatch(request);
                boolean keepAlive = request.keepAlive();
                writer.write(response, out, keepAlive, request.method() != HttpMethod.HEAD);
                if (!keepAlive) {
                    return;
                }
            }
        } catch (SocketTimeoutException e) {
            // 유휴 연결 정리
        } catch (IOException e) {
            LOG.log(System.Logger.Level.DEBUG, "Connection closed: " + e.getMessage());
        }
    }

    private HttpResponse dispatch(HttpRequest request) {
        try {
            return router.route(request);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Handler failed: " + request.method() + " " + request.path(), e);
            return HttpResponse.text(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
        }
    }
}
