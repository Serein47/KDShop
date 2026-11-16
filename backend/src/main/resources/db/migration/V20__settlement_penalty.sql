-- V20：工期超时赔付进结算（ISSUE-36）
-- penalties 表在 V3 已建；这里给结算单与明细补上赔付扣减字段。

ALTER TABLE settlements
    ADD COLUMN penalty_cent INT NOT NULL DEFAULT 0 COMMENT '赔付扣减' AFTER refund_cent;

ALTER TABLE settlement_items
    ADD COLUMN refund_cent  INT NOT NULL DEFAULT 0 COMMENT '本子单退款' AFTER commission_cent,
    ADD COLUMN penalty_cent INT NOT NULL DEFAULT 0 COMMENT '本子单赔付扣减' AFTER refund_cent;
