package com.kdshop.merchant.dto;

import java.time.LocalDateTime;

public record MerchantView(
        Long id,
        Long userId,
        String shopName,
        String description,
        String contactName,
        String contactPhone,
        String status,
        String rejectReason,
        Integer commissionRate,
        LocalDateTime createdAt,
        LocalDateTime approvedAt
) {
}
