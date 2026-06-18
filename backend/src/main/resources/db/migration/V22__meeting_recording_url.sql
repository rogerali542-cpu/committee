-- 保留会议录音：上传的音频存档地址落库，会后可回放/下载。
-- 注：flyway.enabled=false，实际建列靠 Hibernate ddl-auto:update，此文件用于记录 schema 演进。
ALTER TABLE meeting_records
    ADD COLUMN recording_url VARCHAR(500) NULL;
