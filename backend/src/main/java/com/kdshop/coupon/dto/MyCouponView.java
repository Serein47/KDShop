package com.kdshop.coupon.dto;

import java.time.LocalDateTime;

public record MyCouponView(
        Long userCouponId,
        Long couponId,
        String name,
        String type,
        int value,
        int minAmount,
        String status,
        LocalDateTime expiresAt,
        LocalDateTime usedAt
) {
}
