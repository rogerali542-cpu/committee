-- V8: proxy action audit fields for committee attendance and votes
ALTER TABLE record_attendances
    ADD COLUMN operator_id BIGINT NULL AFTER signed,
    ADD COLUMN is_proxy BOOLEAN NOT NULL DEFAULT FALSE AFTER operator_id,
    ADD COLUMN proof_url VARCHAR(500) NULL AFTER is_proxy,
    ADD COLUMN operated_at DATETIME NULL AFTER proof_url,
    ADD CONSTRAINT fk_record_attendance_operator FOREIGN KEY (operator_id) REFERENCES user_roles(id);

ALTER TABLE topic_votes
    ADD COLUMN operator_id BIGINT NULL AFTER selected_id,
    ADD COLUMN is_proxy BOOLEAN NOT NULL DEFAULT FALSE AFTER operator_id,
    ADD COLUMN proof_url VARCHAR(500) NULL AFTER is_proxy,
    ADD COLUMN operated_at DATETIME NULL AFTER proof_url,
    ADD CONSTRAINT fk_topic_vote_operator FOREIGN KEY (operator_id) REFERENCES user_roles(id);
