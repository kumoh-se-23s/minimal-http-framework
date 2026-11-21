package com.minihttp.core.route.dependency.security;

import com.minihttp.core.route.dependency.Dependency;
import com.minihttp.core.route.exception.HttpStatusException;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpStatus;

import java.util.Map;
import java.util.regex.Pattern;

import static com.minihttp.core.route.dependency.security.SecureSchemaType.BEARER;

public abstract class BearerSchema<T> implements Dependency<T> {
    private static final Map<HttpHeaderField, String> CHALLENGE_HEADERS =
            Map.of(HttpHeaderField.WWW_AUTHENTICATE, BEARER.getPrefix());

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9\\-._~+/]+=*");

    @Override
    public final T resolve(HttpRequest request) {
        String authData = request.getAuthHeaderField()
                                 .orElseThrow(() -> BearerSchema.createUnauthorizedException("Not authenticated"));

        int schemaSeparatorIndex = authData.indexOf(' ');
        if (schemaSeparatorIndex < 0 || !BEARER.isCorrectSchemaType(authData.substring(0, schemaSeparatorIndex))) {
            throw BearerSchema.createUnauthorizedException("Invalid authentication credentials");
        }

        String token = authData.substring(schemaSeparatorIndex + 1).strip();
        if (!BearerSchema.TOKEN_PATTERN.matcher(token).matches()) {
            throw BearerSchema.createUnauthorizedException("Invalid authentication credentials");
        }

        return this.authenticate(token);
    }

    protected abstract T authenticate(String token);

    private static HttpStatusException createUnauthorizedException(String detail) {
        return new HttpStatusException(HttpStatus.UNAUTHORIZED, detail, BearerSchema.CHALLENGE_HEADERS);
    }
}
