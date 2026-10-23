package com.minihttp.example.routing;

import com.minihttp.example.http.HttpMethod;
import com.minihttp.example.http.HttpRequest;
import com.minihttp.example.http.HttpResponse;
import com.minihttp.example.http.HttpStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class Router {
    private record Route(HttpMethod method, List<String> segments, Handler handler) {
        long literalCount() {
            return segments.stream().filter(segment -> !segment.startsWith("{")).count();
        }
    }

    private record Match(Route route, Map<String, String> params) {
    }

    private final List<Route> routes = new ArrayList<>();

    public Router get(String pattern, Handler handler) {
        return add(HttpMethod.GET, pattern, handler);
    }

    public Router post(String pattern, Handler handler) {
        return add(HttpMethod.POST, pattern, handler);
    }

    public Router put(String pattern, Handler handler) {
        return add(HttpMethod.PUT, pattern, handler);
    }

    public Router patch(String pattern, Handler handler) {
        return add(HttpMethod.PATCH, pattern, handler);
    }

    public Router delete(String pattern, Handler handler) {
        return add(HttpMethod.DELETE, pattern, handler);
    }

    public Router register(Object controller) {
        ControllerRegistrar.register(this, controller);
        return this;
    }

    public Router add(HttpMethod method, String pattern, Handler handler) {
        routes.add(new Route(method, segments(pattern), handler));
        return this;
    }

    public HttpResponse route(HttpRequest request) throws Exception {
        List<String> requestSegments = segments(request.path());
        List<Match> matches = new ArrayList<>();
        for (Route route : routes) {
            Map<String, String> params = match(route.segments(), requestSegments);
            if (params != null) {
                matches.add(new Match(route, params));
            }
        }

        if (matches.isEmpty()) {
            return HttpResponse.text(HttpStatus.NOT_FOUND, "Not Found");
        }

        Match found = find(matches, request.method());
        if (found == null && request.method() == HttpMethod.HEAD) {
            found = find(matches, HttpMethod.GET);
        }
        if (found != null) {
            return found.route().handler().handle(request.withPathParams(found.params()));
        }

        Set<HttpMethod> allowed = new LinkedHashSet<>();
        for (Match match : matches) {
            allowed.add(match.route().method());
        }
        String allow = allowed.stream().map(Enum::name).collect(Collectors.joining(", "));
        return HttpResponse.text(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed").header("Allow", allow);
    }

    private static Match find(List<Match> matches, HttpMethod method) {
        return matches.stream()
                .filter(match -> match.route().method() == method)
                .max(Comparator.comparingLong(match -> match.route().literalCount()))
                .orElse(null);
    }

    private static Map<String, String> match(List<String> pattern, List<String> path) {
        if (pattern.size() != path.size()) {
            return null;
        }
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < pattern.size(); i++) {
            String expected = pattern.get(i);
            String actual = path.get(i);
            if (expected.startsWith("{") && expected.endsWith("}")) {
                params.put(expected.substring(1, expected.length() - 1), actual);
            } else if (!expected.equals(actual)) {
                return null;
            }
        }
        return params;
    }

    private static List<String> segments(String path) {
        return Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toList();
    }
}
