-- 半年演示数据中，2026-05-22 已为第3次例会。
-- 将其后的旧测试会议统一顺延为第4次，避免首页序号与历史材料冲突。
UPDATE committee_meetings
SET title = REPLACE(title, '第3次', '第4次'),
    notice_title = CASE
        WHEN notice_title IS NULL THEN NULL
        ELSE REPLACE(notice_title, '第3次', '第4次')
    END
WHERE meeting_date > '2026-05-22'
  AND title IN ('2026年第3次业委会例会', '2026年第3次业主委员会例会');
