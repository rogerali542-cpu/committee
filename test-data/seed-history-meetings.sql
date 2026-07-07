SET NAMES utf8mb4;

-- 幂等：先清掉同名历史例会（连带公示记录），避免重复执行插重复
DELETE FROM meeting_publishes WHERE meeting_id IN (
  SELECT id FROM committee_meetings WHERE title IN ('2026年第1次业委会例会','2026年第2次业委会例会'));
DELETE FROM committee_meetings WHERE title IN ('2026年第1次业委会例会','2026年第2次业委会例会');

-- 两条历史例会：已结束 + 有效（valid → 进「历史记录」；前端按标题挡在首页外）
INSERT INTO committee_meetings
  (community_id, title, meeting_date, meeting_time, location, stage, compliance, created_by, created_at, updated_at)
VALUES
  (1, '2026年第1次业委会例会', '2026-02-10', '10:00:00', '社区党群服务中心二楼会议室', 'ended', 'valid', 1, '2026-02-10 10:00:00', '2026-02-10 12:00:00'),
  (1, '2026年第2次业委会例会', '2026-04-14', '10:00:00', '社区党群服务中心二楼会议室', 'ended', 'valid', 1, '2026-04-14 10:00:00', '2026-04-14 12:00:00');

-- 对应公示记录（已公示 → 历史记录里徽标显示「已公示」）
INSERT INTO meeting_publishes
  (meeting_id, published, publish_date, published_at, published_by_id, published_by_name, withdrawn)
SELECT id, 1, '2026-02-11', '2026-02-11 09:00:00', 1, '张建国', 0
  FROM committee_meetings WHERE title = '2026年第1次业委会例会';
INSERT INTO meeting_publishes
  (meeting_id, published, publish_date, published_at, published_by_id, published_by_name, withdrawn)
SELECT id, 1, '2026-04-15', '2026-04-15 09:00:00', 1, '张建国', 0
  FROM committee_meetings WHERE title = '2026年第2次业委会例会';

-- 回显确认
SELECT m.id, m.title, m.stage, m.compliance, m.meeting_date, p.published
  FROM committee_meetings m LEFT JOIN meeting_publishes p ON p.meeting_id = m.id
  WHERE m.title IN ('2026年第1次业委会例会','2026年第2次业委会例会');
