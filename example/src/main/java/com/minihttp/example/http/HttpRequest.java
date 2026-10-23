package com.minihttp.example.http;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

public record HttpRequest(
        HttpMethod method,
        String path,
        Map<String, String> queryParams,
        String version,
        HttpHeaders headers,
        byte[] body,
        Map<String, String> pathParams
) {
    public Optional<String> query(String name) {
        return Optional.ofNullable(queryParams.get(name));
    }

    public Optional<String> header(String name) {
        return headers.get(name);
    }

    public String pathParam(String name) {
        String value = pathParams.get(name);
        if (value == null) {
            throw new IllegalArgumentException("No path parameter: " + name);
        }
        return value;
    }

    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /** HTTP/1.1은 기본이 keep-alive, HTTP/1.0은 기본이 close. */
    public boolean keepAlive() {
        Optional<String> connection = headers.get("Connection");
        if (connection.isPresent()) {
            String value = connection.get().trim();
            if (value.equalsIgnoreCase("close")) {
                return false;
            }
            if (value.equalsIgnoreCase("keep-alive")) {
                return true;
            }
        }
        return version.equals("HTTP/1.1");
    }

    public HttpRequest withPathParams(Map<String, String> params) {
        return new HttpRequest(method, path, queryParams, version, headers, body, Map.copyOf(params));
    }
}
