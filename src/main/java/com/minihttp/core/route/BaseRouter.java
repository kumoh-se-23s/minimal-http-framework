package com.minihttp.core.route;

import lombok.NonNull;

public abstract class BaseRouter {
    private final String prefix;

    protected BaseRouter() {
        this("");
    }

    protected BaseRouter(@NonNull String prefix) {
        this.prefix = RoutePaths.normalize(prefix);
    }

    public final String prefix() {
        return this.prefix;
    }
}
