// 资料库 —— 顶部按大类切换：业委会会议 / 学习培训
// 每类按结束时间倒序（最近在前）；卡片只显示概要，点进 archive-detail 看全部
const api = require('../../utils/api');

function byDateDesc(a, b) { return (b.date || '').localeCompare(a.date || ''); }

Page({
  data: {
    tab: 'committee',
    counts: { committee: 0, learning: 0 },
    lists: { committee: [], learning: [] },
    items: [],
    loading: true
  },

  onShow() {
    const app = getApp();
    if (!app.globalData || !app.globalData.activeRole) {
      wx.redirectTo({ url: '/pages/login/login' });
      return;
    }
    this.loadArchive();
  },

  async loadArchive() {
    this.setData({ loading: true });

    const committee = await api.committeeArchiveList().catch(() => []);
    const learning = await api.learningList('', 'ended').catch(() => []);

    const committeeItems = (committee || [])
      .filter(m => m.compliance !== 'invalid')
      .map(m => ({
        id: m.id, kind: 'committee', title: m.title, date: m.meetingDate || '',
        statusText: (m.publish && m.publish.published) ? '已公示' : '已归档',
        metaText: (m.materials ? m.materials.length : 0) + ' 份材料'
      }))
      .sort(byDateDesc);

    const learningItems = (learning || []).map(m => ({
      id: m.id, kind: 'learning', title: m.title, date: m.date || '',
      statusText: '已完成',
      metaText: (m.trainer ? '讲师 ' + m.trainer : '学习活动')
    })).sort(byDateDesc);

    const lists = { committee: committeeItems, learning: learningItems };
    this.setData({
      lists: lists,
      counts: { committee: committeeItems.length, learning: learningItems.length },
      items: lists[this.data.tab],
      loading: false
    });
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({ tab: tab, items: this.data.lists[tab] });
  },

  openDetail(e) {
    const { id, kind } = e.currentTarget.dataset;
    wx.navigateTo({ url: '/pages/archive-detail/archive-detail?kind=' + kind + '&id=' + id });
  }
});
