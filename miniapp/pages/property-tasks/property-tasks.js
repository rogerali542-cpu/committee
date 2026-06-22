// 物业工作台 —— 业委会派来的物业类工单
// 状态：物业未处理(dispatched) → 处理中(processing) → 处理完(replied)
const api = require('../../utils/api');

Page({
  data: {
    filter: 'pending',        // pending=待处理(未处理+处理中) replied=已完成 all=全部
    counts: { pending: 0, replied: 0, all: 0 },
    items: [],
    loading: true,
    // 回填面板
    replyVisible: false,
    replyId: null,
    replyEvidences: [],
    replyText: '',
    submitting: false
  },

  onShow() {
    const app = getApp();
    const role = app.globalData && app.globalData.activeRole;
    if (!role) { wx.redirectTo({ url: '/pages/login/login' }); return; }
    this.loadTasks();
  },

  async loadTasks() {
    this.setData({ loading: true });
    try {
      const all = await api.receptionPropertyTasks('all');
      const isPending = r => r.propertyStatus === 'dispatched' || r.propertyStatus === 'processing';
      const counts = {
        all: all.length,
        pending: all.filter(isPending).length,
        replied: all.filter(r => r.propertyStatus === 'replied').length
      };
      const filter = this.data.filter;
      const items = filter === 'all' ? all
        : filter === 'pending' ? all.filter(isPending)
        : all.filter(r => r.propertyStatus === 'replied');
      this.setData({ items, counts, loading: false });
      // 回填面板开着时同步当前工单的佐证
      if (this.data.replyVisible && this.data.replyId) {
        const cur = all.find(r => r.id === this.data.replyId);
        if (cur) this.setData({ replyEvidences: cur.evidences || [] });
      }
    } catch (e) {
      this.setData({ loading: false });
    }
  },

  switchFilter(e) {
    this.setData({ filter: e.currentTarget.dataset.filter });
    this.loadTasks();
  },

  noop() {},

  // 物业未处理 → 开始处理
  async startTask(e) {
    const id = e.currentTarget.dataset.id;
    try {
      await api.receptionPropertyStart(id);
      this.loadTasks();
    } catch (err) { wx.showToast({ title: err.message || '操作失败', icon: 'none' }); }
  },

  // 上传佐证（处理中工单）
  addEvidence(e) {
    const id = e.currentTarget.dataset.id;
    const that = this;
    wx.chooseImage({
      count: 1, sizeType: ['compressed'], sourceType: ['album', 'camera'],
      success: async function () {
        const name = 'IMG_' + new Date().getTime() + '.jpg';
        try {
          await api.receptionAddEvidence(id, name, '照片');
          wx.showToast({ title: '已上传', icon: 'success' });
          that.loadTasks();
        } catch (err) { wx.showToast({ title: err.message || '上传失败', icon: 'none' }); }
      }
    });
  },
  async removeEvidence(e) {
    const rid = e.currentTarget.dataset.rid;
    const evId = e.currentTarget.dataset.evId;
    try {
      await api.receptionRemoveEvidence(rid, evId);
      this.loadTasks();
    } catch (err) { wx.showToast({ title: err.message || '删除失败', icon: 'none' }); }
  },

  // 填写结果并完成
  openReply(e) {
    const id = e.currentTarget.dataset.id;
    const rec = this.data.items.find(r => r.id === id);
    this.setData({
      replyVisible: true,
      replyId: id,
      replyText: '',
      replyEvidences: rec ? (rec.evidences || []) : []
    });
  },
  closeReply() {
    this.setData({ replyVisible: false, replyId: null, replyText: '', replyEvidences: [] });
  },
  onReplyInput(e) {
    this.setData({ replyText: e.detail.value });
  },
  async submitReply() {
    if (this.data.submitting) return;
    const reply = (this.data.replyText || '').trim();
    if (!reply) { wx.showToast({ title: '请填写处理结果', icon: 'none' }); return; }
    if (!this.data.replyEvidences.length) { wx.showToast({ title: '请先上传处理佐证', icon: 'none' }); return; }
    this.setData({ submitting: true });
    try {
      await api.receptionPropertyReply(this.data.replyId, reply);
      wx.showToast({ title: '已完成', icon: 'success' });
      this.setData({ replyVisible: false, replyId: null, replyText: '', replyEvidences: [], submitting: false });
      this.loadTasks();
    } catch (err) {
      this.setData({ submitting: false });
      wx.showToast({ title: err.message || '提交失败', icon: 'none' });
    }
  }
});
