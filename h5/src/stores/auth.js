import { defineStore } from 'pinia'
import { getStorage, setStorage, removeStorage } from '@/utils/storage'
import { ROLE } from '@/utils/constants'

// 登录态（开发态假登录：token = 'dev-token-<roleId>'，后端按 X-Active-Role-Id 头识别角色）
// localStorage 是持久真相，store 是响应式镜像；网络层/权限工具直接读 localStorage。
export const useAuthStore = defineStore('auth', {
  state: () => ({ token: '', userId: null, activeRole: null }),
  getters: {
    isLoggedIn: (s) => !!(s.token || (s.activeRole && s.activeRole.id)),
    roleName: (s) => (s.activeRole ? s.activeRole.role : ''),
    realName: (s) => (s.activeRole ? s.activeRole.realName : ''),
    isChair: (s) => {
      const r = s.activeRole && s.activeRole.role
      return r === ROLE.CHAIR || r === ROLE.VICE_CHAIR || r === ROLE.SECRETARY || r === ROLE.TECHNICAL_ADMIN
    },
    isAdmin: (s) => !!(s.activeRole && s.activeRole.role === ROLE.TECHNICAL_ADMIN)
  },
  actions: {
    // 从 localStorage 恢复（app 启动时调）
    restore() {
      const token = getStorage('token', '')
      const activeRole = getStorage('activeRole', null)
      if (token) this.token = token
      if (activeRole) this.activeRole = activeRole
      if (this.activeRole && this.activeRole.id) this.userId = this.activeRole.id
    },
    // role: { id, realName, role, communityId, communityName }
    login(role) {
      const token = 'dev-token-' + role.id
      this.token = token
      this.activeRole = role
      this.userId = role.id
      setStorage('token', token)
      setStorage('activeRole', role)
    },
    switchRole(role) { this.login(role) },
    logout() {
      this.token = ''
      this.activeRole = null
      this.userId = null
      removeStorage('token')
      removeStorage('activeRole')
    }
  }
})
