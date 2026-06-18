-- V5: 补齐小程序新功能所需的表和字段
-- 议题多选一 / 接待佐证 / 学习佐证+签到+通知 / 房屋花名册 / 权限码体系

-- ============================================================
-- 1. 议题多选一：record_topics 加 decision_type + options
-- ============================================================
ALTER TABLE record_topics
    ADD COLUMN decision_type VARCHAR(15) NOT NULL DEFAULT 'simple' AFTER type,
    ADD COLUMN options_json TEXT AFTER decision_type;

ALTER TABLE owner_meeting_topics
    ADD COLUMN decision_type VARCHAR(15) NOT NULL DEFAULT 'simple' AFTER type,
    ADD COLUMN options_json TEXT AFTER decision_type;

-- ============================================================
-- 2. 接待佐证
-- ============================================================
CREATE TABLE reception_evidences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    file_name VARCHAR(200) NOT NULL DEFAULT '佐证.jpg',
    file_type VARCHAR(20) NOT NULL DEFAULT '照片',
    file_url VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (record_id) REFERENCES reception_records(id) ON DELETE CASCADE
);

-- ============================================================
-- 3. 学习记录补充字段 + 佐证 + 签到 + 通知
-- ============================================================
ALTER TABLE learning_records
    ADD COLUMN description TEXT AFTER trainer,
    ADD COLUMN notified BOOLEAN NOT NULL DEFAULT FALSE AFTER description;

-- 学习佐证表
CREATE TABLE learning_evidences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    file_name VARCHAR(200) NOT NULL DEFAULT '佐证.jpg',
    file_type VARCHAR(20) NOT NULL DEFAULT '照片',
    file_url VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (record_id) REFERENCES learning_records(id) ON DELETE CASCADE
);

-- 学习签到表（创建时自动生成，每行对应一个参训人员）
CREATE TABLE learning_sign_ins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    real_name VARCHAR(30) NOT NULL,
    signed_in BOOLEAN NOT NULL DEFAULT FALSE,
    signed_at DATETIME,
    UNIQUE KEY uk_record_name (record_id, real_name),
    FOREIGN KEY (record_id) REFERENCES learning_records(id) ON DELETE CASCADE
);

-- ============================================================
-- 4. 房屋花名册（业主大会的数据基础）
-- ============================================================
CREATE TABLE housing_units (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    community_id BIGINT NOT NULL,
    building VARCHAR(20) NOT NULL COMMENT '楼栋',
    unit_no VARCHAR(20) NOT NULL COMMENT '房号',
    area DECIMAL(10,2) NOT NULL COMMENT '面积·平方米',
    owner_user_id BIGINT COMMENT '关联业主用户',
    owner_name VARCHAR(30) COMMENT '业主姓名（未注册用户用）',
    phone VARCHAR(20) COMMENT '业主联系电话',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (community_id) REFERENCES communities(id),
    FOREIGN KEY (owner_user_id) REFERENCES users(id),
    UNIQUE KEY uk_building_unit (community_id, building, unit_no)
);

-- ============================================================
-- 5. 权限码体系
-- ============================================================
CREATE TABLE permissions (
    code VARCHAR(50) PRIMARY KEY COMMENT '权限码，如 committee.sign_in',
    label VARCHAR(30) NOT NULL COMMENT '中文名，如 签到',
    group_name VARCHAR(20) COMMENT '分组，如 committee/owner/reception/learning'
);

CREATE TABLE role_permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role VARCHAR(10) NOT NULL COMMENT '角色标识',
    permission_code VARCHAR(50) NOT NULL,
    FOREIGN KEY (permission_code) REFERENCES permissions(code),
    UNIQUE KEY uk_role_perm (role, permission_code)
);

