package com.minihttp.core.middleware;

import com.minihttp.core.handler.Handler;
import com.minihttp.core.response.PlainTextResponse;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.HttpResponse;
import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpStatus;
import lombok.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 브라우저의 교차 출처 요청을 허용한다. FastAPI(Starlette)의 {@code CORSMiddleware}와 같은 규칙.
 *
 * <pre>
 *   app.middleware(CorsMiddleware.builder()
 *           .allowOrigins("http://localhost:5173")
 *           .allowAllMethods()
 *           .allowAllHeaders()
 *           .allowCredentials(true)
 *           .build());
 * </pre>
 *
 * <ul>
 *   <li>Origin 헤더가 없는 요청은 그대로 통과시킨다.</li>
 *   <li>preflight(OPTIONS + Access-Control-Request-Method)는 라우팅까지 가지 않고 바로 200 또는 400으로 답한다.</li>
 *   <li>그 밖의 요청은 처리한 뒤 응답에 Access-Control-Allow-Origin 등을 붙인다.</li>
 * </ul>
 */
public final class CorsMiddleware implements Middleware {
    private static final String WILDCARD = "*";

    /** 브라우저가 preflight 없이도 보내는 헤더. 항상 허용한다. */
    private static final Set<String> SAFELISTED_HEADERS = Set.of("accept", "accept-language", "content-language", "content-type");

    private final Set<String> allowedOrigins;
    private final boolean allowAllOrigins;
    private final Set<HttpMethod> allowedMethods;
    private final Set<String> allowedHeaders;
    private final boolean allowAllHeaders;
    private final boolean allowCredentials;
    private final List<String> exposedHeaders;
    private final Duration maxAge;

