package com.kdshop.order.dto;

import java.util.List;

/** 订单分页结果，结构与商品列表保持一致。 */
public record OrderPage(
        List<OrderListItem> items,
        long total,
        int page,
        int size,
        boolean hasNext
) {
}
