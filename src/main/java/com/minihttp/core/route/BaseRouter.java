package com.minihttp.core.route;

public abstract class BaseRouter {
    private final String prefix;

    protected BaseRouter(String prefix) {
        this.prefix = prefix;
    }
}
