-- 演示种子数据（仅本地/演示环境）
-- 口令统一为 Demo@123456（BCrypt 10 轮）；管理员口令由部署脚本另行生成，这里不写。
-- 覆盖场景：单商家订单、跨商家拆单、部分发货、下架商品、零库存 SKU。

INSERT INTO roles (id, code, name) VALUES
  (1, 'CUSTOMER', '顾客'),
  (2, 'MERCHANT', '商家'),
  (3, 'SUPPORT',  '客服'),
  (4, 'OPERATOR', '运营'),
  (5, 'ADMIN',    '管理员');

INSERT INTO users (id, email, password_hash, nickname, phone, status, email_verified_at) VALUES
  (1, 'customer@kdshop.dev',  '$2b$10$sl74lctCfaCuSuQoPL44YeAV0paZnc5cRCHqxnseTe7lOKW6kDdue', '张三',  '13800008000', 'ACTIVE', NOW()),
  (2, 'customer2@kdshop.dev', '$2b$10$sl74lctCfaCuSuQoPL44YeAV0paZnc5cRCHqxnseTe7lOKW6kDdue', '李四',  '13800008001', 'ACTIVE', NOW()),
  (3, 'merchant1@kdshop.dev', '$2b$10$sl74lctCfaCuSuQoPL44YeAV0paZnc5cRCHqxnseTe7lOKW6kDdue', '山野掌柜', '13900001111', 'ACTIVE', NOW()),
  (4, 'merchant2@kdshop.dev', '$2b$10$sl74lctCfaCuSuQoPL44YeAV0paZnc5cRCHqxnseTe7lOKW6kDdue', '茶铺掌柜', '13900002222', 'ACTIVE', NOW()),
  (5, 'merchant3@kdshop.dev', '$2b$10$sl74lctCfaCuSuQoPL44YeAV0paZnc5cRCHqxnseTe7lOKW6kDdue', '待审店主', '13900003333', 'ACTIVE', NOW()),
  (6, 'support@kdshop.dev',   '$2b$10$sl74lctCfaCuSuQoPL44YeAV0paZnc5cRCHqxnseTe7lOKW6kDdue', '客服小周', '13700004444', 'ACTIVE', NOW()),
  (7, 'operator@kdshop.dev',  '$2b$10$sl74lctCfaCuSuQoPL44YeAV0paZnc5cRCHqxnseTe7lOKW6kDdue', '运营小王', '13700005555', 'ACTIVE', NOW()),
  (8, 'admin@kdshop.dev',     '$2b$10$sl74lctCfaCuSuQoPL44YeAV0paZnc5cRCHqxnseTe7lOKW6kDdue', '平台管理员', '13700006666', 'ACTIVE', NOW());

INSERT INTO user_roles (user_id, role_id) VALUES
  (1, 1), (2, 1), (3, 1), (3, 2), (4, 1), (4, 2), (5, 1), (6, 3), (7, 4), (8, 5);

INSERT INTO addresses (id, user_id, receiver, phone, province, city, district, detail, is_default) VALUES
  (1, 1, '张三', '13800008000', '浙江省', '杭州市', '西湖区', '文三路 1 号 2 幢 301', 1),
  (2, 2, '李四', '13800008001', '上海市', '上海市', '徐汇区', '漕溪北路 88 号', 1);

INSERT INTO merchants (id, user_id, shop_name, logo, description, contact_name, contact_phone, status, commission_rate, approved_at, approved_by) VALUES
  (1, 3, '山野杂货', NULL, '只卖当季山货与手作器物', '山野掌柜', '13900001111', 'ACTIVE',  500, NOW(), 7),
  (2, 4, '山野茶铺', NULL, '原叶茶与茶器',             '茶铺掌柜', '13900002222', 'ACTIVE',  500, NOW(), 7),
  (3, 5, '待审核小铺', NULL, '示范审核流程用',           '待审店主', '13900003333', 'PENDING', 500, NULL,  NULL);

