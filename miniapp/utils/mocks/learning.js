// ═══════════════════════════════════════════════
// Mock 处理器 — 业务学习
// ═══════════════════════════════════════════════

var mock = require('../mock');
var H = require('../api/helpers');
var C = require('../constants');

function handle(method, path, data) {
  var basePath = path.split('?')[0];

  // 列表
  if (method === 'GET' && basePath === '/api/learning') {
    var stage = H.qs(path, 'stage');
    var type = H.qs(path, 'type');
    var items = mock.learningList;
    if (type === 'training') items = items.filter(function (i) { return i.type === C.LEARNING_TYPE.STREET || i.type === C.LEARNING_TYPE.SPECIAL; });
    else if (type) items = items.filter(function (i) { return i.type === type; });
    if (stage) items = items.filter(function (i) { return i.stage === stage; });
    return items;
  }

  // 统计
  if (method === 'GET' && basePath === '/api/learning/counts') return H.calcLearningCounts(H.qs(path, 'type'));

  // 签到（仅进行中阶段）
  var signInMatch = path.match(/\/api\/learning\/(\d+)\/sign-in/);
  if (signInMatch && method === 'PUT') {
    var si = mock.learningList.find(function (x) { return x.id === parseInt(signInMatch[1]); });
    if (si && si.stage === C.STAGE.ONGOING && si.signIns) {
      var app = getApp();
      var myName = (app && app.globalData && app.globalData.activeRole) ? app.globalData.activeRole.realName : '';
      if (myName && si.signIns.hasOwnProperty(myName)) {
        si.signIns[myName] = !si.signIns[myName];
      }
    }
    return {};
  }

  // 通知全员（待开阶段）—— 只标记通知，不碰签到数据
  var notifyAllMatch = path.match(/\/api\/learning\/(\d+)\/notify-all/);
  if (notifyAllMatch && method === 'POST') {
    var na = mock.learningList.find(function (x) { return x.id === parseInt(notifyAllMatch[1]); });
    if (na) { na.notified = true; }
    return {};
  }

  // 开始学习 —— 重置签到表
  var startMatch = path.match(/\/api\/learning\/(\d+)\/start/);
  if (startMatch && method === 'PUT') {
    var item = mock.learningList.find(function (x) { return x.id === parseInt(startMatch[1]); });
    if (item) {
      item.stage = C.STAGE.ONGOING;
      item.progress = Math.max(item.progress || 0, 10);
      // 重置签到状态，所有人未签到
      if (item.signIns) {
        Object.keys(item.signIns).forEach(function (k) { item.signIns[k] = false; });
      }
    }
    return {};
  }

  // 完成
  var finishMatch = path.match(/\/api\/learning\/(\d+)\/finish/);
  if (finishMatch && method === 'PUT') {
    var fi = mock.learningList.find(function (x) { return x.id === parseInt(finishMatch[1]); });
    if (fi) { fi.stage = C.STAGE.ENDED; fi.progress = 100; }
    return {};
  }

  // 删除
  var removeMatch = path.match(/\/api\/learning\/(\d+)$/);
  if (removeMatch && method === 'DELETE') {
    mock.learningList = mock.learningList.filter(function (x) { return x.id !== parseInt(removeMatch[1]); });
    return {};
  }

  // 佐证：添加
  var evAddMatch = path.match(/\/api\/learning\/(\d+)\/evidences/);
  if (evAddMatch && method === 'POST') {
    var lr = mock.learningList.find(function (x) { return x.id === parseInt(evAddMatch[1]); });
    if (lr) {
      if (!lr.evidences) lr.evidences = [];
      var ev = { id: H.nextId(lr.evidences), fileName: H.qs(path, 'fileName') || '佐证.jpg', fileType: H.qs(path, 'fileType') || '照片' };
      lr.evidences.push(ev);
      return ev;
    }
    return {};
  }

  // 佐证：删除
  var evRemoveMatch = path.match(/\/api\/learning\/(\d+)\/evidences\/(\d+)/);
  if (evRemoveMatch && method === 'DELETE') {
    var lr2 = mock.learningList.find(function (x) { return x.id === parseInt(evRemoveMatch[1]); });
    if (lr2 && lr2.evidences) {
      lr2.evidences = lr2.evidences.filter(function (e) { return e.id !== parseInt(evRemoveMatch[2]); });
    }
    return {};
  }

  // 创建
  if (method === 'POST' && basePath === '/api/learning') {
    var newId = H.nextId(mock.learningList);
    var attendeesStr = (data && data.attendees) || '';
    var names = attendeesStr.split(/[,，、\s]+/).filter(function (n) { return n.trim(); });
    var signIns = {};
    names.forEach(function (n) { signIns[n.trim()] = false; });
    var item = {
      id: newId,
      title: (data && data.title) || '新建学习活动',
      date: (data && data.date) || H.todayStr(),
      time: (data && data.time) || '14:00',
      location: (data && data.location) || '社区活动室',
      trainer: (data && data.trainer) || '',
      attendees: attendeesStr,
      description: (data && data.description) || '',
      type: (data && data.type) || C.LEARNING_TYPE.INTERNAL,
      stage: C.STAGE.PREPARING,
      progress: 0,
      notified: false,
      signIns: signIns
    };
    mock.learningList.unshift(item);
    return { id: newId };
  }

  return undefined;
}

module.exports = { handle: handle };
