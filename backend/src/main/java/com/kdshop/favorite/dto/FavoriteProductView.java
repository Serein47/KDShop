package com.kdshop.favorite.dto;

public record FavoriteProductView(
        Long productId,
        String name,
        String subtitle,
        String mainImage,
        int priceMin,
        int salesCount,
        Long merchantId,
        String shopName
) {
}
