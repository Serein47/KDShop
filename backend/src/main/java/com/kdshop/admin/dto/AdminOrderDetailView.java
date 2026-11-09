package com.kdshop.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminOrderDetailView(
        String orderNo,
        Long userId,
        String nickname,
        String status,
        int goodsAmount,
        int discount,
        int shippingFee,
        int payAmount,
        String receiver,
        String phone,
        String address,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        List<Sub> subOrders,
        List<Refund> refunds
) {
    public record Sub(
            String subOrderNo,
            Long merchantId,
            String shopName,
            String status,
            int payAmount,
            int refundedAmount,
            String carrier,
            String trackingNo,
            LocalDateTime shippedAt
    ) {
    }

    public record Refund(String refundNo, String subOrderNo, int amount, String status,
                         String reason, String rejectReason, LocalDateTime createdAt) {
    }
}
