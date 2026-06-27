const api = require('../../utils/api');
const perm = require('../../utils/perm');

Page({
  data: {
    minutes: null,
    plainText: '',
    editMode: false,
    editText: '',
    isChair: false,
    isOwner: false,
    isExternalRole: false,
    canEditMinutes: false,
    canReviseMinutes: false,
    canViewInternalArtifacts: false,
    reviseMode: false,
    hasServerMinutes: false,
    aiGenerating: false,   // AI 纪要后台生成中（显示"请稍候"提示）
    isPublished: false,
    isArchived: false,
    accessDenied: false,
    accessTitle: '',
    accessText: ''
  },

  onLoad(options) {
    console.log('[minutes] onLoad 构建标记=BUILD-B（结束按钮已修），options=', options);
    const id = parseInt(options.meetingId);
    const from = options.from || '';
    const isOwner = from === 'owner' || from === 'owner-detail';
    const isExternalRole = perm.isExternal();
    this.setData({
      isChair: perm.isChair() || perm.can('committee.publish'),
      isOwner: isOwner,
      isExternalRole: isExternalRole,
      canEditMinutes: false,
      canReviseMinutes: false,
      canViewInternalArtifacts: false,
      reviseMode: false,
      isPublished: false,
      isArchived: false,
      accessDenied: false,
      accessTitle: '',
      accessText: ''
    });
    this.meetingId = id;
    // gen=1：进入纪要页时直接调大模型生成纪要（整屏显示"生成中"，出文后展示）
    this.genAi = options.gen === '1';
    // 进入即先把界面切成"生成中"，避免 loadMinutes 期间闪现结构化/旧内容
    if (this.genAi) this.setData({ aiGenerating: true });
    // view=1（AI 生成跳转 / 「查看」链接）进入时先看正文；其它渠道进入则直接编辑
    this.viewFirst = options.view === '1';
    if (id) isOwner ? this.loadOwnerMinutes(id) : this.loadMinutes(id);
  },

  onUnload() {
    this._destroyed = true;
    if (this._aiTimer) { clearTimeout(this._aiTimer); this._aiTimer = null; }
  },

  // 轮询后端 AI 纪要：结束/一键生成后大模型在后台跑，跑完自动把正文换成 AI 版本
  startAwaitAiMinutes() {
    if (this._aiTimer) return;       // 避免重复启动
    this._aiTries = 0;
    const maxTries = 20;             // 最多约 20 × 6s = 120s，覆盖大模型较慢的情况
    const intervalMs = 6000;
    const tick = async () => {
      this._aiTimer = null;
      if (this._destroyed) return;
      this._aiTries++;
      try {
        const txt = await api.committeeMinutes(this.meetingId);
        if (txt && String(txt).trim()) {
          if (this._destroyed) return;
          this.setData({ plainText: txt, hasServerMinutes: true, aiGenerating: false });
          wx.showToast({ title: '会议纪要已生成', icon: 'none' });
          return;                    // 拿到 AI 正文，停止轮询
        }
      } catch (e) { /* 忽略，继续重试 */ }
      if (!this._destroyed && this._aiTries < maxTries) {
        this._aiTimer = setTimeout(tick, intervalMs);
      } else if (!this._destroyed) {
        // 超时仍未拿到：撤掉"生成中"提示，先展示已有(结构化)纪要，提示可稍后刷新
        this.setData({ aiGenerating: false });
        wx.showToast({ title: '生成较慢，可稍后下拉刷新查看', icon: 'none' });
      }
    };
    this._aiTimer = setTimeout(tick, intervalMs);
  },

  isPublicCommitteeMinutes(detail) {
    return detail && detail.stage === 'ended' && detail.compliance !== 'invalid' &&
      detail.publish && detail.publish.published;
  },

  isPublicOwnerMinutes(detail) {
    return detail && detail.stage === 'ended' && detail.compliance !== 'invalid' &&
      detail.publishInfo && detail.publishInfo.published;
  },

  denyAccess(title, text) {
    this.setData({
      accessDenied: true,
      accessTitle: title,
      accessText: text,
      minutes: null,
      plainText: '',
      editMode: false,
      aiGenerating: false,
      canEditMinutes: false,
      isPublished: false
    });
  },

  async loadMinutes(id) {
    try {
      const detail = await api.committeeDetail(id);
      if (!detail) return;
      const published = this.isPublicCommitteeMinutes(detail);
      if (this.data.isExternalRole && !published) {
        this.denyAccess('纪要待公示', '会议纪要公示后将作为正式公开文件展示。当前仍属于内部会议记录，暂不可查看。');
        return;
      }
      let serverText = '';
      try { serverText = await api.committeeMinutes(id); } catch (e) {}

      const record = detail.record || {};
      if (!record.attendances) {
        this.denyAccess('暂无可查看纪要', '当前会议记录尚未生成或暂不可公开。');
        return;
      }
      const attendances = record.attendances || [];
      const topics = record.topics || [];
      const total = attendances.length || 7;
      const need = Math.ceil(total / 2);
      const present = attendances.filter(a => a.signedIn);
      const absent = attendances.filter(a => !a.signedIn);
      const host = (attendances.find(a => a.role === '主任') || attendances[0] || {}).name || '';

      // 表决结论
      let resolutionLevel = 'none', resolutionText = '本次会议无表决议题';
      if (topics.length) {
        const passed = topics.filter(t => t.passed).length;
        if (passed === topics.length) { resolutionLevel = 'passed'; resolutionText = topics.length + '项议题全部通过'; }
        else if (passed > 0) { resolutionLevel = 'partial'; resolutionText = passed + '项通过/' + (topics.length - passed) + '项未通过'; }
        else { resolutionLevel = 'failed'; resolutionText = '均未通过，未形成有效决议'; }
      }

      // 结论
      let conclusionLevel = 'valid', conclusionText = '会议有效，记录已归档。';
      const signedInN = attendances.filter(a => a.signedIn).length;
      if (detail.stage !== 'ended') {
        conclusionLevel = 'valid';
        conclusionText = '会议进行中，纪要尚未定稿。';
      } else if (detail.compliance === 'invalid') {
        conclusionLevel = 'invalid';
        conclusionText = '会议无效，本次决议不生效。';
      } else if (detail.compliance === 'flawed') {
        conclusionLevel = 'flawed';
        conclusionText = '会议有效，但存在需说明的记录或决议事项。';
      }

      // 记录级别
      const checks = record.checks || [];
      const recordLevel = detail.stage !== 'ended' ? 'minor'
        : checks.every(c => c.ok) ? 'complete'
        : checks.filter(c => c.ok).length >= (checks.length - 1) ? 'minor' : 'incomplete';
      const recordText = recordLevel === 'complete' ? '记录已归档'
        : recordLevel === 'minor' ? '记录存在瑕疵' : '记录待补正';

      // 构建纪要对象
      const minutes = {
        draft: detail.stage !== 'ended',
        meetingTitle: detail.title,
        meetingDate: detail.meetingDate,
        meetingTime: detail.meetingTime,
        location: detail.location,
        host: host,
        totalMembers: total,
        presentCount: present.length,
        presentNames: present.map(a => a.name).join('、') || '无',
        absentNames: absent.map(a => a.name).join('、') || '',
        quorumMet: present.length >= need,
        description: detail.description,
        topics: topics,
        juweiName: record.hasMajorIssue ? record.juweiName : '',
        juweiSigned: record.juweiSigned,
        conclusionLevel: conclusionLevel,
        conclusionText: conclusionText,
        meetingValid: detail.compliance !== 'invalid',
        recordLevel: recordLevel,
        recordText: recordText,
        resolutionLevel: resolutionLevel,
        resolutionText: resolutionText,
        notes: detail.invalidNotes || [],
        evidences: record.evidences || [],
        published: published
      };

      // 生成纯文本用于复制
      const L = [];
      L.push('业主委员会会议纪要');
      L.push('');
      L.push('会议名称：' + detail.title);
      L.push('会议时间：' + detail.meetingDate + ' ' + detail.meetingTime);
      L.push('会议地点：' + detail.location);
      L.push('主持人：' + host);
      L.push('记录人：业委会委员');
      L.push('');
      L.push('一、参会情况');
      L.push('应到委员 ' + total + ' 人，实到 ' + present.length + ' 人，' + (present.length >= need ? '已过半，达到法定人数' : '未过半，未达法定人数') + '。');
      L.push('出席：' + (present.map(a => a.name).join('、') || '无'));
      if (absent.length) L.push('缺席：' + absent.map(a => a.name).join('、'));
      L.push('');
      L.push('二、会议议题');
      L.push(detail.description || '（无）');
      if (topics.length) {
        L.push('');
        L.push('三、表决情况');
        topics.forEach((t, i) => {
          L.push((i+1) + '. 【' + (t.type === 'major' ? '重大事项' : '决定事项') + '】' + t.title);
          L.push('   赞成 ' + t.forVotes + ' 票，反对 ' + t.agVotes + ' 票，弃权 ' + t.abVotes + ' 票（赞成需≥' + need + '）。表决结果：' + t.text + '。');
        });
      }
      L.push('');
      L.push((topics.length ? '四' : '三') + '、参会确认');
      L.push('本次会议经 ' + present.length + '/' + total + ' 名委员确认参会。');
      if (record.hasMajorIssue && record.juweiName) {
        L.push('重大事项' + (record.juweiSigned ? '已' : '尚未') + '由居委会委员（' + record.juweiName + '）签字。');
      }
      L.push('');
      L.push((topics.length ? '五' : '四') + '、会议结论');
      L.push(conclusionText);

      const archived = !!detail._archived;
      const hasServerMinutes = !!(serverText && String(serverText).trim());
      this.setData({
        minutes,
        plainText: serverText || L.join('\n'),
        hasServerMinutes,
        accessDenied: false,
        accessTitle: '',
        accessText: '',
        isPublished: published,
        isArchived: archived,
        canViewInternalArtifacts: !this.data.isExternalRole && !this.data.isOwner,
        // 未公示未归档：可直接编辑；已公示或已归档：只能走修订版本
        // 主任在未公示/未归档时即可编辑（含 AI 草稿生成后、会议还没结束时；后端 updateMinutes 只拦"已公示"）
        canEditMinutes: this.data.isChair && !published && !archived,
        canReviseMinutes: this.data.isChair && (published || archived) && !minutes.draft
      });
      // gen=1：在本页直接(重新)生成 AI 纪要——无论是否已有旧文本都重新调大模型，显示"生成中"，出文后展示
      if (this.genAi) {
        this.genAi = false;
        this.regenerateAiMinutes();
      } else if (this.data.canEditMinutes && !this.viewFirst) {
        // 编辑/核对/确认为主：可编辑且非"先看正文"入口时，进入即直接打开编辑器
        this.startEdit();
      }
    } catch (e) {
      console.log('Minutes load error', e);
      this.denyAccess('纪要暂不可查看', e.message || '请稍后再试。');
    }
  },

  async loadOwnerMinutes(id) {
    this.denyAccess('业主大会已停用', '当前产品仅供业委会内部使用，不再提供业主大会纪要。');
  },

  copyText() {
    wx.setClipboardData({ data: this.data.plainText, success() {
      wx.showToast({ title: '已复制' });
    }});
  },

  // (重新)生成 AI 会议纪要：在本页直接调大模型润色（用已保存的确认数据），显示"生成中"，出文后展示
  async regenerateAiMinutes() {
    if (this._genRunning) return;
    this._genRunning = true;
    this.setData({ aiGenerating: true });
    let txt = '';
    try {
      if (typeof api.committeeQuickPolish === 'function') {
        const r = await api.committeeQuickPolish(this.meetingId);
        txt = (r && (r.minutesMarkdown || r.minutes)) || '';
      }
    } catch (e) { /* 失败，下面再尝试取已存正文 */ }
    if (!txt) {
      try { txt = await api.committeeMinutes(this.meetingId); } catch (e) {}
    }
    this._genRunning = false;
    if (this._destroyed) return;
    if (txt && String(txt).trim()) {
      this.setData({ plainText: txt, hasServerMinutes: true, aiGenerating: false });
      wx.showToast({ title: '会议纪要已生成', icon: 'none' });
    } else {
      this.setData({ aiGenerating: false });
      wx.showModal({ title: '生成未完成', content: '大模型生成较慢或暂不可用，可稍后再点「重新生成」重试。', showCancel: false });
    }
  },

  // 会议进行中：在纪要页确认纪要无误并结束会议
  endMeetingFromMinutes() {
    if (!this.data.isChair) { wx.showToast({ title: '仅主任/副主任可结束会议', icon: 'none' }); return; }
    const that = this;
    // 真正执行结束：确认框正常弹出后走这里；极少数环境确认框弹不出时也兜底走这里
    // 结束后跳到会议详情页（公示页面）：主任可在此「发起公示」
    const goPublish = function () {
      wx.redirectTo({ url: '/pages/committee-detail/committee-detail?id=' + that.meetingId });
    };
    const doEnd = function () {
      wx.showLoading({ title: '正在结束…', mask: true });
      api.committeeAdvance(that.meetingId, 'end').then(function () {
        wx.hideLoading();
        wx.showToast({ title: '会议已结束', icon: 'success' });
        setTimeout(goPublish, 600);  // 让"会议已结束"提示显示一下，再跳到公示页
      }).catch(function (e) {
        wx.hideLoading();
        const msg = (e && e.message) || '';
        // 会议已是结束态（此前已结束 / 重复点击）：也直接去公示页
        const alreadyEnded = msg.indexOf('仅进行中') >= 0 || msg.indexOf('已结束') >= 0 || msg.indexOf('ended') >= 0;
        if (alreadyEnded) {
          wx.showToast({ title: '会议已是结束状态', icon: 'none' });
          setTimeout(goPublish, 600);
        } else {
          // 其它错误（会议不存在 / 权限 / 网络等）：弹出真实原因，便于排查，绝不静默
          wx.showModal({ title: '结束会议失败', content: msg || '请稍后重试，或返回上一页重新进入', showCancel: false });
        }
      });
    };
    wx.hideToast();  // 清掉可能残留的 toast（如"纪要已生成"），避免它把紧跟的确认框吞掉
    wx.showModal({
      title: '结束会议',
      content: '确认会议纪要无误，结束本次会议并归档吗？',
      confirmText: '确认并结束',
      cancelText: '再想想',
      success: function (res) { console.log('[minutes] 确认框已弹出并响应，confirm=', res.confirm); if (res.confirm) doEnd(); },
      fail: function (err) { console.log('[minutes] 确认框弹出失败，兜底直接结束：', err); doEnd(); }  // 确认框弹不出（极少数环境）→ 兜底直接结束，绝不"点了没反应"
    });
  },

  startEdit() {
    if (!this.data.canEditMinutes) {
      wx.showToast({ title: '已公示/已归档纪要需走修订', icon: 'none' });
      return;
    }
    // 已有正式纪要（AI 生成 / 人工保存）→ 预填正文继续编辑；尚无正式纪要（首次手写）→ 空白
    const seed = this.data.hasServerMinutes ? this.data.plainText : '';
    this.setData({ editMode: true, reviseMode: false, editText: seed });
  },

  // 发起修订：归档/公示后正式内容不可直接改，生成新版本（必填修订原因）
  startRevise() {
    if (!this.data.canReviseMinutes) return;
    this.setData({ editMode: true, reviseMode: true, editText: this.data.plainText });
  },

  cancelEdit() {
    this.setData({ editMode: false, reviseMode: false, editText: '' });
  },

  onEditTextInput(e) {
    this.setData({ editText: e.detail.value });
  },

  async saveEdit() {
    if (!this.data.editText.trim()) {
      wx.showToast({ title: '纪要内容不能为空', icon: 'none' });
      return;
    }
    // 修订模式：必填修订原因，原归档版本保留，生成新版本
    if (this.data.reviseMode) {
      wx.showModal({
        title: '发起修订',
        content: '原归档版本将保留，本次修改作为新版本生效。请填写修订原因。',
        editable: true,
        placeholderText: '请填写修订原因（必填）',
        success: async (res) => {
          if (!res.confirm) return;
          const reason = (res.content || '').trim();
          if (!reason) { wx.showToast({ title: '修订必须填写原因', icon: 'none' }); return; }
          try {
            await api.committeeUpdateMinutes(this.meetingId, this.data.editText, reason);
            wx.showToast({ title: '修订已生效', icon: 'success' });
            this.setData({ editMode: false, reviseMode: false, plainText: this.data.editText });
          } catch (e) {
            wx.showToast({ title: e.message || '修订失败', icon: 'none' });
          }
        }
      });
      return;
    }
    try {
      await api.committeeUpdateMinutes(this.meetingId, this.data.editText);
      wx.showToast({ title: '纪要已保存', icon: 'success' });
      // 注意：不要把 minutes.draft 改成 false。draft 反映"会议是否已结束"，
      // 仅由结束会议(advance 'end')决定；保存纪要不等于结束会议。否则绿色
      // "确认纪要无误，结束会议"按钮(canEditMinutes && minutes.draft)会在保存后消失。
      this.setData({ editMode: false, plainText: this.data.editText, hasServerMinutes: true });
    } catch (e) {
      wx.showToast({ title: e.message || '保存失败', icon: 'none' });
    }
  },

  async viewRevisions() {
    try {
      const list = await api.committeeMinutesRevisions(this.meetingId);
      if (!list || !list.length) { wx.showToast({ title: '暂无修订记录', icon: 'none' }); return; }
      const lines = list.map(function (r) {
        return 'v' + r.versionNo + ' · ' + (r.editorName || '—') + ' · ' + (r.createdAt || '') +
          (r.reason ? '\n  原因：' + r.reason : '');
      });
      wx.showModal({ title: '纪要修订历史', content: lines.join('\n'), showCancel: false, confirmText: '关闭' });
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  viewInternalTopicReport() {
    // 改为独立页展示，避免 showModal 截断长文本
    wx.navigateTo({ url: '/pages/minutes-internal/minutes-internal?meetingId=' + this.meetingId });
  },

  viewTodoList() {
    // 改为独立页结构化卡片展示，避免 showModal 截断/挤成一坨
    wx.navigateTo({ url: '/pages/minutes-todos/minutes-todos?meetingId=' + this.meetingId });
  }
});
