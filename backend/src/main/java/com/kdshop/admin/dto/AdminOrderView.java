package com.kdshop.admin.dto;

import java.time.LocalDateTime;

public record AdminOrderView(
        String orderNo,
        Long userId,
        String nickname,
        String status,
        int merchantCount,
        int goodsAmount,
        int payAmount,
        LocalDateTime createdAt,
        LocalDateTime paidAt
) {
}
