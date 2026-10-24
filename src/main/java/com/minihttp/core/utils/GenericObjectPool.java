package com.minihttp.core.utils;

import lombok.NonNull;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;


public final class GenericObjectPool<T> implements Pool<T> {
    public static final int DEFAULT_POOL_SIZE = 16;
    public static final int DEFAULT_POOL_MAX = 32;

    private final GenericFactory<T> factory;
    private final int size;
    private final int maxSize;

    private final Object lock = new Object();

    private final Deque<T> idle = new ArrayDeque<>();
    private final Set<T> borrowed = Collections.newSetFromMap(new IdentityHashMap<>());

    private int pendingCreations = 0;

    public GenericObjectPool(Supplier<GenericFactory<T>> factorySupplier) {
        this(List.of(), DEFAULT_POOL_SIZE, DEFAULT_POOL_MAX, factorySupplier);
    }

    public GenericObjectPool(List<T> manualPool, Supplier<GenericFactory<T>> factorySupplier) {
        this(manualPool, DEFAULT_POOL_SIZE, DEFAULT_POOL_MAX, factorySupplier);
    }

    public GenericObjectPool(List<T> manualPool, int size, Supplier<GenericFactory<T>> factorySupplier) {
        this(manualPool, size, size * 2, factorySupplier);
    }

    private GenericObjectPool(List<T> manualPool, int size, int maxSize, Supplier<GenericFactory<T>> factorySupplier) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        this.size = size;
        this.maxSize = maxSize;
        this.factory = Objects.requireNonNull(factorySupplier.get(), "factory supplier returned null");
        this.idle.addAll(manualPool);
    }

    @Override
    public T acquire() {
        synchronized (this.lock) {
            if (this.borrowed.size() + this.pendingCreations >= this.maxSize) {
                throw new PoolExhaustedException();
            }

            T reused = this.idle.pollLast();
            if (reused != null) {
                this.borrowed.add(reused);
                return reused;
            }

            this.pendingCreations++;
        }

        T created = null;
        try {
            created = Objects.requireNonNull(this.factory.create(), "factory.create() returned null");
            return created;
        } finally {
            synchronized (this.lock) {
                this.pendingCreations--;
                if (created != null) {
                    this.borrowed.add(created);
                }
            }
        }
    }

    @Override
    public void release(@NonNull T object) {
        synchronized (this.lock) {
            if (!this.borrowed.remove(object)) {
                throw new IllegalStateException("object was not borrowed from this pool (or already released)");
            }
        }

        try {
            this.factory.reset(object);
        } catch (RuntimeException | Error throwable) {
            this.factory.destroy(object);
            throw throwable;
        }

        boolean keep;
        synchronized (this.lock) {
            keep = this.idle.size() < this.size;
            if (keep) {
                this.idle.addLast(object);
            }
        }
        if (!keep) {
            this.factory.destroy(object);
        }
    }

    @Override
    public int remainAmount() {
        synchronized (this.lock) {
            return this.maxSize - this.borrowed.size() - this.pendingCreations;
        }
    }

    @Override
    public boolean canAcquire() {
        return this.remainAmount() > 0;
    }


    @Override
    public boolean canCreate() {
        synchronized (this.lock) {
            return this.idle.isEmpty() && this.borrowed.size() + this.pendingCreations < this.maxSize;
        }
    }
}