INSERT INTO categories (id, parent_id, name, sort, status) VALUES
  (1, NULL, '食品饮料', 10, 'ON'),
  (2, NULL, '家居日用', 20, 'ON'),
  (3, NULL, '数码配件', 30, 'ON'),
  (4, NULL, '服饰鞋包', 40, 'ON'),
  (5, NULL, '美妆个护', 50, 'ON'),
  (6, NULL, '图书文创', 60, 'ON'),
  (7, NULL, '运动户外', 70, 'ON'),
  (8, NULL, '母婴玩具', 80, 'ON'),
  (11, 1, '咖啡', 1, 'ON'),
  (12, 1, '茶饮', 2, 'ON'),
  (13, 1, '零食', 3, 'ON'),
  (21, 2, '厨房', 1, 'ON'),
  (22, 2, '清洁', 2, 'ON'),
  (31, 3, '耳机', 1, 'ON'),
  (32, 3, '充电', 2, 'ON'),
  (41, 4, '上衣', 1, 'ON'),
  (42, 4, '包袋', 2, 'ON'),
  (51, 5, '护肤', 1, 'ON'),
  (52, 5, '洗护', 2, 'ON'),
  (61, 6, '笔记本', 1, 'ON'),
  (62, 6, '文具', 2, 'ON'),
  (71, 7, '健身', 1, 'ON'),
  (72, 7, '露营', 2, 'ON'),
  (81, 8, '玩具', 1, 'ON'),
  (82, 8, '喂养', 2, 'ON');

INSERT INTO products (id, merchant_id, category_id, name, subtitle, description, main_image, images_json, status, sales_count) VALUES
  (1,  1, 11, '手冲咖啡套装', '入门一次到位', '<p>含滤杯、滤纸与 200g 豆子</p>', NULL, JSON_ARRAY(), 'ON', 42),
  (2,  2, 12, '明前龙井 50g', '清明前采摘', '<p>豆香明显，回甘快</p>', NULL, JSON_ARRAY(), 'ON', 18),
  (3,  1, 11, '挂耳咖啡 10 包', '办公室随手冲', NULL, NULL, JSON_ARRAY(), 'ON', 66),
  (4,  2, 12, '正山小种 100g', '松烟香', NULL, NULL, JSON_ARRAY(), 'ON', 25),
  (5,  1, 13, '坚果礼盒', '六种混合', NULL, NULL, JSON_ARRAY(), 'ON', 31),
  (6,  1, 13, '冻干草莓', '整颗无糖', NULL, NULL, JSON_ARRAY(), 'ON', 12),
  (7,  1, 21, '手作陶瓷杯', '每只纹理不同', NULL, NULL, JSON_ARRAY(), 'ON', 21),
  (8,  2, 21, '竹制茶盘', '整竹开料', NULL, NULL, JSON_ARRAY(), 'ON', 7),
  (9,  1, 31, '无线降噪耳机', '通勤必备', NULL, NULL, JSON_ARRAY(), 'ON', 15),
  (10, 1, 31, '入耳式有线耳机', '百元价位', NULL, NULL, JSON_ARRAY(), 'ON', 88),
  (11, 2, 32, '65W 氮化镓充电器', '一个头充三件', NULL, NULL, JSON_ARRAY(), 'ON', 40),
  (12, 2, 32, '磁吸充电宝', '10000mAh', NULL, NULL, JSON_ARRAY(), 'ON', 9),
  (13, 1, 41, '纯棉基础 T 恤', '四季可穿', NULL, NULL, JSON_ARRAY(), 'ON', 52),
  (14, 2, 42, '帆布托特包', '能装下 14 寸电脑', NULL, NULL, JSON_ARRAY(), 'ON', 30),
  (15, 1, 51, '氨基酸洁面', '温和不紧绷', NULL, NULL, JSON_ARRAY(), 'ON', 27),
  (16, 2, 51, '保湿面霜 50g', '换季用', NULL, NULL, JSON_ARRAY(), 'OFF', 5),
  (17, 1, 61, '皮面笔记本', 'A5 横线', NULL, NULL, JSON_ARRAY(), 'ON', 11),
  (18, 2, 62, '中性笔套装', '0.5mm 黑色 10 支', NULL, NULL, JSON_ARRAY(), 'ON', 73),
  (19, 1, 71, '瑜伽垫', '6mm 防滑', NULL, NULL, JSON_ARRAY(), 'ON', 19),
  (20, 2, 72, '露营折叠椅', '承重 120kg', NULL, NULL, JSON_ARRAY(), 'OFF', 3);

