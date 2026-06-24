// ═══════════════════════════════════════════════
// Mock 处理器 — 接待记录
// ═══════════════════════════════════════════════

var mock = require('../mock');
var H = require('../api/helpers');
var C = require('../constants');

// 全体物业共享账号（接待派单的目标）
var PROPERTY_ROLE_ID = 10;

var PROPERTY_STATUS_LABELS = {
  pending_dispatch: '待派单',
  dispatched: '物业未处理',
  processing: '处理中',
  replied: '处理完'
};

function withPropertyLabel(r) {
  return Object.assign({}, r, {
    propertyStatusLabel: r.propertyStatus ? (PROPERTY_STATUS_LABELS[r.propertyStatus] || '') : '',
    evidences: r.evidences || []
  });
}

function addReceptionNotification(userRoleId, recordId, title, content) {
  mock.notifications.unshift({
    id: H.nextId(mock.notifications),
    userRoleId: userRoleId,
    receptionId: recordId,
    type: 'reception',
    title: title,
    content: content,
    read: false,
    createdAt: new Date().toISOString()
  });
}

function handle(method, path, data) {
  var basePath = path.split('?')[0];

  // 制度配置
  if (method === 'GET' && basePath === '/api/receptions/system') return mock.receptionSystem;
  if (method === 'PUT' && basePath === '/api/receptions/system') {
    mock.receptionSystem = Object.assign({}, mock.receptionSystem, data || {});
    return {};
  }

  // 记录列表
  if (method === 'GET' && basePath === '/api/receptions/records') {
    var filter = H.qs(path, 'filter');
    var list = mock.receptionRecords;
    if (filter === 'pending') list = list.filter(function (r) { return !H.receptionDone(r); });
    else if (filter === 'done') list = list.filter(H.receptionDone);
    return list.map(withPropertyLabel);
  }

  // 物业事项公示看板（全体业主可见，脱敏：不含来访业主姓名/房号）
  if (method === 'GET' && basePath === '/api/receptions/property-public') {
    return mock.receptionRecords
      .filter(function (r) { return r.category === C.RECEPTION_CATEGORY.PROPERTY; })
      .map(function (r) {
        var key = r.propertyStatus === 'replied' ? 'done' : (r.propertyStatus === 'processing' ? 'processing' : 'pending');
        return {
          id: r.id,
          date: r.date,
          content: r.content,
          statusKey: key,
          statusLabel: key === 'done' ? '已处理' : (key === 'processing' ? '处理中' : '待处理'),
          propertyReply: r.propertyStatus === 'replied' ? r.propertyReply : null,
          propertyRepliedAt: r.propertyStatus === 'replied' ? r.propertyRepliedAt : null
        };
      });
  }

  // 物业工作台：派给物业的工单（已派单后均可见）
  if (method === 'GET' && basePath === '/api/receptions/property-tasks') {
    return mock.receptionRecords
      .filter(function (r) {
        return r.category === C.RECEPTION_CATEGORY.PROPERTY
          && (r.propertyStatus === 'dispatched' || r.propertyStatus === 'processing' || r.propertyStatus === 'replied');
      })
      .map(withPropertyLabel);
  }

  // 物业开始处理：物业未处理 → 处理中
  var startMatch = path.match(/\/api\/receptions\/records\/(\d+)\/property-start/);
  if (startMatch && method === 'POST') {
    var sr = mock.receptionRecords.find(function (x) { return x.id === parseInt(startMatch[1]); });
    if (!sr) return {};
    if (sr.propertyStatus !== 'dispatched') throw new Error('该工单当前不可开始处理');
    sr.propertyStatus = 'processing';
    return {};
  }

  // 派单给物业
  var dispatchMatch = path.match(/\/api\/receptions\/records\/(\d+)\/dispatch/);
  if (dispatchMatch && method === 'POST') {
    var dr = mock.receptionRecords.find(function (x) { return x.id === parseInt(dispatchMatch[1]); });
    if (!dr) return {};
    if (dr.category !== C.RECEPTION_CATEGORY.PROPERTY) throw new Error('仅物业类诉求可转物业处理');
    if (dr.propertyStatus === 'dispatched' || dr.propertyStatus === 'replied') throw new Error('该诉求已转物业');
    var app = getApp();
    dr.propertyStatus = 'dispatched';
    dr.dispatchedByRoleId = (app && app.globalData && app.globalData.activeRole) ? app.globalData.activeRole.id : 1;
    addReceptionNotification(PROPERTY_ROLE_ID, dr.id, '新的物业处理工单',
      '业主' + (dr.visitorName || '') + (dr.room ? '（' + dr.room + '）' : '') + ' 反映：' + (dr.content || '') + ' 请处理后回填结果。');
    return {};
  }

  // 物业回填处理结果
  var replyMatch = path.match(/\/api\/receptions\/records\/(\d+)\/property-reply/);
  if (replyMatch && method === 'POST') {
    var rp = mock.receptionRecords.find(function (x) { return x.id === parseInt(replyMatch[1]); });
    if (!rp) return {};
    if (rp.propertyStatus !== 'processing') throw new Error('请先开始处理');
    var reply = (data && data.reply ? String(data.reply) : '').trim();
    if (!reply) throw new Error('请填写处理结果');
    if (!rp.evidences || !rp.evidences.length) throw new Error('请先上传处理佐证');
    var app2 = getApp();
    rp.propertyStatus = 'replied';
    rp.propertyReply = reply;
    rp.propertyRepliedBy = (app2 && app2.globalData && app2.globalData.activeRole) ? app2.globalData.activeRole.realName : '物业';
    rp.propertyRepliedAt = H.todayStr() + ' ' + new Date().toTimeString().slice(0, 5);
    rp.fedProperty = true; // 兼容旧字段
    rp.done = H.receptionDone(rp);
    addReceptionNotification(rp.dispatchedByRoleId || 1, rp.id, '物业已回复处理结果',
      '「' + (rp.content || '业主诉求') + '」物业反馈：' + reply);
    return {};
  }

  // 统计
  if (method === 'GET' && basePath === '/api/receptions/stats') return H.calcReceptionStats();

  // 新建记录
  if (method === 'POST' && basePath === '/api/receptions/records') {
    var id = H.nextId(mock.receptionRecords);
    var category = (data && data.category) || C.RECEPTION_CATEGORY.OTHER;
    mock.receptionRecords.unshift({
      id: id,
      date: (data && data.date) || H.todayStr(),
      time: (data && data.time) || '14:00',
      visitorName: (data && data.visitorName) || '来访业主',
      room: (data && data.room) || '',
      receiver: (data && data.receiver) || '',
      category: category,
      categoryLabel: C.RECEPTION_CATEGORY_LABELS[category] || '其他',
      content: (data && data.content) || '',
      resolution: '',
      propertyStatus: category === C.RECEPTION_CATEGORY.PROPERTY ? 'pending_dispatch' : null,
      propertyReply: '',
      propertyRepliedBy: '',
      propertyRepliedAt: '',
      fedProperty: false,
      fedOwner: false,
      done: false,
      needsPropertyFeedback: category === C.RECEPTION_CATEGORY.PROPERTY
    });
    return { id: id };
  }

  // 更新处理结果
  var resMatch = path.match(/\/api\/receptions\/records\/(\d+)\/resolution/);
  if (resMatch && method === 'PUT') {
    var rr = mock.receptionRecords.find(function (x) { return x.id === parseInt(resMatch[1]); });
    if (rr) rr.resolution = H.qs(path, 'resolution');
    return {};
  }

  // 反馈切换
  var followMatch = path.match(/\/api\/receptions\/records\/(\d+)\/follow/);
  if (followMatch && method === 'PUT') {
    var fr = mock.receptionRecords.find(function (x) { return x.id === parseInt(followMatch[1]); });
    var ff = H.qs(path, 'field');
    if (fr && ff) {
      fr[ff] = !fr[ff];
      fr.done = H.receptionDone(fr);
    }
    return {};
  }

  // 佐证：添加
  var evAddMatch = path.match(/\/api\/receptions\/records\/(\d+)\/evidences/);
  if (evAddMatch && method === 'POST') {
    var er = mock.receptionRecords.find(function (x) { return x.id === parseInt(evAddMatch[1]); });
    if (er) {
      if (!er.evidences) er.evidences = [];
      var ev = {
        id: H.nextId(er.evidences),
        fileName: H.qs(path, 'fileName') || '佐证.jpg',
        fileType: H.qs(path, 'fileType') || '照片'
      };
      er.evidences.push(ev);
      return ev;
    }
    return {};
  }

  // 佐证：删除
  var evRemoveMatch = path.match(/\/api\/receptions\/records\/(\d+)\/evidences\/(\d+)/);
  if (evRemoveMatch && method === 'DELETE') {
    var rr = mock.receptionRecords.find(function (x) { return x.id === parseInt(evRemoveMatch[1]); });
    if (rr && rr.evidences) {
      rr.evidences = rr.evidences.filter(function (e) { return e.id !== parseInt(evRemoveMatch[2]); });
    }
    return {};
  }

  // 删除
  var removeMatch = path.match(/\/api\/receptions\/records\/(\d+)$/);
  if (removeMatch && method === 'DELETE') {
    mock.receptionRecords = mock.receptionRecords.filter(function (r) { return r.id !== parseInt(removeMatch[1]); });
    return {};
  }

  return undefined;
}

module.exports = { handle: handle };
