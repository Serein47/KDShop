-- 通用商城类目与常规商品：让 KDShop 首先是一个完整的购物网站，
-- 手作家居/文具/礼物作为三个特色专区并存（categories.featured = 1，已在 V5 中建立）。

INSERT INTO categories (id, parent_id, name, sort, featured, status) VALUES
  (100, NULL, '食品饮料', 10, 0, 'ON'),
  (101, NULL, '家居日用', 20, 0, 'ON'),
  (102, NULL, '数码配件', 30, 0, 'ON'),
  (103, NULL, '服饰鞋包', 40, 0, 'ON'),
  (104, NULL, '美妆个护', 50, 0, 'ON'),
  (105, NULL, '图书文创', 60, 0, 'ON'),
  (106, NULL, '运动户外', 70, 0, 'ON'),
  (107, NULL, '母婴玩具', 80, 0, 'ON'),
  (1001, 100, '饮料冲调', 1, 0, 'ON'),
  (1002, 100, '休闲零食', 2, 0, 'ON'),
  (1011, 101, '厨房用品', 1, 0, 'ON'),
  (1012, 101, '清洁收纳', 2, 0, 'ON'),
  (1021, 102, '充电存储', 1, 0, 'ON'),
  (1022, 102, '手机配件', 2, 0, 'ON'),
  (1031, 103, '上装', 1, 0, 'ON'),
  (1032, 103, '鞋靴', 2, 0, 'ON'),
  (1041, 104, '护肤', 1, 0, 'ON'),
  (1042, 104, '洗护', 2, 0, 'ON'),
  (1051, 105, '图书', 1, 0, 'ON'),
  (1052, 105, '文创', 2, 0, 'ON'),
  (1061, 106, '健身', 1, 0, 'ON'),
  (1062, 106, '露营', 2, 0, 'ON'),
  (1071, 107, '玩具', 1, 0, 'ON'),
  (1072, 107, '喂养', 2, 0, 'ON');

-- 特色专区标记
UPDATE categories SET featured = 1 WHERE id IN (1, 2, 3, 11, 12, 13, 14, 21, 22, 23, 31, 32, 33);

INSERT INTO products (id, merchant_id, category_id, name, subtitle, description, main_image, images_json, status, sales_count) VALUES
  (21, 2, 1001, '天然苏打水 6 瓶', '0 糖 0 卡', '<p>380ml × 6，常温保存。</p>', NULL, JSON_ARRAY(), 'ON', 120),
  (22, 1, 1001, '挂耳咖啡 10 包', '中深烘，办公室随手冲', '<p>10g × 10，热水冲泡 3 分钟。</p>', NULL, JSON_ARRAY(), 'ON', 96),
  (23, 2, 1021, '65W 氮化镓充电头', '一个头充三件', '<p>双 C 口 + A 口，折叠插脚。</p>', NULL, JSON_ARRAY(), 'ON', 88),
  (24, 2, 1022, '磁吸手机壳', '支持无线充电', '<p>磨砂背板，四角气囊。</p>', NULL, JSON_ARRAY(), 'ON', 140),
  (25, 1, 1031, '纯棉白 T', '260g 重磅', '<p>落肩版型，可机洗。</p>', NULL, JSON_ARRAY(), 'ON', 210),
  (26, 2, 1032, '帆布一脚蹬', '硫化底，耐穿', NULL, NULL, JSON_ARRAY(), 'ON', 64),
  (27, 1, 1041, '氨基酸洁面', '温和不紧绷', '<p>150ml，敏感肌可用。</p>', NULL, JSON_ARRAY(), 'ON', 132),
  (28, 2, 1052, '中性笔 10 支装', '0.5mm 黑色', NULL, NULL, JSON_ARRAY(), 'ON', 320),
  (29, 1, 1062, '露营灯', '三档亮度，可当充电宝', '<p>IPX4 防水，续航 12 小时。</p>', NULL, JSON_ARRAY(), 'ON', 45),
  (30, 2, 1071, '木质积木 50 粒', '3 岁以上', NULL, NULL, JSON_ARRAY(), 'ON', 58);

INSERT INTO skus (id, product_id, spec_json, price_cent, original_price_cent, stock, locked_stock, status) VALUES
  (24, 21, JSON_OBJECT('规格', '6 瓶'), 2400,  NULL, 200, 0, 'ON'),
  (25, 22, JSON_OBJECT('规格', '10 包'), 4800,  NULL, 100, 0, 'ON'),
  (26, 23, JSON_OBJECT('颜色', '白'),   12900, 15900, 60, 0, 'ON'),
  (27, 24, JSON_OBJECT('颜色', '黑'),   3900,  NULL, 120, 0, 'ON'),
  (28, 25, JSON_OBJECT('尺码', 'M'),    6900,  NULL, 80, 0, 'ON'),
  (29, 25, JSON_OBJECT('尺码', 'L'),    6900,  NULL, 70, 0, 'ON'),
  (30, 26, JSON_OBJECT('尺码', '40'),   9900,  NULL, 40, 0, 'ON'),
  (31, 27, JSON_OBJECT(),               5900,  NULL, 90, 0, 'ON'),
  (32, 28, JSON_OBJECT(),               1500,  NULL, 500, 0, 'ON'),
  (33, 29, JSON_OBJECT(),              15900,  NULL, 35, 0, 'ON'),
  (34, 30, JSON_OBJECT(),               8800,  NULL, 25, 0, 'ON');

-- 常规商品也给一点标签，便于首页与筛选混排
INSERT INTO product_tags (product_id, tag_id) VALUES
  (21, 14), (22, 6), (23, 3), (24, 3), (25, 4), (26, 4), (27, 6), (28, 3), (29, 2), (30, 2);
