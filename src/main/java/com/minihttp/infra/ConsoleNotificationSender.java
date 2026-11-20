package com.minihttp.infra;

import com.minihttp.log.MiniHttpLogging;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;

public final class ConsoleNotificationSender implements NotificationSender {
    private static final System.Logger LOGGER = MiniHttpLogging.logger("minihttp.example");

    private final Clock clock;
    private final AppSettings appSettings;
    private final ExecutorService notificationExecutor;

    public ConsoleNotificationSender(Clock clock, AppSettings appSettings, ExecutorService notificationExecutor) {
        this.clock = clock;
        this.appSettings = appSettings;
        this.notificationExecutor = notificationExecutor;
    }

    @Override
    public void send(String message) {
        String sentAt = LocalDateTime.now(this.clock).toString();
        this.notificationExecutor.submit(() -> LOGGER.log(
                System.Logger.Level.INFO,
                "[" + this.appSettings.appName() + " notification " + sentAt + "] " + message
        ));
    }
}
