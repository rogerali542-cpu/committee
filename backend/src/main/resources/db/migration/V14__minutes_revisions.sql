-- 产品边界定稿 step 2：会议纪要修订版本历史（见 §5）

CREATE TABLE minutes_revisions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id  BIGINT       NOT NULL,
    version_no  INT          NOT NULL,
    content     TEXT,
    editor_id   BIGINT,
    editor_name VARCHAR(50),
    created_at  DATETIME     NOT NULL,
    FOREIGN KEY (meeting_id) REFERENCES committee_meetings(id)
);

CREATE INDEX idx_minutes_revisions_meeting ON minutes_revisions (meeting_id, version_no);
