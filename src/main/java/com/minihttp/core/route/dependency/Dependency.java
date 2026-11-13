package com.minihttp.core.route.dependency;

import com.minihttp.packet.HttpRequest;

/**
 * 요청에서 값을 하나 만들어 엔드포인트 파라미터로 넘긴다. FastAPI에서 {@code Depends()}에 넘기는 함수.
 *
 * <pre>
 *   public final class CurrentUserId implements Dependency&lt;Long&gt; {
 *       private final JwtTokenProvider tokenProvider;
 *
 *       public Long resolve(HttpRequest request) {
 *           String token = request.header().get("Authorization")
 *                   .orElseThrow(() -&gt; new HttpStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
 *           ...
 *       }
 *   }
 * </pre>
 *
 * <p>생성자 인자가 필요하면 앱에 인스턴스를 등록하고, 기본 생성자만 있으면 프레임워크가 직접 만든다.
 * 인스턴스 하나를 모든 요청이 함께 쓰므로 상태를 가지지 않게 만든다.
 *
 * @param <T> 만들어 내는 값의 타입
 */
@FunctionalInterface
public interface Dependency<T> {
    /**
     * @throws Exception 실패. {@code HttpStatusException}이면 그 상태 코드로, 아니면 등록된 예외 핸들러로 응답한다.
     */
    T resolve(HttpRequest request) throws Exception;
}
