-- V2: 种子会议数据（测试用）

-- 业主委员会会议
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(1, 1, '2026年第3次业委会例会', '2026-06-15', '14:00', '社区活动室', 'preparing', NULL, '审议2026年度上半年工作总结及下半年计划安排', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(1, 1, true, true),
(1, 2, true, false),
(1, 3, false, false),
(1, 4, false, false),
(1, 5, false, false),
(1, 6, false, false),
(1, 7, false, false);

INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(2, 1, '2026年第2次业委会例会', '2026-05-20', '10:00', '物业办公室', 'ongoing', NULL, '审议物业公司服务合同续签方案及小区绿化改造项目', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(2, 1, true, true),
(2, 2, true, true),
(2, 3, true, true),
(2, 4, true, true),
(2, 5, true, true),
(2, 6, true, true),
(2, 7, true, true);

INSERT INTO meeting_records (meeting_id, has_decision, has_major_issue, juwei_name, juwei_signed) VALUES
(2, true, true, '王红梅（社区居委会）', false);

INSERT INTO record_attendances (record_id, user_role_id, signed_in, signed) VALUES
(1, 1, true, true),
(1, 2, true, true),
(1, 3, true, false),
(1, 4, false, false),
(1, 5, true, true),
(1, 6, false, false),
(1, 7, true, true);

INSERT INTO record_topics (id, record_id, title, type, sort_order) VALUES
(1, 1, '物业公司服务合同续签', 'decision', 1),
(2, 1, '小区绿化改造项目资金使用', 'major', 2);

INSERT INTO topic_votes (topic_id, user_role_id, choice) VALUES
(1, 1, 'for_vote'),
(1, 2, 'for_vote'),
(1, 3, 'against'),
(1, 5, 'for_vote'),
(1, 7, 'for_vote'),
(2, 1, 'for_vote'),
(2, 2, 'for_vote'),
(2, 5, 'abstain'),
(2, 7, 'for_vote');

INSERT INTO record_evidences (record_id, file_name, file_type) VALUES
(1, '签到表照片_现场.jpg', '签到表');

INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(3, 1, '2026年第1次业委会例会', '2026-04-10', '09:30', '社区会议室', 'ended', 'valid', '确认2026年度工作计划和重点议题', 1);

INSERT INTO meeting_publishes (meeting_id, published, publish_date) VALUES
(3, true, '2026-04-11');

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(3, 1, true, true),
(3, 2, true, true),
(3, 3, true, true),
(3, 4, true, true),
(3, 5, true, true),
(3, 6, true, true),
(3, 7, true, true);

INSERT INTO meeting_records (meeting_id, has_decision, has_major_issue) VALUES
(3, true, false);

INSERT INTO record_attendances (record_id, user_role_id, signed_in, signed) VALUES
(2, 1, true, true),
(2, 2, true, true),
(2, 3, true, true),
(2, 4, true, true),
(2, 5, false, false),
(2, 6, true, true),
(2, 7, true, true);

INSERT INTO record_topics (id, record_id, title, type, sort_order) VALUES
(3, 2, '2026年度工作计划', 'decision', 1);

INSERT INTO topic_votes (topic_id, user_role_id, choice) VALUES
(3, 1, 'for_vote'),
(3, 2, 'for_vote'),
(3, 3, 'for_vote'),
(3, 4, 'for_vote'),
(3, 6, 'abstain'),
(3, 7, 'for_vote');

-- 无效会议
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(4, 1, '2025年第6次业委会例会', '2025-12-05', '14:00', '社区活动室', 'ended', 'invalid', '年度工作总结及2026年规划研讨', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(4, 1, true, true),
(4, 2, true, true),
(4, 3, true, true),
(4, 4, true, true),
(4, 5, true, true),
(4, 6, true, true),
(4, 7, true, true);

INSERT INTO meeting_records (meeting_id, has_decision, has_major_issue) VALUES
(4, true, false);

INSERT INTO record_attendances (record_id, user_role_id, signed_in, signed) VALUES
(3, 1, true, true),
(3, 2, false, false),
(3, 3, true, true),
(3, 4, false, false),
(3, 5, false, false),
(3, 6, false, false),
(3, 7, true, true);

-- 业主大会种子数据
INSERT INTO owner_meetings (id, community_id, title, meeting_date, meeting_time, location, type, stage, compliance, description, total_owners, total_area, needs_vote) VALUES
(1, 1, '2026年度第一次业主大会', '2026-07-20', '14:00', '小区中心广场', 'regular', 'preparing', NULL, '审议2026年度物业服务费调整方案', 120, 18500, true);

INSERT INTO owner_meeting_notifies (meeting_id, sent_count, announced, content_complete) VALUES
(1, 86, true, true);

INSERT INTO owner_meeting_ballots (meeting_id, delivered_count, records_complete, non_face_announce) VALUES
(1, 64, true, false);

INSERT INTO owner_meetings (id, community_id, title, meeting_date, meeting_time, location, type, stage, compliance, description, total_owners, total_area, needs_vote) VALUES
(2, 1, '2026年小区年度工作通报会', '2026-08-15', '19:00', '小区文化活动中心', 'regular', 'preparing', NULL, '通报2026年上半年小区管理、财务收支情况', 120, 18500, false);

INSERT INTO owner_meeting_notifies (meeting_id, sent_count, announced, content_complete) VALUES
(2, 120, true, true);

-- 业务学习种子数据
INSERT INTO learning_records (community_id, title, date, time, location, trainer, type, stage, progress, attendees) VALUES
(1, '物业管理条例解读培训', '2026-06-10', '14:00', '社区会议室', '张建国', 'internal', 'preparing', 0, '全体委员'),
(1, '消防安全知识学习', '2026-06-05', '09:30', '物业培训室', '李秀英', 'internal', 'ongoing', 65, '全体委员'),
(1, '业委会议事规则学习', '2026-05-20', '15:00', '线上腾讯会议', '王志强', 'internal', 'ongoing', 40, '全体委员'),
(1, '小区公共设施管理规范', '2026-05-08', '10:00', '社区活动室', '赵丽娟', 'internal', 'ended', 100, '全体委员'),
(1, '业委会工作规范培训', '2026-06-12', '09:00', '街道办事处三楼', '街道物管科·周明', 'street', 'preparing', 0, '全体委员'),
(1, '业委会财务管理专项培训', '2026-06-18', '13:30', '市住建局培训中心', '市住建局·吴芳', 'special', 'preparing', 0, '主任、副主任');

-- 接待记录种子数据
INSERT INTO reception_records (community_id, date, time, visitor_name, room, receiver, category, content, resolution, fed_property, fed_owner) VALUES
(1, '2026-05-10', '14:30', '周敏', '5号楼302', '李秀英（副主任）', 'property', '反映地下车库照明长期损坏，多次报修未果。', '已督促物业更换车库照明灯具，承诺一周内完成。', true, true),
(1, '2026-05-10', '15:10', '吴建国', '3号楼1102', '王志强（委员）', 'public_affairs', '建议在中心花园增设儿童活动区健身器材。', '已纳入下次业委会例会议题讨论。', false, true),
(1, '2026-05-28', '14:00', '孙丽', '8号楼601', '张建国（主任）', 'property', '电梯近期频繁故障，要求物业加强日常维保。', '', false, false);
