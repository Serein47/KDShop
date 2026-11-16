package com.kdshop.aftersales.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AfterSaleView(
        String asNo,
        String orderNo,
        String subOrderNo,
        Long merchantId,
        String shopName,
        String customerName,
        String type,
        String reasonCode,
        String description,
        List<String> evidence,
        String status,
        LocalDateTime reviewDueAt,
        int refundAmount,
        String refundNo,
        String returnCarrier,
        String returnTrackingNo,
        LocalDateTime returnShippedAt,
        LocalDateTime returnDueAt,
        LocalDateTime receivedAt,
        LocalDateTime inspectDueAt,
        String inspectResult,
        String inspectNote,
        List<String> inspectImages,
        String responsibility,
        String verdictNote,
        String rejectReason,
        int reapplyCount,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        List<ItemView> items,
        List<LogView> logs
) {
    public record ItemView(String name, java.util.Map<String, String> spec, int price, int quantity,
                           int subtotal, boolean flawAck, List<OptionView> options) {
    }

    public record OptionView(Long optionId, String name, String value, int priceDelta) {
    }

    public record LogView(String fromStatus, String toStatus, String operatorType, String note, LocalDateTime at) {
    }
}
