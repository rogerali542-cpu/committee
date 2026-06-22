-- 注：flyway.enabled=false，实际建列靠 Hibernate ddl-auto:update，此文件用于记录 schema 演进。
ALTER TABLE meeting_records
    ADD COLUMN ai_topic_report_text TEXT NULL,
    ADD COLUMN todo_list_text TEXT NULL;
