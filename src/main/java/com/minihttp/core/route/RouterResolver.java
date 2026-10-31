package com.minihttp.core.route;

import com.minihttp.core.route.binding.Endpoint;
import com.minihttp.core.route.binding.EndpointScanner;
import com.minihttp.core.route.exception.MethodNotAllowedException;
import com.minihttp.core.route.exception.RouteNotFoundException;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.protocol.HttpMethod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class RouterResolver {
    private final List<Endpoint> endpoints;

    private RouterResolver(List<BaseRouter> routers) {
        List<Endpoint> endpoints = new ArrayList<>();
        for (BaseRouter router : routers) {
            endpoints.addAll(EndpointScanner.create(router).getEndpoints());
        }

        endpoints.sort(Comparator.comparing(Endpoint::pathTemplate));
        this.endpoints = List.copyOf(endpoints);
    }

    public static RouterResolver create(List<BaseRouter> routers) {
        return new RouterResolver(routers);
    }

    public Endpoint resolve(HttpRequest request) {
        Set<HttpMethod> allowedMethods = EnumSet.noneOf(HttpMethod.class);

        for (Endpoint endpoint : this.endpoints) {
            Optional<Map<String, String>> maybePathVariables = endpoint.pathTemplate().match(request.path());

            if (maybePathVariables.isEmpty()) {
                continue;
            }

            if (endpoint.httpMethod() != request.method()) {
                allowedMethods.add(endpoint.httpMethod());
                continue;
            }

            maybePathVariables.get().forEach(request::pathParam);
            return endpoint;
        }

        if (allowedMethods.isEmpty()) {
            throw new RouteNotFoundException(request.path());
        }

        throw new MethodNotAllowedException(request.method(), request.path(), allowedMethods);
    }
}
