package com.kdshop.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MockCompleteRequest(
        @NotBlank(message = "支付单号不能为空") String paymentNo,
        @NotNull(message = "支付结果不能为空") Boolean success
) {
}
