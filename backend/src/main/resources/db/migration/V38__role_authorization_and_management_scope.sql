-- 身份授权与管理范围底座：
-- 1. 秘书是独立身份，由主任授权/撤销，不冒充主任；
-- 2. 区、街道管理人员通过 scope_level + scope_region_code 限定数据范围；
-- 3. 技术管理员保留为隐藏身份，不进入普通业务身份列表。
ALTER TABLE user_roles
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN authorized_by_role_id BIGINT NULL,
    ADD COLUMN authorized_at DATETIME NULL,
    ADD COLUMN revoked_at DATETIME NULL,
    ADD COLUMN scope_level VARCHAR(20) NOT NULL DEFAULT 'COMMUNITY',
    ADD COLUMN scope_region_code VARCHAR(30) NULL,
    ADD COLUMN scope_region_name VARCHAR(80) NULL,
    ADD CONSTRAINT fk_user_roles_authorized_by
        FOREIGN KEY (authorized_by_role_id) REFERENCES user_roles(id);

-- 原演示“记录员”升级为正式的业委会秘书，并由主任授权。
UPDATE user_roles
SET role = '业委会秘书',
    enabled = TRUE,
    authorized_by_role_id = (
        SELECT chair.id
        FROM (SELECT id FROM user_roles WHERE community_id = 1 AND role = '主任' ORDER BY id LIMIT 1) chair
    ),
    authorized_at = CURRENT_TIMESTAMP,
    revoked_at = NULL
WHERE role = '记录员' AND real_name = '秘书小李';

-- 隐藏技术管理员：只用于账号恢复、系统配置和数据修复，不显示在测试身份列表。
INSERT INTO users (openid, nick_name)
SELECT 'internal-technical-admin', '技术管理员'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE openid = 'internal-technical-admin');

INSERT INTO user_roles (
    user_id, community_id, role, real_name, enabled,
    scope_level, scope_region_code, scope_region_name
)
SELECT u.id, c.id, '技术管理员', '技术管理员', TRUE,
       'TECHNICAL', '310106', '上海市静安区'
FROM users u
JOIN communities c ON c.id = (SELECT MIN(id) FROM communities)
WHERE u.openid = 'internal-technical-admin'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur
      WHERE ur.user_id = u.id AND ur.role = '技术管理员'
  );
