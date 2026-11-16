package com.kdshop.aftersales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReturnShipmentRequest(
        @NotBlank(message = "请填写快递公司") @Size(max = 32) String carrier,
        @NotBlank(message = "请填写运单号") @Size(max = 64) String trackingNo
) {
}
