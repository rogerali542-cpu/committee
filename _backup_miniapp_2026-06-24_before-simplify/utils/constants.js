// ═══════════════════════════════════════════════
// 全局常量 — 所有魔术字符串的唯一定义
// ═══════════════════════════════════════════════

// 会议阶段
const STAGE = {
  PREPARING: 'preparing',
  ONGOING: 'ongoing',
  ENDED: 'ended'
};

// 合规状态
const COMPLIANCE = {
  VALID: 'valid',
  INVALID: 'invalid',
  FLAWED: 'flawed'
};

// 表决选项
const VOTE_CHOICE = {
  FOR: 'for_vote',
  AGAINST: 'against',
  ABSTAIN: 'abstain'
};

// 议题类型
const TOPIC_TYPE = {
  DECISION: 'decision',
  MAJOR: 'major',
  ORDINARY: 'ordinary'
};

// 学习类型
const LEARNING_TYPE = {
  INTERNAL: 'internal',
  STREET: 'street',
  SPECIAL: 'special'
};
const LEARNING_TYPE_FILTER = {
  ...LEARNING_TYPE,
  TRAINING: 'training'
};

// 接待分类
const RECEPTION_CATEGORY = {
  PROPERTY: 'property',
  PUBLIC_AFFAIRS: 'public_affairs',
  NEIGHBOR: 'neighbor',
  OTHER: 'other'
};

// 接待分类的中文标签
const RECEPTION_CATEGORY_LABELS = {
  property: '物业类',
  public_affairs: '公共事务',
  neighbor: '邻里纠纷',
  other: '其他'
};

// 角色标识
const ROLE = {
  CHAIR: '主任',
  VICE_CHAIR: '副主任',
  COMMITTEE: '委员',
  RECORDER: '记录员',
  OWNER: '业主',
  PROPERTY: '物业',
  ADMIN: '管理员'
};

// 详情页用户视图
const USER_VIEW = {
  CHAIR: 'chair',
  MEMBER: 'member',
  RECORDER: 'recorder',
  OWNER: 'owner',
  PROPERTY: 'property'
};

// 会议推进动作
const ADVANCE_ACTION = {
  START: 'start',
  END: 'end'
};

module.exports = {
  STAGE,
  COMPLIANCE,
  VOTE_CHOICE,
  TOPIC_TYPE,
  LEARNING_TYPE,
  LEARNING_TYPE_FILTER,
  RECEPTION_CATEGORY,
  RECEPTION_CATEGORY_LABELS,
  ROLE,
  USER_VIEW,
  ADVANCE_ACTION
};
