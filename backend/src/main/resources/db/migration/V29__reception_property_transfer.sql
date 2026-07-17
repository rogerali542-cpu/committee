-- 接待处理页拆两条路（0717）：派发工单 / 转物业处理。
-- 当前环境 Flyway 默认关闭，实际建列由 Hibernate ddl-auto:update 完成；本文件记录 schema 演进。
-- 本次只有 ADD，ddl-auto:update 会自动加上，不需要像 V28 那样手工执行（它只加列不删列）。

-- 转物业标记。时间戳兼作布尔：NULL = 没转过。
-- 跟 V28 加的 ticket_* 三件套是两条不同的路：
--   ticket_*                → 真的 POST 到外部工单系统，有对方单号，派出去撤不回
--   property_transferred_at → 不发任何请求，委员自己联系物业后在这记一笔，可反悔（前端是开关）
-- 必须 NULL 可空：既是「没转过」的表达，也让 ddl-auto 能安全加列（NOT NULL 无默认值会炸已有行）。
ALTER TABLE reception_records ADD COLUMN property_transferred_at DATETIME NULL;

-- 办结口径不变：仍是 resolution 非空（见 ReceptionService.isDone）。
-- 转物业故意不参与 —— 转出去 ≠ 办结，事情仍挂在委员名下，等他知道结果、填了处理结果才闭环。
