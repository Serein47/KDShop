package com.kdshop.aftersales.dto;

import jakarta.validation.constraints.Size;

public record ReviewAfterSaleRequest(
        boolean approve,
        @Size(max = 200, message = "驳回原因最多 200 字") String reason
) {
}
