-- 商家发货信息：承运商与运单号
ALTER TABLE merchant_orders ADD COLUMN carrier VARCHAR(32) NULL AFTER completed_at;
ALTER TABLE merchant_orders ADD COLUMN tracking_no VARCHAR(64) NULL AFTER carrier;
