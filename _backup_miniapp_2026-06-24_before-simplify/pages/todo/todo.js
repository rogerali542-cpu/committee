// 我要办理 —— 只聚合"此刻需要我操作"的事项，分两类：待开始 / 进行中，按开始时间排序
// 待开始 = 我还没动手的（如未确认参会、待派单）；进行中 = 我已经在处理但没完成的
const api = require('../../utils/api');
const perm = require('../../utils/perm');

function sortByStart(a, b) {
  return (a.sortKey || '').localeCompare(b.sortKey || '');
}

Page({
  data: {
    pending: [],   // 待开始
    active: [],    // 进行中
    total: 0,
    loading: true
  },

  onShow() {
    const app = getApp();
    if (!app.globalData || !app.globalData.activeRole) {
      wx.redirectTo({ url: '/pages/login/login' });
      return;
    }
    this.loadTodos();
  },

  async loadTodos() {
    this.setData({ loading: true });
    const all = [];
    const app = getApp();
    const role = app.globalData && app.globalData.activeRole ? app.globalData.activeRole.role : '';

    // 1) 业委会会议：仅参会成员（委员/主任/副主任）才有待办
    //    - 准备阶段：通知已送达我但我尚未查看 → 「查看会议通知」（查看即完成）
    //    - 进行中：我还没办完（确认参会/表决）
    if (perm.can('committee.sign_in')) try {
      const list = await api.committeeList(null);
      (list || []).forEach(m => {
        if (m.stage === 'preparing' && m.myNoticeUnread) {
          all.push({
            key: 'cn-' + m.id, id: m.id, kind: 'committee', tag: '业委会会议',
            title: m.title,
            statusText: '会议通知待查看',
            subText: m.meetingDate ? (m.meetingDate + (m.meetingTime ? ' ' + m.meetingTime : '')) : '',
            group: 'pending',
            sortKey: (m.meetingDate || '') + ' ' + (m.meetingTime || '')
          });
        } else if (m.stage === 'ongoing' && m.progress >= 0 && m.progress < 100) {
          all.push({
            key: 'c-' + m.id, id: m.id, kind: 'committee', tag: '业委会会议',
            title: m.title,
            statusText: m.progressLabel || '待处理',
            subText: m.meetingDate ? (m.meetingDate + (m.meetingTime ? ' ' + m.meetingTime : '')) : '',
            group: m.progress === 0 ? 'pending' : 'active',
            sortKey: (m.meetingDate || '') + ' ' + (m.meetingTime || '')
          });
        }
      });
    } catch (e) { /* offline ok */ }

    // 2) 接待事项（业委会，有 reception.manage）—— 都是待我处理且未开始，归「待开始」
    if (perm.can('reception.manage')) {
      try {
        const recs = await api.receptionRecords('pending');
        (recs || []).forEach(r => {
          let statusText = '';
          if (r.category === 'property') {
            if (r.propertyStatus === 'replied' && !r.fedOwner) statusText = '物业已回复·待反馈业主';
            else if (!r.propertyStatus || r.propertyStatus === 'pending_dispatch') statusText = '待转物业处理';
            // dispatched / processing：物业在处理，业委会无需操作，跳过
          } else if (!r.fedOwner) {
            statusText = '待反馈业主';
          }
          if (statusText) {
            all.push({
              key: 'r-' + r.id, id: r.id, kind: 'reception', tag: '接待事项',
              title: r.content || (r.visitorName ? r.visitorName + ' 的诉求' : '接待诉求'),
              statusText: statusText,
              subText: r.date || '',
              group: 'pending',
              sortKey: (r.date || '') + ' ' + (r.time || '')
            });
          }
        });
      } catch (e) { /* offline ok */ }
    }

    // 3) 物业工单（仅物业角色）—— 未处理=待开始，处理中=进行中
    if (role === '物业') {
      try {
        const tasks = await api.receptionPropertyTasks('all');
        (tasks || [])
          .filter(t => t.propertyStatus === 'dispatched' || t.propertyStatus === 'processing')
          .forEach(t => {
            all.push({
              key: 'p-' + t.id, id: t.id, kind: 'property', tag: '物业工单',
              title: t.content || '物业诉求',
              statusText: t.propertyStatus === 'processing' ? '处理中·待回填' : '待处理',
              subText: t.date || '',
              group: t.propertyStatus === 'processing' ? 'active' : 'pending',
              sortKey: (t.date || '') + ' ' + (t.time || '')
            });
          });
      } catch (e) { /* offline ok */ }
    }

    const pending = all.filter(i => i.group === 'pending').sort(sortByStart);
    const active = all.filter(i => i.group === 'active').sort(sortByStart);
    this.setData({ pending, active, total: all.length, loading: false });
  },

  openItem(e) {
    const { id, kind } = e.currentTarget.dataset;
    if (kind === 'reception') {
      wx.navigateTo({ url: '/pages/reception/reception' });
    } else if (kind === 'property') {
      wx.navigateTo({ url: '/pages/property-tasks/property-tasks' });
    } else {
      wx.navigateTo({ url: '/pages/committee-detail/committee-detail?id=' + id });
    }
  }
});
