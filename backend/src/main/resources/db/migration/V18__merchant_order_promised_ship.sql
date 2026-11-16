-- V18：预售/定制子单的承诺发货时间（ISSUE-34）
-- 定金支付成功时写入 promised_ship_at = 支付时间 + 该子单最长工期；
-- 主单 orders.promised_ship_at 取全部子单的最大值，用于列表与尾款提醒。

ALTER TABLE merchant_orders
    ADD COLUMN promised_ship_at DATETIME(3) NULL COMMENT '承诺发货时间（定金支付时写入）';
