package com.minihttp.repository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    record Order(long id, String item, int quantity, int unitPrice, int totalPrice, String orderedAt) {
    }

    Order save(Order order);

    Optional<Order> findById(long id);

    List<Order> findPage(int page, int pageSize);
}
