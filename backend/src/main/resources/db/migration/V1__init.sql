-- V1: 初始建表
-- 用户与身份
CREATE TABLE communities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid VARCHAR(64) NOT NULL UNIQUE,
    unionid VARCHAR(64),
    nick_name VARCHAR(50),
    avatar_url VARCHAR(500),
    phone VARCHAR(20),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    community_id BIGINT NOT NULL,
    role VARCHAR(10) NOT NULL,
    real_name VARCHAR(30) NOT NULL,
    room_number VARCHAR(50),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (community_id) REFERENCES communities(id)
);

-- 业主委员会会议
CREATE TABLE committee_meetings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    community_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    meeting_date DATE,
    meeting_time TIME,
    location VARCHAR(100),
    stage VARCHAR(15) NOT NULL DEFAULT 'preparing',
    compliance VARCHAR(10),
    description TEXT,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (community_id) REFERENCES communities(id)
);

CREATE TABLE meeting_deliveries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL,
    user_role_id BIGINT NOT NULL,
    notice_delivered BOOLEAN NOT NULL DEFAULT FALSE,
    material_delivered BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uk_meeting_user (meeting_id, user_role_id),
    FOREIGN KEY (meeting_id) REFERENCES committee_meetings(id),
    FOREIGN KEY (user_role_id) REFERENCES user_roles(id)
);

CREATE TABLE meeting_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL UNIQUE,
    has_decision BOOLEAN NOT NULL DEFAULT FALSE,
    has_major_issue BOOLEAN NOT NULL DEFAULT FALSE,
    juwei_name VARCHAR(50) DEFAULT '王红梅（社区居委会）',
    juwei_signed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (meeting_id) REFERENCES committee_meetings(id)
);

CREATE TABLE record_attendances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    user_role_id BIGINT NOT NULL,
    signed_in BOOLEAN NOT NULL DEFAULT FALSE,
    signed BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uk_record_user (record_id, user_role_id),
    FOREIGN KEY (record_id) REFERENCES meeting_records(id),
    FOREIGN KEY (user_role_id) REFERENCES user_roles(id)
);

CREATE TABLE record_topics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    type VARCHAR(15) NOT NULL DEFAULT 'decision',
    sort_order INT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (record_id) REFERENCES meeting_records(id)
);

CREATE TABLE topic_votes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic_id BIGINT NOT NULL,
    user_role_id BIGINT NOT NULL,
    choice VARCHAR(10) NOT NULL,
    UNIQUE KEY uk_topic_user (topic_id, user_role_id),
    FOREIGN KEY (topic_id) REFERENCES record_topics(id),
    FOREIGN KEY (user_role_id) REFERENCES user_roles(id)
);

CREATE TABLE record_evidences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    file_type VARCHAR(20),
    file_url VARCHAR(500),
    FOREIGN KEY (record_id) REFERENCES meeting_records(id)
);

CREATE TABLE meeting_publishes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL UNIQUE,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    publish_date DATE,
    FOREIGN KEY (meeting_id) REFERENCES committee_meetings(id)
);

-- 业主大会
CREATE TABLE owner_meetings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    community_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    meeting_date DATE,
    meeting_time TIME,
    location VARCHAR(100),
    type VARCHAR(10) DEFAULT 'regular',
    stage VARCHAR(15) NOT NULL DEFAULT 'preparing',
    compliance VARCHAR(10),
    description TEXT,
    total_owners INT DEFAULT 120,
    total_area INT DEFAULT 18500,
    needs_vote BOOLEAN DEFAULT TRUE,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (community_id) REFERENCES communities(id)
);

CREATE TABLE owner_meeting_notifies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL UNIQUE,
    sent_count INT NOT NULL DEFAULT 0,
    announced BOOLEAN NOT NULL DEFAULT FALSE,
    content_complete BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (meeting_id) REFERENCES owner_meetings(id)
);

CREATE TABLE owner_meeting_ballots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL UNIQUE,
    delivered_count INT NOT NULL DEFAULT 0,
    records_complete BOOLEAN NOT NULL DEFAULT FALSE,
    non_face_announce BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (meeting_id) REFERENCES owner_meetings(id)
);

