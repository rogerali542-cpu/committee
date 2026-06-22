const api = require('../../utils/api');
const app = getApp();

Page({
  data: {
    detail: null,
    userView: '',
    isChair: false,
    noticeEditVisible: false,
    noticeEditForm: { title: '', content: '' },
    // 补录投票
    proxyVisible: false,
    proxyUnits: [],
    proxyUnitId: null,
    proxyTopics: [],
    proxyTopicId: null,
    proxyChoice: '',
    proxyVotedTopics: [],
    proxyProofUrl: '',
    proxyProofName: '',
    proxySubmitting: false,
    voteStatusList: [],
    // 纪要
    minutesVisible: false,
    minutesText: '',
    minutesEditing: false,
    minutesEditText: '',
    // 编辑大会
    editVisible: false,
    editForm: { title: '', meetingDate: '', meetingTime: '', location: '', description: '' },
    locationOptions: ['小区中心广场', '社区活动室', '物业会议室', '小区篮球场', '线上会议'],
    attendanceList: [],
    topicFormVisible: false,
    topicSubmitting: false,
    newTopicForm: { title: '', type: 'ordinary' },
    currentOwnerTopicIndex: 0
  },
  onLoad(options) {
    this.meetingId = parseInt(options.id);
    this.loadDetail();
  },
  onShow() { this.loadDetail(); },

  async loadDetail() {
    try {
      const detail = await api.omDetail(this.meetingId);
      if (!detail) return;

      // 角色视图
      const role = app.globalData && app.globalData.activeRole ? app.globalData.activeRole.role : '';
      var isChair = role === '主任' || role === '副主任';
      var userView = isChair ? 'chair' : 'owner';

      // Pre-compute display values
      detail.areaWan = (detail.totalArea / 10000).toFixed(2);
      if (detail.notifyInfo) {
        detail.notifyInfo.absDaysLeft = Math.abs(detail.notifyInfo.daysLeft || 0);
      }
      if (detail.ballotInfo) {
        detail.ballotInfo.absDaysLeft = Math.abs(detail.ballotInfo.daysLeft || 0);
        detail.ballotInfo.daysLeftText = detail.ballotInfo.daysLeft != null ? String(detail.ballotInfo.daysLeft) : '';
      }
      if (detail.recordInfo) {
        detail.recordInfo.presentAreaWan = (detail.recordInfo.presentArea / 10000).toFixed(1);
        detail.recordInfo.totalAreaWan = (detail.totalArea / 10000).toFixed(1);
        detail.recordInfo.pctOwners = detail.totalOwners ? Math.round(detail.recordInfo.presentOwners / detail.totalOwners * 100) : 0;
        detail.recordInfo.pctArea = detail.totalArea ? Math.round(detail.recordInfo.presentArea / detail.totalArea * 100) : 0;
        if (detail.recordInfo.topics) {
          detail.recordInfo.topics.forEach(t => {
            t.pctForOwners = detail.totalOwners ? Math.round(t.forOwners / detail.totalOwners * 100) : 0;
            t.pctAgOwners = detail.totalOwners ? Math.round((t.agOwners || 0) / detail.totalOwners * 100) : 0;
            t.pctAbOwners = detail.totalOwners ? Math.round((t.abOwners || 0) / detail.totalOwners * 100) : 0;
            t.pctForArea = detail.totalArea ? Math.round(t.forArea / detail.totalArea * 100) : 0;
            t.pctAgArea = detail.totalArea ? Math.round((t.agArea || 0) / detail.totalArea * 100) : 0;
            t.pctAbArea = detail.totalArea ? Math.round((t.abArea || 0) / detail.totalArea * 100) : 0;
            t.forAreaWan = (t.forArea / 10000).toFixed(1);
            t.agAreaWan = (t.agArea / 10000).toFixed(1);
            t.abAreaWan = (t.abArea / 10000).toFixed(1);
            // 当前用户（含身兼户代表的主任）本人对该议题的投票
            var mc = detail.myVotes && detail.myVotes[t.id];
            t.myChoice = mc || '';
            t.myChoiceLabel = mc === 'for' ? '同意' : mc === 'against' ? '不同意' : mc === 'abstain' ? '弃权' : '';
          });
        }
      }
      if (detail.recordInfo && detail.recordInfo.topics) {
        var topics = detail.recordInfo.topics || [];
        var doneCount = topics.filter(function (t) {
          return detail.myVotes && detail.myVotes[t.id];
        }).length;
        detail.ownerVoteTotal = topics.length;
        detail.ownerVoteDoneCount = doneCount;
        detail.ownerAllVoted = topics.length > 0 && doneCount >= topics.length;
      } else {
        detail.ownerVoteTotal = 0;
        detail.ownerVoteDoneCount = 0;
        detail.ownerAllVoted = !detail.needsVote;
      }
      if (detail.publishInfo && detail.publishInfo.daysLeft != null) {
        detail.publishInfo.daysLeftText = String(detail.publishInfo.daysLeft);
      }
      detail.canEditMinutes = isChair && detail.stage === 'ended' &&
        !(detail.publishInfo && detail.publishInfo.published);
      // 代表人房屋总面积
      if (detail.myUnits && detail.myUnits.length) {
        detail.myUnitsArea = detail.myUnits.reduce(function (s, u) { return s + (u.area || 0); }, 0).toFixed(0);
      }
      var currentOwnerTopicIndex = this.data.currentOwnerTopicIndex || 0;
      if (userView === 'owner' && detail.recordInfo && detail.recordInfo.topics && detail.recordInfo.topics.length) {
        var firstPending = detail.recordInfo.topics.findIndex(function (t) {
          return !(detail.myVotes && detail.myVotes[t.id]);
        });
        if (firstPending >= 0) {
          currentOwnerTopicIndex = firstPending;
        } else if (currentOwnerTopicIndex >= detail.recordInfo.topics.length) {
          currentOwnerTopicIndex = detail.recordInfo.topics.length - 1;
        }
      }
      // 快速会议模式（进行中时生效）：隐藏补录、添加议题等逐题表决相关入口
      const quickMode = detail.stage === 'ongoing' && detail.meetingMode === 'quick';
      this.setData({ detail, userView, isChair, currentOwnerTopicIndex, quickMode });
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  async toggleNotif(e) {
    try { await api.omToggleNotify(this.meetingId, e.currentTarget.dataset.field); this.loadDetail(); }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },
  async notifyAll() { try { await api.omNotifyAll(this.meetingId); this.loadDetail(); } catch(e){} },
  async ballotAll() { try { await api.omBallotAll(this.meetingId); this.loadDetail(); } catch(e){} },

  async toggleBallot(e) {
    try { await api.omToggleBallot(this.meetingId, e.currentTarget.dataset.field); this.loadDetail(); }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async adjustCount(e) {
    try {
      await api.omAdjustCount(this.meetingId, e.currentTarget.dataset.field, parseInt(e.currentTarget.dataset.delta));
      this.loadDetail();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async toggleSupervisor() {
    try { await api.omToggleSupervisor(this.meetingId); this.loadDetail(); }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async toggleProcess(e) {
    try { await api.omToggleProcess(this.meetingId, e.currentTarget.dataset.key); this.loadDetail(); }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  addTopic() {
    this.setData({
      topicFormVisible: true,
      topicSubmitting: false,
      newTopicForm: { title: '', type: 'ordinary' }
    });
  },

  closeTopicForm() {
    if (this.data.topicSubmitting) return;
    this.setData({ topicFormVisible: false });
  },

  onTopicInput(e) {
    this.setData({ 'newTopicForm.title': e.detail.value });
  },

  pickTopicType(e) {
    this.setData({ 'newTopicForm.type': e.currentTarget.dataset.type });
  },

  async submitTopic() {
    if (this.data.topicSubmitting) return;
    var form = this.data.newTopicForm || {};
    var title = (form.title || '').trim();
    if (!title) {
      wx.showToast({ title: '请输入议题名称', icon: 'none' });
      return;
    }
    this.setData({ topicSubmitting: true });
    try {
      await api.omAddTopic(this.meetingId, title, form.type || 'ordinary');
      wx.showToast({ title: '已添加议题', icon: 'success' });
      this.setData({ topicFormVisible: false, topicSubmitting: false });
      await this.loadDetail();
    } catch (e) {
      this.setData({ topicSubmitting: false });
      wx.showToast({ title: e.message || '添加失败', icon: 'none' });
    }
  },

  async adjustVote(e) {
    try {
      const { topicId, field, delta } = e.currentTarget.dataset;
      await api.omVoteAdj(this.meetingId, topicId, field, parseInt(delta));
      this.loadDetail();
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
          await api.omAddEvidence(that.meetingId, name, '照片');
          wx.showToast({ title: '已上传', icon: 'success' });
          that.loadDetail();
        } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
      }
    });
  },

  async removeEvidence(e) {
    try {
      await api.omRemoveEvidence(this.meetingId, e.currentTarget.dataset.evId);
      this.loadDetail();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  advance(e) {
    const action = e.currentTarget.dataset.action;
    // 开始大会：先选模式（普通 / 快速），再进入对应进行中页
    if (action === 'start') { this.startWithMode(); return; }
    this._doAdvance(action);
  },

  startWithMode() {
    wx.showActionSheet({
      itemList: ['普通模式（逐题表决）', '快速会议模式 · 录音生成（体验版）'],
      success: async (res) => {
        const mode = res.tapIndex === 1 ? 'quick' : 'normal';
        try {
          await api.omAdvance(this.meetingId, 'start', mode);
          wx.showToast({ title: '大会已开始', icon: 'success' });
          this.enterLive(mode);
        } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
      }
    });
  },

  async _doAdvance(action) {
    try {
      await api.omAdvance(this.meetingId, action);
      wx.showToast({ title: '已推进', icon: 'success' });
      this.loadDetail();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  // 进入「大会进行」全屏向导页（按模式分流；再次进入时从大会详情读回模式）
  enterLive(mode) {
    if (typeof mode !== 'string') mode = (this.data.detail && this.data.detail.meetingMode) || 'normal';
    if (mode === 'quick') {
      wx.navigateTo({ url: '/pages/meeting-live-quick/meeting-live-quick?type=owner&meetingId=' + this.meetingId });
    } else {
      wx.navigateTo({ url: '/pages/owner-meeting-live/owner-meeting-live?id=' + this.meetingId });
    }
  },

  async publishNow() {
    try { await api.omPublish(this.meetingId); wx.showToast({ title: '已公示', icon: 'success' }); this.loadDetail(); }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  withdrawPublish() {
    wx.showModal({
      title: '撤回公示',
      content: '撤回后业主将看到"已撤回"状态，且需重新公示。',
      editable: true,
      placeholderText: '请填写撤回原因（必填）',
      success: async (res) => {
        if (!res.confirm) return;
        var reason = (res.content || '').trim();
        if (!reason) { wx.showToast({ title: '撤回必须填写原因', icon: 'none' }); return; }
        try {
          await api.omWithdrawPublish(this.meetingId, reason);
          wx.showToast({ title: '已撤回公示', icon: 'success' });
          this.loadDetail();
        } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
      }
    });
  },

  async viewMinutesRevisions() {
    try {
      var list = await api.omMinutesRevisions(this.meetingId);
      if (!list || !list.length) { wx.showToast({ title: '暂无修订记录', icon: 'none' }); return; }
      var lines = list.map(function (r) {
        return 'v' + r.versionNo + ' · ' + (r.editorName || '—') + ' · ' + (r.createdAt || '');
      });
      wx.showModal({ title: '纪要修订历史', content: lines.join('\n'), showCancel: false, confirmText: '关闭' });
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  // ── 材料管理 ──

  previewMaterial(e) {
    var idx = e.currentTarget.dataset.index;
    var materials = this.data.detail && this.data.detail.materials;
    if (!materials || !materials[idx]) return;
    var m = materials[idx];
    wx.showModal({
      title: m.name,
      content: m.content || '（暂无预览内容）',
      showCancel: false,
      confirmText: '关闭'
    });
  },

  uploadMaterial() {
    var that = this;
    wx.chooseMessageFile({
      count: 3, type: 'file',
      success: function (res) {
        var files = (res.tempFiles || []).map(function (file) {
          return { name: file.name || ('材料_' + Date.now()), sizeText: that.formatSize(file.size) };
        });
        Promise.all(files.map(function (file) {
          return api.omAddMaterial(that.meetingId, file.name, file.sizeText);
        })).then(function () {
          wx.showToast({ title: '已上传 ' + files.length + ' 份材料', icon: 'success' });
          that.loadDetail();
        }).catch(function (err) {
          wx.showToast({ title: err.message || '上传失败', icon: 'none' });
        });
      }
    });
  },

  removeMaterial(e) {
    var idx = e.currentTarget.dataset.index;
    var materials = this.data.detail && this.data.detail.materials;
    if (!materials || !materials[idx]) return;
    var item = materials[idx];
    var that = this;
    wx.showModal({
      title: '删除材料',
      content: '确认删除「' + item.name + '」？',
      success: function (res) {
        if (res.confirm) {
          api.omRemoveMaterial(that.meetingId, idx).then(function () { that.loadDetail(); })
            .catch(function (err) { wx.showToast({ title: err.message, icon: 'none' }); });
        }
      }
    });
  },

  formatSize(size) {
    var n = Number(size) || 0;
    if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + 'MB';
    if (n >= 1024) return Math.round(n / 1024) + 'KB';
    return n + 'B';
  },

  // ── 编辑通知草稿 ──

  editNoticeDraft() {
    var draft = this.data.detail && this.data.detail.noticeDraft;
    this.setData({
      noticeEditVisible: true,
      noticeEditForm: {
        title: draft ? draft.title : '',
        content: draft ? draft.content : ''
      }
    });
  },

  closeNoticeEdit() {
    this.setData({ noticeEditVisible: false });
  },

  onNoticeEditInput(e) {
    var field = e.currentTarget.dataset.field;
    this.setData({ ['noticeEditForm.' + field]: e.detail.value });
  },

  async submitNoticeEdit() {
    var form = this.data.noticeEditForm;
    if (!form.title || !form.content) {
      wx.showToast({ title: '标题和正文不能为空', icon: 'none' });
      return;
    }
    try {
      await api.omUpdateNotice(this.meetingId, form.title, form.content);
      wx.showToast({ title: '通知已更新', icon: 'success' });
      this.setData({ noticeEditVisible: false });
      this.loadDetail();
    } catch (e) {
      wx.showToast({ title: e.message || '保存失败', icon: 'none' });
    }
  },

  // ── 补录投票 ──

  async openProxyVote() {
    try {
      var statusList = await api.omVoteStatus(this.meetingId);
      this.loadDetail(); // 同时刷新详情
      var topics = this.data.detail && this.data.detail.recordInfo ? (this.data.detail.recordInfo.topics || []) : [];
      this.setData({
        proxyVisible: true,
        proxyUnits: (statusList || []).map(function (u) { return Object.assign({}, u, { disabled: u.allVoted }); }),
        proxyUnitId: null,
        proxyTopics: topics,
        proxyTopicId: null,
        proxyChoice: '',
        proxyVotedTopics: [],
        proxyProofUrl: '',
        proxyProofName: '',
        proxySubmitting: false,
        voteStatusList: statusList || []
      });
    } catch (e) {
      wx.showToast({ title: '加载投票状态失败', icon: 'none' });
    }
  },

  closeProxyVote() {
    this.setData({ proxyVisible: false });
  },

  pickProxyUnit(e) {
    var unitId = e.currentTarget.dataset.unitId;
    var unit = this.data.proxyUnits.find(function (u) { return u.unitId === unitId; });
    var votedTopics = unit ? (unit.votedTopics || []) : [];
    this.setData({
      proxyUnitId: unitId,
      proxyTopicId: null,
      proxyChoice: '',
      proxyVotedTopics: votedTopics
    });
  },

  pickProxyTopic(e) {
    var topicId = e.currentTarget.dataset.topicId;
    this.setData({ proxyTopicId: topicId, proxyChoice: '' });
  },

  pickProxyChoice(e) {
    this.setData({ proxyChoice: e.currentTarget.dataset.choice });
  },

  pickProxyProof() {
    var that = this;
    wx.chooseImage({
      count: 1, sizeType: ['compressed'], sourceType: ['album', 'camera'],
      success: function (res) {
        var url = res.tempFilePaths && res.tempFilePaths[0] ? res.tempFilePaths[0] : '';
        var parts = url.split(/[\\/]/);
        that.setData({
          proxyProofUrl: url,
          proxyProofName: parts[parts.length - 1] || ('proxy_' + Date.now() + '.jpg')
        });
      }
    });
  },

  async submitProxyVote() {
    if (this.data.proxySubmitting) return;
    if (!this.data.proxyProofUrl) {
      wx.showToast({ title: '请上传代录凭证', icon: 'none' });
      return;
    }
    this.setData({ proxySubmitting: true });
    try {
      await api.omProxyVote(this.meetingId, {
        unitId: this.data.proxyUnitId,
        topicId: this.data.proxyTopicId,
        choice: this.data.proxyChoice,
        proofUrl: this.data.proxyProofUrl,
        proofName: this.data.proxyProofName
      });
      wx.showToast({ title: '补录成功', icon: 'success' });
      this.setData({ proxyVisible: false, proxySubmitting: false });
      this.loadDetail();
    } catch (e) {
      this.setData({ proxySubmitting: false });
      wx.showToast({ title: (e && e.message) || '补录失败', icon: 'none' });
    }
  },

  // ── 到场登记 ──

  async loadAttendance() {
    try {
      var list = await api.omAttendanceList(this.meetingId);
      this.setData({ attendanceList: list || [] });
      this.loadDetail();
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  async toggleAttendance(e) {
    var unitId = e.currentTarget.dataset.unitId;
    try {
      await api.omToggleAttendance(this.meetingId, unitId);
      this.loadAttendance();
    } catch (err) {
      wx.showToast({ title: '操作失败', icon: 'none' });
    }
  },

  // ── 线上投票 ──

  async submitVote(e) {
    var topicId = e.currentTarget.dataset.topicId;
    var choice = e.currentTarget.dataset.choice;
    var label = choice === 'for' ? '同意' : choice === 'against' ? '不同意' : '弃权';
    var topics = this.data.detail && this.data.detail.recordInfo ? (this.data.detail.recordInfo.topics || []) : [];
    var topic = topics.find(function (t) { return String(t.id) === String(topicId); });
    wx.showModal({
      title: '确认你的选择',
      content: '事项：' + (topic ? topic.title : '') + '\n你的选择：' + label + '\n提交后不能修改。',
      confirmText: '确认提交',
      cancelText: '再看看',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          var result = await api.omVote(this.meetingId, topicId, choice);
          if (result && result.error) { wx.showToast({ title: result.error, icon: 'none' }); return; }
          wx.showToast({ title: '投票成功', icon: 'success' });
          await this.loadDetail();
        } catch (err) {
          wx.showToast({ title: err.message || '投票失败', icon: 'none' });
        }
      }
    });
  },

  openOwnerMinutes() {
    wx.navigateTo({ url: '/pages/minutes/minutes?meetingId=' + this.meetingId + '&from=owner-detail' });
  },

  // ── 纪要 ──

  async viewOwnerMinutes() {
    this.setData({ minutesVisible: true, minutesText: '加载中...', minutesEditing: false });
    try {
      var text = await api.omMinutes(this.meetingId);
      this.setData({ minutesText: text || '' });
    } catch (e) {
      this.setData({ minutesText: '加载失败' });
    }
  },

  closeMinutes() {
    this.setData({ minutesVisible: false, minutesEditing: false });
  },

  editMinutes() {
    var detail = this.data.detail || {};
    if (!detail.canEditMinutes) {
      wx.showToast({ title: '已公示纪要需撤回或修订', icon: 'none' });
      return;
    }
    this.setData({ minutesEditing: true, minutesEditText: this.data.minutesText });
  },

  cancelEditMinutes() {
    this.setData({ minutesEditing: false });
  },

  onMinutesInput(e) {
    this.setData({ minutesEditText: e.detail.value });
  },

  async confirmMinutes() {
    try {
      await api.omUpdateMinutes(this.meetingId, this.data.minutesEditText);
      wx.showToast({ title: '纪要已保存', icon: 'success' });
      this.setData({ minutesVisible: false, minutesEditing: false });
    } catch (e) {
      wx.showToast({ title: e.message || '保存失败', icon: 'none' });
    }
  },

  // ── 补充归档 ──

  addArchiveExtra() {
    var that = this;
    wx.chooseImage({
      count: 1, sizeType: ['compressed'], sourceType: ['album', 'camera'],
      success: function (res) {
        var name = '归档材料_' + Date.now() + '.jpg';
        var detail = that.data.detail;
        if (!detail.archiveExtras) detail.archiveExtras = [];
        detail.archiveExtras.push({ id: (detail.archiveExtras.length + 1), fileName: name });
        that.setData({ 'detail.archiveExtras': detail.archiveExtras });
        wx.showToast({ title: '已添加', icon: 'success' });
      }
    });
  },

  removeArchiveExtra(e) {
    var aeId = e.currentTarget.dataset.aeId;
    var detail = this.data.detail;
    detail.archiveExtras = (detail.archiveExtras || []).filter(function (ae) { return ae.id !== aeId; });
    this.setData({ 'detail.archiveExtras': detail.archiveExtras });
  },

  // ── 归档/公示 ──

  archiveDirect() {
    var detail = this.data.detail;
    detail._archived = true;
    detail.publishInfo = detail.publishInfo || {};
    detail.publishInfo.published = false;
    this.setData({ detail: detail });
    wx.showToast({ title: '已归档', icon: 'success' });
  },

  async markCompliance(e) {
    var status = e.currentTarget.dataset.status;
    try { await api.omToggleCompliance(this.meetingId, status); this.loadDetail(); }
    catch (err) { wx.showToast({ title: err.message, icon: 'none' }); }
  },

  previewArchiveMaterials() {
    var materials = this.data.detail && this.data.detail.materials;
    if (!materials || !materials.length) return;
    var list = materials.map(function (m, i) { return (i + 1) + '. ' + m.name; }).join('\n');
    wx.showModal({
      title: '会议材料（' + materials.length + '份）',
      content: list,
      showCancel: false,
      confirmText: '关闭'
    });
  },

  // ── 编辑大会 ──

  openEdit() {
    var d = this.data.detail;
    this.setData({
      editVisible: true,
      editForm: {
        title: d.title || '',
        meetingDate: d.meetingDate || '',
        meetingTime: d.meetingTime || '09:00',
        location: d.location || '',
        description: d.description || ''
      }
    });
  },

  closeEdit() { this.setData({ editVisible: false }); },

  onEditInput(e) {
    this.setData({ ['editForm.' + e.currentTarget.dataset.field]: e.detail.value });
  },

  onEditDateChange(e) { this.setData({ 'editForm.meetingDate': e.detail.value }); },
  onEditTimeChange(e) { this.setData({ 'editForm.meetingTime': e.detail.value }); },

  pickEditLocation(e) { this.setData({ 'editForm.location': e.currentTarget.dataset.location }); },

  async submitEdit() {
    var form = this.data.editForm;
    if (!form.title || !form.meetingDate || !form.meetingTime || !form.location) {
      wx.showToast({ title: '请补全标题、时间和地点', icon: 'none' });
      return;
    }
    try {
      await api.omUpdate(this.meetingId, form);
      wx.showToast({ title: '已保存', icon: 'success' });
      this.setData({ editVisible: false });
      this.loadDetail();
    } catch (e) {
      wx.showToast({ title: e.message || '保存失败', icon: 'none' });
    }
  },

  showWip() { wx.showToast({ title: '功能开发中', icon: 'none' }); },
  async removeMeeting() {
    wx.showModal({
      title: '确认取消', content: '确定取消该大会？',
      success: async (res) => {
        if (res.confirm) { try { await api.omRemove(this.meetingId); wx.navigateBack(); } catch(e){} }
      }
    });
  }
});
