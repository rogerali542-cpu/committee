-- 进行中"录音负责人"：任意已签到参会人可认领/转交。
-- 注：flyway.enabled=false，实际建列靠 Hibernate ddl-auto:update，此文件用于记录 schema 演进。
ALTER TABLE meeting_records
    ADD COLUMN recorder_role_id BIGINT NULL;
