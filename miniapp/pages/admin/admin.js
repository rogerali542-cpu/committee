const mock = require('../../utils/mock');

Page({
  data: {
    users: [],
    expandedId: null
  },

  onShow() {
    this.loadUsers();
  },

  loadUsers() {
    const perms = [
      { code: 'committee.sign_in', label: '签到' },
      { code: 'committee.sign', label: '签字' },
      { code: 'committee.vote', label: '投票' },
      { code: 'committee.create', label: '新建会议' },
      { code: 'committee.advance', label: '推进阶段' },
      { code: 'committee.delivery', label: '送达管理' },
      { code: 'committee.evidence', label: '上传佐证' },
      { code: 'committee.topic', label: '议题管理' },
      { code: 'committee.publish', label: '发起公示' },
      { code: 'learning.create', label: '创建学习' },
      { code: 'learning.advance', label: '推进学习阶段' },
      { code: 'learning.view', label: '查看学习' },
      { code: 'view.internal', label: '查看内部流程' },
      { code: 'view.public', label: '查看公示' }
    ];

    const overrides = mock.userPermOverrides;
    const defaults = mock.roleDefaultPerms;

    this.setData({
      users: mock.allUsers.map(u => {
        const def = defaults[u.role] || [];
        const over = overrides[u.id] || {};
        return {
          id: u.id,
          realName: u.realName,
          role: u.role,
          defaultPerms: def,
          allPerms: perms.map(p => {
            // 覆写优先，否则用角色默认
            let granted = def.includes(p.code) || def.includes(p.code.split('.')[0] + '.*');
            if (p.code in over) granted = over[p.code];
            if (def.includes('*')) granted = true;
            return { ...p, granted };
          })
        };
      })
    });
  },

  toggleExpand(e) {
    const id = e.currentTarget.dataset.id;
    this.setData({ expandedId: this.data.expandedId === id ? null : id });
  },

  togglePerm(e) {
    const userId = e.currentTarget.dataset.userId;
    const code = e.currentTarget.dataset.code;
    const granted = e.detail.value;
    if (!mock.userPermOverrides[userId]) mock.userPermOverrides[userId] = {};
    mock.userPermOverrides[userId][code] = granted;
    this.loadUsers();
    wx.showToast({ title: granted ? '已授权' : '已禁用', icon: 'success' });
  },

  resetPerms(e) {
    const userId = e.currentTarget.dataset.userId;
    delete mock.userPermOverrides[userId];
    this.loadUsers();
    wx.showToast({ title: '已重置', icon: 'success' });
  }
});
