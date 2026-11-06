package com.kdshop.merchant.dto;

import java.time.LocalDateTime;

public record ShopProfileView(
        Long id,
        String shopName,
        String description,
        String logo,
        String contactName,
        String contactPhone,
        String status,
        Integer commissionRate,
        LocalDateTime createdAt
) {
}
