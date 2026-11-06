package com.kdshop.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ProductStatusRequest(
        @NotBlank @Pattern(regexp = "ON|OFF", message = "状态只能是 ON 或 OFF") String status
) {
}
