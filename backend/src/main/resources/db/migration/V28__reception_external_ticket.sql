-- 接待重做（0716 方案 A：外部工单取代内部派单流）的 schema 变更。
-- 当前环境 Flyway 默认关闭，实际建列由 Hibernate ddl-auto:update 完成；本文件记录 schema 演进。
-- ⚠ 下面的 DROP 已在本地 ywh_db 手工执行过（ddl-auto:update 只加列不删列，删列必须手工来）。
--    部署到别的库时需照此执行一遍。

-- 新增：派发外部工单的追踪字段，与 V27 会议待办的三字段同构
ALTER TABLE reception_records ADD COLUMN external_ticket_no VARCHAR(128) NULL;
ALTER TABLE reception_records ADD COLUMN ticket_no VARCHAR(128) NULL;
ALTER TABLE reception_records ADD COLUMN ticket_pushed_at DATETIME NULL;

-- 删除：内部派单流的状态机字段（pending_dispatch→dispatched→processing→replied）
ALTER TABLE reception_records DROP COLUMN property_status;
ALTER TABLE reception_records DROP COLUMN property_reply;
ALTER TABLE reception_records DROP COLUMN property_replied_by;
ALTER TABLE reception_records DROP COLUMN property_replied_at;

-- 删除：ownerFeedback 半成品（写了实体+service 但没有 controller 端点、够不着，见 6745a12）
ALTER TABLE reception_records DROP COLUMN owner_feedback;
ALTER TABLE reception_records DROP COLUMN owner_fed_by;
ALTER TABLE reception_records DROP COLUMN owner_fed_at;

-- 删除：旧办结判定的两个布尔。它们原本是 NOT NULL 无默认值，所以必须「先 DROP 列、再删实体字段」，
-- 反过来做（先删字段）会让 Hibernate 插入时不带这两列 → 新增接待记录直接 SQL 报错。
ALTER TABLE reception_records DROP COLUMN fed_property;
ALTER TABLE reception_records DROP COLUMN fed_owner;

-- 办结口径随之改为：resolution 非空即已办结（见 ReceptionService.isDone）。
-- 本地 12 条演示数据下，新旧口径的已办结数都是 6，换判定不会让首页数字跳变。
