package com.kdshop.payment.dto;

import java.time.LocalDateTime;

public record PaymentView(String paymentNo, String orderNo, int amount, String status,
                          String payUrl, LocalDateTime expireAt, LocalDateTime paidAt) {
}
