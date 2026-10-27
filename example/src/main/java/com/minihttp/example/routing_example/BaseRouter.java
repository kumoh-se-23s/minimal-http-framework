package com.minihttp.example.routing_example;

import com.minihttp.core.handler.Handler;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 라우터의 공통 부모. FastAPI의 {@code BaseRouter(APIRouter, ABC)}에 해당한다. 상속해서 라우트 묶음을 만든다.
 *
 * <p>추상 메서드는 없다. Python {@code BaseRouter}의 추상 메서드 {@code setup_routes()}가 하던 일은
 * 메서드 어노테이션({@code @Get}, {@code @Post} ...)이 대신한다. {@code abstract}는 "직접 만들지 말고 상속해서 쓰라"는 표시다
 * (서블릿의 {@code HttpServlet}도 같은 이유로 추상 메서드 없이 abstract다).
 *
 * <h2>상속해서 쓰기</h2>
 * <pre>
 *   public class UserRouter extends BaseRouter {
 *       private final UserService service;
 *
 *       public UserRouter(UserService service) {
 *           super("/users");                       // BaseRouter.__init__(prefix = "/users")
 *           this.service = service;                // Depends(get_user_service) 대신 생성자 주입
 *       }
 *
 *       {@literal @}Get("/{id}")                         // {@literal @}self.get("/{id}")
 *       public String find(long id) {
 *           return this.service.find(id);
 *       }
 *   }
 *
 *   public class ExampleApplication extends BaseRouter {         // UserAPI(FastAPI)
 *       public ExampleApplication(UserService service) {
 *           super("/api");
 *           this.include(new UserRouter(service));               // self.include_router(router)
 *       }
 *   }
 *
 *   Handler handler = new ExampleApplication(service).build();
 *   new HttpServerExample(config, handler);                      // uvicorn.run(app)
 * </pre>
 *
 * <h2>라우트가 들어오는 길</h2>
 * <ul>
 *   <li>자기 클래스의 어노테이션 메서드 — {@link #routes()}가 처음 불릴 때 스캔한다</li>
 *   <li>{@link #include(Router)} — 다른 라우터의 라우트를 prefix째로 가져온다</li>
 *   <li>{@link #register(Object)} — Router가 아닌 평범한 컨트롤러 객체의 어노테이션 메서드</li>
 * </ul>
 *
 * <h2>만드는 단계와 처리하는 단계</h2>
 * <pre>
 *   [main 스레드, 서버 시작 전]                       [요청 스레드들, 서버 시작 후]
 *   BaseRouter: include / register ──build()──▶  RoutingHandler: handle (읽기만)
 *   스레드 안전하지 않다 (한 스레드에서만 쓴다)      불변 객체라 lock 없이 동시에 써도 된다
 * </pre>
 * BaseRouter는 {@link Handler}가 아니다. 그래서 build()를 빠뜨리고 서버에 넘기면 컴파일 에러가 난다.
 * 동시성은 RoutingHandler 쪽에서만 생각하면 되고, 그쪽은 불변이라 따로 할 일이 없다.
 *
 * <h2>라우트가 고정되는 시점</h2>
 * 처음 {@link #routes()}가 불릴 때(부모가 include하거나 build()할 때) 자기 클래스의 어노테이션 메서드를 스캔하고
 * 라우트 목록을 고정한다. 그 뒤로 include / register를 부르면 예외다.
 * <ul>
 *   <li><b>왜 생성자에서 스캔하지 않나</b> — 부모 생성자에서 스캔하면 자식 생성자가 끝나기 전의 {@code this}를
 *       밖(ControllerRegistrar, 핸들러 람다)으로 넘기게 된다 (this-escape). 지금은 스캔이 필드를 읽지 않아 당장 터지지는 않지만,
 *       나중에 등록 시점에 필드를 쓰는 코드가 생기면 초기화 전의 null을 보게 된다.
 *       routes()가 불릴 때는 하위 클래스 생성자까지 다 끝났으므로 이런 걱정이 없다.</li>
 *   <li><b>왜 고정하나</b> — 부모가 include로 라우트를 복사해 간 뒤에 자식에 라우트를 더 넣으면 부모 쪽에는 반영되지 않는다.
 *       조용히 무시되는 대신 예외로 알려 준다.</li>
 * </ul>
 */
public abstract class BaseRouter implements Router {
    private final String prefix;

    // prefix를 붙이기 전의 상대 패턴으로 쌓인다.
    private final List<RouteDefinition> pending = new ArrayList<>();

    // 처음 routes()가 불릴 때 채워진다. null이 아니면 고정된 것이다.
    private List<RouteDefinition> fixed;

    /** prefix 없는 라우터. 최상위 앱처럼 경로 앞에 붙일 게 없을 때. */
    protected BaseRouter() {
        this("");
    }

    /**
     * @param prefix 모든 라우트 앞에 붙는 경로. {@code ""} 또는 {@code /}로 시작해야 한다.
     *               끝의 {@code /}는 떼어 낸다 ({@code "/users/"} → {@code "/users"}).
     */
    protected BaseRouter(@NonNull String prefix) {
        this.prefix = RoutePaths.normalizePrefix(prefix);
    }

    @Override
    public String prefix() {
        return this.prefix;
    }

    @Override
    public Router include(@NonNull Router child) {
        if (child == this) {
            throw new IllegalArgumentException("router cannot include itself");
        }
        this.ensureModifiable();

        // child.routes()가 child를 고정시킨다. 이 뒤로 child에 라우트를 넣으면 예외가 나므로
        // "include한 뒤에 child를 고쳤는데 반영이 안 된다"는 상황이 생기지 않는다.
        child.routes().forEach(this::addDefinition);
        return this;
    }

    @Override
    public Router register(@NonNull Object controller) {
        if (controller instanceof Router router) {
            throw new IllegalArgumentException(
                    router.getClass().getSimpleName() + " is a Router; use include() so its prefix is kept");
        }
        this.ensureModifiable();

        // 평범한 컨트롤러는 이미 생성이 끝난 객체로 넘어오므로 지금 바로 스캔해도 된다
        ControllerRegistrar.scan(controller).forEach(this::addDefinition);
        return this;
    }

    @Override
    public List<RouteDefinition> routes() {
        if (this.fixed == null) {
            ControllerRegistrar.scan(this).forEach(this::addDefinition);
            this.fixed = this.pending.stream()
                                     .map(definition -> new RouteDefinition(definition.method(),
                                             RoutePaths.join(this.prefix, definition.pattern()), definition.handler()))
                                     .toList();
        }
        return this.fixed;
    }

    @Override
    public Handler build() {
        return new RoutingHandler(this.routes());
    }

    private void ensureModifiable() {
        if (this.fixed != null) {
            throw new IllegalStateException(this.describe() + ": routes are fixed once the router is included or built");
        }
    }

    private void addDefinition(RouteDefinition definition) {
        for (RouteDefinition existing : this.pending) {
            if (existing.method() == definition.method() && RoutePaths.sameShape(existing.pattern(), definition.pattern())) {
                throw new IllegalArgumentException(this.describe() + ": duplicate route " + definition.method() + " "
                        + definition.pattern() + " (already registered as " + existing.pattern() + ")");
            }
        }
        this.pending.add(definition);
    }

    private String describe() {
        String name = this.getClass().getSimpleName();
        return this.prefix.isEmpty() ? name : name + "(" + this.prefix + ")";
    }
}
