const app = getApp();
const api = require('../../utils/api');
const perm = require('../../utils/perm');

Page({
  data: {
    detail: null,
    userView: '',
    activeRole: {},
    currentStep: 1,
    step1Done: false, step2Done: false, step3Done: false,
    step1Time: '', step2Time: '', step3Time: '',
    stepDone: 0, stepTotal: 3, stepAllDone: false,
    // 添加议题表单
    addTopicVisible: false,
    newTopicForm: {
      title: '',
      decisionType: 'simple',
      type: 'decision',
      optionsText: ''
    },
    proxyVisible: false,
    proxyAction: 'signIn',
    proxyTargets: [],
    proxyAllTargets: [],
    proxyKeyword: '',
    proxyProofUrl: '',
    proxyProofName: '',
    proxyTopicId: null,
    proxyChoice: 'for_vote',
    proxySelectedId: null,
    proxySelectedCount: 0,
    proxySubmitting: false
  },

  onLoad(options) {
    this.meetingId = parseInt(options.id);
    this.setData({ activeRole: app.globalData.activeRole || {} });
    this.loadDetail();
  },

  onShow() { this.loadDetail(); },

  noop() {},

  async loadDetail() {
    try {
      const detail = await api.committeeDetail(this.meetingId);
      const userView = detail.userView;
      if (detail.taskItems) detail.taskItemsText = detail.taskItems.join(' / ');

      // Calculate step states for member & chair view
      let step1Done = false, step2Done = false, step3Done = false;
      if ((userView === 'member' || userView === 'chair') && detail.record && detail.record.attendances) {
        const me = detail.record.attendances.find(a => a.isSelf);
        if (me) {
          step1Done = me.signedIn;
          step2Done = me.signed;
        }
        // Step 3: all topics voted
        if (detail.record.topics) {
          step3Done = detail.record.topics.length > 0 &&
            detail.record.topics.every(t => t.myVote);
        } else {
          step3Done = true; // no topics = done
        }
      }
      // 如果后端说已完成（taskLevel === 'ok'），直接全锁
      const forceDone = detail.taskLevel === 'ok' && detail.stage === 'ongoing';
      if (forceDone) { step1Done = true; step2Done = true; step3Done = true; }
      const stepDone = [step1Done, step2Done, step3Done].filter(Boolean).length;
      const stepAllDone = forceDone || (stepDone === 3);
      // 自动设置当前步骤
      const currentStep = stepAllDone ? 3 : step1Done ? (step2Done ? 3 : 2) : 1;

      // Pre-compute topics passed count
      if (detail.record && detail.record.topics) {
        detail.record.topicsPassed = detail.record.topics.filter(function (t) { return t.passed; }).length;
      }

      // Pre-compute chair view stats
      if (detail.record && detail.record.attendances) {
        detail.record.signedInCount = detail.record.attendances.filter(a => a.signedIn).length;
        detail.record.signedCount = detail.record.attendances.filter(a => a.signed).length;
        detail.record.signedInPct = Math.round(detail.record.signedInCount / detail.record.attendances.length * 100);
        detail.record.signedPct = Math.round(detail.record.signedCount / detail.record.attendances.length * 100);
      }

      this.setData({
        detail, userView, currentStep,
        step1Done, step2Done, step3Done,
        stepDone, stepTotal: detail.record && detail.record.topics && detail.record.topics.length > 0 ? 3 : 2,
        stepAllDone
      });
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  goStep(e) {
    const step = parseInt(e.currentTarget.dataset.step);
    // 只能跳转到已解锁的步骤
    if (step === 1 || (step === 2 && this.data.step1Done) || (step === 3 && this.data.step2Done)) {
      this.setData({ currentStep: step });
    }
  },

  confirmStep1() {
    const now = new Date();
    const time = now.getHours()+':'+String(now.getMinutes()).padStart(2,'0');
    this.setData({
      step1Done: true, step1Time: time,
      currentStep: 2, stepDone: Math.max(this.data.stepDone, 1)
    });
    api.committeeSelfToggle(this.meetingId, 'signedIn').catch(()=>{});
  },

  confirmStep2() {
    const now = new Date();
    const time = now.getHours()+':'+String(now.getMinutes()).padStart(2,'0');
    this.setData({
      step2Done: true, step2Time: time,
      currentStep: 3, stepDone: Math.max(this.data.stepDone, 2)
    });
    api.committeeSelfToggle(this.meetingId, 'signed').catch(()=>{});
  },

  confirmStep3() {
    const now = new Date();
    const time = now.getHours()+':'+String(now.getMinutes()).padStart(2,'0');
    this.setData({
      step3Done: true, step3Time: time,
      stepDone: 3, stepAllDone: true
    });
  },

  async vote(e) {
    const dataset = e.currentTarget.dataset;
    const topicId = dataset.topicId;
    const choice = dataset.choice;
    const selectedId = dataset.selectedId;
    const detail = this.data.detail;
    if (detail && detail.record && detail.record.topics) {
      const topics = detail.record.topics.map(t => {
        if (t.id == topicId) {
          t.myVote = choice || selectedId;
          if (selectedId) {
            var opt = (t.options || []).find(function (o) { return String(o.id) === String(selectedId); });
            if (opt) t.myVoteLabel = { id: opt.id, label: opt.label };
          }
        }
        return t;
      });
      const allVoted = topics.every(t => t.myVote);
      this.setData({ 'detail.record.topics': topics });
      if (allVoted) this.setData({ step3Ready: true });
    }
    try { await api.committeeVote(this.meetingId, topicId, choice, selectedId); } catch (e) {}
  },

  async openProxyRecorder() {
    const detail = this.data.detail;
    if (!detail || detail.stage !== 'ongoing') {
      wx.showToast({ title: '会议进行中才可代录', icon: 'none' });
      return;
    }
    this.setData({
      proxyVisible: true,
      proxyAction: 'signIn',
      proxyKeyword: '',
      proxyProofUrl: '',
      proxyProofName: '',
      proxyTopicId: detail.record && detail.record.topics && detail.record.topics[0] ? detail.record.topics[0].id : null,
      proxyChoice: 'for_vote',
      proxySelectedId: null,
      proxySelectedCount: 0
    });
    await this.loadProxyTargets();
  },

  closeProxyRecorder() {
    this.setData({ proxyVisible: false });
  },

  async loadProxyTargets() {
    try {
      const targets = await api.committeeProxyTargets(this.meetingId, this.data.proxyKeyword);
      this.setData({ proxyAllTargets: targets || [] });
      this.refreshProxyTargets();
    } catch (e) {
      wx.showToast({ title: e.message || '代录名单加载失败', icon: 'none' });
    }
  },

  refreshProxyTargets() {
    const action = this.data.proxyAction;
    const topicId = this.data.proxyTopicId;
    const keyword = (this.data.proxyKeyword || '').trim().toLowerCase();
    let selectedCount = 0;
    const targets = (this.data.proxyAllTargets || [])
      .filter(function (item) {
        if (!keyword) return true;
        const name = (item.name || '').toLowerCase();
        const room = (item.roomNumber || '').toLowerCase();
        return name.indexOf(keyword) >= 0 || room.indexOf(keyword) >= 0;
      })
      .map(function (item) {
        const votedTopicIds = item.votedTopicIds || [];
        let disabled = false;
        let disabledText = '';
        if (action === 'signIn') {
          disabled = !!item.signedIn;
          disabledText = item.signedIn ? (item.signInByProxy ? '已代录签到' : '已签到') : '';
        } else {
          if (!topicId) {
            disabled = true;
            disabledText = '请选择议题';
          } else if (!item.signedIn) {
            disabled = true;
            disabledText = '未签到';
          } else if (votedTopicIds.indexOf(Number(topicId)) >= 0) {
            disabled = true;
            disabledText = '已投票';
          }
        }
        const checked = disabled ? false : !!item.checked;
        if (checked) selectedCount += 1;
        return Object.assign({}, item, { disabled: disabled, disabledText: disabledText, checked: checked });
      });
    this.setData({ proxyTargets: targets, proxySelectedCount: selectedCount });
  },

  onProxyKeywordInput(e) {
    this.setData({ proxyKeyword: e.detail.value || '' });
    this.refreshProxyTargets();
  },

  pickProxyAction(e) {
    this.setData({
      proxyAction: e.currentTarget.dataset.action,
      proxyChoice: 'for_vote',
      proxySelectedId: null
    });
    this.refreshProxyTargets();
  },

  pickProxyTopic(e) {
    this.setData({
      proxyTopicId: Number(e.currentTarget.dataset.topicId),
      proxyChoice: 'for_vote',
      proxySelectedId: null
    });
    this.refreshProxyTargets();
  },

  pickProxyChoice(e) {
    this.setData({ proxyChoice: e.currentTarget.dataset.choice, proxySelectedId: null });
  },

  pickProxyOption(e) {
    this.setData({ proxySelectedId: Number(e.currentTarget.dataset.selectedId), proxyChoice: '' });
  },

  toggleProxyTarget(e) {
    const memberId = Number(e.currentTarget.dataset.memberId);
    const targets = (this.data.proxyTargets || []).map(function (item) {
      if (Number(item.memberId) === memberId && !item.disabled) {
        return Object.assign({}, item, { checked: !item.checked });
      }
      return item;
    });
    const selectedIds = targets.filter(function (item) { return item.checked; }).map(function (item) { return item.memberId; });
    const allTargets = (this.data.proxyAllTargets || []).map(function (item) {
      return Object.assign({}, item, { checked: selectedIds.indexOf(item.memberId) >= 0 });
    });
    this.setData({ proxyTargets: targets, proxyAllTargets: allTargets, proxySelectedCount: selectedIds.length });
  },

  pickProxyProof() {
    const that = this;
    wx.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success(res) {
        const url = res.tempFilePaths && res.tempFilePaths[0] ? res.tempFilePaths[0] : '';
        const parts = url.split(/[\\/]/);
        that.setData({
          proxyProofUrl: url,
          proxyProofName: parts[parts.length - 1] || ('proxy_' + Date.now() + '.jpg')
        });
      }
    });
  },

  async submitProxyAction() {
    if (this.data.proxySubmitting) return;
    const selectedIds = (this.data.proxyTargets || [])
      .filter(function (item) { return item.checked && !item.disabled; })
      .map(function (item) { return item.memberId; });
    if (!selectedIds.length) {
      wx.showToast({ title: '请选择代录对象', icon: 'none' });
      return;
    }
    if (!this.data.proxyProofUrl) {
      wx.showToast({ title: '请上传凭证', icon: 'none' });
      return;
    }

    const payload = {
      actionType: this.data.proxyAction,
      memberIds: selectedIds,
      proofUrl: this.data.proxyProofUrl,
      proofName: this.data.proxyProofName
    };
    if (this.data.proxyAction === 'vote') {
      if (!this.data.proxyTopicId) {
        wx.showToast({ title: '请选择投票议题', icon: 'none' });
        return;
      }
      payload.topicId = this.data.proxyTopicId;
      const topic = (this.data.detail.record.topics || []).find(t => Number(t.id) === Number(this.data.proxyTopicId));
      if (topic && topic.decisionType === 'multi_choice') {
        if (!this.data.proxySelectedId) {
          wx.showToast({ title: '请选择投票选项', icon: 'none' });
          return;
        }
        payload.selectedId = this.data.proxySelectedId;
      } else {
        payload.choice = this.data.proxyChoice || 'for_vote';
      }
    }

    this.setData({ proxySubmitting: true });
    try {
      await api.committeeProxySubmit(this.meetingId, payload);
      wx.showToast({ title: '代录已提交', icon: 'success' });
      this.setData({ proxyVisible: false, proxySubmitting: false });
      this.loadDetail();
    } catch (e) {
      this.setData({ proxySubmitting: false });
      wx.showToast({ title: e.message || '提交失败', icon: 'none' });
    }
  },

  async startMeeting() {
    try {
      await api.committeeAdvance(this.meetingId, 'start');
      wx.showToast({ title: '会议已开始', icon: 'success' });
      this.loadDetail();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async endMeeting() {
    try {
      await api.committeeAdvance(this.meetingId, 'end');
      wx.showToast({ title: '会议已结束', icon: 'success' });
      this.loadDetail();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async toggleFlag(e) {
    try { await api.committeeToggleFlag(this.meetingId, e.currentTarget.dataset.flag); this.loadDetail(); }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async toggleJuwei() {
    try { await api.committeeToggleJuwei(this.meetingId); this.loadDetail(); }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async sendAll() {
    try { await api.committeeSendAll(this.meetingId); this.loadDetail(); }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async signAll() {
    try { await api.committeeSignAll(this.meetingId); this.loadDetail(); }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async markCompliance(e) {
    var status = e.currentTarget.dataset.status;
    try { await api.committeeCompliance(this.meetingId, status); this.loadDetail(); }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async archiveDirect() {
    var detail = this.data.detail;
    detail.publish = { published: false, publishDate: null };
    detail._archived = true;
    this.setData({ detail: detail });
    wx.showToast({ title: '已归档', icon: 'success' });
  },

  async publishNow() {
    try { await api.committeePublish(this.meetingId); wx.showToast({ title: '已公示', icon: 'success' }); this.loadDetail(); }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  async removeMeeting() {
    wx.showModal({
      title: '确认取消',
      content: '确定取消该会议？',
      success: async (res) => {
        if (res.confirm) {
          try { await api.committeeRemove(this.meetingId); wx.navigateBack(); }
          catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
        }
      }
    });
  },

  openAddTopic() {
    this.setData({
      addTopicVisible: true,
      newTopicForm: { title: '', decisionType: 'simple', type: 'decision', optionsText: '' }
    });
  },
  closeAddTopic() {
    this.setData({ addTopicVisible: false });
  },
  onTopicFormInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ ['newTopicForm.' + field]: e.detail.value });
  },
  pickDecisionType(e) {
    this.setData({ 'newTopicForm.decisionType': e.currentTarget.dataset.type });
  },
  async submitAddTopic() {
    const f = this.data.newTopicForm;
    if (!f.title.trim()) {
      wx.showToast({ title: '请输入议题名称', icon: 'none' });
      return;
    }
    var optionsJson = null;
    if (f.decisionType === 'multi_choice' && f.optionsText.trim()) {
      var labels = f.optionsText.split('\n').filter(function (l) { return l.trim(); });
      var opts = labels.map(function (l, i) { return { id: i + 1, label: l.trim() }; });
      optionsJson = JSON.stringify(opts);
    }
    try {
      await api.committeeAddTopic(this.meetingId, f.title.trim(), f.type, f.decisionType, optionsJson);
      wx.showToast({ title: '议题已添加', icon: 'success' });
      this.setData({ addTopicVisible: false });
      this.loadDetail();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },
  async removeTopic(e) {
    wx.showModal({ title: '删除议题', content: '确认删除该议题？',
      success: async (res) => { if (res.confirm) {
        try { await api.committeeRemoveTopic(this.meetingId, e.currentTarget.dataset.topicId); await this.loadDetail(); }
        catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
      }}
    });
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
          await api.committeeAddEvidence(that.meetingId, name, '照片');
          wx.showToast({ title: '已上传', icon: 'success' });
          that.loadDetail();
        } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
      }
    });
  },
  async removeEvidence(e) {
    try { await api.committeeRemoveEvidence(this.meetingId, e.currentTarget.dataset.evId); await this.loadDetail(); }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  viewMinutes() {
    wx.navigateTo({ url: '/pages/minutes/minutes?meetingId=' + this.meetingId + '&from=committee-detail' });
  },

  showAttendanceDetail() {
    wx.showModal({ title: '签到记录', content: '请查看下方参会名单', showCancel: false });
  },

  showVoteDetail() {
    wx.showModal({ title: '表决记录', content: '请查看下方表决结果', showCancel: false });
  },

  viewNoticeDraft() {
    var detail = this.data.detail;
    if (detail && detail.noticeDraft) {
      wx.showModal({ title: '会议通知', content: detail.noticeDraft.content, showCancel: false });
    }
  },

  addArchiveExtra() {
    var that = this;
    wx.chooseImage({
      count: 1, sizeType: ['compressed'], sourceType: ['album', 'camera'],
      success: function (res) {
        var name = '归档材料_' + new Date().getTime() + '.jpg';
        var detail = that.data.detail;
        if (!detail.archiveExtras) detail.archiveExtras = [];
        detail.archiveExtras.push({ id: detail.archiveExtras.length + 1, fileName: name });
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

  showWip() { wx.showToast({ title: '功能开发中', icon: 'none' }); }
});
