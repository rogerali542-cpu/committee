// 通用工具函数（自 miniapp/utils/api/helpers.js 迁移，去掉 mock 依赖）
// 说明：原 calcReceptionStats/calcLearningCounts/getMyState/mockState 是 mock 模式专用，
// 真实模式下接待/学习的统计走后端 api（receptionStats/learningCounts），故此处不再提供，
// 仅保留与平台无关的纯函数。getApp().globalData.activeRole → getStorage('activeRole')。
import { getStorage } from '@/utils/storage'
import { ROLE, STAGE, COMPLIANCE, RECEPTION_CATEGORY } from '@/utils/constants'

function activeRole() { return getStorage('activeRole', null) }

export function nextId(list) {
  return (list || []).reduce(function (max, item) { return Math.max(max, Number(item.id) || 0); }, 0) + 1
}

export function todayStr() {
  var d = new Date()
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}

export function qs(path, name) {
  var match = String(path).match(new RegExp('[?&]' + name + '=([^&]+)'))
  return match ? decodeURIComponent(match[1]) : ''
}

// 通知草稿
export function buildCommitteeNoticeDraft(m) {
  var title = '关于召开' + (m.title || '业主委员会会议') + '的通知'
  var lines = [
    title,
    '会议时间：' + (m.meetingDate || '待定') + ' ' + (m.meetingTime || ''),
    '会议地点：' + (m.location || '待定'),
    '主要议题：' + (m.description || '待补充'),
    '请各位委员按时参加，并提前查阅会议材料。'
  ]
  return { title: title, content: lines.filter(Boolean).join('\n'), status: 'draft' }
}

// 接待是否完成（纯字段判断）
export function receptionDone(r) {
  if (!r.fedOwner) return false
  return r.category !== RECEPTION_CATEGORY.PROPERTY || r.propertyStatus === 'replied' || !!r.fedProperty
}

// 阶段描述（会议列表卡片副标题）
export function getStageDesc(m) {
  if (m.stage === STAGE.PREPARING) return '会前准备 · 通知送达'
  if (m.stage === STAGE.ONGOING) return '进行中 · 确认参会表决'
  if (m.stage === STAGE.ENDED) {
    if (m.compliance === COMPLIANCE.INVALID) return '已结束 · 会议无效'
    if (m.publish && m.publish.published) return '已结束 · 已公示归档'
    if (m._archived) return '已结束 · 已归档'
    return '已结束 · 待归档'
  }
  return ''
}

// 个人参会状态摘要（列表卡片用）；st = { signedIn, topicVotes }（真实模式由后端 detail 提供）
export function getPersonalSummary(m, st) {
  st = st || { signedIn: false, topicVotes: {} }
  var role = (activeRole() || {}).role || ''
  var isChair = role === ROLE.CHAIR || role === ROLE.VICE_CHAIR
  var isExt = role === ROLE.OWNER || role === ROLE.PROPERTY

  if (isExt) {
    if (m.stage === STAGE.ENDED && m.compliance !== COMPLIANCE.INVALID) return '已公示，可查看纪要'
    return '内部会议，结束公示后可见'
  }
  if (m.stage === STAGE.PREPARING) {
    if (isChair) return '待主任发送通知 · 通知全体委员'
    return '等待主任发送会议通知'
  }
  if (m.stage === STAGE.ONGOING) {
    var voted = Object.keys(st.topicVotes || {}).length
    if (st.signedIn && voted >= 3) return '已完成参会，等待会议结束'
    if (st.signedIn) return '待表决 ' + (3 - voted) + ' 个议题'
    if (isChair) return '会议进行中，请关注出席和表决进度'
    return '待确认参会 · 请确认出席'
  }
  if (m.compliance === COMPLIANCE.INVALID) return '会议无效，决议不生效'
  if (m.stage === STAGE.ENDED) {
    if (isChair) return m.compliance === COMPLIANCE.VALID ? '会议有效，待公示' : m.compliance === COMPLIANCE.FLAWED ? '有效（瑕疵），待公示' : '待判定有效性'
    return '会议已结束，可查看结果'
  }
  return ''
}

export function getPersonalProgress(m, st) {
  st = st || { signedIn: false, topicVotes: {} }
  if (m.stage === STAGE.PREPARING) return { pct: 0, label: '⏳ 待开始' }
  if (m.stage === STAGE.ENDED) return { pct: 100, label: '📄 已归档' }
  var steps = [st.signedIn, Object.keys(st.topicVotes || {}).length >= 3]
  var done = steps.filter(Boolean).length
  if (done === 0) return { pct: 0, label: '🔴 待确认参会' }
  if (done === 1) return { pct: 50, label: '🔶 待表决' }
  return { pct: 100, label: '✅ 已完成' }
}

// 语音识别热词纠错：业委会场景常见同音误识（Committee 创建页 / MeetingLiveQuick 添加议题 共用）
export const HOTWORDS = [
  ['叶委会', '业委会'], ['夜委会', '业委会'], ['页委会', '业委会'], ['一委会', '业委会'],
  ['物业肥', '物业费'], ['物业菲', '物业费'],
  ['主人', '主任'], ['副主人', '副主任'],
  ['为员', '委员'], ['位员', '委员'], ['纬员', '委员'],
  ['记要', '纪要'], ['计要', '纪要'],
  ['意题', '议题'], ['一题', '议题'],
  ['签道', '签到'], ['前到', '签到'], ['前道', '签到'],
  ['表绝', '表决'],
  ['公探', '公摊'], ['弓摊', '公摊'],
  ['停车未', '停车位'], ['停车卫', '停车位'],
  ['物业公私', '物业公司'],
  ['维修基础', '维修基金'],
]
export function applyHotwords(text) {
  let r = text
  for (const [wrong, right] of HOTWORDS) r = r.replaceAll(wrong, right)
  return r
}

export default { nextId, todayStr, qs, buildCommitteeNoticeDraft, receptionDone, getStageDesc, getPersonalSummary, getPersonalProgress, applyHotwords }
