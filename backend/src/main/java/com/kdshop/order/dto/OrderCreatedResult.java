package com.kdshop.order.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OrderCreatedResult(
        String orderNo,
        String status,
        int payAmount,
        LocalDateTime expireAt,
        List<SubOrder> subOrders
) {
    public record SubOrder(String subOrderNo, Long merchantId, String shopName, int payAmount) {
    }
}
