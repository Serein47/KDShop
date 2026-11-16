-- V19：尾款通知与逾期关闭（ISSUE-35）
-- 定金支付后订单进入 PRODUCING；承诺发货前 N 天写 balance_notified_at / balance_due_at 并提醒，
-- 截止后仍未付清则关闭订单，定金不退。

ALTER TABLE orders
    ADD COLUMN balance_notified_at DATETIME(3) NULL COMMENT '尾款通知时间',
    ADD COLUMN balance_due_at      DATETIME(3) NULL COMMENT '尾款支付截止时间',
    ADD COLUMN balance_paid_at     DATETIME(3) NULL COMMENT '尾款支付时间';
