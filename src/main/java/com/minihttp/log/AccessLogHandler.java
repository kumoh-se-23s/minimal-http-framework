package com.minihttp.log;

import com.minihttp.core.handler.Handler;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.HttpStatus;
import lombok.NonNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 요청마다 한 줄씩 남긴다. ex) "GET /projects?limit=5 HTTP/1.1" 200 OK 1.23ms */
public final class AccessLogHandler implements Handler {
    private static final System.Logger LOGGER = MiniHttpLogging.logger(MiniHttpLogging.ACCESS);

    private final Handler delegate;

    private AccessLogHandler(Handler delegate) {
        this.delegate = delegate;
    }

    public static AccessLogHandler wrap(@NonNull Handler delegate) {
        return new AccessLogHandler(delegate);
    }

    @Override
    public HttpResponse handle(HttpRequest request) throws Exception {
        long startedAt = System.nanoTime();
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        try {
            HttpResponse response = this.delegate.handle(request);
            status = response.status();
            return response;

        } finally {
            // 예외가 그대로 올라가면 Connection이 500으로 응답하므로 500으로 남긴다.
            AccessLogHandler.log(request, status, System.nanoTime() - startedAt);
        }
    }

    private static void log(HttpRequest request, HttpStatus status, long elapsedNanos) {
        System.Logger.Level level = status.code() >= 500 ? System.Logger.Level.WARNING : System.Logger.Level.INFO;

        if (!LOGGER.isLoggable(level)) {
            return;
        }

        LOGGER.log(level, String.format("\"%s %s %s\" %d %s %.2fms",
                request.method(),
                AccessLogHandler.target(request),
                request.version().token(),
                status.code(),
                status.reason(),
                elapsedNanos / 1_000_000.0));
    }

    private static String target(HttpRequest request) {
        Map<String, List<String>> queryParams = request.queryParams();

        if (queryParams.isEmpty()) {
            return request.path();
        }

        String queryString = queryParams.entrySet().stream()
                                        .flatMap(entry -> entry.getValue().stream().map(value -> entry.getKey() + "=" + value))
                                        .collect(Collectors.joining("&"));
        return request.path() + "?" + queryString;
    }
}
