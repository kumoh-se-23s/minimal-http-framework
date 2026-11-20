package com.minihttp.infra;

public record AppSettings(String appName, int pageSize, String tokenSecret) {
    public AppSettings {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive: " + pageSize);
        }

        if (tokenSecret.isBlank()) {
            throw new IllegalArgumentException("tokenSecret must not be blank");
        }
    }
}