    private CorsMiddleware(Builder builder) {
        this.allowedOrigins = Set.copyOf(builder.allowedOrigins);
        this.allowAllOrigins = builder.allowAllOrigins;
        this.allowedMethods = Set.copyOf(builder.allowedMethods);
        this.allowedHeaders = builder.allowedHeaders.stream()
                                                    .map(headerName -> headerName.toLowerCase(Locale.ROOT))
                                                    .collect(Collectors.toUnmodifiableSet());
        this.allowAllHeaders = builder.allowAllHeaders;
        this.allowCredentials = builder.allowCredentials;
        this.exposedHeaders = List.copyOf(builder.exposedHeaders);
        this.maxAge = builder.maxAge;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public HttpResponse handle(HttpRequest request, Handler next) throws Exception {
        Optional<String> maybeOrigin = request.header().get(HttpHeaderField.ORIGIN);

        if (maybeOrigin.isEmpty()) {
            return next.handle(request);
        }

        boolean isPreflight = request.method() == HttpMethod.OPTIONS
                && request.header().contains(HttpHeaderField.ACCESS_CONTROL_REQUEST_METHOD);

        if (isPreflight) {
            return this.preflightResponse(request, maybeOrigin.get());
        }

        HttpResponse response = next.handle(request);
        this.applySimpleHeaders(response, maybeOrigin.get());
        return response;
    }

    private HttpResponse preflightResponse(HttpRequest request, String origin) {
        List<String> failures = new ArrayList<>();

        if (!this.isOriginAllowed(origin)) {
            failures.add("origin");
        }

        String requestedMethod = request.header().get(HttpHeaderField.ACCESS_CONTROL_REQUEST_METHOD).orElse("");
        if (!this.isMethodAllowed(requestedMethod)) {
            failures.add("method");
        }

        String requestedHeaders = request.header().get(HttpHeaderField.ACCESS_CONTROL_REQUEST_HEADERS).orElse("");
        if (!this.areHeadersAllowed(requestedHeaders)) {
            failures.add("headers");
        }

        HttpResponse response = failures.isEmpty()
                ? new PlainTextResponse(HttpStatus.OK, "OK")
                : new PlainTextResponse(HttpStatus.BAD_REQUEST, "Disallowed CORS " + String.join(", ", failures));

        this.applyOriginHeader(response, origin, true);
        response.header().set(HttpHeaderField.ACCESS_CONTROL_ALLOW_METHODS, this.allowMethodsValue());
        response.header().set(HttpHeaderField.ACCESS_CONTROL_MAX_AGE, String.valueOf(this.maxAge.toSeconds()));

        String allowHeadersValue = this.allowHeadersValue(requestedHeaders);
        if (!allowHeadersValue.isEmpty()) {
            response.header().set(HttpHeaderField.ACCESS_CONTROL_ALLOW_HEADERS, allowHeadersValue);
        }

        if (this.allowCredentials) {
            response.header().set(HttpHeaderField.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }

        return response;
    }

    private void applySimpleHeaders(HttpResponse response, String origin) {
        if (!this.isOriginAllowed(origin)) {
            return;
        }

        this.applyOriginHeader(response, origin, false);

        if (this.allowCredentials) {
            response.header().set(HttpHeaderField.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }

        if (!this.exposedHeaders.isEmpty()) {
            response.header().set(HttpHeaderField.ACCESS_CONTROL_EXPOSE_HEADERS, String.join(", ", this.exposedHeaders));
        }
    }

    /**
     * 모든 출처를 허용하고 쿠키를 쓰지 않으면 "*"를 보낸다.
     * 쿠키를 쓰면 브라우저가 "*"를 거부하므로 요청 Origin을 그대로 돌려주고, 캐시가 섞이지 않게 Vary: Origin을 붙인다.
     */
    private void applyOriginHeader(HttpResponse response, String origin, boolean isPreflight) {
        if (this.allowAllOrigins && !this.allowCredentials) {
            response.header().set(HttpHeaderField.ACCESS_CONTROL_ALLOW_ORIGIN, WILDCARD);
            return;
        }

        if (isPreflight && !this.isOriginAllowed(origin)) {
            return;
        }

        response.header().set(HttpHeaderField.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        response.header().add(HttpHeaderField.VARY, "Origin");
    }

    private boolean isOriginAllowed(String origin) {
        return this.allowAllOrigins || this.allowedOrigins.contains(origin);
    }

    private boolean isMethodAllowed(String methodName) {
        return this.allowedMethods.stream().anyMatch(method -> method.name().equalsIgnoreCase(methodName.strip()));
    }

    private boolean areHeadersAllowed(String requestedHeaders) {
        if (this.allowAllHeaders) {
            return true;
        }

        return CorsMiddleware.splitHeaderNames(requestedHeaders).stream()
                             .allMatch(headerName -> SAFELISTED_HEADERS.contains(headerName)
                                     || this.allowedHeaders.contains(headerName));
    }

    private String allowMethodsValue() {
        return this.allowedMethods.stream()
                                  .map(HttpMethod::name)
                                  .sorted()
                                  .collect(Collectors.joining(", "));
    }

    private String allowHeadersValue(String requestedHeaders) {
        // 모든 헤더를 허용하면 브라우저가 물어본 헤더를 그대로 허용한다고 답한다.
        if (this.allowAllHeaders) {
            return requestedHeaders.strip();
        }

        Set<String> headerNames = new LinkedHashSet<>(SAFELISTED_HEADERS);
        headerNames.addAll(this.allowedHeaders);
        return headerNames.stream().sorted().collect(Collectors.joining(", "));
    }

    private static List<String> splitHeaderNames(String headerList) {
        return Arrays.stream(headerList.split(","))
                     .map(headerName -> headerName.strip().toLowerCase(Locale.ROOT))
                     .filter(headerName -> !headerName.isEmpty())
                     .toList();
    }

    /** 기본값은 Starlette와 같다: 출처 없음, GET만, 추가 헤더 없음, 쿠키 없음, max-age 600초. */
    public static final class Builder {
        private final Set<String> allowedOrigins = new LinkedHashSet<>();
        private boolean allowAllOrigins = false;
        private final Set<HttpMethod> allowedMethods = EnumSet.of(HttpMethod.GET);
        private final Set<String> allowedHeaders = new LinkedHashSet<>();
        private boolean allowAllHeaders = false;
        private boolean allowCredentials = false;
        private final List<String> exposedHeaders = new ArrayList<>();
        private Duration maxAge = Duration.ofSeconds(600);

        private Builder() {
        }

        public Builder allowOrigins(@NonNull String... origins) {
            for (String origin : origins) {
                if (WILDCARD.equals(origin)) {
                    this.allowAllOrigins = true;
                } else {
                    this.allowedOrigins.add(origin);
                }
            }
            return this;
        }

        public Builder allowAllOrigins() {
            this.allowAllOrigins = true;
            return this;
        }

        public Builder allowMethods(@NonNull HttpMethod... methods) {
            this.allowedMethods.clear();
            this.allowedMethods.addAll(List.of(methods));
            return this;
        }

        public Builder allowAllMethods() {
            this.allowedMethods.addAll(EnumSet.allOf(HttpMethod.class));
            return this;
        }

        public Builder allowHeaders(@NonNull String... headerNames) {
            for (String headerName : headerNames) {
                if (WILDCARD.equals(headerName)) {
                    this.allowAllHeaders = true;
                } else {
                    this.allowedHeaders.add(headerName);
                }
            }
            return this;
        }

        public Builder allowAllHeaders() {
            this.allowAllHeaders = true;
            return this;
        }

        public Builder allowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
            return this;
        }

        public Builder exposeHeaders(@NonNull String... headerNames) {
            this.exposedHeaders.addAll(List.of(headerNames));
            return this;
        }

        public Builder maxAge(@NonNull Duration maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        public CorsMiddleware build() {
            return new CorsMiddleware(this);
        }
    }
}
