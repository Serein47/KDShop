# 演示素材（图片）

## 原则

- **图片不进 Git**：体积大、来源复杂、公开仓库留证
- **只进 manifest 与 resolved.json**：记录每个商品要什么图、怎么生成
- **存储走 MinIO**（Q14），不塞进前端 `public/`
- **数据库存 key**：`products/21/1.jpg`，渲染时拼 `VITE_MEDIA_BASE`

## 为什么用 AI 生成

| 图源 | 结论 |
|---|---|
| Pexels API | 2026-11 起暂停发放新 key，走不通 |
| Openverse / Wikimedia | 本机网络超时 |
| 抓电商站图片 | 违反平台条款、有独立版权，公开仓库会留证 |
| **Pollinations（Flux）** | ✅ 免 key、可直连、无第三方版权；缺点是每 45 秒限 1 张 |

生成图自带右下角水印，脚本会裁掉底部 8%。

## 目录

```
scripts/media/
  manifest.json       # 商品 → 提示词、张数、是否试点
  fetch-assets.ps1    # 调 Pollinations 生成，写 resolved.json（提示词/模型/许可）
  upload-assets.ps1   # 同步到 MinIO 并开放公共只读
  bind-media.sql      # 把已上传的 key 回写到 products
infra/demo-assets/
  images/             # 实际图片，已 gitignore
```

## 跑一遍

```powershell
# 试点（10 个商品 × 2 张，约 16 分钟，受 45 秒/张限流）
powershell -ExecutionPolicy Bypass -File scripts/media/fetch-assets.ps1 -Pilot -MaxPerProduct 2

# 全部（30 个商品 × 3 张，约 70 分钟）
powershell -ExecutionPolicy Bypass -File scripts/media/fetch-assets.ps1

# 上传到 MinIO
powershell -ExecutionPolicy Bypass -File scripts/media/upload-assets.ps1

# 绑定到商品
mysql -h 127.0.0.1 -P 3307 -ukdshop -pkdshop123 kdshop < scripts/media/bind-media.sql
```

## 提示词策略

统一风格后缀，保证 30 个商品看起来像一家店拍的：

```
{subject}, soft natural window light, warm beige background,
minimal product photography, centered, high detail, no text
```

## 合规

- 生成图无第三方版权，可自由使用
- **不抓取**淘宝 / 京东 / 小红书的商品图与文案
- 评价与销量**不爬取**：由真实订单生成，见 ISSUE-26

## 待办

- [x] 管线跑通（MinIO → 前端渲染验证）
- [x] 试点 10 个商品出图（7 张成功，3 张被限流，脚本会自动补）
- [ ] 铺满 30 个商品 × 3 张
- [ ] 店铺 logo / banner
- [ ] 评价与销量生成脚本

## 试点结果（2026-11-02）

- 成功出图：商品 5、11、14、21、23、25、29（各 1 张主图）
- 失败：1、3、7（HTTP 402 限流，重试耗尽）；重跑脚本会自动补齐
- 风格：暖米色背景 + 柔和窗光，与站点配色一致
- 链路：MinIO 公共桶 → `VITE_MEDIA_BASE` → 详情/列表渲染，均已实测
