package com.minihttp.core.middleware;

import com.minihttp.core.handler.Handler;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;

/**
 * 모든 요청의 앞뒤에 끼어드는 처리. FastAPI의 {@code @app.middleware("http")}.
 *
 * <pre>
 *   app.middleware((request, next) -&gt; {
 *       long startedAt = System.nanoTime();
 *       HttpResponse response = next.handle(request);
 *       response.header().set("X-Process-Time", String.valueOf(System.nanoTime() - startedAt));
 *       return response;
 *   });
 * </pre>
 *
 * <p>{@code next}를 부르지 않고 직접 응답을 만들어 돌려주면 라우팅까지 가지 않는다. (CORS preflight 등)
 */
@FunctionalInterface
public interface Middleware {
    HttpResponse handle(HttpRequest request, Handler next) throws Exception;
}
