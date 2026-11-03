package com.kdshop.order.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单详情：主单 + 子单 + 商品快照 + 状态时间线。
 * actions 由服务端算好，前端只渲染，不自行判断状态。
 */
public record OrderDetailView(
        String orderNo,
        String status,
        int goodsAmount,
        int discount,
        int shippingFee,
        int payAmount,
        AddressView address,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime expireAt,
        List<TimelineEntry> timeline,
        List<SubOrderView> subOrders
) {

    public record AddressView(
            String receiver,
            String phone,
            String province,
            String city,
            String district,
            String detail
    ) {
    }

    public record TimelineEntry(String status, LocalDateTime at, String actor) {
    }

    public record ShipmentView(String carrier, String trackingNo) {
    }

    public record ItemView(
            String name,
            Map<String, String> spec,
            int price,
            int quantity,
            int subtotal
    ) {
    }

    public record SubOrderView(
            String subOrderNo,
            Long merchantId,
            String shopName,
            String status,
            int payAmount,
            LocalDateTime shippedAt,
            ShipmentView shipment,
            List<ItemView> items,
            List<String> actions
    ) {
    }
}
