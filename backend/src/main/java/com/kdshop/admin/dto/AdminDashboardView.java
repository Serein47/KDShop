package com.kdshop.admin.dto;

import java.util.List;

public record AdminDashboardView(
        int gmv,
        int commission,
        int orderCount,
        int paidOrderCount,
        int merchantActive,
        int merchantPending,
        int customerCount,
        int productOnSale,
        List<TrendPoint> trend
) {
    public record TrendPoint(String date, int orders, int amount) {
    }
}
