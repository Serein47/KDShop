# 种子数据规格（G0）

每次 `docker compose up` 后由 Flyway 迁移或种子脚本写入，保证任何环境起点一致。**演示账号口令只用于演示，生产部署后必须全部重置。**

## 账号

| 邮箱 | 口令 | 角色 | 用途 |
|---|---|---|---|
| `customer@kdshop.dev` | `Demo@123456` | CUSTOMER | 主演示：下单、支付、退款、评价 |
| `customer2@kdshop.dev` | `Demo@123456` | CUSTOMER | 演示越权：A 不能看 B 的订单 |
| `merchant1@kdshop.dev` | `Demo@123456` | MERCHANT | 山野杂货，ACTIVE，有商品与子单 |
| `merchant2@kdshop.dev` | `Demo@123456` | MERCHANT | 山野茶铺，ACTIVE，用于演示拆单 |
| `merchant3@kdshop.dev` | `Demo@123456` | CUSTOMER | 已提交入驻、状态 PENDING，用于演示审核 |
| `support@kdshop.dev` | `Demo@123456` | SUPPORT | 客服：退款介入、订单检索 |
| `operator@kdshop.dev` | `Demo@123456` | OPERATOR | 运营：商家审核、类目、平台券 |
| `admin@kdshop.dev` | 部署时生成 | ADMIN | 管理员：角色、费率、审计 |

> 管理员口令不写入仓库：部署脚本生成随机口令并打印到控制台一次，由部署者记录。

## 类目（两级，8 个一级）

| 一级 | 子类（示例） |
|---|---|
| 食品饮料 | 咖啡、茶饮、零食 |
| 家居日用 | 厨房、清洁 |
| 数码配件 | 耳机、充电 |
| 服饰鞋包 | 上衣、鞋 |
| 美妆个护 | 护肤、洗护 |
| 图书文创 | 笔记本、文具 |
| 运动户外 | 健身、露营 |
| 母婴玩具 | 玩具、喂养 |

## 商品（20 个，覆盖边界场景）

| 覆盖点 | 数量 | 说明 |
|---|---|---|
| 单 SKU | 8 | 规格为空对象 `{}` |
| 双规格 | 10 | 颜色、规格各一维 |
| 三规格 | 2 | 演示规格组合选择 |
| 已下架 | 3 | 前台不可见，商家后台可见 |
| 库存为 0 | 2 | 商品详情显示「售罄」，加购被拒 |
| 价格区间 | 全部 | ¥9.90 – ¥1,999.00，覆盖包邮门槛两侧 |

- 商家归属：merchant1 名下 12 个，merchant2 名下 8 个
- 图片：每商品 2–3 张，放 MinIO `product/{id}/` 前缀
- 销量：随机 0–200，用于热销榜排序

## 优惠券（2 张）

| 券码 | 类型 | 面额 | 门槛 | 总量 | 有效期 |
|---|---|---|---|---|---|
| `WELCOME10` | FIXED | ¥10 | 无 | 1000 | 长期 |
| `AUTUMN300` | THRESHOLD | ¥16.80 | 满 ¥300 | 500 | 30 天 |

## 预置订单（3 笔，便于演示各状态）

| 订单 | 状态 | 内容 |
|---|---|---|
| `KD202610010001` | COMPLETED | 单商家，已完成，可演示评价与结算 |
| `KD202610010002` | SHIPPED | 单商家，已发货，可演示确认收货 |
| `KD202610010003` | 跨商家 | 两子单：一个已发货、一个待发货，演示主单「部分发货」 |

## 一致性校验

种子写入后执行：

```sql
-- 主单金额必须等于子单之和
SELECT o.order_no FROM orders o
JOIN (SELECT order_id, SUM(pay_amount_cent) s FROM merchant_orders GROUP BY order_id) m
  ON m.order_id = o.id WHERE o.pay_amount_cent <> m.s;
-- 库存不得为负
SELECT id FROM skus WHERE stock < 0 OR locked_stock < 0;
-- 每个 ACTIVE 商家至少 1 个上架商品
```

三条都必须返回空结果，否则种子脚本视为失败。
