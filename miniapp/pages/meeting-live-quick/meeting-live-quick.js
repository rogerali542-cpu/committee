const api = require('../../utils/api');

const POLL_INTERVAL = 1500;
const MAX_POLL_COUNT = 120;
const TOPIC_REPORT_POLL_INTERVAL = 2000;
const TOPIC_REPORT_MAX_POLL = 120;
const QUICK_STATE_PREFIX = 'committee_quick_meeting_state_';

function voteHintText(hint) {
  if (!hint) return '需人工确认';
  if (hint.result === 'passed') return '建议通过';
  if (hint.result === 'rejected') return '建议不通过';
  return '需人工确认';
}

function resultLabel(result) {
  if (result === 'passed') return '通过';
  if (result === 'rejected') return '未通过';
  if (result === 'abstain') return '弃权';
  return '需人工确认';
}

function topicTypeLabel(type) {
  if (type === 'notice') return '通报事项';
  if (type === 'discussion') return '讨论事项';
  if (type === 'major') return '重大表决';
  return '表决事项';
}

function reviewLevelLabel(level) {
  if (level === 'auto') return '建议匹配';
  if (level === 'review') return '疑似匹配';
  return '待人工处理';
}

function asPercent(value) {
  if (typeof value !== 'number') return null;
  return Math.round(value > 1 ? value : value * 100);
}

function voteResultLabel(result) {
  if (result === 'passed') return '通过';
  if (result === 'rejected') return '否决';
  return '不明确';
}

// rule_kb 抽取字段 -> 展示用列表（每项一行：字段名 + 文本 + 佐证片段）
function mapExtractedFields(hit) {
  const fields = (hit && hit.extractedFields) || [];
  return fields.map(function (f) {
    const values = f.values || [];
    let text;
    if (f.type === 'keyword_mapping') {
      // 表决结果归一化为中文；资金来源/责任方直接用标签
      text = f.field === '表决结果' ? voteResultLabel(f.normalized) : (f.normalized || '');
    } else {
      // regex：把抽到的具体值去重拼接
      const seen = {};
      const distinct = [];
      values.forEach(function (v) {
        if (v && v.value && !seen[v.value]) { seen[v.value] = 1; distinct.push(v.value); }
      });
      text = distinct.join('、');
    }
    return {
      field: f.field,
      text: text,
      evidences: values.map(function (v) { return { value: v.value, text: v.evidence || '' }; })
    };
  }).filter(function (f) { return f.text; });
}

