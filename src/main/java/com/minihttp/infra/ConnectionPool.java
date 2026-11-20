package com.minihttp.infra;

import com.minihttp.log.MiniHttpLogging;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ConnectionPool implements AutoCloseable {
    public record Status(String url, int maxTotal, boolean closed) {
    }

    private static final System.Logger LOGGER = MiniHttpLogging.logger("minihttp.example");

    private final String url;
    private final int maxTotal;
    private final AtomicBoolean closed = new AtomicBoolean();

    private ConnectionPool(String url, int maxTotal) {
        this.url = url;
        this.maxTotal = maxTotal;
    }

    public static ConnectionPool open(String url, int maxTotal) {
        LOGGER.log(System.Logger.Level.INFO, "ConnectionPool opened: " + url + " (maxTotal=" + maxTotal + ")");
        return new ConnectionPool(url, maxTotal);
    }

    public Status status() {
        return new Status(this.url, this.maxTotal, this.closed.get());
    }

    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            LOGGER.log(System.Logger.Level.INFO, "ConnectionPool closed: " + this.url);
        }
    }
}
