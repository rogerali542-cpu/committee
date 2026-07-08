-- 为测试会议 #250「2026年第3次业委会例会」的三个议题造模拟委员意见，
-- 用于验证「议题审议以委员意见为主」的纪要生成。source: text=打字 / voice=语音转写。
-- 议题：474 电梯年度维保方案 / 475 地下车库照明改造预算 / 476 第三季度物业费调整
-- 委员：2 李秀英(副主任) 3 王志强 4 赵丽娟 5 刘海涛 6 陈晓梅 7 杨国华
-- 幂等：先删本脚本造的模拟意见（保留原有 source=ai/主任已存在的两条），再插入。
SET NAMES utf8mb4;

DELETE o FROM topic_opinions o
JOIN record_topics t ON o.topic_id = t.id
JOIN meeting_records r ON t.record_id = r.id
WHERE r.meeting_id = 250 AND o.speaker_name IN ('李秀英','王志强','赵丽娟','刘海涛','陈晓梅','杨国华')
  AND o.source IN ('text','voice');

INSERT INTO topic_opinions (topic_id, user_role_id, speaker_name, content, source, created_at) VALUES
-- 议题 474：电梯年度维保方案
(474, 3, '王志强', '我同意开展年度维保，但建议必须选有电梯维保资质的公司，并要求对方每月提供维保记录备查。', 'text', NOW()),
(474, 4, '赵丽娟', '原则同意方案。3号楼电梯使用年限最长、故障也多，建议把它列为本年度重点检修对象。', 'text', NOW()),
(474, 5, '刘海涛', '对维保报价还有疑问，建议至少再比较两家公司的报价和服务内容，择优确定后再签约。', 'voice', NOW()),
(474, 2, '李秀英', '同意维保方案。维保过程要留存台账，方便日后业主查询和责任追溯。', 'text', NOW()),
-- 议题 475：地下车库照明改造预算
(475, 6, '陈晓梅', '支持照明改造，建议统一更换为 LED 节能灯，虽然一次性投入高，但长期能省不少电费。', 'text', NOW()),
(475, 7, '杨国华', '我觉得预算偏高，建议分两期实施，先改造人流量最大的出入口和主通道区域。', 'text', NOW()),
(475, 3, '王志强', '同意改造，但要求施工尽量安排在白天进行，避开夜间以免影响业主休息。', 'voice', NOW()),
-- 议题 476：第三季度物业费调整
(476, 4, '赵丽娟', '不同意上调物业费。当前物业服务质量还没到位，应先督促整改，服务改善后再谈调价。', 'text', NOW()),
(476, 5, '刘海涛', '建议暂缓表决，物业费调整涉及全体业主利益，应先书面征求广大业主意见。', 'text', NOW()),
(476, 6, '陈晓梅', '如确需调整，幅度不宜超过 10%，并应向全体业主公示成本明细，做到公开透明。', 'voice', NOW()),
(476, 2, '李秀英', '同意暂缓。建议让物业先拿出一份切实可行的服务提升方案，再评估是否调价。', 'text', NOW());

SELECT o.topic_id, o.speaker_name, o.source, LEFT(o.content,24) AS content
FROM topic_opinions o JOIN record_topics t ON o.topic_id=t.id JOIN meeting_records r ON t.record_id=r.id
WHERE r.meeting_id=250 ORDER BY o.topic_id, o.id;
