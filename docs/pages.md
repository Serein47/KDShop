# 页面清单与路由（G0）

三套界面：顾客前台（`/`）、商家后台（`/merchant`）、平台后台（`/admin`）。

## 顾客前台

| 路由 | 页面 | 核心内容 | 鉴权 |
|---|---|---|---|
| `/` | 首页 | 轮播、类目入口、热销 Top8 | 无 |
| `/category/:id` | 类目列表 | 筛选、排序、分页 | 无 |
| `/search?q=` | 搜索结果 | 同列表 + 关键词回显 | 无 |
| `/product/:id` | 商品详情 | 图集、价格、规格、库存、店铺信息、加购 | 无 |
| `/shop/:merchantId` | 店铺页 | 店内商品、店铺简介 | 无 |
| `/cart` | 购物车 | **按商家分组**、勾选、改数量、合计 | 游客可用（本地） |
| `/checkout` | 结算 | 地址、优惠券、金额明细、按商家拆单预览 | 需登录 |
| `/pay/:paymentNo` | 支付页 | 跳转模拟网关、倒计时 | 需登录 |
| `/mock-pay/:paymentNo` | 模拟网关 | 成功/失败按钮、签名回调演示 | 无（模拟第三方） |
| `/orders` | 订单列表 | 主单列表 + 子单状态摘要 | 需登录 |
| `/orders/:orderNo` | 订单详情 | **主单 + 子单折叠**，每包裹独立状态与操作 | 需登录 |
| `/account/profile` | 个人资料 | 昵称、手机、改密码 | 需登录 |
| `/account/addresses` | 地址簿 | 增删改、设默认 | 需登录 |
| `/account/coupons` | 我的优惠券 | 可用/已用/过期 | 需登录 |
| `/account/favorites` | 收藏夹 | 列表、取消收藏 | 需登录 |
| `/account/apply-merchant` | 申请入驻 | 店铺资料表单、审核状态、驳回原因 | 需登录 |
| `/login` `/register` `/forgot` `/reset` | 认证 | 表单校验、错误提示 | 无 |

## 商家后台

| 路由 | 页面 | 核心内容 |
|---|---|---|
| `/merchant/login` | 商家登录 | 独立入口，非商家账号提示「无店铺或审核未通过」 |
| `/merchant/dashboard` | 店铺看板 | 今日/近一周销售额、订单量、待发货数、热销 Top5 |
| `/merchant/shop` | 店铺设置 | 店名、Logo、简介、联系方式、审核状态 |
| `/merchant/products` | 商品列表 | 搜索、上下架、编辑、删除 |
| `/merchant/products/edit/:id?` | 商品编辑 | 基本信息、SKU 与库存、图片上传 |
| `/merchant/orders` | 子单列表 | 状态筛选、搜索单号、批量发货 |
| `/merchant/orders/:subOrderNo` | 子单详情 | 商品、收货信息、发货、状态时间线 |
| `/merchant/refunds` | 退款处理 | 待审核、同意/驳回（填原因） |
| `/merchant/settlements` | 结算单 | 周期、应结金额、佣金、明细、导出 |

## 平台后台

| 路由 | 页面 | 核心内容 | 最低角色 |
|---|---|---|---|
| `/admin/login` | 平台登录 | 独立入口 | — |
| `/admin/dashboard` | 平台看板 | GMV、订单量、商家数、热销 Top5、佣金收入 | 客服（只读） |
| `/admin/merchants` | 商家管理 | 入驻审核、封禁/解封、费率查看 | 运营 |
| `/admin/merchants/:id` | 商家详情 | 资料、资质、商品数、成交额、审核记录 | 运营 |
| `/admin/products` | 商品管理 | 全平台检索、强制下架 | 运营 |
| `/admin/categories` | 类目管理 | 两级树、排序 | 运营 |
| `/admin/orders` | 主单列表 | 多条件检索、状态筛选、跳子单 | 客服 |
| `/admin/orders/:orderNo` | 主单详情 | 子单列表、支付信息、状态时间线 | 客服 |
| `/admin/refunds` | 退款介入 | 全平台退款单、强制同意/驳回 | 客服 |
| `/admin/customers` | 顾客管理 | 搜索、订单数、禁用 | 客服只读 / 运营可改 |
| `/admin/coupons` | 平台券 | 新建、发放、停用 | 运营 |
| `/admin/reviews` | 评价管理 | 隐藏/恢复 | 运营 |
| `/admin/settlements` | 结算管理 | 生成、查看明细、确认打款 | 运营（费率仅管理员） |
| `/admin/roles` | 角色权限 | 用户角色分配 | 管理员 |
| `/admin/audit` | 审计日志 | 按操作人/时间/对象筛选 | 管理员 |

## 约定

- 三套界面共用一次构建，按 `/admin`、`/merchant`、`/` 前缀分包懒加载
- 未登录访问受保护路由 → 跳对应登录页并带 `redirect`
- 403 页区分「未登录」「无权限」「非本店资源」三种文案
- 列表页筛选条件写进 URL，刷新不丢状态
