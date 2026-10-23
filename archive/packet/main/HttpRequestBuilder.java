package com.minihttp.packet;

import com.minihttp.packet.body.HttpBody;
import com.minihttp.packet.header.MutableHttpHeader;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpVersion;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HttpRequestBuilder {
    private final HttpMethod method;
    private final String path;
    private HttpVersion version = HttpVersion.HTTP_1_1;
    private final Map<String, List<String>> queryParams = new HashMap<>();
    private final Map<String, String> pathParams = new HashMap<>();
    private final MutableHttpHeader header = new MutableHttpHeader();
    private HttpBody body = HttpBody.empty();

    public HttpRequestBuilder(HttpMethod method, String path) {
        this.method = method;
        this.path = path;
    }

    public HttpRequestBuilder version(@NonNull HttpVersion version) {
        this.version = version;
        return this;
    }

    public HttpRequestBuilder queryParam(@NonNull String key, @NonNull String value) {
        queryParams.computeIfAbsent(key, _ -> new ArrayList<>()).add(value);
        return this;
    }

    public HttpRequestBuilder pathParam(@NonNull String key, @NonNull String value) {
        pathParams.put(key, value);
        return this;
    }

    public HttpRequestBuilder header(@NonNull String name, @NonNull String value) {
        header.add(name, value);
        return this;
    }

    public HttpRequestBuilder body(@NonNull HttpBody body) {
        this.body = body;
        return this;
    }

    public HttpRequestBuilder body(@NonNull byte[] body) {
        return body(HttpBody.of(body));
    }

    public HttpRequest build() {
        return new HttpRequest(method, path, version, queryParams, pathParams, header, body);
    }
}
