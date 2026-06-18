-- 产品边界定稿 step 2 平移：业主大会公示状态机 + 纪要修订版本历史

ALTER TABLE owner_meeting_publishes
    ADD COLUMN published_by_id   BIGINT       NULL,
    ADD COLUMN published_by_name VARCHAR(50)  NULL,
    ADD COLUMN published_at      DATETIME     NULL,
    ADD COLUMN withdrawn         BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN withdrawn_by_id   BIGINT       NULL,
    ADD COLUMN withdrawn_by_name VARCHAR(50)  NULL,
    ADD COLUMN withdrawn_at      DATETIME     NULL,
    ADD COLUMN withdraw_reason   VARCHAR(500) NULL;

UPDATE owner_meeting_publishes
SET published_at = TIMESTAMP(publish_date)
WHERE published = TRUE AND publish_date IS NOT NULL;

CREATE TABLE owner_minutes_revisions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id  BIGINT       NOT NULL,
    version_no  INT          NOT NULL,
    content     TEXT,
    editor_id   BIGINT,
    editor_name VARCHAR(50),
    created_at  DATETIME     NOT NULL,
    FOREIGN KEY (meeting_id) REFERENCES owner_meetings(id)
);

CREATE INDEX idx_owner_minutes_revisions_meeting ON owner_minutes_revisions (meeting_id, version_no);
