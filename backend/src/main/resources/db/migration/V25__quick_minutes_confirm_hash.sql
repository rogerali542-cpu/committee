-- 注：flyway.enabled=false，实际建列靠 Hibernate ddl-auto:update，此文件用于记录 schema 演进。
ALTER TABLE meeting_records
    ADD COLUMN quick_confirm_hash varchar(64) NULL,
    ADD COLUMN minutes_confirm_hash varchar(64) NULL;
