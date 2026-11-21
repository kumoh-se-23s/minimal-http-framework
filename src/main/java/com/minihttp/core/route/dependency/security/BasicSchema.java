package com.minihttp.core.route.dependency.security;

import com.minihttp.core.route.dependency.Dependency;
import com.minihttp.core.route.exception.HttpStatusException;
import com.minihttp.packet.HttpRequest;
import com.minihttp.packet.protocol.HttpHeaderField;
import com.minihttp.packet.protocol.HttpStatus;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static com.minihttp.core.route.dependency.security.SecureSchemaType.BASIC;

public abstract class BasicSchema<T> implements Dependency<T> {
    private static final Map<HttpHeaderField, String> CHALLENGE_HEADERS =
            Map.of(HttpHeaderField.WWW_AUTHENTICATE, BASIC.getPrefix());

    @Override
    public final T resolve(HttpRequest request) {
        String authData = request.getAuthHeaderField()
                                 .orElseThrow(() -> BasicSchema.createUnauthorizedException("Not authenticated"));

        int schemaSeparatorIndex = authData.indexOf(' ');
        if (schemaSeparatorIndex < 0 || !BASIC.isCorrectSchemaType(authData.substring(0, schemaSeparatorIndex))) {
            throw BasicSchema.createUnauthorizedException("Invalid authentication credentials");
        }

        String encodedCredentials = authData.substring(schemaSeparatorIndex + 1).strip();
        String credentials = BasicSchema.decodeCredentials(encodedCredentials);

        int credentialSeparatorIndex = credentials.indexOf(':');
        if (credentialSeparatorIndex < 0 || credentials.chars().anyMatch(Character::isISOControl)) {
            throw BasicSchema.createUnauthorizedException("Invalid authentication credentials");
        }

        String username = credentials.substring(0, credentialSeparatorIndex);
        String password = credentials.substring(credentialSeparatorIndex + 1);

        return this.authenticate(username, password);
    }

    protected abstract T authenticate(String username, String password);

    private static String decodeCredentials(String encodedCredentials) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encodedCredentials);

            return StandardCharsets.UTF_8.newDecoder()
                                         .onMalformedInput(CodingErrorAction.REPORT)
                                         .onUnmappableCharacter(CodingErrorAction.REPORT)
                                         .decode(ByteBuffer.wrap(decodedBytes))
                                         .toString();
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            throw BasicSchema.createUnauthorizedException("Invalid authentication credentials");
        }
    }

    private static HttpStatusException createUnauthorizedException(String detail) {
        return new HttpStatusException(HttpStatus.UNAUTHORIZED, detail, BasicSchema.CHALLENGE_HEADERS);
    }
}
