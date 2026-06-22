const app = getApp();
const api = require('../../utils/api');
const mock = require('../../utils/mock');
const perm = require('../../utils/perm');
const meetingParser = require('../../utils/meeting-parser');

Page({
  data: {
    meetings: [],
    pending: [],
    others: [],
    stats: {},
    currentStage: 'preparing',
    isChair: false,
    canCreate: false,
    createVisible: false,
    createForm: {
      title: '',
      meetingDate: '',
      meetingTime: '09:00',
      location: '',
      type: 'regular',
      needsVote: true,
      totalOwners: 120,
      totalArea: 18500,
      description: '',
      needNotice: true,
      needBallot: true,
      topics: []
    },
    locationOptions: ['小区中心广场', '社区活动室', '物业会议室', '小区篮球场', '线上会议'],
    totalOwners: 0,
    totalArea: 0,
    materialPrefillOpen: false,
    materialText: '',
    materialFiles: [],
    materialScanResult: null,
    keyword: ''
  },

  onShow() {
    var role = app.globalData && app.globalData.activeRole ? app.globalData.activeRole.role : '';
    var isChair = role === '主任' || role === '副主任';
    // 从房屋花名册取总户数/总面积
    var H = require('../../utils/api/helpers');
    this.setData({
      isChair: isChair,
      canCreate: isChair,
      totalOwners: H.ownerTotal(null),
      totalArea: H.ownerArea(null)
    });
    this.loadAll();
  },

  async loadAll() {
    try {
      const [list, stats] = await Promise.all([
        api.omList(this.data.isChair ? this.data.currentStage : null),
        api.omStats()
      ]);
      // 关键词过滤
      var kw = this.data.keyword || '';
      if (kw) {
        list = list.filter(function (m) { return (m.title || '').indexOf(kw) >= 0; });
      }

      if (this.data.isChair) {
        this.setData({ meetings: list, stats });
      } else {
        var pending = [], others = [];
        list.forEach(function (m) {
          var prog = getOwnerProgress(m);
          m.progress = prog.pct;
          m.progressLabel = prog.label;
          if (prog.pct < 100) pending.push(m);
          else others.push(m);
        });
        this.setData({ meetings: list, pending: pending, others: others, stats: stats });
      }
    } catch (e) {
      if (this.data.isChair) {
        var stage = this.data.currentStage;
        var filtered = mock.ownerMeetings.filter(function (m) { return m.stage === stage; });
        this.setData({ meetings: filtered, stats: mock.ownerStats });
      } else {
        var list2 = [].concat(mock.ownerMeetings);
        var pending2 = [], others2 = [];
        list2.forEach(function (m) {
          var prog = getOwnerProgress(m);
          m.progress = prog.pct;
          m.progressLabel = prog.label;
          if (prog.pct < 100) pending2.push(m);
          else others2.push(m);
        });
        this.setData({ meetings: list2, pending: pending2, others: others2, stats: mock.ownerStats });
      }
    }
  },

  onSearch(e) { this.setData({ keyword: e.detail.value }); this.loadAll(); },
  clearSearch() { this.setData({ keyword: '' }); this.loadAll(); },

  switchTab(e) {
    this.setData({ currentStage: e.currentTarget.dataset.stage });
    this.loadAll();
  },

  openDetail(e) {
    wx.navigateTo({ url: '/pages/owners-detail/owners-detail?id=' + e.currentTarget.dataset.id });
  },

  openCreate() {
    this.setData({
      createVisible: true,
      materialPrefillOpen: false,
      materialText: '',
      materialFiles: [],
      materialScanResult: null,
      createForm: {
        title: '',
        meetingDate: this.todayStr(),
        meetingTime: '09:00',
        location: '小区中心广场',
        type: 'regular',
        needsVote: true,
        totalOwners: 120,
        totalArea: 18500,
        description: '',
        needNotice: true,
        needBallot: true,
        topics: []
      }
    });
  },

  closeCreate() {
    this.setData({ createVisible: false });
  },

  noop() {},

  toggleMaterialPrefill() {
    this.setData({ materialPrefillOpen: !this.data.materialPrefillOpen });
  },

  onMaterialTextInput(e) {
    this.setData({ materialText: e.detail.value, materialScanResult: null });
  },

  chooseMaterialFile() {
    if (!wx.chooseMessageFile) {
      wx.showToast({ title: '当前环境暂不支持选择文件', icon: 'none' });
      return;
    }
    wx.chooseMessageFile({
      count: 3,
      type: 'file',
      success: (res) => {
        const files = (res.tempFiles || []).map((file, index) => ({
          name: file.name || ('会议资料' + (index + 1)),
          sizeText: this.formatSize(file.size)
        }));
        this.setData({ materialFiles: files, materialScanResult: null });
      }
    });
  },

  removeMaterialFile(e) {
    const idx = e.currentTarget.dataset.index;
    const files = this.data.materialFiles.filter((_, i) => i !== idx);
    this.setData({ materialFiles: files, materialScanResult: null });
  },

  clearMaterialPrefill() {
    this.setData({ materialText: '', materialFiles: [], materialScanResult: null });
  },

  scanMaterialPrefill() {
    const fileText = this.data.materialFiles.map(file => file.name).join('\n');
    const sourceText = [this.data.materialText, fileText].filter(Boolean).join('\n');
    if (!sourceText.trim()) {
      wx.showToast({ title: '请先粘贴资料正文或上传文件', icon: 'none' });
      return;
    }

    const result = meetingParser.parseMeetingText(sourceText, { kind: 'owners' });
    const updates = { materialScanResult: result };
    Object.keys(result.fields).forEach((key) => {
      if (Object.prototype.hasOwnProperty.call(this.data.createForm, key)) {
        updates['createForm.' + key] = result.fields[key];
      }
    });
    this.setData(updates);
    wx.showToast({ title: result.matched.length ? '已预填，请确认' : '未识别到字段', icon: 'none' });
  },

  pickType(e) {
    this.setData({ 'createForm.type': e.currentTarget.dataset.type });
  },

  toggleNeedsVote() {
    this.setData({ 'createForm.needsVote': !this.data.createForm.needsVote });
  },

  onDateChange(e) {
    this.setData({ 'createForm.meetingDate': e.detail.value });
  },

  onTimeChange(e) {
    this.setData({ 'createForm.meetingTime': e.detail.value });
  },

  pickLocation(e) {
    this.setData({ 'createForm.location': e.currentTarget.dataset.location });
  },

  onCreateSwitch(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['createForm.' + field]: e.detail.value });
  },

  onCreateInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['createForm.' + field]: e.detail.value });
  },

  formatSize(size) {
    const n = Number(size) || 0;
    if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + 'MB';
    if (n >= 1024) return Math.round(n / 1024) + 'KB';
    return n + 'B';
  },

  // ── 创建表单：议题管理 ──

  addCreateTopic() {
    var topics = this.data.createForm.topics.concat([{
      title: '', decisionType: 'simple', type: 'ordinary', options: []
    }]);
    this.setData({ 'createForm.topics': topics });
  },

  removeCreateTopic(e) {
    var idx = e.currentTarget.dataset.index;
    var topics = this.data.createForm.topics.filter(function (_, i) { return i !== idx; });
    this.setData({ 'createForm.topics': topics });
  },

  onCreateTopicInput(e) {
    var idx = e.currentTarget.dataset.index;
    var field = e.currentTarget.dataset.field;
    this.setData({ ['createForm.topics[' + idx + '].' + field]: e.detail.value });
  },

  pickCreateTopicType(e) {
    var idx = e.currentTarget.dataset.index;
    this.setData({
      ['createForm.topics[' + idx + '].decisionType']: e.currentTarget.dataset.type,
      ['createForm.topics[' + idx + '].options']: e.currentTarget.dataset.type === 'multi_choice' ? [{ id: 1, label: '' }] : []
    });
  },

  addCreateTopicOption(e) {
    var idx = e.currentTarget.dataset.index;
    var options = this.data.createForm.topics[idx].options || [];
    var newId = options.length ? Math.max.apply(null, options.map(function (o) { return o.id; })) + 1 : 1;
    options = options.concat([{ id: newId, label: '' }]);
    this.setData({ ['createForm.topics[' + idx + '].options']: options });
  },

  removeCreateTopicOption(e) {
    var idx = e.currentTarget.dataset.index;
    var optIdx = e.currentTarget.dataset.optIndex;
    var options = this.data.createForm.topics[idx].options.filter(function (_, i) { return i !== optIdx; });
    this.setData({ ['createForm.topics[' + idx + '].options']: options });
  },

  onTopicOptionInput(e) {
    var idx = e.currentTarget.dataset.index;
    var optIdx = e.currentTarget.dataset.optIndex;
    this.setData({ ['createForm.topics[' + idx + '].options[' + optIdx + '].label']: e.detail.value });
  },

  async submitCreate() {
    var form = this.data.createForm;
    if (!form.title || !form.meetingDate || !form.meetingTime) {
      wx.showToast({ title: '请补全标题、日期和时间', icon: 'none' });
      return;
    }
    // 过滤空议题
    var topics = (form.topics || []).filter(function (t) { return t.title.trim(); });
    topics = topics.map(function (t) {
      if (t.decisionType === 'multi_choice') {
        t.options = (t.options || []).filter(function (o) { return o.label.trim(); });
      } else { delete t.options; }
      return t;
    });
    // 仅 needsVote 时传 topics
    var data = Object.assign({}, form, {
      totalOwners: Number(form.totalOwners) || this.data.totalOwners || 10,
      totalArea: Number(form.totalArea) || this.data.totalArea || 940,
      topics: form.needsVote ? topics : []
    });
    delete data.needNotice;
    delete data.needBallot;
    delete data.locationOptions;
    try {
      await api.omCreate(data);
      wx.showToast({ title: '已创建，通知草稿已生成', icon: 'none' });
      this.setData({ createVisible: false, currentStage: 'preparing' });
      this.loadAll();
    } catch (e) {
      wx.showToast({ title: e.message, icon: 'none' });
    }
  },

  todayStr() {
    const d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }
});

// 计算业主大会个人进度
function getOwnerProgress(m) {
  if (m.stage === 'preparing') return { pct: 0, label: '⏳ 待开始' };
  if (m.stage === 'ended') {
    if (m.compliance === 'invalid') return { pct: 100, label: '✕ 大会无效' };
    return { pct: 100, label: '📄 已归档' };
  }
  // ongoing
  return { pct: 50, label: '🔶 进行中' };
}
