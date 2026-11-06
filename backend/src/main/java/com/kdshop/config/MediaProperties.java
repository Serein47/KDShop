package com.kdshop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 图片存储：MinIO 兼容 S3。 */
@ConfigurationProperties(prefix = "kdshop.media")
public record MediaProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket
) {
}
