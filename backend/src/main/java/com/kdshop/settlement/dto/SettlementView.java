package com.kdshop.settlement.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SettlementView(
        String settlementNo,
        Long merchantId,
        String shopName,
        LocalDate periodStart,
        LocalDate periodEnd,
        int grossAmount,
        int commission,
        int refund,
        int netAmount,
        String status,
        int orderCount,
        LocalDateTime settledAt,
        LocalDateTime createdAt
) {
}
