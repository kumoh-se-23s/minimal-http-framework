package com.minihttp.core.route.binding;

import com.minihttp.core.route.BaseRouter;
import lombok.Getter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
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

            endpoints.add(maybeEndpoint.get());
        }

        return List.copyOf(endpoints);
    }
}
