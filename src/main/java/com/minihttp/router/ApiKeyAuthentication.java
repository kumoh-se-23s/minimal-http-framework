package com.minihttp.router;

import com.minihttp.core.route.dependency.Dependency;
import com.minihttp.core.route.exception.HttpStatusException;
import com.minihttp.infra.AppSettings;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.protocol.HttpStatus;

public final class ApiKeyAuthentication implements Dependency<String> {
    private static final String API_KEY_HEADER = "X-Api-Key";

    private final AppSettings appSettings;

    public ApiKeyAuthentication(AppSettings appSettings) {
        this.appSettings = appSettings;
    }

    @Override
    public String resolve(HttpRequest request) {
        String apiKey = request.header().get(API_KEY_HEADER)
                               .orElseThrow(() -> new HttpStatusException(HttpStatus.UNAUTHORIZED, "Missing " + API_KEY_HEADER));

        if (!apiKey.equals(this.appSettings.apiKey())) {
            throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Invalid " + API_KEY_HEADER);
        }

        return "admin";
    }
}
