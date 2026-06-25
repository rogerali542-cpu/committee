const api = require('../../utils/api');

function timeStr(s) {
  const m = Math.floor(s / 60);
  return String(m).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
}

function topicResult(t) {
  if (t.voteRequired === false) return '已记录';
  if (t.passed || t.status === 'passed') return '通过';
  if (t.status === 'failed') return '未通过';
  return '待定';
}

function meetingResult(d) {
  if (d.compliance === 'invalid') return '会议无效';
  if (d.compliance === 'flawed') return '会议有效（有说明）';
  if (d.compliance === 'valid') return '会议有效';
  return '会议已结束';
}

Page({
  data: {
    meetingId: null,
    loading: true,
    title: '',
    dateText: '',
    location: '',
    description: '',
    stage: '',            // preparing / ongoing / ended
    signedIn: false,
    declined: false,      // 因故缺席
    materials: [],
    hasMaterials: false,

    // 录音
    recording: false,
    recorderStarted: false,
    hasRecording: false,
    tempFilePath: '',
    seconds: 0,
    timeText: '00:00',
    uploading: false,
    recordings: [],       // 已上传的录音列表

    // 已结束
    resultText: '',
    topics: []
  },

  onLoad(options) {
    this.meetingId = parseInt(options.id || options.meetingId);
    this.setData({ meetingId: this.meetingId });
    this.initRecorder();
  },

  onShow() {
    if (this.meetingId) this.loadDetail();
  },

  onUnload() {
    this.clearTimer();
    if (this.data.recording && this.recorder) {
      try { this.recorder.stop(); } catch (e) {}
    }
  },

  // ── 录音管理 ──

  initRecorder() {
    if (!wx.getRecorderManager) return;
    const recorder = wx.getRecorderManager();
    this.recorder = recorder;

    recorder.onStart(() => {
      this.startTimer();
      this.setData({
        recording: true,
        recorderStarted: true,
        hasRecording: true,
        tempFilePath: ''
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
      this.setData({ recording: false, recorderStarted: false, uploading: false });
      wx.showToast({ title: (err && err.errMsg) || '录音失败', icon: 'none' });
    });
  },

  startTimer() {
    this.clearTimer();
    this._timer = setInterval(() => {
      const s = this.data.seconds + 1;
      this.setData({ seconds: s, timeText: timeStr(s) });
    }, 1000);
  },

  clearTimer() {
    if (this._timer) {
      clearInterval(this._timer);
      this._timer = null;
    }
  },

  toggleRecord() {
    if (!this.data.signedIn) {
      wx.showToast({ title: '请先确认参会', icon: 'none' });
      return;
    }
    if (!this.recorder) {
      wx.showToast({ title: '当前微信版本不支持录音', icon: 'none' });
      return;
    }
    if (this.data.uploading) return;

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
        content: '已有一段录音，重新开始会覆盖。是否继续？',
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

  startRecord() {
    this.clearTimer();
    this.setData({ seconds: 0, timeText: '00:00', hasRecording: false, tempFilePath: '' });
    this.recorder.start({
      duration: 2 * 60 * 60 * 1000,
      sampleRate: 16000,
      numberOfChannels: 1,
      encodeBitRate: 48000,
      format: 'mp3'
    });
  },

  finishRecord() {
    if (!this.data.signedIn) {
      wx.showToast({ title: '请先确认参会', icon: 'none' });
      return;
    }
    if (!this.data.hasRecording && !this.data.tempFilePath) {
      wx.showToast({ title: '请先开始录音', icon: 'none' });
      return;
    }
    if (this.data.uploading) return;

    if (this.data.recorderStarted) {
      this._pendingUploadAfterStop = true;
      this.recorder.stop();
      return;
    }
    if (this.data.tempFilePath) {
      this.uploadRecording(this.data.tempFilePath);
    }
  },

  chooseAudioFile() {
    if (!this.data.signedIn) {
      wx.showToast({ title: '请先确认参会', icon: 'none' });
      return;
    }
    if (this.data.uploading) return;
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
    this.setData({ uploading: true });
    try {
      const result = await api.committeeUploadRecording(this.meetingId, filePath);
      wx.showToast({ title: '录音已上传', icon: 'success' });
      this.setData({ uploading: false, seconds: 0, timeText: '00:00', hasRecording: false, tempFilePath: '' });
      this.loadDetail(); // 刷新录音列表
    } catch (e) {
      this.setData({ uploading: false });
      wx.showToast({ title: e.message || '上传失败', icon: 'none' });
    }
  },

  // ── 会议详情 ──

  async loadDetail() {
    try {
      const d = await api.committeeDetail(this.meetingId);
      const record = d.record || {};
      const me = (record.attendances || []).find(a => a.isSelf) || {};
      const materials = d.materials || [];
      const topics = (record.topics || []).map(t => ({
        title: t.title,
        resultText: topicResult(t)
      }));
      // 录音列表
      const recordings = record.recordings || [];
      this.setData({
        loading: false,
        title: d.title || '',
        dateText: ((d.meetingDate || '') + ' ' + (d.meetingTime || '')).trim(),
        location: d.location || '',
        description: d.description || '',
        stage: d.stage,
        signedIn: !!me.signedIn,
        declined: !!me.declined,
        materials: materials,
        hasMaterials: materials.length > 0,
        topics: topics,
        resultText: meetingResult(d),
        recordings: recordings
      });
    } catch (e) {
      this.setData({ loading: false });
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  confirmAttend() {
    if (this.data.signedIn) return;
    wx.showModal({
      title: '确认参加',
      content: '确认参加本次会议？',
      confirmText: '确认参加',
      confirmColor: '#FFA800',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await api.committeeSelfToggle(this.meetingId, 'signedIn');
          this.setData({ signedIn: true, declined: false });
          wx.showToast({ title: '已确认参加', icon: 'success' });
        } catch (e) {
          wx.showToast({ title: (e && e.message) || '操作失败', icon: 'none' });
        }
      }
    });
  },

  declineAttend() {
    if (this.data.signedIn) return;
    wx.showModal({
      title: '无法参会',
      content: '确认本次会议无法参加？将登记为"因故缺席"。',
      confirmText: '无法参会',
      cancelText: '再想想',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await api.committeeSelfToggle(this.meetingId, 'declined');
          this.setData({ declined: true, signedIn: false });
          wx.showToast({ title: '已登记：无法参会', icon: 'none' });
        } catch (e) {
          wx.showToast({ title: (e && e.message) || '操作失败', icon: 'none' });
        }
      }
    });
  },

  cancelAttend() {
    if (!this.data.signedIn) return;
    wx.showModal({
      title: '取消参加',
      content: '确定取消参加本次会议？',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await api.committeeSelfToggle(this.meetingId, 'cancel');
          this.setData({ signedIn: false, declined: false });
        } catch (e) {
          wx.showToast({ title: (e && e.message) || '操作失败', icon: 'none' });
        }
      }
    });
  },

  viewMaterials() {
    const names = this.data.materials
      .map((m, i) => (i + 1) + '. ' + (m.fileName || m.name || '材料'))
      .join('\n');
    wx.showModal({ title: '会议材料', content: names || '暂无材料', showCancel: false });
  },

  viewResult() {
    wx.navigateTo({ url: '/pages/minutes-public/minutes-public?meetingId=' + this.meetingId });
  },

  // 试听录音
  togglePlay(e) {
    const idx = e.currentTarget.dataset.index;
    const rec = this.data.recordings[idx];
    if (!rec || !rec.recordingUrl) {
      wx.showToast({ title: '录音暂不可用', icon: 'none' });
      return;
    }
    if (this._audioCtx && this._playingIdx === idx) {
      this._audioCtx.destroy();
      this._audioCtx = null;
      this.setData({ playingIdx: -1 });
      return;
    }
    if (this._audioCtx) this._audioCtx.destroy();
    const ctx = wx.createInnerAudioContext();
    ctx.src = rec.recordingUrl;
    ctx.onPlay(() => this.setData({ playingIdx: idx }));
    ctx.onStop(() => this.setData({ playingIdx: -1 }));
    ctx.onEnded(() => this.setData({ playingIdx: -1 }));
    ctx.onError(() => {
      this.setData({ playingIdx: -1 });
      wx.showToast({ title: '播放失败', icon: 'none' });
    });
    ctx.play();
    this._audioCtx = ctx;
    this._playingIdx = idx;
  },

  noop() {}
});
