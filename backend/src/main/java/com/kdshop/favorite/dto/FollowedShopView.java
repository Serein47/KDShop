package com.kdshop.favorite.dto;

public record FollowedShopView(
        Long merchantId,
        String shopName,
        String logo,
        String description,
        int productCount
) {
}
