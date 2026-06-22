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
    reviseMode: false,
    hasServerMinutes: false,
    isPublished: false,
    isArchived: false,
    accessDenied: false,
    accessTitle: '',
    accessText: ''
  },

  onLoad(options) {
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
      reviseMode: false,
      isPublished: false,
      isArchived: false,
      accessDenied: false,
      accessTitle: '',
      accessText: ''
    });
    this.meetingId = id;
    if (id) isOwner ? this.loadOwnerMinutes(id) : this.loadMinutes(id);
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
        // 未公示未归档：可直接编辑；已公示或已归档：只能走修订版本
        canEditMinutes: this.data.isChair && !published && !archived && !minutes.draft,
        canReviseMinutes: this.data.isChair && (published || archived) && !minutes.draft
      });
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

  startEdit() {
    if (!this.data.canEditMinutes) {
      wx.showToast({ title: '已公示/已归档纪要需走修订', icon: 'none' });
      return;
    }
    this.setData({ editMode: true, reviseMode: false, editText: this.data.plainText });
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
      this.setData({ editMode: false, plainText: this.data.editText });
      this.setData({ hasServerMinutes: true });
      // 更新显示
      var minutes = this.data.minutes;
      if (minutes) minutes.draft = false;
      this.setData({ minutes: minutes });
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
  }
});
