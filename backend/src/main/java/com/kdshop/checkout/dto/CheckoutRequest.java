package com.kdshop.checkout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * items 为空时，使用购物车里已勾选的行。
 */
public record CheckoutRequest(
        Long addressId,
        Long couponId,
        @Valid List<Item> items
) {
    public record Item(@NotNull Long skuId, @NotNull @Min(1) Integer quantity) {
    }
}
