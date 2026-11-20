package com.minihttp.auth;

import com.minihttp.infra.AppSettings;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

public final class TokenProvider {
    private static final String ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = ".";

    private final SecretKeySpec key;

    public TokenProvider(AppSettings appSettings) {
        this.key = new SecretKeySpec(appSettings.tokenSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String createAccessToken(long userId) {
        return userId + SEPARATOR + this.sign(String.valueOf(userId));
    }

    public Optional<Long> getUserId(String token) {
        int separatorIndex = token.indexOf(SEPARATOR);

        if (separatorIndex <= 0) {
            return Optional.empty();
        }

        String userIdText = token.substring(0, separatorIndex);
        byte[] expectedSignature = this.sign(userIdText).getBytes(StandardCharsets.UTF_8);
        byte[] actualSignature = token.substring(separatorIndex + 1).getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Long.parseLong(userIdText));

        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(this.key);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to sign token", exception);
        }
    }
}
