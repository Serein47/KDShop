package com.kdshop.order.dto;

import java.time.LocalDateTime;

/** 订单列表行：只带摘要，不返回商品明细。 */
public record OrderListItem(
        String orderNo,
        String status,
        int payAmount,
        LocalDateTime createdAt,
        int subOrderCount,
        int shippedCount,
        String summary,
        String coverImage,
        LocalDateTime expireAt
) {
}
