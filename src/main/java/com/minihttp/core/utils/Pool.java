package com.minihttp.core.utils;

public interface Pool<T> {
    T acquire();

    void release(T object);

    int remainAmount();

    boolean canAcquire();

    boolean canCreate();
}
