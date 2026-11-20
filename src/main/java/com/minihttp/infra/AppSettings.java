package com.minihttp.infra;

public record AppSettings(String appName, int pageSize, String apiKey) {
    public AppSettings {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be positive: " + pageSize);
        }
    }
}
