-- 将「2026年第3次业委会例会」(committee_meetings.id=2 -> meeting_records.id=1) 的预设议题
-- 调整为与快速会议测试音频一致的 4 条：2 个非表决(通报/讨论) + 2 个表决。
-- 原种子(公共设施维修/绿化养护续聘/电梯更新)与测试音频对不上，这里替换。

-- 1. 先清掉引用旧议题(id=1,2,3)的表决记录，避免外键约束报错
DELETE FROM topic_votes WHERE topic_id IN (1, 2, 3);

-- 2. 删除该会议记录下的旧预设议题
DELETE FROM record_topics WHERE record_id = 1;

-- 3. 插入与音频对齐的 4 条议题（类型：notice/discussion 不表决，decision 需表决）
--    real_name_vote / source 为 NOT NULL 无默认值，必须显式给值。
INSERT INTO record_topics (record_id, title, type, decision_type, sort_order, real_name_vote, source) VALUES
(1, '上月物业服务情况通报',         'notice',     'none',   1, 0, 'preset'),
(1, '小区停车管理优化方案讨论',     'discussion', 'none',   2, 0, 'preset'),
(1, '公共设施维修专项资金使用方案', 'decision',   'simple', 3, 0, 'preset'),
(1, '小区门禁系统改造方案',         'decision',   'simple', 4, 0, 'preset');

-- 4. 该会议已无重大事项(major)，关闭重大事项标记（影响居委会签字等展示）
UPDATE meeting_records SET has_major_issue = FALSE WHERE id = 1;
