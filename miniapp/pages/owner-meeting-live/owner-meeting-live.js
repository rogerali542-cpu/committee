const app = getApp();
const api = require('../../utils/api');

function needHalf(total) { return total > 0 ? Math.floor(total / 2) + 1 : 0; }

Page({
  data: {
    meetingId: null,
    detail: null,
    userView: '',          // 'chair' | 'owner'
    isRepresentative: false,
    needsVote: true,
    steps: [],
    stepTotal: 0,
    currentStep: 1,
    topics: [],            // 派生后的议题（含双过半进度、我的选择）
    topicIndex: 0,         // 逐项表决游标
    topicTotal: 0,
    voteDone: false,       // 我代表的户是否全部投完
    quorum: null,          // 法定人数概览
    hint: '',
    readiness: [],
    readyAllOk: false,
    ending: false
  },

  onLoad(options) {
    this.meetingId = parseInt(options.id);
    this.setData({ meetingId: this.meetingId });
    this.loadDetail();
  },
  onShow() {
    if (this.meetingId && this.data.detail) this.loadDetail({ keepStep: true });
  },

  async loadDetail(opts) {
    opts = opts || {};
    try {
      const detail = await api.omDetail(this.meetingId);
      if (!detail) return;
      if (detail.stage !== 'ongoing') {
        wx.showToast({ title: '大会未在进行中', icon: 'none' });
        setTimeout(() => wx.navigateBack(), 600);
        return;
      }
      this.applyView(detail, opts.keepStep);
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  applyView(detail, keepStep) {
    const role = app.globalData && app.globalData.activeRole ? app.globalData.activeRole.role : '';
    const userView = (role === '主任' || role === '副主任') ? 'chair' : 'owner';
    const isRepresentative = !!detail.isRepresentative;
    const needsVote = detail.needsVote !== false;

    const totalOwners = detail.totalOwners || 0;
    const totalArea = detail.totalArea || 0;
    const needOwners = needHalf(totalOwners);
    const needArea = needHalf(totalArea);

    const rawTopics = (detail.recordInfo && detail.recordInfo.topics) || [];
    const myVotes = detail.myVotes || {};
    const topics = rawTopics.map(t => {
      const mc = myVotes[t.id];
      const votedAgg = (t.forOwners || 0) + (t.agOwners || 0) + (t.abOwners || 0);
      return {
        id: t.id, title: t.title, type: t.type,
        myChoice: mc || '',
        myChoiceLabel: mc === 'for' ? '同意' : mc === 'against' ? '不同意' : mc === 'abstain' ? '弃权' : '',
        forOwners: t.forOwners || 0, needOwners: needOwners,
        pctForOwners: totalOwners ? Math.round((t.forOwners || 0) / totalOwners * 100) : 0,
        forAreaWan: ((t.forArea || 0) / 10000).toFixed(1),
        needAreaWan: (needArea / 10000).toFixed(1),
        pctForArea: totalArea ? Math.round((t.forArea || 0) / totalArea * 100) : 0,
        passed: !!t.passed, decided: votedAgg > 0
      };
    });
    const topicTotal = topics.length;
    const myVotedCount = topics.filter(t => t.myChoice).length;
    const voteDone = !needsVote || !isRepresentative || topicTotal === 0 || myVotedCount >= topicTotal;

    const ri = detail.recordInfo || {};
    const quorum = {
      okOwners: !!ri.okOwners, okArea: !!ri.okArea, quorumOk: !!ri.quorumOk,
      presentOwners: ri.presentOwners || 0, needOwners: needOwners,
      pctOwners: ri.pctOwners || 0, pctArea: ri.pctArea || 0,
      presentAreaWan: ri.presentAreaWan, totalAreaWan: ri.totalAreaWan,
      supervisorName: ri.supervisorName, supervisorSigned: !!ri.supervisorSigned
    };

    const steps = userView === 'chair'
      ? [{ key: 'attend', label: '出席' }, { key: 'vote', label: '表决' }, { key: 'end', label: '结束公示' }]
      : [{ key: 'vote', label: '表决' }, { key: 'done', label: '完成' }];

    // 逐项游标：默认落在第一个未投的议题
    let topicIndex = this.data.topicIndex || 0;
    const firstPending = topics.findIndex(t => !t.myChoice);
    if (!keepStep) topicIndex = firstPending >= 0 ? firstPending : 0;
    if (topicIndex >= topicTotal) topicIndex = Math.max(0, topicTotal - 1);

    let currentStep;
    if (keepStep) currentStep = Math.min(this.data.currentStep, steps.length);
    else if (userView === 'chair') currentStep = quorum.quorumOk ? 2 : 1;
    else if (isRepresentative && needsVote) currentStep = voteDone ? 2 : 1;
    else currentStep = 1; // 非户代表/无需投票：停在表决进展页

    const readiness = this.buildReadiness(quorum, topics);
    const readyAllOk = readiness.every(r => r.ok);

    this.setData({
      detail, userView, isRepresentative, needsVote,
      steps, stepTotal: steps.length, currentStep,
      topics, topicIndex, topicTotal, voteDone, quorum,
      readiness, readyAllOk,
      hint: this.buildHint({ userView, steps, currentStep, isRepresentative, needsVote, topics, topicIndex, voteDone, quorum, readyAllOk, readiness })
    });
  },

  buildReadiness(quorum, topics) {
    const list = [];
    list.push({
      label: '法定人数（双过半）', ok: quorum.quorumOk,
      detail: '人数 ' + quorum.presentOwners + '/' + quorum.needOwners + ' · 面积 ' + quorum.pctArea + '%'
    });
    if (topics.length) {
      const passed = topics.filter(t => t.passed).length;
      const decided = topics.filter(t => t.decided).length;
      list.push({
        label: '议题表决', ok: decided === topics.length,
        detail: passed + '/' + topics.length + ' 通过' + (decided < topics.length ? ' · ' + (topics.length - decided) + ' 项无票' : '')
      });
    }
    if (quorum.supervisorName) {
      list.push({ label: '监督人签字', ok: quorum.supervisorSigned, detail: quorum.supervisorSigned ? '已签字' : '待签字' });
    }
    return list;
  },

  buildHint(s) {
    const key = s.steps[s.currentStep - 1] ? s.steps[s.currentStep - 1].key : '';
    if (key === 'attend') {
      if (s.quorum.quorumOk) return '已达法定人数（人数与面积均过半），可进入表决。';
      const miss = [];
      if (!s.quorum.okOwners) miss.push('人数');
      if (!s.quorum.okArea) miss.push('面积');
      return '尚未达法定人数（' + miss.join('、') + '未过半）。可返回详情页继续登记出席，确认无误后仍可推进。';
    }
    if (key === 'vote') {
      if (!s.needsVote) return '本次大会无需表决。';
      if (!s.isRepresentative) return '你不是户代表，没有表决权，可查看各事项的表决进展。';
      if (s.topics.length === 0) return '暂无表决事项。';
      const cur = s.topics[s.topicIndex];
      const unvoted = s.topics.filter(t => !t.myChoice).length;
      if (unvoted === 0) return '你代表的房屋已完成全部 ' + s.topics.length + ' 项表决。';
      if (cur && !cur.myChoice) return '请对第 ' + (s.topicIndex + 1) + ' 项「' + cur.title + '」投票（还有 ' + unvoted + ' 项未投）。';
      return '本项你已投票，可切换到未投的事项。';
    }
    if (key === 'done') {
      return s.isRepresentative
        ? '你代表的房屋已完成投票，等待主持人结束大会并公示结果。'
        : '大会进行中，等待主持人结束大会并公示结果。';
    }
    if (key === 'end') {
      if (s.readyAllOk) return '各项已就绪，可结束大会并公示结果。';
      const undone = s.readiness.filter(r => !r.ok).map(r => r.label).join('、');
      return '以下为结束前参考：' + undone + ' 尚未完成。未完成项不影响结束大会，确认无误即可结束。';
    }
    return '';
  },

  refreshHint() {
    const d = this.data;
    this.setData({
      hint: this.buildHint({
        userView: d.userView, steps: d.steps, currentStep: d.currentStep,
        isRepresentative: d.isRepresentative, needsVote: d.needsVote,
        topics: d.topics, topicIndex: d.topicIndex, voteDone: d.voteDone,
        quorum: d.quorum, readyAllOk: d.readyAllOk, readiness: d.readiness
      })
    });
  },

  // ── 步骤导航 ──
  goStep(e) {
    const step = parseInt(e.currentTarget.dataset.step);
    this.setData({ currentStep: step }, () => this.refreshHint());
  },
  nextStep() {
    const next = Math.min(this.data.currentStep + 1, this.data.stepTotal);
    this.setData({ currentStep: next }, () => this.refreshHint());
  },
  prevStep() {
    const prev = Math.max(this.data.currentStep - 1, 1);
    this.setData({ currentStep: prev }, () => this.refreshHint());
  },

  // ── 逐项游标 ──
  prevTopic() {
    if (this.data.topicIndex > 0) this.setData({ topicIndex: this.data.topicIndex - 1 }, () => this.refreshHint());
  },
  nextTopic() {
    if (this.data.topicIndex < this.data.topicTotal - 1) this.setData({ topicIndex: this.data.topicIndex + 1 }, () => this.refreshHint());
  },

  // ── 投票（户代表 / 身兼户代表的主席）──
  submitVote(e) {
    const topicId = e.currentTarget.dataset.topicId;
    const choice = e.currentTarget.dataset.choice;
    const label = choice === 'for' ? '同意' : choice === 'against' ? '不同意' : '弃权';
    const topic = this.data.topics.find(t => String(t.id) === String(topicId));
    wx.showModal({
      title: '确认你的选择',
      content: '事项：' + (topic ? topic.title : '') + '\n你的选择：' + label + '\n提交后不能修改。',
      confirmText: '确认提交',
      cancelText: '再看看',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          const result = await api.omVote(this.meetingId, topicId, choice);
          if (result && result.error) { wx.showToast({ title: result.error, icon: 'none' }); return; }
          wx.showToast({ title: '投票成功', icon: 'success' });
          // 投完自动跳到下一个未投议题
          await this.loadDetail({ keepStep: true });
          const next = this.data.topics.findIndex(t => !t.myChoice);
          if (next >= 0) this.setData({ topicIndex: next }, () => this.refreshHint());
        } catch (err) {
          wx.showToast({ title: err.message || '投票失败', icon: 'none' });
        }
      }
    });
  },

  // ── 结束大会（主席，只提示不拦截）──
  endMeeting() {
    const undone = this.data.readiness.filter(r => !r.ok).map(r => r.label);
    const content = undone.length
      ? '仍有「' + undone.join('、') + '」未完成。这些不影响结束大会，确认现在结束并进入公示吗？'
      : '确认结束本次大会并进入公示？';
    wx.showModal({
      title: '结束大会',
      content,
      confirmText: '结束大会',
      cancelText: '再想想',
      success: async (res) => {
        if (!res.confirm) return;
        this.setData({ ending: true });
        try {
          await api.omAdvance(this.meetingId, 'end');
          wx.showToast({ title: '大会已结束', icon: 'success' });
          setTimeout(() => wx.navigateBack(), 600);
        } catch (e) {
          this.setData({ ending: false });
          wx.showToast({ title: e.message || '操作失败', icon: 'none' });
        }
      }
    });
  },

  // 重操作回详情页完成（出席登记 / 补录纸质票）
  backToDetail() {
    wx.navigateBack();
  },
  viewMinutes() {
    wx.navigateTo({ url: '/pages/minutes/minutes?meetingId=' + this.meetingId + '&from=owner-meeting-live' });
  },
  exitLive() {
    wx.navigateBack();
  }
});
