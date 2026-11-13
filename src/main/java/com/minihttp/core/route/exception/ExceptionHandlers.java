package com.minihttp.core.route.exception;

import com.minihttp.core.response.JsonResponse;
import com.minihttp.core.response.PlainTextResponse;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.HttpStatus;
import lombok.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 예외 타입 → {@link ExceptionHandler} 표. 예외가 나면 그 클래스부터 상위 클래스로 올라가며 가장 가까운 핸들러를 고른다.
 *
 * <p>기본으로 등록된 핸들러 (FastAPI와 같은 응답 형식, 같은 타입으로 다시 등록하면 덮어쓴다):
 * <pre>
 *   HttpStatusException         → 그 상태 코드 + {"detail": ...} + 예외에 담긴 헤더
 *   RequestValidationException  → 422 {"detail":[{"type":..., "loc":[...], "msg":...}, ...]}
 *   Exception                   → 500 "Internal Server Error"  (스택 트레이스는 로그로)
 * </pre>
 */
public final class ExceptionHandlers {
    private static final System.Logger LOGGER = System.getLogger(ExceptionHandlers.class.getName());

    private final Map<Class<? extends Throwable>, ExceptionHandler<? extends Throwable>> handlers = new ConcurrentHashMap<>();

    private ExceptionHandlers() {
    }

    public static ExceptionHandlers createDefault() {
        ExceptionHandlers exceptionHandlers = new ExceptionHandlers();
        exceptionHandlers.register(HttpStatusException.class, ExceptionHandlers::handleHttpStatus);
        exceptionHandlers.register(RequestValidationException.class, ExceptionHandlers::handleValidation);
        exceptionHandlers.register(Exception.class, ExceptionHandlers::handleUnexpected);
        return exceptionHandlers;
    }

    public <T extends Throwable> void register(@NonNull Class<T> exceptionType, @NonNull ExceptionHandler<? super T> handler) {
        this.handlers.put(exceptionType, handler);
    }

    /**
     * @throws Exception 맞는 핸들러가 없거나(Error 등) 핸들러 자신이 실패했을 때.
     */
    public HttpResponse handle(HttpRequest request, Throwable exception) throws Exception {
        for (Class<?> current = exception.getClass(); current != null; current = current.getSuperclass()) {
            ExceptionHandler<? extends Throwable> handler = this.handlers.get(current);

            if (handler != null) {
                return ExceptionHandlers.invoke(handler, request, exception);
            }
        }

        if (exception instanceof Exception checkedException) {
            throw checkedException;
        }
        throw (Error) exception;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> HttpResponse invoke(ExceptionHandler<T> handler,
                                                             HttpRequest request,
                                                             Throwable exception) throws Exception {
        // 표에서 꺼낸 핸들러는 exception의 클래스(또는 상위 클래스)로 등록된 것이라 캐스팅이 안전하다.
        return handler.handle(request, (T) exception);
    }

    private static HttpResponse handleHttpStatus(HttpRequest request, HttpStatusException exception) {
        HttpResponse response = new JsonResponse(exception.status(), Collections.singletonMap("detail", exception.detail()));
        exception.headers().forEach(response::addHeaderField);
        return response;
    }

    private static HttpResponse handleValidation(HttpRequest request, RequestValidationException exception) {
        List<Map<String, Object>> details = exception.errors().stream()
                                                     .map(ExceptionHandlers::toDetail)
                                                     .toList();
        return new JsonResponse(HttpStatus.UNPROCESSABLE_CONTENT, Map.of("detail", details));
    }

    private static Map<String, Object> toDetail(ValidationError error) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("type", error.errorType());
        detail.put("loc", error.location());
        detail.put("msg", error.message());
        return detail;
    }

    private static HttpResponse handleUnexpected(HttpRequest request, Exception exception) {
        LOGGER.log(System.Logger.Level.ERROR,
                   "Unhandled exception while handling " + request.method() + " " + request.path(),
                   exception);
        return new PlainTextResponse(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.reason());
    }
}
