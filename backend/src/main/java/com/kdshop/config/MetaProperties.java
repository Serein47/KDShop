package com.kdshop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 前端启动配置：货币、时区、运费与各类时限。 */
@ConfigurationProperties(prefix = "kdshop.meta")
public record MetaProperties(
        String currency,
        String timezone,
        int shippingFreeThresholdCent,
        int shippingFeeCent,
        int payTimeoutMinutes,
        int autoConfirmDays,
        int settlementProtectionDays
) {
}
