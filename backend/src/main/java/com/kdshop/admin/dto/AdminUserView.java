package com.kdshop.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserView(
        Long id,
        String email,
        String nickname,
        String status,
        List<String> roles,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
}
