// ═══════════════════════════════════════════════
// Mock 处理器 — 应用内通知
// ═══════════════════════════════════════════════

var mock = require('../mock');
var H = require('../api/helpers');

function mergeMeetingNotifications(list) {
  var grouped = {};
  var merged = [];
  list.forEach(function (n) {
    if (!n.meetingId || (n.type !== 'notice' && n.type !== 'material')) {
      merged.push(n);
      return;
    }
    var key = n.userRoleId + '_' + n.meetingId;
    if (!grouped[key]) {
      grouped[key] = Object.assign({}, n, {
        type: 'notice',
        title: n.type === 'material' ? '业委会会议通知与材料' : n.title,
        ids: [n.id],
        hasMaterialNotice: n.type === 'material'
      });
      merged.push(grouped[key]);
      return;
    }
    var item = grouped[key];
    item.ids.push(n.id);
    item.read = item.read && n.read;
    item.hasMaterialNotice = item.hasMaterialNotice || n.type === 'material';
    if (new Date(n.createdAt) > new Date(item.createdAt)) item.createdAt = n.createdAt;
    if (item.hasMaterialNotice) item.title = '业委会会议通知与材料';
    if (n.type === 'material') item.content = n.content;
  });
  return merged.sort(function (a, b) {
    return new Date(b.createdAt || 0) - new Date(a.createdAt || 0);
  });
}

function handle(method, path, data) {
  var app = getApp();
  var activeRole = (app && app.globalData && app.globalData.activeRole) ? app.globalData.activeRole : { id: 1 };
  var myRoleId = activeRole.id;
  var basePath = path.split('?')[0];

  // 列表（当前用户）
  if (method === 'GET' && basePath === '/api/notifications') {
    var mine = mock.notifications.filter(function (n) { return n.userRoleId === myRoleId; });
    var list = mergeMeetingNotifications(mine);
    return {
      list: list.slice(0, 50),
      unread: list.filter(function (n) { return !n.read; }).length,
      total: list.length
    };
  }

  // 标记已读
  var readMatch = path.match(/\/api\/notifications\/(\d+)\/read/);
  if (readMatch && method === 'PUT') {
    var nid = parseInt(readMatch[1]);
    var n = mock.notifications.find(function (x) { return x.id === nid; });
    if (n && n.meetingId && (n.type === 'notice' || n.type === 'material')) {
      mock.notifications.forEach(function (x) {
        if (x.userRoleId === n.userRoleId && x.meetingId === n.meetingId && (x.type === 'notice' || x.type === 'material')) {
          x.read = true;
        }
      });
    } else if (n) {
      n.read = true;
    }
    return {};
  }

  // 全部已读
  if (method === 'PUT' && basePath === '/api/notifications/read-all') {
    mock.notifications.forEach(function (n) {
      if (n.userRoleId === myRoleId) n.read = true;
    });
    return {};
  }

  return undefined;
}

module.exports = { handle: handle };
