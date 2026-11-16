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
        List<LogView> logs
) {
    public record LogView(String fromStatus, String toStatus, String operatorType, String note, LocalDateTime at) {
    }
}
