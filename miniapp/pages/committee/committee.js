const app = getApp();
const api = require('../../utils/api');
const mock = require('../../utils/mock');
const perm = require('../../utils/perm');
const meetingParser = require('../../utils/meeting-parser');

Page({
  data: {
    isChair: false,
    isExternal: false,
    currentStage: 'preparing',
    meetings: [],
    stats: {},
    publishScore: { ontime: 0, overdue: 0, pending: 0 },
    counts: { preparing: 0, ongoing: 0, ended: 0 },
    roleView: { title: '', intro: '' },
    createVisible: false,
    createForm: {
      title: '',
      meetingDate: '',
      meetingTime: '09:00',
      location: '',
      description: '',
      topics: []
    },
    materialPrefillOpen: false,
    materialText: '',
    materialFiles: [],
    materialScanResult: null,
    locationOptions: ['社区活动室', '物业办公室', '社区会议室', '线上会议', '待定'],
    keyword: ''
  },

  onShow() {
    const role = app.globalData.activeRole;
    if (!role) { wx.redirectTo({ url: '/pages/login/login' }); return; }
    this.setData({ isChair: perm.isChair(), isRecorder: perm.isRecorder(), isExternal: perm.isExternal() });
    this.setupRoleView();
    this.loadAll();
  },

  setupRoleView() {
    const app = getApp();
    if (perm.isChair()) {
      this.setData({ roleView: { title: '主任/副主任工作台', intro: '管理会议全流程：新建、推进阶段、核查合规性、公示归档。' } });
    } else if (app.isRecorder()) {
      this.setData({ roleView: { title: '委员工作台', intro: '按流程缺口优先展示，补齐送达、代录确认参会和佐证。' } });
    } else if (app.isExternal()) {
      this.setData({ roleView: { title: '公开信息', intro: '内部会议流程仅业委会成员可见，可查看已公示的会议纪要与决定。' } });
    } else {
      this.setData({ roleView: { title: '我的会议工作台', intro: '按你的待办与关注事项优先展示；会议新建、开始、结束由主任/副主任操作。' } });
    }
  },

  async loadAll() {
    try {
      const [list, stats, score] = await Promise.all([
        api.committeeList(this.data.isChair ? this.data.currentStage : null),
        api.committeeStats(),
        api.committeePublishScore().catch(() => null)
      ]);
      const now = new Date();
      const month = now.getMonth() + 1;
      const bimonth = Math.ceil(month / 2);
      const periodStart = (bimonth - 1) * 2 + 1;
      const periodEnd = bimonth * 2;
      stats.periodLabel = periodStart + '-' + periodEnd + '月';
      stats.annualYear = now.getFullYear();

      // 非主席：分组为"待办"和"其他"
      let pending = [], others = [];
      if (!this.data.isChair) {
        pending = list.filter(m => m.progress >= 0 && m.progress < 100);
        others = list.filter(m => m.progress >= 100 || m.progress < 0);
      }

      // 关键词过滤
      var kw = this.data.keyword || '';
      if (kw) {
        list = list.filter(function (m) { return (m.title || '').indexOf(kw) >= 0; });
        pending = pending.filter(function (m) { return (m.title || '').indexOf(kw) >= 0; });
        others = others.filter(function (m) { return (m.title || '').indexOf(kw) >= 0; });
      }

      this.setData({
        meetings: list, pending, others,
        stats,
        publishScore: score || { ontime: 0, overdue: 0, pending: 0 },
        counts: { preparing: stats.preparing || 0, ongoing: stats.ongoing || 0, ended: stats.ended || 0 }
      });
    } catch (e) {
      const stage = this.data.currentStage;
      const all = this.data.isChair ? mock.committeeMeetings.filter(m => m.stage === stage || !stage) : mock.committeeMeetings;
      let pending = [], others = [];
      if (!this.data.isChair) {
        const enriched = all.map(m => {
          const prog = m.summaryLine && m.summaryLine.includes('待') ? 50 : (m.summaryLine && m.summaryLine.includes('已完成') ? 100 : -1);
          const progressLabel = prog >= 100 ? '已完成' : (prog >= 0 ? '待处理' : '仅查看');
          return { ...m, progress: prog, progressLabel };
        });
        pending = enriched.filter(m => m.progress >= 0 && m.progress < 100);
        others = enriched.filter(m => m.progress >= 100 || m.progress < 0);
      }
      this.setData({
        meetings: all, pending, others,
        stats: mock.committeeStats,
        publishScore: mock.publishScore,
        counts: { preparing: mock.committeeStats.preparing, ongoing: mock.committeeStats.ongoing, ended: mock.committeeStats.ended }
      });
    }
  },

  onSearch(e) { this.setData({ keyword: e.detail.value }); this.loadAll(); },
  clearSearch() { this.setData({ keyword: '' }); this.loadAll(); },

  switchTab(e) {
    this.setData({ currentStage: e.currentTarget.dataset.stage, keyword: '' });
    this.loadAll();
  },

  openDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/committee-detail/committee-detail?id=' + id });
  },

  openMeetingTap(e) {
    if (e.currentTarget.dataset.invalid) {
      this.openMinutes(e);
      return;
    }
    this.openDetail(e);
  },

  openMinutes(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/minutes/minutes?meetingId=' + id + '&from=committee' });
  },

  openNewMeeting() {
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
        location: '社区活动室',
        description: '',
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

    const result = meetingParser.parseMeetingText(sourceText, { kind: 'committee' });
    const updates = { materialScanResult: result };
    Object.keys(result.fields).forEach((key) => {
      if (Object.prototype.hasOwnProperty.call(this.data.createForm, key)) {
        updates['createForm.' + key] = result.fields[key];
      }
    });
    this.setData(updates);
    wx.showToast({ title: result.matched.length ? '已预填，请确认' : '未识别到字段', icon: 'none' });
  },

  onCreateInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['createForm.' + field]: e.detail.value });
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

  formatSize(size) {
    const n = Number(size) || 0;
    if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + 'MB';
    if (n >= 1024) return Math.round(n / 1024) + 'KB';
    return n + 'B';
  },

  // ── 创建表单：议题管理 ──

  addCreateTopic() {
    var topics = this.data.createForm.topics.concat([{
      title: '',
      type: 'discussion',
      decisionType: 'none',
      options: []
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
    var value = e.detail.value;
    var path = 'createForm.topics[' + idx + '].' + field;
    this.setData({ [path]: value });
  },

  pickCreateAgendaType(e) {
    var idx = e.currentTarget.dataset.index;
    var type = e.currentTarget.dataset.type;
    this.setData({
      ['createForm.topics[' + idx + '].type']: type,
      ['createForm.topics[' + idx + '].decisionType']: type === 'decision' ? 'simple' : 'none',
      ['createForm.topics[' + idx + '].options']: []
    });
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
    var value = e.detail.value;
    this.setData({ ['createForm.topics[' + idx + '].options[' + optIdx + '].label']: value });
  },

  async submitNewMeeting() {
    var form = this.data.createForm;
    if (!form.title || !form.meetingDate || !form.meetingTime || !form.location) {
      wx.showToast({ title: '请补全标题、时间和地点', icon: 'none' });
      return;
    }
    // 过滤掉空标题的议题
    var topics = (form.topics || []).filter(function (t) { return t.title.trim(); });
    // 对 multi_choice 过滤空选项；非表决项不带表决方式
    topics = topics.map(function (t) {
      if (t.type !== 'decision') {
        t.decisionType = 'none';
        delete t.options;
      } else if (t.decisionType === 'multi_choice') {
        t.options = (t.options || []).filter(function (o) { return o.label.trim(); });
      } else {
        delete t.options;
      }
      return t;
    });
    if (!topics.length) {
      wx.showToast({ title: '请至少添加一个会议议题', icon: 'none' });
      return;
    }
    var invalidMulti = topics.find(function (t) {
      return t.type === 'decision' && t.decisionType === 'multi_choice' && (!t.options || t.options.length < 2);
    });
    if (invalidMulti) {
      wx.showToast({ title: '多选一议题至少需要两个选项', icon: 'none' });
      return;
    }
    try {
      const created = await api.committeeCreate({
        title: form.title,
        meetingDate: form.meetingDate,
        meetingTime: form.meetingTime,
        location: form.location,
        description: form.description,
        topics: topics
      });
      this.setData({ createVisible: false, currentStage: 'preparing' });
      if (created && created.id) {
        wx.navigateTo({ url: '/pages/committee-detail/committee-detail?id=' + created.id });
      } else {
        this.loadAll();
      }
    } catch (e) {
      wx.showToast({ title: e.message, icon: 'none' });
    }
  },

  todayStr() {
    const d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0');
  }
});
