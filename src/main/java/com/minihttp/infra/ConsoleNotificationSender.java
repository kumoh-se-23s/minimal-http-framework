package com.minihttp.infra;

import com.minihttp.log.MiniHttpLogging;

import java.time.Clock;
import java.time.LocalDateTime;

public final class ConsoleNotificationSender implements NotificationSender {
    private static final System.Logger LOGGER = MiniHttpLogging.logger("minihttp.example");

    private final Clock clock;
    private final AppSettings appSettings;

    public ConsoleNotificationSender(Clock clock, AppSettings appSettings) {
        this.clock = clock;
        this.appSettings = appSettings;
    }

    @Override
    public void send(String message) {
        LOGGER.log(System.Logger.Level.INFO,
                   "[" + this.appSettings.appName() + " notification " + LocalDateTime.now(this.clock) + "] " + message);
    }
}
