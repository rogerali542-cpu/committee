-- 事项基础模型：为未来串联接待、会议议题、待办、公示和档案预留。
-- 本迁移只建立独立基础表；现有业务表不增加外键，本批演示数据也不写入该表。
CREATE TABLE IF NOT EXISTS work_items (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    community_id    BIGINT       NOT NULL,
    title           VARCHAR(300) NOT NULL,
    description     TEXT         NULL,
    source_type     VARCHAR(30)  NULL,
    source_id       BIGINT       NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'todo',
    owner_name      VARCHAR(100) NULL,
    due_date        DATE         NULL,
    created_by      BIGINT       NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    closed_at       DATETIME(6)  NULL,
    INDEX idx_work_items_community_status (community_id, status),
    INDEX idx_work_items_source (source_type, source_id),
    INDEX idx_work_items_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
