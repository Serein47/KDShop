package com.kdshop.admin.dto;

public record AdminProductView(
        Long id,
        String name,
        Long merchantId,
        String shopName,
        Long categoryId,
        String categoryName,
        String status,
        String offlineReason,
        String mainImage,
        int priceMin,
        int stock,
        int salesCount
) {
}
