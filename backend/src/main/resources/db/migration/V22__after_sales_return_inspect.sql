-- V22：退货物流与质检时限（ISSUE-38）
-- 商家同意退货退款时写 return_due_at；商家确认收货时写 inspect_due_at。
-- stock_restored 防止自动质检与人工质检并发时重复回补库存。

ALTER TABLE after_sales
    ADD COLUMN return_due_at  DATETIME(3) NULL COMMENT '寄回截止时间' AFTER return_shipped_at,
    ADD COLUMN inspect_due_at DATETIME(3) NULL COMMENT '质检截止时间' AFTER received_at,
    ADD COLUMN stock_restored TINYINT NOT NULL DEFAULT 0 COMMENT '良品回补库存标记' AFTER inspect_images_json;
