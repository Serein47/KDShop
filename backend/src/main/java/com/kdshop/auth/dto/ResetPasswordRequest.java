package com.kdshop.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "令牌不能为空") String token,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 之间")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码需同时包含字母与数字")
        String newPassword
) {
}
