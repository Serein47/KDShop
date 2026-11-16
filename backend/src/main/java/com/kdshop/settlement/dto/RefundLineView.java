package com.kdshop.settlement.dto;

import java.time.LocalDateTime;

public record RefundLineView(String refundNo, int amount, String reason, LocalDateTime finishedAt) {
}
