const api = require('../../utils/api');
const mock = require('../../utils/mock');

Page({
  data: {
    counts: { committeeActive: 0, receptionPending: 0 },
    isProperty: false
  },
  onShow() {
    var app = getApp();
    var role = app.globalData && app.globalData.activeRole ? app.globalData.activeRole.role : '';
    // 物业不参与小区行政，不展示业委会入口
    this.setData({ isProperty: role === '物业' });
    this.loadCounts();
  },
  async loadCounts() {
    try {
      const cs = await api.committeeStats();
      const rs = await api.receptionStats();
      this.setData({
        counts: {
          committeeActive: (cs.preparing || 0) + (cs.ongoing || 0),
          receptionPending: rs.pending || 0
        }
      });
    } catch (e) {
      // Fallback to mock data
      const cMeetings = mock.committeeMeetings;
      const rRecords = mock.receptionRecords;
      this.setData({
        counts: {
          committeeActive: cMeetings.filter(m => m.stage === 'preparing' || m.stage === 'ongoing').length,
          receptionPending: rRecords.filter(r => !r.done).length
        }
      });
    }
  },
  goCommittee() { wx.navigateTo({ url: '/pages/committee/committee' }); },
  goReception() { wx.navigateTo({ url: '/pages/reception/reception' }); },
  goLearning() { wx.navigateTo({ url: '/pages/learning/learning' }); }
});
