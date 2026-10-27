/**
 * 라우팅 참고 구현. FastAPI의 {@code APIRouter} + {@code include_router} 구조를 Java로 옮긴 것이다.
 *
 * <h2>전체 그림</h2>
 * <pre>
 *   [만드는 단계: main 스레드, 서버 시작 전]
 *   ExampleApplication (BaseRouter, prefix "/api")
 *    ├─ include(UserRouter)     prefix "/users"   → GET /api/users/{id}, POST /api/users
 *    └─ include(HealthRouter)   prefix "/health"  → GET /api/health
 *         │
 *         │ build()
 *         ▼
 *   [처리하는 단계: 요청 스레드들, 서버 시작 후]
 *   HttpServer ── handler.handle(request) ──▶ RoutingHandler (불변, 위 라우트 전체를 평평하게 가진다)
 * </pre>
 * 서버와 Connection은 build()로 만든 Handler 하나만 안다.
 * 라우터를 몇 단으로 나눴는지, 컨트롤러가 몇 개인지는 서버 쪽에서 보이지 않는다.
 *
 * <p>만드는 쪽(BaseRouter)과 처리하는 쪽(RoutingHandler)을 나눈 이유: 만드는 일은 한 스레드에서 끝나고
 * 처리는 여러 스레드에서 동시에 일어난다. 한 객체가 둘 다 하면 lock이 필요해지지만, 나눠 두면 만드는 쪽은
 * 스레드를 신경 쓸 필요가 없고 처리하는 쪽은 불변이라 역시 신경 쓸 게 없다.
 *
 * <h2>FastAPI와의 대응</h2>
 * <table>
 *   <tr><th>FastAPI (trend-catcher/user_api)</th><th>여기</th></tr>
 *   <tr><td>{@code BaseRouter(APIRouter)}의 {@code prefix}</td><td>{@code BaseRouter(String prefix)} 생성자</td></tr>
 *   <tr><td>{@code setup_routes()} 안의 {@code @self.get(...)}</td><td>하위 클래스 메서드의 {@code @Get(...)}</td></tr>
 *   <tr><td>{@code app.include_router(router)}</td><td>{@link com.minihttp.example.routing_example.Router#include(Router)}</td></tr>
 *   <tr><td>{@code UserAPI(FastAPI)}</td><td>{@code BaseRouter}를 상속한 최상위 앱 클래스</td></tr>
 *   <tr><td>{@code Depends(get_user_service)}</td><td>라우터 생성자로 서비스를 주입</td></tr>
 *   <tr><td>{@code uvicorn.run(app)}</td><td>{@code new HttpServerExample(config, app.build())}</td></tr>
 * </table>
 *
 * <h2>Python과 다른 점: 등록 시점</h2>
 * Python {@code BaseRouter}는 생성자에서 {@code setup_routes()}를 불러 그 자리에서 데코레이터로 라우트를 등록한다.
 * 여기서는 데코레이터 대신 메서드 어노테이션을 쓰고, 그 어노테이션을 라우트가 <b>처음 쓰일 때</b>
 * (include되거나 첫 요청이 올 때) 읽는다. 그래서 {@code setup_routes()}에 해당하는 메서드를 따로 만들 필요가 없다.
 * 생성자에서 읽지 않는 이유는 {@link com.minihttp.example.routing_example.BaseRouter} 참고.
 *
 * <h2>구성</h2>
 * <ul>
 *   <li>{@link com.minihttp.example.routing_example.Router} — 라우트를 모으는 인터페이스 (HttpHeader / DefaultHttpHeader와 같은 관계)</li>
 *   <li>{@link com.minihttp.example.routing_example.BaseRouter} — 추상 부모 클래스. 상속하고 어노테이션 메서드로 라우트 묶음을 만든다</li>
 *   <li>{@code RoutingHandler} — build()가 만드는 불변 Handler. 매칭과 404/405는 여기서 한다 (package-private)</li>
 *   <li>{@link com.minihttp.example.routing_example.RouteDefinition} — 라우터끼리 주고받는 라우트 한 줄</li>
 *   <li>{@code ControllerRegistrar} — 어노테이션 메서드를 Handler로 바꾸는 리플렉션 담당 (package-private)</li>
 *   <li>{@code RoutePaths} — 경로를 세그먼트로 자르고 합치는 규칙 (package-private)</li>
 *   <li>{@link com.minihttp.example.routing_example.RoutingExampleMain} — 실제로 띄워 볼 수 있는 예제</li>
 * </ul>
 */
package com.minihttp.example.routing_example;
