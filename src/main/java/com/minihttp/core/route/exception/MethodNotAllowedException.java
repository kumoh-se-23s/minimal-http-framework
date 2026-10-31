package com.minihttp.core.route.exception;

import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpMethod;
import com.minihttp.packet.protocol.HttpStatus;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MethodNotAllowedException extends HttpStatusException {
    public MethodNotAllowedException(HttpMethod method, String path, Set<HttpMethod> allowedMethods) {
        super(
                HttpStatus.METHOD_NOT_ALLOWED,
                method + " is not allowed for " + path,
                Map.of(HttpHeaderField.ALLOW, MethodNotAllowedException.toAllowValue(allowedMethods))
        );
    }

    private static String toAllowValue(Set<HttpMethod> allowedMethods) {
        return allowedMethods.stream()
                             .map(HttpMethod::name)
                             .collect(Collectors.joining(", "));
    }
}
