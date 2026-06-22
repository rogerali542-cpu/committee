Page({
  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 0 });
    }
  },
  goToYwh() {
    wx.switchTab({ url: '/pages/main/main' });
  }
});
