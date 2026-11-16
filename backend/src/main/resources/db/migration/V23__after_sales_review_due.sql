-- V23：售后审核时限与平台裁决（ISSUE-39）
-- 申请/重申请时写 review_due_at = now + 48 小时；超时后仅退款自动通过，退货退款升级平台。

ALTER TABLE after_sales
    ADD COLUMN review_due_at DATETIME(3) NULL COMMENT '商家审核截止时间' AFTER status;
