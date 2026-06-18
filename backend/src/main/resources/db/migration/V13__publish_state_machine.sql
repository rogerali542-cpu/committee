-- 产品边界定稿 step 1：公示状态机 + 物业可见性配置

-- 小区级配置：公示后是否对物业开放（默认关闭）
ALTER TABLE communities
    ADD COLUMN disclose_to_property BOOLEAN NOT NULL DEFAULT FALSE;

-- 公示成为正式、可追溯动作：公示人/公示时间，以及撤回留痕
ALTER TABLE meeting_publishes
    ADD COLUMN published_by_id   BIGINT       NULL,
    ADD COLUMN published_by_name VARCHAR(50)  NULL,
    ADD COLUMN published_at      DATETIME     NULL,
    ADD COLUMN withdrawn         BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN withdrawn_by_id   BIGINT       NULL,
    ADD COLUMN withdrawn_by_name VARCHAR(50)  NULL,
    ADD COLUMN withdrawn_at      DATETIME     NULL,
    ADD COLUMN withdraw_reason   VARCHAR(500) NULL;

-- 历史数据回填：已公示的记录用 publish_date 作为公示时间
UPDATE meeting_publishes
SET published_at = TIMESTAMP(publish_date)
WHERE published = TRUE AND publish_date IS NOT NULL;
