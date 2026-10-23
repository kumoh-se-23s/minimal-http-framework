package com.minihttp.example.server;

import com.minihttp.example.routing.Router;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * accept 전용 스레드 하나가 연결을 받고, 연결마다 가상 스레드에 ConnectionHandler를 맡긴다.
 */
public final class HttpServer implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(HttpServer.class.getName());

    private final int port;
    private final Router router;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private Thread acceptThread;

    /** @param port 0이면 비어 있는 포트를 OS가 고른다. */
    public HttpServer(int port, Router router) {
        this.port = port;
        this.router = router;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        acceptThread = Thread.ofPlatform().name("minihttp-acceptor").start(this::acceptLoop);
    }

    /** 실제로 바인딩된 포트. */
    public int port() {
        return serverSocket.getLocalPort();
    }

    /** 서버가 닫힐 때까지 기다린다. */
    public void join() throws InterruptedException {
        acceptThread.join();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                executor.submit(new ConnectionHandler(socket, router));
            } catch (IOException e) {
                if (serverSocket.isClosed()) {
                    return;
                }
                LOG.log(System.Logger.Level.WARNING, "accept failed", e);
            }
        }
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        executor.shutdownNow();
    }
}
