-- V24：作品档案瑕疵确认快照（ISSUE-43）
-- 下单时若商品要求确认手工痕迹，订单行记录当时的勾选状态，售后可查。

ALTER TABLE order_items
    ADD COLUMN flaw_ack TINYINT NOT NULL DEFAULT 0 COMMENT '下单时已确认瑕疵说明';
