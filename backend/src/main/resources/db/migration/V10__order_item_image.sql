-- 订单明细补商品图快照（下单时写入，历史订单从商品表回填）
ALTER TABLE order_items ADD COLUMN product_image VARCHAR(255) NULL AFTER product_name;

UPDATE order_items oi
JOIN products p ON p.id = oi.product_id
SET oi.product_image = p.main_image
WHERE oi.product_image IS NULL;