CREATE TABLE owner_meeting_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL UNIQUE,
    present_owners INT DEFAULT 0,
    present_area INT DEFAULT 0,
    supervisor_name VARCHAR(50) DEFAULT '（街道办/居委会监督员）',
    supervisor_signed BOOLEAN DEFAULT FALSE,
    designated BOOLEAN NOT NULL DEFAULT FALSE,
    monitor BOOLEAN NOT NULL DEFAULT FALSE,
    callout BOOLEAN NOT NULL DEFAULT FALSE,
    tally BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (meeting_id) REFERENCES owner_meetings(id)
);

CREATE TABLE owner_meeting_topics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    type VARCHAR(15) NOT NULL DEFAULT 'ordinary',
    for_owners INT DEFAULT 0,
    ag_owners INT DEFAULT 0,
    ab_owners INT DEFAULT 0,
    for_area INT DEFAULT 0,
    ag_area INT DEFAULT 0,
    ab_area INT DEFAULT 0,
    FOREIGN KEY (record_id) REFERENCES owner_meeting_records(id)
);

CREATE TABLE owner_meeting_evidences (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    file_type VARCHAR(20),
    file_url VARCHAR(500),
    FOREIGN KEY (record_id) REFERENCES owner_meeting_records(id)
);

CREATE TABLE owner_meeting_publishes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id BIGINT NOT NULL UNIQUE,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    publish_date DATE,
    FOREIGN KEY (meeting_id) REFERENCES owner_meetings(id)
);

-- 接待记录
CREATE TABLE reception_systems (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    community_id BIGINT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    time_desc VARCHAR(100),
    place VARCHAR(200),
    person VARCHAR(100),
    FOREIGN KEY (community_id) REFERENCES communities(id)
);

CREATE TABLE reception_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    community_id BIGINT NOT NULL,
    date DATE,
    time TIME,
    visitor_name VARCHAR(30),
    room VARCHAR(50),
    receiver VARCHAR(30),
    category VARCHAR(15) NOT NULL DEFAULT 'property',
    content TEXT,
    resolution TEXT,
    fed_property BOOLEAN NOT NULL DEFAULT FALSE,
    fed_owner BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (community_id) REFERENCES communities(id)
);

-- 业务学习
CREATE TABLE learning_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    community_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    date DATE,
    time TIME,
    location VARCHAR(100),
    trainer VARCHAR(30),
    type VARCHAR(15) NOT NULL DEFAULT 'internal',
    stage VARCHAR(15) NOT NULL DEFAULT 'pending',
    progress INT NOT NULL DEFAULT 0,
    attendees VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (community_id) REFERENCES communities(id)
);

-- ===== 初始种子数据 =====
INSERT INTO communities (name, address) VALUES ('阳光家园', '幸福路88号');

INSERT INTO users (openid, nick_name) VALUES
    ('dev-openid-zhang', '张建国'),
    ('dev-openid-li-xiuying', '李秀英'),
    ('dev-openid-wang', '王志强'),
    ('dev-openid-zhao', '赵丽娟'),
    ('dev-openid-liu', '刘海涛'),
    ('dev-openid-chen', '陈晓梅'),
    ('dev-openid-yang', '杨国华'),
    ('dev-openid-secretary', '秘书小李'),
    ('dev-openid-owner', '测试业主'),
    ('dev-openid-property', '物业张经理');

INSERT INTO user_roles (user_id, community_id, role, real_name) VALUES
    (1, 1, '主任', '张建国'),
    (2, 1, '副主任', '李秀英'),
    (3, 1, '委员', '王志强'),
    (4, 1, '委员', '赵丽娟'),
    (5, 1, '委员', '刘海涛'),
    (6, 1, '委员', '陈晓梅'),
    (7, 1, '委员', '杨国华'),
    (8, 1, '记录员', '秘书小李'),
    (9, 1, '业主', '测试业主'),
    (10, 1, '物业', '物业张经理');

-- 接待制度初始化
INSERT INTO reception_systems (community_id, published, time_desc, place, person) VALUES
    (1, TRUE, '每月10日 14:00–16:00', '社区党群服务中心一楼接待室', '业委会委员轮值（详见公示栏）');
