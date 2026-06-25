// 可复用的自定义橙色导航头部：大粗白色标题 + 返回键，自动适配状态栏高度。
// 用法：页面 json 设 "navigationStyle":"custom" 并引入本组件，wxml 顶部放 <page-nav title="标题" />
Component({
  properties: {
    title: { type: String, value: '' }
  },
  data: {
    statusBarHeight: 20
  },
  lifetimes: {
    attached() {
      if (wx.getWindowInfo) {
        this.setData({ statusBarHeight: wx.getWindowInfo().statusBarHeight || 20 });
      }
    }
  },
  methods: {
    goBack() {
      wx.navigateBack({
        delta: 1,
        fail() { wx.switchTab({ url: '/pages/main/main' }); }
      });
    }
  }
});
