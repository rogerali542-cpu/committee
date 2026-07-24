-- 待办来源关联（0724 领导意见#4）：记录每条会议待办派生自哪条议题的决议，仅存档追溯、前端不展示。
-- source_ref=来源议题标题文本；source_topic_id=匹配到的议题 id（匹配不到为空）。
-- 注：运行时由 ddl-auto:update 自动建列，本文件仅作变更记录。
ALTER TABLE meeting_todos ADD COLUMN source_ref VARCHAR(300) NULL;
ALTER TABLE meeting_todos ADD COLUMN source_topic_id BIGINT NULL;
