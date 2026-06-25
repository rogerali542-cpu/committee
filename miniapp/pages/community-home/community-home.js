const api = require('../../utils/api');

// 兜底示例公告（接口为空/异常时显示）
const DEFAULT_NOTICES = [
  {
    id: 1,
    date: '2026-06-20',
    title: '关于小区电梯年度维保的通知',
    detail: '定于6月28日对1-8栋电梯进行年度安全维保，届时每栋将轮流停梯约2小时，请各位业主提前做好准备。'
  },
  {
    id: 2,
    date: '2026-06-15',
    title: '地下车库照明改造施工公告',
    detail: '6月18日-7月5日对地下车库B1/B2层进行LED照明节能改造，施工期间部分车位将临时封闭，请按现场指引停放车辆。'
  },
  {
    id: 3,
    date: '2026-06-10',
    title: '小区绿化养护工作安排',
    detail: '6月12日起对小区公共绿地进行夏季修剪打药，请各位业主在此期间关好门窗，看护好儿童和宠物。'
  },
  {
    id: 4,
    date: '2026-06-05',
    title: '物业费缴纳提醒',
    detail: '2026年第三季度物业费已开始收缴，请于7月15日前通过微信小程序或前往物业服务中心缴纳，逾期将产生滞纳金。'
  },
  {
    id: 5,
    date: '2026-05-28',
    title: '关于小区门禁系统升级的通知',
    detail: '6月1日起小区东门、南门门禁将升级为人脸识别+刷卡双模式，请未录入人脸信息的业主携带身份证到物业服务中心办理。'
  }
];

function getStatusBarHeight() {
  if (wx.getWindowInfo) {
    return wx.getWindowInfo().statusBarHeight || 20;
  }
  return 20;
}

Page({
  data: {
    statusBarHeight: 0,
    currentCard: 0,

    // 卡片1: 小区公告
    notices: [],
    noticeLoading: true,

    // 卡片2: 物业事项公示
    properties: [],
    filteredProperties: [],
    propFilter: 'all',
    propLoading: true,

    // 卡片3: 会议纪要公示
    publicMinutes: [],
    minutesLoading: true
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 0 });
    }
    this.setData({ statusBarHeight: getStatusBarHeight() });
    this.loadNotices();
    this.loadProperties();
    this.loadPublicMinutes();
  },

  // ── 小区公告 ──
  async loadNotices() {
    this.setData({ noticeLoading: true });
    try {
      const list = await api.noticeList();
      const notices = Array.isArray(list) && list.length > 0 ? list : DEFAULT_NOTICES;
      this.setData({ notices, noticeLoading: false });
    } catch (e) {
      // 网络异常时用示例兜底
      this.setData({ notices: DEFAULT_NOTICES, noticeLoading: false });
    }
  },

  // ── 物业事项 ──
  async loadProperties() {
    this.setData({ propLoading: true });
    try {
      const list = await api.receptionPropertyPublic();
      const properties = Array.isArray(list) && list.length > 0 ? list : [];
      if (properties.length === 0) {
        // API 无数据时用示例兜底
        properties.push({
          id: 101,
          date: '2026-06-18',
          content: '3栋2单元电梯运行时异响，业主反映存在安全隐患，请安排检修。',
          statusKey: 'done',
          statusLabel: '已处理',
          propertyReply: '已联系电梯维保单位检查，更换了曳引轮轴承，异响已消除，电梯恢复正常运行。',
          propertyRepliedAt: '2026-06-21'
        });
      }
      this.setData({ properties, propLoading: false });
      this.applyPropFilter();
    } catch (e) {
      // 网络异常时用示例兜底
      const fallback = [{
        id: 101,
        date: '2026-06-18',
        content: '3栋2单元电梯运行时异响，业主反映存在安全隐患，请安排检修。',
        statusKey: 'done',
        statusLabel: '已处理',
        propertyReply: '已联系电梯维保单位检查，更换了曳引轮轴承，异响已消除，电梯恢复正常运行。',
        propertyRepliedAt: '2026-06-21'
      }];
      this.setData({ properties: fallback, propLoading: false });
      this.applyPropFilter();
    }
  },

  filterProperty(e) {
    const filter = e.currentTarget.dataset.filter;
    this.setData({ propFilter: filter });
    this.applyPropFilter();
  },

  applyPropFilter() {
    const { properties, propFilter } = this.data;
    if (propFilter === 'all') {
      this.setData({ filteredProperties: properties });
    } else {
      this.setData({
        filteredProperties: properties.filter(function (r) {
          return r.statusKey === propFilter;
        })
      });
    }
  },

  // ── 会议纪要公示 ──
  async loadPublicMinutes() {
    this.setData({ minutesLoading: true });
    try {
      const list = await api.publicInfo();
      const publicMinutes = Array.isArray(list) && list.length > 0 ? list : [];
      if (publicMinutes.length === 0) {
        // API 无数据时用示例兜底
        publicMinutes.push({
          id: 1,
          type: 'committee',
          title: '2026年6月业委会例会',
          description: '审议小区电梯维保方案、地下车库照明改造预算及第三季度物业费调整事项',
          date: '2026-06-15',
          publishDate: '2026-06-18'
        });
      }
      this.setData({ publicMinutes, minutesLoading: false });
    } catch (e) {
      // 网络异常时用示例兜底
      const fallback = [{
        id: 1,
        type: 'committee',
        title: '2026年6月业委会例会',
        description: '审议小区电梯维保方案、地下车库照明改造预算及第三季度物业费调整事项',
        date: '2026-06-15',
        publishDate: '2026-06-18'
      }];
      this.setData({ publicMinutes: fallback, minutesLoading: false });
    }
  },

  openMinutes(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/minutes-public/minutes-public?meetingId=' + id });
  },

  // ── Swiper 变化 ──
  onSwiperChange(e) {
    this.setData({ currentCard: e.detail.current });
  },

  // 点击版块标签切换
  switchCard(e) {
    this.setData({ currentCard: e.currentTarget.dataset.index });
  }
});
