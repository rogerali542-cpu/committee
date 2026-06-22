const api = require('../../utils/api');

Page({
  data: { items: [] },
  onShow() { this.loadData(); },
  async loadData() {
    try {
      const items = await api.publicInfo();
      this.setData({ items });
    } catch (e) { /* offline ok */ }
  },
  viewDetail(e) {
    const { id, type } = e.currentTarget.dataset;
    if (type === 'committee') {
      wx.navigateTo({ url: '/pages/minutes/minutes?meetingId=' + id + '&from=info-public' });
    }
  }
});
