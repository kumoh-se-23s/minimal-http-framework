package com.minihttp.example.routing_example;

import com.minihttp.core.handler.Handler;
import com.minihttp.packet.protocol.HttpMethod;
import lombok.NonNull;

/**
 * 라우트 한 줄: "이 메서드 + 이 경로 패턴이면 이 핸들러".
 *
 * <p>라우터끼리 라우트를 넘길 때 쓴다. {@link Router#routes()}는 자기 prefix를 붙인 pattern으로 내보내고,
 * 부모는 {@link Router#include(Router)}에서 그걸 받아 다시 자기 라우트로 등록한다.
 * 그래서 prefix가 여러 단이어도 include 한 번마다 한 단씩 붙는다.
 *
 * <pre>
 *   UserRouter.routes()         → GET /users/{id}
 *   ExampleApplication.routes() → GET /api/users/{id}   (include할 때 "/users/{id}"로 받고, 내보낼 때 "/api"가 붙음)
 * </pre>
 *
 * @param method  HTTP 메서드
 * @param pattern 경로 패턴. {@code {name}} 자리는 경로 파라미터
 * @param handler 매칭되면 부를 핸들러. ControllerRegistrar가 어노테이션 메서드를 감싸서 만든다
 */
public record RouteDefinition(@NonNull HttpMethod method, @NonNull String pattern, @NonNull Handler handler) {
}
