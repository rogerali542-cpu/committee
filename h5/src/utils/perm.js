// 权限判断 — 自 miniapp/utils/perm.js + mock.roleDefaultPerms 1:1 迁移。
// 从 localStorage 读 activeRole（避免对 pinia 的循环/时机依赖，任何地方可调）。
import { getStorage } from '@/utils/storage'
import { ROLE } from '@/utils/constants'

const roleDefaultPerms = {
  '主任': ['committee.*', 'reception.*', 'learning.*', 'seal.*', 'view.*', 'secretary.manage', 'member.permission.manage', 'formal.archive.revoke'],
  '副主任': ['committee.*', 'reception.*', 'learning.*', 'seal.*', 'view.*'],
  '业委会秘书': ['committee.*', 'reception.*', 'learning.*', 'seal.*', 'view.*'],
  '委员': ['committee.sign_in', 'committee.sign', 'committee.vote', 'committee.evidence', 'committee.topic', 'committee.sign_all', 'reception.manage', 'learning.view', 'seal.apply', 'seal.view', 'view.internal', 'view.public'],
  '业主': ['view.public'],
  '物业': ['view.public', 'reception.property_feedback'],
  '街道管理员': ['management.overview'],
  '区级管理员': ['management.overview'],
  '技术管理员': ['*']
}

const ALL_PERMS = [
  'committee.sign_in', 'committee.sign', 'committee.vote',
  'committee.create', 'committee.advance', 'committee.delivery',
  'committee.evidence', 'committee.topic', 'committee.juwei',
  'committee.sign_all', 'committee.publish',
  'reception.manage', 'reception.property_feedback',
  'learning.create', 'learning.advance', 'learning.view',
  'seal.apply', 'seal.approve', 'seal.view',
  'view.internal', 'view.public',
  'secretary.manage', 'member.permission.manage', 'formal.archive.revoke',
  'management.overview'
]

function activeRole() { return getStorage('activeRole', null) }

function getEffectivePerms() {
  const role = activeRole()
  if (!role || role.enabled === false) return []
  const defaults = roleDefaultPerms[role.role] || []
  const result = new Set()
  if (defaults.includes('*')) {
    ALL_PERMS.forEach((p) => result.add(p))
  } else {
    defaults.forEach((p) => {
      if (p.endsWith('.*')) {
        const prefix = p.replace('.*', '.')
        ALL_PERMS.forEach((ap) => { if (ap.startsWith(prefix)) result.add(ap) })
      } else {
        result.add(p)
      }
    })
  }
  return [...result]
}

export function can(code) {
  const perms = getEffectivePerms()
  return perms.includes(code) || perms.includes('*')
}
export function canAny(codes) { return codes.some((c) => can(c)) }
export function canAll(codes) { return codes.every((c) => can(c)) }

export function isChair() {
  const r = activeRole()
  // 兼容旧页面中的“主任操作视图”：已授权秘书使用同一套日常业务界面。
  return !!(r && r.enabled !== false && (r.role === ROLE.CHAIR || r.role === ROLE.VICE_CHAIR || r.role === ROLE.SECRETARY || r.role === ROLE.TECHNICAL_ADMIN))
}
export function isLegalChair() {
  const r = activeRole()
  return !!(r && r.enabled !== false && r.role === ROLE.CHAIR)
}
export function isGovernmentManager() {
  const r = activeRole()
  return !!(r && r.enabled !== false && (r.role === ROLE.STREET_MANAGER || r.role === ROLE.DISTRICT_MANAGER))
}
export function isRecorder() { return can('committee.delivery') && !isChair() }
export function isCommitteeMember() { return can('committee.sign_in') }
export function isExternal() { return !can('view.internal') && can('view.public') }
export function isManager() { return isChair() || isRecorder() }

export default { can, canAny, canAll, isChair, isLegalChair, isGovernmentManager, isRecorder, isCommitteeMember, isExternal, isManager, getEffectivePerms }
