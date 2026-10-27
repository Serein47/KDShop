package com.kdshop.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(@NotBlank(message = "令牌不能为空") String token) {
}
