package com.kdshop.settlement.dto;

import java.time.LocalDateTime;

public record PenaltyLineView(String penaltyNo, int daysOverdue, int amount, LocalDateTime createdAt) {
}
