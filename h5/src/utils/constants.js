// ═══════════════════════════════════════════════
// 全局常量 — 所有魔术字符串的唯一定义（自 miniapp/utils/constants.js 1:1 迁移）
// ═══════════════════════════════════════════════

export const STAGE = { PREPARING: 'preparing', ONGOING: 'ongoing', ENDED: 'ended' }

export const COMPLIANCE = { VALID: 'valid', INVALID: 'invalid', FLAWED: 'flawed' }

export const VOTE_CHOICE = { FOR: 'for_vote', AGAINST: 'against', ABSTAIN: 'abstain' }

export const TOPIC_TYPE = { DECISION: 'decision', MAJOR: 'major', ORDINARY: 'ordinary' }

export const LEARNING_TYPE = { INTERNAL: 'internal', STREET: 'street', SPECIAL: 'special' }
export const LEARNING_TYPE_FILTER = { ...LEARNING_TYPE, TRAINING: 'training' }

export const RECEPTION_CATEGORY = { PROPERTY: 'property', PUBLIC_AFFAIRS: 'public_affairs', NEIGHBOR: 'neighbor', OTHER: 'other' }
export const RECEPTION_CATEGORY_LABELS = { property: '物业类', public_affairs: '公共事务', neighbor: '邻里纠纷', other: '其他' }

export const ROLE = {
  CHAIR: '主任', VICE_CHAIR: '副主任', COMMITTEE: '委员', SECRETARY: '业委会秘书',
  RECORDER: '记录员', OWNER: '业主', PROPERTY: '物业',
  STREET_MANAGER: '街道管理员', DISTRICT_MANAGER: '区级管理员',
  TECHNICAL_ADMIN: '技术管理员'
}

export const USER_VIEW = { CHAIR: 'chair', MEMBER: 'member', RECORDER: 'recorder', OWNER: 'owner', PROPERTY: 'property' }

export const ADVANCE_ACTION = { START: 'start', END: 'end' }

export default {
  STAGE, COMPLIANCE, VOTE_CHOICE, TOPIC_TYPE, LEARNING_TYPE, LEARNING_TYPE_FILTER,
  RECEPTION_CATEGORY, RECEPTION_CATEGORY_LABELS, ROLE, USER_VIEW, ADVANCE_ACTION
}
