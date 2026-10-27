package com.minihttp.core.routing;

import com.minihttp.core.handler.Handler;
import com.minihttp.core.routing.binding.ControllerRegistrar;
import com.minihttp.core.routing.support.RoutePaths;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 라우터의 공통 부모. 상속하고 {@code @Get}, {@code @Post} ... 메서드로 라우트를 정의한다.
 *
 * <pre>
 *   public class UserRouter extends BaseRouter {
 *       private final UserService service;
 *
 *       public UserRouter(UserService service) {
 *           super("/users");
 *           this.service = service;
 *       }
 *
 *       {@literal @}Get("/{id}")
 *       public String find(long id) { ... }
 *   }
 *
 *   public class Application extends BaseRouter {
 *       public Application(UserService service) {
 *           super("/api");
 *           this.include(new UserRouter(service));
 *       }
 *   }
 *
 *   new HttpServer(config, new Application(service).build());
 * </pre>
 *
 * <p>자기 클래스의 어노테이션 메서드는 처음 {@link #routes()}가 불릴 때(include되거나 build될 때) 스캔한다.
 * 생성자에서 스캔하면 하위 클래스 생성자가 끝나기 전의 {@code this}가 밖으로 새기 때문이다.
 * 그 시점에 라우트가 고정되고, 이후의 include / register는 예외다.
 */
public abstract class BaseRouter implements Router {
    private final String prefix;
    private final List<RouteDefinition> pending = new ArrayList<>();

    // null이 아니면 고정된 것이다
    private List<RouteDefinition> fixed;

    protected BaseRouter() {
        this("");
    }

    /** @param prefix {@code ""} 또는 {@code /}로 시작하는 경로. 끝의 {@code /}는 뗀다. */
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

        // child.routes()가 child를 고정하므로, include 뒤에 child를 고쳐서 생기는 불일치가 없다
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

        ControllerRegistrar.scan(controller).forEach(this::addDefinition);
        return this;
    }

    @Override
    public List<RouteDefinition> routes() {
        if (this.fixed == null) {
            ControllerRegistrar.scan(this).forEach(this::addDefinition);
            this.fixed = this.pending.stream()
                                     .map(this::withPrefix)
                                     .toList();
        }
        return this.fixed;
    }

    @Override
    public Handler build() {
        return new RoutingHandler(this.routes());
    }

    private RouteDefinition withPrefix(RouteDefinition definition) {
        return new RouteDefinition(definition.method(), RoutePaths.join(this.prefix, definition.pattern()), definition.handler());
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
