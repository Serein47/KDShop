# 演示素材（图片）

## 原则

- **图片不进 Git**：体积大、版权归属复杂、公开仓库会留下证据链
- **只进 manifest**：`scripts/media/manifest.json` 记录每个商品要什么图、从哪来
- **存储走 MinIO**（ADR/Q14），不塞进前端 `public/`
- **数据库存 key**：`products/21/1.jpg`，渲染时拼 `VITE_MEDIA_BASE`

## 目录

```
scripts/media/
  manifest.json       # 商品 → 搜索词、张数、是否试点
  fetch-assets.ps1    # 按清单从 Pexels 拉图，写 resolved.json（作者/许可）
  upload-assets.ps1   # 同步到 MinIO 并开放公共只读
  bind-media.sql      # 把已上传的 key 回写到 products
infra/demo-assets/
  images/             # 实际图片，已 gitignore
```

## 跑一遍

```powershell
# 1) 免费申请 key：https://www.pexels.com/api/
$env:PEXELS_API_KEY = "你的 key"

# 2) 拉图（-Pilot 只跑 10 个试点商品）
powershell -ExecutionPolicy Bypass -File scripts/media/fetch-assets.ps1 -Pilot

# 3) 上传到 MinIO（基础设施在 WSL 里）
powershell -ExecutionPolicy Bypass -File scripts/media/upload-assets.ps1

# 4) 绑定到商品（试点 10 个的 SQL 已写好）
mysql -h 127.0.0.1 -P 3307 -ukdshop -pkdshop123 kdshop < scripts/media/bind-media.sql
```

打开 <http://localhost:5180/products/21> 应看到真实图片而不是占位字。

## 许可与合规

- Pexels License：可免费商用，无需署名；`resolved.json` 仍记录作者与原始链接，便于追溯
- **不要**抓取淘宝 / 京东 / 小红书等平台的商品图：违反平台条款、图片有独立版权，公开仓库会留证
- 评价与销量**不爬取**：用脚本基于真实订单生成，避免"演示数据来自别家商城"的尴尬

## 待办

- [ ] 拿到 Pexels key 后跑完 30 个商品 × 3 张
- [ ] 店铺 logo / banner（3 个店铺）
- [ ] 评价与销量生成脚本（依赖已完成订单，见 ISSUE-26）
