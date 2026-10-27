package com.minihttp.core.routing;

import com.minihttp.core.handler.Handler;
import lombok.NonNull;

import java.util.List;

/**
 * 라우트를 모으는 쪽. 구현할 때는 {@link BaseRouter}를 상속한다.
 *
 * <p>Router는 {@link Handler}가 아니다. 다 모은 뒤 {@link #build()}로 만든 Handler를 서버에 넘긴다.
 * 모으는 일은 서버 시작 전 한 스레드에서 끝나고, 요청 처리는 여러 스레드에서 동시에 일어나기 때문에 둘을 나눈다.
 *
 * <p>스레드 안전하지 않다.
 */
public interface Router {

    /** 모든 라우트 앞에 붙는 경로. 없으면 빈 문자열. */
    String prefix();

    /** child의 라우트를 child의 prefix를 붙인 채로 가져온다. (FastAPI {@code include_router}) */
    Router include(@NonNull Router child);

    /** Router가 아닌 평범한 컨트롤러 객체의 어노테이션 메서드를 등록한다. Router는 {@link #include}로 붙인다. */
    Router register(@NonNull Object controller);

    /** prefix를 붙인 전체 라우트 목록. 처음 불릴 때 라우트가 고정되고, 그 뒤로는 더 넣을 수 없다. */
    List<RouteDefinition> routes();

    /** 모은 라우트로 요청을 처리하는 불변 Handler를 만든다. */
    Handler build();
}
