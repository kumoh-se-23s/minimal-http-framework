package com.minihttp.controller.external;

import com.minihttp.auth.TokenProvider;
import com.minihttp.core.route.BaseRouter;
import com.minihttp.core.route.annotation.Post;

public class AuthRouter extends BaseRouter {
    public record TokenRequest(long userId) {
    }

    public record TokenResponse(String accessToken) {
    }

    private final TokenProvider tokenProvider;

    public AuthRouter(TokenProvider tokenProvider) {
        super("/auth");
        this.tokenProvider = tokenProvider;
    }

    @Post("/token")
    public TokenResponse issueToken(TokenRequest request) {
        return new TokenResponse(this.tokenProvider.createAccessToken(request.userId()));
    }
}
