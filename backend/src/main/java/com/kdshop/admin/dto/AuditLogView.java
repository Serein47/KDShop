package com.kdshop.admin.dto;

import java.time.LocalDateTime;

public record AuditLogView(
        Long id,
        String actorType,
        Long actorId,
        String action,
        String targetType,
        String targetId,
        String beforeJson,
        String afterJson,
        String ip,
        LocalDateTime createdAt
) {
}
