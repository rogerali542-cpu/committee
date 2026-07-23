-- 直接归档（0723 补实现）：此前前端「直接归档/撤销归档/资料库」调用的后端能力整体缺失（测试反馈#6）。
-- archived 为空视为未归档（旧数据兼容）；归档是终局动作，未结束的会一并置为 ended。
-- 注：运行时由 ddl-auto:update 自动建列，本文件仅作变更记录。
ALTER TABLE committee_meetings ADD COLUMN archived BIT NULL;
ALTER TABLE committee_meetings ADD COLUMN archived_at DATETIME NULL;
