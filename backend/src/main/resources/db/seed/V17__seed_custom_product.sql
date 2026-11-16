-- 演示数据：第三个预售/定制商品（ISSUE-33）
-- 另两个样例在 V5：5 号「雪松香薰蜡烛」预售 7 天、9 号「刻字胡桃木盒」定制 14 天。
-- 这里把 31 号「粗陶手捏小兽摆件」改成定制，凑齐 3 个可演示的商品。

UPDATE products
SET sale_mode        = 'CUSTOM',
    lead_days        = 21,
    deposit_rate     = 4000,
    subtitle         = '手捏成型，一只一个神态，可加刻字',
    story            = '每只小兽先捏形再修坯，入窑后釉色随火候变化，工期约三周。',
    flaw_note        = '手捏痕迹、釉色深浅不一属于手作特征',
    requires_flaw_ack = 1
WHERE id = 31;
