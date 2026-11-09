-- 平台封禁商家：记录原因
ALTER TABLE merchants ADD COLUMN suspend_reason VARCHAR(200) NULL AFTER reject_reason;
