const api = require('../../utils/api');
const mock = require('../../utils/mock');
const perm = require('../../utils/perm');

let undoTimer = null;

Page({
  data: {
    learnType: 'internal',
    learnStage: 'preparing',
    trainSub: 'street',
    items: [],
    counts: { pending: 0, ongoing: 0, ended: 0 },
    done: 0, ongoing: 0, pending: 0, total: 0, target: 2,
    streetCount: 0, specialCount: 0,
    canCreate: false,
    undoVisible: false,
    undoText: '',
    createVisible: false,
    createForm: {
      title: '', date: '', time: '14:00', location: '',
      trainer: '', attendees: '', type: 'internal', description: ''
    }
  },

  onShow() {
    this.setData({ canCreate: perm.can('learning.create') });
    this.loadAll();
  },

  openDetail(e) {
    var id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/learning-detail/learning-detail?id=' + id });
  },

  async loadAll() {
    try {
      const [rawItems, counts] = await Promise.all([
        api.learningList(this.data.learnType, this.data.learnStage),
        api.learningCounts(this.data.learnType)
      ]);
      const items = this.data.learnType === 'training'
        ? rawItems.filter(i => i.type === this.data.trainSub)
        : rawItems;
      const visibleCounts = this.data.learnType === 'training' ? {
        pending: items.filter(i => i.stage === 'preparing').length,
        ongoing: items.filter(i => i.stage === 'ongoing').length,
        ended: items.filter(i => i.stage === 'ended').length
      } : counts;

      // For internal, get all items to calculate target stats
      let allItems = items;
      let trainCounts = { street: 0, special: 0 };
      if (this.data.learnType === 'internal') {
        try { allItems = await api.learningList('internal', null); } catch (e) {}
        this.setData({
          items, counts: visibleCounts,
          done: allItems.filter(i => i.stage === 'ended').length,
          ongoing: allItems.filter(i => i.stage === 'ongoing').length,
          pending: allItems.filter(i => i.stage === 'preparing').length,
          total: allItems.length
        });
      } else {
        try { const street = await api.learningList('training', null); trainCounts.street = street.filter(i => i.type === 'street').length; trainCounts.special = street.filter(i => i.type === 'special').length; } catch (e) {}
        this.setData({ items, counts: visibleCounts, streetCount: trainCounts.street, specialCount: trainCounts.special });
      }
    } catch (e) {
      const type = this.data.learnType;
      const stage = this.data.learnStage;
      let items = mock.learningList.filter(i => i.type === type || (type === 'training' && (i.type === 'street' || i.type === 'special')));
      if (type === 'training') items = items.filter(i => i.type === this.data.trainSub);
      if (stage) items = items.filter(i => i.stage === stage);
      const counts = {
        pending: items.filter(i => i.stage === 'preparing').length,
        ongoing: items.filter(i => i.stage === 'ongoing').length,
        ended: items.filter(i => i.stage === 'ended').length
      };
      this.setData({ items, counts });
    }
  },

  switchType(e) {
    this.setData({ learnType: e.currentTarget.dataset.type, learnStage: 'preparing' });
    this.loadAll();
  },

  switchTrainSub(e) {
    this.setData({ trainSub: e.currentTarget.dataset.sub, learnStage: 'preparing' });
    this.loadAll();
  },

  switchStage(e) {
    this.setData({ learnStage: e.currentTarget.dataset.stage });
    this.loadAll();
  },

  async startLearn(e) {
    const id = parseInt(e.currentTarget.dataset.id);
    const item = mock.learningList.find(i => i.id === id) || this.data.items.find(i => i.id === id);
    const prev = item ? { id, stage: item.stage, progress: item.progress } : null;
    try {
      await api.learningStart(id);
      this.showUndo('已开始学习', prev);
      this.loadAll();
    }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async finishLearn(e) {
    const id = parseInt(e.currentTarget.dataset.id);
    const item = mock.learningList.find(i => i.id === id) || this.data.items.find(i => i.id === id);
    const prev = item ? { id, stage: item.stage, progress: item.progress } : null;
    try {
      await api.learningFinish(id);
      this.showUndo('已完成学习', prev);
      this.loadAll();
    }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  showUndo(text, prev) {
    if (!prev) return;
    clearTimeout(undoTimer);
    this.undoData = prev;
    this.setData({ undoVisible: true, undoText: text });
    undoTimer = setTimeout(() => {
      this.undoData = null;
      this.setData({ undoVisible: false });
    }, 5000);
  },

  undoLearning() {
    if (!this.undoData) return;
    const item = mock.learningList.find(i => i.id === this.undoData.id);
    if (item) {
      item.stage = this.undoData.stage;
      item.progress = this.undoData.progress;
    }
    clearTimeout(undoTimer);
    this.undoData = null;
    this.setData({ undoVisible: false });
    this.loadAll();
  },

  // ── 创建学习记录 ──

  openCreate() {
    this.setData({
      createVisible: true,
      createForm: {
        title: '', date: this.todayStr(), time: '14:00', location: '社区活动室',
        trainer: '', attendees: '', type: this.data.learnType, description: ''
      }
    });
  },

  closeCreate() {
    this.setData({ createVisible: false });
  },

  noop() {},

  onCreateInput(e) {
    var field = e.currentTarget.dataset.field;
    this.setData({ ['createForm.' + field]: e.detail.value });
  },

  onDateChange(e) {
    this.setData({ 'createForm.date': e.detail.value });
  },

  onTimeChange(e) {
    this.setData({ 'createForm.time': e.detail.value });
  },

  pickCreateType(e) {
    this.setData({ 'createForm.type': e.currentTarget.dataset.type });
  },

  async submitCreate() {
    var form = this.data.createForm;
    if (!form.title || !form.date) {
      wx.showToast({ title: '请补全标题和日期', icon: 'none' });
      return;
    }
    try {
      var result = await api.learningCreate(form);
      wx.showToast({ title: '已创建', icon: 'success' });
      this.setData({ createVisible: false });
      this.loadAll();
      // 自动跳转到详情页
      wx.navigateTo({ url: '/pages/learning-detail/learning-detail?id=' + result.id });
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  todayStr() {
    var d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }
});
