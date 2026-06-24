// 归档详情（只读）—— 资料库点进来，展示会议/活动的全部信息：纪要 + 材料 + 佐证
const api = require('../../utils/api');
const perm = require('../../utils/perm');

const LEARNING_TYPE_LABEL = { internal: '内部学习', street: '街道培训', special: '专项学习' };
const COMPLIANCE_LABEL = { valid: '会议有效', flawed: '有效（带说明）', invalid: '会议无效' };

Page({
  data: {
    kind: '',
    view: null,
    loading: true
  },

  onLoad(options) {
    this.kind = options.kind || 'committee';
    this.id = parseInt(options.id);
    this.load();
  },

  async load() {
    this.setData({ loading: true });
    try {
      let view;
      if (this.kind === 'owner') {
        wx.showToast({ title: '业主大会模块已停用', icon: 'none' });
        wx.navigateBack();
        return;
      } else if (this.kind === 'learning') view = await this.buildLearning();
      else view = await this.buildCommittee();
      this.setData({ view, loading: false });
    } catch (e) {
      this.setData({ loading: false });
      wx.showToast({ title: e.message || '加载失败', icon: 'none' });
    }
  },

  async buildCommittee() {
    const d = await api.committeeDetail(this.id);
    const published = d.publish && d.publish.published;
    const withdrawn = d.publish && d.publish.withdrawn;
    const isChair = perm.isChair();
    return {
      title: d.title,
      sub: (d.meetingDate || '') + ' ' + (d.meetingTime || '') + (d.location ? ' · ' + d.location : ''),
      tags: [COMPLIANCE_LABEL[d.compliance] || '', published ? '已公示' : withdrawn ? '公示已撤回' : '已归档'].filter(Boolean),
      desc: '',
      hasMinutes: true, minutesFrom: 'library',
      materials: d.materials || [],
      archiveExtras: d.archiveExtras || [],
      evidences: (d.record && d.record.evidences) || [],
      signInText: '',
      canSupplement: isChair,
      // 归档管理（仅主任/副主任，委员会会议）
      isCommittee: true,
      isChair: isChair,
      published: !!published,
      withdrawn: !!withdrawn,
      withdrawReason: (d.publish && d.publish.withdrawReason) || '',
      archiveLog: d.archiveLog || [],
      minutesRevisionCount: d.minutesRevisionCount || 0
    };
  },

  async buildLearning() {
    const list = await api.learningList('', 'ended');
    const d = (list || []).find(x => x.id === this.id) || {};
    const signIns = d.signIns || {};
    const total = Object.keys(signIns).length;
    const signed = Object.keys(signIns).filter(k => signIns[k]).length;
    return {
      title: d.title || '',
      sub: (d.date || '') + ' ' + (d.time || '') + (d.location ? ' · ' + d.location : '') + (d.trainer ? ' · 讲师 ' + d.trainer : ''),
      tags: [LEARNING_TYPE_LABEL[d.type] || '学习', '已完成'],
      desc: d.description || '',
      hasMinutes: false, minutesFrom: '',
      materials: [],
      archiveExtras: [],
      evidences: d.evidences || [],
      signInText: total ? ('签到 ' + signed + '/' + total + ' 人') : '',
      canSupplement: false
    };
  },

  openMinutes() {
    const v = this.data.view;
    if (!v || !v.hasMinutes) return;
    wx.navigateTo({ url: '/pages/minutes/minutes?meetingId=' + this.id + '&from=' + v.minutesFrom });
  },

  previewMaterial(e) {
    const idx = e.currentTarget.dataset.index;
    const mt = this.data.view.materials[idx];
    if (!mt) return;
    wx.showModal({ title: mt.name, content: mt.content || '（暂无预览内容）', showCancel: false, confirmText: '关闭' });
  },

  previewArchiveExtra(e) {
    const idx = e.currentTarget.dataset.index;
    const item = this.data.view.archiveExtras[idx];
    if (!item) return;
    wx.showModal({
      title: item.fileName,
      content: '补充原因：' + (item.reason || '补充归档资料') + '\n补充人：' + (item.addedBy || '—') + '\n补充时间：' + (item.addedAt || '—'),
      showCancel: false,
      confirmText: '关闭'
    });
  },

  addArchiveExtra() {
    if (this.kind !== 'committee') return;
    var that = this;
    wx.showModal({
      title: '补充归档材料',
      content: '请填写补充原因。原归档内容不会被修改，补充材料会单独留痕。',
      editable: true,
      placeholderText: '例如：补充会议记录照片',
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
              await api.committeeAddArchiveExtra(that.id, name, that.formatSize(file.size), reason);
              wx.showToast({ title: '已补充归档', icon: 'success' });
              that.load();
            } catch (e) {
              wx.showToast({ title: e.message || '补充失败', icon: 'none' });
            }
          }
        });
      }
    });
  },

  // 撤回公示（必填原因，仍留在资料库，可重新公示或撤销归档）
  withdrawPublish() {
    var that = this;
    wx.showModal({
      title: '撤回公示',
      content: '撤回后业主将看到"已撤回"状态，会议仍在档。如需彻底撤出，请再走"撤销归档"。',
      editable: true,
      placeholderText: '请填写撤回原因（必填）',
      success: async function (res) {
        if (!res.confirm) return;
        var reason = (res.content || '').trim();
        if (!reason) { wx.showToast({ title: '撤回必须填写原因', icon: 'none' }); return; }
        try {
          await api.committeeWithdrawPublish(that.id, reason);
          wx.showToast({ title: '已撤回公示', icon: 'success' });
          that.load();
        } catch (e) { wx.showToast({ title: e.message || '撤回失败', icon: 'none' }); }
      }
    });
  },

  // 修订后重新公示
  async rePublish() {
    try {
      await api.committeePublish(this.id);
      wx.showToast({ title: '已重新公示', icon: 'success' });
      this.load();
    } catch (e) { wx.showToast({ title: e.message || '公示失败', icon: 'none' }); }
  },

  // 撤销归档（仅误归档用，必填原因；已公示需先撤回公示）
  revokeArchive() {
    var that = this;
    wx.showModal({
      title: '撤销归档',
      content: '仅用于误归档（归错会议/误点归档/未结束就归档）。撤销后会议撤出资料库，回到归档前阶段。',
      editable: true,
      placeholderText: '请填写撤销原因（必填）',
      success: async function (res) {
        if (!res.confirm) return;
        var reason = (res.content || '').trim();
        if (!reason) { wx.showToast({ title: '撤销必须填写原因', icon: 'none' }); return; }
        try {
          await api.committeeRevokeArchive(that.id, reason);
          wx.showToast({ title: '已撤销归档', icon: 'success' });
          setTimeout(function () { wx.navigateBack(); }, 600);
        } catch (e) { wx.showToast({ title: e.message || '撤销失败', icon: 'none' }); }
      }
    });
  },

  async viewMinutesRevisions() {
    try {
      var list = await api.committeeMinutesRevisions(this.id);
      if (!list || !list.length) { wx.showToast({ title: '暂无修订记录', icon: 'none' }); return; }
      var lines = list.map(function (r) {
        return 'v' + r.versionNo + ' · ' + (r.editorName || '—') + ' · ' + (r.createdAt || '') +
          (r.reason ? '\n  原因：' + r.reason : '');
      });
      wx.showModal({ title: '纪要修订历史', content: lines.join('\n'), showCancel: false, confirmText: '关闭' });
    } catch (e) { wx.showToast({ title: e.message, icon: 'none' }); }
  },

  formatSize(size) {
    const n = Number(size) || 0;
    if (n >= 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + 'MB';
    if (n >= 1024) return Math.round(n / 1024) + 'KB';
    return n + 'B';
  }
});
