package com.kdshop.merchant.dto;

import java.util.List;

public record MerchantProductView(
        Long id,
        String name,
        String subtitle,
        Long categoryId,
        String categoryName,
        String status,
        String mainImage,
        int priceMin,
        int priceMax,
        int stock,
        int salesCount,
        List<Sku> skus
) {
    public record Sku(Long id, int price, int stock, String spec) {
    }
}
