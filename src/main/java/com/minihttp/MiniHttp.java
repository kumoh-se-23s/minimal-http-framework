package com.minihttp;

import com.minihttp.config.ServerConfig;
import com.minihttp.core.HttpServer;
import com.minihttp.core.handler.Handler;
import com.minihttp.core.middleware.Middleware;
import com.minihttp.core.route.BaseRouter;
import com.minihttp.core.route.RouteHandler;
import com.minihttp.core.route.RouterResolver;
import com.minihttp.core.route.binding.ArgumentBinder;
import com.minihttp.core.route.binding.Endpoint;
import com.minihttp.core.route.binding.ParameterSource;
import com.minihttp.core.route.binding.ParameterSpec;
import com.minihttp.core.route.dependency.Dependency;
import com.minihttp.core.route.dependency.DependencyRegistry;
import com.minihttp.core.route.exception.ExceptionHandler;
import com.minihttp.core.route.exception.ExceptionHandlers;
import com.minihttp.core.route.exception.ExceptionHandlingHandler;
import com.minihttp.json.DefaultJsonMapper;
import com.minihttp.json.JsonMapper;
import com.minihttp.log.AccessLogHandler;
import com.minihttp.log.MiniHttpLogging;
import lombok.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;


public final class MiniHttp {
    private static final System.Logger LOGGER = MiniHttpLogging.logger(MiniHttpLogging.SERVER);

    private final List<BaseRouter> routers = new ArrayList<>();
    private final List<Middleware> middlewares = new ArrayList<>();
    private final DependencyRegistry dependencyRegistry = DependencyRegistry.create();
    private final ExceptionHandlers exceptionHandlers = ExceptionHandlers.createDefault();
    private JsonMapper jsonMapper = DefaultJsonMapper.getInstance();

    private MiniHttp() {
    }

    public static MiniHttp create() {
        return new MiniHttp();
    }

    public MiniHttp includeRouter(@NonNull BaseRouter router) {
        this.routers.add(router);
        return this;
    }

    public MiniHttp dependency(@NonNull Dependency<?> dependency) {
        this.dependencyRegistry.register(dependency);
        return this;
    }

    public <T extends Throwable> MiniHttp exceptionHandler(@NonNull Class<T> exceptionType,
                                                           @NonNull ExceptionHandler<? super T> handler) {
        this.exceptionHandlers.register(exceptionType, handler);
        return this;
    }

    public MiniHttp middleware(@NonNull Middleware middleware) {
        this.middlewares.add(middleware);
        return this;
    }

    public MiniHttp jsonMapper(@NonNull JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        return this;
    }


    public Handler buildHandler() {
        RouterResolver routerResolver = RouterResolver.create(List.copyOf(this.routers));
        this.prepareDependencies(routerResolver.endpoints());
        MiniHttp.logRoutes(routerResolver.endpoints());

        ArgumentBinder argumentBinder = ArgumentBinder.create(this.dependencyRegistry, this.jsonMapper);
        Handler handler = ExceptionHandlingHandler.wrap(
                RouteHandler.create(routerResolver, argumentBinder, this.jsonMapper),
                this.exceptionHandlers
        );

        for (Middleware middleware : this.middlewares) {
            Handler next = handler;
            handler = request -> middleware.handle(request, next);
        }

        return AccessLogHandler.wrap(ExceptionHandlingHandler.wrap(handler, this.exceptionHandlers));
    }

    public HttpServer start(@NonNull ServerConfig config) throws IOException {
        HttpServer server = new HttpServer(config, this.buildHandler());
        server.start();
        return server;
    }


    public void run(@NonNull ServerConfig config) throws IOException, InterruptedException {
        MiniHttpLogging.enableConsole();
        HttpServer server = this.start(config);
        CountDownLatch stopped = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
            try {
                server.stop();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                stopped.countDown();
            }
        }));

        stopped.await();
    }

    private static void logRoutes(List<Endpoint> endpoints) {
        LOGGER.log(System.Logger.Level.INFO, "Registered " + endpoints.size() + " routes");
        for (Endpoint endpoint : endpoints) {
            LOGGER.log(System.Logger.Level.INFO, "  " + endpoint.describe());
        }
    }

    private void prepareDependencies(List<Endpoint> endpoints) {
        for (Endpoint endpoint : endpoints) {
            for (ParameterSpec parameter : endpoint.parameters()) {
                if (parameter.source() != ParameterSource.DEPENDENCY) {
                    continue;
                }

                this.dependencyRegistry.prepare(
                        parameter.dependencyType(),
                        parameter.rawValueType(),
                        "parameter '" + parameter.name() + "' of " + endpoint.describe()
                );
            }
        }
    }
}
