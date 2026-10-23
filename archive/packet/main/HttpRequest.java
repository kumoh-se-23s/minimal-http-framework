package com.minihttp.packet;

import com.minihttp.packet.body.HttpBody;
import com.minihttp.packet.header.HttpHeader;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpVersion;
import lombok.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public record HttpRequest(
        @NonNull HttpMethod method,
        @NonNull String path,
        @NonNull HttpVersion version,

        Map<String, List<String>> queryParams,
        Map<String, String> pathParams,

        @NonNull HttpHeader header,
        @NonNull HttpBody body
) implements HttpMessage {
    public HttpRequest {
        queryParams = queryParams.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
        pathParams = Map.copyOf(pathParams);
        header = HttpHeader.copyOf(header);
    }

    public static HttpRequestBuilder builder(@NonNull HttpMethod method, @NonNull String path) {
        return new HttpRequestBuilder(method, path);
    }

    public Optional<String> queryParam(String key) {
        return queryParams(key).stream().findFirst();
    }

    public List<String> queryParams(String key) {
        return queryParams.getOrDefault(key, List.of());
    }

    public Optional<String> pathParam(String key) {
        return Optional.ofNullable(pathParams.get(key));
    }

    public HttpRequest withPathParams(Map<String, String> pathParams) {
        return new HttpRequest(method, path, version, queryParams, pathParams, header, body);
    }

    public boolean keepAlive() {
        List<String> tokens = header.getAll("Connection").stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(token -> token.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (tokens.contains("close")) {
            return false;
        }
        if (tokens.contains("keep-alive")) {
            return true;
        }
        return version.defaultKeepAlive();
    }
}
