// ═══════════════════════════════════════════════
// API 工具函数 — 从原 api.js 提取
// ═══════════════════════════════════════════════

const mock = require('../mock');
const C = require('../constants');

// ── 可变 mock 状态（每人独立）──
// 结构: { "meetingId_roleId": { signedIn, signed, topicVotes: { topicId: choice } } }，signed 仅保留兼容旧数据
const mockState = {};

function nextId(list) {
  return list.reduce(function (max, item) { return Math.max(max, Number(item.id) || 0); }, 0) + 1;
}

function todayStr() {
  var d = new Date();
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}

function qs(path, name) {
  var match = path.match(new RegExp('[?&]' + name + '=([^&]+)'));
  return match ? decodeURIComponent(match[1]) : '';
}

function getMyState(meetingId) {
  var app = getApp();
  var role = app && app.globalData && app.globalData.activeRole;
  var roleId = role ? role.id : 1;
  var key = meetingId + '_' + roleId;
  if (!mockState[key]) {
    mockState[key] = { signedIn: false, signed: false, topicVotes: {} };
  }
  return mockState[key];
}

// ── 通知草稿 ──

function buildCommitteeNoticeDraft(m) {
  var title = '关于召开' + (m.title || '业主委员会会议') + '的通知';
  var lines = [
    title,
    '会议时间：' + (m.meetingDate || '待定') + ' ' + (m.meetingTime || ''),
    '会议地点：' + (m.location || '待定'),
    '主要议题：' + (m.description || '待补充'),
    '请各位委员按时参加，并提前查阅会议材料。'
  ];
  return { title: title, content: lines.filter(Boolean).join('\n'), status: 'draft' };
}

// ── 统计函数 ──

function receptionDone(r) {
  if (!r.fedOwner) return false;
  // 物业类：物业已回复(propertyStatus==='replied')才算物业侧完成；兼容旧数据的 fedProperty
  return r.category !== C.RECEPTION_CATEGORY.PROPERTY
    || r.propertyStatus === 'replied' || !!r.fedProperty;
}

function calcReceptionStats() {
  var now = new Date();
  var ym = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');
  return {
    monthCount: mock.receptionRecords.filter(function (r) { return (r.date || '').startsWith(ym); }).length,
    monthLabel: (now.getMonth() + 1) + '月已接待',
    pending: mock.receptionRecords.filter(function (r) { return !receptionDone(r); }).length,
    yearCount: mock.receptionRecords.filter(function (r) { return String(r.date || '').startsWith(String(now.getFullYear())); }).length,
    done: mock.receptionRecords.filter(receptionDone).length,
    total: mock.receptionRecords.length
  };
}

function calcLearningCounts(type) {
  var items = mock.learningList;
  if (type === C.LEARNING_TYPE.INTERNAL) items = items.filter(function (i) { return i.type === C.LEARNING_TYPE.INTERNAL; });
  else if (type === 'training') items = items.filter(function (i) { return i.type === C.LEARNING_TYPE.STREET || i.type === C.LEARNING_TYPE.SPECIAL; });
  else if (type) items = items.filter(function (i) { return i.type === type; });
  return {
    pending: items.filter(function (i) { return i.stage === C.STAGE.PREPARING; }).length,
    ongoing: items.filter(function (i) { return i.stage === C.STAGE.ONGOING; }).length,
    ended: items.filter(function (i) { return i.stage === C.STAGE.ENDED; }).length
  };
}

// ── 个人参会状态（列表卡片用）──

function getPersonalSummary(m, st) {
  var app = getApp();
  var role = (app && app.globalData && app.globalData.activeRole) ? app.globalData.activeRole.role : '';
  var isChair = role === C.ROLE.CHAIR || role === C.ROLE.VICE_CHAIR;
  var isExt = role === C.ROLE.OWNER || role === C.ROLE.PROPERTY;

  if (isExt) {
    if (m.stage === C.STAGE.ENDED && m.compliance !== C.COMPLIANCE.INVALID) return '已公示，可查看纪要';
    return '内部会议，结束公示后可见';
  }

  if (m.stage === C.STAGE.PREPARING) {
    if (isChair) return '待主任发送通知 · 通知全体委员';
    return '等待主任发送会议通知';
  }

  if (m.stage === C.STAGE.ONGOING) {
    if (st.signedIn && Object.keys(st.topicVotes).length >= 3) return '已完成参会，等待会议结束';
    if (st.signedIn) return '待表决 ' + (3 - Object.keys(st.topicVotes).length) + ' 个议题';
    if (isChair) return '会议进行中，请关注出席和表决进度';
    return '待确认参会 · 请确认出席';
  }

  if (m.compliance === C.COMPLIANCE.INVALID) return '会议无效，决议不生效';
  if (m.stage === C.STAGE.ENDED) {
    if (isChair) return m.compliance === C.COMPLIANCE.VALID ? '会议有效，待公示' : m.compliance === C.COMPLIANCE.FLAWED ? '有效（瑕疵），待公示' : '待判定有效性';
    return '会议已结束，可查看结果';
  }
  return '';
}

// 阶段描述（列表卡片副标题）
function getStageDesc(m) {
  if (m.stage === C.STAGE.PREPARING) return '会前准备 · 通知送达';
  if (m.stage === C.STAGE.ONGOING) return '进行中 · 确认参会表决';
  if (m.stage === C.STAGE.ENDED) {
    if (m.compliance === C.COMPLIANCE.INVALID) return '已结束 · 会议无效';
    if (m.publish && m.publish.published) return '已结束 · 已公示归档';
    if (m._archived) return '已结束 · 已归档';
    return '已结束 · 待归档';
  }
  return '';
}

function getPersonalProgress(m, st) {
  if (m.stage === C.STAGE.PREPARING) return { pct: 0, label: '⏳ 待开始' };
  if (m.stage === C.STAGE.ENDED) return { pct: 100, label: '📄 已归档' };
  var steps = [st.signedIn, Object.keys(st.topicVotes).length >= 3];
  var done = steps.filter(Boolean).length;
  if (done === 0) return { pct: 0, label: '🔴 待确认参会' };
  if (done === 1) return { pct: 50, label: '🔶 待表决' };
  return { pct: 100, label: '✅ 已完成' };
}

module.exports = {
  mockState,
  nextId,
  todayStr,
  qs,
  buildCommitteeNoticeDraft,
  receptionDone,
  calcReceptionStats,
  calcLearningCounts,
  getMyState,
  getPersonalSummary,
  getPersonalProgress,
  getStageDesc
};
