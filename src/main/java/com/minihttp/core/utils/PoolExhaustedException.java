package com.minihttp.core.utils;

public class PoolExhaustedException extends RuntimeException {

    public PoolExhaustedException() {
        super("Pool is exhausted: no available objects to lend.");
    }

    public PoolExhaustedException(String message) {
        super(message);
    }

    public PoolExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}