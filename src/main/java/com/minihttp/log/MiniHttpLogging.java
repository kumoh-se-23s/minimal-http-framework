package com.minihttp.log;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * 프레임워크 로거.
 *
 * <p>평소에는 System.Logger 표준 백엔드(JUL, 또는 사용자가 붙인 slf4j 등)로 보낸다.
 * MiniHttp.run()으로 띄우면 uvicorn처럼 콘솔에 한 줄 형식으로 직접 찍는다.
 * JUL의 셧다운 훅이 핸들러를 먼저 지워 버려서 종료 로그가 사라지는 문제를 피하려고 콘솔은 직접 쓴다.
 * 콘솔 레벨은 -Dminihttp.log.level=DEBUG 처럼 바꾼다. (기본 INFO)
 */
public final class MiniHttpLogging {
    public static final String ACCESS = "minihttp.access";
    public static final String SERVER = "minihttp.server";
    public static final String ERROR = "minihttp.error";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static volatile boolean consoleEnabled = false;
    private static volatile System.Logger.Level consoleLevel = System.Logger.Level.INFO;

    private MiniHttpLogging() {
    }

    public static System.Logger logger(String name) {
        return new FrameworkLogger(name);
    }

    /** 사용자가 JUL 설정이나 다른 로깅 백엔드를 붙였으면 그쪽을 존중하고 아무것도 하지 않는다. */
    public static void enableConsole() {
        boolean hasJulConfig = System.getProperty("java.util.logging.config.file") != null
                || System.getProperty("java.util.logging.config.class") != null;
        String loggerFinderPackage = System.LoggerFinder.getLoggerFinder().getClass().getPackageName();
        boolean hasCustomBackend = !loggerFinderPackage.startsWith("jdk.") && !loggerFinderPackage.startsWith("sun.");

        if (hasJulConfig || hasCustomBackend) {
            return;
        }

        consoleLevel = MiniHttpLogging.parseLevel(System.getProperty("minihttp.log.level", "INFO"));
        consoleEnabled = true;
    }

    private static System.Logger.Level parseLevel(String levelName) {
        try {
            return System.Logger.Level.valueOf(levelName.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return System.Logger.Level.INFO;
        }
    }

    private static final class FrameworkLogger implements System.Logger {
        private final String name;
        private final System.Logger backend;

        private FrameworkLogger(String name) {
            this.name = name;
            this.backend = System.getLogger(name);
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public boolean isLoggable(Level level) {
            if (!consoleEnabled) {
                return this.backend.isLoggable(level);
            }
            return level != Level.OFF && level.getSeverity() >= consoleLevel.getSeverity();
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String message, Throwable thrown) {
            if (!consoleEnabled) {
                this.backend.log(level, bundle, message, thrown);
                return;
            }

            if (this.isLoggable(level)) {
                this.writeLine(level, message, thrown);
            }
        }

        @Override
        public void log(Level level, ResourceBundle bundle, String format, Object... parameters) {
            if (!consoleEnabled) {
                this.backend.log(level, bundle, format, parameters);
                return;
            }

            if (this.isLoggable(level)) {
                String message = parameters == null || parameters.length == 0 ? format : MessageFormat.format(format, parameters);
                this.writeLine(level, message, null);
            }
        }

        // 2026-11-13 10:00:00.123 INFO  [minihttp.access] "GET /example/test HTTP/1.1" 200 OK 0.84ms
        private void writeLine(Level level, String message, Throwable thrown) {
            StringBuilder line = new StringBuilder()
                    .append(LocalDateTime.now().format(TIME_FORMAT)).append(' ')
                    .append(String.format("%-5s", FrameworkLogger.levelName(level))).append(' ')
                    .append('[').append(this.name).append("] ")
                    .append(message)
                    .append(System.lineSeparator());

            if (thrown != null) {
                StringWriter stackTrace = new StringWriter();
                thrown.printStackTrace(new PrintWriter(stackTrace));
                line.append(stackTrace);
            }

            // 여러 스레드의 줄이 섞이지 않게 한 번에 쓴다.
            PrintStream console = System.err;
            synchronized (console) {
                console.print(line);
                console.flush();
            }
        }

        private static String levelName(Level level) {
            return level == Level.WARNING ? "WARN" : level.getName();
        }
    }
}
