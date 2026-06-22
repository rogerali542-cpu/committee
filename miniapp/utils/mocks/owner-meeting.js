// ═══════════════════════════════════════════════
// Mock 处理器 — 业主大会
// ═══════════════════════════════════════════════

var mock = require('../mock');
var H = require('../api/helpers');
var C = require('../constants');

// 业主大会投票状态
var ownerVoteStates = {};
// 到场登记: { [meetingId]: Set<unitId> }
var ownerAttendance = {};

function ensureOwnerAttendance(meetingId) {
  if (!ownerAttendance[meetingId]) ownerAttendance[meetingId] = {};
  return ownerAttendance[meetingId];
}
// 投票元数据: { [meetingId]: { [userRoleId]: { [topicId]: { choice, proxy, proofUrl, operatorName } } } }
var ownerVoteMeta = {};

function ensureOwnerVoteState(meetingId, userRoleId) {
  if (!ownerVoteStates[meetingId]) ownerVoteStates[meetingId] = {};
  if (!ownerVoteStates[meetingId][userRoleId]) ownerVoteStates[meetingId][userRoleId] = {};
  return ownerVoteStates[meetingId][userRoleId];
}

function ensureOwnerVoteMeta(meetingId, userRoleId) {
  if (!ownerVoteMeta[meetingId]) ownerVoteMeta[meetingId] = {};
  if (!ownerVoteMeta[meetingId][userRoleId]) ownerVoteMeta[meetingId][userRoleId] = {};
  return ownerVoteMeta[meetingId][userRoleId];
}

function isOwnerPublished(m) {
  return !!(m && ((m.publish && m.publish.published) ||
    (m.statusLine && m.statusLine.indexOf('已公示') >= 0)));
}

function ownerPublishDate(m) {
  if (m && m.publish && m.publish.publishDate) return m.publish.publishDate;
  var match = m && m.statusLine ? m.statusLine.match(/已公示（(.+?)）/) : null;
  return match ? match[1] : null;
}

