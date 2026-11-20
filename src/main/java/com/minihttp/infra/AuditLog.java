package com.minihttp.infra;

import com.minihttp.log.MiniHttpLogging;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AuditLog implements AutoCloseable {
    private static final System.Logger LOGGER = MiniHttpLogging.logger("minihttp.example");

    private final Clock clock;
    private final List<String> entries = new CopyOnWriteArrayList<>();

    public AuditLog(Clock clock) {
        this.clock = clock;
    }

    public void record(String action) {
        this.entries.add(LocalDateTime.now(this.clock) + " " + action);
    }

    public List<String> entries() {
        return List.copyOf(this.entries);
    }

    @Override
    public void close() {
        LOGGER.log(System.Logger.Level.INFO, "AuditLog closed by container with " + this.entries.size() + " entries");
    }
}
