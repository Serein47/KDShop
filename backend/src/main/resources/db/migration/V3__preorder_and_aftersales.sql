-- V3：定制交易与售后闭环（ADR-0008）
-- 增量式变更：只加列、加表，不动已有数据。

ALTER TABLE products
    ADD COLUMN sale_mode    VARCHAR(16) NOT NULL DEFAULT 'IN_STOCK' COMMENT 'IN_STOCK/PREORDER/CUSTOM',
    ADD COLUMN lead_days    INT             NULL COMMENT '定制/预售工期（天）',
    ADD COLUMN deposit_rate INT         NOT NULL DEFAULT 0 COMMENT '定金比例，基点，3000=30%';

ALTER TABLE orders
    ADD COLUMN order_type       VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/PREORDER/CUSTOM',
    ADD COLUMN deposit_cent     INT         NOT NULL DEFAULT 0,
    ADD COLUMN balance_cent     INT         NOT NULL DEFAULT 0,
    ADD COLUMN promised_ship_at DATETIME(3)     NULL,
    ADD COLUMN penalty_cent     INT         NOT NULL DEFAULT 0;

ALTER TABLE payments
    ADD COLUMN purpose VARCHAR(16) NOT NULL DEFAULT 'FULL' COMMENT 'FULL/DEPOSIT/BALANCE';

CREATE TABLE after_sales (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    as_no               VARCHAR(32)     NOT NULL,
    merchant_order_id   BIGINT UNSIGNED NOT NULL,
    order_id            BIGINT UNSIGNED NOT NULL,
    merchant_id         BIGINT UNSIGNED NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    type                VARCHAR(16)     NOT NULL COMMENT 'REFUND_ONLY/RETURN_REFUND',
    reason_code         VARCHAR(32)     NOT NULL,
    description         VARCHAR(500)        NULL,
    evidence_json       JSON                NULL COMMENT '最多 3 张图',
    status              VARCHAR(24)     NOT NULL COMMENT 'APPLIED/APPROVED/REJECTED/RETURNING/RECEIVED/INSPECTED/REFUNDED/CLOSED',
    refund_amount_cent  INT             NOT NULL DEFAULT 0,
    return_carrier      VARCHAR(32)         NULL,
    return_tracking_no  VARCHAR(64)         NULL,
    return_shipped_at   DATETIME(3)         NULL,
    received_at         DATETIME(3)         NULL,
    inspect_result      VARCHAR(16)         NULL COMMENT 'GOOD/DEFECTIVE',
    inspect_note        VARCHAR(300)        NULL,
    responsibility      VARCHAR(16)         NULL COMMENT 'MERCHANT/LOGISTICS/CUSTOMER',
    reject_reason       VARCHAR(200)        NULL,
    operator_type       VARCHAR(16)         NULL,
    operator_id         BIGINT UNSIGNED     NULL,
    completed_at        DATETIME(3)         NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_after_sales_no (as_no),
    KEY idx_after_sales_mo (merchant_order_id),
    KEY idx_after_sales_merchant_status (merchant_id, status, created_at),
    KEY idx_after_sales_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '售后单';

CREATE TABLE after_sales_logs (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    after_sales_id BIGINT UNSIGNED NOT NULL,
    from_status    VARCHAR(24)         NULL,
    to_status      VARCHAR(24)     NOT NULL,
    operator_type  VARCHAR(16)     NOT NULL COMMENT 'USER/MERCHANT/ADMIN/SYSTEM',
    operator_id    BIGINT UNSIGNED     NULL,
    note           VARCHAR(300)        NULL,
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_as_logs_as (after_sales_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '售后状态流水';

CREATE TABLE penalties (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    penalty_no        VARCHAR(32)     NOT NULL,
    merchant_order_id BIGINT UNSIGNED NOT NULL,
    order_id          BIGINT UNSIGNED NOT NULL,
    merchant_id       BIGINT UNSIGNED NOT NULL,
    days_overdue      INT             NOT NULL,
    amount_cent       INT             NOT NULL,
    status            VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SETTLED',
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_penalties_no (penalty_no),
    UNIQUE KEY uk_penalties_mo_day (merchant_order_id, days_overdue),
    KEY idx_penalties_merchant (merchant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工期超时赔付';
