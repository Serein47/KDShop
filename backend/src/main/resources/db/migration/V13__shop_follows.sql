-- 关注店铺
CREATE TABLE shop_follows (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    merchant_id BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_shop_follows (user_id, merchant_id),
    KEY idx_shop_follows_merchant (merchant_id),
    CONSTRAINT fk_shop_follows_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_shop_follows_merchant FOREIGN KEY (merchant_id) REFERENCES merchants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
