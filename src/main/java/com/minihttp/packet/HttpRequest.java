package com.minihttp.packet;

import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpVersion;
import com.minihttp.packet.structure.HttpBody;
import com.minihttp.packet.structure.HttpHeader;
import lombok.NonNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class HttpRequest extends HttpPacket {
    private final HttpMethod method;
    private final String path;
    private final Map<String, List<String>> queryParam = new LinkedHashMap<>();
    private final Map<String, String> pathParam = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public HttpRequest(@NonNull HttpVersion version,
                       @NonNull HttpHeader header,
                       @NonNull HttpBody body,
                       @NonNull HttpMethod method,
                       @NonNull String path,
                       @NonNull Map<String, List<String>> queryParam,
                       @NonNull Map<String, String> pathParam) {
        super(version, header, body);
        this.method = method;
        this.path = path;
        queryParam.forEach((key, values) -> this.queryParam.put(key, List.copyOf(values)));
        this.pathParam.putAll(pathParam);
    }

    public HttpMethod method() {
        return this.method;
    }

    public String path() {
        return this.path;
    }

    public Optional<String> queryParam(@NonNull String key) {
        return queryParams(key).stream().findFirst();
    }

    public List<String> queryParams(@NonNull String key) {
        return this.queryParam.getOrDefault(key, List.of());
    }

    public Map<String, List<String>> queryParams() {
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

    public boolean keepAlive() {
        List<String> tokens = this.header.getAll("Connection").stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(token -> token.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (tokens.contains("close")) {
            return false;
        }
        if (tokens.contains("keep-alive")) {
            return true;
        }
        return this.version.defaultKeepAlive();
    }

    @Override
    protected String startLine() {
        return this.method.name() + " " + requestTarget() + " " + this.version.token();
    }

    @Override
    protected boolean contentLengthRequired() {
        return this.method == HttpMethod.POST || this.method == HttpMethod.PUT || this.method == HttpMethod.PATCH;
    }

    @Override
    protected void validate() {
        if (this.version == HttpVersion.HTTP_1_1 && !this.header.contains("Host")) {
            throw new IllegalStateException("HTTP/1.1 request requires Host header");
        }
    }

    private String requestTarget() {
        String encodedPath = Arrays.stream(this.path.split("/", -1))
                .map(segment -> urlEncode(segment).replace("+", "%20"))
                .collect(Collectors.joining("/"));

        if (this.queryParam.isEmpty()) {
            return encodedPath;
        }

        return encodedPath + "?" + this.queryParam.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                .map(value -> urlEncode(entry.getKey()) + "=" + urlEncode(value)))
                .collect(Collectors.joining("&"));
    }

    private static String urlEncode(String component) {
        return URLEncoder.encode(component, StandardCharsets.UTF_8);
    }
}
