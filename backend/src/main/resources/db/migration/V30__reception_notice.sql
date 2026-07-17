-- 接待日公告：编辑 + 导出打印（0717）。
-- 当前环境 Flyway 默认关闭，实际建表/建列由 Hibernate ddl-auto:update 完成；本文件记录 schema 演进。
-- 本次全是 ADD / CREATE，ddl-auto:update 会自动做，不需要像 V28 那样手工执行（它只加不删）。

-- 1) 接待安排加「上次修改时间」。
--    背景：按规定每月要设接待时间，主任时间不定所以基本每月都改 —— 那么「这个月改了没」
--    就是委员每月的第一个问题，没这个字段答不上来。
--    必须 NULL 可空：老行没有这个值，且 NOT NULL 无默认值会让 ddl-auto 加列时炸掉已有行。
ALTER TABLE reception_systems ADD COLUMN updated_at DATETIME NULL;

-- 2) 导出留痕表。
--    用户定的存档口径：接待安排只存当前一份（reception_systems，编辑即覆盖），
--    每导出一次记一笔，用来回答「你证明一下每个月都公示了接待时间」。
--    不做每月一份独立存档：委员每月得「新建本月通知」太重，留痕已经够证明。
--    ⚠ time_desc/place/person 是导出那一刻的快照，故意不做外键：
--      reception_systems 下个月就被改了，这里若只存引用，回头看 7 月那笔会显示 8 月的时间，
--      留痕就成了假证据。
CREATE TABLE reception_notice_exports (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  community_id  BIGINT       NOT NULL,
  exported_by   VARCHAR(30)  NULL,          -- 存姓名不存 user_id：委员换届后 id 关联不上就成空白
  exported_at   DATETIME     NOT NULL,
  time_desc     VARCHAR(100) NULL,          -- ↓ 三个快照字段
  place         VARCHAR(200) NULL,
  person        VARCHAR(100) NULL,
  PRIMARY KEY (id),
  KEY idx_rne_community_exported (community_id, exported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
