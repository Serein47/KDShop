-- 演示目录换成手作市集（仅 local profile 加载）
-- 保留账号、商家与三笔演示订单；只重写类目、商品、标签与定制项。

DELETE FROM product_options;
DELETE FROM product_tags;
DELETE FROM tags;
DELETE FROM categories;

INSERT INTO categories (id, parent_id, name, sort, status) VALUES
  (1, NULL, '家居', 10, 'ON'),
  (2, NULL, '文具', 20, 'ON'),
  (3, NULL, '礼物', 30, 'ON'),
  (11, 1, '餐具', 1, 'ON'),
  (12, 1, '摆件', 2, 'ON'),
  (13, 1, '香氛', 3, 'ON'),
  (14, 1, '织物', 4, 'ON'),
  (21, 2, '本册', 1, 'ON'),
  (22, 2, '书写', 2, 'ON'),
  (23, 2, '纸品', 3, 'ON'),
  (31, 3, '礼盒', 1, 'ON'),
  (32, 3, '贺卡', 2, 'ON'),
  (33, 3, '伴手礼', 3, 'ON');

INSERT INTO tags (id, dimension, name, slug, sort, status) VALUES
  (1,  'CRAFT',    '陶艺', 'pottery',    1, 'ON'),
  (2,  'CRAFT',    '木作', 'woodwork',   2, 'ON'),
  (3,  'CRAFT',    '金工', 'metalwork',  3, 'ON'),
  (4,  'CRAFT',    '织造', 'weaving',    4, 'ON'),
  (5,  'CRAFT',    '纸艺', 'papercraft', 5, 'ON'),
  (6,  'CRAFT',    '香氛', 'fragrance',  6, 'ON'),
  (7,  'MATERIAL', '粗陶', 'stoneware',  1, 'ON'),
  (8,  'MATERIAL', '胡桃木', 'walnut',   2, 'ON'),
  (9,  'MATERIAL', '黄铜', 'brass',      3, 'ON'),
  (10, 'MATERIAL', '亚麻', 'linen',      4, 'ON'),
  (11, 'MATERIAL', '羊毛', 'wool',       5, 'ON'),
  (12, 'MATERIAL', '玻璃', 'glass',      6, 'ON'),
  (13, 'OCCASION', '送恋人', 'for-lover',    1, 'ON'),
  (14, 'OCCASION', '自用', 'self',           2, 'ON'),
  (15, 'OCCASION', '乔迁', 'housewarming',   3, 'ON'),
  (16, 'OCCASION', '生日', 'birthday',       4, 'ON'),
  (17, 'OCCASION', '伴手礼', 'souvenir',     5, 'ON');

-- 商品：名称、类目、销售模式、工期、定金比例、档案
UPDATE products SET name='粗陶马克杯', subtitle='手工拉坯，每只纹理不同', category_id=11,
  sale_mode='IN_STOCK', story='工作室在景德镇，一只杯子从拉坯到烧成需要三天，釉色随窑温变化。',
  flaw_note='釉面可能有细微开片与针孔，属正常手作痕迹', requires_flaw_ack=1 WHERE id=1;
UPDATE products SET name='亚麻餐垫', subtitle='水洗亚麻，越用越软', category_id=14 WHERE id=2;
UPDATE products SET name='胡桃木托盘', subtitle='整块木料开槽', category_id=11,
  story='选用北美黑胡桃，木纹走向决定每一只的深浅。', flaw_note='天然木纹与结疤不视为瑕疵', requires_flaw_ack=1 WHERE id=3;
UPDATE products SET name='黄铜书签', subtitle='手工錾刻，越用越亮', category_id=22,
  edition_size=100, edition_no=12, story='黄铜会随使用氧化，留下属于你的痕迹。' WHERE id=4;
UPDATE products SET name='雪松香薰蜡烛', subtitle='大豆蜡，燃烧约 40 小时', category_id=13,
  sale_mode='PREORDER', lead_days=7, deposit_rate=3000 WHERE id=5;
UPDATE products SET name='手工皂礼盒', subtitle='三块装，可作伴手礼', category_id=31 WHERE id=6;
UPDATE products SET name='粗陶花器', subtitle='素坯不上釉', category_id=12,
  edition_size=20, edition_no=3, flaw_note='素坯表面有手工修坯痕迹', requires_flaw_ack=1 WHERE id=7;
UPDATE products SET name='手工玻璃杯', subtitle='吹制，杯壁厚薄不一', category_id=11,
  flaw_note='吹制过程会产生细小气泡', requires_flaw_ack=1 WHERE id=8;
UPDATE products SET name='刻字胡桃木盒', subtitle='可刻名字或一句话', category_id=31,
  sale_mode='CUSTOM', lead_days=14, deposit_rate=3000,
  story='每只盒子按订单开料，刻字由工作室手工雕刻，工期约两周。' WHERE id=9;
