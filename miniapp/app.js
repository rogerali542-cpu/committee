App({
  globalData: {
    token: '',
    userId: null,
    activeRole: null,
    roles: [],
    baseUrl: 'http://localhost:8080'
  },

  onLaunch() {
    const token = wx.getStorageSync('token');
    if (token) {
      this.globalData.token = token;
      const role = wx.getStorageSync('activeRole');
      if (role) this.globalData.activeRole = role;
    }
  },

  // 角色判断
  isChair() { const p = require('./utils/perm'); return p.isChair(); },
  isRecorder() { const p = require('./utils/perm'); return p.isRecorder(); },
  isCommitteeMember() { const p = require('./utils/perm'); return p.isCommitteeMember(); },
  isOwner() { const C = require('./utils/constants'); return this.globalData.activeRole && this.globalData.activeRole.role === C.ROLE.OWNER; },
  isPropertyMgmt() { const C = require('./utils/constants'); return this.globalData.activeRole && this.globalData.activeRole.role === C.ROLE.PROPERTY; },
  isExternal() { const p = require('./utils/perm'); return p.isExternal(); },
  isAdmin() { const C = require('./utils/constants'); return this.globalData.activeRole && this.globalData.activeRole.role === C.ROLE.ADMIN; },
  isSelf(name) {
    return this.globalData.activeRole && this.globalData.activeRole.realName === name;
  }
});
