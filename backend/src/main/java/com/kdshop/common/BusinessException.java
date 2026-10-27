package com.kdshop.common;

/** 业务异常：携带错误码与可选补充数据，由全局处理器转成统一响应。 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object data;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    public ErrorCode errorCode() { return errorCode; }
    public Object data() { return data; }
}
