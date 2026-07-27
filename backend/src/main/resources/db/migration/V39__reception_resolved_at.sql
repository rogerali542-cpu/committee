-- 接待记录办结时间（0727）。
-- 有来访的接待「办结」后需公示一个月，期满转「已留档」；无人来访登记即办结、直接留档。
-- 前端据 resolved_at 判断「公示中 / 已留档」，后端 isDone 口径不变（仍以填了处理结果为准）。
ALTER TABLE reception_records ADD COLUMN resolved_at DATETIME NULL;

-- 历史已办结记录（已填处理结果，含无人来访的「无需处理」）回填办结时间为创建时间，
-- 使其按记录年龄决定是否已过一个月公示期（多数历史记录将直接显示为「已留档」）。
UPDATE reception_records
   SET resolved_at = created_at
 WHERE resolved_at IS NULL
   AND resolution IS NOT NULL
   AND TRIM(resolution) <> '';
