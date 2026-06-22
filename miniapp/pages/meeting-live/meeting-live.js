const app = getApp();
const api = require('../../utils/api');

function nowHM() {
  const d = new Date();
  return d.getHours() + ':' + String(d.getMinutes()).padStart(2, '0');
}

Page({
  data: {
    meetingId: null,
    detail: null,
    userView: '',          // 'chair' | 'member'
    steps: [],             // [{ key, label }]
    stepTotal: 0,
    currentStep: 1,        // 1-based index into steps
    step1Done: false,      // 确认参会
    step3Done: false,      // 全部议题已表决
    step1Time: '',
    hint: '',              // 当前步骤的引导提示（只提示，不强制）
    readiness: [],         // 主任结束前的参考清单（advisory）
    readyAllOk: false,
    ending: false
  },

  onLoad(options) {
    this.meetingId = options.meetingId;
    this.setData({ meetingId: options.meetingId });
    this.loadDetail();
  },

  onShow() {
    // 从代录等返回时刷新
    if (this.meetingId && this.data.detail) this.loadDetail({ keepStep: true });
  },

  async loadDetail(opts) {
    opts = opts || {};
    try {
      const detail = await api.committeeDetail(this.meetingId);
      if (detail.stage !== 'ongoing') {
        // 会议已结束/未开始，退回详情页
        wx.showToast({ title: '会议未在进行中', icon: 'none' });
        setTimeout(() => wx.navigateBack(), 600);
        return;
      }
      this.applyView(detail, opts.keepStep ? this.data.currentStep : null);
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  // 由 detail 推导视图状态
  applyView(detail, keepStep) {
    const userView = detail.userView === 'chair' ? 'chair' : 'member';
    const record = detail.record || {};
    const attendances = record.attendances || [];
    const topics = record.topics || [];

    const me = attendances.find(a => a.isSelf);
    const step1Done = !!(me && me.signedIn);
    const step3Done = topics.length === 0 || topics.every(t => t.myVote);

    const steps = userView === 'chair'
      ? [{ key: 'attend', label: '确认参会' }, { key: 'vote', label: '表决' }, { key: 'end', label: '结束会议' }]
      : [{ key: 'attend', label: '确认参会' }, { key: 'vote', label: '表决' }, { key: 'done', label: '完成' }];

    // 默认落在「该处理」的步骤；keepStep 时保留用户当前所在步
    let currentStep;
    if (keepStep) currentStep = Math.min(keepStep, steps.length);
    else if (!step1Done) currentStep = 1;
    else if (!step3Done) currentStep = 2;
    else currentStep = 3;

    const signedInCount = attendances.filter(a => a.signedIn).length;
    record.signedInCount = signedInCount;
    record.signedInTotal = attendances.length;

    const readiness = this.buildReadiness(detail, topics);
    const readyAllOk = readiness.every(r => r.ok);

    this.setData({
      detail, userView, steps, stepTotal: steps.length,
      currentStep, step1Done, step3Done,
      readiness, readyAllOk,
      hint: this.buildHint(userView, steps, currentStep, step1Done, topics, readyAllOk, readiness)
    });
  },

  buildReadiness(detail, topics) {
    const record = detail.record || {};
    const checks = record.checks || [];
    const list = [];
    // 参会过半（取后端 checks[0]）
    if (checks[0]) list.push({ label: checks[0].label, detail: checks[0].detail, ok: !!checks[0].ok });
    // 议题表决
    if (topics.length) {
      const passed = topics.filter(t => t.status === 'passed').length;
      const pending = topics.filter(t => t.status === 'pending').length;
      list.push({
        label: '议题表决', ok: pending === 0,
        detail: passed + '/' + topics.length + ' 通过' + (pending ? ' · ' + pending + ' 项待表决' : '')
      });
    }
    // 线下佐证（取后端 checks[1]）
    if (checks[1]) list.push({ label: checks[1].label, detail: checks[1].detail, ok: !!checks[1].ok });
    return list;
  },

  buildHint(userView, steps, currentStep, step1Done, topics, readyAllOk, readiness) {
    const key = steps[currentStep - 1] ? steps[currentStep - 1].key : '';
    if (key === 'attend') {
      return step1Done ? '已确认参加，可进入下一步表决。' : '请先确认是否参加本次会议。';
    }
    if (key === 'vote') {
      if (!topics.length) return '本次会议没有表决议题，可直接进入下一步。';
      const unvoted = topics.filter(t => !t.myVote).length;
      if (unvoted > 0) return '还有 ' + unvoted + ' 项议题待你表决（未投不影响推进流程）。';
      return '你已完成全部 ' + topics.length + ' 项表决。';
    }
    if (key === 'done') {
      return '你的参会操作已完成，等待主持人宣布结束会议。';
    }
    if (key === 'end') {
      if (readyAllOk) return '各项已就绪，可结束会议并生成会议纪要。';
      const undone = readiness.filter(r => !r.ok).map(r => r.label).join('、');
      return '以下为结束前参考：' + undone + ' 尚未完成。未完成项不影响结束会议，确认无误即可结束。';
    }
    return '';
  },

  // ── 步骤导航（不强制：允许自由前后切换已解锁步骤）──
  goStep(e) {
    const step = parseInt(e.currentTarget.dataset.step);
    if (step === 1) return this.setStep(1);
    if (this.data.step1Done) this.setStep(step);
    else wx.showToast({ title: '请先确认参会', icon: 'none' });
  },
  nextStep() {
    const next = Math.min(this.data.currentStep + 1, this.data.stepTotal);
    if (next === 2 && !this.data.step1Done) {
      wx.showToast({ title: '请先确认参会', icon: 'none' });
      return;
    }
    this.setStep(next);
  },
  prevStep() {
    this.setStep(Math.max(this.data.currentStep - 1, 1));
  },
  setStep(step) {
    const d = this.data;
    this.setData({
      currentStep: step,
      hint: this.buildHint(d.userView, d.steps, step, d.step1Done, (d.detail.record || {}).topics || [], d.readyAllOk, d.readiness)
    });
  },

  // ── 步骤1：确认参会 ──
  confirmAttend() {
    wx.showModal({
      title: '确认参加会议',
      content: '请确认：你会参加本次会议。',
      confirmText: '确认参加',
      cancelText: '再看看',
      success: (res) => {
        if (!res.confirm) return;
        api.committeeSelfToggle(this.meetingId, 'signedIn').catch(() => {});
        this.setData({ step1Done: true, step1Time: nowHM() });
        // 确认后自动进入表决步
        this.setStep(2);
      }
    });
  },

  // ── 步骤2：表决 ──
  vote(e) {
    if (!this.data.step1Done) {
      wx.showToast({ title: '请先确认参会', icon: 'none' });
      return;
    }
    const ds = e.currentTarget.dataset;
    const topicId = ds.topicId;
    const choice = ds.choice;
    const selectedId = ds.selectedId;
    const record = this.data.detail.record || {};
    const topic = (record.topics || []).find(t => String(t.id) === String(topicId));
    const opt = topic && selectedId ? (topic.options || []).find(o => String(o.id) === String(selectedId)) : null;
    const label = opt ? opt.label : (choice === 'for_vote' ? '同意' : choice === 'against' ? '不同意' : '弃权');
    wx.showModal({
      title: '确认你的选择',
      content: '你选择了「' + label + '」。确认后这项不能修改。',
      confirmText: '确认选择',
      cancelText: '再看看',
      success: async (res) => {
        if (!res.confirm) return;
        await this._submitVote(topicId, choice, selectedId);
      }
    });
  },

  async _submitVote(topicId, choice, selectedId) {
    const detail = this.data.detail;
    const topics = (detail.record.topics || []).map(t => {
      if (String(t.id) === String(topicId)) {
        t.myVote = choice || selectedId;
        if (selectedId) {
          const opt = (t.options || []).find(o => String(o.id) === String(selectedId));
          if (opt) t.myVoteLabel = { id: opt.id, label: opt.label };
        }
      }
      return t;
    });
    detail.record.topics = topics;
    const step3Done = topics.length === 0 || topics.every(t => t.myVote);
    const readiness = this.buildReadiness(detail, topics);
    const readyAllOk = readiness.every(r => r.ok);
    this.setData({
      'detail.record.topics': topics,
      step3Done, readiness, readyAllOk,
      hint: this.buildHint(this.data.userView, this.data.steps, this.data.currentStep, this.data.step1Done, topics, readyAllOk, readiness)
    });
    try { await api.committeeVote(this.meetingId, topicId, choice, selectedId); } catch (e) {}
  },

  // ── 步骤3（主任）：结束会议（只提示不拦截）──
  endMeeting() {
    const undone = this.data.readiness.filter(r => !r.ok).map(r => r.label);
    const content = undone.length
      ? '仍有「' + undone.join('、') + '」未完成。这些不影响结束会议，确认现在结束并生成纪要吗？'
      : '确认结束本次会议并生成会议纪要？';
    wx.showModal({
      title: '结束会议',
      content,
      confirmText: '结束会议',
      cancelText: '再想想',
      success: async (res) => {
        if (!res.confirm) return;
        this.setData({ ending: true });
        try {
          await api.committeeAdvance(this.meetingId, 'end');
          wx.showToast({ title: '会议已结束', icon: 'success' });
          setTimeout(() => wx.navigateBack(), 600);
        } catch (e) {
          this.setData({ ending: false });
          wx.showToast({ title: e.message || '操作失败', icon: 'none' });
        }
      }
    });
  },

  viewMinutes() {
    wx.navigateTo({ url: '/pages/minutes/minutes?meetingId=' + this.meetingId + '&from=meeting-live' });
  },

  exitLive() {
    wx.navigateBack();
  }
});
