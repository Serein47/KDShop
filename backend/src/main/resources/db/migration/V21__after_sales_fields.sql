-- V21：售后闭环补字段（ISSUE-37 ~ 40）
-- 售后表在 V3 已建，这里补齐：质检图片、关联退款单、重申请次数、平台裁决说明；
-- 子单增加「售后中冻结结算」标记。

ALTER TABLE after_sales
    ADD COLUMN refund_no            VARCHAR(32)  NULL COMMENT '关联的退款单号' AFTER refund_amount_cent,
    ADD COLUMN inspect_images_json  JSON         NULL COMMENT '质检图片，最多 3 张' AFTER inspect_note,
    ADD COLUMN reapply_count        INT          NOT NULL DEFAULT 0 COMMENT '补充证据重申请次数，上限 1' AFTER reject_reason,
    ADD COLUMN verdict_note         VARCHAR(300) NULL COMMENT '平台强制裁决说明' AFTER responsibility;

ALTER TABLE merchant_orders
    ADD COLUMN after_sales_frozen TINYINT NOT NULL DEFAULT 0 COMMENT '售后中冻结结算，1=冻结';
