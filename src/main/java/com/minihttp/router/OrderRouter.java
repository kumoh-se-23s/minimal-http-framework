package com.minihttp.router;

import com.minihttp.core.route.BaseRouter;
import com.minihttp.core.route.annotation.Depends;
import com.minihttp.core.route.annotation.Get;
import com.minihttp.core.route.annotation.Post;
import com.minihttp.core.route.annotation.Query;
import com.minihttp.infra.AppSettings;
import com.minihttp.infra.AuditLog;
import com.minihttp.packet.protocol.HttpStatus;
import com.minihttp.repository.OrderRepository.Order;
import com.minihttp.service.OrderService;

import java.util.List;

public class OrderRouter extends BaseRouter {
    public record CreateOrderRequest(String item, int quantity, int unitPrice) {
    }

    public record AppInfo(String appName, int pageSize) {
    }

    public record AuditReport(String requestedBy, List<String> entries) {
    }

    private final OrderService orderService;
    private final AuditLog auditLog;
    private final AppSettings appSettings;

    public OrderRouter(OrderService orderService, AuditLog auditLog, AppSettings appSettings) {
        super("/orders");
        this.orderService = orderService;
        this.auditLog = auditLog;
        this.appSettings = appSettings;
    }

    @Get
    public List<Order> list(@Query(defaultValue = "1") int page) {
        return this.orderService.list(page);
    }

    @Get("/{orderId}")
    public Order get(long orderId) {
        return this.orderService.get(orderId);
    }

    @Post(status = HttpStatus.CREATED)
    public Order place(CreateOrderRequest request) {
        return this.orderService.place(request.item(), request.quantity(), request.unitPrice());
    }

    @Get("/info")
    public AppInfo info() {
        return new AppInfo(this.appSettings.appName(), this.appSettings.pageSize());
    }

    @Get("/audit")
    public AuditReport audit(@Depends(ApiKeyAuthentication.class) String requestedBy) {
        return new AuditReport(requestedBy, this.auditLog.entries());
    }
}
