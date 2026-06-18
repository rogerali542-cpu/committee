-- V10: housing_units 支持一户多产权人
-- ============================================================

-- 1. 产权人关联表（多对多）
CREATE TABLE housing_unit_owners (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    user_role_id BIGINT NOT NULL,
    UNIQUE KEY uk_unit_owner (unit_id, user_role_id),
    FOREIGN KEY (unit_id) REFERENCES housing_units(id),
    FOREIGN KEY (user_role_id) REFERENCES user_roles(id)
);

-- 2. 迁移现有 owner_user_id 数据
INSERT INTO housing_unit_owners (unit_id, user_role_id)
SELECT id, owner_user_id FROM housing_units
WHERE owner_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM housing_unit_owners WHERE unit_id = housing_units.id AND user_role_id = housing_units.owner_user_id);

-- 3. 新增张丽华（张建国家属）
INSERT INTO users (openid, nick_name, phone) VALUES ('dev-openid-zhanglihua', '张丽华', '13800010011');

INSERT INTO user_roles (user_id, community_id, role, real_name, room_number)
VALUES (11, 1, '业主', '张丽华', '3栋101');

-- 4. 张丽华加入 3栋101 的产权人（非代表人）
INSERT INTO housing_unit_owners (unit_id, user_role_id)
SELECT id, 11 FROM housing_units
WHERE building = '3栋' AND unit_no = '101'
  AND NOT EXISTS (SELECT 1 FROM housing_unit_owners WHERE unit_id = housing_units.id AND user_role_id = 11);