INSERT INTO skus (id, product_id, spec_json, price_cent, original_price_cent, stock, locked_stock, status) VALUES
  (1,  1,  JSON_OBJECT('颜色', '白色'), 12800, 15800, 50, 0, 'ON'),
  (2,  1,  JSON_OBJECT('颜色', '黑色'), 16800, 19800, 30, 0, 'ON'),
  (3,  2,  JSON_OBJECT('规格', '50g'),  8000,  NULL,  40, 0, 'ON'),
  (4,  3,  JSON_OBJECT(),               4800,  NULL, 100, 0, 'ON'),
  (5,  4,  JSON_OBJECT('规格', '100g'), 6800,  NULL,  35, 0, 'ON'),
  (6,  5,  JSON_OBJECT(),               9900, 12800,  20, 0, 'ON'),
  (7,  6,  JSON_OBJECT(),               3200,  NULL,  60, 0, 'ON'),
  (8,  7,  JSON_OBJECT('颜色', '米白'), 5800,  NULL,  25, 0, 'ON'),
  (9,  8,  JSON_OBJECT(),              16800,  NULL,   8, 0, 'ON'),
  (10, 9,  JSON_OBJECT('颜色', '黑色'), 89900, 99900, 12, 0, 'ON'),
  (11, 9,  JSON_OBJECT('颜色', '白色'), 89900, 99900, 10, 0, 'ON'),
  (12, 10, JSON_OBJECT(),               3900,  NULL, 200, 0, 'ON'),
  (13, 11, JSON_OBJECT('颜色', '白色'), 12900,  NULL,  45, 0, 'ON'),
  (14, 12, JSON_OBJECT(),              15900,  NULL,  18, 0, 'ON'),
  (15, 13, JSON_OBJECT('颜色', '白色', '尺码', 'M'), 6900, NULL, 40, 0, 'ON'),
  (16, 13, JSON_OBJECT('颜色', '黑色', '尺码', 'L'), 6900, NULL, 35, 0, 'ON'),
  (17, 14, JSON_OBJECT(),               4900,  NULL,  22, 0, 'ON'),
  (18, 15, JSON_OBJECT(),               5900,  NULL,  50, 0, 'ON'),
  (19, 16, JSON_OBJECT(),              12900,  NULL,  15, 0, 'OFF'),
  (20, 17, JSON_OBJECT(),               6800,  NULL,  30, 0, 'ON'),
  (21, 18, JSON_OBJECT(),               2900,  NULL,   0, 0, 'ON'),
  (22, 19, JSON_OBJECT(),               9900,  NULL,  16, 0, 'ON'),
  (23, 20, JSON_OBJECT(),              19900,  NULL,   6, 0, 'OFF');

INSERT INTO coupons (id, code, name, type, value_cent, min_amount_cent, total, used_count, scope, starts_at, ends_at, status) VALUES
  (1, 'WELCOME10', '新人立减 10 元', 'FIXED',     1000,     0, 1000, 12, 'PLATFORM', DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 365 DAY), 'ON'),
  (2, 'AUTUMN300', '满 300 减 16.8', 'THRESHOLD', 1680, 30000,  500, 38, 'PLATFORM', DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY),  'ON');

