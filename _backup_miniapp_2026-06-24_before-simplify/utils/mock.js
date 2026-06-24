// 离线模拟数据 - 与数据库 V3 同步
module.exports = {
  committeeMeetings: [
    { id: 1, title: '2026年第4次业委会例会', meetingDate: '2026-06-15', meetingTime: '14:00', location: '社区活动室', stage: 'preparing', compliance: null, summaryLine: '📨 通知 4/7 · 📎 材料 2/7' },
    { id: 2, title: '2026年第3次业委会例会', meetingDate: '2026-05-10', meetingTime: '10:00', location: '物业办公室', stage: 'ongoing', compliance: null, summaryLine: '确认参会 5/7' },
    { id: 3, title: '2026年第2次业委会例会', meetingDate: '2026-03-15', meetingTime: '15:30', location: '社区活动室', stage: 'ended', compliance: 'valid', summaryLine: '✓ 会议有效 · 已公示', archived: true, publish: { published: true, publishDate: '2026-03-16', scoreState: 'ontime' }, _materials: [ { name: '维修资金使用方案.pdf', sizeText: '0.8MB', content: '公共设施维修专项资金使用方案（草案）……' }, { name: '绿化养护报价单.xlsx', sizeText: '0.3MB', content: '绿化养护承包单位报价明细……' } ] },
    { id: 4, title: '2026年第1次业委会例会', meetingDate: '2026-01-18', meetingTime: '10:00', location: '社区活动室', stage: 'ended', compliance: 'valid', summaryLine: '✓ 会议有效 · 已公示', archived: true, publish: { published: true, publishDate: '2026-01-19', scoreState: 'ontime' } },
    { id: 5, title: '2025年第6次业委会例会', meetingDate: '2025-12-05', meetingTime: '14:00', location: '社区活动室', stage: 'ended', compliance: 'flawed', summaryLine: '✓ 会议有效 · 逾期未公示' },
    { id: 6, title: '2025年第5次业委会例会', meetingDate: '2025-10-12', meetingTime: '10:00', location: '物业办公室', stage: 'ended', compliance: 'valid', summaryLine: '✓ 会议有效 · 已公示', archived: true, publish: { published: true, publishDate: '2025-10-14', scoreState: 'ontime' }, _materials: [ { name: '会议签到表扫描件.pdf', sizeText: '1.2MB', content: '到会委员签到扫描件……' } ] },
    { id: 7, title: '2026年第5次业委会例会（待开始）', meetingDate: '2026-06-20', meetingTime: '09:00', location: '社区会议室', stage: 'preparing', compliance: null, summaryLine: '📨 通知 7/7 · 📎 材料 7/7' },
    { id: 8, title: '2026年下半年工作部署会', meetingDate: null, meetingTime: '14:00', location: '待定', stage: 'preparing', compliance: null, summaryLine: '⚠ 日期待定，需补填后发通知' },
    { id: 9, title: '2026年6月临时业委会会议', meetingDate: '2026-06-08', meetingTime: '16:00', location: '线上腾讯会议', stage: 'ongoing', compliance: null, summaryLine: '确认参会 7/7' },
    { id: 10, title: '2026年5月物业续聘讨论会', meetingDate: '2026-05-25', meetingTime: '10:00', location: '物业办公室', stage: 'ongoing', compliance: null, summaryLine: '确认参会 4/7' },
    { id: 11, title: '2025年第4次业委会例会', meetingDate: '2025-08-20', meetingTime: '14:00', location: '社区活动室', stage: 'ended', compliance: 'valid', summaryLine: '✓ 会议有效 · 已公示' },
    { id: 12, title: '2025年第3次业委会例会', meetingDate: '2025-06-10', meetingTime: '09:30', location: '社区会议室', stage: 'ended', compliance: 'valid', summaryLine: '✓ 会议有效 · 逾期公示' },
    { id: 13, title: '2025年第2次业委会例会', meetingDate: '2025-04-05', meetingTime: '14:00', location: '社区活动室', stage: 'ended', compliance: 'flawed', summaryLine: '⚠ 带说明归档 · 待公示' },
    { id: 14, title: '2025年第1次业委会例会（无效）', meetingDate: '2025-02-10', meetingTime: '10:00', location: '物业办公室', stage: 'ended', compliance: 'invalid', summaryLine: '✕ 会议不成立' }
  ],

  committeeStats: { annualCount: 2, annualTarget: 6, periodCount: 2, periodTarget: 1, preparing: 3, ongoing: 3, ended: 8, periodLabel: '5-6月', annualYear: 2026 },

  publishScore: { ontime: 3, overdue: 2, pending: 2 },

  dashboardStats: { todo: 6, done: 3907, overdue: 3 },

  // 权限管理
  // 角色默认权限
  roleDefaultPerms: {
    '主任':   ['committee.*','reception.*','learning.*','view.*'],
    '副主任': ['committee.*','reception.*','learning.*','view.*'],
    '委员':   ['committee.sign_in','committee.sign','committee.vote','committee.evidence','committee.topic','committee.sign_all','reception.manage','learning.view','view.internal','view.public'],
    '业主':   ['view.public'],
    '物业':   ['view.public','reception.property_feedback'],
    '管理员': ['*'],
  },
  // 个人权限覆写（空=用角色默认）
  userPermOverrides: {},
  // 所有用户列表
  allUsers: [
    { id: 1, realName: '张建国', role: '主任' },
    { id: 2, realName: '李秀英', role: '副主任' },
    { id: 3, realName: '王志强', role: '委员' },
    { id: 4, realName: '赵丽娟', role: '委员' },
    { id: 5, realName: '刘海涛', role: '委员' },
    { id: 6, realName: '陈晓梅', role: '委员' },
    { id: 7, realName: '杨国华', role: '委员' },
    { id: 8, realName: '秘书小李', role: '委员' },
    { id: 9, realName: '测试业主', role: '业主' },
    { id: 10, realName: '物业张经理', role: '物业' },
    { id: 99, realName: '系统管理员', role: '管理员' },
    { id: 11, realName: '张丽华', role: '业主' }
  ],


  receptionSystem: { published: true, timeDesc: '每月10日 14:00–16:00', place: '社区党群服务中心一楼接待室', person: '业委会委员轮值（详见公示栏）' },

  receptionRecords: [
    { id: 1, date: '2026-05-10', time: '14:30', visitorName: '周敏', room: '5号楼302', receiver: '李秀英（副主任）', category: 'property', categoryLabel: '物业类', content: '反映地下车库照明长期损坏，多次报修未果。', resolution: '已督促物业更换车库照明灯具，承诺一周内完成。', propertyStatus: 'replied', propertyReply: '已安排电工更换地下车库全部损坏灯具，5月12日完成并恢复照明。', propertyRepliedBy: '物业客服', propertyRepliedAt: '2026-05-12 10:20', fedProperty: true, fedOwner: true, done: true, needsPropertyFeedback: true },
    { id: 2, date: '2026-05-10', time: '15:10', visitorName: '吴建国', room: '3号楼1102', receiver: '王志强（委员）', category: 'public_affairs', categoryLabel: '公共事务', content: '建议在中心花园增设儿童活动区健身器材。', resolution: '已纳入下次业委会例会议题讨论。', propertyStatus: null, propertyReply: '', propertyRepliedBy: '', propertyRepliedAt: '', fedProperty: false, fedOwner: true, done: true, needsPropertyFeedback: false },
    { id: 3, date: '2026-05-28', time: '14:00', visitorName: '孙丽', room: '8号楼601', receiver: '张建国（主任）', category: 'property', categoryLabel: '物业类', content: '电梯近期频繁故障，要求物业加强日常维保。', resolution: '', propertyStatus: 'dispatched', propertyReply: '', propertyRepliedBy: '', propertyRepliedAt: '', fedProperty: false, fedOwner: false, done: false, needsPropertyFeedback: true },
    { id: 4, date: '2026-04-10', time: '15:00', visitorName: '郑强', room: '2号楼205', receiver: '赵丽娟（委员）', category: 'neighbor', categoryLabel: '邻里纠纷', content: '与楼上住户因漏水产生纠纷，请求协调。', resolution: '已组织双方现场协商，达成维修与赔偿方案。', propertyStatus: null, propertyReply: '', propertyRepliedBy: '', propertyRepliedAt: '', fedProperty: false, fedOwner: true, done: true, needsPropertyFeedback: false }
  ],

  receptionStats: { monthCount: 2, monthLabel: '5月已接待', pending: 1, yearCount: 4, done: 3, total: 4 },

  learningList: [
    { id: 1, title: '物业管理条例解读学习', date: '2026-07-10', time: '14:00', location: '社区会议室', trainer: '张建国', type: 'internal', stage: 'preparing', progress: 0, notified: false, attendees: '张建国,李秀英,王志强,赵丽娟,刘海涛,陈晓梅,杨国华', description: '学习最新修订的物业管理条例，重点掌握业委会职责范围和议事规则。', signIns: { '张建国': false, '李秀英': false, '王志强': false, '赵丽娟': false, '刘海涛': false, '陈晓梅': false, '杨国华': false } },
    { id: 2, title: '业主大会议事规则专题培训', date: '2026-04-18', time: '09:30', location: '街道党群服务中心', trainer: '街道办王科长', type: 'street', stage: 'ended', progress: 100, notified: true, attendees: '张建国,李秀英,王志强,赵丽娟', description: '街道组织的业主大会议事规则与表决程序培训。', signIns: { '张建国': true, '李秀英': true, '王志强': true, '赵丽娟': false }, evidences: [ { id: 1, fileName: '培训签到表.jpg', fileType: '照片' }, { id: 2, fileName: '现场培训照片.jpg', fileType: '照片' } ] }
  ],

  // 应用内通知
  notifications: []
};
