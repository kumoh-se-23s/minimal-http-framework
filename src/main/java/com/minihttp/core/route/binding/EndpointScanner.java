package com.minihttp.core.route.binding;

import com.minihttp.core.route.BaseRouter;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class EndpointScanner {
    private final Class<? extends BaseRouter> routerClass;
    private final Map<String, Endpoint> routeMap;

    private EndpointScanner(Class<? extends BaseRouter> routerClass) {
        this.routerClass = routerClass;
        try {
            this.routeMap = this.readRouteMap();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Failed to read routes from router class: " + routerClass.getName(),
                    exception
            );
        }
    }

    public static EndpointScanner create(Class<? extends BaseRouter> routerClass) {
        return new EndpointScanner(routerClass);
    }

    public Optional<Endpoint> getEndpoint(String path) {
        return Optional.ofNullable(this.routeMap.get(path));
    }

    private Map<String, Endpoint> readRouteMap() throws ReflectiveOperationException {
        Map<String, Endpoint> routeMap = new HashMap<>();
        for (Method endpointMethod : this.routerClass.getDeclaredMethods()) {
            Optional<Endpoint> maybeEndpoint = Endpoint.from(endpointMethod);

            maybeEndpoint.ifPresent(
                    endpoint -> routeMap.putIfAbsent(endpoint.path(), endpoint)
            );
        }

        return routeMap;
    }


    public Class<? extends BaseRouter> getRouterClass() {
        return routerClass;
    }

    public Map<String, Endpoint> getRouteMap() {
        return routeMap;
    }
}
