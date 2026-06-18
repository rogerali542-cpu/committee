-- V11: 签到/签字 → 确认
-- ============================================================

-- record_attendances: signed_in → confirmed（确认参会）
ALTER TABLE record_attendances
    CHANGE COLUMN signed_in confirmed BOOLEAN NOT NULL DEFAULT FALSE;

-- record_attendances: signed → attested（确认签字）
ALTER TABLE record_attendances
    CHANGE COLUMN signed attested BOOLEAN NOT NULL DEFAULT FALSE;
