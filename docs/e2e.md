# 端到端测试（E2E）

用真实浏览器把关键链路走一遍：浏览器 → 前端 → 后端 → MySQL → Mailpit。断言的是页面与接口的最终状态，不是单个方法。

## 前置条件

- 后端：`http://127.0.0.1:8081`（`--server.port=8081`）
- 前端：`http://localhost:5180`（`npm run dev`，`VITE_API_TARGET` 指向 8081）
- Mailpit：`http://localhost:8025`
- 浏览器：本机安装 Microsoft Edge（脚本用 `channel: 'msedge'`，不额外下载 Chromium）

## 运行

```bash
cd frontend
npm run e2e
```

可选环境变量：`E2E_BASE`（默认 `http://localhost:5180`）、`MAILPIT_URL`（默认 `http://localhost:8025`）。

退出码 0 表示全部通过；失败时截图写到 `.shots/e2e/`，结构化结果写到 `.shots/e2e-report.json`。

## 覆盖的场景（6 条）

| # | 场景 | 断言 |
|---|---|---|
| 1 | 注册与邮箱验证 | 调注册接口 → 从 Mailpit 取验证 token → 验证 → UI 登录 → `emailVerified=true` |
| 2 | 领券 | 管理员建一张专属券 → 顾客在 `/coupons` 点击领取 → 券出现在「我的券」 |
| 3 | 加购、用券下单与模拟支付 | 商品页加购 → 购物车结算 → 选地址与券 → 提交订单 → 模拟支付 → 订单为已付款 |
| 4 | 商家发货 | 商家在 `/merchant/orders` 找到该子单 → 填单号发货 → 子单状态 SHIPPED |
| 5 | 确认收货与评价 | 顾客确认收货（COMPLETED）→ 提交评价 → 按钮变「已评价」 |
| 6 | 退款与平台裁决 | 顾客申请退款 → 管理员在 `/admin/refunds` 判给买家 → 出现 REFUNDED 记录 |

## 可重复性

- 每次运行注册新邮箱（`e2e+时间戳@kdshop.dev`）、新建专属券、新下单，不依赖固定订单号
- 订单号由「下单后跳转的订单列表首条」获得，并在后续场景中串起来用
- 失败不会中断后续场景，最后汇总通过数；适合本地反复跑

## 已知边界

- 依赖演示种子数据（地址簿、商家账号）；空库直接跑会失败
- 未覆盖：密码找回、定制交易、多商家拆单的并发、管理端全部接口
- 尚未接入 CI；需要先有可重建的测试库
