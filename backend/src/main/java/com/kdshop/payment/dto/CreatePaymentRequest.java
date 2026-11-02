package com.kdshop.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentRequest(@NotBlank(message = "订单号不能为空") String orderNo) {
}
