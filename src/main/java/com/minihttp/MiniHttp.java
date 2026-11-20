package com.minihttp;

import com.minihttp.config.OpenApiConfig;
import com.minihttp.config.ServerConfig;
import com.minihttp.core.HttpServer;
import com.minihttp.core.handler.Handler;
import com.minihttp.core.inject.Container;
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
import com.minihttp.core.route.exception.RequestValidationException;
import com.minihttp.core.route.template.PathTemplate;
import com.minihttp.json.DefaultJsonMapper;
import com.minihttp.json.JsonMapper;
import com.minihttp.log.AccessLogHandler;
import com.minihttp.log.MiniHttpLogging;
import com.minihttp.openapi.OpenApiDocument;
import com.minihttp.openapi.OpenApiHandler;
import com.minihttp.packet.protocol.HttpMethod;
import lombok.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;


public final class MiniHttp {
    private static final System.Logger LOGGER = MiniHttpLogging.logger(MiniHttpLogging.SERVER);

    private final Container container = Container.create();
    private final List<Function<Container, BaseRouter>> routerFactories = new ArrayList<>();
    private final List<Middleware> middlewares = new ArrayList<>();
    private final DependencyRegistry dependencyRegistry = DependencyRegistry.create(this.container);
    private final ExceptionHandlers exceptionHandlers = ExceptionHandlers.createDefault();
    private JsonMapper jsonMapper = DefaultJsonMapper.getInstance();
    private OpenApiConfig openApiConfig = OpenApiConfig.builder().build();

    private MiniHttp() {
    }

    public static MiniHttp create() {
        return new MiniHttp();
    }

    public MiniHttp includeRouter(@NonNull BaseRouter router) {
        this.routerFactories.add(container -> router);
        return this;
    }

    public MiniHttp includeRouter(@NonNull Class<? extends BaseRouter> routerType) {
        this.routerFactories.add(container -> container.get(routerType));
        return this;
    }

    public <T> MiniHttp bind(@NonNull Class<T> type, @NonNull Class<? extends T> implementation) {
        this.container.bind(type, implementation);
        return this;
    }

    public <T> MiniHttp provide(@NonNull Class<T> type, @NonNull T instance) {
        this.container.provide(type, instance);
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

    public MiniHttp openApi(@NonNull OpenApiConfig openApiConfig) {
        this.openApiConfig = openApiConfig;
        return this;
    }


    public Handler buildHandler() {
        List<BaseRouter> routers = this.routerFactories.stream()
                                                       .map(routerFactory -> routerFactory.apply(this.container))
                                                       .toList();
        RouterResolver routerResolver = RouterResolver.create(routers);
        this.prepareDependencies(routerResolver.endpoints());
        MiniHttp.logRoutes(routerResolver.endpoints());

        ArgumentBinder argumentBinder = ArgumentBinder.create(this.dependencyRegistry, this.jsonMapper);
        Handler handler = ExceptionHandlingHandler.wrap(
                RouteHandler.create(routerResolver, argumentBinder, this.jsonMapper),
                this.exceptionHandlers
        );

        if (this.openApiConfig.enabled()) {
            handler = this.wrapOpenApi(handler, routerResolver.endpoints());
        }

        for (Middleware middleware : this.middlewares) {
            Handler next = handler;
            handler = request -> middleware.handle(request, next);
        }

        return AccessLogHandler.wrap(ExceptionHandlingHandler.wrap(handler, this.exceptionHandlers));
    }

    public HttpServer start(@NonNull ServerConfig config) throws IOException {
        try {
            HttpServer server = new HttpServer(config, this.buildHandler());
            server.onStopped(this.container::close);
            server.start();
            return server;

        } catch (IOException | RuntimeException exception) {
            this.container.close();
            throw exception;
        }
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

    private Handler wrapOpenApi(Handler handler, List<Endpoint> endpoints) {
        List<String> reservedPaths = OpenApiHandler.reservedPaths(this.openApiConfig);
        MiniHttp.validateOpenApiPaths(endpoints, reservedPaths);

        boolean validationErrorsDocumented = !this.exceptionHandlers.isCustomized(RequestValidationException.class);
        String specJson = OpenApiDocument.create(this.openApiConfig, endpoints, validationErrorsDocumented).toJson();
        LOGGER.log(System.Logger.Level.INFO,
                   "OpenAPI docs at " + this.openApiConfig.docsPath() + " (spec " + this.openApiConfig.specPath() + ")");
        return OpenApiHandler.wrap(handler, this.openApiConfig, specJson);
    }

    private static void validateOpenApiPaths(List<Endpoint> endpoints, List<String> reservedPaths) {
        for (Endpoint endpoint : endpoints) {
            if (endpoint.httpMethod() != HttpMethod.GET) {
                continue;
            }

            for (String reservedPath : reservedPaths) {
                if (PathTemplate.isSamePath(endpoint.pathTemplate(), PathTemplate.create(reservedPath))) {
                    throw new IllegalStateException(
                            "Endpoint " + endpoint.describe() + " conflicts with OpenAPI path " + reservedPath
                                    + ". Change the endpoint or OpenApiConfig paths, or disable OpenAPI");
                }
            }
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