-- 种子数据：所有权限码
INSERT INTO permissions (code, label, group_name) VALUES
('committee.sign_in',   '签到',       'committee'),
('committee.sign',      '签字',       'committee'),
('committee.vote',      '投票',       'committee'),
('committee.create',    '新建会议',   'committee'),
('committee.advance',   '推进阶段',   'committee'),
('committee.delivery',  '送达管理',   'committee'),
('committee.evidence',  '上传佐证',   'committee'),
('committee.topic',     '议题管理',   'committee'),
('committee.juwei',     '居委会签字', 'committee'),
('committee.sign_all',  '代录全员',   'committee'),
('committee.publish',   '发起公示',   'committee'),
('owner.create',        '新建大会',   'owner'),
('owner.advance',       '推进大会',   'owner'),
('owner.data_entry',    '录入数据',   'owner'),
('owner.publish',       '公示大会',   'owner'),
('reception.manage',    '管理接待',   'reception'),
('reception.property_feedback', '物业反馈', 'reception'),
('learning.create',     '创建学习',   'learning'),
('learning.advance',    '推进学习阶段', 'learning'),
('learning.view',       '查看学习',   'learning'),
('view.internal',       '查看内部流程', 'view'),
('view.public',         '查看公示',   'view');

-- 种子数据：角色-权限映射（和前端 perm.js/mock.js 一致）
-- 主任
INSERT INTO role_permissions (role, permission_code) VALUES
('主任','committee.sign_in'),('主任','committee.sign'),('主任','committee.vote'),
('主任','committee.create'),('主任','committee.advance'),('主任','committee.delivery'),
('主任','committee.evidence'),('主任','committee.topic'),('主任','committee.juwei'),
('主任','committee.sign_all'),('主任','committee.publish'),
('主任','owner.create'),('主任','owner.advance'),('主任','owner.data_entry'),('主任','owner.publish'),
('主任','reception.manage'),('主任','reception.property_feedback'),
('主任','learning.create'),('主任','learning.advance'),('主任','learning.view'),
('主任','view.internal'),('主任','view.public');

-- 副主任 = 主任权限
INSERT INTO role_permissions (role, permission_code)
SELECT '副主任', permission_code FROM role_permissions WHERE role = '主任';

-- 委员
INSERT INTO role_permissions (role, permission_code) VALUES
('委员','committee.sign_in'),('委员','committee.sign'),('委员','committee.vote'),
('委员','committee.evidence'),('委员','committee.topic'),('委员','committee.sign_all'),
('委员','reception.manage'),
('委员','learning.view'),
('委员','view.internal'),('委员','view.public');

-- 业主
INSERT INTO role_permissions (role, permission_code) VALUES
('业主','view.public');

-- 物业
INSERT INTO role_permissions (role, permission_code) VALUES
('物业','view.public'),('物业','reception.property_feedback');

-- 管理员 = 全部权限
INSERT INTO role_permissions (role, permission_code)
SELECT '管理员', code FROM permissions;

-- ============================================================
-- 6. 种子数据：房屋花名册（示例）
-- ============================================================
INSERT INTO housing_units (community_id, building, unit_no, area, owner_user_id, owner_name, phone) VALUES
(1,'1栋','101', 92.50,  9,  '测试业主',   '1380000101'),
(1,'1栋','102', 92.50,  NULL,'李明',       '1380000102'),
(1,'1栋','201', 95.00,  NULL,'王芳',       '1380000201'),
(1,'1栋','202', 95.00,  NULL,'赵伟',       '1380000202'),
(1,'2栋','101', 88.00,  NULL,'陈静',       '1380000301'),
(1,'2栋','102', 88.00,  NULL,'周涛',       '1380000302'),
(1,'2栋','201', 92.00,  NULL,'吴敏',       '1380000401'),
(1,'2栋','202', 92.00,  NULL,'郑强',       '1380000402'),
(1,'3栋','101', 105.00, NULL,'孙丽',       '1380000501'),
(1,'3栋','102', 105.00, NULL,'马超',       '1380000502');
