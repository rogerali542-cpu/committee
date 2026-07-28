-- 学习记录显式分类：内部学习(internal) / 外部培训(external)
-- 不再由 type 推断；首页不展示，详情可查看/修改，年度学习按 category 计数。
ALTER TABLE learning_records ADD COLUMN category VARCHAR(15) NOT NULL DEFAULT 'internal';

-- 存量回填：街镇/专项培训归为外部培训，其余（内部学习）保持 internal 默认值
UPDATE learning_records SET category = 'external' WHERE type IN ('street', 'special');
