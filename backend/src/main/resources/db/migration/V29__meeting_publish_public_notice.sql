ALTER TABLE meeting_publishes
    ADD COLUMN public_title VARCHAR(300),
    ADD COLUMN public_content TEXT;
