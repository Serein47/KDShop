# 接口文档

本目录是前后端的唯一契约来源。代码按这里实现，前端按这里做 mock，测试按这里写断言。

## 目录

| 文件 | 内容 | 调用方 |
|---|---|---|
| [01-公共与认证.md](01-公共与认证.md) | 通用约定、认证、会话、系统配置 | 全部 |
| [02-顾客端.md](02-顾客端.md) | 商品、购物车、结算、订单、支付、个人中心 | 顾客前台 |
| [03-商家端.md](03-商家端.md) | 入驻、店铺、商品、子单、发货、退款、结算 | 商家后台 |
| [04-平台端.md](04-平台端.md) | 商家审核、订单、类目、平台券、结算、审计 | 平台后台 |
| [05-支付网关.md](05-支付网关.md) | 模拟网关协议、签名、回调、对账 | 网关与联调 |

## 通用约定

| 项 | 约定 |
|---|---|
| 基址 | `/api/v1` |
| 编码 | UTF-8，JSON 用 `application/json; charset=utf-8` |
| 认证 | 会话 Cookie `KD_SESSION`（HttpOnly, SameSite=Lax, 生产加 Secure） |
| CSRF | 非 GET 请求带 `X-XSRF-TOKEN`，值来自 `GET /auth/csrf` |
| 金额 | 整数分，字段以 `_cent` 结尾；展示层除 100 并保留两位 |
| 时间 | ISO-8601 带偏移，如 `2026-10-27T14:05:00+08:00`；请求一律用该格式 |
| 分页 | `page` 从 1 开始，`size` 默认 20（上限 100），`sort` 如 `createdAt,desc` |
| 幂等 | 下单与发起支付带 `Idempotency-Key`（UUID），同键 10 分钟内返回首次结果 |
| 追踪 | 每个响应带 `traceId`，报错时把它给到后端查日志 |

## 统一响应

成功：

```json
{ "code": 0, "message": "ok", "data": { }, "traceId": "9f2c1a7e" }
```

失败：

```json
{ "code": 40002, "message": "库存不足，当前仅剩 3 件", "data": { "skuId": 101, "available": 3 }, "traceId": "9f2c1a7e" }
```

- `code` 为 0 表示成功，非 0 时 `data` 可能是补充信息（如字段级错误、可用库存）
- HTTP 状态码同步语义：400 参数 / 401 未登录 / 403 无权限 / 404 不存在 / 409 状态冲突 / 500 系统
- 错误码全表见 [../error-codes.md](../error-codes.md)

## 分页对象

```json
{ "items": [], "total": 128, "page": 1, "size": 20, "hasNext": true }
```

## 枚举

### 角色 `Role`

| 值 | 含义 |
|---|---|
| `GUEST` | 游客（未登录） |
| `CUSTOMER` | 顾客 |
| `MERCHANT` | 商家 |
| `SUPPORT` | 客服 |
| `OPERATOR` | 平台运营 |
| `ADMIN` | 管理员 |

### 主单状态 `OrderStatus`

| 值 | 含义 | 可执行操作 |
|---|---|---|
| `PENDING_PAYMENT` | 待付款 | 支付、取消 |
| `PAID` | 已付款 | 等待商家发货 |
| `PARTIALLY_SHIPPED` | 部分发货 | 等待其余包裹 |
| `SHIPPED` | 已发货 | 确认收货、申请退款 |
| `COMPLETED` | 已完成 | 评价 |
| `CANCELLED` | 已取消 | 无 |
| `REFUNDED` | 已退款 | 无 |

### 子单状态 `SubOrderStatus`

| 值 | 含义 |
|---|---|
| `PENDING_PAYMENT` | 待付款 |
| `PENDING_SHIPMENT` | 待发货 |
| `SHIPPED` | 已发货 |
| `COMPLETED` | 已完成 |
| `REFUNDING` | 退款中 |
| `REFUNDED` | 已退款 |
| `CANCELLED` | 已取消 |

### 支付状态 `PaymentStatus`

`INIT` 已创建 · `PENDING` 待支付 · `SUCCESS` 成功 · `FAILED` 失败 · `CLOSED` 已关闭 · `REFUNDED` 已退款

### 退款状态 `RefundStatus`

`APPLIED` 待审核 · `APPROVED` 已同意 · `REJECTED` 已驳回 · `REFUNDED` 已退款

### 商家状态 `MerchantStatus`

`PENDING` 待审核 · `ACTIVE` 正常 · `REJECTED` 已驳回 · `SUSPENDED` 已封禁

## 对象约定

地址快照 `Address`：

```json
{ "receiver": "张三", "phone": "138****8000", "province": "浙江省", "city": "杭州市",
  "district": "西湖区", "detail": "文三路 1 号 2 幢 301" }
```

商品卡片 `ProductCard`：

```json
{ "id": 88, "name": "手冲咖啡套装", "mainImage": "https://.../p/88/1.jpg",
  "minPrice": 12800, "salesCount": 42, "shopName": "山野杂货", "merchantId": 3 }
```

## 变更规则

1. 先改文档，再改实现；PR 里两处一起出现
2. 删字段、改字段含义、改状态机 = 破坏性变更，PR 标题加 `[BREAKING]` 并在本目录记一条变更说明
3. 新增可选字段 = 兼容变更，直接加，前端忽略未知字段
