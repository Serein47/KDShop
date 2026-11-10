-- 演示数据：更多可领取优惠券 + 已购商品的评价
INSERT INTO coupons (code, name, type, value_cent, min_amount_cent, total, used_count, scope, starts_at, ends_at, status)
VALUES
  ('CRAFT30', '手作专区满 300 减 30', 'THRESHOLD', 3000, 30000, 200, 0, 'PLATFORM', '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'ON'),
  ('MUG15', '满 199 减 15', 'THRESHOLD', 1500, 19900, 300, 0, 'PLATFORM', '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'ON'),
  ('TEA12', '满 128 减 12', 'THRESHOLD', 1200, 12800, 500, 0, 'PLATFORM', '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'ON'),
  ('INSTANT5', '无门槛 5 元', 'FIXED', 500, 0, 1000, 0, 'PLATFORM', '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'ON'),
  ('YEAREND50', '满 599 减 50', 'THRESHOLD', 5000, 59900, 100, 0, 'PLATFORM', '2026-01-01 00:00:00', '2027-12-31 23:59:59', 'ON')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 给两个演示顾客先发几张，登录后「我的券」不是空的
INSERT INTO user_coupons (user_id, coupon_id, status)
SELECT u.id, c.id, 'UNUSED'
FROM users u
JOIN coupons c ON c.code IN ('CRAFT30', 'MUG15', 'INSTANT5')
WHERE u.email IN ('customer@kdshop.dev', 'customer2@kdshop.dev')
  AND NOT EXISTS (
    SELECT 1 FROM user_coupons uc WHERE uc.user_id = u.id AND uc.coupon_id = c.id
  );

-- 已完成子单的评价：每件商品最多一条，昵称在接口层脱敏
INSERT INTO reviews (order_item_id, user_id, product_id, merchant_id, rating, content, status)
SELECT oi.id, o.user_id, oi.product_id, oi.merchant_id,
       CASE WHEN oi.id % 6 = 0 THEN 4 ELSE 5 END,
       CASE oi.id % 4
         WHEN 0 THEN '釉色比图片更内敛，杯口打磨得很细，日常用着舒服。'
         WHEN 1 THEN '物流快，包装扎实，收到没有一点磕碰。'
         WHEN 2 THEN '尺寸比想象中稍大，但很实用，做工没得挑。'
         ELSE '第二次回购了，手作的细小痕迹反而让人喜欢。'
       END,
       'VISIBLE'
FROM order_items oi
JOIN merchant_orders mo ON mo.id = oi.merchant_order_id
JOIN orders o ON o.id = oi.order_id
WHERE mo.status = 'COMPLETED'
  AND NOT EXISTS (SELECT 1 FROM reviews r WHERE r.order_item_id = oi.id)
ORDER BY oi.id
LIMIT 12;
