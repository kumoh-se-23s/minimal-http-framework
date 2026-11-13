package com.minihttp.core.route.exception;

import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;

/**
 * 예외 하나를 응답으로 바꾼다. FastAPI의 {@code @app.exception_handler(SomeError)}로 등록하는 함수.
 *
 * <pre>
 *   app.exceptionHandler(BusinessException.class,
 *           (request, exception) -&gt; JsonResponse.fromJsonText(HttpStatus.BAD_REQUEST, exception.toJsonString()));
 * </pre>
 */
@FunctionalInterface
public interface ExceptionHandler<T extends Throwable> {
    HttpResponse handle(HttpRequest request, T exception) throws Exception;
}
