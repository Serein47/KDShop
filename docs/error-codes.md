# 错误码与统一响应（G0）

## 响应体

```json
{ "code": 0, "message": "ok", "data": { }, "traceId": "9f2c1a" }
```

- `code` 为 0 表示成功；非 0 时 `data` 为 `null`
- HTTP 状态码与 `code` 并存：HTTP 表达传输语义，`code` 表达业务语义
- `traceId` 每次请求唯一，前端报错提示里带上，便于查日志

## 错误码分段

| 段 | 含义 | HTTP |
|---|---|---|
| 0 | 成功 | 200 |
| 10xxx | 参数与请求格式 | 400 |
| 20xxx | 认证与会话 | 401 / 403 |
| 30xxx | 权限 | 403 |
| 40xxx | 业务规则 | 400 / 404 / 409 |
| 50xxx | 系统与下游 | 500 / 504 |

## 明细

| code | 常量 | 含义 | HTTP |
|---|---|---|---|
| 0 | OK | 成功 | 200 |
| 10001 | PARAM_INVALID | 参数校验失败，`data` 返回字段级错误 | 400 |
| 10002 | BODY_MALFORMED | 请求体不是合法 JSON | 400 |
| 10003 | PAGE_OUT_OF_RANGE | 分页参数越界 | 400 |
| 10004 | ENDPOINT_NOT_FOUND | 接口不存在 | 404 |
| 20001 | UNAUTHENTICATED | 未登录 | 401 |
| 20002 | SESSION_EXPIRED | 会话已过期 | 401 |
| 20003 | BAD_CREDENTIALS | 账号或密码错误 | 401 |
| 20004 | ACCOUNT_LOCKED | 连续失败，账号临时锁定 | 403 |
| 20005 | EMAIL_NOT_VERIFIED | 邮箱未验证 | 403 |
| 20006 | ACCOUNT_DISABLED | 账号已禁用 | 403 |
| 20007 | TOKEN_INVALID | 验证/重置链接无效或已用 | 400 |
| 30001 | FORBIDDEN | 无权限 | 403 |
| 30002 | RESOURCE_FORBIDDEN | 越权访问他人资源 | 403 |
| 40001 | PRODUCT_NOT_FOUND | 商品不存在或已下架 | 404 |
| 40002 | STOCK_INSUFFICIENT | 库存不足（附可用数量） | 409 |
| 40003 | CART_EMPTY | 购物车为空 | 400 |
| 40004 | ORDER_NOT_FOUND | 订单不存在 | 404 |
| 40005 | ORDER_STATE_INVALID | 当前状态不允许该操作 | 409 |
| 40006 | DUPLICATE_SUBMIT | 重复提交（防空单） | 409 |
| 40007 | PAYMENT_NOT_FOUND | 支付单不存在 | 404 |
| 40008 | SIGNATURE_INVALID | 回调验签失败 | 400 |
| 40009 | AMOUNT_MISMATCH | 金额不一致 | 409 |
| 40010 | REFUND_STATE_INVALID | 退款单状态不允许 | 409 |
| 40011 | COUPON_UNAVAILABLE | 优惠券不可用（过期/门槛/已用） | 400 |
| 40012 | COUPON_ALREADY_CLAIMED | 已领取过 | 409 |
| 40013 | REVIEW_EXISTS | 该订单项已评价 | 409 |
| 40014 | ADDRESS_INVALID | 地址缺失或不属于本人 | 400 |
| 40015 | EMAIL_ALREADY_USED | 邮箱已注册 | 409 |
| 40016 | MERCHANT_NOT_APPROVED | 店铺未通过审核 | 403 |
| 40017 | MERCHANT_SUSPENDED | 店铺已封禁 | 403 |
| 40018 | MERCHANT_APPLY_DUPLICATE | 已有待审核的入驻申请 | 409 |
| 40019 | SUB_ORDER_STATE_INVALID | 子单当前状态不允许该操作 | 409 |
| 40020 | SETTLEMENT_EXISTS | 该商家该周期已生成结算单 | 409 |
| 40021 | SETTLEMENT_NOT_ELIGIBLE | 子单未满足结算条件 | 409 |
| 30003 | MERCHANT_SCOPE_FORBIDDEN | 访问了非本店资源 | 403 |
| 50001 | INTERNAL_ERROR | 系统异常（不回显堆栈） | 500 |
| 50002 | UPSTREAM_TIMEOUT | 下游超时 | 504 |

## 约定

- 所有业务异常抛 `BusinessException(code, message)`，全局 `@RestControllerAdvice` 统一转换
- `message` 是给用户看的中文文案；日志里用 `code` + `traceId` 定位
- 前端按 `code` 分支处理（如 20002 跳登录、40002 提示库存），不按 `message` 匹配
