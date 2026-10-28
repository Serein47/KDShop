-- V4：手作市集的作品档案与多维标签（ADR-0009）
-- 让数据模型能回答：谁做的、用什么做的、做了多久、有没有瑕疵、能不能改。

ALTER TABLE products
    ADD COLUMN story             TEXT            NULL COMMENT '创作故事，200-800 字',
    ADD COLUMN edition_size      INT             NULL COMMENT '限量件数',
    ADD COLUMN edition_no        INT             NULL COMMENT '当前是第几件',
    ADD COLUMN is_unique         TINYINT     NOT NULL DEFAULT 0 COMMENT '孤品',
    ADD COLUMN flaw_note         VARCHAR(500)    NULL COMMENT '手工痕迹说明',
    ADD COLUMN requires_flaw_ack TINYINT     NOT NULL DEFAULT 0 COMMENT '下单前是否需确认瑕疵说明';

CREATE TABLE tags (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dimension  VARCHAR(16)     NOT NULL COMMENT 'CRAFT/MATERIAL/OCCASION',
    name       VARCHAR(32)     NOT NULL,
    slug       VARCHAR(48)     NOT NULL,
    sort       INT             NOT NULL DEFAULT 0,
    status     VARCHAR(8)      NOT NULL DEFAULT 'ON',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tags_dimension_slug (dimension, slug),
    KEY idx_tags_dimension (dimension, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '标签：工艺/材质/场景';

CREATE TABLE product_tags (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id BIGINT UNSIGNED NOT NULL,
    tag_id     BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_tags (product_id, tag_id),
    KEY idx_product_tags_tag (tag_id, product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品与标签多对多';

CREATE TABLE product_options (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id       BIGINT UNSIGNED NOT NULL,
    name             VARCHAR(32)     NOT NULL COMMENT '如 刻字内容 / 尺寸',
    type             VARCHAR(8)      NOT NULL COMMENT 'TEXT/ENUM',
    required         TINYINT         NOT NULL DEFAULT 0,
    max_length       INT                 NULL COMMENT 'TEXT 类型用',
    choices_json     JSON                NULL COMMENT 'ENUM 类型的可选值',
    price_delta_cent INT             NOT NULL DEFAULT 0,
    sort             INT             NOT NULL DEFAULT 0,
    status           VARCHAR(8)      NOT NULL DEFAULT 'ON',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_product_options_product (product_id, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '定制项定义';

CREATE TABLE order_item_options (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_item_id    BIGINT UNSIGNED NOT NULL,
    option_id        BIGINT UNSIGNED     NULL,
    option_name      VARCHAR(32)     NOT NULL,
    value_text       VARCHAR(200)    NOT NULL COMMENT '用户填写或选择的快照',
    price_delta_cent INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_order_item_options_item (order_item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单行的定制项快照';

ALTER TABLE orders
    ADD COLUMN is_gift      TINYINT      NOT NULL DEFAULT 0,
    ADD COLUMN gift_message VARCHAR(100)     NULL,
    ADD COLUMN gift_wrap    TINYINT      NOT NULL DEFAULT 0;
