package com.kdshop.checkout.dto;

import java.util.List;
import java.util.Map;

public record CheckoutPreview(
        List<SubOrder> subOrders,
        Summary summary,
        Coupons coupons,
        AddressView address
) {
    public record SubOrder(
            Long merchantId,
            String shopName,
            List<Line> items,
            int goodsAmount,
            int shippingFee,
            int discount,
            int payAmount
    ) {
    }

    public record Line(
            Long skuId,
            Long productId,
            String name,
            String image,
            Map<String, String> spec,
            int price,
            int quantity,
            int subtotal
    ) {
    }

    /**
     * @param orderType NORMAL / PREORDER / CUSTOM；预售与定制整单只付定金
     * @param deposit   本次应付定金（分）；普通订单为 0
     * @param balance   尾款（分）；普通订单为 0
     */
    public record Summary(int goodsAmount, int discount, int shippingFee, int payAmount,
                          String orderType, int deposit, int balance) {
    }

    public record Coupons(List<CouponView> usable, List<Unusable> unusable) {
    }

    public record CouponView(Long userCouponId, Long couponId, String name, String type,
                             int value, int minAmount, String expiresAt) {
    }

    public record Unusable(Long couponId, String name, String reason) {
    }

    public record AddressView(String receiver, String phone, String province, String city,
                              String district, String detail) {
    }
}
