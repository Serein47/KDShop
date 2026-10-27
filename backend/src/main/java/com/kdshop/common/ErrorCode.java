package com.kdshop.common;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。分段：10xxx 参数、20xxx 认证、30xxx 权限、40xxx 业务、50xxx 系统。
 * 与 docs/error-codes.md 一一对应，新增错误码先改文档。
 */
public enum ErrorCode {

    OK(0, "ok", HttpStatus.OK),

    PARAM_INVALID(10001, "参数校验失败", HttpStatus.BAD_REQUEST),
    BODY_MALFORMED(10002, "请求体不是合法 JSON", HttpStatus.BAD_REQUEST),
    PAGE_OUT_OF_RANGE(10003, "分页参数越界", HttpStatus.BAD_REQUEST),
    ENDPOINT_NOT_FOUND(10004, "接口不存在", HttpStatus.NOT_FOUND),

    UNAUTHENTICATED(20001, "未登录", HttpStatus.UNAUTHORIZED),
    SESSION_EXPIRED(20002, "会话已过期", HttpStatus.UNAUTHORIZED),
    BAD_CREDENTIALS(20003, "账号或密码错误", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(20004, "连续失败，账号临时锁定", HttpStatus.FORBIDDEN),
    EMAIL_NOT_VERIFIED(20005, "邮箱未验证", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(20006, "账号已禁用", HttpStatus.FORBIDDEN),
    TOKEN_INVALID(20007, "链接无效或已使用", HttpStatus.BAD_REQUEST),

    FORBIDDEN(30001, "无权限", HttpStatus.FORBIDDEN),
    RESOURCE_FORBIDDEN(30002, "无权访问该资源", HttpStatus.FORBIDDEN),
    MERCHANT_SCOPE_FORBIDDEN(30003, "访问了非本店资源", HttpStatus.FORBIDDEN),

    PRODUCT_NOT_FOUND(40001, "商品不存在或已下架", HttpStatus.NOT_FOUND),
    STOCK_INSUFFICIENT(40002, "库存不足", HttpStatus.CONFLICT),
    CART_EMPTY(40003, "购物车为空", HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND(40004, "订单不存在", HttpStatus.NOT_FOUND),
    ORDER_STATE_INVALID(40005, "当前状态不允许该操作", HttpStatus.CONFLICT),
    DUPLICATE_SUBMIT(40006, "重复提交", HttpStatus.CONFLICT),
    PAYMENT_NOT_FOUND(40007, "支付单不存在", HttpStatus.NOT_FOUND),
    SIGNATURE_INVALID(40008, "回调验签失败", HttpStatus.BAD_REQUEST),
    AMOUNT_MISMATCH(40009, "金额不一致", HttpStatus.CONFLICT),
    REFUND_STATE_INVALID(40010, "退款单状态不允许", HttpStatus.CONFLICT),
    COUPON_UNAVAILABLE(40011, "优惠券不可用", HttpStatus.BAD_REQUEST),
    COUPON_ALREADY_CLAIMED(40012, "已领取过", HttpStatus.CONFLICT),
    REVIEW_EXISTS(40013, "该订单项已评价", HttpStatus.CONFLICT),
    ADDRESS_INVALID(40014, "地址缺失或不属于本人", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_USED(40015, "邮箱已注册", HttpStatus.CONFLICT),
    MERCHANT_NOT_APPROVED(40016, "店铺未通过审核", HttpStatus.FORBIDDEN),
    MERCHANT_SUSPENDED(40017, "店铺已封禁", HttpStatus.FORBIDDEN),
    MERCHANT_APPLY_DUPLICATE(40018, "已有待审核的入驻申请", HttpStatus.CONFLICT),
    SUB_ORDER_STATE_INVALID(40019, "子单当前状态不允许该操作", HttpStatus.CONFLICT),
    SETTLEMENT_EXISTS(40020, "该商家该周期已生成结算单", HttpStatus.CONFLICT),
    SETTLEMENT_NOT_ELIGIBLE(40021, "子单未满足结算条件", HttpStatus.CONFLICT),

    INTERNAL_ERROR(50001, "系统繁忙，请稍后再试", HttpStatus.INTERNAL_SERVER_ERROR),
    UPSTREAM_TIMEOUT(50002, "下游服务超时", HttpStatus.GATEWAY_TIMEOUT);

    private final int code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(int code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public int code() { return code; }
    public String message() { return message; }
    public HttpStatus status() { return status; }
}
