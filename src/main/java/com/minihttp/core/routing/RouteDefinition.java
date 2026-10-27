package com.minihttp.core.routing;

import com.minihttp.core.handler.Handler;
import com.minihttp.packet.protocol.HttpMethod;
import lombok.NonNull;

/**
 * 라우트 한 줄: 이 메서드 + 이 경로 패턴이면 이 핸들러. 라우터끼리 include로 라우트를 넘길 때 쓴다.
 *
 * @param pattern 경로 패턴. {@code {name}} 자리는 경로 파라미터
 */
public record RouteDefinition(@NonNull HttpMethod method, @NonNull String pattern, @NonNull Handler handler) {
}
