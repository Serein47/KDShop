package com.kdshop.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShipRequest(
        @NotBlank(message = "请选择承运商") @Size(max = 32) String carrier,
        @NotBlank(message = "请填写运单号") @Size(max = 64) String trackingNo
) {
}
