const app = getApp();
const api = require('../../utils/api');
const perm = require('../../utils/perm');

function toNumber(value) {
  var n = Number(value);
  return isNaN(n) ? 0 : n;
}

function percent(done, total) {
  return total > 0 ? Math.round(done / total * 100) : 0;
}

function topicVoteCount(topic) {
  if (topic && topic.decisionType === 'multi_choice' && topic.options) {
    return topic.options.reduce(function (sum, opt) { return sum + toNumber(opt.votes); }, 0);
  }
  return toNumber(topic && topic.forVotes) + toNumber(topic && topic.agVotes) + toNumber(topic && topic.abVotes);
}

function topicTypeLabel(topic) {
  var type = String(topic && topic.type || '').toLowerCase();
  if (type === 'notice') return '通报';
  if (type === 'discussion') return '讨论';
  if (type === 'major') return '重大';
  if (topic && topic.decisionType === 'multi_choice') return '多选一';
  if (topic && topic.voteRequired === false) return '记录';
  return '表决';
}

function topicTypeClass(topic) {
  var type = String(topic && topic.type || '').toLowerCase();
  if (type === 'notice') return 'notice';
  if (type === 'discussion') return 'discussion';
  if (type === 'major') return 'major';
  if (topic && topic.decisionType === 'multi_choice') return 'multi';
  return 'decision';
}

function topicStatusLabel(topic) {
  if (!topic) return '待确认';
  if (topic.voteRequired === false) return '已记录';
  if (topic.status === 'passed' || topic.passed) return '已通过';
  if (topic.status === 'failed') return '未通过';
  return '待完成';
}

function topicStatusClass(topic) {
  if (!topic) return 'pending';
  if (topic.voteRequired === false) return 'recorded';
  if (topic.status === 'passed' || topic.passed) return 'passed';
  if (topic.status === 'failed') return 'failed';
  return 'pending';
}

function topicVoteSummary(topic) {
  if (!topic) return '';
  if (topic.voteRequired === false) return topic.text || '无需表决，已作为会议记录事项';
  if (topic.decisionType === 'multi_choice') {
    var opts = topic.options || [];
    if (!opts.length) return topic.text || '多选一表决';
    return opts.map(function (opt) {
      return (opt.label || opt.name || '选项') + ' ' + toNumber(opt.votes);
    }).join(' / ') + (topic.need ? '，通过需≥' + topic.need : '');
  }
  return '同意 ' + toNumber(topic.forVotes) +
    ' / 反对 ' + toNumber(topic.agVotes) +
    ' / 弃权 ' + toNumber(topic.abVotes) +
    (topic.need ? '，通过需≥' + topic.need : '');
}

function decorateMeetingTopics(detail) {
  if (!detail || !detail.record || !detail.record.topics) return;
  detail.record.topics = detail.record.topics.map(function (topic) {
    topic.typeLabel = topicTypeLabel(topic);
    topic.typeClass = topicTypeClass(topic);
    topic.statusLabel = topicStatusLabel(topic);
    topic.statusClass = topicStatusClass(topic);
    topic.voteSummary = topicVoteSummary(topic);
    return topic;
  });
}

