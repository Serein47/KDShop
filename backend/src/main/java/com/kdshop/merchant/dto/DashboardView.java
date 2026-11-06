package com.kdshop.merchant.dto;

import java.util.List;

public record DashboardView(
        String shopName,
        String status,
        int onSaleProducts,
        int pendingShipCount,
        int shippedCount,
        int totalSales,
        int todayOrders,
        int todaySales,
        List<TrendPoint> trend
) {
    public record TrendPoint(String date, int orders, int amount) {
    }
}