function formatMs(ms) {
  const total = Math.floor((ms || 0) / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0');
}

function decorateEvidence(topic) {
  const matches = topic.segmentMatches || [];
  const first = matches[0];
  const last = matches.length > 1 ? matches[matches.length - 1] : null;
  const range = first
    ? (first.time || '') + (last && last.time && last.time !== first.time ? ' - ' + last.time : '')
    : '';
  const expanded = !!topic.evidenceExpanded;
  return Object.assign({}, topic, {
    evidenceRange: range,
    evidencePreview: matches.slice(0, 2),
    visibleSegments: expanded ? matches : matches.slice(0, 2),
    evidenceHiddenCount: Math.max(0, matches.length - 2),
    evidenceExpanded: expanded
  });
}

function mapTopic(hit, fallback, voteTotal) {
  const fallbackTitle = typeof fallback === 'string' ? fallback : (fallback && fallback.title);
  // voteTotal: 实到人数，用于"全票/一致通过"按人数补齐预填票数
  const type = (hit && hit.type) || (fallback && fallback.type) || 'decision';
  const voteRequired = hit && typeof hit.voteRequired === 'boolean'
    ? hit.voteRequired
    : (fallback && typeof fallback.voteRequired === 'boolean' ? fallback.voteRequired : !(type === 'notice' || type === 'discussion'));
  const confidence = hit && hit.voteHint && typeof hit.voteHint.confidence === 'number'
    ? Math.round(hit.voteHint.confidence * 100)
    : null;
  // 根据 voteHint 自动预填票数（人工可改、需确认）
  const vh = hit && hit.voteHint;
  const total = Number(voteTotal) || 0;
  let voteFor = 0, voteAgainst = 0, voteAbstain = 0, aiVote = '', aiVoteLabel = '';
  if (voteRequired && vh) {
    if (vh.source === 'explicit') {
      voteFor = Number(vh.forVotes) || 0; voteAgainst = Number(vh.agVotes) || 0; voteAbstain = Number(vh.abVotes) || 0;
      aiVote = 'explicit'; aiVoteLabel = '按录音播报票数';
    } else if (vh.source === 'unanimous' && vh.unanimous && total > 0) {
      voteFor = total; voteAgainst = 0; voteAbstain = 0;
      aiVote = 'unanimous'; aiVoteLabel = '录音为全票/一致通过';
    } else if (vh.source === 'counted') {
      voteFor = Number(vh.forVotes) || 0; voteAgainst = Number(vh.agVotes) || 0; voteAbstain = Number(vh.abVotes) || 0;
      aiVote = 'counted'; aiVoteLabel = '按发言逐个计数(粗略)';
    }
  }
  let result = (vh && vh.result) || 'unclear';
  if (voteRequired && aiVote) {
    const need = total > 0 ? Math.floor(total / 2) + 1 : 1;
    const counted = voteFor + voteAgainst + voteAbstain;
    if (voteFor >= need) result = 'passed';
    else if (total > 0 && counted >= total) result = 'rejected';
    else result = 'unclear';
  }
  const matchConfidence = hit && typeof hit.confidence === 'number' ? asPercent(hit.confidence) : null;
  const matchedSegments = (hit && hit.matchedSegments) || [];
  const segmentMatches = ((hit && hit.segmentMatches) || []).map(function (m) {
    const reasons = m.reasons || [];
    return {
      segmentIndex: m.segmentIndex,
      speaker: m.speaker || '',
      time: typeof m.startMs === 'number' ? formatMs(m.startMs) : '',
      text: m.text || '',
      score: typeof m.score === 'number' ? asPercent(m.score) : null,
      level: m.level || 'review',
      levelLabel: reviewLevelLabel(m.level),
      reasons: reasons,
      reasonText: reasons.join('；'),
      scoreDetail: m.scoreDetail || null
    };
  });
  return decorateEvidence({
    id: (hit && (hit.topicId || hit.tempId)) || fallbackTitle,
    title: (hit && hit.title) || fallbackTitle || '未命名议题',
    type: type,
    typeLabel: topicTypeLabel(type),
    voteRequired: voteRequired,
    suggest: voteRequired ? voteHintText(hit && hit.voteHint) : '记录要点',
    result: result,
    resultLabel: resultLabel(result),
    voteFor: voteFor,
    voteAgainst: voteAgainst,
    voteAbstain: voteAbstain,
    voteCounted: voteFor + voteAgainst + voteAbstain,
    aiVote: aiVote,
    aiVoteLabel: aiVoteLabel,
    confidence: confidence,
    matchConfidence: matchConfidence,
    reviewLevel: (hit && hit.reviewLevel) || (matchedSegments.length ? 'review' : 'empty'),
    reviewLevelLabel: reviewLevelLabel(hit && hit.reviewLevel),
    needManualReview: hit && typeof hit.needManualReview === 'boolean' ? hit.needManualReview : true,
    summaryDraft: (hit && hit.summaryDraft) || '',
    extractedFields: mapExtractedFields(hit),
    segmentMatches: segmentMatches,
    matchedSegments: matchedSegments,
    matchedCount: segmentMatches.length || matchedSegments.length,
    evidenceExpanded: false,
    confirmed: false
  });
}

Page({
  data: {
    type: 'committee',
    meetingId: null,
    detail: null,
    steps: [
      { key: 'sign', label: '确认参会' },
      { key: 'record', label: '录音' },
      { key: 'generate', label: '转写文本' },
      { key: 'confirm', label: '议题匹配' }
    ],
    currentStep: 1,
    signedIn: false,
    selfAttendance: null,

    recording: false,
    recorderStarted: false,
    hasRecording: false,
    tempFilePath: '',
    seconds: 0,
    timeText: '00:00',

    uploading: false,
    polling: false,
    extracting: false,
    taskId: '',
    asrStatus: '',
    processText: '正在上传录音...',
    finishText: '录音已转写，规则抽取结果已生成',
    generated: false,
    extraction: null,

    presetTopics: [],
    aiTopics: [],
    detailOpen: false,
    detailGroup: '',
    detailIndex: -1,
    transcript: [],
    transcriptPreview: '',
    transcriptFullText: '',
    transcriptCharCount: 0,
    transcriptVisible: false,
    transcriptMode: 'short',
    ending: false,

    // 角色 / 录音负责人 / 资料 / 实时议题
    isChair: false,
    myRoleId: null,
    recorderRoleId: null,
    recorderName: '',
    isRecorder: false,
    materials: [],
    signedInList: [],
    voteTotal: 0,
    voteNeed: 0,
    addTopicVisible: false,
    newTopicForm: { title: '', decisionType: 'simple', type: 'decision', optionsText: '' }
  },

  onLoad(options) {
    this.type = options.type === 'owner' ? 'owner' : 'committee';
    this.meetingId = options.meetingId;
    if (this.type === 'owner') {
      wx.showToast({ title: '业主大会模块已停用', icon: 'none' });
      setTimeout(function () { wx.navigateBack(); }, 500);
      return;
    }
    this._pollCount = 0;
    this._topicReportTimers = {};
    this._pendingUploadAfterStop = false;
    this.setData({ type: this.type, meetingId: options.meetingId });
    this.initRecorder();
    this.loadDetail();
  },

  onUnload() {
    this.persistQuickState();
    this.clearTimer();
    this.clearPoll();
    this.clearTopicReportPolls();
    if (this.data.recording && this.recorder) {
      try { this.recorder.stop(); } catch (e) {}
    }
  },

  quickStateKey() {
    return QUICK_STATE_PREFIX + this.meetingId;
  },

  persistQuickState(extra) {
    if (!this.meetingId || !this.data.detail || this.data.detail.stage !== 'ongoing') return;
    const state = Object.assign({
      meetingId: this.meetingId,
      savedAt: Date.now(),
      currentStep: this.data.currentStep,
      generated: this.data.generated,
      taskId: this.data.taskId,
      asrStatus: this.data.asrStatus,
      processText: this.data.processText,
      finishText: this.data.finishText,
      extraction: this.data.extraction,
      presetTopics: this.data.presetTopics,
      aiTopics: this.data.aiTopics,
      transcript: this.data.transcript,
      transcriptPreview: this.data.transcriptPreview,
      transcriptFullText: this.data.transcriptFullText,
      transcriptCharCount: this.data.transcriptCharCount
    }, extra || {});
    try { wx.setStorageSync(this.quickStateKey(), state); } catch (e) {}
  },

  restoreQuickState(signedIn) {
    let saved = null;
    try { saved = wx.getStorageSync(this.quickStateKey()); } catch (e) {}
    if (!saved || String(saved.meetingId) !== String(this.meetingId)) return false;

    const transcript = saved.transcript || [];
    const transcriptState = this.buildTranscriptState(transcript);
    let step = Number(saved.currentStep) || (saved.generated ? 4 : 2);
    if (!signedIn) step = 1;
    else if (step < 2) step = 2;
    if (step > 4) step = 4;
    if (!saved.generated && step > 3) step = 3;

    this.setData(Object.assign({
      currentStep: step,
      generated: !!saved.generated,
      taskId: saved.taskId || '',
      asrStatus: saved.asrStatus || '',
      processText: saved.processText || this.data.processText,
      finishText: saved.finishText || this.data.finishText,
      extraction: saved.extraction || null,
      presetTopics: saved.presetTopics && saved.presetTopics.length ? saved.presetTopics.map(decorateEvidence) : this.data.presetTopics,
      aiTopics: (saved.aiTopics || []).map(decorateEvidence),
      transcript,
      transcriptVisible: false,
      uploading: false,
      polling: false,
      extracting: false
    }, transcriptState, {
      transcriptPreview: transcriptState.transcriptPreview || saved.transcriptPreview || '',
      transcriptFullText: transcriptState.transcriptFullText || saved.transcriptFullText || '',
      transcriptCharCount: transcriptState.transcriptCharCount || saved.transcriptCharCount || 0
    }));

    if (signedIn && saved.taskId && !saved.generated && saved.asrStatus === 'done') {
      this._asrDoneHandled = false;
      this.handleAsrDone({ taskId: saved.taskId, status: 'done' });
    } else if (signedIn && saved.taskId && !saved.generated && saved.asrStatus !== 'failed') {
      this.setData({
        currentStep: 3,
        polling: true,
        processText: saved.processText || this.statusText(saved.asrStatus || 'pending')
      });
      this.startPoll(saved.taskId);
    }
    return true;
  },

  clearQuickState() {
    if (!this.meetingId) return;
    try { wx.removeStorageSync(this.quickStateKey()); } catch (e) {}
  },

  initRecorder() {
    if (!wx.getRecorderManager) return;
    const recorder = wx.getRecorderManager();
    this.recorder = recorder;
    ['offStart', 'offPause', 'offResume', 'offStop', 'offError'].forEach(fn => {
      if (typeof recorder[fn] === 'function') recorder[fn]();
    });

    recorder.onStart(() => {
      this.startTimer();
      this.setData({
        recording: true,
        recorderStarted: true,
        hasRecording: true,
        tempFilePath: '',
        generated: false,
        taskId: '',
        asrStatus: '',
        processText: '正在录音...'
      });
    });

    recorder.onPause(() => {
      this.clearTimer();
      this.setData({ recording: false });
    });

    recorder.onResume(() => {
      this.startTimer();
      this.setData({ recording: true });
    });

    recorder.onStop((res) => {
      this.clearTimer();
      const filePath = res && res.tempFilePath;
      this.setData({
        recording: false,
        recorderStarted: false,
        hasRecording: !!filePath || this.data.hasRecording,
        tempFilePath: filePath || this.data.tempFilePath
      });
      if (this._pendingUploadAfterStop) {
        this._pendingUploadAfterStop = false;
        if (filePath) this.uploadRecording(filePath);
        else wx.showToast({ title: '录音文件为空', icon: 'none' });
      }
    });

    recorder.onError((err) => {
      this.clearTimer();
      this._pendingUploadAfterStop = false;
      this.setData({ recording: false, recorderStarted: false, uploading: false, polling: false, extracting: false });
      wx.showToast({ title: (err && err.errMsg) || '录音失败', icon: 'none' });
    });
  },

  async loadDetail() {
    try {
      const detail = await api.committeeDetail(this.meetingId);
      const raw = (detail.record && detail.record.topics) || [];
      const presetTopics = raw.map(t => mapTopic(null, t));
      const selfAttendance = this.getSelfAttendance(detail);
      const signedIn = !!(selfAttendance && selfAttendance.signedIn);
      const appG = getApp();
      const myRoleId = appG.globalData && appG.globalData.activeRole ? appG.globalData.activeRole.id : null;
      const rec = detail.record || {};
      const recorderRoleId = rec.recorderRoleId || null;
      const isRecorder = !!(recorderRoleId && myRoleId && Number(recorderRoleId) === Number(myRoleId));
      const signedInList = (rec.attendances || []).filter(function (a) { return a.signedIn; });
      const voteTotal = signedInList.length;
      this.setData({
        detail,
        presetTopics,
        selfAttendance,
        signedIn,
        isChair: detail.userView === 'chair',
        myRoleId,
        recorderRoleId,
        recorderName: rec.recorderName || '',
        isRecorder,
        materials: detail.materials || [],
        signedInList,
        voteTotal,
        voteNeed: Math.floor(voteTotal / 2) + 1,
        currentStep: signedIn && this.data.currentStep === 1 ? 2 : this.data.currentStep
      });
      if (detail.stage === 'ongoing') {
        const restored = this.restoreQuickState(signedIn);
        if (signedIn && (!restored || (!this.data.generated && !this.data.taskId))) this.tryRestoreGeneratedFromServer();
      } else {
        this.clearQuickState();
      }
    } catch (e) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  async tryRestoreGeneratedFromServer() {
    if (!this.meetingId || !this.data.signedIn || this.data.generated) return;
    if (typeof api.committeeQuickTranscript !== 'function' || typeof api.committeeQuickExtract !== 'function') return;
    try {
      const transcriptRaw = await api.committeeQuickTranscript(this.meetingId);
      const transcript = this.mapTranscript(transcriptRaw);
      if (!transcript.length) return;
      const extraction = await api.committeeQuickExtract(this.meetingId);
      const mapped = this.mapExtraction(extraction);
      this.setData(Object.assign({
        currentStep: 4,
        extraction,
        presetTopics: mapped.presetTopics,
        aiTopics: mapped.aiTopics,
        transcript,
        transcriptVisible: false,
        uploading: false,
        polling: false,
        extracting: false,
        generated: true,
        finishText: '已恢复上次转写与议题匹配结果'
      }, this.buildTranscriptState(transcript)));
      this.persistQuickState();
    } catch (e) {}
  },

  getSelfAttendance(detail) {
    const attendances = detail && detail.record ? (detail.record.attendances || []) : [];
    return attendances.find(function (a) { return a.isSelf; }) || null;
  },

  confirmSignIn() {
    if (this.data.signedIn) {
      this.setData({ currentStep: 2 });
      this.persistQuickState();
      return;
    }
    wx.showModal({
      title: '确认参加会议',
      content: '请确认本人已进入本次业委会会议。确认后将进入录音转写流程。',
      confirmText: '确认参会',
      cancelText: '再看看',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await api.committeeSelfToggle(this.meetingId, 'signedIn');
          wx.showToast({ title: '已确认参会', icon: 'success' });
          this.setData({ signedIn: true, currentStep: 2 });
          this.persistQuickState();
          this.loadDetail();
        } catch (e) {
          wx.showToast({ title: e.message || '确认失败', icon: 'none' });
        }
      }
    });
  },

  toggleRecord() {
    if (this.type !== 'committee') {
      wx.showToast({ title: '快速录音暂先支持业委会会议', icon: 'none' });
      return;
    }
    if (!this.data.signedIn) {
      wx.showToast({ title: '请先确认参会', icon: 'none' });
      return;
    }
    if (!this.recorder) {
      wx.showToast({ title: '当前微信版本不支持录音', icon: 'none' });
      return;
    }
    if (this.data.uploading || this.data.polling || this.data.extracting) return;

    if (this.data.recording) {
      this.recorder.pause();
      return;
    }
    if (this.data.recorderStarted) {
      this.recorder.resume();
      return;
    }
    if (this.data.tempFilePath) {
      wx.showModal({
        title: '重新录音',
        content: '已有一段录音，重新开始会覆盖当前录音。是否继续？',
        confirmText: '重新录音',
        cancelText: '取消',
        success: (res) => {
          if (res.confirm) this.startRecord();
        }
      });
      return;
    }
    this.startRecord();
  },

  // 开始录音/上传时自动认领为录音人（仅供"谁在录"的协调提示，失败不影响录音）
  markRecorder() {
    if (this.data.isRecorder) return;
    api.committeeClaimRecorder(this.meetingId)
      .then(() => this.setData({ recorderRoleId: this.data.myRoleId, isRecorder: true }))
      .catch(() => {});
  },

  startRecord() {
    this.markRecorder();
    this.clearTimer();
    this.clearPoll();
    this.clearQuickState();
    this.setData({
      seconds: 0,
      timeText: '00:00',
      hasRecording: false,
      tempFilePath: '',
      generated: false,
      extraction: null,
      aiTopics: [],
      transcript: [],
      transcriptPreview: '',
      transcriptFullText: '',
      transcriptCharCount: 0,
      transcriptVisible: false,
      uploading: false,
      polling: false,
      extracting: false
    });
    this.recorder.start({
      duration: 2 * 60 * 60 * 1000,
      sampleRate: 16000,
      numberOfChannels: 1,
      encodeBitRate: 48000,
      format: 'mp3'
    });
  },

  startTimer() {
    this.clearTimer();
    this._timer = setInterval(() => {
      const s = this.data.seconds + 1;
      this.setData({ seconds: s, timeText: this.fmt(s) });
    }, 1000);
  },

  clearTimer() {
    if (this._timer) {
      clearInterval(this._timer);
      this._timer = null;
    }
  },

  clearPoll() {
    if (this._pollTimer) {
      clearInterval(this._pollTimer);
      this._pollTimer = null;
    }
  },

  clearTopicReportPoll(taskKey) {
    if (!this._topicReportTimers) this._topicReportTimers = {};
    if (this._topicReportTimers[taskKey]) {
      clearTimeout(this._topicReportTimers[taskKey]);
      delete this._topicReportTimers[taskKey];
    }
  },

  clearTopicReportPolls() {
    const timers = this._topicReportTimers || {};
    Object.keys(timers).forEach(function (key) {
      clearTimeout(timers[key]);
    });
    this._topicReportTimers = {};
  },

  fmt(s) {
    const m = Math.floor(s / 60);
    return String(m).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
  },

  finishRecord() {
    if (this.type !== 'committee') {
      wx.showToast({ title: '快速录音暂先支持业委会会议', icon: 'none' });
      return;
    }
    if (!this.data.signedIn) {
      wx.showToast({ title: '请先确认参会', icon: 'none' });
      return;
    }
    if (!this.data.hasRecording && !this.data.tempFilePath) {
      wx.showToast({ title: '请先开始录音', icon: 'none' });
      return;
    }
    if (this.data.uploading || this.data.polling || this.data.extracting) return;

    if (this.data.recorderStarted) {
      this._pendingUploadAfterStop = true;
      this.recorder.stop();
      return;
    }
    if (this.data.tempFilePath) {
      this.uploadRecording(this.data.tempFilePath);
    }
  },

  // 不现场录音，直接选一个已有的录音文件上传（微信限制：只能从聊天会话里选文件）
  chooseAudioFile() {
    if (this.type !== 'committee') {
      wx.showToast({ title: '快速录音暂先支持业委会会议', icon: 'none' });
      return;
    }
    if (!this.data.signedIn) {
      wx.showToast({ title: '请先确认参会', icon: 'none' });
      return;
    }
    if (this.data.uploading || this.data.polling || this.data.extracting) return;
    if (!wx.chooseMessageFile) {
      wx.showToast({ title: '当前微信版本不支持选择文件', icon: 'none' });
      return;
    }
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['mp3', 'wav', 'm4a', 'aac', 'amr', 'ogg', 'flac'],
      success: (res) => {
        const file = res.tempFiles && res.tempFiles[0];
        if (!file || !file.path) {
          wx.showToast({ title: '未选择文件', icon: 'none' });
          return;
        }
        this.setData({ tempFilePath: file.path, hasRecording: true, recording: false, recorderStarted: false });
        this.uploadRecording(file.path);
      },
      fail: (err) => {
        if (err && err.errMsg && err.errMsg.indexOf('cancel') >= 0) return;
        wx.showToast({ title: '选择文件失败', icon: 'none' });
      }
    });
  },

  async uploadRecording(filePath) {
    this.markRecorder();
    this.clearPoll();
    this._asrDoneHandled = false;
    this.setData({
      currentStep: 3,
      uploading: true,
      polling: false,
      extracting: false,
      generated: false,
      transcript: [],
      transcriptPreview: '',
      transcriptFullText: '',
      transcriptCharCount: 0,
      transcriptVisible: false,
      asrStatus: 'uploading',
      processText: '正在上传录音...'
    });

    this.persistQuickState();

    try {
      const task = await api.committeeQuickUploadRecording(this.meetingId, filePath);
      this.setData({
        uploading: false,
        polling: true,
        taskId: task.taskId,
        asrStatus: task.status || 'pending',
        processText: this.statusText(task.status)
      });
      this.persistQuickState();
      if (task.status === 'done') {
        this.handleAsrDone(task);
      } else if (task.status === 'failed') {
        this.handleAsrFailed(task.message);
      } else {
        this.startPoll(task.taskId);
      }
    } catch (e) {
      this.setData({ uploading: false, polling: false, extracting: false, processText: '上传失败，请重试' });
      wx.showToast({ title: e.message || '上传失败', icon: 'none' });
    }
  },

  startPoll(taskId) {
    this.clearPoll();
    this._pollCount = 0;
    this._pollTimer = setInterval(async () => {
      this._pollCount += 1;
      if (this._pollCount > MAX_POLL_COUNT) {
        this.clearPoll();
        this.setData({ polling: false, processText: '转写超时，请稍后重试' });
        wx.showToast({ title: '转写超时', icon: 'none' });
        return;
      }
      try {
        const task = await api.committeeQuickRecordingStatus(this.meetingId, taskId);
        this.setData({
          asrStatus: task.status || '',
          processText: this.statusText(task.status, task.message)
        });
        this.persistQuickState();
        if (task.status === 'done') this.handleAsrDone(task);
        if (task.status === 'failed') this.handleAsrFailed(task.message);
      } catch (e) {
        this.clearPoll();
        this.setData({ polling: false, processText: '查询转写状态失败' });
        wx.showToast({ title: e.message || '查询失败', icon: 'none' });
      }
    }, POLL_INTERVAL);
  },

  statusText(status, message) {
    if (status === 'done') return '转写完成，正在抽取议题与表决提示...';
    if (status === 'failed') return message || '转写失败';
    if (status === 'processing') return '录音转写中...';
    if (status === 'pending') return '转写任务已提交，等待处理...';
    if (status === 'uploading') return '正在上传录音...';
    return '正在处理录音...';
  },

  async handleAsrDone(task) {
    if (this._asrDoneHandled) return;
    this._asrDoneHandled = true;
    this.clearPoll();
    this.setData({
      uploading: false,
      polling: false,
      extracting: true,
      asrStatus: task.status || 'done',
      processText: '转写完成，正在抽取议题与表决提示...'
    });
    this.persistQuickState();
    try {
      const extraction = await api.committeeQuickExtract(this.meetingId);
      const mapped = this.mapExtraction(extraction);
      this.setData({
        extraction,
        presetTopics: mapped.presetTopics,
        aiTopics: mapped.aiTopics,
        extracting: false,
        generated: true,
        finishText: '转写完成，已生成转写原文与待确认的议题'
      });
      this.persistQuickState();
    } catch (e) {
      this.setData({ extracting: false, processText: '规则抽取失败' });
      wx.showToast({ title: e.message || '抽取失败', icon: 'none' });
      return;
    }

    // 转写原文：best-effort，失败不影响主流程
    try {
      if (typeof api.committeeQuickTranscript === 'function') {
        const transcript = await api.committeeQuickTranscript(this.meetingId);
        const mappedTranscript = this.mapTranscript(transcript);
        this.setData(Object.assign({ transcript: mappedTranscript }, this.buildTranscriptState(mappedTranscript)));
        this.persistQuickState();
      }
    } catch (e) { /* 忽略：原文展示是附加功能 */ }
  },

  // 转写结果（说话人+时间+文本）转成可渲染列表
  mapTranscript(asr) {
    const segs = (asr && asr.segments) || [];
    return segs.map((s, i) => ({
      id: i,
      speaker: s.speaker || '',
      time: this.fmt(Math.floor((s.startMs || 0) / 1000)),
      text: s.text || ''
    }));
  },

  buildTranscriptState(transcript) {
    const lines = (transcript || []).map(function (seg) {
      var prefix = [seg.time, seg.speaker].filter(Boolean).join(' ');
      return (prefix ? prefix + '：' : '') + (seg.text || '');
    }).filter(Boolean);
    const fullText = lines.join('\n');
    const compact = fullText.replace(/\s+/g, ' ').trim();
    return {
      transcriptFullText: fullText,
      transcriptPreview: compact.length > 92 ? compact.slice(0, 92) + '...' : compact,
      transcriptCharCount: compact.length
    };
  },

  openTranscript(e) {
    const mode = e && e.currentTarget && e.currentTarget.dataset.mode
      ? e.currentTarget.dataset.mode
      : 'short';
    this.setData({ transcriptVisible: true, transcriptMode: mode });
  },

  closeTranscript() {
    this.setData({ transcriptVisible: false });
  },

  switchTranscriptMode(e) {
    this.setData({ transcriptMode: e.currentTarget.dataset.mode || 'short' });
  },

  handleAsrFailed(message) {
    this.clearPoll();
    this.setData({
      uploading: false,
      polling: false,
      extracting: false,
      generated: false,
      processText: message || '转写失败'
    });
    wx.showToast({ title: message || '转写失败', icon: 'none' });
  },

  mapExtraction(extraction) {
    const hitList = (extraction && extraction.presetTopicHits) || [];
    const candidateList = (extraction && extraction.candidateTopics) || [];
    const fallbackPreset = this.data.presetTopics || [];
    const voteTotal = this.data.voteTotal || 0;
    const presetTopics = hitList.length
      ? hitList.map(hit => mapTopic(hit, null, voteTotal))
      : fallbackPreset.map(t => Object.assign({}, t, { confirmed: false }));
    const aiTopics = candidateList.map(hit => mapTopic(hit, null, voteTotal));
    return { presetTopics, aiTopics };
  },

  goConfirm() {
    if (!this.data.generated) return;
    this.setData({ currentStep: 4 });
    this.persistQuickState();
  },

  toggleConfirm(e) {
    const ds = e.currentTarget.dataset;
    const key = ds.group === 'ai' ? 'aiTopics' : 'presetTopics';
    const list = this.data[key].slice();
    const idx = Number(ds.idx);
    const item = list[idx];
    if (item && item.voteRequired && !item.confirmed) {
      const counted = Number(item.voteCounted) || 0;
      if (!this.data.voteTotal || counted !== this.data.voteTotal) {
        wx.showToast({ title: '请先填写与实到人数一致的票数', icon: 'none' });
        return;
      }
    }
    list[idx] = Object.assign({}, list[idx], { confirmed: !list[idx].confirmed });
    this.setData({ [key]: list });
    this.persistQuickState();
    this.saveQuickConfirmToServer(true);
  },

  // 列表点议题 → 打开全屏详情面板
  openTopicDetail(e) {
    const ds = e.currentTarget.dataset;
    this.setData({ detailOpen: true, detailGroup: ds.group, detailIndex: Number(ds.idx) });
  },
  closeTopicDetail() {
    this.setData({ detailOpen: false });
  },

  // 采纳疑似新议题：主任选类型 → 调用现场添加议题接口建真实议题 → 并入正式议题列表
  adoptCandidate(e) {
    if (!this.data.isChair) { wx.showToast({ title: '仅主任/副主任可立项', icon: 'none' }); return; }
    const idx = Number(e.currentTarget.dataset.idx);
    const cand = (this.data.aiTopics || [])[idx];
    if (!cand) return;
    const choices = [
      { label: '通报事项', type: 'notice', decisionType: 'none', voteRequired: false },
      { label: '讨论事项', type: 'discussion', decisionType: 'none', voteRequired: false },
      { label: '表决事项', type: 'decision', decisionType: 'simple', voteRequired: true }
    ];
    wx.showActionSheet({
      itemList: choices.map(function (c) { return c.label; }),
      success: async (res) => {
        const c = choices[res.tapIndex];
        if (!c) return;
        try {
          const created = await api.committeeAddTopic(this.meetingId, cand.title, c.type, c.decisionType, null, false);
          const realId = created && created.id ? created.id : cand.id;
          const adopted = decorateEvidence(Object.assign({}, cand, {
            id: realId,
            type: c.type,
            typeLabel: topicTypeLabel(c.type),
            voteRequired: c.voteRequired,
            suggest: c.voteRequired ? voteHintText(null) : '记录要点',
            result: 'unclear',
            resultLabel: resultLabel('unclear'),
            voteFor: 0, voteAgainst: 0, voteAbstain: 0, voteCounted: 0,
            confirmed: false
          }));
          const presetTopics = (this.data.presetTopics || []).concat([adopted]);
          const aiTopics = (this.data.aiTopics || []).slice();
          aiTopics.splice(idx, 1);
          this.setData({ presetTopics: presetTopics, aiTopics: aiTopics, detailOpen: false });
          this.persistQuickState();
          wx.showToast({ title: '已立项为' + c.label, icon: 'success' });
        } catch (err) {
          wx.showToast({ title: err.message || '立项失败', icon: 'none' });
        }
      }
    });
  },

  // 按需：让大模型把该议题命中片段整理成书面正文，填进 summaryDraft（主要用于通报/讨论）
  async summarizeTopic(e) {
    const ds = e.currentTarget.dataset;
    const key = ds.group === 'ai' ? 'aiTopics' : 'presetTopics';
    const idx = Number(ds.idx);
    const list = this.data[key].slice();
    const t = list[idx];
    if (!t || t.summarizing) return;
    const segmentIndexes = (t.segmentMatches || []).map(function (s) { return s.segmentIndex; });
    const segmentTexts = (t.segmentMatches || []).map(function (s) {
      return [s.time, s.speaker, s.text].filter(Boolean).join(' ');
    }).filter(Boolean);
    if (!segmentIndexes.length) { wx.showToast({ title: '暂无可整理的片段', icon: 'none' }); return; }
    list[idx] = Object.assign({}, t, { summarizing: true });
    this.setData({ [key]: list });
    try {
      const task = await api.committeeQuickTopicSummaryTask(this.meetingId, {
        title: t.title, type: t.type, segmentIndexes: segmentIndexes, segmentTexts: segmentTexts
      });
      if (!task || !task.taskId) throw new Error('未获取到生成任务');
      const taskKey = key + ':' + idx;
      this.clearTopicReportPoll(taskKey);
      this.pollTopicSummaryTask(taskKey, key, idx, task.taskId, 0);
      wx.showToast({ title: '已开始生成', icon: 'none' });
      return;
    } catch (err) {
      const next = this.data[key].slice();
      next[idx] = Object.assign({}, next[idx], { summarizing: false });
      this.setData({ [key]: next });
      wx.showToast({ title: err.message || '生成失败', icon: 'none' });
    }
  },

  // 忽略疑似新议题：移出待研判区，不进纪要
  pollTopicSummaryTask(taskKey, key, idx, taskId, count) {
    const self = this;
    this._topicReportTimers = this._topicReportTimers || {};
    const tick = async function () {
      try {
        const task = await api.committeeQuickTopicSummaryTaskStatus(self.meetingId, taskId);
        const list = (self.data[key] || []).slice();
        if (!list[idx]) {
          self.clearTopicReportPoll(taskKey);
          return;
        }
        if (task && task.status === 'succeeded') {
          list[idx] = Object.assign({}, list[idx], {
            summarizing: false,
            summaryDraft: task.result || list[idx].summaryDraft,
            summaryAi: !!task.result,
            summaryTaskId: taskId
          });
          self.setData({ [key]: list });
          self.persistQuickState();
          self.clearTopicReportPoll(taskKey);
          wx.showToast({ title: '已生成', icon: 'success' });
          return;
        }
        if (task && task.status === 'failed') {
          list[idx] = Object.assign({}, list[idx], { summarizing: false, summaryTaskId: taskId });
          self.setData({ [key]: list });
          self.persistQuickState();
          self.clearTopicReportPoll(taskKey);
          wx.showToast({ title: task.message || '生成失败', icon: 'none' });
          return;
        }
        if (count >= TOPIC_REPORT_MAX_POLL) {
          list[idx] = Object.assign({}, list[idx], { summarizing: false, summaryTaskId: taskId });
          self.setData({ [key]: list });
          self.persistQuickState();
          self.clearTopicReportPoll(taskKey);
          wx.showToast({ title: '生成时间较长，请稍后重试', icon: 'none' });
          return;
        }
        self._topicReportTimers[taskKey] = setTimeout(function () {
          self.pollTopicSummaryTask(taskKey, key, idx, taskId, count + 1);
        }, TOPIC_REPORT_POLL_INTERVAL);
      } catch (err) {
        if (count >= 3) {
          const list = (self.data[key] || []).slice();
          if (list[idx]) {
            list[idx] = Object.assign({}, list[idx], { summarizing: false, summaryTaskId: taskId });
            self.setData({ [key]: list });
            self.persistQuickState();
          }
          self.clearTopicReportPoll(taskKey);
          wx.showToast({ title: err.message || '查询生成状态失败', icon: 'none' });
          return;
        }
        self._topicReportTimers[taskKey] = setTimeout(function () {
          self.pollTopicSummaryTask(taskKey, key, idx, taskId, count + 1);
        }, TOPIC_REPORT_POLL_INTERVAL);
      }
    };
    tick();
  },

  ignoreCandidate(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const aiTopics = (this.data.aiTopics || []).slice();
    if (idx < 0 || idx >= aiTopics.length) return;
    aiTopics.splice(idx, 1);
    this.setData({ aiTopics: aiTopics, detailOpen: false });
    this.persistQuickState();
  },

  pickTopicResult(e) {
    const ds = e.currentTarget.dataset;
    const key = ds.group === 'ai' ? 'aiTopics' : 'presetTopics';
    const list = this.data[key].slice();
    const idx = Number(ds.idx);
    const result = ds.result || 'unclear';
    list[idx] = Object.assign({}, list[idx], {
      result: result,
      resultLabel: resultLabel(result),
      confirmed: false
    });
    this.setData({ [key]: list });
    this.persistQuickState();
  },

  recalcVoteTopic(topic) {
    const voteFor = Number(topic.voteFor) || 0;
    const voteAgainst = Number(topic.voteAgainst) || 0;
    const voteAbstain = Number(topic.voteAbstain) || 0;
    const counted = voteFor + voteAgainst + voteAbstain;
    const need = this.data.voteNeed || 1;
    const total = this.data.voteTotal || 0;
    let result = 'unclear';
    if (voteFor >= need) result = 'passed';
    else if (total > 0 && counted >= total) result = 'rejected';
    return Object.assign({}, topic, {
      voteFor,
      voteAgainst,
      voteAbstain,
      voteCounted: counted,
      result,
      resultLabel: resultLabel(result),
      confirmed: false
    });
  },

  changeVoteCount(e) {
    const ds = e.currentTarget.dataset;
    const key = ds.group === 'ai' ? 'aiTopics' : 'presetTopics';
    const idx = Number(ds.idx);
    const field = ds.field;
    const delta = Number(ds.delta) || 0;
    const list = this.data[key].slice();
    const topic = Object.assign({}, list[idx]);
    const current = Number(topic[field]) || 0;
    const next = Math.max(0, current + delta);
    const total = this.data.voteTotal || 0;
    const otherTotal = (Number(topic.voteFor) || 0) + (Number(topic.voteAgainst) || 0) + (Number(topic.voteAbstain) || 0) - current;
    if (total > 0 && otherTotal + next > total) {
      wx.showToast({ title: '票数不能超过实到人数', icon: 'none' });
      return;
    }
    topic[field] = next;
    list[idx] = this.recalcVoteTopic(topic);
    this.setData({ [key]: list });
    this.persistQuickState();
  },

  refreshTopicMatchMeta(topic) {
    const matches = topic.segmentMatches || [];
    const maxScore = matches.reduce(function (max, seg) {
      return typeof seg.score === 'number' ? Math.max(max, seg.score) : max;
    }, -1);
    return decorateEvidence(Object.assign({}, topic, {
      matchedSegments: matches.map(function (seg) { return seg.segmentIndex; }),
      matchedCount: matches.length,
      matchConfidence: maxScore >= 0 ? maxScore : null,
      reviewLevel: matches.length ? 'review' : 'empty',
      reviewLevelLabel: reviewLevelLabel(matches.length ? 'review' : 'empty'),
      confirmed: false
    }));
  },

  toggleTopicEvidence(e) {
    const ds = e.currentTarget.dataset;
    const key = ds.group === 'ai' ? 'aiTopics' : 'presetTopics';
    const idx = Number(ds.idx);
    const list = this.data[key].slice();
    if (!list[idx]) return;
    const topic = Object.assign({}, list[idx], { evidenceExpanded: !list[idx].evidenceExpanded });
    list[idx] = decorateEvidence(topic);
    this.setData({ [key]: list });
    this.persistQuickState();
  },

  removeMatchedSegment(e) {
    const ds = e.currentTarget.dataset;
    const key = ds.group === 'ai' ? 'aiTopics' : 'presetTopics';
    const topicIndex = Number(ds.topicIndex);
    const segmentIndex = Number(ds.segmentIndex);
    const topics = this.data[key].slice();
    const topic = Object.assign({}, topics[topicIndex]);
    const matches = (topic.segmentMatches || []).slice();
    topic.segmentMatches = matches.filter(function (seg) { return Number(seg.segmentIndex) !== segmentIndex; });
    topics[topicIndex] = this.refreshTopicMatchMeta(topic);
    this.setData({ [key]: topics });
    this.persistQuickState();
  },

  buildConfirmPayload() {
    return {
      topics: (this.data.presetTopics || []).map(function (t) {
        return {
          topicId: t.id,
          result: t.voteRequired ? t.result : 'recorded',
          confirmed: t.confirmed,
          summaryDraft: t.summaryDraft || '',
          segmentIndexes: (t.segmentMatches || []).map(function (seg) { return seg.segmentIndex; }),
          forVotes: t.voteRequired ? (Number(t.voteFor) || 0) : null,
          agVotes: t.voteRequired ? (Number(t.voteAgainst) || 0) : null,
          abVotes: t.voteRequired ? (Number(t.voteAbstain) || 0) : null,
          totalVotes: t.voteRequired ? (Number(t.voteCounted) || 0) : null
        };
      }),
      ignoredSegmentIndexes: []
    };
  },

  allPresetTopicsConfirmed() {
    const topics = this.data.presetTopics || [];
    return topics.length > 0 && topics.every(function (t) { return !!t.confirmed; });
  },

  async saveQuickConfirmToServer(silent) {
    if (!this.data.isChair || this.data.currentStep !== 4 || typeof api.committeeQuickConfirm !== 'function') return false;
    try {
      await api.committeeQuickConfirm(this.meetingId, this.buildConfirmPayload());
      return true;
    } catch (e) {
      if (!silent) wx.showToast({ title: e.message || '同步确认失败', icon: 'none' });
      return false;
    }
  },

  async viewMinutes() {
    if (this.data.isChair && this.data.currentStep === 4) {
      const payload = this.buildConfirmPayload();
      let syncError = null;
      wx.showLoading({ title: '生成草稿中', mask: true });
      try {
        await api.committeeQuickConfirm(this.meetingId, payload);
        if (typeof api.committeeQuickPolish === 'function') {
          await api.committeeQuickPolish(this.meetingId, payload);
        }
      } catch (e) {
        syncError = e;
      } finally {
        wx.hideLoading();
      }
      if (syncError) {
        wx.showToast({ title: syncError.message || '草稿刷新失败，先查看旧稿', icon: 'none' });
      }
    }
    wx.navigateTo({ url: '/pages/minutes/minutes?meetingId=' + this.meetingId + '&from=meeting-live-quick' });
  },

  endMeeting() {
    if (!this.data.isChair) {
      wx.showToast({ title: '仅主任/副主任可确认表决并结束', icon: 'none' });
      return;
    }
    const unconfirmed = (this.data.presetTopics || []).filter(function (t) { return !t.confirmed; });
    const pendingCandidates = (this.data.aiTopics || []).length;
    if (unconfirmed.length) {
      wx.showToast({ title: '请先确认全部议题', icon: 'none' });
      return;
    }
    let warn = '';
    if (pendingCandidates) warn += '还有 ' + pendingCandidates + ' 个疑似新议题未研判（需采纳或忽略）；';
    wx.showModal({
      title: '结束会议',
      content: warn ? (warn + '可继续生成纪要并结束，但这些内容不会进入会议纪要。') : '确认生成正式会议纪要并结束本次会议？系统将调用大模型根据会议内容润色纪要。',
      confirmText: '生成并结束',
      cancelText: '再想想',
      success: async (res) => {
        if (!res.confirm) return;
        this.setData({ ending: true });
        try {
          const confirmPayload = this.buildConfirmPayload();
          await api.committeeQuickPolish(this.meetingId, confirmPayload);
          await api.committeeAdvance(this.meetingId, 'end');
          this.clearQuickState();
          wx.showToast({ title: '纪要已生成', icon: 'success' });
          setTimeout(() => wx.navigateBack(), 600);
        } catch (e) {
          this.setData({ ending: false });
          wx.showToast({ title: e.message || '操作失败', icon: 'none' });
        }
      }
    });
  },

  // 主任兜底：重置录音人指示（清空后由下一个开始录音的人接管显示）
  resetRecorder() {
    if (!this.data.isChair) {
      wx.showToast({ title: '仅主任/副主任可重置录音人', icon: 'none' });
      return;
    }
    wx.showModal({
      title: '重置录音人',
      content: '重置后当前录音人被清空，可由其他人重新开始录音。注意：已录制的片段需由原设备自行上传，换人需重新录制。',
      confirmText: '确认重置',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await api.committeeResetRecorder(this.meetingId);
          wx.showToast({ title: '已重置', icon: 'success' });
          this.loadDetail();
        } catch (e) { wx.showToast({ title: e.message || '操作失败', icon: 'none' }); }
      }
    });
  },

  // ── 会议资料：任意已签到参会人可上传/查看 ──
  formatSize(bytes) {
    if (!bytes) return '';
    if (bytes < 1024) return bytes + 'B';
    if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + 'KB';
    return (bytes / 1024 / 1024).toFixed(1) + 'MB';
  },

  uploadMaterial() {
    if (!this.data.signedIn) {
      wx.showToast({ title: '请先确认参会', icon: 'none' });
      return;
    }
    const that = this;
    wx.chooseMessageFile({
      count: 3,
      type: 'file',
      success: function (res) {
        const files = (res.tempFiles || []).map(function (f) {
          return { name: f.name || ('资料_' + Date.now()), sizeText: that.formatSize(f.size) };
        });
        Promise.all(files.map(function (f) {
          return api.committeeAddMaterial(that.meetingId, f.name, f.sizeText);
        })).then(function () {
          wx.showToast({ title: '已上传 ' + files.length + ' 份', icon: 'success' });
          that.loadDetail();
        }).catch(function (err) {
          wx.showToast({ title: err.message || '上传失败', icon: 'none' });
        });
      }
    });
  },

  previewMaterial(e) {
    const idx = e.currentTarget.dataset.index;
    const materials = this.data.materials || [];
    const m = materials[idx];
    if (!m) return;
    wx.showModal({ title: m.name, content: m.content || '（暂无预览内容）', showCancel: false, confirmText: '关闭' });
  },

  // ── 实时添加议题（主任/副主任）──
  openAddTopic() {
    if (!this.data.isChair) {
      wx.showToast({ title: '仅主任/副主任可添加议题', icon: 'none' });
      return;
    }
    this.setData({ addTopicVisible: true, newTopicForm: { title: '', decisionType: 'simple', type: 'decision', optionsText: '' } });
  },
  closeAddTopic() { this.setData({ addTopicVisible: false }); },
  onTopicFormInput(e) {
    this.setData({ ['newTopicForm.' + e.currentTarget.dataset.field]: e.detail.value });
  },
  pickDecisionType(e) {
    this.setData({ 'newTopicForm.decisionType': e.currentTarget.dataset.type });
  },
  async submitAddTopic() {
    const f = this.data.newTopicForm;
    if (!f.title.trim()) { wx.showToast({ title: '请输入议题名称', icon: 'none' }); return; }
    let optionsJson = null;
    if (f.decisionType === 'multi_choice' && f.optionsText.trim()) {
      const labels = f.optionsText.split('\n').filter(function (l) { return l.trim(); });
      optionsJson = JSON.stringify(labels.map(function (l, i) { return { id: i + 1, label: l.trim() }; }));
    }
    try {
      // 现场新增只允许通报/讨论/决定，重大事项后端会拦截
      await api.committeeAddTopic(this.meetingId, f.title.trim(), f.type, f.decisionType, optionsJson, false);
      wx.showToast({ title: '议题已添加', icon: 'success' });
      this.setData({ addTopicVisible: false });
      this.loadDetail();
    } catch (e) { wx.showToast({ title: e.message || '添加失败', icon: 'none' }); }
  },

  // ── 居委会签字 / 线下佐证（结束步）──
  async toggleJuwei() {
    try { await api.committeeToggleJuwei(this.meetingId); this.loadDetail(); }
    catch (e) { wx.showToast({ title: e.message || '操作失败', icon: 'none' }); }
  },
  addEvidence() {
    const that = this;
    wx.chooseImage({
      count: 1, sizeType: ['compressed'], sourceType: ['album', 'camera'],
      success: async function () {
        const name = 'IMG_' + new Date().getTime() + '.jpg';
        try { await api.committeeAddEvidence(that.meetingId, name, '照片'); that.loadDetail(); }
        catch (e) { wx.showToast({ title: e.message || '上传失败', icon: 'none' }); }
      }
    });
  },
  async removeEvidence(e) {
    const evId = e.currentTarget.dataset.evId;
    try { await api.committeeRemoveEvidence(this.meetingId, evId); this.loadDetail(); }
    catch (err) { wx.showToast({ title: err.message || '删除失败', icon: 'none' }); }
  },

  // ── 导出签到名单（写临时 CSV → 转发/复制）──
  async exportAttendance() {
    try {
      const res = await api.committeeExportAttendance(this.meetingId);
      const fs = wx.getFileSystemManager();
      const filePath = wx.env.USER_DATA_PATH + '/' + (res.fileName || 'attendance.csv');
      fs.writeFile({
        filePath: filePath, data: res.content || '', encoding: 'utf8',
        success: () => {
          if (wx.shareFileMessage) {
            wx.shareFileMessage({ filePath: filePath, fileName: res.fileName,
              fail: () => { wx.setClipboardData({ data: res.content || '' }); wx.showToast({ title: '名单已复制', icon: 'none' }); } });
          } else {
            wx.setClipboardData({ data: res.content || '' });
            wx.showToast({ title: '名单已复制', icon: 'none' });
          }
        },
        fail: () => { wx.setClipboardData({ data: res.content || '' }); wx.showToast({ title: '名单已复制', icon: 'none' }); }
      });
    } catch (e) { wx.showToast({ title: e.message || '导出失败', icon: 'none' }); }
  },

  noop() {},

  exitLive() {
    wx.navigateBack();
  }
});
