package com.kdshop.coupon.dto;

import java.time.LocalDateTime;

public record CouponView(
        Long couponId,
        String name,
        String type,
        int value,
        int minAmount,
        int remaining,
        boolean claimed,
        LocalDateTime expiresAt
) {
}
