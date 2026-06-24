var api = require('../utils/api');

Component({
  data: { selected: 1, unread: 0 },
  lifetimes: {
    attached() { this.loadUnread(); }
  },
  pageLifetimes: {
    show() { this.loadUnread(); }
  },
  methods: {
    switchTab(e) {
      const { index, path } = e.currentTarget.dataset;
      this.setData({ selected: index });
      wx.switchTab({ url: path });
    },
    async loadUnread() {
      try {
        var res = await api.notificationList();
        this.setData({ unread: res.unread || 0 });
      } catch (e) {}
    }
  }
});
