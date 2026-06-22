// ═══════════════════════════════════════════════
// Mock 路由器 — 统一入口
// ═══════════════════════════════════════════════

var mock = require('../mock');
var H = require('../api/helpers');

// 域 handler（按路径前缀匹配，优先级从高到低）
var domainHandlers = [
  { prefix: '/api/committees', handler: require('./committee') },
  { prefix: '/api/owner-meetings', handler: require('./owner-meeting') },
  { prefix: '/api/receptions', handler: require('./reception') },
  { prefix: '/api/learning', handler: require('./learning') },
  { prefix: '/api/notifications', handler: require('./notifications') }
];

function mockRequest(method, path, data) {
  // 精确匹配的简单端点（不需要域 handler 的）
  var basePath = path.split('?')[0];

  if (method === 'GET' && basePath === '/api/dashboard/stats') return mock.dashboardStats;
  if (method === 'GET' && basePath === '/api/public-info') {
    // 返回所有已公示的委员会会议 + 业主大会
    var committee = require('./committee');
    var ownerM = require('./owner-meeting');
    var published = [];
    mock.committeeMeetings.forEach(function (m) {
      if (m.stage === 'ended' && m.publish && m.publish.published) {
        published.push({ id: m.id, title: m.title, date: m.meetingDate, location: m.location,
          type: 'committee', description: m.description, compliance: m.compliance, publishDate: m.publish.publishDate });
      }
    });
    mock.ownerMeetings.forEach(function (m) {
      if (m.stage === 'ended' && ownerM.isOwnerPublished(m)) {
        published.push({ id: m.id, title: m.title, date: m.meetingDate, location: m.location,
          type: 'owner', description: m.description, compliance: m.compliance, publishDate: ownerM.ownerPublishDate(m) });
      }
    });
    return published;
  }
  if (method === 'GET' && basePath === '/api/auth/me') return {};
  if (method === 'POST' && basePath === '/api/auth/login') {
    return { token: 'dev-token-1', userId: 1, roles: [], activeRole: {} };
  }

  // 转发到域 handler
  for (var i = 0; i < domainHandlers.length; i++) {
    if (path.indexOf(domainHandlers[i].prefix) === 0) {
      var result = domainHandlers[i].handler.handle(method, path, data);
      if (result !== undefined) return result;
    }
  }

  // 兜底
  return (method === 'POST' || method === 'PUT') ? {} : [];
}

module.exports = { mockRequest: mockRequest };
