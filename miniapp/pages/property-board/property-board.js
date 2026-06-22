// 物业事项公示 —— 全体业主可见，只读，显示问题实时处理状态（已脱敏）
const api = require('../../utils/api');

Page({
  data: {
    filter: 'all',   // all / processing / done
    counts: { all: 0, pending: 0, processing: 0, done: 0 },
    items: [],
    loading: true
  },

  onShow() {
    this.loadBoard();
  },

  async loadBoard() {
    this.setData({ loading: true });
    try {
      const all = await api.receptionPropertyPublic();
      const counts = {
        all: all.length,
        pending: all.filter(r => r.statusKey === 'pending').length,
        processing: all.filter(r => r.statusKey === 'processing').length,
        done: all.filter(r => r.statusKey === 'done').length
      };
      const filter = this.data.filter;
      const items = filter === 'all' ? all : all.filter(r => r.statusKey === filter);
      this.setData({ items, counts, loading: false });
    } catch (e) {
      this.setData({ loading: false });
    }
  },

  switchFilter(e) {
    this.setData({ filter: e.currentTarget.dataset.filter });
    this.loadBoard();
  }
});
