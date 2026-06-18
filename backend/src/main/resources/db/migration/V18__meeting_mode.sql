-- 快速会议模式：会议模式字段（normal / quick），开始会议时确定。
-- 与 ddl-auto=validate 对齐：实体已加 meetingMode，必须有此列。
ALTER TABLE committee_meetings ADD COLUMN meeting_mode VARCHAR(10) NULL;
ALTER TABLE owner_meetings     ADD COLUMN meeting_mode VARCHAR(10) NULL;
