// 内部「AI 议题报告」独立页：详细版议题报告（topicReportMarkdown）+ 待办事项。
// 仅供业委会内部查看，与面向公众的公开纪要(pages/minutes-public)分开。
const api = require('../../utils/api');

Page({
  data: {
    loading: true,
    title: '',
    reportText: '',
    todoText: '',
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
    let title = '会议议题报告';
    try {
      const detail = await api.committeeDetail(this.meetingId);
      if (detail && detail.title) title = detail.title;
    } catch (e) {}
    let reportText = '';
    let todoText = '';
    try { reportText = await api.committeeQuickTopicReport(this.meetingId); } catch (e) {}
    try { todoText = await api.committeeQuickTodos(this.meetingId); } catch (e) {}
    reportText = reportText && String(reportText).trim();
    todoText = todoText && String(todoText).trim();
    this.setData({
      loading: false,
      title,
      reportText: reportText || '',
      todoText: todoText || '',
      emptyText: reportText ? '' : '内部AI议题报告尚未生成，请先在会议进行页生成纪要草稿。'
    });
  },

  copyReport() {
    const text = [this.data.reportText, this.data.todoText].filter(Boolean).join('\n\n');
    if (!text) return;
    wx.setClipboardData({ data: text, success() { wx.showToast({ title: '已复制' }); } });
  }
});
