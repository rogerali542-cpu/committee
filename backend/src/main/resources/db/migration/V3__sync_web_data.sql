-- V3: 同步网页版完整会议数据
-- 先清空 V2 种子数据，再导入网页原版完整数据

DELETE FROM topic_votes;
DELETE FROM record_topics;
DELETE FROM record_evidences;
DELETE FROM record_attendances;
DELETE FROM meeting_records;
DELETE FROM meeting_deliveries;
DELETE FROM meeting_publishes;
DELETE FROM committee_meetings;

DELETE FROM owner_meeting_evidences;
DELETE FROM owner_meeting_topics;
DELETE FROM owner_meeting_records;
DELETE FROM owner_meeting_ballots;
DELETE FROM owner_meeting_notifies;
DELETE FROM owner_meeting_publishes;
DELETE FROM owner_meetings;

DELETE FROM reception_records;
DELETE FROM learning_records;

-- ===== 业主委员会会议 =====

-- ID 1: 准备阶段（2026年第4次）
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(1, 1, '2026年第4次业委会例会', '2026-06-15', '14:00', '社区活动室', 'preparing', NULL, '讨论小区绿化维护方案及停车位管理优化', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(1, 1, true, true),     -- 张建国
(1, 2, true, true),     -- 李秀英
(1, 3, true, false),    -- 王志强
(1, 4, true, false),    -- 赵丽娟
(1, 5, false, false),   -- 刘海涛
(1, 6, false, false),   -- 陈晓梅
(1, 7, false, false);   -- 杨国华

-- ID 2: 进行中（2026年第3次）
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(2, 1, '2026年第3次业委会例会', '2026-05-10', '10:00', '物业办公室', 'ongoing', NULL, '审议小区公共设施维修专项资金使用方案', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(2, 1, true, true), (2, 2, true, true), (2, 3, true, true),
(2, 4, true, true), (2, 5, true, true), (2, 6, true, true), (2, 7, true, true);

INSERT INTO meeting_records (id, meeting_id, has_decision, has_major_issue, juwei_name, juwei_signed) VALUES
(1, 2, true, true, '王红梅（社区居委会）', false);

INSERT INTO record_attendances (record_id, user_role_id, signed_in, signed) VALUES
(1, 1, true, true),    -- 张建国
(1, 2, true, true),    -- 李秀英
(1, 3, true, true),    -- 王志强
(1, 4, true, false),   -- 赵丽娟
(1, 5, true, false),   -- 刘海涛
(1, 6, false, false),  -- 陈晓梅
(1, 7, false, false);  -- 杨国华

INSERT INTO record_topics (id, record_id, title, type, sort_order) VALUES
(1, 1, '公共设施维修专项资金使用方案', 'decision', 1),
(2, 1, '绿化养护承包单位续聘', 'decision', 2),
(3, 1, '提请业主大会审议电梯更新事项', 'major', 3);

INSERT INTO topic_votes (topic_id, user_role_id, choice) VALUES
-- 议题1: 张建国=赞成, 李秀英=赞成, 王志强=反对
(1, 1, 'for_vote'), (1, 2, 'for_vote'), (1, 3, 'against'),
-- 议题2: 张建国=赞成, 李秀英=赞成
(2, 1, 'for_vote'), (2, 2, 'for_vote'),
-- 议题3: 张建国=赞成
(3, 1, 'for_vote');

INSERT INTO record_evidences (record_id, file_name, file_type) VALUES
(1, '签到表照片_现场.jpg', '签到表');

-- ID 3: 已结束·已公示（2026年第2次）
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(3, 1, '2026年第2次业委会例会', '2026-03-15', '15:30', '社区活动室', 'ended', 'valid', '审议2025年度财务报告及预算安排', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(3, 1, true, true), (3, 2, true, true), (3, 3, true, true),
(3, 4, true, true), (3, 5, true, true), (3, 6, true, true), (3, 7, true, true);

INSERT INTO meeting_publishes (meeting_id, published, publish_date) VALUES (3, true, '2026-03-17');

INSERT INTO meeting_records (id, meeting_id, has_decision, has_major_issue) VALUES (2, 3, true, false);

INSERT INTO record_attendances (record_id, user_role_id, signed_in, signed) VALUES
(2, 1, true, true), (2, 2, true, true), (2, 3, true, true),
(2, 4, true, true), (2, 5, true, true), (2, 6, true, true), (2, 7, true, true);

INSERT INTO record_topics (id, record_id, title, type, sort_order) VALUES
(4, 2, '2025年度财务报告审议', 'decision', 1);

INSERT INTO topic_votes (topic_id, user_role_id, choice) VALUES
(4, 1, 'for_vote'), (4, 2, 'for_vote'), (4, 3, 'for_vote'),
(4, 4, 'for_vote'), (4, 5, 'for_vote'), (4, 6, 'for_vote'), (4, 7, 'for_vote');

-- ID 4: 已结束·已公示（2026年第1次）
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(4, 1, '2026年第1次业委会例会', '2026-01-18', '10:00', '社区活动室', 'ended', 'valid', '确认2026年度工作计划和重点议题', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(4, 1, true, true), (4, 2, true, true), (4, 3, true, true),
(4, 4, true, true), (4, 5, true, true), (4, 6, true, true), (4, 7, true, true);

INSERT INTO meeting_publishes (meeting_id, published, publish_date) VALUES (4, true, '2026-01-20');

INSERT INTO meeting_records (id, meeting_id, has_decision, has_major_issue) VALUES (3, 4, false, false);

INSERT INTO record_attendances (record_id, user_role_id, signed_in, signed) VALUES
(3, 1, true, true), (3, 2, true, true), (3, 3, true, true),
(3, 4, true, true), (3, 5, true, true), (3, 6, true, true), (3, 7, true, true);

-- ID 5: 已结束·瑕疵·未公示（2025年第6次）
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(5, 1, '2025年第6次业委会例会', '2025-12-05', '14:00', '社区活动室', 'ended', 'flawed', '年度工作总结及2026年规划研讨', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(5, 1, true, true), (5, 2, true, true), (5, 3, true, true),
(5, 4, true, true), (5, 5, true, true), (5, 6, true, true), (5, 7, true, true);

INSERT INTO meeting_records (id, meeting_id, has_decision, has_major_issue) VALUES (4, 5, true, false);

INSERT INTO record_attendances (record_id, user_role_id, signed_in, signed) VALUES
(4, 1, true, true), (4, 2, true, true), (4, 3, true, true),
(4, 4, true, true), (4, 5, false, false), (4, 6, false, false), (4, 7, false, false);

INSERT INTO record_topics (id, record_id, title, type, sort_order) VALUES
(5, 4, '2026年度经费预算', 'decision', 1);

INSERT INTO topic_votes (topic_id, user_role_id, choice) VALUES
(5, 1, 'for_vote'), (5, 2, 'for_vote'), (5, 3, 'for_vote'), (5, 4, 'for_vote');

-- ID 6: 已结束·已公示（2025年第5次）
INSERT INTO committee_meetings (id, community_id, title, meeting_date, meeting_time, location, stage, compliance, description, created_by) VALUES
(6, 1, '2025年第5次业委会例会', '2025-10-12', '10:00', '物业办公室', 'ended', 'valid', '审议物业公司服务合同续签方案', 1);

INSERT INTO meeting_deliveries (meeting_id, user_role_id, notice_delivered, material_delivered) VALUES
(6, 1, true, true), (6, 2, true, true), (6, 3, true, true),
(6, 4, true, true), (6, 5, true, true), (6, 6, true, true), (6, 7, true, true);

INSERT INTO meeting_publishes (meeting_id, published, publish_date) VALUES (6, true, '2025-10-13');

INSERT INTO meeting_records (id, meeting_id, has_decision, has_major_issue) VALUES (5, 6, true, false);

INSERT INTO record_attendances (record_id, user_role_id, signed_in, signed) VALUES
(5, 1, true, true), (5, 2, true, true), (5, 3, true, true),
(5, 4, true, true), (5, 5, true, true), (5, 6, true, true), (5, 7, true, true);

INSERT INTO record_topics (id, record_id, title, type, sort_order) VALUES
(6, 5, '物业公司服务合同续签', 'decision', 1);

INSERT INTO topic_votes (topic_id, user_role_id, choice) VALUES
(6, 1, 'for_vote'), (6, 2, 'for_vote'), (6, 3, 'for_vote'),
(6, 4, 'for_vote'), (6, 5, 'for_vote'), (6, 6, 'for_vote'), (6, 7, 'for_vote');

-- ===== 业主大会 =====

-- ID 1: 准备阶段
INSERT INTO owner_meetings (id, community_id, title, meeting_date, meeting_time, location, type, stage, compliance, description, total_owners, total_area, needs_vote) VALUES
(1, 1, '2026年度第一次业主大会', '2026-07-20', '14:00', '小区中心广场', 'regular', 'preparing', NULL, '审议2026年度物业服务费调整方案；审议公共维修资金专项使用计划', 120, 18500, true);

INSERT INTO owner_meeting_notifies (meeting_id, sent_count, announced, content_complete) VALUES
(1, 86, true, true);

INSERT INTO owner_meeting_ballots (meeting_id, delivered_count, records_complete, non_face_announce) VALUES
(1, 64, true, false);

-- ID 2: 已结束·有效·已公示
INSERT INTO owner_meetings (id, community_id, title, meeting_date, meeting_time, location, type, stage, compliance, description, total_owners, total_area, needs_vote) VALUES
(2, 1, '2025年临时业主大会（物业更换）', '2025-09-20', '10:00', '小区会议室', 'special', 'ended', 'valid', '审议解除与原物业公司委托合同，选聘新物业公司', 120, 18500, true);

INSERT INTO owner_meeting_notifies (meeting_id, sent_count, announced, content_complete) VALUES
(2, 120, true, true);

INSERT INTO owner_meeting_ballots (meeting_id, delivered_count, records_complete, non_face_announce) VALUES
(2, 120, true, true);

INSERT INTO owner_meeting_publishes (meeting_id, published, publish_date) VALUES (2, true, '2025-09-22');

INSERT INTO owner_meeting_records (id, meeting_id, present_owners, present_area, supervisor_name, supervisor_signed, designated, monitor, callout, tally) VALUES
(1, 2, 85, 12800, '陈副主任（街道办）', true, true, true, true, true);

INSERT INTO owner_meeting_topics (id, record_id, title, type, for_owners, ag_owners, ab_owners, for_area, ag_area, ab_area) VALUES
(1, 1, '解除原物业委托合同', 'major', 72, 8, 5, 10900, 1300, 600),
(2, 1, '选聘新物业公司', 'major', 68, 10, 7, 10200, 1500, 1100);

INSERT INTO owner_meeting_evidences (record_id, file_name, file_type) VALUES
(1, '签到册照片.jpg', '签到册'),
(1, '投票单汇总.jpg', '投票单');

-- ID 3: 已结束·无效（未达双过半）
INSERT INTO owner_meetings (id, community_id, title, meeting_date, meeting_time, location, type, stage, compliance, description, total_owners, total_area, needs_vote) VALUES
(3, 1, '2025年度第一次业主大会', '2025-05-10', '09:00', '小区活动广场', 'regular', 'ended', 'invalid', '审议2024年度财务报告；审议2025年度物业预算方案', 120, 18500, true);

INSERT INTO owner_meeting_notifies (meeting_id, sent_count, announced, content_complete) VALUES
(3, 120, true, true);

INSERT INTO owner_meeting_ballots (meeting_id, delivered_count, records_complete, non_face_announce) VALUES
(3, 118, true, false);

INSERT INTO owner_meeting_records (id, meeting_id, present_owners, present_area, supervisor_name, supervisor_signed, designated, monitor, callout, tally) VALUES
(2, 3, 55, 9200, '王主任（街道办）', false, true, false, false, false);

INSERT INTO owner_meeting_topics (id, record_id, title, type, for_owners, ag_owners, ab_owners, for_area, ag_area, ab_area) VALUES
(3, 2, '2024年度财务报告审议', 'ordinary', 40, 10, 5, 6800, 1500, 900);

-- ID 4: 准备阶段·无表决
INSERT INTO owner_meetings (id, community_id, title, meeting_date, meeting_time, location, type, stage, compliance, description, total_owners, total_area, needs_vote) VALUES
(4, 1, '2026年小区年度工作通报会', '2026-08-15', '19:00', '小区文化活动中心', 'regular', 'preparing', NULL, '通报2026年上半年小区管理、财务收支及维修资金使用情况，听取业主意见建议（无表决事项）', 120, 18500, false);

INSERT INTO owner_meeting_notifies (meeting_id, sent_count, announced, content_complete) VALUES
(4, 120, true, true);

-- ===== 接待记录 =====
INSERT INTO reception_records (community_id, date, time, visitor_name, room, receiver, category, content, resolution, fed_property, fed_owner) VALUES
(1, '2026-05-10', '14:30', '周敏', '5号楼302', '李秀英（副主任）', 'property', '反映地下车库照明长期损坏，多次报修未果。', '已督促物业更换车库照明灯具，承诺一周内完成。', true, true),
(1, '2026-05-10', '15:10', '吴建国', '3号楼1102', '王志强（委员）', 'public_affairs', '建议在中心花园增设儿童活动区健身器材。', '已纳入下次业委会例会议题讨论，将征求更多业主意见。', false, true),
(1, '2026-05-28', '14:00', '孙丽', '8号楼601', '张建国（主任）', 'property', '电梯近期频繁故障，要求物业加强日常维保。', '', false, false),
(1, '2026-04-10', '15:00', '郑强', '2号楼205', '赵丽娟（委员）', 'neighbor', '与楼上住户因漏水产生纠纷，请求协调。', '已组织双方现场协商，达成维修与赔偿方案。', false, true);

-- ===== 业务学习 =====
INSERT INTO learning_records (community_id, title, date, time, location, trainer, type, stage, progress, attendees) VALUES
(1, '物业管理条例解读培训', '2026-06-10', '14:00', '社区会议室', '张建国', 'internal', 'preparing', 0, '全体委员'),
(1, '消防安全知识学习', '2026-06-05', '09:30', '物业培训室', '李秀英', 'internal', 'ongoing', 65, '全体委员'),
(1, '业委会议事规则学习', '2026-05-20', '15:00', '线上腾讯会议', '王志强', 'internal', 'ongoing', 40, '全体委员'),
(1, '小区公共设施管理规范', '2026-05-08', '10:00', '社区活动室', '赵丽娟', 'internal', 'ended', 100, '全体委员'),
(1, '业主权益保护法律知识', '2026-04-15', '14:30', '社区会议室', '刘海涛', 'internal', 'ended', 100, '全体委员'),
(1, '业委会工作规范培训', '2026-06-12', '09:00', '街道办事处三楼', '街道物管科·周明', 'street', 'preparing', 0, '全体委员'),
(1, '物业管理政策宣讲', '2026-05-15', '14:00', '街镇社区服务中心', '街道物管科·张华', 'street', 'ongoing', 55, '全体委员'),
(1, '业委会换届选举流程培训', '2026-03-20', '09:30', '街道办事处', '街道民政科·李强', 'street', 'ended', 100, '全体委员'),
(1, '业委会财务管理专项培训', '2026-06-18', '13:30', '市住建局培训中心', '市住建局·吴芳', 'special', 'preparing', 0, '主任、副主任、保管印章委员'),
(1, '物业维修资金监管专项培训', '2026-05-25', '10:00', '区住建局', '区住建局·陈主任', 'special', 'ongoing', 50, '主任、副主任、保管印章委员'),
(1, '业委会法律风险防范培训', '2026-04-20', '14:00', '区司法局', '区司法局·王律师', 'special', 'ended', 100, '主任、副主任、保管印章委员'),
(1, '公共收益管理专项培训', '2026-03-10', '09:00', '街道办事处', '街道财政所·赵科长', 'special', 'ended', 100, '主任、副主任、保管印章委员');
