package com.minihttp.example.routing_example;

import com.minihttp.core.handler.Handler;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpStatus;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 요청 처리 단계의 라우터. {@link Router#build()}가 만들고, 서버에는 이것이 {@link Handler}로 넘어간다.
 *
 * <h2>왜 따로 있나</h2>
 * 라우트를 모으는 일(BaseRouter)은 서버 시작 전에 main 스레드 하나에서 끝난다.
 * 요청 처리는 Connection마다 다른 가상 스레드에서 동시에 일어난다.
 * 두 일을 한 객체가 하면 "처음 요청이 올 때 확정한다" 같은 지연 처리에 lock이 필요해진다.
 * 그래서 확정을 build() 한 번으로 당기고, 확정된 결과는 이 불변 객체에 담는다.
 *
 * <h2>왜 lock 없이 안전한가</h2>
 * <ul>
 *   <li>필드가 전부 {@code final}이고, 라우트 목록은 {@code List.copyOf}로 만든 불변 List다.
 *       final 필드는 생성자가 끝난 뒤 어느 스레드에서 봐도 초기화된 값이 보인다 (JLS 17.5).</li>
 *   <li>생성 뒤로 아무것도 바꾸지 않는다. 여러 스레드가 동시에 읽기만 하는 것은 경쟁이 아니다.</li>
 *   <li>요청마다 바뀌는 값(매칭 결과, 경로 파라미터)은 지역 변수와 그 요청의 HttpRequest에만 쓴다.</li>
 * </ul>
 *
 * <h2>매칭 규칙</h2>
 * <ul>
 *   <li>패턴의 {@code {name}} 자리는 아무 세그먼트 하나와 맞고, 그 값은 {@link HttpRequest#pathParam(String)}로 들어간다.</li>
 *   <li>같은 메서드에서 여러 패턴이 맞으면 고정 세그먼트가 더 많은 쪽이 이긴다
 *       ({@code /users/me}가 {@code /users/{id}}보다 우선). 등록 순서는 상관없다.</li>
 *   <li>경로가 하나도 안 맞으면 404, 경로는 맞는데 메서드가 없으면 405 + {@code Allow} 헤더.</li>
 *   <li>HEAD는 같은 경로의 GET 라우트로도 처리한다.</li>
 * </ul>
 */
final class RoutingHandler implements Handler {

    /** pattern과 segments는 prefix까지 붙은 전체 경로 기준. segments는 요청마다 자르지 않도록 미리 잘라 둔다. */
    private record Route(HttpMethod method, String pattern, List<String> segments, Handler handler) {
        long literalCount() {
            return this.segments.stream().filter(segment -> !RoutePaths.isVariable(segment)).count();
        }
    }

    private record Match(Route route, Map<String, String> pathParams) {
    }

    private final List<Route> routes;

    RoutingHandler(@NonNull List<RouteDefinition> definitions) {
        this.routes = definitions.stream()
                                 .map(definition -> new Route(definition.method(), definition.pattern(),
                                         RoutePaths.segments(definition.pattern()), definition.handler()))
                                 .collect(Collectors.collectingAndThen(Collectors.toList(), List::copyOf));
    }

    @Override
    public HttpResponse handle(@NonNull HttpRequest request) throws Exception {
        List<Match> matches = this.match(RoutePaths.segments(request.path()));
        if (matches.isEmpty()) {
            return RoutingResponses.text(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reason());
        }

        Optional<Match> found = find(matches, request.method());
        if (found.isEmpty() && request.method() == HttpMethod.HEAD) {
            found = find(matches, HttpMethod.GET);
        }
        if (found.isEmpty()) {
            return methodNotAllowed(matches);
        }

        Match match = found.get();
        match.pathParams().forEach(request::pathParam);
        return match.route().handler().handle(request);
    }

    private List<Match> match(List<String> requestSegments) {
        List<Match> matches = new ArrayList<>();
        for (Route route : this.routes) {
            matchOne(route, requestSegments).ifPresent(matches::add);
        }
        return matches;
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

    private static Optional<Match> find(List<Match> matches, HttpMethod method) {
        return matches.stream()
                      .filter(match -> match.route().method() == method)
                      .max(Comparator.comparingLong(match -> match.route().literalCount()));
    }

    private static HttpResponse methodNotAllowed(List<Match> matches) {
        // 등록 순서(어노테이션 스캔은 메서드 이름순)가 아니라 HttpMethod 선언 순서로 내보내 응답을 일정하게 한다
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
