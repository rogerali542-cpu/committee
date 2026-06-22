const app = getApp();

Page({
  data: {
    internalRoles: [],
    externalRoles: []
  },
  onLoad() {
    // Dev mode: use fixed test accounts
    const roleDesc = {
      '主任': '负责召集主持会议',
      '副主任': '协助主任开展工作',
      '委员': '确认参会和参与表决',
      '业主': '查看公开信息',
      '物业': '接待工单处理'
    };

    // In production, first call wx.login() then backend login
    // Here we show test identities
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
      ],
      externalRoles: [
        { id: 9, realName: '测试业主', role: '业主', desc: '查看公开信息' },
        { id: 10, realName: '物业张经理', role: '物业', desc: '接待工单处理' }
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
      1: '主任', 2: '副主任', 3: '委员', 4: '委员', 5: '委员', 6: '委员', 7: '委员', 9: '业主', 10: '物业', 99: '管理员'
    };
    return map[id] || '委员';
  }
});
