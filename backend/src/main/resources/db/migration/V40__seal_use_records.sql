-- 印章管理：用印申请 + 使用记录留档（用印台账）（0727）。
-- 依据《印章管理制度》：印章三枚由主任、副主任分人保管；用印须登记时间、用途、文件、申请人，
-- 并经保管人确认。本表即「用印台账」，可查询、可导出。
CREATE TABLE seal_use_records (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    community_id   BIGINT       NOT NULL,
    seal_type      VARCHAR(20)  NOT NULL,           -- general_assembly / committee / finance
    purpose        TEXT,                            -- 用印事由/用途
    document_name  VARCHAR(200),                    -- 关联文件名称
    applicant_name VARCHAR(30),                     -- 申请人
    applicant_role VARCHAR(20),                     -- 申请人角色
    status         VARCHAR(15)  NOT NULL DEFAULT 'pending',  -- pending / approved / rejected
    custodian_name VARCHAR(30),                     -- 保管人确认人
    confirmed_at   DATETIME     NULL,               -- 确认（盖章留档）时间
    reject_reason  VARCHAR(200),
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 申请时间
    PRIMARY KEY (id),
    KEY idx_seal_use_community (community_id)
);

-- 演示数据：给演示社区(community_id=1)造几条用印记录，覆盖三枚印章与两种状态。
INSERT INTO seal_use_records
  (community_id, seal_type, purpose, document_name, applicant_name, applicant_role, status, custodian_name, confirmed_at, created_at) VALUES
  (1, 'committee',        '业委会会议决议公示盖章',     '第3次业委会会议决议',        '李秀英', '委员', 'approved', '张建国', '2026-06-12 10:20:00', '2026-06-12 09:30:00'),
  (1, 'finance',          '维修资金支用凭证盖章',       '电梯维保费用支付申请',        '王志强', '委员', 'approved', '张建国', '2026-06-20 15:00:00', '2026-06-20 14:10:00'),
  (1, 'general_assembly', '业主大会表决结果公告盖章',   '2026年业主大会表决结果公告',  '张建国', '主任', 'pending',  NULL,     NULL,                  '2026-07-25 16:40:00');
