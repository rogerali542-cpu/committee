var api = require('../../utils/api');

Page({
  data: { list: [], unread: 0, loading: true },

  onShow() {
    this.loadList();
  },

  async loadList() {
    this.setData({ loading: true });
    try {
      var res = await api.notificationList();
      var that = this;
      var list = (res.list || []).map(function (n) {
        n._time = that.formatTime(n.createdAt);
        return n;
      });
      this.setData({ list: list, unread: res.unread || 0, loading: false });
    } catch (e) {
      this.setData({ loading: false });
    }
  },

  openMeeting(e) {
    var meetingId = e.currentTarget.dataset.meetingId;
    var nid = e.currentTarget.dataset.id;
    var type = e.currentTarget.dataset.type;
    // 标记已读
    api.notificationRead(nid).catch(function () {});
    var list = this.data.list.map(function (n) {
      if (n.id === nid) n.read = true;
      return n;
    });
    this.setData({ list: list, unread: Math.max(0, this.data.unread - 1) });
    if (meetingId) {
      if (type === 'owner_notice') {
        wx.showToast({ title: '业主大会模块已停用', icon: 'none' });
        return;
      }
      wx.navigateTo({ url: '/pages/committee-detail/committee-detail?id=' + meetingId + '&fromNotice=1' });
      return;
    }
    // 接待派单/回复类通知：无 meetingId，按角色跳到对应接待入口
    var app = getApp();
    var role = app.globalData && app.globalData.activeRole ? app.globalData.activeRole.role : '';
    if (role === '物业') {
      wx.navigateTo({ url: '/pages/property-tasks/property-tasks' });
    } else {
      wx.navigateTo({ url: '/pages/reception/reception' });
    }
  },

  async markRead(e) {
    var nid = e.currentTarget.dataset.id;
    try {
      await api.notificationRead(nid);
      var list = this.data.list.map(function (n) {
        if (n.id === nid) n.read = true;
        return n;
      });
      this.setData({ list: list, unread: Math.max(0, this.data.unread - 1) });
    } catch (e) {}
  },

  async markAllRead() {
    if (this.data.unread === 0) return;
    try {
      await api.notificationReadAll();
      var list = this.data.list.map(function (n) { n.read = true; return n; });
      this.setData({ list: list, unread: 0 });
      wx.showToast({ title: '已全部已读', icon: 'success' });
    } catch (e) {}
  },

  formatTime(iso) {
    if (!iso) return '';
    var d = new Date(iso);
    var now = new Date();
    var diff = now - d;
    if (diff < 60000) return '刚刚';
    if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
    if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
    return (d.getMonth() + 1) + '/' + d.getDate() + ' ' + d.getHours() + ':' + String(d.getMinutes()).padStart(2, '0');
  }
});
