package com.kdshop.common;

import org.slf4j.MDC;

import java.util.UUID;

/** traceId 存放在 MDC，由 TraceIdFilter 写入，响应体与日志共用。 */
public final class TraceId {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Request-Id";

    private TraceId() {
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null ? "unknown" : value;
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
