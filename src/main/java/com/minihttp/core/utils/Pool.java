package com.minihttp.core.utils;

import java.util.Optional;

public interface Pool<T> {
    Optional<T> tryAcquire();

    void release(T object);

    int remainAmount();

    boolean canAcquire();

    boolean canCreate();
}
