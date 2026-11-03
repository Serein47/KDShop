package com.kdshop.order;

import com.kdshop.common.ApiResponse;
import com.kdshop.order.dto.CreateOrderRequest;
import com.kdshop.order.dto.OrderCancelResult;
import com.kdshop.order.dto.OrderCreatedResult;
import com.kdshop.order.dto.OrderDetailView;
import com.kdshop.order.dto.OrderPage;
import com.kdshop.order.dto.SubOrderConfirmResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService queryService;
    private final OrderActionService actionService;

    public OrderController(OrderService orderService, OrderQueryService queryService,
                           OrderActionService actionService) {
        this.orderService = orderService;
        this.queryService = queryService;
        this.actionService = actionService;
    }

    @PostMapping
    public ApiResponse<OrderCreatedResult> create(@Valid @RequestBody CreateOrderRequest request,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(orderService.create(request, idempotencyKey));
    }

    @GetMapping
    public ApiResponse<OrderPage> list(@RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(queryService.list(status, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDetailView> detail(@PathVariable String orderNo) {
        return ApiResponse.ok(queryService.detail(orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderCancelResult> cancel(@PathVariable String orderNo) {
        return ApiResponse.ok(actionService.cancel(orderNo));
    }

    @PostMapping("/{orderNo}/sub-orders/{subOrderNo}/confirm")
    public ApiResponse<SubOrderConfirmResult> confirm(@PathVariable String orderNo,
                                                      @PathVariable String subOrderNo) {
        return ApiResponse.ok(actionService.confirm(orderNo, subOrderNo));
    }
}
