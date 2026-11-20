package com.minihttp.auth;

import com.minihttp.core.route.dependency.Dependency;
import com.minihttp.core.route.exception.HttpStatusException;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.protocol.HttpStatus;

public final class CurrentUser implements Dependency<Long> {
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenProvider tokenProvider;

    public CurrentUser(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Long resolve(HttpRequest request) {
        String authorization = request.header().get("Authorization")
                                      .orElseThrow(() -> new HttpStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));

        String token = authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length())
                : authorization;

        return this.tokenProvider.getUserId(token.strip())
                                 .orElseThrow(() -> new HttpStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));
    }
}