function mockDetail(m) {
  var app2 = getApp();
  var activeRole = (app2 && app2.globalData && app2.globalData.activeRole) ? app2.globalData.activeRole : { id: 1 };
  var myRoleId = activeRole.id;
  var myUnits = H.getRepresentedUnits(myRoleId);

  var totalOwners = H.ownerTotal(m);
  var totalArea = H.ownerArea(m);
  var notify = H.ensureOwnerNotify(m);
  var ballot = m.needsVote !== false ? H.ensureOwnerBallot(m) : null;
  var record = m.stage === C.STAGE.ONGOING ? H.ensureOwnerRecord(m) : null;
  var notifyPct = Math.min(100, Math.round((notify.sent || 0) / totalOwners * 100));
  var ballotPct = ballot ? Math.min(100, Math.round((ballot.delivered || 0) / totalOwners * 100)) : 0;
  var needOwners = Math.floor(totalOwners / 2) + 1;
  var needArea = Math.floor(totalArea / 2) + 1;

  var recordInfo = record ? {
    presentOwners: record.presentOwners || 0,
    presentArea: record.presentArea || 0,
    presentAreaWan: ((record.presentArea || 0) / 10000).toFixed(1),
    totalAreaWan: (totalArea / 10000).toFixed(1),
    quorumOk: (record.presentOwners || 0) >= needOwners && (record.presentArea || 0) >= needArea,
    okOwners: (record.presentOwners || 0) >= needOwners,
    okArea: (record.presentArea || 0) >= needArea,
    pctOwners: Math.min(100, Math.round((record.presentOwners || 0) / totalOwners * 100)),
    pctArea: Math.min(100, Math.round((record.presentArea || 0) / totalArea * 100)),
    supervisorName: record.supervisorName || '街道办/居委会监督员',
    supervisorSigned: !!record.supervisorSigned,
    process: record.process || { designated: false, monitor: false, callout: false, tally: false },
    topics: (record.topics || []).map(function (t) {
      return {
        id: t.id, title: t.title, type: t.type,
        forOwners: t.forOwners || 0, agOwners: t.agOwners || 0, abOwners: t.abOwners || 0,
        forArea: t.forArea || 0, agArea: t.agArea || 0, abArea: t.abArea || 0,
        passOwners: (t.forOwners || 0) >= needOwners, passArea: (t.forArea || 0) >= needArea,
        passed: (t.forOwners || 0) >= needOwners && (t.forArea || 0) >= needArea
      };
    }),
    evidences: record.evidence || []
  } : null;

  return {
    id: m.id, title: m.title, meetingDate: m.meetingDate, meetingTime: m.meetingTime,
    location: m.location, type: m.type, stage: m.stage, compliance: m.compliance,
    meetingMode: m.meetingMode || 'normal',
    description: m.description || '大会说明（离线模式）',
    noticeDraft: m.noticeDraft || H.buildOwnerNoticeDraft(m),
    totalOwners: totalOwners, totalArea: totalArea,
    needsVote: m.needsVote !== false,
    areaWan: (totalArea / 10000).toFixed(2),
    materials: m._materials || [],
    // 房屋花名册（含代表人信息，用于投票校验）
    housingUnits: (mock.housingUnits || []).map(function (u) {
      var repRole = mock.allUsers.find(function (ur) { return ur.id === u.representativeUserId; });
      return {
        id: u.id, building: u.building, unitNo: u.unitNo, area: u.area,
        representativeUserId: u.representativeUserId,
        representativeName: repRole ? repRole.realName : ''
      };
    }),
    // 当前用户的投票状态和代表房屋
    myVotes: (ownerVoteStates[m.id] && ownerVoteStates[m.id][myRoleId]) || {},
    myVoteMeta: (ownerVoteMeta[m.id] && ownerVoteMeta[m.id][myRoleId]) || {},
    myUnits: myUnits || [],
    isRepresentative: myUnits && myUnits.length > 0,
    notifyInfo: m.stage === C.STAGE.PREPARING ? {
      deadlineStr: '2026-07-05', daysLeft: 25,
      sent: notify.sent || 0, total: totalOwners, pct: notifyPct,
      announced: !!notify.announced, contentComplete: !!notify.contentComplete, absDaysLeft: 25
    } : null,
    ballotInfo: m.stage === C.STAGE.PREPARING && ballot ? {
      deadlineStr: '2026-07-13', daysLeft: 33,
      delivered: ballot.delivered || 0, total: totalOwners, pct: ballotPct,
      recordsComplete: !!ballot.records, nonFaceAnnounce: !!ballot.nonFaceAnnounce,
      absDaysLeft: 33, daysLeftText: '33'
    } : null,
    recordInfo: recordInfo,
    publishInfo: m.stage === C.STAGE.ENDED && m.compliance !== C.COMPLIANCE.INVALID ? {
      published: isOwnerPublished(m),
      publishDate: ownerPublishDate(m),
      deadlineStr: '2025-09-23', daysLeft: -626,
      scoreState: isOwnerPublished(m) ? 'ontime' : 'overdue', daysLeftText: '-626'
    } : null
  };
}

function handleCreate(data) {
  var id = H.nextId(mock.ownerMeetings);
  var totalOwners = H.ownerTotal(null);   // 从 housingUnits 实时取
  var totalArea = H.ownerArea(null);
  var m = {
    id: id,
    title: data.title || '新建业主大会',
    meetingDate: data.meetingDate || H.todayStr(),
    meetingTime: data.meetingTime || '09:00',
    location: data.location || '待定',
    type: data.type || C.OWNER_TYPE.REGULAR,
    stage: C.STAGE.PREPARING,
    compliance: null,
    needsVote: data.needsVote !== false,
    totalOwners: totalOwners,
    totalArea: totalArea,
    description: data.description || '',
    notify: { sent: 0, announced: false, contentComplete: false }
  };
  // 存储用户创建的议题
  if (data.topics && data.topics.length) {
    var r = H.ensureOwnerRecord(m);
    r.topics = data.topics.map(function (t, i) {
      return {
        id: i + 1,
        title: t.title,
        type: t.type || C.TOPIC_TYPE.ORDINARY,
        decisionType: t.decisionType || 'simple',
        options: t.options || [],
        forOwners: 0, agOwners: 0, abOwners: 0,
        forArea: 0, agArea: 0, abArea: 0
      };
    });
  }
  m.noticeDraft = H.buildOwnerNoticeDraft(m);
  m._materials = [];
  if (m.needsVote) m.ballot = { delivered: 0, records: false, nonFaceAnnounce: false };
  m.statusLine = H.ownerStatusLine(m);
  mock.ownerMeetings.unshift(m);
  return { id: id };
}

