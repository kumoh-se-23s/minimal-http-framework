package com.minihttp.core.routing;

import com.minihttp.core.handler.Handler;
import com.minihttp.core.routing.support.RoutePaths;
import com.minihttp.core.routing.support.RoutingResponses;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpStatus;
import lombok.NonNull;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link Router#build()}가 만드는 Handler. 생성 뒤로 바뀌지 않으므로 여러 요청 스레드가 lock 없이 함께 쓴다.
 *
 * <ul>
 *   <li>같은 메서드에서 여러 패턴이 맞으면 고정 세그먼트가 많은 쪽이 이긴다 ({@code /users/me} > {@code /users/{id}})</li>
 *   <li>경로가 없으면 404, 경로는 있는데 메서드가 없으면 405 + Allow</li>
 *   <li>HEAD는 GET 라우트로도 처리한다</li>
 * </ul>
 */
final class RoutingHandler implements Handler {
    private record Route(HttpMethod method, List<String> segments, Handler handler) {
        long literalCount() {
            return this.segments.stream().filter(segment -> !RoutePaths.isVariable(segment)).count();
        }
    }

    private record Match(Route route, Map<String, String> pathParams) {
    }

    private final List<Route> routes;

    RoutingHandler(@NonNull List<RouteDefinition> definitions) {
        this.routes = definitions.stream()
                                 .map(definition -> new Route(definition.method(),
                                         RoutePaths.segments(definition.pattern()), definition.handler()))
                                 .toList();
    }

    @Override
    public HttpResponse handle(@NonNull HttpRequest request) throws Exception {
        List<Match> matches = this.matchPath(RoutePaths.segments(request.path()));
        if (matches.isEmpty()) {
            return RoutingResponses.text(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reason());
        }

        Optional<Match> found = bestFor(matches, request.method());
        if (found.isEmpty() && request.method() == HttpMethod.HEAD) {
            found = bestFor(matches, HttpMethod.GET);
        }
        if (found.isEmpty()) {
            return methodNotAllowed(matches);
        }

        Match match = found.get();
        match.pathParams().forEach(request::pathParam);
        return match.route().handler().handle(request);
    }

    private List<Match> matchPath(List<String> requestSegments) {
        return this.routes.stream()
                          .map(route -> matchOne(route, requestSegments))
                          .flatMap(Optional::stream)
                          .toList();
    }

    private static Optional<Match> matchOne(Route route, List<String> requestSegments) {
        List<String> pattern = route.segments();
        if (pattern.size() != requestSegments.size()) {
            return Optional.empty();
        }

        Map<String, String> pathParams = new HashMap<>();
        for (int i = 0; i < pattern.size(); i++) {
            String expected = pattern.get(i);
            String actual = requestSegments.get(i);
            if (RoutePaths.isVariable(expected)) {
                pathParams.put(RoutePaths.variableName(expected), actual);
            } else if (!expected.equals(actual)) {
                return Optional.empty();
            }
        }
        return Optional.of(new Match(route, pathParams));
    }

    private static Optional<Match> bestFor(List<Match> matches, HttpMethod method) {
        return matches.stream()
                      .filter(match -> match.route().method() == method)
                      .max(Comparator.comparingLong(match -> match.route().literalCount()));
    }

    private static HttpResponse methodNotAllowed(List<Match> matches) {
        // 등록 순서가 아니라 HttpMethod 선언 순서로 내보내 응답을 일정하게 한다
        Set<HttpMethod> allowed = EnumSet.noneOf(HttpMethod.class);
        matches.forEach(match -> allowed.add(match.route().method()));
        if (allowed.contains(HttpMethod.GET)) {
            allowed.add(HttpMethod.HEAD);
        }

        HttpResponse response = RoutingResponses.text(HttpStatus.METHOD_NOT_ALLOWED, HttpStatus.METHOD_NOT_ALLOWED.reason());
        response.addHeaderField(HttpHeaderField.ALLOW, allowed.stream().map(Enum::name).collect(Collectors.joining(", ")));
        return response;
    }
}
