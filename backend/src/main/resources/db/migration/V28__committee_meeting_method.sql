ALTER TABLE committee_meetings
    ADD COLUMN meeting_method VARCHAR(10) NOT NULL DEFAULT 'offline';
