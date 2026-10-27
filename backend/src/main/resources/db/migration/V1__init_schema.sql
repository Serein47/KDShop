-- KDShop 初始表结构
-- 约定：表名单数 snake_case；金额为整数分；时间为 UTC 存储（DATETIME(3)）；
--       不建物理外键（ADR-0003），一致性由应用层与唯一索引保证。

-- ============ 账号与商家域 ============

CREATE TABLE users (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email             VARCHAR(128)    NOT NULL,
    password_hash     VARCHAR(100)    NOT NULL,
    nickname          VARCHAR(32)     NOT NULL,
    phone             VARCHAR(20)         NULL,
    status            VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    email_verified_at DATETIME(3)         NULL,
    last_login_at     DATETIME(3)         NULL,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户';

CREATE TABLE roles (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code       VARCHAR(32)     NOT NULL COMMENT 'GUEST/CUSTOMER/MERCHANT/SUPPORT/OPERATOR/ADMIN',
    name       VARCHAR(64)     NOT NULL,
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色';

CREATE TABLE user_roles (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    role_id    BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_roles (user_id, role_id),
    KEY idx_user_roles_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色';

CREATE TABLE addresses (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    receiver   VARCHAR(32)     NOT NULL,
    phone      VARCHAR(20)     NOT NULL,
    province   VARCHAR(32)     NOT NULL,
    city       VARCHAR(32)     NOT NULL,
    district   VARCHAR(32)     NOT NULL,
    detail     VARCHAR(200)    NOT NULL,
    is_default TINYINT      NOT NULL DEFAULT 0,
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3)         NULL,
    PRIMARY KEY (id),
    KEY idx_addresses_user (user_id, deleted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收货地址';

CREATE TABLE auth_tokens (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    type       VARCHAR(16)     NOT NULL COMMENT 'VERIFY/RESET',
    token_hash VARCHAR(64)     NOT NULL,
    expires_at DATETIME(3)     NOT NULL,
    used_at    DATETIME(3)         NULL,
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_tokens_hash (token_hash),
    KEY idx_auth_tokens_user (user_id, type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '一次性令牌（只存哈希）';

CREATE TABLE merchants (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '店主',
    shop_name       VARCHAR(64)     NOT NULL,
    logo            VARCHAR(255)        NULL,
    description     VARCHAR(500)        NULL,
    contact_name    VARCHAR(32)     NOT NULL,
    contact_phone   VARCHAR(20)     NOT NULL,
    license_image   VARCHAR(255)        NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE/REJECTED/SUSPENDED',
    reject_reason   VARCHAR(200)        NULL,
    commission_rate INT             NOT NULL DEFAULT 500 COMMENT '基点，500=5%',
    approved_at     DATETIME(3)         NULL,
    approved_by     BIGINT UNSIGNED     NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchants_user (user_id),
    UNIQUE KEY uk_merchants_shop (shop_name),
    KEY idx_merchants_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商家（入驻即一条记录）';

-- ============ 商品域 ============

CREATE TABLE categories (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    parent_id  BIGINT UNSIGNED     NULL,
    name       VARCHAR(32)     NOT NULL,
    sort       INT             NOT NULL DEFAULT 0,
    status     VARCHAR(16)     NOT NULL DEFAULT 'ON',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_categories_parent (parent_id, sort),
    UNIQUE KEY uk_categories_parent_name (parent_id, name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '类目（两级）';

CREATE TABLE products (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    merchant_id   BIGINT UNSIGNED NOT NULL,
    category_id   BIGINT UNSIGNED NOT NULL,
    name          VARCHAR(128)    NOT NULL,
    subtitle      VARCHAR(200)        NULL,
    description   TEXT                NULL,
    main_image    VARCHAR(255)        NULL,
    images_json   JSON                NULL,
    status        VARCHAR(8)      NOT NULL DEFAULT 'ON' COMMENT 'ON/OFF',
    offline_reason VARCHAR(200)       NULL,
    sales_count   INT             NOT NULL DEFAULT 0,
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at    DATETIME(3)         NULL,
    PRIMARY KEY (id),
    KEY idx_products_merchant (merchant_id, status),
    KEY idx_products_category_status (category_id, status),
    FULLTEXT KEY ft_products_name (name, subtitle, description) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品';

CREATE TABLE skus (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id         BIGINT UNSIGNED NOT NULL,
    spec_json          JSON            NOT NULL,
    price_cent         INT             NOT NULL,
    original_price_cent INT                NULL,
    stock              INT             NOT NULL DEFAULT 0,
    locked_stock       INT             NOT NULL DEFAULT 0,
    status             VARCHAR(8)      NOT NULL DEFAULT 'ON',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_skus_product (product_id, status),
    CONSTRAINT ck_skus_stock CHECK (stock >= 0 AND locked_stock >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'SKU（库存与价格的真实单位）';

CREATE TABLE stock_logs (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sku_id      BIGINT UNSIGNED NOT NULL,
    change_qty  INT             NOT NULL,
    before_qty  INT             NOT NULL,
    after_qty   INT             NOT NULL,
    type        VARCHAR(16)     NOT NULL COMMENT 'LOCK/UNLOCK/DEDUCT/RELEASE/ADJUST',
    ref_no      VARCHAR(64)         NULL,
    operator_id BIGINT UNSIGNED     NULL,
    reason      VARCHAR(200)        NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_stock_logs_sku (sku_id, created_at),
    KEY idx_stock_logs_ref (ref_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '库存变动流水';

-- ============ 交易域 ============

CREATE TABLE cart_items (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    sku_id     BIGINT UNSIGNED NOT NULL,
    quantity   INT             NOT NULL,
    selected   TINYINT      NOT NULL DEFAULT 1,
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user_sku (user_id, sku_id),
    CONSTRAINT ck_cart_quantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '购物车行（游客车存前端本地）';

CREATE TABLE orders (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no          VARCHAR(32)     NOT NULL,
    user_id           BIGINT UNSIGNED NOT NULL,
    status            VARCHAR(24)     NOT NULL COMMENT 'PENDING_PAYMENT/PAID/PARTIALLY_SHIPPED/SHIPPED/COMPLETED/CANCELLED/REFUNDED',
    merchant_count    INT             NOT NULL DEFAULT 1,
    goods_amount_cent INT             NOT NULL,
    discount_cent     INT             NOT NULL DEFAULT 0,
    shipping_fee_cent INT             NOT NULL DEFAULT 0,
    pay_amount_cent   INT             NOT NULL,
    address_json      JSON            NOT NULL,
    coupon_id         BIGINT UNSIGNED     NULL,
    remark            VARCHAR(200)        NULL,
    paid_at           DATETIME(3)         NULL,
    completed_at      DATETIME(3)         NULL,
    cancelled_at      DATETIME(3)         NULL,
    cancel_reason     VARCHAR(200)        NULL,
    version           INT             NOT NULL DEFAULT 0,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_no (order_no),
    KEY idx_orders_user_status (user_id, status, created_at),
    KEY idx_orders_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '主单（顾客视角）';

CREATE TABLE merchant_orders (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    sub_order_no         VARCHAR(40)     NOT NULL,
    order_id             BIGINT UNSIGNED NOT NULL,
    merchant_id          BIGINT UNSIGNED NOT NULL,
    status               VARCHAR(24)     NOT NULL COMMENT 'PENDING_PAYMENT/PENDING_SHIPMENT/SHIPPED/COMPLETED/REFUNDING/REFUNDED/CANCELLED',
    goods_amount_cent    INT             NOT NULL,
    discount_cent        INT             NOT NULL DEFAULT 0,
    shipping_fee_cent    INT             NOT NULL DEFAULT 0,
    pay_amount_cent      INT             NOT NULL,
    refunded_amount_cent INT             NOT NULL DEFAULT 0,
    shipped_at           DATETIME(3)         NULL,
    completed_at         DATETIME(3)         NULL,
    settled              TINYINT      NOT NULL DEFAULT 0,
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mo_no (sub_order_no),
    KEY idx_mo_order (order_id),
    KEY idx_mo_merchant_status (merchant_id, status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商家子单';

CREATE TABLE order_items (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    merchant_order_id BIGINT UNSIGNED NOT NULL,
    order_id         BIGINT UNSIGNED NOT NULL,
    merchant_id      BIGINT UNSIGNED NOT NULL,
    sku_id           BIGINT UNSIGNED NOT NULL,
    product_id       BIGINT UNSIGNED NOT NULL,
    product_name     VARCHAR(128)    NOT NULL,
    shop_name        VARCHAR(64)     NOT NULL,
    sku_spec         JSON                NULL,
    unit_price_cent  INT             NOT NULL,
    quantity         INT             NOT NULL,
    subtotal_cent    INT             NOT NULL,
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_oi_mo (merchant_order_id),
    KEY idx_oi_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单行（商品快照）';

CREATE TABLE payments (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_no    VARCHAR(32)     NOT NULL,
    order_id      BIGINT UNSIGNED NOT NULL,
    channel       VARCHAR(16)     NOT NULL DEFAULT 'MOCK',
    amount_cent   INT             NOT NULL,
    status        VARCHAR(16)     NOT NULL COMMENT 'INIT/PENDING/SUCCESS/FAILED/CLOSED/REFUNDED',
    out_trade_no  VARCHAR(32)     NOT NULL,
    order_no      VARCHAR(32)     NOT NULL,
    paid_at       DATETIME(3)         NULL,
    callback_at   DATETIME(3)         NULL,
    callback_raw  JSON                NULL,
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_no (payment_no),
    UNIQUE KEY uk_payments_out_trade_no (out_trade_no),
    KEY idx_payments_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '支付单（挂在主单上）';

CREATE TABLE refunds (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    refund_no         VARCHAR(32)     NOT NULL,
    merchant_order_id BIGINT UNSIGNED NOT NULL,
    order_id          BIGINT UNSIGNED NOT NULL,
    merchant_id       BIGINT UNSIGNED NOT NULL,
    amount_cent       INT             NOT NULL,
    reason            VARCHAR(200)    NOT NULL,
    status            VARCHAR(16)     NOT NULL COMMENT 'APPLIED/APPROVED/REJECTED/REFUNDED',
    reject_reason     VARCHAR(200)        NULL,
    operator_type     VARCHAR(16)         NULL COMMENT 'MERCHANT/SUPPORT',
    operator_id       BIGINT UNSIGNED     NULL,
    finished_at       DATETIME(3)         NULL,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refunds_no (refund_no),
    KEY idx_refunds_merchant (merchant_id, status),
    KEY idx_refunds_mo (merchant_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '退款单（按子单整单退）';

CREATE TABLE shipments (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    merchant_order_id BIGINT UNSIGNED NOT NULL,
    carrier           VARCHAR(32)     NOT NULL,
    tracking_no       VARCHAR(64)     NOT NULL,
    operator_id       BIGINT UNSIGNED     NULL,
    shipped_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipments_mo (merchant_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '发货记录';

CREATE TABLE order_status_logs (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    scope             VARCHAR(16)     NOT NULL COMMENT 'ORDER/MERCHANT_ORDER',
    order_id          BIGINT UNSIGNED NOT NULL,
    merchant_order_id BIGINT UNSIGNED     NULL,
    from_status       VARCHAR(24)         NULL,
    to_status         VARCHAR(24)     NOT NULL,
    operator_type     VARCHAR(16)     NOT NULL COMMENT 'USER/MERCHANT/ADMIN/SYSTEM',
    operator_id       BIGINT UNSIGNED     NULL,
    reason            VARCHAR(200)        NULL,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_osl_order (order_id, created_at),
    KEY idx_osl_mo (merchant_order_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '状态变更流水';

-- ============ 结算域 ============

CREATE TABLE settlements (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    settlement_no    VARCHAR(32)     NOT NULL,
    merchant_id      BIGINT UNSIGNED NOT NULL,
    period_start     DATE            NOT NULL,
    period_end       DATE            NOT NULL,
    gross_amount_cent INT            NOT NULL,
    commission_cent  INT             NOT NULL,
    refund_cent      INT             NOT NULL DEFAULT 0,
    net_amount_cent  INT             NOT NULL,
    status           VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SETTLED',
    settled_at       DATETIME(3)         NULL,
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlements_no (settlement_no),
    UNIQUE KEY uk_settlements_merchant_period (merchant_id, period_start, period_end),
    KEY idx_settlements_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '结算单';

CREATE TABLE settlement_items (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    settlement_id     BIGINT UNSIGNED NOT NULL,
    merchant_order_id BIGINT UNSIGNED NOT NULL,
    gross_amount_cent INT             NOT NULL,
    commission_cent   INT             NOT NULL,
    net_amount_cent   INT             NOT NULL,
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_items_mo (merchant_order_id),
    KEY idx_settlement_items_settlement (settlement_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '结算明细（一子单只能被结算一次）';

-- ============ 营销与支撑域 ============

CREATE TABLE coupons (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code           VARCHAR(32)     NOT NULL,
    name           VARCHAR(64)     NOT NULL,
    type           VARCHAR(16)     NOT NULL COMMENT 'FIXED/THRESHOLD',
    value_cent     INT             NOT NULL,
    min_amount_cent INT            NOT NULL DEFAULT 0,
    total          INT             NOT NULL,
    used_count     INT             NOT NULL DEFAULT 0,
    scope          VARCHAR(16)     NOT NULL DEFAULT 'PLATFORM',
    starts_at      DATETIME(3)     NOT NULL,
    ends_at        DATETIME(3)     NOT NULL,
    status         VARCHAR(8)      NOT NULL DEFAULT 'ON',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupons_code (code),
    KEY idx_coupons_status (status, ends_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '平台优惠券';

CREATE TABLE user_coupons (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id        BIGINT UNSIGNED NOT NULL,
    coupon_id      BIGINT UNSIGNED NOT NULL,
    status         VARCHAR(16)     NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED/USED/EXPIRED',
    order_id       BIGINT UNSIGNED     NULL,
    used_at        DATETIME(3)         NULL,
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_coupons (user_id, coupon_id),
    KEY idx_user_coupons_status (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户券';

CREATE TABLE favorites (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorites (user_id, product_id),
    KEY idx_favorites_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收藏';

CREATE TABLE reviews (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_item_id  BIGINT UNSIGNED NOT NULL,
    user_id        BIGINT UNSIGNED NOT NULL,
    product_id     BIGINT UNSIGNED NOT NULL,
    merchant_id    BIGINT UNSIGNED NOT NULL,
    rating         TINYINT         NOT NULL,
    content        VARCHAR(500)    NOT NULL,
    status         VARCHAR(16)     NOT NULL DEFAULT 'VISIBLE' COMMENT 'VISIBLE/HIDDEN',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviews_item (order_item_id),
    KEY idx_reviews_product (product_id, status, created_at),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品评价（先发后审）';

CREATE TABLE email_outbox (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    to_email     VARCHAR(128)    NOT NULL,
    template     VARCHAR(64)     NOT NULL,
    biz_key      VARCHAR(128)    NOT NULL,
    payload_json JSON                NULL,
    status       VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    error        VARCHAR(500)        NULL,
    sent_at      DATETIME(3)         NULL,
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_biz_key (biz_key),
    KEY idx_outbox_status (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '邮件发件箱（不真发，写库+可查看）';

CREATE TABLE audit_logs (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_type  VARCHAR(16)     NOT NULL,
    actor_id    BIGINT UNSIGNED     NULL,
    action      VARCHAR(64)     NOT NULL,
    target_type VARCHAR(32)         NULL,
    target_id   VARCHAR(64)         NULL,
    before_json JSON                NULL,
    after_json  JSON                NULL,
    ip          VARCHAR(45)         NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_actor (actor_id, created_at),
    KEY idx_audit_target (target_type, target_id),
    KEY idx_audit_action (action, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审计日志（只增不改）';