INSERT INTO user_coupons (id, user_id, coupon_id, status) VALUES
  (1, 1, 1, 'UNUSED'),
  (2, 1, 2, 'UNUSED');

-- ===== 预置订单 =====
-- 订单 A：单商家，已完成
INSERT INTO orders (id, order_no, user_id, status, merchant_count, goods_amount_cent, discount_cent, shipping_fee_cent, pay_amount_cent, address_json, paid_at, completed_at, created_at) VALUES
  (1, 'KD202610010001', 1, 'COMPLETED', 1, 25600, 0, 0, 25600,
   JSON_OBJECT('receiver','张三','phone','138****8000','province','浙江省','city','杭州市','district','西湖区','detail','文三路 1 号 2 幢 301'),
   DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY));

INSERT INTO merchant_orders (id, sub_order_no, order_id, merchant_id, status, goods_amount_cent, discount_cent, shipping_fee_cent, pay_amount_cent, shipped_at, completed_at, settled) VALUES
  (1, 'KD202610010001-1', 1, 1, 'COMPLETED', 25600, 0, 0, 25600, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), 0);

INSERT INTO order_items (id, merchant_order_id, order_id, merchant_id, sku_id, product_id, product_name, shop_name, sku_spec, unit_price_cent, quantity, subtotal_cent) VALUES
  (1, 1, 1, 1, 1, 1, '手冲咖啡套装', '山野杂货', JSON_OBJECT('颜色','白色'), 12800, 2, 25600);

INSERT INTO payments (id, payment_no, order_id, channel, amount_cent, status, out_trade_no, order_no, paid_at) VALUES
  (1, 'PAY202610010001', 1, 'MOCK', 25600, 'SUCCESS', 'PAY202610010001', 'KD202610010001', DATE_SUB(NOW(), INTERVAL 20 DAY));

INSERT INTO shipments (id, merchant_order_id, carrier, tracking_no, operator_id, shipped_at) VALUES
  (1, 1, '顺丰', 'SF1000000001', 3, DATE_SUB(NOW(), INTERVAL 15 DAY));

-- 订单 B：单商家，已发货
INSERT INTO orders (id, order_no, user_id, status, merchant_count, goods_amount_cent, discount_cent, shipping_fee_cent, pay_amount_cent, address_json, paid_at, created_at) VALUES
  (2, 'KD202610010002', 2, 'SHIPPED', 1, 8000, 0, 1000, 9000,
   JSON_OBJECT('receiver','李四','phone','138****8001','province','上海市','city','上海市','district','徐汇区','detail','漕溪北路 88 号'),
   DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY));

INSERT INTO merchant_orders (id, sub_order_no, order_id, merchant_id, status, goods_amount_cent, discount_cent, shipping_fee_cent, pay_amount_cent, shipped_at) VALUES
  (2, 'KD202610010002-2', 2, 2, 'SHIPPED', 8000, 0, 1000, 9000, DATE_SUB(NOW(), INTERVAL 3 DAY));

INSERT INTO order_items (id, merchant_order_id, order_id, merchant_id, sku_id, product_id, product_name, shop_name, sku_spec, unit_price_cent, quantity, subtotal_cent) VALUES
  (2, 2, 2, 2, 3, 2, '明前龙井 50g', '山野茶铺', JSON_OBJECT('规格','50g'), 8000, 1, 8000);

INSERT INTO payments (id, payment_no, order_id, channel, amount_cent, status, out_trade_no, order_no, paid_at) VALUES
  (2, 'PAY202610010002', 2, 'MOCK', 9000, 'SUCCESS', 'PAY202610010002', 'KD202610010002', DATE_SUB(NOW(), INTERVAL 5 DAY));

INSERT INTO shipments (id, merchant_order_id, carrier, tracking_no, operator_id, shipped_at) VALUES
  (2, 2, '中通', 'ZT2000000002', 4, DATE_SUB(NOW(), INTERVAL 3 DAY));

