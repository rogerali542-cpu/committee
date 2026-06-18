-- V7: topic_votes 加 selected_id 支持多选一
ALTER TABLE topic_votes
    ADD COLUMN selected_id BIGINT AFTER choice,
    MODIFY COLUMN choice VARCHAR(10) NULL;
