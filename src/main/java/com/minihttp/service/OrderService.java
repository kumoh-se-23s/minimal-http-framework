package com.minihttp.service;

import com.minihttp.core.route.exception.HttpStatusException;
import com.minihttp.infra.AppSettings;
import com.minihttp.infra.AuditLog;
import com.minihttp.infra.IdGenerator;
import com.minihttp.infra.NotificationSender;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.repository.OrderRepository;
import com.minihttp.repository.OrderRepository.Order;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderService {
    private final OrderRepository orderRepository;
    private final IdGenerator idGenerator;
    private final PriceCalculator priceCalculator;
    private final NotificationSender notificationSender;
    private final AuditLog auditLog;
    private final AppSettings appSettings;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository,
                        IdGenerator idGenerator,
                        PriceCalculator priceCalculator,
                        NotificationSender notificationSender,
                        AuditLog auditLog,
                        AppSettings appSettings,
                        Clock clock) {
        this.orderRepository = orderRepository;
        this.idGenerator = idGenerator;
        this.priceCalculator = priceCalculator;
        this.notificationSender = notificationSender;
        this.auditLog = auditLog;
        this.appSettings = appSettings;
        this.clock = clock;
    }

    public Order place(String item, int quantity, int unitPrice) {
        if (quantity <= 0 || unitPrice < 0) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "quantity must be positive and unitPrice must not be negative");
        }

        Order order = this.orderRepository.save(new Order(
                this.idGenerator.nextId(),
                item,
                quantity,
                unitPrice,
                this.priceCalculator.calculate(unitPrice, quantity),
                LocalDateTime.now(this.clock).toString()
        ));

        this.auditLog.record("placed order " + order.id());
        this.notificationSender.send("order " + order.id() + " placed: " + item + " x" + quantity + " = " + order.totalPrice());
        return order;
    }

    public Order get(long orderId) {
        return this.orderRepository.findById(orderId)
                                   .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    public List<Order> list(int page) {
        if (page <= 0) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "page must be positive");
        }

        return this.orderRepository.findPage(page, this.appSettings.pageSize());
    }
}
