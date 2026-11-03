package com.kdshop.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 邮件投递配置：本地 SMTP 到 Mailpit，生产默认 OutboxOnly。 */
@ConfigurationProperties(prefix = "kdshop.mail")
public record MailProperties(
        String mode,
        String from,
        String fromName,
        String baseUrl,
        int maxAttempts,
        int retryDelaySeconds,
        int batchSize
) {
}
