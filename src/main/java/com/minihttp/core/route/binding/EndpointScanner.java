package com.minihttp.core.route.binding;

import com.minihttp.core.route.BaseRouter;
import com.minihttp.core.route.template.PathTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class EndpointScanner {
    private final BaseRouter router;
    private final List<Endpoint> endpoints;

    private EndpointScanner(BaseRouter router) {
        this.router = router;
        try {
            this.endpoints = this.readEndpoints();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Failed to read routes from router class: " + router.getClass().getName(),
                    exception
            );
        }
    }

    public static EndpointScanner create(BaseRouter router) {
        return new EndpointScanner(router);
    }

    private List<Endpoint> readEndpoints() throws ReflectiveOperationException {
        List<Endpoint> endpoints = new ArrayList<>();
        for (Method endpointMethod : this.router.getClass().getDeclaredMethods()) {
            Optional<Endpoint> maybeEndpoint = Endpoint.from(this.router, endpointMethod);

            if (maybeEndpoint.isEmpty()) {
                continue;
            }

            Endpoint endpoint = maybeEndpoint.get();
            EndpointScanner.validateNotDuplicated(endpoints, endpoint);
            endpoints.add(endpoint);
        }

        endpoints.sort(Comparator.comparing(Endpoint::pathTemplate));
        return List.copyOf(endpoints);
    }

    private static void validateNotDuplicated(List<Endpoint> endpoints, Endpoint newEndpoint) {
        for (Endpoint endpoint : endpoints) {
            boolean isSameMethod = endpoint.httpMethod() == newEndpoint.httpMethod();
            boolean isSamePath = PathTemplate.isSamePath(endpoint.pathTemplate(), newEndpoint.pathTemplate());

            if (isSameMethod && isSamePath) {
                throw new IllegalStateException(
                        "Duplicate endpoint " + newEndpoint.httpMethod() + " " + newEndpoint.handler()
                                + " conflicts with " + endpoint.handler());
            }
        }
    }

    public BaseRouter getRouter() {
        return this.router;
    }

    public List<Endpoint> getEndpoints() {
        return this.endpoints;
    }
}
