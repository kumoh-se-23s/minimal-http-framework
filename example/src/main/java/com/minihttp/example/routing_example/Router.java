package com.minihttp.example.routing_example;

import com.minihttp.core.handler.Handler;
import lombok.NonNull;

import java.util.List;

/**
 * 라우트를 모으는 쪽. 구현할 때는 {@link BaseRouter}를 상속한다.
 *
 * <p>Router는 {@link Handler}가 아니다. 다 모은 뒤 {@link #build()}로 요청을 처리하는 Handler를 만들어 서버에 넘긴다.
 * 모으는 일은 서버 시작 전에 한 스레드에서 끝나고, 처리는 여러 스레드에서 동시에 일어나므로 둘을 나눠 둔다.
 * <pre>
 *   Handler handler = new ExampleApplication(service).build();   // ExampleApplication extends BaseRouter
 *   new HttpServerExample(config, handler);
 * </pre>
 *
 * <p>라우트는 어노테이션 메서드로만 정의한다. 라우터에 들어오는 길은 세 가지다.
 * <ul>
 *   <li>자기 클래스의 {@code @Get}, {@code @Post} ... 메서드</li>
 *   <li>{@link #include(Router)} — 다른 Router의 라우트를 prefix째로 가져온다 (FastAPI {@code include_router})</li>
 *   <li>{@link #register(Object)} — Router가 아닌 평범한 객체의 어노테이션 메서드를 등록</li>
 * </ul>
 *
 * <p>등록 메서드는 자기 자신을 돌려줘서 이어 쓸 수 있다. 스레드 안전하지 않다.
 */
public interface Router {

    /** 이 라우터의 모든 라우트 앞에 붙는 경로. 없으면 빈 문자열. 예: {@code "/internal/user"} */
    String prefix();

    /** child의 라우트를 child의 prefix를 붙인 채로 가져온다. 이 라우터의 prefix는 이 라우터가 내보낼 때 붙는다. */
    Router include(@NonNull Router child);

    /**
     * Router가 아닌 객체(평범한 컨트롤러)의 {@code @Get}, {@code @Post} ... 메서드를 등록한다.
     * Router를 넘기면 prefix가 빠지므로 예외다. Router는 {@link #include(Router)}로 붙인다.
     */
    Router register(@NonNull Object controller);

    /**
     * 이 라우터가 가진 라우트를 prefix를 붙여 내보낸다. 부모의 {@link #include(Router)}와 {@link #build()}가 쓴다.
     * 처음 불릴 때 라우트를 고정하므로, 그 뒤로는 이 라우터에 라우트를 더 넣을 수 없다.
     */
    List<RouteDefinition> routes();

    /** 지금까지 모은 라우트로 요청을 처리하는 불변 Handler를 만든다. 서버에는 이 결과를 넘긴다. */
    Handler build();
}