function buildFlowStats(detail) {
  if (!detail) return null;
  var delivery = detail.delivery || {};
  var memberDeliveries = delivery.memberDeliveries || [];
  var memberTotal = detail.members && detail.members.length ? detail.members.length : 0;
  var deliveryTotal = toNumber(delivery.total) || memberDeliveries.length || memberTotal;
  var noticeDone = delivery.noticeDone !== undefined && delivery.noticeDone !== null
    ? toNumber(delivery.noticeDone)
    : memberDeliveries.filter(function (item) { return item.noticeDelivered; }).length;
  var materialDone = delivery.materialDone !== undefined && delivery.materialDone !== null
    ? toNumber(delivery.materialDone)
    : memberDeliveries.filter(function (item) { return item.materialDelivered; }).length;

  var record = detail.record || {};
  var attendances = record.attendances || [];
  var attendanceTotal = attendances.length || memberTotal || deliveryTotal;
  var signedInCount = record.signedInCount !== undefined && record.signedInCount !== null
    ? toNumber(record.signedInCount)
    : attendances.filter(function (item) { return item.signedIn; }).length;
  var need = attendanceTotal > 0 ? Math.floor(attendanceTotal / 2) + 1 : 0;

  var topics = (record.topics || []).filter(function (topic) { return topic.voteRequired !== false; });
  var topicCount = topics.length;
  var passedCount = topics.filter(function (topic) { return topic.passed; }).length;
  var pendingCount = topics.filter(function (topic) {
    return topic.status === 'pending' || (attendanceTotal > 0 && topicVoteCount(topic) < attendanceTotal);
  }).length;
  var failedCount = Math.max(0, topicCount - passedCount - pendingCount);
  var votedCount = topics.reduce(function (sum, topic) { return sum + topicVoteCount(topic); }, 0);
  var voteTotal = topicCount * attendanceTotal;
  var noticePct = percent(noticeDone, deliveryTotal);
  var materialPct = percent(materialDone, deliveryTotal);

  return {
    delivery: {
      total: deliveryTotal,
      noticeDone: noticeDone,
      materialDone: materialDone,
      noticePct: noticePct,
      materialPct: materialPct,
      pct: Math.min(noticePct, materialPct),
      done: deliveryTotal > 0 && noticeDone >= deliveryTotal && materialDone >= deliveryTotal,
      statusText: deliveryTotal > 0 ? '未完成' : '未记录'
    },
    attendance: {
      total: attendanceTotal,
      signedInCount: signedInCount,
      need: need,
      pct: percent(signedInCount, attendanceTotal),
      done: attendanceTotal > 0 && signedInCount >= need,
      statusText: attendanceTotal > 0 ? (signedInCount >= need ? '已达到法定人数' : '未达到法定人数') : '暂无参会记录'
    },
    vote: {
      topicCount: topicCount,
      passedCount: passedCount,
      pendingCount: pendingCount,
      failedCount: failedCount,
      votedCount: votedCount,
      totalVoteCount: voteTotal,
      pct: percent(votedCount, voteTotal),
      done: topicCount === 0 || pendingCount === 0,
      metaText: topicCount === 0 ? '未设置表决议题' : '投票完成 ' + votedCount + '/' + voteTotal,
      statusText: topicCount === 0
        ? '本次无表决议题'
        : (pendingCount > 0 ? pendingCount + '项待完成' : passedCount + '/' + topicCount + '项通过')
    }
  };
}

