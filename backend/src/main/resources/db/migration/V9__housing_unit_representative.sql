-- V9: housing_units 加 representative_user_id（户代表制投票）
-- ============================================================

ALTER TABLE housing_units
    ADD COLUMN representative_user_id BIGINT COMMENT '投票代表人 user_role_id';

-- 已有数据：默认产权人即为代表人
UPDATE housing_units SET representative_user_id = owner_user_id
WHERE representative_user_id IS NULL;

-- 外键
ALTER TABLE housing_units
    ADD CONSTRAINT fk_housing_rep FOREIGN KEY (representative_user_id) REFERENCES user_roles(id);

-- 示例：张建国(user_role_id=1) 的实名用户 (user_id=1) 对应 user_roles 记录 id=1（主任）
-- 同一人还有一个 user_role id=11（委员），但代表人是主任身份来投
