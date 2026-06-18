-- 产品边界定稿 step：临时议题留痕（规则6）+ 实名投票标记（规则5）+ 通知后锁定（规则8）

-- 议题留痕 + 实名表决标记
ALTER TABLE record_topics
    ADD COLUMN source          VARCHAR(20)  NOT NULL DEFAULT 'live',   -- live=现场新增
    ADD COLUMN created_by_id   BIGINT       NULL,
    ADD COLUMN created_by_name VARCHAR(50)  NULL,
    ADD COLUMN real_name_vote  BOOLEAN      NOT NULL DEFAULT FALSE;    -- 实名公开表决

-- 通知后锁定：记录"通知完成"时间，用于锁定重大字段
ALTER TABLE committee_meetings
    ADD COLUMN notified_at     DATETIME     NULL;
