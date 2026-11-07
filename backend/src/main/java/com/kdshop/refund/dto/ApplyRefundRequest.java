package com.kdshop.refund.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyRefundRequest(
        @NotBlank(message = "子单号不能为空") String subOrderNo,
        @NotBlank(message = "请填写退款原因") @Size(max = 200, message = "原因最多 200 字") String reason
) {
}
