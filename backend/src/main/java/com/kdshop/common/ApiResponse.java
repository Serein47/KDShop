package com.kdshop.common;

/**
 * 统一响应体：{ code, message, data, traceId }。
 * code = 0 表示成功；非 0 时 data 可能携带补充信息（字段错误、可用库存等）。
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, TraceId.current());
    }

    public static <T> ApiResponse<T> fail(ErrorCode error, String message, T data) {
        return new ApiResponse<>(error.code(),
                message == null || message.isBlank() ? error.message() : message,
                data, TraceId.current());
    }
}
