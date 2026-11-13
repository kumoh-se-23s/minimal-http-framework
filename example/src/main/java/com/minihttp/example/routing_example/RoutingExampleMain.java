package com.minihttp.example.routing_example;

import com.minihttp.config.ServerConfig;
import com.minihttp.core.HttpServer;
import com.minihttp.example.routing_example.annotation.Body;
import com.minihttp.example.routing_example.annotation.Delete;
import com.minihttp.example.routing_example.annotation.Get;
import com.minihttp.example.routing_example.annotation.Header;
import com.minihttp.example.routing_example.annotation.Post;
import com.minihttp.example.routing_example.annotation.Query;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.HttpStatus;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * trend-catcher/user_api 구조를 그대로 옮긴 실행 예제.
 *
 * <pre>
 *   Python (FastAPI)                              Java (여기)
 *   ──────────────────────────────────────────    ──────────────────────────────────────────
 *   class UserAPI(FastAPI):                       class ExampleApplication extends BaseRouter
 *       _setup_routers():                             생성자: include(new UserRouter(...))
 *           for router in INTERNAL + EXTERNAL:                include(new HealthRouter())
 *               self.include_router(router)
 *
 *   class UserRouter(BaseRouter):                 class UserRouter extends BaseRouter
 *       __init__: super(prefix="/user")               생성자: super("/user"), service 주입
 *       setup_routes:                                 어노테이션 메서드
 *           @self.get("/get-all")                         @Get("/get-all")
 *           async def get_all(service = Depends(...))     public String getAll()  (this.service 사용)
 *
 *   uvicorn.run("app:UserAPI", port=...)          new HttpServer(config, app.build())
 * </pre>
 *
 * 띄운 뒤 해 볼 것:
 * <pre>
 *   curl http://localhost:8081/api/health
 *   curl -X POST -d 'kim' http://localhost:8081/api/user/create
 *   curl http://localhost:8081/api/user/get-all
 *   curl http://localhost:8081/api/user/1
 *   curl http://localhost:8081/api/user/abc                → 400 (id가 long이 아님)
 *   curl http://localhost:8081/api/user/get-by-name        → 400 (name 쿼리 없음)
 *   curl -X POST http://localhost:8081/api/user/1          → 405 + Allow: GET, DELETE, HEAD
 *   curl -X DELETE http://localhost:8081/api/user/get-all  → 400 (아래 참고)
 *   curl http://localhost:8081/api/nope                    → 404
 * </pre>
 *
 * <p>마지막에서 두 번째가 405가 아닌 이유: DELETE 라우트는 {@code /{id}}뿐이라 {@code get-all}이 id로 매칭되고,
 * 그 값이 long으로 안 바뀌어서 400이 된다. "고정 세그먼트가 많은 쪽이 이긴다"는 규칙은
 * <b>같은 메서드</b>의 라우트끼리만 적용된다. 메서드가 다른 {@code GET /get-all}은 후보가 아니다.
 */
public final class RoutingExampleMain {

    /** 서비스 계층 자리. 실제로는 DB를 쓰겠지만 예제에서는 메모리 Map. */
    public static final class UserService {
        private final Map<Long, String> users = new ConcurrentHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        public long create(String name) {
            long id = this.sequence.incrementAndGet();
            this.users.put(id, name);
            return id;
        }

        public Optional<String> find(long id) {
            return Optional.ofNullable(this.users.get(id));
        }

        public Map<Long, String> findAll() {
            return Map.copyOf(this.users);
        }

        public boolean remove(long id) {
            return this.users.remove(id) != null;
        }
    }

    /**
     * 어노테이션 방식 라우터. FastAPI {@code setup_routes()} 안의 {@code @self.get(...)}을 메서드 어노테이션으로 바꾼 것.
     *
     * <p>{@code this.service}는 생성자에서 채워진다. 라우트 스캔은 생성자가 끝난 뒤(include될 때) 일어나고,
     * 메서드 호출은 요청이 올 때 일어나므로 언제 읽어도 null이 아니다.
     */
    public static final class UserRouter extends BaseRouter {
        private final UserService service;

        public UserRouter(UserService service) {
            super("/user");
            this.service = service;
        }

        @Get("/get-all")
        public String getAll() {
            return this.service.findAll().entrySet().stream()
                               .map(entry -> entry.getKey() + "=" + entry.getValue())
                               .collect(Collectors.joining(", ", "[", "]"));
        }

        // 쿼리 ?name=... 이 없으면 400. Optional<String>으로 받으면 없어도 들어온다.
        @Get("/get-by-name")
        public String getByName(@Query String name) {
            return this.service.findAll().entrySet().stream()
                               .filter(entry -> entry.getValue().equals(name))
                               .map(entry -> String.valueOf(entry.getKey()))
                               .collect(Collectors.joining(", ", "[", "]"));
        }

        // 이름이 경로 변수 {id}와 같으므로 @PathParam 없이도 경로에서 채워진다.
        // "/get-all"과 "/{id}"가 둘 다 맞는 요청은 고정 세그먼트가 많은 "/get-all"이 이긴다.
        @Get("/{id}")
        public HttpResponse find(long id) {
            return this.service.find(id)
                               .map(name -> RoutingResponses.text(HttpStatus.OK, name))
                               .orElseGet(() -> RoutingResponses.text(HttpStatus.NOT_FOUND, "no user " + id));
        }

        // 본문 전체를 이름으로 쓴다. JSON 바인딩은 아직 없어서 String으로 받는다.
        @Post("/create")
        public HttpResponse create(@Body String name, @Header Optional<String> userAgent) {
            long id = this.service.create(name.strip());
            return RoutingResponses.text(HttpStatus.CREATED, "created " + id + " via " + userAgent.orElse("unknown"));
        }

        // void → 204 No Content
        @Delete("/{id}")
        public void remove(long id) {
            this.service.remove(id);
        }
    }

    /** 두 번째 라우터. 여러 라우터를 include로 합치는 모습을 보이려고 둔다 (INTERNAL_ROUTERS + EXTERNAL_ROUTERS 자리). */
    public static final class HealthRouter extends BaseRouter {
        private final long startedAt = System.currentTimeMillis();

        public HealthRouter() {
            super("/health");
        }

        // "/"는 prefix 자체와 같다 → GET /api/health
        @Get("/")
        public String health() {
            return "ok";
        }

        @Get("/uptime")
        public String uptime() {
            return (System.currentTimeMillis() - this.startedAt) + "ms";
        }
    }

    /**
     * 앱 = 최상위 라우터. FastAPI {@code UserAPI(FastAPI)}의 {@code _setup_routers()} 자리.
     * 이 객체는 Handler가 아니다. {@code build()}로 만든 Handler 하나가 서버에 넘어간다.
     */
    public static final class ExampleApplication extends BaseRouter {
        public ExampleApplication(UserService userService) {
            super("/api");
            this.include(new UserRouter(userService))
                .include(new HealthRouter());
        }
    }

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.builder()
                                          .listen(8081)
                                          .build();

        // 만드는 단계: 여기까지는 main 스레드 하나에서만 돈다
        ExampleApplication app = new ExampleApplication(new UserService());

        // build() 이후로 라우트는 고정되고, 서버 스레드들은 불변 Handler만 본다
        HttpServer server = new HttpServer(config, app.build());
        server.start();

        System.out.println("listening on " + server.listeners().getFirst().boundAddress());
        app.routes().forEach(route -> System.out.println("  " + route.method() + " " + route.pattern()));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }));
    }
}
