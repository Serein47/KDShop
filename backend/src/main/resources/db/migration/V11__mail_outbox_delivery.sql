-- 邮件投递重试：记录尝试次数与下次重试时间
ALTER TABLE email_outbox
    ADD COLUMN attempts      INT         NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN next_retry_at DATETIME(3)     NULL AFTER error,
    ADD KEY idx_outbox_retry (status, next_retry_at);
