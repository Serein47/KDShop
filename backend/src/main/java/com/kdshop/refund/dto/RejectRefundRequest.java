package com.kdshop.refund.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRefundRequest(
        @NotBlank(message = "请填写驳回原因") @Size(max = 200, message = "原因最多 200 字") String reason
) {
}
