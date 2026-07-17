-- 会议待办推送外部工单的追踪字段。
-- 当前环境 Flyway 默认关闭，实际建列由 Hibernate ddl-auto:update 完成；本文件记录 schema 演进。
ALTER TABLE meeting_todos ADD COLUMN external_ticket_no VARCHAR(128) NULL;
ALTER TABLE meeting_todos ADD COLUMN ticket_no VARCHAR(128) NULL;
ALTER TABLE meeting_todos ADD COLUMN ticket_pushed_at DATETIME NULL;