function handleRemove(meetingId) {
  mock.ownerMeetings = mock.ownerMeetings.filter(function (m) { return m.id !== meetingId; });
  return {};
}

function handleAdvance(meetingId, path) {
  var m = mock.ownerMeetings.find(function (x) { return x.id === meetingId; });
  var action = H.qs(path, 'action');
  if (m && action === C.ADVANCE_ACTION.START) {
    m.stage = C.STAGE.ONGOING;
    m.meetingMode = H.qs(path, 'mode') || 'normal';
    H.ensureOwnerRecord(m);
  }
  if (m && action === C.ADVANCE_ACTION.END) {
    var r = H.ensureOwnerRecord(m);
    var okOwners = (r.presentOwners || 0) > H.ownerTotal(m) / 2;
    var okArea = (r.presentArea || 0) > H.ownerArea(m) / 2;
    m.stage = C.STAGE.ENDED;
    m.compliance = (!m.needsVote || (okOwners && okArea)) ? C.COMPLIANCE.VALID : C.COMPLIANCE.INVALID;
    m.publish = { published: false, publishDate: null };
  }
  return {};
}

function addOwnerNotification(userRoleId, meetingId, type, title, content) {
  var n = {
    id: H.nextId(mock.notifications),
    userRoleId: userRoleId,
    meetingId: meetingId,
    type: type,
    title: title,
    content: content,
    read: false,
    createdAt: new Date().toISOString()
  };
  mock.notifications.unshift(n);
}

