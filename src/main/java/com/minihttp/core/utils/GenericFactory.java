package com.minihttp.core.utils;

public interface GenericFactory<T> {
    T create();

    void reset(T object);

    void destroy(T target);
}
