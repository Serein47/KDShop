-- 类目分两组：通用类目与特色专区（手作家居/文具/礼物）
ALTER TABLE categories
    ADD COLUMN featured TINYINT NOT NULL DEFAULT 0 COMMENT '1=特色专区';
