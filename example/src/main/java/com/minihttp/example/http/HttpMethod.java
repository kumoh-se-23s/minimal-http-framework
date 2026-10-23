package com.minihttp.example.http;

public enum HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS;

    public static HttpMethod from(String token) {
        for (HttpMethod method : values()) {
            if (method.name().equals(token)) {
                return method;
            }
        }
        throw new HttpParseException(HttpStatus.NOT_IMPLEMENTED, "Unsupported method: " + token);
    }
}
