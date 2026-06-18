-- V6: 用户信息补全 + 记录员清理 + 多重身份
-- 前置: V5 已建好 permissions/housing_units 等表

-- ============================================================
-- 1. 给用户补全信息（手机、头像、微信号）
-- ============================================================
UPDATE users SET phone = '13800010001', avatar_url = '/avatar/zhang.png' WHERE openid = 'dev-openid-zhang';
UPDATE users SET phone = '13800010002', avatar_url = '/avatar/li.png' WHERE openid = 'dev-openid-li-xiuying';
UPDATE users SET phone = '13800010003', avatar_url = '/avatar/wang.png' WHERE openid = 'dev-openid-wang';
UPDATE users SET phone = '13800010004', avatar_url = '/avatar/zhao.png' WHERE openid = 'dev-openid-zhao';
UPDATE users SET phone = '13800010005', avatar_url = '/avatar/liu.png' WHERE openid = 'dev-openid-liu';
UPDATE users SET phone = '13800010006', avatar_url = '/avatar/chen.png' WHERE openid = 'dev-openid-chen';
UPDATE users SET phone = '13800010007', avatar_url = '/avatar/yang.png' WHERE openid = 'dev-openid-yang';
UPDATE users SET phone = '13800010008', avatar_url = '/avatar/xiaoli.png' WHERE openid = 'dev-openid-secretary';
UPDATE users SET phone = '13800010009', avatar_url = '/avatar/owner.png' WHERE openid = 'dev-openid-owner';
UPDATE users SET phone = '13800010010', avatar_url = '/avatar/property.png' WHERE openid = 'dev-openid-property';

-- ============================================================
-- 2. 删除记录员身份（和前端保持一致）
-- ============================================================
DELETE FROM user_roles WHERE role = '记录员';

-- 把秘书小李改为委员
INSERT INTO user_roles (user_id, community_id, role, real_name, room_number)
VALUES (8, 1, '委员', '秘书小李', '1栋101');

-- ============================================================
-- 3. 给委员补小区房号（关联到 housing_units）
-- ============================================================
UPDATE user_roles SET room_number = '3栋201' WHERE user_id = 1 AND role = '主任';
UPDATE user_roles SET room_number = '3栋202' WHERE user_id = 2 AND role = '副主任';
UPDATE user_roles SET room_number = '2栋101' WHERE user_id = 3 AND role = '委员';
UPDATE user_roles SET room_number = '2栋102' WHERE user_id = 4 AND role = '委员';
UPDATE user_roles SET room_number = '2栋201' WHERE user_id = 5 AND role = '委员';
UPDATE user_roles SET room_number = '2栋202' WHERE user_id = 6 AND role = '委员';
UPDATE user_roles SET room_number = '1栋201' WHERE user_id = 7 AND role = '委员';

-- ============================================================
-- 4. 主任/副主任同时加入委员身份（让他们也能签到表决）
-- ============================================================
INSERT INTO user_roles (user_id, community_id, role, real_name, room_number) VALUES
    (1, 1, '委员', '张建国', '3栋201'),
    (2, 1, '委员', '李秀英', '3栋202');

-- ============================================================
-- 5. 房屋花名册关联到真实用户
-- ============================================================
-- 更新已有数据，绑定业主
UPDATE housing_units SET owner_user_id = 9, owner_name = '测试业主', phone = '13800010009' WHERE building = '1栋' AND unit_no = '101';
UPDATE housing_units SET owner_user_id = 8, owner_name = '秘书小李', phone = '13800010008' WHERE building = '1栋' AND unit_no = '102';
UPDATE housing_units SET owner_user_id = 7, owner_name = '杨国华', phone = '13800010007' WHERE building = '1栋' AND unit_no = '201';
UPDATE housing_units SET owner_user_id = 6, owner_name = '陈晓梅', phone = '13800010006' WHERE building = '1栋' AND unit_no = '202';
UPDATE housing_units SET owner_user_id = 5, owner_name = '刘海涛', phone = '13800010005' WHERE building = '2栋' AND unit_no = '101';
UPDATE housing_units SET owner_user_id = 4, owner_name = '赵丽娟', phone = '13800010004' WHERE building = '2栋' AND unit_no = '102';
UPDATE housing_units SET owner_user_id = 3, owner_name = '王志强', phone = '13800010003' WHERE building = '2栋' AND unit_no = '201';
UPDATE housing_units SET owner_user_id = 2, owner_name = '李秀英', phone = '13800010002' WHERE building = '2栋' AND unit_no = '202';
UPDATE housing_units SET owner_user_id = 1, owner_name = '张建国', phone = '13800010001' WHERE building = '3栋' AND unit_no = '101';
UPDATE housing_units SET owner_user_id = 1, owner_name = '张建国', phone = '13800010001' WHERE building = '3栋' AND unit_no = '102';
