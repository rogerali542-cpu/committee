-- V4: 补充各阶段多样化测试数据

-- ===== 准备阶段：新增 2 条 =====
-- 准备-1：全部送达，待开始
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(7, 1, '2026年第5次业委会例会（待开始）', '2026-06-20', '09:00', '社区会议室', 'preparing', NULL, '审议小区电梯维修基金使用方案', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(7, 1, true, true), (7, 2, true, true), (7, 3, true, true),
(7, 4, true, true), (7, 5, true, true), (7, 6, true, true), (7, 7, true, true);

-- 准备-2：日期未填
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(8, 1, '2026年下半年工作部署会', NULL, '14:00', '待定', 'preparing', NULL, '部署下半年重点工作任务', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(8, 1, false, false), (8, 2, false, false), (8, 3, false, false),
(8, 4, false, false), (8, 5, false, false), (8, 6, false, false), (8, 7, false, false);

-- ===== 进行中：新增 2 条 =====
-- 进行-1：签到全部完成，待投票
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(9, 1, '2026年6月临时业委会会议', '2026-06-08', '16:00', '线上腾讯会议', 'ongoing', NULL, '紧急讨论小区围墙修缮事宜', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(9, 1, true, true), (9, 2, true, true), (9, 3, true, true),
(9, 4, true, true), (9, 5, true, true), (9, 6, true, true), (9, 7, true, true);

INSERT INTO meeting_records (id, meeting_id, has_decision, has_major_issue, juwei_name, juwei_signed) VALUES
(6, 9, true, false, '王红梅（社区居委会）', false);

INSERT INTO record_attendances (record_id, user_role_id, signed_in, signed) VALUES
(6, 1, true, true), (6, 2, true, true), (6, 3, true, true),
(6, 4, true, true), (6, 5, true, true), (6, 6, true, true), (6, 7, true, true);

INSERT INTO record_topics (id, record_id, title, type, sort_order) VALUES
(7, 6, '小区围墙修缮方案', 'decision', 1),
(8, 6, '临时聘用工程队', 'decision', 2);

-- 暂无投票数据（全员待表决）

-- 进行-2：签到不全，无法形成决议
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(10, 1, '2026年5月物业续聘讨论会', '2026-05-25', '10:00', '物业办公室', 'ongoing', NULL, '讨论是否续聘当前物业公司', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(10, 1, true, true), (10, 2, true, true), (10, 3, true, true),
(10, 4, true, true), (10, 5, true, true), (10, 6, true, true), (10, 7, true, true);

INSERT INTO meeting_records (id, meeting_id, has_decision, has_major_issue, juwei_name, juwei_signed) VALUES
(7, 10, true, true, '王红梅（社区居委会）', false);

INSERT INTO record_attendances (record_id, user_role_id, signed_in, signed) VALUES
(7, 1, true, true), (7, 2, true, true), (7, 3, true, false),
(7, 4, false, false), (7, 5, false, false), (7, 6, true, false), (7, 7, false, false);
-- 只有4人签到，未过半（需≥4人，这里刚好4人 = 过半）

INSERT INTO record_topics (id, record_id, title, type, sort_order) VALUES
(9, 7, '物业公司续聘决议', 'major', 1);

INSERT INTO topic_votes (topic_id, user_role_id, choice) VALUES
(9, 1, 'for_vote'), (9, 2, 'for_vote');

-- ===== 已结束：新增 4 条 =====
-- 结束-1：有效+已公示
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(11, 1, '2025年第4次业委会例会', '2025-08-20', '14:00', '社区活动室', 'ended', 'valid', '审议2025年度上半年财务报告', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(11, 1, true, true), (11, 2, true, true), (11, 3, true, true),
(11, 4, true, true), (11, 5, true, true), (11, 6, true, true), (11, 7, true, true);

INSERT INTO meeting_publishes (meeting_id, published, publish_date) VALUES (11, true, '2025-08-21');

INSERT INTO meeting_records (id, meeting_id, has_decision, has_major_issue) VALUES (8, 11, true, false);

INSERT INTO record_attendances (record_id, user_role_id, signed_in, signed) VALUES
(8, 1, true, true), (8, 2, true, true), (8, 3, true, true),
(8, 4, true, true), (8, 5, true, true), (8, 6, true, true), (8, 7, true, true);

INSERT INTO record_topics (id, record_id, title, type, sort_order) VALUES
(10, 8, '2025上半年财务报告', 'decision', 1);

INSERT INTO topic_votes (topic_id, user_role_id, choice) VALUES
(10, 1, 'for_vote'), (10, 2, 'for_vote'), (10, 3, 'for_vote'),
(10, 4, 'for_vote'), (10, 5, 'for_vote'), (10, 6, 'for_vote'), (10, 7, 'for_vote');

-- 结束-2：有效+逾期公示
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(12, 1, '2025年第3次业委会例会', '2025-06-10', '09:30', '社区会议室', 'ended', 'valid', '审议物业费调整方案', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(12, 1, true, true), (12, 2, true, true), (12, 3, true, true),
(12, 4, true, true), (12, 5, true, true), (12, 6, true, true), (12, 7, true, true);

INSERT INTO meeting_publishes (meeting_id, published, publish_date) VALUES (12, true, '2025-06-18');
-- 会议日期 6-10，公示截止 6-13，实际 6-18 公示 = 逾期！

INSERT INTO meeting_records (id, meeting_id, has_decision, has_major_issue) VALUES (9, 12, true, false);

INSERT INTO record_attendances (record_id, user_role_id, signed_in, signed) VALUES
(9, 1, true, true), (9, 2, true, true), (9, 3, true, true),
(9, 4, true, true), (9, 5, false, false), (9, 6, true, true), (9, 7, true, true);

INSERT INTO record_topics (id, record_id, title, type, sort_order) VALUES
(11, 9, '物业费调整方案', 'decision', 1);

INSERT INTO topic_votes (topic_id, user_role_id, choice) VALUES
(11, 1, 'for_vote'), (11, 2, 'for_vote'), (11, 3, 'for_vote'),
(11, 4, 'for_vote'), (11, 6, 'for_vote'), (11, 7, 'against');

-- 结束-3：瑕疵+待公示
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(13, 1, '2025年第2次业委会例会', '2025-04-05', '14:00', '社区活动室', 'ended', 'flawed', '讨论小区停车管理制度修订', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(13, 1, true, true), (13, 2, true, true), (13, 3, true, true),
(13, 4, true, true), (13, 5, true, true), (13, 6, true, true), (13, 7, true, true);

-- 未公示
INSERT INTO meeting_records (id, meeting_id, has_decision, has_major_issue, juwei_name, juwei_signed) VALUES
(10, 13, true, true, '王红梅（社区居委会）', false);

INSERT INTO record_attendances (record_id, user_role_id, signed_in, signed) VALUES
(10, 1, true, true), (10, 2, true, true), (10, 3, true, true),
(10, 4, true, false), (10, 5, true, true), (10, 6, true, true), (10, 7, true, true);
-- 签到 7人，签字 6人（过半），但重大事项缺居委会签字

INSERT INTO record_topics (id, record_id, title, type, sort_order) VALUES
(12, 10, '停车管理制度修订', 'major', 1);

INSERT INTO topic_votes (topic_id, user_role_id, choice) VALUES
(12, 1, 'for_vote'), (12, 2, 'for_vote'), (12, 3, 'for_vote'),
(12, 5, 'for_vote'), (12, 6, 'for_vote'), (12, 7, 'for_vote');

-- 结束-4：无效（签到未过半）
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(14, 1, '2025年第1次业委会例会（无效）', '2025-02-10', '10:00', '物业办公室', 'ended', 'invalid', '审议年度预算方案', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(14, 1, true, true), (14, 2, true, true), (14, 3, true, true),
(14, 4, true, true), (14, 5, true, true), (14, 6, true, true), (14, 7, true, true);

INSERT INTO meeting_records (id, meeting_id, has_decision, has_major_issue) VALUES (11, 14, true, false);

INSERT INTO record_attendances (record_id, user_role_id, signed_in, signed) VALUES
(11, 1, true, true), (11, 2, true, true), (11, 3, false, false),
(11, 4, false, false), (11, 5, false, false), (11, 6, false, false), (11, 7, false, false);
-- 只有 2 人签到（需≥4）→ 无效

INSERT INTO record_topics (id, record_id, title, type, sort_order) VALUES
(13, 11, '年度预算方案', 'decision', 1);

INSERT INTO topic_votes (topic_id, user_role_id, choice) VALUES
(13, 1, 'for_vote'), (13, 2, 'for_vote');

-- ===== 业主大会追加 =====
INSERT INTO owner_meetings (id, community_id, title, meeting_date, meeting_time, location, type, stage, compliance, description, total_owners, total_area, needs_vote) VALUES
(5, 1, '2025年度临时业主大会（停车费调整）', '2025-07-15', '14:00', '小区中心广场', 'special', 'ended', 'valid', '审议小区停车费调整方案', 120, 18500, true);

INSERT INTO owner_meeting_notifies (meeting_id, sent_count, announced, content_complete) VALUES
(5, 120, true, true);

INSERT INTO owner_meeting_ballots (meeting_id, delivered_count, records_complete, non_face_announce) VALUES
(5, 120, true, false);

INSERT INTO owner_meeting_publishes (meeting_id, published, publish_date) VALUES (5, true, '2025-07-17');

INSERT INTO owner_meeting_records (id, meeting_id, present_owners, present_area, supervisor_name, supervisor_signed, designated, monitor, callout, tally) VALUES
(3, 5, 88, 13200, '王主任（街道办）', true, true, true, true, true);

INSERT INTO owner_meeting_topics (id, record_id, title, type, for_owners, ag_owners, ab_owners, for_area, ag_area, ab_area) VALUES
(4, 3, '停车费由80元调整为120元', 'ordinary', 52, 28, 8, 8500, 3800, 900);

INSERT INTO owner_meeting_evidences (record_id, file_name, file_type) VALUES
(3, '签到册_20250715.jpg', '签到册'),
(3, '投票汇总_停车费.jpg', '投票单'),
(3, '会议记录_20250715.pdf', '会议记录');

-- ===== 接待追加 =====
INSERT INTO reception_records (community_id, date, time, visitor_name, room, receiver, category, content, resolution, fed_property, fed_owner) VALUES
(1, '2026-06-02', '10:00', '赵明', '6号楼1502', '刘海涛（委员）', 'property', '小区门禁系统频繁故障，建议全面检修或更换。', '', false, false),
(1, '2026-03-15', '14:00', '钱小红', '1号楼703', '陈晓梅（委员）', 'public_affairs', '建议在小区增设快递柜，方便业主取件。', '已联系丰巢快递柜公司，计划在小区北门安装两组。', false, true),
(1, '2026-02-20', '09:30', '孙大力', '9号楼1101', '杨国华（委员）', 'neighbor', '楼上住户长期深夜噪音扰民，多次沟通无果。', '已约谈双方，楼上承诺22点后减少噪音活动。', false, true);

-- ===== 学习追加 =====
INSERT INTO learning_records (community_id, title, date, time, location, trainer, type, stage, progress, attendees) VALUES
(1, '新版民法典物业管理条款解读', '2026-06-25', '14:00', '区司法局会议室', '区司法局·刘律师', 'special', 'preparing', 0, '全体委员'),
(1, '小区消防设施日常维护培训', '2026-06-08', '10:00', '物业培训室', '消防大队·李教官', 'internal', 'ongoing', 30, '全体委员'),
(1, '街道业委会年度交流会', '2026-04-28', '09:00', '街道办事处', '街道物管科·周明', 'street', 'ended', 100, '主任、副主任');
