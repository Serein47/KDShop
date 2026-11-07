package com.kdshop.refund.dto;

import java.time.LocalDateTime;

public record RefundView(
        String refundNo,
        String subOrderNo,
        String orderNo,
        Long merchantId,
        String shopName,
        int amount,
        String reason,
        String status,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime finishedAt
) {
}
