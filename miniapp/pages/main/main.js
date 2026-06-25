const app = getApp();
const api = require('../../utils/api');
const perm = require('../../utils/perm');

function getStatusBarHeight() {
  if (wx.getWindowInfo) {
    return wx.getWindowInfo().statusBarHeight || 20;
  }
  return 20;
}

// 会议阶段 → 开会主线三步
const STEP_BY_STAGE = { preparing: 1, ongoing: 2, ended: 3 };
const STEP_LABELS = ['', '准备开会', '正式开会', '会后总结'];

Page({
  data: {
    statusBarHeight: 0,
    activeRole: {},
    canCreate: false,       // 主任/副主任：可发起会议
    canViewInternal: false, // 可见资料库
    unread: 0,
    loading: true,
    current: null,          // 当前进行中的会议（含三步进度与主操作）
    recent: null            // 最近一次已结束会议
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({ selected: 1 });
    }
    const role = app.globalData.activeRole;
    if (!role) {
      wx.redirectTo({ url: '/pages/login/login' });
      return;
    }
    this.setData({
      activeRole: role,
      canCreate: perm.can('committee.create'),
      canViewInternal: perm.can('view.internal'),
      statusBarHeight: getStatusBarHeight()
    });
    this.loadUnread();
    this.loadMeetings();
  },

  async loadUnread() {
    try {
      const res = await api.notificationList();
      this.setData({ unread: res.unread || 0 });
    } catch (e) { /* 离线可忽略 */ }
  },

  async loadMeetings() {
    try {
      const list = await api.committeeList();
      const meetings = Array.isArray(list) ? list : [];
      const isChair = perm.isChair() || perm.isRecorder();

      // 当前会议：优先「进行中 / 准备中」；主任再把「已结束未公示」视为待整理纪要
      let current = meetings.find(m => m.stage === 'preparing' || m.stage === 'ongoing');
      if (!current && isChair) {
        current = meetings.find(m => m.stage === 'ended'
          && m.compliance !== 'invalid'
          && (!m.publish || !m.publish.published));
      }
      // 最近一次已结束会议（空闲态展示）
      const recent = meetings.find(m => m.stage === 'ended');

      this.setData({
        current: current ? this.decorateCurrent(current, isChair) : null,
        recent: recent ? this.decorateRecent(recent) : null,
        loading: false
      });
    } catch (e) {
      this.setData({ loading: false });
    }
  },

  // 给当前会议附加：三步进度、主操作按钮文案/图标
  decorateCurrent(m, isChair) {
    const step = STEP_BY_STAGE[m.stage] || 1;
    const steps = [1, 2, 3].map(no => ({
      no: no,
      label: STEP_LABELS[no],
      state: no < step ? 'done' : (no === step ? 'active' : 'todo')
    }));

    let ctaLabel, ctaIcon;
    if (m.stage === 'preparing') {
      ctaLabel = isChair ? '继续准备会议' : '查看会议 · 确认参加';
      ctaIcon = '📋';
    } else if (m.stage === 'ongoing') {
      ctaLabel = isChair ? '进入会议 · 开始录音' : '查看会议';
      ctaIcon = isChair ? '🎙️' : '👀';
    } else {
      ctaLabel = '整理会议记录';
      ctaIcon = '📝';
    }

    return {
      id: m.id,
      title: m.title,
      meetingDate: m.meetingDate,
      meetingTime: m.meetingTime,
      location: m.location,
      step: step,
      stepText: STEP_LABELS[step],
      steps: steps,
      ctaLabel: ctaLabel,
      ctaIcon: ctaIcon
    };
  },

  decorateRecent(m) {
    let status = '已结束';
    if (m.publish && m.publish.published) status = '已张榜公开';
    else if (m.compliance === 'invalid') status = '会议无效';
    return { id: m.id, title: m.title, meetingDate: m.meetingDate, status: status };
  },

  // 删除当前会议（仅主任；清理测试数据用，任意阶段均可）
  removeCurrent() {
    const m = this.data.current;
    if (!m) return;
    wx.showModal({
      title: '删除会议',
      content: '确定删除「' + m.title + '」？删除后无法恢复。',
      confirmText: '删除',
      confirmColor: '#E74C3C',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await api.committeeRemove(m.id);
          wx.showToast({ title: '已删除', icon: 'success' });
          this.loadMeetings();
        } catch (e) {
          wx.showToast({ title: (e && e.message) || '删除失败', icon: 'none' });
        }
      }
    });
  },

  // ── 导航 ──
  goCurrent() {
    if (!this.data.current) return;
    const id = this.data.current.id;
    // 委员走专属极简会议页；主任/记录员走详情页（操作流程）
    const isChair = perm.isChair() || perm.isRecorder();
    const url = isChair
      ? '/pages/committee-detail/committee-detail?id=' + id
      : '/pages/my-meeting/my-meeting?id=' + id;
    wx.navigateTo({ url: url });
  },
  goCreate() {
    // 发起会议 → 会议管理页（含新建入口）。阶段 3 会改成一步直达的建会向导。
    wx.navigateTo({ url: '/pages/committee/committee' });
  },
  goRecent() {
    if (!this.data.recent) return;
    wx.navigateTo({ url: '/pages/committee-detail/committee-detail?id=' + this.data.recent.id });
  },
  goCommittee() { wx.navigateTo({ url: '/pages/committee/committee' }); },
  goReception() { wx.navigateTo({ url: '/pages/reception/reception' }); },
  goNoticeAdmin() { wx.navigateTo({ url: '/pages/notice-admin/notice-admin' }); },
  goLearning() { wx.navigateTo({ url: '/pages/learning/learning' }); },
  goLibrary() { wx.navigateTo({ url: '/pages/library/library' }); },
  goNotifications() { wx.navigateTo({ url: '/pages/notifications/notifications' }); }
});
