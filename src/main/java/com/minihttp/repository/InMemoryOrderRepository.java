package com.minihttp.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

public final class InMemoryOrderRepository implements OrderRepository {
    private final Map<Long, Order> orders = new ConcurrentSkipListMap<>();

    @Override
    public Order save(Order order) {
        this.orders.put(order.id(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(long id) {
        return Optional.ofNullable(this.orders.get(id));
    }

    @Override
    public List<Order> findPage(int page, int pageSize) {
        return this.orders.values().stream()
                          .skip((long) (page - 1) * pageSize)
                          .limit(pageSize)
                          .toList();
    }
}