UPDATE products SET name='纸艺台灯', subtitle='手工折叠灯罩', category_id=12 WHERE id=10;
UPDATE products SET name='手账本 · 棉麻封面', subtitle='可平摊，120g 纸', category_id=21 WHERE id=11;
UPDATE products SET name='黄铜笔搁', subtitle='一体铸造，配重稳', category_id=22 WHERE id=12;
UPDATE products SET name='亚麻桌旗', subtitle='草木染，颜色略深', category_id=14 WHERE id=13;
UPDATE products SET name='帆布托特包', subtitle='加厚帆布，可水洗', category_id=33 WHERE id=14;
UPDATE products SET name='燕麦手工皂', subtitle='冷制皂，温和', category_id=33 WHERE id=15;
UPDATE products SET name='香氛礼盒', subtitle='蜡烛 + 香片', category_id=31, status='OFF' WHERE id=16;
UPDATE products SET name='皮面笔记本', subtitle='A5，可换内芯', category_id=21 WHERE id=17;
UPDATE products SET name='黄铜蘸水笔', subtitle='配两支笔尖', category_id=22 WHERE id=18;
UPDATE products SET name='手编羊毛地毯', subtitle='手工打结，1.2×1.8m', category_id=14,
  edition_size=8, edition_no=2 WHERE id=19;
UPDATE products SET name='折叠木凳', subtitle='榫卯结构，免安装', category_id=11, status='OFF' WHERE id=20;

-- SKU 价格与规格对齐新商品
UPDATE skus SET spec_json=JSON_OBJECT('颜色','米白'), price_cent=12800, original_price_cent=15800 WHERE id=1;
UPDATE skus SET spec_json=JSON_OBJECT('颜色','灰'),   price_cent=12800, original_price_cent=15800 WHERE id=2;
UPDATE skus SET spec_json=JSON_OBJECT('尺寸','30×20'), price_cent=6800 WHERE id=3;
UPDATE skus SET price_cent=16800 WHERE id=4;
UPDATE skus SET price_cent=6800 WHERE id=5;
UPDATE skus SET price_cent=9900 WHERE id=6;
UPDATE skus SET price_cent=8800 WHERE id=7;
UPDATE skus SET spec_json=JSON_OBJECT('颜色','原色'), price_cent=5800 WHERE id=8;
UPDATE skus SET price_cent=16800 WHERE id=9;
UPDATE skus SET spec_json=JSON_OBJECT('尺寸','小'), price_cent=29900, original_price_cent=35900 WHERE id=10;
UPDATE skus SET spec_json=JSON_OBJECT('尺寸','大'), price_cent=32900, original_price_cent=38900 WHERE id=11;
UPDATE skus SET price_cent=3900 WHERE id=12;
UPDATE skus SET spec_json=JSON_OBJECT('颜色','米白'), price_cent=12900 WHERE id=13;
UPDATE skus SET price_cent=15900 WHERE id=14;
UPDATE skus SET spec_json=JSON_OBJECT('颜色','原色'), price_cent=6900 WHERE id=15;
UPDATE skus SET price_cent=4900 WHERE id=16;
UPDATE skus SET price_cent=5900 WHERE id=17;
UPDATE skus SET price_cent=12900 WHERE id=18;
UPDATE skus SET price_cent=6800 WHERE id=19;
UPDATE skus SET price_cent=2900 WHERE id=20;
UPDATE skus SET price_cent=9900 WHERE id=21;
UPDATE skus SET price_cent=19900 WHERE id=22;

-- 订单行快照跟着改，避免演示时图文对不上
UPDATE order_items SET product_name='粗陶马克杯', sku_spec=JSON_OBJECT('颜色','米白'), unit_price_cent=12800, subtotal_cent=25600 WHERE id=1;
UPDATE order_items SET product_name='亚麻餐垫', sku_spec=JSON_OBJECT('尺寸','30×20'), unit_price_cent=6800, subtotal_cent=6800 WHERE id=2;
UPDATE order_items SET product_name='粗陶花器', sku_spec=JSON_OBJECT('颜色','原色'), unit_price_cent=5800, subtotal_cent=5800 WHERE id=3;
UPDATE order_items SET product_name='手账本 · 棉麻封面', sku_spec=JSON_OBJECT('颜色','米白'), unit_price_cent=12900, subtotal_cent=12900 WHERE id=4;

-- 标签映射
INSERT INTO product_tags (product_id, tag_id) VALUES
  (1,1),(1,7),(1,14),
  (2,4),(2,10),(2,15),
  (3,2),(3,8),(3,15),
  (4,3),(4,9),(4,16),
  (5,6),(5,12),(5,13),
  (6,6),(6,12),(6,17),
  (7,1),(7,7),(7,15),
  (8,3),(8,12),(8,14),
  (9,2),(9,8),(9,17),
  (10,5),(10,8),(10,14),
  (11,5),(11,10),(11,14),
  (12,3),(12,9),(12,14),
  (13,4),(13,10),(13,15),
  (14,4),(14,10),(14,17),
  (15,6),(15,12),(15,17),
  (16,6),(16,12),(16,13),
  (17,5),(17,10),(17,14),
  (18,3),(18,9),(18,14),
  (19,4),(19,11),(19,15),
  (20,2),(20,8),(20,15);

-- 定制项
INSERT INTO product_options (product_id, name, type, required, max_length, choices_json, price_delta_cent, sort, status) VALUES
  (1, '杯底刻字', 'TEXT', 0, 6, NULL, 1000, 1, 'ON'),
  (9, '盒面刻字', 'TEXT', 1, 12, NULL, 0, 1, 'ON'),
  (9, '尺寸', 'ENUM', 1, NULL, JSON_ARRAY('小','中','大'), 2000, 2, 'ON');
