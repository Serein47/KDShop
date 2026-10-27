# 邮件模板与触发点（G0）

邮件不真发：写入 `email_outbox`，由后台「邮件记录」页与 Mailpit 查看。这一份定义「什么时候发、发给谁、内容长什么样」。

## 通用约定

- 幂等键 = `template + businessKey`，同一业务事件只入队一次（唯一索引兜底）
- 入队失败只记日志，不回滚业务事务；业务接口照常成功
- 模板变量缺字段 → 该封邮件标 `FAILED` 并记录原因，不阻塞队列
- 所有邮件带退订说明与「本邮件由系统自动发送」

## 模板

### 1. `EMAIL_VERIFY` 邮箱验证

| 项 | 内容 |
|---|---|
| 触发 | 注册成功、用户点「重新发送」 |
| 收件人 | 注册邮箱 |
| 业务键 | `verify:{userId}:{tokenId}` |
| 主题 | 请验证你的 KDShop 邮箱 |
| 变量 | `nickname`、`verifyUrl`、`expireMinutes`(30) |
| 正文要点 | 一句说明 + 按钮 + 「链接 30 分钟内有效」+ 「非本人操作请忽略」 |

### 2. `ORDER_CREATED` 下单成功

| 项 | 内容 |
|---|---|
| 触发 | 订单创建成功（待付款） |
| 收件人 | 下单账号邮箱 |
| 业务键 | `order_created:{orderNo}` |
| 主题 | 订单 {orderNo} 已创建，请在 {payDeadline} 前完成支付 |
| 变量 | `orderNo`、`items`(名称×数量)、`payAmount`、`payUrl`、`payDeadline` |
| 正文要点 | 商品清单、应付金额、支付入口、超时自动取消提示 |

### 3. `PAYMENT_SUCCESS` 支付成功

| 项 | 内容 |
|---|---|
| 触发 | 支付回调验签通过、订单转「已付款」 |
| 收件人 | 下单账号邮箱 |
| 业务键 | `payment_success:{paymentNo}` |
| 主题 | 支付成功，订单 {orderNo} 正在备货 |
| 变量 | `orderNo`、`paidAmount`、`paidAt`、`orderUrl` |
| 正文要点 | 实付金额、支付时间、预计发货说明 |

### 4. `ORDER_SHIPPED` 发货通知

| 项 | 内容 |
|---|---|
| 触发 | 后台发货成功、订单转「已发货」 |
| 收件人 | 下单账号邮箱 |
| 业务键 | `order_shipped:{orderNo}` |
| 主题 | 你的订单 {orderNo} 已发货 |
| 变量 | `orderNo`、`carrier`、`trackingNo`、`shippedAt`、`orderUrl` |
| 正文要点 | 承运商、单号、查看订单入口 |

### 5. `REFUND_SUCCESS` 退款成功（P1）

| 项 | 内容 |
|---|---|
| 触发 | 退款单转为「已退款」 |
| 业务键 | `refund_success:{refundNo}` |
| 主题 | 订单 {orderNo} 的退款已处理 |
| 变量 | `orderNo`、`refundAmount`、`refundedAt` |

## 验收线

1. 注册后 Mailpit 收到验证邮件，点链接能完成验证
2. 下单 → 收到下单邮件；支付成功 → 收到支付邮件；后台发货 → 收到发货邮件
3. 同一订单重复触发发货 → 邮件只有一封（幂等键生效）
4. 把 outbox 表设为不可写 → 下单仍成功，日志有错误记录

## 实现提示

- 模板放 `backend/src/main/resources/mail-templates/`，用 Thymeleaf 渲染 HTML + 纯文本两版
- 发信抽象成 `MailSender` 接口：`SmtpMailSender`（本地 Mailpit）与 `OutboxOnlySender`（生产演示，只入库）
- 生产环境默认 `OutboxOnlySender`，避免误发真实邮件