-- 订单 C：跨商家，部分发货（主单派生状态）
INSERT INTO orders (id, order_no, user_id, status, merchant_count, goods_amount_cent, discount_cent, shipping_fee_cent, pay_amount_cent, address_json, paid_at, created_at) VALUES
  (3, 'KD202610010003', 1, 'PARTIALLY_SHIPPED', 2, 18700, 0, 1000, 19700,
   JSON_OBJECT('receiver','张三','phone','138****8000','province','浙江省','city','杭州市','district','西湖区','detail','文三路 1 号 2 幢 301'),
   DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

INSERT INTO merchant_orders (id, sub_order_no, order_id, merchant_id, status, goods_amount_cent, discount_cent, shipping_fee_cent, pay_amount_cent, shipped_at) VALUES
  (3, 'KD202610010003-1', 3, 1, 'SHIPPED',          5800,  0, 1000, 6800,  DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (4, 'KD202610010003-2', 3, 2, 'PENDING_SHIPMENT', 12900, 0, 0,    12900, NULL);

INSERT INTO order_items (id, merchant_order_id, order_id, merchant_id, sku_id, product_id, product_name, shop_name, sku_spec, unit_price_cent, quantity, subtotal_cent) VALUES
  (3, 3, 3, 1, 8,  7,  '手作陶瓷杯',       '山野杂货', JSON_OBJECT('颜色','米白'), 5800,  1, 5800),
  (4, 4, 3, 2, 13, 11, '65W 氮化镓充电器', '山野茶铺', JSON_OBJECT('颜色','白色'), 12900, 1, 12900);

INSERT INTO payments (id, payment_no, order_id, channel, amount_cent, status, out_trade_no, order_no, paid_at) VALUES
  (3, 'PAY202610010003', 3, 'MOCK', 19700, 'SUCCESS', 'PAY202610010003', 'KD202610010003', DATE_SUB(NOW(), INTERVAL 2 DAY));

INSERT INTO shipments (id, merchant_order_id, carrier, tracking_no, operator_id, shipped_at) VALUES
  (3, 3, '顺丰', 'SF1000000003', 3, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- 状态流水
INSERT INTO order_status_logs (scope, order_id, merchant_order_id, from_status, to_status, operator_type, operator_id, created_at) VALUES
  ('ORDER', 1, NULL, 'PENDING_PAYMENT', 'PAID',      'SYSTEM', NULL, DATE_SUB(NOW(), INTERVAL 20 DAY)),
  ('ORDER', 1, NULL, 'PAID',            'SHIPPED',   'SYSTEM', NULL, DATE_SUB(NOW(), INTERVAL 15 DAY)),
  ('ORDER', 1, NULL, 'SHIPPED',         'COMPLETED', 'USER',   1,    DATE_SUB(NOW(), INTERVAL 12 DAY)),
  ('ORDER', 3, NULL, 'PENDING_PAYMENT', 'PAID',              'SYSTEM', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  ('ORDER', 3, NULL, 'PAID',            'PARTIALLY_SHIPPED', 'SYSTEM', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  ('MERCHANT_ORDER', 3, 3, 'PENDING_SHIPMENT', 'SHIPPED', 'MERCHANT', 3, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- 邮件示例（不真发）
INSERT INTO email_outbox (to_email, template, biz_key, payload_json, status, sent_at) VALUES
  ('customer@kdshop.dev', 'ORDER_CREATED',  'order_created:KD202610010001',  JSON_OBJECT('orderNo','KD202610010001'),  'SENT', DATE_SUB(NOW(), INTERVAL 20 DAY)),
  ('customer@kdshop.dev', 'ORDER_SHIPPED',  'order_shipped:KD202610010001',  JSON_OBJECT('orderNo','KD202610010001','carrier','顺丰'), 'SENT', DATE_SUB(NOW(), INTERVAL 15 DAY));
