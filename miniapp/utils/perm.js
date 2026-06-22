const mock = require('./mock');
const C = require('./constants');

// 当前用户的最终权限（默认+覆写）
function getEffectivePerms() {
  const app = getApp();
  const role = app && app.globalData && app.globalData.activeRole;
  if (!role) return [];

  const defaults = mock.roleDefaultPerms[role.role] || [];
  const overrides = (mock.userPermOverrides && mock.userPermOverrides[role.id]) || {};

  const result = new Set();
  defaults.forEach(p => result.add(p));

  // 处理通配符
  if (defaults.includes('*')) {
    // 全权限：加上所有已知权限
    ALL_PERMS.forEach(p => result.add(p));
  } else {
    defaults.forEach(p => {
      if (p.endsWith('.*')) {
        const prefix = p.replace('.*', '.');
        ALL_PERMS.forEach(ap => { if (ap.startsWith(prefix)) result.add(ap); });
      }
    });
  }

  // 覆写
  Object.entries(overrides).forEach(([code, granted]) => {
    if (granted) result.add(code);
    else result.delete(code);
  });

  return [...result];
}

// 所有权限码
const ALL_PERMS = [
  'committee.sign_in', 'committee.sign', 'committee.vote',
  'committee.create', 'committee.advance', 'committee.delivery',
  'committee.evidence', 'committee.topic', 'committee.juwei',
  'committee.sign_all', 'committee.publish',
  'reception.manage', 'reception.property_feedback',
  'learning.create', 'learning.advance', 'learning.view',
  'view.internal', 'view.public'
];

// 权限标签
const PERM_LABELS = {
  'committee.sign_in': '确认参会', 'committee.sign': '记录确认（兼容）', 'committee.vote': '投票',
  'committee.create': '新建会议', 'committee.advance': '推进阶段', 'committee.delivery': '送达管理',
  'committee.evidence': '上传佐证', 'committee.topic': '议题管理', 'committee.juwei': '居委会签字',
  'committee.sign_all': '代录全员', 'committee.publish': '发起公示',
  'reception.manage': '管理接待', 'reception.property_feedback': '物业反馈',
  'learning.create': '创建学习', 'learning.advance': '推进学习阶段', 'learning.view': '查看学习',
  'view.internal': '查看内部', 'view.public': '查看公示'
};

function can(code) {
  const perms = getEffectivePerms();
  return perms.includes(code) || perms.includes('*');
}

function canAny(codes) {
  return codes.some(c => can(c));
}

function canAll(codes) {
  return codes.every(c => can(c));
}

// 便捷判断（向后兼容旧的角色判断）
function isChair() {
  const app = getApp();
  const r = app && app.globalData && app.globalData.activeRole;
  return r && (r.role === C.ROLE.CHAIR || r.role === C.ROLE.VICE_CHAIR || r.role === C.ROLE.ADMIN);
}
function isRecorder() { return can('committee.delivery') && !isChair(); }
function isCommitteeMember() { return can('committee.sign_in'); }
function isExternal() { return !can('view.internal') && can('view.public'); }
// 管理角色：主任/副主任/管理员/记录员 → 默认进会议管理；其余（普通委员/业主/物业）走简洁模式
function isManager() { return isChair() || isRecorder(); }

module.exports = { can, canAny, canAll, isChair, isRecorder, isCommitteeMember, isExternal, isManager, getEffectivePerms, PERM_LABELS };
