var api = require('../../utils/api');
var mock = require('../../utils/mock');
var perm = require('../../utils/perm');

Page({
  data: {
    item: null,
    canManage: false   // 可以操作（创建/推进）= 主任/副主任/委员
  },

  onLoad(options) {
    this.itemId = parseInt(options.id);
    this.setData({ canManage: perm.can('learning.create') });
    this.loadItem();
  },

  onShow() { this.loadItem(); },

  loadItem() {
    var id = this.itemId;
    var item = mock.learningList.find(function (i) { return i.id === id; });
    if (item) {
      // 将 signIns 对象转为可渲染的数组
      item._signList = [];
      if (item.signIns) {
        var self = this;
        Object.keys(item.signIns).forEach(function (name) {
          item._signList.push({ name: name, signed: item.signIns[name] });
        });
      }
      item._signedCount = item._signList.filter(function (s) { return s.signed; }).length;
      item._totalCount = item._signList.length;
      // 当前用户是否在参训名单中
      var app = getApp();
      var myName = (app && app.globalData && app.globalData.activeRole) ? app.globalData.activeRole.realName : '';
      item._isMySignIn = item._signList.some(function (s) { return s.name === myName && s.signed; });
      this.setData({ item: item, myName: myName });
      return;
    }
    // fallback: 通过 API 列表查找
    var self = this;
    api.learningList('internal', null).then(function (internal) {
      var found = internal.find(function (i) { return i.id === id; });
      if (found) { self.setData({ item: found }); return; }
      api.learningList('training', null).then(function (training) {
        var f2 = training.find(function (i) { return i.id === id; });
        if (f2) self.setData({ item: f2 });
      });
    });
  },

  async notifyAll() {
    if (!this.data.canManage) return;
    try {
      await api.learningNotifyAll(this.itemId);
      wx.showToast({ title: '已通知全员', icon: 'success' });
      this.loadItem();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async signIn() {
    try {
      await api.learningSignIn(this.itemId);
      this.loadItem();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async startLearn() {
    if (!this.data.canManage) {
      wx.showToast({ title: '仅主任/副主任/委员可操作', icon: 'none' });
      return;
    }
    try {
      await api.learningStart(this.itemId);
      wx.showToast({ title: '已开始', icon: 'success' });
      this.loadItem();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async finishLearn() {
    if (!this.data.canManage) {
      wx.showToast({ title: '仅主任/副主任/委员可操作', icon: 'none' });
      return;
    }
    try {
      await api.learningFinish(this.itemId);
      wx.showToast({ title: '已完成', icon: 'success' });
      this.loadItem();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  addEvidence() {
    var that = this;
    wx.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: async function (res) {
        var name = 'IMG_' + new Date().getTime() + '.jpg';
        try {
          await api.learningAddEvidence(that.itemId, name, '照片');
          wx.showToast({ title: '已上传', icon: 'success' });
          that.loadItem();
        } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
      }
    });
  },

  removeEvidence(e) {
    var evId = e.currentTarget.dataset.evId;
    var that = this;
    try {
      api.learningRemoveEvidence(that.itemId, evId).then(function () { that.loadItem(); });
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  removeLearning() {
    var that = this;
    wx.showModal({
      title: '确认删除',
      content: '确定删除该学习记录？',
      success: async function (res) {
        if (res.confirm) {
          try {
            await api.learningRemove(that.itemId);
            wx.showToast({ title: '已删除', icon: 'success' });
            wx.navigateBack();
          } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
        }
      }
    });
  }
});
