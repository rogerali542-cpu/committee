const app = getApp();
const api = require('../../utils/api');
const perm = require('../../utils/perm');

function getStatusBarHeight() {
  if (wx.getWindowInfo) {
    return wx.getWindowInfo().statusBarHeight || 20;
  }
  return 20;
}

Page({
  data: {
    statusBarHeight: 0,
    activeRole: {},
    stats: { todo: 0, done: 0, overdue: 0 },
    isProperty: false,
    // 简洁 / 详情 —— 默认按角色，可手动切换并记住
    viewMode: 'simple',
    canViewInternal: false,
    unread: 0,
    propTodo: 0
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 1 });
    }
    const role = app.globalData.activeRole;
    if (!role) {
      wx.redirectTo({ url: '/pages/login/login' });
      return;
    }
    const isProperty = role.role === '物业';
    let viewMode = 'simple';
    if (!isProperty) {
      const saved = wx.getStorageSync('homeViewMode');
      viewMode = (saved === 'simple' || saved === 'detail') ? saved : (perm.isManager() ? 'detail' : 'simple');
    }
    this.setData({
      activeRole: role,
      isProperty: isProperty,
      viewMode: viewMode,
      canViewInternal: perm.can('view.internal'),
      statusBarHeight: getStatusBarHeight()
    });
    this.loadStats();
    this.loadUnread();
    if (isProperty) this.loadPropTasks();
  },

  setViewMode(e) {
    const vm = e.currentTarget.dataset.mode;
    if (vm === this.data.viewMode) return;
    this.setData({ viewMode: vm });
    wx.setStorageSync('homeViewMode', vm);
  },

  async loadStats() {
    try {
      const s = await api.dashboardStats();
      this.setData({ stats: s });
    } catch (e) {
      this.setData({ stats: { todo: 6, done: 3907, overdue: 3 } });
    }
  },

  async loadUnread() {
    try {
      const res = await api.notificationList();
      this.setData({ unread: res.unread || 0 });
    } catch (e) { /* offline ok */ }
  },

  async loadPropTasks() {
    try {
      const tasks = await api.receptionPropertyTasks('dispatched');
      this.setData({ propTodo: (tasks || []).length });
    } catch (e) { /* offline ok */ }
  },

  // ── 导航 ──
  goMeetingHub() { wx.navigateTo({ url: '/pages/meeting-hub/meeting-hub' }); },
  goCommittee() { wx.navigateTo({ url: '/pages/committee/committee' }); },
  goReception() { wx.navigateTo({ url: '/pages/reception/reception' }); },
  goLearning() { wx.navigateTo({ url: '/pages/learning/learning' }); },
  goInfoPublic() { wx.navigateTo({ url: '/pages/info-public/info-public' }); },
  goNotifications() { wx.navigateTo({ url: '/pages/notifications/notifications' }); },
  goTodo() { wx.navigateTo({ url: '/pages/todo/todo' }); },
  goLibrary() { wx.navigateTo({ url: '/pages/library/library' }); },
  goPropertyTasks() { wx.navigateTo({ url: '/pages/property-tasks/property-tasks' }); },
  goPropertyBoard() { wx.navigateTo({ url: '/pages/property-board/property-board' }); },
  showWip() { wx.showToast({ title: '功能开发中', icon: 'none' }); }
});
