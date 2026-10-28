package com.kdshop.cart.dto;

import java.util.Map;

public record CartItemView(
        Long id,
        Long skuId,
        Long productId,
        String name,
        Map<String, String> spec,
        String image,
        Integer price,
        Integer quantity,
        Integer stock,
        Boolean selected,
        Integer subtotal
) {
}