function handle(method, path, data) {
  var basePath = path.split('?')[0];

  // 列表
  if (method === 'GET' && basePath === '/api/owner-meetings') {
    // 资料库：已归档（已结束且已公示）
    if (H.qs(path, 'archived') === 'true') {
      return mock.ownerMeetings
        .filter(function (m) { return m.stage === C.STAGE.ENDED && m.compliance !== C.COMPLIANCE.INVALID && isOwnerPublished(m); })
        .map(function (m) {
          return {
            id: m.id, title: m.title, meetingDate: m.meetingDate, meetingTime: m.meetingTime,
            location: m.location, type: m.type, compliance: m.compliance,
            published: true, publishDate: ownerPublishDate(m), materials: m._materials || []
          };
        });
    }
    var stage = H.qs(path, 'stage');
    var list = stage ? mock.ownerMeetings.filter(function (m) { return m.stage === stage; }) : mock.ownerMeetings;
    // 已归档（已结束+已公示）从管理列表移除，只在资料库可见
    list = list.filter(function (m) { return !(m.stage === C.STAGE.ENDED && isOwnerPublished(m)); });
    return list.map(function (m) {
      return {
        id: m.id, title: m.title, meetingDate: m.meetingDate, meetingTime: m.meetingTime,
        location: m.location, type: m.type, stage: m.stage, compliance: m.compliance,
        needsVote: m.needsVote, totalOwners: m.totalOwners, totalArea: m.totalArea,
        description: m.description, statusLine: H.ownerStatusLine(m)
      };
    });
  }
  if (method === 'GET' && basePath === '/api/owner-meetings/stats') return H.calcOwnerStats();

  // 详情
  var detailMatch = path.match(/\/api\/owner-meetings\/(\d+)$/);
  if (detailMatch && method === 'GET') {
    var m = mock.ownerMeetings.find(function (x) { return x.id === parseInt(detailMatch[1]); });
    if (m) return mockDetail(m);
    return null;
  }

  // create
  if (method === 'POST' && basePath === '/api/owner-meetings') return handleCreate(data || {});

  // update（准备阶段编辑基本信息）
  var updateMatch = path.match(/\/api\/owner-meetings\/(\d+)$/);
  if (updateMatch && method === 'PUT') {
    var um = mock.ownerMeetings.find(function (x) { return x.id === parseInt(updateMatch[1]); });
    if (um && data) {
      if (data.title) um.title = data.title;
      if (data.meetingDate) um.meetingDate = data.meetingDate;
      if (data.meetingTime) um.meetingTime = data.meetingTime;
      if (data.location) um.location = data.location;
      if (typeof data.description !== 'undefined') um.description = data.description;
      um.noticeDraft = H.buildOwnerNoticeDraft(um);
      return {};
    }
    return {};
  }

  // remove
  var removeMatch = path.match(/\/api\/owner-meetings\/(\d+)$/);
  if (removeMatch && method === 'DELETE') return handleRemove(parseInt(removeMatch[1]));

  // advance
  var advMatch = path.match(/\/api\/owner-meetings\/(\d+)\/advance/);
  if (advMatch && method === 'POST') return handleAdvance(parseInt(advMatch[1]), path);

  // notify toggle
  var notifyMatch = path.match(/\/api\/owner-meetings\/(\d+)\/notify/);
  if (notifyMatch && method === 'PUT') {
    var nm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(notifyMatch[1]); });
    var n = nm && H.ensureOwnerNotify(nm);
    var field = H.qs(path, 'field');
    if (n && field === 'announced') n.announced = !n.announced;
    if (n && field === 'contentComplete') n.contentComplete = !n.contentComplete;
    return {};
  }

  // notify-all（遍历房屋花名册，给所有产权人发通知，去重）
  var notifyAllMatch = path.match(/\/api\/owner-meetings\/(\d+)\/notify-all/);
  if (notifyAllMatch && method === 'POST') {
    var nam = mock.ownerMeetings.find(function (x) { return x.id === parseInt(notifyAllMatch[1]); });
    if (nam) {
      var totalOwners = H.ownerTotal(nam);
      H.ensureOwnerNotify(nam).sent = totalOwners;
      var units = require('../mock').housingUnits;
      // 收集所有产权人（去重）
      var notified = {};
      units.forEach(function (u) {
        (u.ownerUserIds || []).forEach(function (uid) {
          if (!notified[uid]) {
            notified[uid] = true;
            addOwnerNotification(uid, nam.id, 'owner_notice',
              '业主大会通知',
              (nam.title || '业主大会') + ' 将于 ' + (nam.meetingDate || '') + ' ' + (nam.meetingTime || '') + ' 在 ' + (nam.location || '') + ' 召开。');
          }
        });
      });
    }
    return {};
  }

  // ballot toggle
  var ballotMatch = path.match(/\/api\/owner-meetings\/(\d+)\/ballot/);
  if (ballotMatch && method === 'PUT') {
    var bm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(ballotMatch[1]); });
    var b = bm && H.ensureOwnerBallot(bm);
    var bf = H.qs(path, 'field');
    if (b && bf === 'records') b.records = !b.records;
    if (b && bf === 'nonFaceAnnounce') b.nonFaceAnnounce = !b.nonFaceAnnounce;
    return {};
  }

  // ballot-all
  var ballotAllMatch = path.match(/\/api\/owner-meetings\/(\d+)\/ballot-all/);
  if (ballotAllMatch && method === 'POST') {
    var bam = mock.ownerMeetings.find(function (x) { return x.id === parseInt(ballotAllMatch[1]); });
    if (bam) H.ensureOwnerBallot(bam).delivered = H.ownerTotal(bam);
    return {};
  }

  // count adjust
  var countMatch = path.match(/\/api\/owner-meetings\/(\d+)\/count/);
  if (countMatch && method === 'PUT') {
    var cm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(countMatch[1]); });
    var r = cm && H.ensureOwnerRecord(cm);
    var cf = H.qs(path, 'field');
    var delta = parseInt(H.qs(path, 'delta')) || 0;
    if (r && cf === 'presentOwners') r.presentOwners = Math.max(0, Math.min(H.ownerTotal(cm), (r.presentOwners || 0) + delta));
    if (r && cf === 'presentArea') r.presentArea = Math.max(0, Math.min(H.ownerArea(cm), (r.presentArea || 0) + delta));
    return {};
  }

  // supervisor toggle
  var supMatch = path.match(/\/api\/owner-meetings\/(\d+)\/supervisor/);
  if (supMatch && method === 'POST') {
    var sm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(supMatch[1]); });
    if (sm) { var sr = H.ensureOwnerRecord(sm); sr.supervisorSigned = !sr.supervisorSigned; }
    return {};
  }

  // process toggle
  var procMatch = path.match(/\/api\/owner-meetings\/(\d+)\/process\/(\w+)/);
  if (procMatch && method === 'PUT') {
    var pm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(procMatch[1]); });
    if (pm) { var pp = H.ensureOwnerRecord(pm).process; pp[procMatch[2]] = !pp[procMatch[2]]; }
    return {};
  }

  // add topic
  var topicMatch = basePath.match(/\/api\/owner-meetings\/(\d+)\/topics$/);
  if (topicMatch && method === 'POST') {
    var tm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(topicMatch[1]); });
    if (tm) {
      if (tm.stage !== C.STAGE.ONGOING) throw new Error('仅大会进行中可添加表决议题');
      if (tm.needsVote === false) throw new Error('本次大会不涉及表决事项，不能添加议题');
      var tr = H.ensureOwnerRecord(tm);
      var title = (H.qs(path, 'title') || '').trim();
      if (!title) throw new Error('议题名称不能为空');
      var tid = H.nextId(tr.topics);
      var topic = {
        id: tid, title: title,
        type: H.qs(path, 'type') || C.TOPIC_TYPE.ORDINARY,
        forOwners: 0, agOwners: 0, abOwners: 0,
        forArea: 0, agArea: 0, abArea: 0
      };
      tr.topics.push(topic);
      return topic;
    }
    return {};
  }

  // vote（代表人线上投票）
  var ownerVoteMatch = path.match(/\/api\/owner-meetings\/(\d+)\/topics\/(\d+)\/vote/);
  if (ownerVoteMatch && method === 'PUT') {
    var vmId = parseInt(ownerVoteMatch[1]);
    var vtId = parseInt(ownerVoteMatch[2]);
    var choice = H.qs(path, 'choice');
    var vm = mock.ownerMeetings.find(function (x) { return x.id === vmId; });
    if (!vm) return {};

    // 获取当前用户身份
    var app = getApp();
    var activeRole = (app && app.globalData && app.globalData.activeRole) ? app.globalData.activeRole : { id: 1 };
    var userRoleId = activeRole.id;

    // 校验是否为代表人
    var myUnits = H.getRepresentedUnits(userRoleId);
    if (!myUnits || !myUnits.length) return { error: '你不是任何房屋的代表人，无法投票' };

    // 记录投票
    var vs = ensureOwnerVoteState(vmId, userRoleId);
    if (vs[vtId]) return { error: '已投过票，不可更改' };
    vs[vtId] = choice;

    // 按户聚合更新票数
    var unitsCount = myUnits.length;
    var unitsArea = myUnits.reduce(function (s, u) { return s + (u.area || 0); }, 0);
    var r = H.ensureOwnerRecord(vm);
    var tp = (r.topics || []).find(function (t) { return String(t.id) === String(vtId); });
    if (tp) {
      if (choice === 'for') {
        tp.forOwners = (tp.forOwners || 0) + unitsCount;
        tp.forArea = (tp.forArea || 0) + unitsArea;
      } else if (choice === 'against') {
        tp.agOwners = (tp.agOwners || 0) + unitsCount;
        tp.agArea = (tp.agArea || 0) + unitsArea;
      } else {
        tp.abOwners = (tp.abOwners || 0) + unitsCount;
        tp.abArea = (tp.abArea || 0) + unitsArea;
      }
    }
    return { unitsCount: unitsCount, unitsArea: unitsArea };
  }

  // attendance（到场登记）
  var attMatch = path.match(/\/api\/owner-meetings\/(\d+)\/attendance$/);
  if (attMatch && method === 'GET') {
    var attm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(attMatch[1]); });
    var attState = ensureOwnerAttendance(attm ? attm.id : 0);
    return (mock.housingUnits || []).map(function (u) {
      var repRole = mock.allUsers.find(function (ur) { return ur.id === u.representativeUserId; });
      return {
        unitId: u.id, building: u.building, unitNo: u.unitNo, area: u.area,
        representativeName: repRole ? repRole.realName : '',
        present: !!attState[u.id]
      };
    });
  }

  var attToggleMatch = path.match(/\/api\/owner-meetings\/(\d+)\/attendance\/(\d+)/);
  if (attToggleMatch && method === 'PUT') {
    var atmid = parseInt(attToggleMatch[1]);
    var unitId = parseInt(attToggleMatch[2]);
    var atstate = ensureOwnerAttendance(atmid);
    atstate[unitId] = !atstate[unitId];
    // 自动更新 presentOwners/presentArea
    var atm = mock.ownerMeetings.find(function (x) { return x.id === atmid; });
    if (atm) {
      var r2 = H.ensureOwnerRecord(atm);
      var units = require('../mock').housingUnits;
      var presentUnits = units.filter(function (u) { return atstate[u.id]; });
      r2.presentOwners = presentUnits.length;
      r2.presentArea = presentUnits.reduce(function (s, u) { return s + (u.area || 0); }, 0);
    }
    return { present: atstate[unitId] };
  }

  // proxy-vote（主席补录纸质票）
  var proxyVoteMatch = path.match(/\/api\/owner-meetings\/(\d+)\/proxy-vote/);
  if (proxyVoteMatch && method === 'POST') {
    var pvm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(proxyVoteMatch[1]); });
    if (!pvm || !data) return {};
    var unitId = data.unitId;
    var topicId = data.topicId;
    var choice = data.choice;
    var unit = (mock.housingUnits || []).find(function (u) { return u.id === unitId; });
    if (!unit) return { error: '房屋不存在' };
    var repId = unit.representativeUserId;

    // 检查是否已投
    var vs = ensureOwnerVoteState(pvm.id, repId);
    if (vs[topicId]) return { error: '该户代表已投过此议题，不可重复投票' };

    // 记录投票
    vs[topicId] = choice;
    var meta = ensureOwnerVoteMeta(pvm.id, repId);
    var app2 = getApp();
    var operator = (app2 && app2.globalData && app2.globalData.activeRole) ? app2.globalData.activeRole.realName : '主任';
    meta[topicId] = { choice: choice, proxy: true, proofUrl: data.proofUrl || '', proofName: data.proofName || '', operatorName: operator };

    // 按户聚合更新票数
    var unitsCount = 1;
    var unitsArea = unit.area || 0;
    var r = H.ensureOwnerRecord(pvm);
    var tp = (r.topics || []).find(function (t) { return String(t.id) === String(topicId); });
    if (tp) {
      if (choice === 'for') {
        tp.forOwners = (tp.forOwners || 0) + unitsCount;
        tp.forArea = (tp.forArea || 0) + unitsArea;
      } else if (choice === 'against') {
        tp.agOwners = (tp.agOwners || 0) + unitsCount;
        tp.agArea = (tp.agArea || 0) + unitsArea;
      } else {
        tp.abOwners = (tp.abOwners || 0) + unitsCount;
        tp.abArea = (tp.abArea || 0) + unitsArea;
      }
    }
    return { unitId: unitId, unitsCount: unitsCount, unitsArea: unitsArea };
  }

  // vote-status（每户投票情况）
  var voteStatusMatch = path.match(/\/api\/owner-meetings\/(\d+)\/vote-status/);
  if (voteStatusMatch && method === 'GET') {
    var vsm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(voteStatusMatch[1]); });
    var record = vsm ? H.ensureOwnerRecord(vsm) : null;
    var topicIds = (record && record.topics) ? record.topics.map(function (t) { return String(t.id); }) : [];
    return (mock.housingUnits || []).map(function (u) {
      var repVs = (ownerVoteStates[vsm.id] && ownerVoteStates[vsm.id][u.representativeUserId]) || {};
      var repMeta = (ownerVoteMeta[vsm.id] && ownerVoteMeta[vsm.id][u.representativeUserId]) || {};
      var repRole = mock.allUsers.find(function (ur) { return ur.id === u.representativeUserId; });
      var votedTopics = topicIds.filter(function (tid) { return !!repVs[tid]; });
      var allVoted = topicIds.length > 0 && votedTopics.length === topicIds.length;
      return {
        unitId: u.id, building: u.building, unitNo: u.unitNo, area: u.area,
        representativeUserId: u.representativeUserId,
        representativeName: repRole ? repRole.realName : '',
        allVoted: allVoted,
        votedTopics: votedTopics.map(Number),
        votes: topicIds.reduce(function (acc, tid) {
          if (repVs[tid]) acc[tid] = { choice: repVs[tid], proxy: !!(repMeta[tid] && repMeta[tid].proxy), operatorName: (repMeta[tid] && repMeta[tid].operatorName) || '' };
          return acc;
        }, {})
      };
    });
  }

  // vote adjust
  var voteAdjMatch = path.match(/\/api\/owner-meetings\/(\d+)\/topics\/(\d+)\/vote-adj/);
  if (voteAdjMatch && method === 'PUT') {
    var vm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(voteAdjMatch[1]); });
    var vr = vm && H.ensureOwnerRecord(vm);
    var vtp = vr && vr.topics.find(function (t) { return String(t.id) === voteAdjMatch[2]; });
    var vf = H.qs(path, 'field');
    var vdelta = parseInt(H.qs(path, 'delta')) || 0;
    if (vtp && vf) {
      var vmax = vf.indexOf('Area') >= 0 ? H.ownerArea(vm) : H.ownerTotal(vm);
      vtp[vf] = Math.max(0, Math.min(vmax, (Number(vtp[vf]) || 0) + vdelta));
    }
    return {};
  }

  // add evidence
  var evMatch = path.match(/\/api\/owner-meetings\/(\d+)\/evidences/);
  if (evMatch && method === 'POST') {
    var em = mock.ownerMeetings.find(function (x) { return x.id === parseInt(evMatch[1]); });
    if (em) {
      var evs = H.ensureOwnerRecord(em).evidence;
      var evItem = {
        id: H.nextId(evs),
        name: H.qs(path, 'fileName') || '纸质佐证.jpg',
        type: H.qs(path, 'fileType') || '照片'
      };
      evs.push(evItem);
    }
    return {};
  }

  // remove evidence
  var evRemoveMatch = path.match(/\/api\/owner-meetings\/(\d+)\/evidences\/(\d+)/);
  if (evRemoveMatch && method === 'DELETE') {
    var erm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(evRemoveMatch[1]); });
    if (erm) {
      var revs = H.ensureOwnerRecord(erm).evidence;
      var evId = parseInt(evRemoveMatch[2]);
      H.ensureOwnerRecord(erm).evidence = revs.filter(function (e) { return e.id !== evId; });
    }
    return {};
  }

  // materials (准备阶段上传)
  var matAddMatch = path.match(/\/api\/owner-meetings\/(\d+)\/materials$/);
  if (matAddMatch && method === 'POST') {
    var matm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(matAddMatch[1]); });
    if (matm) {
      if (!matm._materials) matm._materials = [];
      matm._materials.push({
        name: H.qs(path, 'fileName') || '未命名材料',
        sizeText: H.qs(path, 'sizeText') || '',
        content: '（上传文件，暂无预览内容）'
      });
      return {};
    }
    return {};
  }

  var matRemoveMatch = path.match(/\/api\/owner-meetings\/(\d+)\/materials\/(\d+)$/);
  if (matRemoveMatch && method === 'DELETE') {
    var matrm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(matRemoveMatch[1]); });
    if (matrm && matrm._materials) {
      var matIdx = parseInt(matRemoveMatch[2]);
      matrm._materials = matrm._materials.filter(function (_, i) { return i !== matIdx; });
    }
    return {};
  }

  // update-notice-draft
  var noticeEditMatch = path.match(/\/api\/owner-meetings\/(\d+)\/notice-draft/);
  if (noticeEditMatch && method === 'PUT') {
    var nem = mock.ownerMeetings.find(function (x) { return x.id === parseInt(noticeEditMatch[1]); });
    if (nem && data) {
      nem.noticeDraft = { title: data.title || '', content: data.content || '', status: 'edited' };
      return {};
    }
    return {};
  }

  // minutes（自动生成 + 手动编辑）
  var minutesMatch = path.match(/\/api\/owner-meetings\/(\d+)\/minutes/);
  if (minutesMatch && method === 'GET') {
    var mid = parseInt(minutesMatch[1]);
    var mt = mock.ownerMeetings.find(function (x) { return x.id === mid; });
    var p = require('../perm');
    var publicMinutes = mt && mt.stage === C.STAGE.ENDED && mt.compliance !== C.COMPLIANCE.INVALID &&
      isOwnerPublished(mt);
    if (p.isExternal() && !publicMinutes) throw new Error('Minutes are not published yet');
    if (mt && mt._minutes) return mt._minutes;
    var detail = mockDetail(mt);
    var mLines = [];
    mLines.push('业主大会会议纪要');
    mLines.push('');
    mLines.push('大会名称：' + (mt ? mt.title : ''));
    mLines.push('大会时间：' + (mt ? mt.meetingDate : '') + ' ' + (mt ? mt.meetingTime : ''));
    mLines.push('大会地点：' + (mt ? mt.location : ''));
    mLines.push('大会类型：' + (mt && mt.type === 'special' ? '临时大会' : '定期大会'));
    mLines.push('');
    if (detail.recordInfo) {
      mLines.push('一、到场情况');
      mLines.push('到场 ' + (detail.recordInfo.presentOwners || 0) + ' 户，面积 ' + (detail.recordInfo.presentAreaWan || '0') + ' 万㎡');
      mLines.push('总户数 ' + (detail.totalOwners || 0) + '，总面积 ' + (detail.areaWan || '0') + ' 万㎡');
    }
    if (detail.recordInfo && detail.recordInfo.topics && detail.recordInfo.topics.length) {
      mLines.push('');
      mLines.push('二、表决情况');
      detail.recordInfo.topics.forEach(function (t, i) {
        mLines.push((i+1) + '. ' + t.title + '（' + (t.type === 'major' ? '重大事项' : '普通决议') + '）');
        mLines.push('   投票结果：赞成 ' + t.forOwners + ' 户/' + (t.forAreaWan || '0') + ' 万㎡，反对 ' + (t.agOwners || 0) + ' 户/' + (t.agAreaWan || '0') + ' 万㎡，弃权 ' + (t.abOwners || 0) + ' 户/' + (t.abAreaWan || '0') + ' 万㎡');
        mLines.push('   ' + (t.passed ? '✓ 决议通过' : '✕ 未通过'));
      });
    }
    mLines.push('');
    mLines.push('三、大会结论');
    mLines.push(mt && mt.compliance === 'valid' ? '大会有效，决议按表决结果执行。' : mt && mt.compliance === 'flawed' ? '大会有效（部分决议说明），记录归档。' : mt && mt.compliance === 'invalid' ? '大会无效，决议不生效。' : '待判定');
    if (mt) mt._minutes = mLines.join('\n');
    return mLines.join('\n');
  }
  if (minutesMatch && method === 'PUT') {
    var mm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(minutesMatch[1]); });
    if (mm && data && data.text) {
      if (isOwnerPublished(mm)) throw new Error('Published minutes cannot be edited directly');
      mm._minutes = data.text;
      return {};
    }
    return {};
  }

  // compliance toggle
  var compMatch = path.match(/\/api\/owner-meetings\/(\d+)\/compliance/);
  if (compMatch && method === 'PUT') {
    var cm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(compMatch[1]); });
    if (cm) cm.compliance = H.qs(path, 'status');
    return {};
  }

  // publish
  var pubMatch = path.match(/\/api\/owner-meetings\/(\d+)\/publish/);
  if (pubMatch && method === 'POST') {
    var pubm = mock.ownerMeetings.find(function (x) { return x.id === parseInt(pubMatch[1]); });
    if (pubm) pubm.publish = { published: true, publishDate: H.todayStr() };
    return {};
  }

  return undefined;
}

module.exports = { handle: handle, isOwnerPublished: isOwnerPublished, ownerPublishDate: ownerPublishDate };
