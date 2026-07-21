-- 当前环境默认由 Hibernate ddl-auto:update 建列；本文件记录 schema 演进。
ALTER TABLE record_attendances ADD COLUMN attendance_mode VARCHAR(20) NULL;
ALTER TABLE record_attendances ADD COLUMN proxy_sign_authorized BIT NOT NULL DEFAULT 0;
ALTER TABLE record_attendances ADD COLUMN proxy_sign_authorized_at DATETIME NULL;
