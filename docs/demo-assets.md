# 演示素材（图片）

## 结论先行

- 免费文生图（Pollinations / Flux、Turbo）**做不出真正的纯白抠图**：模型总会带浅灰背景和投影，实测 30 张里只有 3 张接近纯白
- 试过三种补救都失败：换模型、从四角区域生长刷白（白瓷杯会被整只刷掉）、只提对比度压白场（灰阶 200 压不白）
- **已选定风格：浅灰影棚 + 柔和投影**，视觉上接近商品图，与站点暖色背景搭配可接受
- 真要纯白底，需要分割模型（rembg / remove.bg），留作后续增强

## 存储方式

- 图片放 MinIO（`kdshop-media` 桶，公共只读），**不进 Git**
- 数据库存相对 key：`products/21/1.jpg`，前端用 `VITE_MEDIA_BASE` 拼完整地址
- 换域名或用云 OSS 时只需改环境变量，数据库不动

## 脚本

| 脚本 | 作用 |
|---|---|
| `fetch-assets.ps1` | 按 manifest 批量生成主图（限流重试、裁水印） |
| `fix-images.ps1` | 个别生成错误时用指定提示词重跑（`fix-list.json`） |
| `check-whiteness.ps1` | 采样四边像素，判断背景是否接近纯白，用于筛图 |
| `upload-assets.ps1` | 同步到 MinIO 并开放公共只读 |
| `bind-media.ps1` | 扫描本地图片，生成并执行绑定 SQL |

## 跑一遍

```powershell
# 生成（30 个商品各 1 张，约 30-40 分钟，受 45 秒/张限流）
powershell -ExecutionPolicy Bypass -File scripts/media/fetch-assets.ps1 -MaxPerProduct 1

# 个别图不对时
powershell -ExecutionPolicy Bypass -File scripts/media/fix-images.ps1

# 上传 + 绑定
powershell -ExecutionPolicy Bypass -File scripts/media/upload-assets.ps1
powershell -ExecutionPolicy Bypass -File scripts/media/bind-media.ps1
```

## 提示词策略

```
isolated product photo, {商品英文名}, e-commerce catalog photography,
high key lighting, sharp focus, no props, no scenery, no people, centered, no text
```

经验：**flux 主体还原度高但背景偏灰；turbo 背景更亮但容易跑偏**（例如“中性笔”生成人像）。批量用 turbo、错误的用 flux 按 `fix-list.json` 补。

## 合规

- 生成图无第三方版权
- 不抓取淘宝 / 京东 / 小红书图片
- 评价与销量由真实订单生成，不爬取
