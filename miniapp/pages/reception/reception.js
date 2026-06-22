const api = require('../../utils/api');
const mock = require('../../utils/mock');
const perm = require('../../utils/perm');

Page({
  data: {
    sys: { published: true, timeDesc: '', place: '', person: '' },
    stats: { monthCount: 0, monthLabel: '', pending: 0, yearCount: 0, done: 0, total: 0 },
    records: [],
    filter: 'all',
    canManage: false,
    canFeedback: false,
    createVisible: false,
    resolutionVisible: false,
    editingId: null,
    editingResolution: '',
    createForm: {
      date: '',
      time: '14:00',
      visitorName: '',
      room: '',
      receiver: '',
      category: 'property',
      content: ''
    }
  },

  onShow() {
    this.setData({
      canManage: perm.can('reception.manage'),
      canFeedback: perm.can('reception.property_feedback')
    });
    this.loadAll();
  },

  async loadAll() {
    try {
      const [sys, records, stats] = await Promise.all([
        api.receptionSystem(),
        api.receptionRecords(this.data.filter),
        api.receptionStats()
      ]);
      const now = new Date();
      this.setData({
        sys: sys || {},
        records: this.enrichRecords(records),
        stats: {
          ...stats,
          monthLabel: (now.getMonth()+1) + '月已接待'
        }
      });
    } catch (e) {
      const filter = this.data.filter;
      let recs = mock.receptionRecords;
      if (filter === 'pending') recs = recs.filter(r => !r.done);
      if (filter === 'done') recs = recs.filter(r => r.done);
      const now = new Date();
      this.setData({
        sys: mock.receptionSystem,
        records: this.enrichRecords(recs),
        stats: { ...mock.receptionStats, monthLabel: (now.getMonth()+1) + '月已接待' }
      });
    }
  },

  // 给每条记录算出「下一步该做什么」——业委会视角，补上闭环断点
  enrichRecords(list) {
    const canManage = this.data.canManage;
    return (list || []).map((r) => {
      let nextAction = null;
      if (canManage && !r.done) {
        if (r.category === 'property') {
          if (r.propertyStatus === 'replied' && !r.fedOwner) {
            nextAction = { text: '物业已回复，下一步：把结果反馈给业主', action: 'fedOwner', cta: '向业主反馈' };
          } else if (!r.propertyStatus || r.propertyStatus === 'pending_dispatch') {
            nextAction = { text: '下一步：转物业处理', action: 'dispatch', cta: '转物业处理' };
          } else if (r.propertyStatus === 'dispatched') {
            nextAction = { text: '已派物业，等待物业受理', action: '', cta: '' };
          } else if (r.propertyStatus === 'processing') {
            nextAction = { text: '物业处理中，请等待回填结果', action: '', cta: '' };
          }
        } else if (!r.fedOwner) {
          nextAction = { text: '下一步：向业主反馈处理结果', action: 'fedOwner', cta: '向业主反馈' };
        }
      }
      return Object.assign({}, r, { nextAction });
    });
  },

  doNextAction(e) {
    const action = e.currentTarget.dataset.action;
    const id = e.currentTarget.dataset.id;
    if (action === 'dispatch') {
      this.dispatchProperty({ currentTarget: { dataset: { id } } });
    } else if (action === 'fedOwner') {
      this.toggleFollow({ currentTarget: { dataset: { id, field: 'fedOwner' } } });
    }
  },

  switchFilter(e) {
    this.setData({ filter: e.currentTarget.dataset.filter });
    this.loadAll();
  },

  openCreate() {
    if (!this.data.canManage) return;
    this.setData({
      createVisible: true,
      createForm: {
        date: this.todayStr(),
        time: '14:00',
        visitorName: '',
        room: '',
        receiver: '',
        category: 'property',
        content: ''
      }
    });
  },

  closeCreate() {
    this.setData({ createVisible: false });
  },

  noop() {},

  pickCategory(e) {
    this.setData({ 'createForm.category': e.currentTarget.dataset.category });
  },

  onDateChange(e) {
    this.setData({ 'createForm.date': e.detail.value });
  },

  onTimeChange(e) {
    this.setData({ 'createForm.time': e.detail.value });
  },

  onCreateInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['createForm.' + field]: e.detail.value });
  },

  async submitCreate() {
    const form = this.data.createForm;
    if (!form.date || !form.visitorName || !form.content) {
      wx.showToast({ title: '请补全日期、来访业主和诉求内容', icon: 'none' });
      return;
    }
    try {
      await api.receptionCreate(form);
      wx.showToast({ title: '已登记', icon: 'success' });
      this.setData({ createVisible: false, filter: 'all' });
      this.loadAll();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  openSysEditor() {
    wx.showModal({
      title: '编辑接待制度',
      editable: true,
      placeholderText: '定时：每周六上午9:00-11:00\n定点：物业办公室\n定人：张建国（主任）',
      content: this.data.sys.timeDesc + '\n' + this.data.sys.place + '\n' + this.data.sys.person,
      success: async (res) => {
        if (res.confirm && res.content) {
          var lines = res.content.split('\n').filter(l => l.trim());
          var updates = { timeDesc: lines[0] || '', place: lines[1] || '', person: lines[2] || '' };
          try {
            await api.receptionUpdateSystem(updates);
            this.loadAll();
          } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
        }
      }
    });
  },

  async togglePublish() {
    try {
      await api.receptionUpdateSystem({ published: !this.data.sys.published });
      this.loadAll();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  openResolutionEditor(e) {
    var id = e.currentTarget.dataset.id;
    var record = this.data.records.find(function (r) { return r.id === id; });
    this.setData({
      resolutionVisible: true,
      editingId: id,
      editingResolution: record ? (record.resolution || '') : ''
    });
  },

  closeResolutionEditor() {
    this.setData({ resolutionVisible: false, editingId: null, editingResolution: '' });
  },

  onResolutionInput(e) {
    this.setData({ editingResolution: e.detail.value });
  },

  async submitResolution() {
    var id = this.data.editingId;
    var resolution = this.data.editingResolution;
    if (!resolution.trim()) {
      wx.showToast({ title: '请输入处理结果', icon: 'none' });
      return;
    }
    try {
      await api.receptionUpdateResolution(id, resolution.trim());
      wx.showToast({ title: '已保存', icon: 'success' });
      this.setData({ resolutionVisible: false, editingId: null, editingResolution: '' });
      this.loadAll();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  dispatchProperty(e) {
    if (!this.data.canManage) return;
    var id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '转物业处理',
      content: '将该物业类诉求派给物业处理。物业会收到通知并回填处理结果。',
      confirmText: '确认派单',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await api.receptionDispatch(id);
          wx.showToast({ title: '已转物业', icon: 'success' });
          this.loadAll();
        } catch (err) { wx.showToast({ title: err.message || '派单失败', icon: 'none' }); }
      }
    });
  },

  async toggleFollow(e) {
    var field = e.currentTarget.dataset.field;
    if (field === 'fedProperty' && !this.data.canManage && !this.data.canFeedback) return;
    if (field === 'fedOwner' && !this.data.canManage) return;
    try {
      await api.receptionToggleFollow(e.currentTarget.dataset.id, e.currentTarget.dataset.field);
      this.loadAll();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  addEvidence(e) {
    var id = e.currentTarget.dataset.id;
    var that = this;
    wx.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: async function (res) {
        var path = res.tempFilePaths[0];
        var name = 'IMG_' + new Date().getTime() + '.jpg';
        try {
          await api.receptionAddEvidence(id, name, '照片');
          wx.showToast({ title: '已上传', icon: 'success' });
          that.loadAll();
        } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
      }
    });
  },

  async removeEvidence(e) {
    var rid = e.currentTarget.dataset.rid;
    var evId = e.currentTarget.dataset.evId;
    try {
      await api.receptionRemoveEvidence(rid, evId);
      this.loadAll();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async removeRecord(e) {
    wx.showModal({
      title: '确认删除',
      content: '确定删除该接待记录？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await api.receptionRemove(e.currentTarget.dataset.id);
            this.loadAll();
          } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
        }
      }
    });
  },

  todayStr() {
    const d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0');
  }
});
