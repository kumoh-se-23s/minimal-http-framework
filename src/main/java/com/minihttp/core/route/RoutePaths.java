package com.minihttp.core.route;

import lombok.NonNull;

public final class RoutePaths {
    private static final String ROOT = "/";

    private RoutePaths() {
    }

    public static String join(@NonNull String prefix, @NonNull String path) {
        String normalizedPrefix = RoutePaths.normalize(prefix);
        String normalizedPath = RoutePaths.normalize(path);

        if (normalizedPrefix.equals(ROOT)) {
            return normalizedPath;
        }

        if (normalizedPath.equals(ROOT)) {
            return normalizedPrefix;
        }

        return normalizedPrefix + normalizedPath;
    }

    public static String normalize(@NonNull String path) {
        String collapsedPath = ("/" + path.strip()).replaceAll("/{2,}", "/");

        if (collapsedPath.length() > 1 && collapsedPath.endsWith("/")) {
            return collapsedPath.substring(0, collapsedPath.length() - 1);
        }

        return collapsedPath;
    }
}
