package com.kdshop.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        Long addressId,
        Long couponId,
        @Valid List<Item> items,
        @Size(max = 200, message = "备注最多 200 字") String remark,
        Boolean isGift,
        @Size(max = 100, message = "贺卡内容最多 100 字") String giftMessage,
        Boolean giftWrap
) {
    public record Item(@NotNull Long skuId, @NotNull @Min(1) Integer quantity) {
    }
}