Page({
  data: {
    detail: null,
    userView: '',
    activeRole: {},
    currentStep: 1,
    step1Done: false, step2Done: false, step3Done: false,
    step1Time: '', step2Time: '', step3Time: '',
    stepDone: 0, stepTotal: 2, stepAllDone: false,
    prepareSteps: [], prepareHint: '', prepareMode: '',
    deliveryExpanded: false,
    flowStatsOpen: false,
    archiveLogOpen: false,
    noticePackageVisible: false,
    recAudioPlaying: false,
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
    proxySubmitting: false,
    // 编辑会议
    editVisible: false,
    editForm: { title: '', meetingDate: '', meetingTime: '', location: '', description: '', content: '' },
    noticeContentDirty: false,
    locationOptions: ['社区活动室', '物业办公室', '社区会议室', '线上会议', '待定'],
    // 编辑通知
    noticeEditVisible: false,
    noticeEditForm: { title: '', content: '' },
    sendVisible: false,
    sendMembers: [],
    sendSelectedCount: 0,
    sendSubmitting: false
  },

  onLoad(options) {
    this.meetingId = parseInt(options.id);
    this.fromNotice = options.fromNotice === '1';
    this.setData({ activeRole: app.globalData.activeRole || {} });
    this.loadDetail();
  },

  onShow() { this.loadDetail(); },

  onUnload() { this.destroyRecAudio(); },
  onHide() { this.pauseRecAudio(); },

  noop() {},

  // ── 会议录音存档：试听 / 下载转发 ──
  destroyRecAudio() {
    if (this._recAudio) { try { this._recAudio.destroy(); } catch (e) {} this._recAudio = null; }
  },
  pauseRecAudio() {
    if (this._recAudio && this.data.recAudioPlaying) { try { this._recAudio.pause(); } catch (e) {} }
  },
  toggleRecording() {
    var url = this.data.detail && this.data.detail.record && this.data.detail.record.recordingUrl;
    if (!url) { wx.showToast({ title: '暂无录音', icon: 'none' }); return; }
    if (this._recAudio && this.data.recAudioPlaying) {
      this._recAudio.pause();
      return;
    }
    if (!this._recAudio) {
      var ctx = wx.createInnerAudioContext();
      ctx.src = url;
      ctx.onPlay(() => this.setData({ recAudioPlaying: true }));
      ctx.onPause(() => this.setData({ recAudioPlaying: false }));
      ctx.onStop(() => this.setData({ recAudioPlaying: false }));
      ctx.onEnded(() => this.setData({ recAudioPlaying: false }));
      ctx.onError((e) => {
        this.setData({ recAudioPlaying: false });
        wx.showToast({ title: '播放失败，请检查网络/域名白名单', icon: 'none' });
      });
      this._recAudio = ctx;
    }
    this._recAudio.play();
  },
  downloadRecording() {
    var url = this.data.detail && this.data.detail.record && this.data.detail.record.recordingUrl;
    if (!url) { wx.showToast({ title: '暂无录音', icon: 'none' }); return; }
    wx.showLoading({ title: '下载中…' });
    wx.downloadFile({
      url: url,
      success: (res) => {
        wx.hideLoading();
        if (res.statusCode !== 200 || !res.tempFilePath) { wx.showToast({ title: '下载失败', icon: 'none' }); return; }
        if (wx.shareFileMessage) {
          wx.shareFileMessage({ filePath: res.tempFilePath, fileName: '会议录音.mp3',
            fail: () => wx.showToast({ title: '已下载，可在文件中查看', icon: 'none' }) });
        } else {
          wx.showToast({ title: '已下载', icon: 'none' });
        }
      },
      fail: () => { wx.hideLoading(); wx.showToast({ title: '下载失败', icon: 'none' }); }
    });
  },

  async loadDetail() {
    try {
      const detail = await api.committeeDetail(this.meetingId);
      const userView = detail.userView;
      // 委员走专属极简会议页，不进操作者用的详情/录音页（覆盖通知、待办等入口）
      if (userView === 'member') {
        wx.redirectTo({ url: '/pages/my-meeting/my-meeting?id=' + this.meetingId });
        return;
      }
      // 进行中主任直接进入「会议进行」录音向导（替换当前页，退出即回列表）
      if (detail.stage === 'ongoing' && detail.record && !this.fromNotice &&
          userView === 'chair') {
        wx.redirectTo({ url: '/pages/meeting-live-quick/meeting-live-quick?type=committee&meetingId=' + this.meetingId });
        return;
      }
      if (detail.taskItems) detail.taskItemsText = detail.taskItems.join(' / ');

      // Calculate step states for member & chair view
      let step1Done = false, step2Done = false, step3Done = false;
      if ((userView === 'member' || userView === 'chair') && detail.record && detail.record.attendances) {
        const me = detail.record.attendances.find(a => a.isSelf);
        if (me) {
          step1Done = me.signedIn;
          step2Done = step1Done;
        }
        // Step 2: all topics voted
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
      const stepDone = [step1Done, step3Done].filter(Boolean).length;
      const stepAllDone = forceDone || (stepDone === 2);
      // 自动设置当前步骤
      const currentStep = stepAllDone ? 2 : step1Done ? 2 : 1;

      // Pre-compute topics passed count
      if (detail.record && detail.record.topics) {
        decorateMeetingTopics(detail);
        detail.record.topicsPassed = detail.record.topics.filter(function (t) { return t.passed; }).length;
      }

      // Pre-compute chair view stats
      if (detail.record && detail.record.attendances) {
        detail.record.signedInCount = detail.record.attendances.filter(a => a.signedIn).length;
        detail.record.signedCount = detail.record.attendances.filter(a => a.signed).length;
        detail.record.signedInPct = percent(detail.record.signedInCount, detail.record.attendances.length);
        detail.record.signedPct = percent(detail.record.signedCount, detail.record.attendances.length);
      }

      // Pre-compute delivery stats
      if (detail.delivery && detail.delivery.memberDeliveries) {
        var dels = detail.delivery.memberDeliveries;
        var noticeDone = dels.filter(function (d) { return d.noticeDelivered; }).length;
        var materialDone = dels.filter(function (d) { return d.materialDelivered; }).length;
        var readDone = detail.delivery.readDone !== undefined && detail.delivery.readDone !== null
          ? toNumber(detail.delivery.readDone)
          : dels.filter(function (d) { return d.noticeRead; }).length;
        detail.delivery.noticeDone = noticeDone;
        detail.delivery.materialDone = materialDone;
        detail.delivery.readDone = readDone;
        detail.delivery.noticePct = percent(noticeDone, dels.length);
        detail.delivery.materialPct = percent(materialDone, dels.length);
        detail.delivery.readPct = percent(readDone, dels.length);
      }
      // 确认参会人数：委员"确认参会"走的是出席(signedIn)，按应通知人数 delivery.total 统计
      if (detail.delivery) {
        var atts = (detail.record && detail.record.attendances) ? detail.record.attendances : [];
        var attendConfirmed = atts.filter(function (a) { return a.signedIn; }).length;
        var attendDeclined = atts.filter(function (a) { return a.declined; }).length;
        var attendTotal = detail.delivery.total || atts.length || 0;
        detail.delivery.attendConfirmed = attendConfirmed;
        detail.delivery.attendDeclined = attendDeclined;
        detail.delivery.attendPct = percent(attendConfirmed, attendTotal);
        var mine = atts.filter(function (a) { return a.isSelf; })[0];
        detail.mySignedIn = !!(mine && mine.signedIn);
        detail.myDeclined = !!(mine && mine.declined);
      }
      detail.flowStats = buildFlowStats(detail);

      // 准备阶段（主任）：材料是可选附件，不作为必经步骤。
      let prepareSteps = [], prepareHint = '', prepareMode = '';
      if (userView === 'chair' && detail.stage === 'preparing') {
        const del = detail.delivery || {};
        const allDone = !!del.allDone;
        let cur = allDone ? 2 : 1;
        const st = (i, done) => done ? 'done' : (cur === i ? 'current' : 'todo');
        prepareSteps = [
          { label: '确认通知', state: 'done' },
          { label: '发送通知', state: st(1, allDone) },
          { label: '进度追踪', state: cur === 2 ? 'current' : 'todo' }
        ];
        prepareMode = allDone ? 'start' : 'send';
        prepareHint = allDone
          ? '已送达 ' + (del.total || 0) + ' 位委员，可以开始会议'
          : '发送后委员才能收到会议通知；材料可作为附件补充';
      }
      const noticePackageVisible = !!this.fromNotice &&
        detail.stage !== 'ended' &&
        (userView === 'member' || userView === 'chair') &&
        (!!detail.noticeDraft || !!(detail.materials && detail.materials.length));

      // 快速会议模式（进行中时生效）：隐藏代录、添加议题等逐题表决相关入口
      const quickMode = detail.stage === 'ongoing' && detail.meetingMode === 'quick';

      this.setData({
        detail, userView, currentStep,
        step1Done, step2Done, step3Done,
        stepDone, stepTotal: 2,
        stepAllDone, quickMode,
        prepareSteps, prepareHint, prepareMode,
        noticePackageVisible,
        deliveryExpanded: false
      });
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  goStep(e) {
    const step = parseInt(e.currentTarget.dataset.step);
    // 只能跳转到已解锁的步骤
    if (step === 1 || (step === 2 && this.data.step1Done)) {
      this.setData({ currentStep: step });
    }
  },

  confirmStep1() {
    wx.showModal({
      title: '确认参加会议',
      content: '请确认：你会参加本次会议。',
      confirmText: '确认参加',
      cancelText: '再看看',
      success: (res) => {
        if (!res.confirm) return;
        const now = new Date();
        const time = now.getHours()+':'+String(now.getMinutes()).padStart(2,'0');
        this.setData({
          step1Done: true, step2Done: true, step1Time: time,
          currentStep: 2, stepDone: Math.max(this.data.stepDone, 1)
        });
        api.committeeSelfToggle(this.meetingId, 'signedIn').catch(()=>{});
      }
    });
  },

  confirmStep2() {
    this.setData({ currentStep: 2 });
  },

  confirmStep3() {
    const topics = this.data.detail && this.data.detail.record ? (this.data.detail.record.topics || []) : [];
    if (topics.length && topics.some(t => !t.myVote)) {
      wx.showToast({ title: '请先完成全部议题投票', icon: 'none' });
      return;
    }
    wx.showModal({
      title: '确认提交',
      content: '提交后不能修改。请确认你的选择已经完成。',
      confirmText: '确认提交',
      cancelText: '再看看',
      success: (res) => {
        if (!res.confirm) return;
        const now = new Date();
        const time = now.getHours()+':'+String(now.getMinutes()).padStart(2,'0');
        this.setData({
          step3Done: true, step3Time: time,
          stepDone: 2, stepAllDone: true
        });
      }
    });
  },

  async vote(e) {
    if (!this.data.step1Done) {
      wx.showToast({ title: '请先确认参会', icon: 'none' });
      return;
    }
    const dataset = e.currentTarget.dataset;
    const topicId = dataset.topicId;
    const choice = dataset.choice;
    const selectedId = dataset.selectedId;
    const detail = this.data.detail;
    const topic = detail && detail.record && detail.record.topics
      ? detail.record.topics.find(t => String(t.id) === String(topicId))
      : null;
    const selectedOption = topic && selectedId
      ? (topic.options || []).find(function (o) { return String(o.id) === String(selectedId); })
      : null;
    const choiceLabel = selectedOption
      ? selectedOption.label
      : (choice === 'for_vote' ? '同意' : choice === 'against' ? '不同意' : '弃权');
    wx.showModal({
      title: '确认你的选择',
      content: '你选择了「' + choiceLabel + '」。确认后这项不能修改。',
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
          disabledText = item.signedIn ? (item.signInByProxy ? '已代录参会' : '已确认参会') : '';
        } else {
          if (!topicId) {
            disabled = true;
            disabledText = '请选择议题';
          } else if (!item.signedIn) {
            disabled = true;
            disabledText = '未确认参会';
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

  // 开始会议：固定使用快速模式，传统逐题表决入口不再开放
  async startMeeting() {
    try {
      await api.committeeAdvance(this.meetingId, 'start', 'quick');
      wx.showToast({ title: '会议已开始', icon: 'success' });
      this.enterLive();
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  // 进入「会议进行」全屏向导页：仅快速模式
  enterLive() {
    wx.navigateTo({ url: '/pages/meeting-live-quick/meeting-live-quick?type=committee&meetingId=' + this.meetingId });
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

  toggleDeliveryList() {
    this.setData({ deliveryExpanded: !this.data.deliveryExpanded });
  },

  toggleFlowStats() {
    this.setData({ flowStatsOpen: !this.data.flowStatsOpen });
  },

  toggleArchiveLog() {
    this.setData({ archiveLogOpen: !this.data.archiveLogOpen });
  },

  async sendAll() {
    await this.openSendDialog();
  },

  async openSendDialog() {
    try {
      const members = await api.committeeMembers();
      const deliveredRows = this.data.detail && this.data.detail.delivery
        ? (this.data.detail.delivery.memberDeliveries || [])
        : [];
      const existingIds = deliveredRows.map(function (item) { return Number(item.userRoleId); });
      const hasExisting = existingIds.length > 0;
      const rows = (members || []).map(function (item) {
        const id = Number(item.userRoleId);
        return Object.assign({}, item, {
          checked: hasExisting ? existingIds.indexOf(id) >= 0 : true
        });
      });
      this.setData({
        sendVisible: true,
        sendMembers: rows,
        sendSelectedCount: rows.filter(function (item) { return item.checked; }).length,
        sendSubmitting: false
      });
    } catch (e) {
      wx.showToast({ title: e.message || '通知对象加载失败', icon: 'none' });
    }
  },

  closeSendDialog() {
    this.setData({ sendVisible: false, sendSubmitting: false });
  },

  toggleSendMember(e) {
    const id = Number(e.currentTarget.dataset.id);
    const rows = (this.data.sendMembers || []).map(function (item) {
      if (Number(item.userRoleId) === id) {
        return Object.assign({}, item, { checked: !item.checked });
      }
      return item;
    });
    this.setData({
      sendMembers: rows,
      sendSelectedCount: rows.filter(function (item) { return item.checked; }).length
    });
  },

  selectAllSendMembers() {
    const rows = (this.data.sendMembers || []).map(function (item) {
      return Object.assign({}, item, { checked: true });
    });
    this.setData({ sendMembers: rows, sendSelectedCount: rows.length });
  },

  clearSendMembers() {
    const rows = (this.data.sendMembers || []).map(function (item) {
      return Object.assign({}, item, { checked: false });
    });
    this.setData({ sendMembers: rows, sendSelectedCount: 0 });
  },

  async confirmSendAll() {
    if (this.data.sendSubmitting) return;
    const selectedIds = (this.data.sendMembers || [])
      .filter(function (item) { return item.checked; })
      .map(function (item) { return item.userRoleId; });
    if (!selectedIds.length) {
      wx.showToast({ title: '请选择通知对象', icon: 'none' });
      return;
    }
    this.setData({ sendSubmitting: true });
    try {
      await api.committeeSendAll(this.meetingId, selectedIds);
      wx.showToast({ title: '通知已发送', icon: 'success' });
      this.setData({ sendVisible: false, sendSubmitting: false });
      this.loadDetail();
    } catch (e) {
      this.setData({ sendSubmitting: false });
      wx.showToast({ title: e.message || '发送失败', icon: 'none' });
    }
  },

  // 委员主动确认已阅读会议通知 → 回写已读、清待办
  async confirmNoticeRead() {
    try {
      await api.committeeMarkDeliveryRead(this.meetingId);
      wx.showToast({ title: '已确认参会', icon: 'success' });
      this.loadDetail();
    } catch (e) { wx.showToast({ title: e.message || '操作失败', icon: 'none' }); }
  },

  // 委员"确认参会"：标记本人出席(signedIn)，与主任的确认参会人数统计、「我的会议」页保持一致
  async confirmAttend() {
    try {
      await api.committeeSelfToggle(this.meetingId, 'signedIn');
      wx.showToast({ title: '已确认参会', icon: 'success' });
      this.loadDetail();
    } catch (e) { wx.showToast({ title: e.message || '操作失败', icon: 'none' }); }
  },

  // 委员"无法参会"：标记因故缺席(declined)
  async declineAttend() {
    try {
      await api.committeeSelfToggle(this.meetingId, 'declined');
      wx.showToast({ title: '已登记：无法参会', icon: 'none' });
      this.loadDetail();
    } catch (e) { wx.showToast({ title: e.message || '操作失败', icon: 'none' }); }
  },

  // "取消参会"：回到未响应（从确认参会人数中移除）
  cancelAttend() {
    wx.showModal({
      title: '取消参会',
      content: '确定取消本人参会？取消后将从确认参会人数中移除。',
      confirmText: '取消参会',
      cancelText: '再想想',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await api.committeeSelfToggle(this.meetingId, 'cancel');
          wx.showToast({ title: '已取消参会', icon: 'none' });
          this.loadDetail();
        } catch (e) { wx.showToast({ title: e.message || '操作失败', icon: 'none' }); }
      }
    });
  },

  async toggleDelivery(e) {
    var userRoleId = e.currentTarget.dataset.userRoleId;
    var field = e.currentTarget.dataset.field;
    try { await api.committeeToggleDelivery(this.meetingId, userRoleId, field); this.loadDetail(); }
    catch (err) { wx.showToast({ title: err.message, icon: 'none' }); }
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

  archiveDirect() {
    wx.showModal({
      title: '直接归档',
      content: '归档后该会议将移出会议管理，进入资料库供查阅。确认归档？',
      confirmText: '确认归档',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await api.committeeArchive(this.meetingId);
          wx.showToast({ title: '已归档', icon: 'success' });
          this.loadDetail();
        } catch (e) { wx.showToast({ title: e.message || '归档失败', icon: 'none' }); }
      }
    });
  },

  async publishNow() {
    try { await api.committeePublish(this.meetingId); wx.showToast({ title: '已公示', icon: 'success' }); this.loadDetail(); }
    catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  // 撤销归档：仅误归档用，必填原因，留痕；已公示需先撤回公示
  revokeArchive() {
    wx.showModal({
      title: '撤销归档',
      content: '仅用于误归档（归错会议/误点归档/未结束就归档）。撤销后会议回到归档前阶段，需重新归档。',
      editable: true,
      placeholderText: '请填写撤销原因（必填）',
      success: async (res) => {
        if (!res.confirm) return;
        var reason = (res.content || '').trim();
        if (!reason) { wx.showToast({ title: '撤销必须填写原因', icon: 'none' }); return; }
        try {
          await api.committeeRevokeArchive(this.meetingId, reason);
          wx.showToast({ title: '已撤销归档', icon: 'success' });
          this.loadDetail();
        } catch (e) { wx.showToast({ title: e.message || '撤销失败', icon: 'none' }); }
      }
    });
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
          await api.committeeWithdrawPublish(this.meetingId, reason);
          wx.showToast({ title: '已撤回公示', icon: 'success' });
          this.loadDetail();
        } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
      }
    });
  },

  async viewMinutesRevisions() {
    try {
      var list = await api.committeeMinutesRevisions(this.meetingId);
      if (!list || !list.length) { wx.showToast({ title: '暂无修订记录', icon: 'none' }); return; }
      var lines = list.map(function (r) {
        return 'v' + r.versionNo + ' · ' + (r.editorName || '—') + ' · ' + (r.createdAt || '');
      });
      wx.showModal({
        title: '纪要修订历史',
        content: lines.join('\n'),
        showCancel: false,
        confirmText: '关闭'
      });
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
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
      newTopicForm: { title: '', decisionType: 'simple', type: 'decision', optionsText: '', realName: false }
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
  toggleRealName() {
    this.setData({ 'newTopicForm.realName': !this.data.newTopicForm.realName });
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
      await api.committeeAddTopic(this.meetingId, f.title.trim(), f.type, f.decisionType, optionsJson, f.realName);
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

  // 面向民众的公开纪要：只读正式纪要正文，与内部工作视图分开
  viewPublicMinutes() {
    wx.navigateTo({ url: '/pages/minutes-public/minutes-public?meetingId=' + this.meetingId });
  },

  // 内部 AI 议题报告（详细版）+ 待办，仅供业委会内部查看
  viewInternalReport() {
    wx.navigateTo({ url: '/pages/minutes-internal/minutes-internal?meetingId=' + this.meetingId });
  },

  viewNoticeDraft() {
    var detail = this.data.detail;
    if (detail && detail.noticeDraft) {
      wx.showModal({ title: '会议通知', content: detail.noticeDraft.content, showCancel: false });
    }
  },

  addArchiveExtra() {
    var that = this;
    wx.showModal({
      title: '补充归档材料',
      content: '请填写补充原因。原归档内容不会被修改，补充材料会单独留痕。',
      editable: true,
      placeholderText: '例如：补充纸质签到表扫描件',
      confirmText: '选择文件',
      success: function (modalRes) {
        if (!modalRes.confirm) return;
        var reason = (modalRes.content || '').trim();
        if (!reason) {
          wx.showToast({ title: '请填写补充原因', icon: 'none' });
          return;
        }
        wx.chooseMessageFile({
          count: 1,
          type: 'file',
          success: async function (res) {
            var file = res.tempFiles && res.tempFiles[0] ? res.tempFiles[0] : {};
            var name = file.name || ('归档后补充材料_' + new Date().getTime());
            try {
              await api.committeeAddArchiveExtra(that.meetingId, name, that.formatSize(file.size), reason);
              wx.showToast({ title: '已补充归档', icon: 'success' });
              that.loadDetail();
            } catch (e) {
              wx.showToast({ title: e.message || '补充失败', icon: 'none' });
            }
          }
        });
      }
    });
  },

  removeArchiveExtra(e) {
    wx.showToast({ title: '归档补充材料已留痕，不支持直接删除', icon: 'none' });
  },

  previewMaterial(e) {
    var idx = e.currentTarget.dataset.index;
    var materials = this.data.detail && this.data.detail.materials;
    if (!materials || !materials[idx]) return;
    var m = materials[idx];
    var content = m.content || '（暂无预览内容）';
    wx.showModal({
      title: m.name,
      content: content,
      showCancel: false,
      confirmText: '关闭'
    });
  },

  uploadMaterial() {
    var that = this;
    wx.chooseMessageFile({
      count: 3,
      type: 'file',
      success: function (res) {
        var files = (res.tempFiles || []).map(function (file) {
          return {
            name: file.name || ('材料_' + Date.now()),
            sizeText: that.formatSize(file.size),
            content: '（上传文件，暂无预览内容）'
          };
        });
        Promise.all(files.map(function (file) {
          return api.committeeAddMaterial(that.meetingId, file.name, file.sizeText);
        })).then(function () {
          wx.showToast({ title: '已上传 ' + files.length + ' 份材料', icon: 'success' });
          that.loadDetail();
        }).catch(function (err) {
          wx.showToast({ title: err.message || '上传失败', icon: 'none' });
        });
      }
    });
  },

  async removeMaterial(e) {
    var idx = e.currentTarget.dataset.index;
    var materials = this.data.detail && this.data.detail.materials;
    if (!materials || !materials[idx]) return;
    var item = materials[idx];
    var that = this;
    wx.showModal({
      title: '删除材料',
      content: '确认删除「' + item.name + '」？',
      success: async function (res) {
        if (res.confirm) {
          try { await api.committeeRemoveMaterial(that.meetingId, idx); that.loadDetail(); }
          catch (err) { wx.showToast({ title: err.message, icon: 'none' }); }
        }
      }
    });
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

  // ── 编辑会议 ──

  openEdit() {
    var d = this.data.detail;
    // 规则8：已通知后修改重大信息须重新通知全体成员
    if (d.coreLocked) {
      var that = this;
      wx.showModal({
        title: '会议已通知',
        content: '会议已通知全体成员。修改日期/时间/地点等重大信息后，需重新通知。是否继续？',
        confirmText: '继续修改',
        success: function (res) { if (res.confirm) that._doOpenEdit(d); }
      });
      return;
    }
    this._doOpenEdit(d);
  },

  _doOpenEdit(d) {
    this.setData({
      editVisible: true,
      noticeContentDirty: false,
      editForm: {
        title: d.title || '',
        meetingDate: d.meetingDate || '',
        meetingTime: d.meetingTime || '',
        location: d.location || '',
        description: d.description || '',
        content: (d.noticeDraft && d.noticeDraft.content) || ''
      }
    });
  },

  closeEdit() {
    this.setData({ editVisible: false });
  },

  onEditInput(e) {
    var field = e.currentTarget.dataset.field;
    this.setData({ ['editForm.' + field]: e.detail.value });
    if (field === 'content') this.setData({ noticeContentDirty: true });
  },

  onEditDateChange(e) {
    this.setData({ 'editForm.meetingDate': e.detail.value });
  },

  onEditTimeChange(e) {
    this.setData({ 'editForm.meetingTime': e.detail.value });
  },

  pickEditLocation(e) {
    this.setData({ 'editForm.location': e.currentTarget.dataset.location });
  },

  async submitEdit() {
    var form = this.data.editForm;
    if (!form.title || !form.meetingDate || !form.meetingTime || !form.location) {
      wx.showToast({ title: '请补全标题、时间和地点', icon: 'none' });
      return;
    }
    try {
      // 先存会议要素（会按新要素自动重生成通知草稿）
      await api.committeeUpdate(this.meetingId, form);
      // 若手动改过通知正文，再覆盖保存（保留自定义措辞）
      if (this.data.noticeContentDirty && form.content) {
        await api.committeeUpdateNotice(this.meetingId, form.title, form.content);
      }
      wx.showToast({ title: '已保存', icon: 'success' });
      this.setData({ editVisible: false });
      this.loadDetail();
    } catch (e) {
      wx.showToast({ title: e.message || '保存失败', icon: 'none' });
    }
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
      await api.committeeUpdateNotice(this.meetingId, form.title, form.content);
      wx.showToast({ title: '通知已更新', icon: 'success' });
      this.setData({ noticeEditVisible: false });
      this.loadDetail();
    } catch (e) {
      wx.showToast({ title: e.message || '保存失败', icon: 'none' });
    }
  },

  showWip() { wx.showToast({ title: '功能开发中', icon: 'none' }); }
});
