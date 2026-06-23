// 面向民众的「公开纪要」独立页：只读展示正式会议纪要正文（minutesMarkdown）。
// 与 pages/minutes 的内部工作视图分开——这里不含内部AI议题报告/待办/编辑/修订入口。
const api = require('../../utils/api');

Page({
  data: {
    loading: true,
    title: '',
    meetingDate: '',
    meetingTime: '',
    location: '',
    plainText: '',
    isDraft: false,
    isPublished: false,
    emptyText: ''
  },

  onLoad(options) {
    this.meetingId = parseInt(options.meetingId);
    if (!this.meetingId) {
      this.setData({ loading: false, emptyText: '缺少会议参数' });
      return;
    }
    this.load();
  },

  async load() {
    try {
      const detail = await api.committeeDetail(this.meetingId);
      const published = !!(detail && detail.publish && detail.publish.published);
      const isDraft = !detail || detail.stage !== 'ended';
      let text = '';
      try { text = await api.committeeMinutes(this.meetingId); } catch (e) {}
      this.setData({
        loading: false,
        title: (detail && detail.title) || '业主委员会会议纪要',
        meetingDate: (detail && detail.meetingDate) || '',
        meetingTime: (detail && detail.meetingTime) || '',
        location: (detail && detail.location) || '',
        plainText: (text && String(text).trim()) || '',
        isDraft,
        isPublished: published,
        emptyText: (text && String(text).trim()) ? '' : '正式纪要尚未生成，请先在会议进行页生成纪要草稿。'
      });
    } catch (e) {
      this.setData({ loading: false, emptyText: e.message || '纪要暂不可查看' });
    }
  },

  copyText() {
    if (!this.data.plainText) return;
    wx.setClipboardData({ data: this.data.plainText, success() { wx.showToast({ title: '已复制' }); } });
  }
});
