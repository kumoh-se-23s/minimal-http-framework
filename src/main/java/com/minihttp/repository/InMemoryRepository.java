package com.minihttp.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;
import java.util.function.Predicate;

public abstract class InMemoryRepository<T> {
    private final Map<Long, T> entities = new ConcurrentSkipListMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public T create(LongFunction<T> entityFactory) {
        long id = this.sequence.incrementAndGet();
        T entity = entityFactory.apply(id);
        this.entities.put(id, entity);
        return entity;
    }

    public Optional<T> findById(long id) {
        return Optional.ofNullable(this.entities.get(id));
    }

    public List<T> findAll() {
        return List.copyOf(this.entities.values());
    }

    public List<T> find(Predicate<T> filter) {
        return this.entities.values().stream()
                            .filter(filter)
                            .toList();
    }

    public boolean delete(long id) {
        return this.entities.remove(id) != null;
    }
}
