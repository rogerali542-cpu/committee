-- 向真实材料看齐（0723）：
-- 1) 业委会届别：备案证/公章/落款均带「第X届」，落款全称=小区名+业主委员会（第X届）
-- 2) 列席人员：居委/街道/物业等非委员到会者，会议记录实到写「委员数+列席数」
-- 注：运行时由 ddl-auto:update 自动建列，本文件仅作变更记录。
ALTER TABLE communities ADD COLUMN committee_term VARCHAR(20) NULL;
ALTER TABLE meeting_records ADD COLUMN observers_text VARCHAR(500) NULL;
