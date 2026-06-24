ALTER TABLE meeting_records DROP COLUMN recording_url;
ALTER TABLE meeting_records DROP COLUMN recorder_role_id;

CREATE TABLE meeting_recordings (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id      BIGINT NOT NULL,
    uploader_role_id BIGINT NULL,
    recording_url   VARCHAR(500) NOT NULL,
    file_name       VARCHAR(255) NULL,
    file_size       BIGINT NULL,
    asr_status      VARCHAR(20) NOT NULL DEFAULT 'none',
    asr_task_id     VARCHAR(100) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (meeting_id) REFERENCES committee_meetings(id),
    FOREIGN KEY (uploader_role_id) REFERENCES user_roles(id)
);

CREATE INDEX idx_meeting_recordings_meeting ON meeting_recordings (meeting_id);
