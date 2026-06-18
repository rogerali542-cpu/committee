-- 送达已读回执：区分"主任已送达"与"委员已读"。
-- 送达由主任置 notice_delivered；委员首次打开会议详情时回写 notice_read_at。
ALTER TABLE meeting_deliveries
    ADD COLUMN notice_read_at   DATETIME NULL,
    ADD COLUMN material_read_at DATETIME NULL;
