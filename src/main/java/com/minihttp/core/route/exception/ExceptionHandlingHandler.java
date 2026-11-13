package com.minihttp.core.route.exception;

import com.minihttp.core.handler.Handler;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import lombok.NonNull;

/** 감싼 {@link Handler}가 던진 예외를 {@link ExceptionHandlers}로 응답으로 바꾼다. */
public final class ExceptionHandlingHandler implements Handler {
    private final Handler delegate;
    private final ExceptionHandlers exceptionHandlers;

    private ExceptionHandlingHandler(Handler delegate, ExceptionHandlers exceptionHandlers) {
        this.delegate = delegate;
        this.exceptionHandlers = exceptionHandlers;
    }

    public static ExceptionHandlingHandler wrap(@NonNull Handler delegate, @NonNull ExceptionHandlers exceptionHandlers) {
        return new ExceptionHandlingHandler(delegate, exceptionHandlers);
    }

    @Override
    public HttpResponse handle(HttpRequest request) throws Exception {
        try {
            return this.delegate.handle(request);

        } catch (Exception exception) {
            return this.exceptionHandlers.handle(request, exception);
        }
    }
}
