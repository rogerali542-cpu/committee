-- 接待诉求派发外部工单的追踪字段（0716 方案 A：外部工单取代内部派单流）。
-- 与 V27 会议待办的三字段同构。
-- 当前环境 Flyway 默认关闭，实际建列由 Hibernate ddl-auto:update 完成；本文件记录 schema 演进。
ALTER TABLE reception_records ADD COLUMN external_ticket_no VARCHAR(128) NULL;
ALTER TABLE reception_records ADD COLUMN ticket_no VARCHAR(128) NULL;
ALTER TABLE reception_records ADD COLUMN ticket_pushed_at DATETIME NULL;

-- ── 以下列已随内部派单流下线，实体里已删，但列仍留在库中 ──
-- ddl-auto:update 只加列不删列，所以它们不会被自动清理；留着也不影响读写（全部 nullable）。
-- 需要真正清掉时手工执行：
-- ALTER TABLE reception_records DROP COLUMN property_status;
-- ALTER TABLE reception_records DROP COLUMN property_reply;
-- ALTER TABLE reception_records DROP COLUMN property_replied_by;
-- ALTER TABLE reception_records DROP COLUMN property_replied_at;
-- ALTER TABLE reception_records DROP COLUMN owner_feedback;
-- ALTER TABLE reception_records DROP COLUMN owner_fed_by;
-- ALTER TABLE reception_records DROP COLUMN owner_fed_at;

-- ⚠ fed_property / fed_owner 是 NOT NULL 无默认值，实体里必须保留字段（恒 false），
-- 否则 Hibernate 插入时不带这两列 → SQL 报错。要清理必须先：
-- ALTER TABLE reception_records MODIFY COLUMN fed_property TINYINT(1) NULL DEFAULT 0;
-- ALTER TABLE reception_records MODIFY COLUMN fed_owner    TINYINT(1) NULL DEFAULT 0;
-- 再从 ReceptionRecord 里删掉这两个字段。
