package com.kdshop.merchant.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record SubOrderView(
        String subOrderNo,
        String orderNo,
        String status,
        int goodsAmount,
        int discount,
        int shippingFee,
        int payAmount,
        LocalDateTime createdAt,
        LocalDateTime shippedAt,
        String carrier,
        String trackingNo,
        String receiver,
        String phone,
        String address,
        boolean isGift,
        String giftMessage,
        boolean giftWrap,
        List<Item> items
) {
    public record Item(String name, Map<String, String> spec, int price, int quantity, int subtotal,
                       String image, List<ItemOption> options) {
    }

    public record ItemOption(Long optionId, String name, String value, int priceDelta) {
    }
}
