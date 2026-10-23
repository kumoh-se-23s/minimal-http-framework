package com.minihttp.packet;

import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.HttpBody;
import com.minihttp.packet.structure.HttpHeader;
import lombok.NonNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class HttpRequest extends HttpPacket {
    private final HttpMethod method;
    private final String path;
    private final Map<String, String> queryParam = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, String> pathParam = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public HttpRequest(@NonNull HttpVersion version,
                       @NonNull HttpHeader header,
                       @NonNull HttpBody body,
                       @NonNull HttpMethod method,
                       @NonNull String path,
                       @NonNull Map<String, String> queryParam,
                       @NonNull Map<String, String> pathParam) {
        super(version, header, body);
        this.method = method;
        this.path = path;
        this.queryParam.putAll(queryParam);
        this.pathParam.putAll(pathParam);
    }

    public HttpMethod method() {
        return this.method;
    }

    public String path() {
        return this.path;
    }

    public Optional<String> queryParam(@NonNull String key) {
        return Optional.ofNullable(this.queryParam.get(key));
    }

    public Map<String, String> queryParams() {
        return Collections.unmodifiableMap(this.queryParam);
    }

    public Optional<String> pathParam(@NonNull String key) {
        return Optional.ofNullable(this.pathParam.get(key));
    }

    public Map<String, String> pathParams() {
        return Collections.unmodifiableMap(this.pathParam);
    }

    public void pathParam(@NonNull String key, @NonNull String value) {
        this.pathParam.put(key, value);
    }

    @Override
    protected String startLine() {
        return this.method.name() + " " + requestTarget() + " " + this.version.token();
    }

    private String requestTarget() {
        if (this.queryParam.isEmpty()) {
            return this.path;
        }
        return this.path + "?" + this.queryParam.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String component) {
        return URLEncoder.encode(component, StandardCharsets.UTF_8);
    }
}
