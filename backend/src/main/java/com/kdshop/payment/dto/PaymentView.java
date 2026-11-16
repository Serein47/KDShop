package com.kdshop.payment.dto;

import java.time.LocalDateTime;

/** purpose：FULL 一次付清 / DEPOSIT 定金 / BALANCE 尾款。 */
public record PaymentView(String paymentNo, String orderNo, int amount, String purpose, String status,
                          String payUrl, LocalDateTime expireAt, LocalDateTime paidAt) {
}
