const app = getApp();
const api = require('../../utils/api');

Page({
  data: {
    activeRole: {},
    headerGrad: '',
    textColor: '#5C3D00',
    roleDesc: '',
    roleClass: '',
    showStats: false,
    stats: { total: 0, voted: 0, attended: 0 },
    recordsTitle: '',
    recordsList: [],
    internalRoles: [],
    unreadCount: 0
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 2 });
    }
    const role = app.globalData.activeRole;
    if (!role) { wx.redirectTo({ url: '/pages/login/login' }); return; }

    // All identities for switching
    const isAdmin = role.role === '管理员';
    const allIdentities = [
      { id: 99, realName: '系统管理员', role: '管理员', roleClass: 'chair' },
      { id: 1, realName: '张建国', role: '主任', roleClass: 'chair' },
      { id: 2, realName: '李秀英', role: '副主任', roleClass: 'chair' },
      { id: 3, realName: '王志强', role: '委员', roleClass: 'member' },
      { id: 4, realName: '赵丽娟', role: '委员', roleClass: 'member' },
      { id: 5, realName: '刘海涛', role: '委员', roleClass: 'member' },
      { id: 6, realName: '陈晓梅', role: '委员', roleClass: 'member' },
      { id: 7, realName: '杨国华', role: '委员', roleClass: 'member' }
    ];

    const gradMap = {
      '主任': 'linear-gradient(160deg,#FFCC44,#FFA800)',
      '副主任': 'linear-gradient(160deg,#FFCC44,#FFA800)',
      '委员': 'linear-gradient(160deg,#FFCC44,#FFA800)',
      '管理员': 'linear-gradient(160deg,#5D6D7E,#2C3E50)'
    };
    const descMap = {
      '主任': '负责召集主持会议，具有最高操作权限',
      '副主任': '协助主任开展工作，享有同等会议权限',
      '委员': '确认参会，参与表决，查看会议资料',
      '管理员': '系统权限管理与配置'
    };
    const roleClassMap = {
      '主任': '', '副主任': '', '委员': ''
    };

    const light = role.role === '管理员';
    const isCommittee = ['主任', '副主任', '委员'].includes(role.role);

    // Records menu by role
    let recordsTitle = '';
    let recordsList = [];
    if (isCommittee) {
      recordsTitle = '我的记录';
      recordsList = [
        { icon: '🗳️', bg: '#FFF3DC', label: '我的投票记录' },
        { icon: '📋', bg: '#FFF3DC', label: '参会记录' },
        { icon: '✍️', bg: '#FFF3DC', label: '确认记录' },
        { icon: '📝', bg: '#F5EEF8', label: '整理的纪要' },
        { icon: '📎', bg: '#F5EEF8', label: '上传的佐证' }
      ];
    } else if (role.role === '管理员') {
      recordsTitle = '系统管理';
      recordsList = [
        { icon: '⚙️', bg: '#F5EEF8', label: '权限管理', action: 'admin' }
      ];
    }

    const stats = { total: isCommittee ? 3 : 0, voted: isCommittee ? 2 : 0, attended: isCommittee ? 3 : 0 };

    this.setData({
      activeRole: role,
      headerGrad: gradMap[role.role] || gradMap['主任'],
      textColor: light ? '#fff' : '#5C3D00',
      roleDesc: descMap[role.role] || '',
      roleClass: roleClassMap[role.role] || '',
      showStats: isCommittee,
      stats,
      recordsTitle,
      recordsList,
      internalRoles: allIdentities.filter(i => ['主任','副主任','委员','管理员'].includes(i.role))
    });
    this.loadUnread();
  },

  async loadUnread() {
    try {
      var res = await api.notificationList();
      this.setData({ unreadCount: res.unread || 0 });
    } catch (e) {}
  },

  openNotifications() {
    wx.navigateTo({ url: '/pages/notifications/notifications' });
  },

  switchRole(e) {
    const { id, name, role } = e.currentTarget.dataset;
    const newRole = {
      id: parseInt(id),
      role: role,
      realName: name,
      communityId: 1,
      communityName: '阳光家园'
    };
    app.globalData.token = 'dev-token-' + id;
    app.globalData.activeRole = newRole;
    wx.setStorageSync('token', app.globalData.token);
    wx.setStorageSync('activeRole', newRole);
    wx.showToast({ title: '已切换为 ' + name, icon: 'success' });
    setTimeout(() => this.onShow(), 400);
  },

  goAdmin() { wx.navigateTo({ url: '/pages/admin/admin' }); },
  showWip() { wx.showToast({ title: '功能开发中', icon: 'none' }); },
  doLogout() {
    app.globalData.token = '';
    app.globalData.activeRole = null;
    wx.clearStorageSync();
    wx.redirectTo({ url: '/pages/login/login' });
  }
});
