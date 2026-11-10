# 演示账号（上线后实测）

> 口令统一 `Demo@123456`（`newbie@kdshop.dev` 为注册测试账号，口令不同）。
> 「已验证」= 在 `http://8.134.215.195` 上用该账号实际登录并访问过对应页面。

## 账号

| 账号 | 角色 | 能做什么 | 验证状态 |
|---|---|---|---|
| `customer@kdshop.dev` | 顾客 | 浏览、购物车、结算、支付、订单、评价、退款、领券 | ✅ 已在 ECS 登录并通过接口冒烟 |
| `customer2@kdshop.dev` | 顾客 + 商家 | 同上，且是「李四杂货铺」店主 | ⏳ 待 80 端口放行后实测 |
| `merchant1@kdshop.dev` | 商家 | 店铺、商品、本店订单、退款处理、结算单 | ⏳ 待验证 |
| `merchant2@kdshop.dev` | 商家 | 山野茶铺 | ⏳ 待验证 |
| `merchant3@kdshop.dev` | 商家 | 待审核小铺 | ⏳ 待验证 |
| `merchant4`–`merchant8@kdshop.dev` | 商家 | 北辰数码 / 白露衣橱 / 青禾日用 / 拾光书屋 / 野趣户外 | ⏳ 待验证 |
| `support@kdshop.dev` | 客服 | 仅角色，暂无专属页面 | ⏳ 待验证 |
| `operator@kdshop.dev` | 运营 | 仅角色，暂无专属页面 | ⏳ 待验证 |
| `admin@kdshop.dev` | 管理员 | 平台看板、订单、退款、类目、商品、商家审核、结算、账号权限、审计 | ⏳ 待验证 |

## 访问地址

| 用途 | 地址 | 状态 |
|---|---|---|
| 前台 | `http://8.134.215.195/` | ⏳ 安全组 80 放行后可用 |
| 健康检查 | `http://8.134.215.195/api/v1/categories` | 同上 |
| 邮件（Mailpit） | SSH 隧道后 `http://127.0.0.1:8025` | ✅ 容器内可用 |

## 服务器内部验证（已完成）

```text
curl http://127.0.0.1/                     → 200
curl http://127.0.0.1/api/v1/categories    → 200，返回 8 个一级类目
curl http://127.0.0.1/media/products/1/1.jpg → 200
customer@kdshop.dev 登录                    → 成功
/orders、/coupons、/auth/me                 → 均返回数据
/admin（前端路由）                          → 200
```

## 待办

1. 控制台放行 80 后，用浏览器逐个账号登录，把上表 ⏳ 改成 ✅
2. 若改过口令，记得同步本文件
