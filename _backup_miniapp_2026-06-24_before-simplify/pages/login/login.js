const app = getApp();

Page({
  data: {
    internalRoles: []
  },
  onLoad() {
    // In production, first call wx.login() then backend login
    // Here we show test identities (committee members only)
    this.setData({
      internalRoles: [
        { id: 99, realName: '系统管理员', role: '管理员', desc: '管理权限分配' },
        { id: 1, realName: '张建国', role: '主任', desc: '负责召集主持会议' },
        { id: 2, realName: '李秀英', role: '副主任', desc: '协助主任开展工作' },
        { id: 3, realName: '王志强', role: '委员', desc: '确认参会和参与表决' },
        { id: 4, realName: '赵丽娟', role: '委员', desc: '确认参会和参与表决' },
        { id: 5, realName: '刘海涛', role: '委员', desc: '确认参会和参与表决' },
        { id: 6, realName: '陈晓梅', role: '委员', desc: '确认参会和参与表决' },
        { id: 7, realName: '杨国华', role: '委员', desc: '确认参会和参与表决' }
      ]
    });
  },

  doLogin(e) {
    const { id, name } = e.currentTarget.dataset;
    // Dev mode: pretend we logged in
    const token = 'dev-token-' + id;
    const activeRole = {
      id: parseInt(id),
      role: this.getRoleById(id),
      realName: name,
      communityId: 1,
      communityName: '阳光家园'
    };

    app.globalData.token = token;
    app.globalData.activeRole = activeRole;
    app.globalData.userId = id;
    wx.setStorageSync('token', token);
    wx.setStorageSync('activeRole', activeRole);

    wx.switchTab({ url: '/pages/main/main' });
  },

  getRoleById(id) {
    const map = {
      1: '主任', 2: '副主任', 3: '委员', 4: '委员', 5: '委员', 6: '委员', 7: '委员', 99: '管理员'
    };
    return map[id] || '委员';
  }
});
