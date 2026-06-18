// ═══════════════════════════════════════════════
// Mock 处理器 — 委员会会议
// ═══════════════════════════════════════════════

var mock = require('../mock');
var H = require('../api/helpers');
var C = require('../constants');

var proxyStates = {};

// 送达状态追踪: { [meetingId]: { [userRoleId]: { notice: boolean, material: boolean } } }
var deliveryStates = {};

function ensureDeliveryState(meetingId, memberIds, defaultDone) {
  if (!deliveryStates[meetingId]) deliveryStates[meetingId] = {};
  var ds = deliveryStates[meetingId];
  (memberIds || []).forEach(function (uid) {
    if (!ds[uid]) ds[uid] = { notice: !!defaultDone, material: !!defaultDone, noticeRead: !!defaultDone, materialRead: !!defaultDone };
  });
  return ds;
}

function getMeetingMemberIds(m) {
  if (m && Array.isArray(m.memberIds)) {
    return m.memberIds.map(function (id) { return Number(id); });
  }
  return getMockCommitteeMembers().map(function (item) { return item.userRoleId; });
}

function getDeliverySummary(m) {
  var memberIds = getMeetingMemberIds(m);
  var ds = deliveryStates[m.id] || {};
  var noticeDone = memberIds.filter(function (id) { return ds[id] && ds[id].notice; }).length;
  var materialDone = memberIds.filter(function (id) { return ds[id] && ds[id].material; }).length;
  var total = memberIds.length;
  return '通知 ' + noticeDone + '/' + total + ' · 材料 ' + materialDone + '/' + total;
}

function addNotification(userRoleId, meetingId, type, title, content) {
  var n = {
    id: H.nextId(mock.notifications),
    userRoleId: userRoleId,
    meetingId: meetingId,
    type: type,        // 'notice' | 'material'
    title: title,
    content: content,
    read: false,
    createdAt: new Date().toISOString()
  };
  mock.notifications.unshift(n);
  return n;
}

function upsertMeetingNotice(userRoleId, meeting, deliveryState) {
  var existing = mock.notifications.find(function (n) {
    return n.userRoleId === userRoleId && n.meetingId === meeting.id && n.type === 'notice';
  });
  var title = deliveryState && deliveryState.material ? '业委会会议通知与材料' : '业委会会议通知';
  var content = (meeting.title || '业委会会议') + ' 将于 ' + (meeting.meetingDate || '') + ' ' + (meeting.meetingTime || '') +
    ' 在 ' + (meeting.location || '') + ' 召开。' +
    (deliveryState && deliveryState.material ? '会议材料已同步送达，请查看通知正文和附件。' : '会议材料送达后会在本通知中更新。');
  if (existing) {
    existing.title = title;
    existing.content = content;
    existing.read = false;
    existing.createdAt = new Date().toISOString();
    return existing;
  }
  return addNotification(userRoleId, meeting.id, 'notice', title, content);
}

function getMemberName(userRoleId) {
  var members = getMockCommitteeMembers();
  var m = members.find(function (x) { return x.userRoleId === userRoleId; });
  return m ? m.name : '';
}

function proxyKey(meetingId, memberId) {
  return meetingId + '_' + memberId;
}

function getProxyState(meetingId, memberId) {
  var key = proxyKey(meetingId, memberId);
  if (!proxyStates[key]) proxyStates[key] = { signedIn: null, signed: null, topicVotes: {}, proofUrl: '', operatorName: '' };
  return proxyStates[key];
}

function getProxyVoteValues(meetingId, topicId) {
  return Object.keys(proxyStates).map(function (key) {
    var st = proxyStates[key];
    return st && st.topicVotes ? st.topicVotes[topicId] || st.topicVotes[String(topicId)] : null;
  }).filter(Boolean);
}

function getMockCommitteeMembers() {
  var names = ['张建国', '李秀英', '王志强', '赵丽娟', '刘海涛', '陈晓梅', '杨国华'];
  var roles = ['主任', '副主任', '委员', '委员', '委员', '委员', '委员'];
  var rooms = ['1号楼101', '1号楼102', '2号楼201', '2号楼202', '3号楼301', '3号楼302', '4号楼401'];
  return names.map(function (name, i) {
    return { userRoleId: i + 1, name: name, role: roles[i], roomNumber: rooms[i] };
  });
}

// GET 列表 + 注入个人状态
function injectPersonalState(list) {
  var appL = getApp();
  var myIdL = (appL && appL.globalData && appL.globalData.activeRole) ? appL.globalData.activeRole.id : 1;
  return list.map(function (m) {
    var st = H.getMyState(m.id);
    var prog = H.getPersonalProgress(m, st);
    var dsL = deliveryStates[m.id];
    var mydL = dsL && dsL[myIdL];
    var myNoticeUnread = m.stage === C.STAGE.PREPARING && !!(mydL && mydL.notice && !mydL.noticeRead);
    return {
      myNoticeUnread: myNoticeUnread,
      id: m.id,
      title: m.title,
      meetingDate: m.meetingDate,
      meetingTime: m.meetingTime,
      location: m.location,
      stage: m.stage,
      compliance: m.compliance,
      publish: m.publish || null,
      archived: !!m.archived,
      materials: m._materials || [],
      summaryLine: H.getPersonalSummary(m, st),
      stageDesc: H.getStageDesc(m),
      progress: prog.pct,
      progressLabel: prog.label
    };
  });
}

// 详情 mock
function mockDetail(m) {
  var st = H.getMyState(m.id);
  var allCommitteeMembers = getMockCommitteeMembers();
  var selectedIds = getMeetingMemberIds(m);
  var selectedMembers = allCommitteeMembers.filter(function (item) { return selectedIds.indexOf(Number(item.userRoleId)) >= 0; });
  var members = selectedMembers.map(function (item) { return item.name; });
  var roles = selectedMembers.map(function (item) { return item.role; });
  var rooms = selectedMembers.map(function (item) { return item.roomNumber; });
  var memberIds = selectedMembers.map(function (item) { return item.userRoleId; });
  var app2 = getApp();
  var activeRole = (app2 && app2.globalData && app2.globalData.activeRole) ? app2.globalData.activeRole : { role: '委员', id: 1 };
  var role = activeRole.role;
  var myRoleId = activeRole.id;
  var myIndex = memberIds.indexOf(Number(myRoleId));
  var isExternalRole = role === '业主' || role === '物业';
  var isPublicMinutes = m.stage === C.STAGE.ENDED && m.compliance !== C.COMPLIANCE.INVALID &&
    m.publish && m.publish.published;
  var hideInternalRecord = isExternalRole && !isPublicMinutes;

  var ds = ensureDeliveryState(m.id, memberIds, m.stage !== C.STAGE.PREPARING);
  var memberDeliveries = members.map(function (name, i) {
    var uid = memberIds[i];
    var d = ds[uid] || { notice: false, material: false };
    return { userRoleId: uid, name: name, role: roles[i], noticeDelivered: d.notice, materialDelivered: d.material, noticeRead: !!d.noticeRead };
  });
  var noticeDone = memberDeliveries.filter(function (d) { return d.noticeDelivered; }).length;
  var materialDone = memberDeliveries.filter(function (d) { return d.materialDelivered; }).length;
  var readDone = memberDeliveries.filter(function (d) { return d.noticeRead; }).length;
  var allDelivered = memberDeliveries.length > 0 && memberDeliveries.every(function (d) { return d.noticeDelivered && d.materialDelivered; });

  var attendances = members.map(function (name, i) {
    var pst = getProxyState(m.id, memberIds[i]);
    var signedInDefault = i === myIndex ? st.signedIn : (i < 5);
    var signedDefault = i === myIndex ? st.signed : (i < 3);
    return {
      userRoleId: memberIds[i], name: name, role: roles[i], roomNumber: rooms[i],
      signedIn: typeof pst.signedIn === 'boolean' ? pst.signedIn : signedInDefault,
      signed: typeof pst.signed === 'boolean' ? pst.signed : signedDefault,
      isSelf: i === myIndex,
      isProxy: !!pst.proofUrl,
      operatorName: pst.operatorName,
      proofUrl: pst.proofUrl
    };
  });

  var builtInTopics = [
    { id: 1, title: '上月物业服务情况通报', type: 'notice', decisionType: 'none' },
    { id: 2, title: '小区停车管理优化方案讨论', type: 'discussion', decisionType: 'none' },
    { id: 3, title: '公共设施维修专项资金使用方案', type: 'decision', decisionType: 'simple' },
    { id: 4, title: '小区门禁系统改造方案', type: 'decision', decisionType: 'multi_choice',
      options: [
        { id: 1, label: '完全更换为智能门禁' },
        { id: 2, label: '保留现有门禁，加装人脸识别' },
        { id: 3, label: '暂不改造，维持现状' }
      ]
    }
  ];
  // 如果会议有自定义议题（创建时传入），只用自定义的；否则用内置示例
  var storedTopics = ensureTopics(m);
  var allTopics = storedTopics.length ? storedTopics : builtInTopics.concat(storedTopics);

  var topics = m.stage !== C.STAGE.PREPARING ? allTopics.map(function (t) {
    var myV = st.topicVotes[t.id] || null;
    var dt = t.decisionType || 'simple';
    var voteRequired = t.type !== 'notice' && t.type !== 'discussion';
    if (!voteRequired) {
      return {
        id: t.id, title: t.title, type: t.type, decisionType: 'none', voteRequired: false,
        options: [], total: 7, need: 4, passed: true, status: 'recorded',
        text: t.type === 'notice' ? '通报记录' : '讨论记录',
        myVote: null
      };
    }

    if (dt === 'multi_choice') {
      var proxyVals = getProxyVoteValues(m.id, t.id);
      // 多选一：计算每个选项的票数
      var opts = (t.options || []).map(function (o) {
        var votes = o.id === 1 ? 2 : (o.id === 2 ? 1 : 0); // 模拟其他委员的票
        if (myV && parseInt(myV) === o.id) votes += 1; // 我的票
        votes += proxyVals.filter(function (v) { return String(v) === String(o.id); }).length;
        return { id: o.id, label: o.label, votes: votes };
      });
      var total = 7;
      var need = 4;
      var leading = opts.reduce(function (a, b) { return a.votes > b.votes ? a : b; }, opts[0]);
      var passed = leading.votes >= need;
      var voted = opts.reduce(function (s, o) { return s + o.votes; }, 0);
      return {
        id: t.id, title: t.title, type: t.type, decisionType: dt, voteRequired: true,
        options: opts,
        total: total, need: need, passed: passed,
        status: passed ? 'passed' : (voted < total ? 'pending' : 'failed'),
        text: passed ? '决议通过：' + leading.label : '待投票',
        myVote: myV,
        myVoteLabel: myV ? (t.options || []).find(function (o) { return String(o.id) === String(myV); }) || {} : null
      };
    }

    // simple（默认）
    var proxySimpleVals = getProxyVoteValues(m.id, t.id);
    var forBase = t.id === 1 ? 3 : (t.id === 2 ? 2 : 1);
    var agBase = t.id === 1 ? 1 : 0;
    var forV = forBase + (myV === C.VOTE_CHOICE.FOR ? 1 : 0) + proxySimpleVals.filter(function (v) { return v === C.VOTE_CHOICE.FOR; }).length;
    var agV = agBase + (myV === C.VOTE_CHOICE.AGAINST ? 1 : 0) + proxySimpleVals.filter(function (v) { return v === C.VOTE_CHOICE.AGAINST; }).length;
    var abV = (myV === C.VOTE_CHOICE.ABSTAIN ? 1 : 0) + proxySimpleVals.filter(function (v) { return v === C.VOTE_CHOICE.ABSTAIN; }).length;
    var simpleVoted = 3 + (myV ? 1 : 0) + proxySimpleVals.length;
    var simplePassed = forV >= 4;
    return {
      id: t.id, title: t.title, type: t.type, decisionType: dt, voteRequired: true,
      forVotes: forV, agVotes: agV,
      abVotes: abV,
      total: 7, need: 4, passed: simplePassed,
      status: simplePassed ? 'passed' : (simpleVoted < 7 ? 'pending' : 'failed'),
      text: simplePassed ? '决议通过' : '待继续表决', myVote: myV
    };
  }) : [];

  var checks = m.stage === C.STAGE.ONGOING ? [
    { label: '委员确认参会过半', detail: attendances.filter(function (a) { return a.signedIn; }).length + '/' + members.length + '（需≥' + (Math.floor(members.length / 2) + 1) + '）', ok: attendances.filter(function (a) { return a.signedIn; }).length >= (Math.floor(members.length / 2) + 1) },
    { label: '线下佐证归档', detail: '待上传', ok: false }
  ] : [];

  var isCompleted = st.signedIn && Object.keys(st.topicVotes).length >= 3;
  var taskLevel = m.stage === C.STAGE.PREPARING ? 'wait' : m.stage === C.STAGE.ONGOING ? (isCompleted ? 'ok' : 'todo') : 'ok';
  var taskLabels = [];
  if (m.stage === C.STAGE.ONGOING && !isCompleted) {
    if (!st.signedIn) taskLabels.push('我要确认参会');
    var unvoted = allTopics.filter(function (t) { return !st.topicVotes[t.id]; }).length;
    if (unvoted > 0) taskLabels.push('待表决 ' + unvoted + ' 项');
  }
  var taskItems = m.stage === C.STAGE.PREPARING ? ['待委员送达通知'] : m.stage === C.STAGE.ONGOING ? (taskLabels.length ? taskLabels : ['参会操作已完成']) : ['会议已结束'];

  // 视图判断（基于权限）
  var p = require('../perm');
  var userView;
  if (p.isChair()) userView = C.USER_VIEW.CHAIR;
  else if (p.can('committee.sign_in')) userView = C.USER_VIEW.MEMBER;
  else if (p.can('view.public')) userView = role === C.ROLE.PROPERTY ? C.USER_VIEW.PROPERTY : C.USER_VIEW.OWNER;
  else userView = C.USER_VIEW.MEMBER;

  var perms = {
    canSignIn: p.can('committee.sign_in'), canSign: p.can('committee.sign'),
    canVote: p.can('committee.vote'), canCreate: p.can('committee.create'),
    canAdvance: p.can('committee.advance'), canDelivery: p.can('committee.delivery'),
    canEvidence: p.can('committee.evidence'), canTopic: p.can('committee.topic'),
    canPublish: p.can('committee.publish')
  };

  return {
    id: m.id, title: m.title, meetingDate: m.meetingDate, meetingTime: m.meetingTime,
    location: m.location, description: m.description || '', stage: m.stage, compliance: m.compliance,
    meetingMode: m.meetingMode || 'quick',
    noticeDraft: m.noticeDraft || H.buildCommitteeNoticeDraft(m),
    userRole: role, userView: userView,
    taskLevel: taskLevel, taskTitle: taskLevel === 'todo' ? '待处理' : '待关注',
    taskItems: taskItems, taskItemsText: taskItems.join(' / '),
    taskHint: '', flowNodeText: m.stage === C.STAGE.PREPARING ? '准备阶段·待开始' : m.stage === C.STAGE.ONGOING ? '进行中·确认参会/表决中' : '会议结束',
    delivery: hideInternalRecord ? null : {
      deadlineStr: '2026-06-08', daysLeft: -2,
      allDone: allDelivered, noDate: false,
      noticeDone: noticeDone, materialDone: materialDone, readDone: readDone,
      total: members.length, memberDeliveries: memberDeliveries
    },
    myDelivery: m.stage === C.STAGE.PREPARING && myIndex >= 0 ? {
      noticeDelivered: memberDeliveries[myIndex] ? memberDeliveries[myIndex].noticeDelivered : false,
      materialDelivered: memberDeliveries[myIndex] ? memberDeliveries[myIndex].materialDelivered : false,
      noticeRead: memberDeliveries[myIndex] ? memberDeliveries[myIndex].noticeRead : false,
      materialRead: (ds[memberIds[myIndex]] && ds[memberIds[myIndex]].materialRead) || false
    } : null,
    record: !hideInternalRecord ? {
      hasDecision: true, hasMajorIssue: !!m.hasMajorIssue, juweiName: '王红梅（社区居委会）', juweiSigned: !!m.juweiSigned,
      recorderRoleId: m._recorderRoleId || null,
      recorderName: m._recorderRoleId ? getMemberName(m._recorderRoleId) : null,
      // mock 没有真实录音；已结束会议给个占位 URL 以便预览录音卡（真后端为实际存档地址）
      recordingUrl: m._recordingUrl || (m.stage === C.STAGE.ENDED ? 'https://example.com/mock-recording.mp3' : null),
      attendances: attendances, topics: topics, evidences: ensureCommitteeEvidences(m), checks: checks,
      recordLevel: 'minor', recordText: '记录有瑕疵',
      signedInCount: attendances.filter(function (a) { return a.signedIn; }).length,
      signedCount: attendances.filter(function (a) { return a.signed; }).length,
      signedInPct: members.length ? Math.round(attendances.filter(function (a) { return a.signedIn; }).length / members.length * 100) : 0,
      signedPct: members.length ? Math.round(attendances.filter(function (a) { return a.signed; }).length / members.length * 100) : 0
    } : null,
    publish: (m.stage === C.STAGE.ENDED && m.publish) ? m.publish : null,
    _archived: !!m.archived,
    complianceReason: m._complianceReason || '',
    materials: m._materials || [],
    archiveExtras: ensureArchiveExtras(m),
    archiveLog: m._archiveLog || [],
    minutesRevisionCount: (m._minutesRevisions || []).length,
    revokeArchiveReason: m._revokeArchiveReason || '',
    members: members.map(function (name, i) { return { userRoleId: memberIds[i], name: name, role: roles[i], roomNumber: rooms[i] }; }),
    perms: perms
  };
}

// ── 佐证辅助 ──

function ensureCommitteeEvidences(m) {
  if (!m.evidences) m.evidences = [];
  return m.evidences;
}

function ensureArchiveExtras(m) {
  if (!m.archiveExtras) m.archiveExtras = [];
  return m.archiveExtras;
}

function currentOperatorName() {
  var app2 = getApp();
  return (app2 && app2.globalData && app2.globalData.activeRole && app2.globalData.activeRole.realName) || '主任';
}

// 归档操作日志（撤回公示/撤销归档/修订等关键动作留痕）
function ensureArchiveLog(m) {
  if (!m._archiveLog) m._archiveLog = [];
  return m._archiveLog;
}

function pushArchiveLog(m, action, reason) {
  var log = ensureArchiveLog(m);
  log.unshift({
    id: H.nextId(log),
    action: action,
    reason: reason || '',
    operator: currentOperatorName(),
    at: new Date().toISOString()
  });
}

// ── 纪要修订版本 ──
// 归档/公示后的正式内容不可直接改，只能生成新版本：v1=原归档版本，v2+=修订版本
function ensureMinutesRevisions(m) {
  if (!m._minutesRevisions) m._minutesRevisions = [];
  return m._minutesRevisions;
}

function addMinutesRevision(m, newText, reason) {
  var revs = ensureMinutesRevisions(m);
  if (!revs.length) {
    // 首次修订：先把原归档版本固化为 v1
    revs.push({
      versionNo: 1,
      editorName: (m.publish && m.publish.publishedBy) || '系统归档',
      reason: '原归档版本',
      createdAt: (m.publish && m.publish.publishDate) || H.todayStr(),
      text: m._minutes || '',
      isOriginal: true
    });
  }
  var prev = revs[revs.length - 1].text || '';
  revs.push({
    versionNo: revs.length + 1,
    editorName: currentOperatorName(),
    reason: reason,
    createdAt: new Date().toISOString(),
    text: newText,
    before: prev,
    after: newText
  });
  m._minutes = newText; // 最新修订版本生效
  pushArchiveLog(m, '修订纪要', reason);
  return revs[revs.length - 1];
}

// ── 写操作 ──

function handleSelf(meetingId, path) {
  var st = H.getMyState(meetingId);
  var f = H.qs(path, 'field');
  if (f) {
    if (f === 'signedIn') st.signedIn = true;
    if (f === 'signed' && st.signedIn) st.signed = true;
  }
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  var topics = m && m.stage !== C.STAGE.PREPARING ? 3 : 0;
  st.completed = st.signedIn && Object.keys(st.topicVotes).length >= topics;
  return {};
}

function handleVote(meetingId, topicId, path) {
  var st = H.getMyState(meetingId);
  var c = H.qs(path, 'choice');
  var sid = H.qs(path, 'selectedId');
  if (!st.signedIn) return {};
  // multi_choice: 记录 selectedId，simple: 记录 choice
  var voteValue = sid || c;
  if (voteValue && !st.topicVotes[topicId]) st.topicVotes[topicId] = voteValue;
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  if (m && m.stage === C.STAGE.ONGOING) {
    st.completed = st.signedIn && Object.keys(st.topicVotes).length >= 3;
  }
  return {};
}

function handleProxyTargets(meetingId, path) {
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  if (!m) return [];
  var detail = mockDetail(m);
  var kw = (H.qs(path, 'keyword') || '').toLowerCase();
  return (detail.record && detail.record.attendances ? detail.record.attendances : []).map(function (a) {
    var st = getProxyState(meetingId, a.userRoleId);
    var votedTopicIds = Object.keys(st.topicVotes || {}).map(function (id) { return Number(id); });
    return {
      memberId: a.userRoleId,
      name: a.name,
      role: a.role,
      roomNumber: a.roomNumber,
      signedIn: a.signedIn,
      signed: a.signed,
      signInByProxy: !!st.proofUrl,
      signInOperatorName: st.operatorName,
      proofUrl: st.proofUrl,
      votedTopicIds: votedTopicIds
    };
  }).filter(function (item) {
    if (!kw) return true;
    return (item.name || '').toLowerCase().indexOf(kw) >= 0 ||
      (item.roomNumber || '').toLowerCase().indexOf(kw) >= 0;
  });
}

function handleProxyAction(meetingId, data) {
  var ids = data && data.memberIds ? data.memberIds : [];
  var app2 = getApp();
  var operator = app2 && app2.globalData && app2.globalData.activeRole ? app2.globalData.activeRole.realName : '主任';
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  var attendances = m ? ((mockDetail(m).record || {}).attendances || []) : [];
  ids.forEach(function (id) {
    var st = getProxyState(meetingId, id);
    if (data.actionType === 'signIn') {
      st.signedIn = true;
      st.proofUrl = data.proofUrl || '';
      st.operatorName = operator;
    } else if (data.actionType === 'vote' && data.topicId) {
      var attendance = attendances.find(function (a) { return Number(a.userRoleId) === Number(id); });
      if (!attendance || !attendance.signedIn) return;
      st.topicVotes[data.topicId] = data.selectedId || data.choice;
    }
  });
  return {};
}

function handleAdvance(meetingId, path) {
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  var a = H.qs(path, 'action');
  if (m && a) {
    if (a === C.ADVANCE_ACTION.START) {
      if (!getMeetingMemberIds(m).length) throw new Error('请先选择应参会人员并发送会议通知');
      m.stage = C.STAGE.ONGOING;
      m.meetingMode = 'quick';
    }
    if (a === C.ADVANCE_ACTION.END) {
      m.stage = C.STAGE.ENDED;
      // 自动判定：基于确认参会数据
      m._signInCount = 5; // 模拟：确认参会 5/7
      m.compliance = autoCompliance(m);
      m.publish = null;
    }
  }
  return {};
}

function autoCompliance(m) {
  // 确认参会过半 → valid；确认参会未过半 → invalid
  if (m._signInCount >= 4) {
    m._complianceReason = '确认参会人数已过半，会议满足法定要求';
    return C.COMPLIANCE.VALID;
  }
  m._complianceReason = '确认参会人数未过半，未达法定人数';
  return C.COMPLIANCE.INVALID;
}

function handleCompliance(meetingId, status) {
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  if (m && m.stage === C.STAGE.ENDED) {
    m.compliance = status; // 手动覆盖
  }
  return {};
}

function handlePublish(meetingId) {
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  if (m && m.stage === C.STAGE.ENDED && m.compliance && m.compliance !== C.COMPLIANCE.INVALID) {
    m.publish = { published: true, publishDate: H.todayStr(), scoreState: 'ontime', daysLeft: 0, deadlineStr: H.todayStr() };
    m.archived = true; // 公示=归档完成 → 移出会议管理、进资料库
  }
  return {};
}

function handleArchive(meetingId) {
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  if (m && m.stage === C.STAGE.ENDED) {
    m.archived = true; // 直接归档（不公示）
  }
  return {};
}

function handleCreate(data) {
  if (!data.topics || !data.topics.filter(function (t) { return t && t.title && t.title.trim(); }).length) {
    throw new Error('请至少添加一个会议议题');
  }
  var id = H.nextId(mock.committeeMeetings);
  var m = {
    id: id,
    title: data.title || '新建业委会会议',
    meetingDate: data.meetingDate || H.todayStr(),
    meetingTime: data.meetingTime || '09:00',
    location: data.location || '待定',
    description: data.description || '',
    stage: C.STAGE.PREPARING,
    compliance: null,
    summaryLine: '待发送通知',
    memberIds: []
  };
  // 存储用户创建的议题（含 decisionType 和 options）
  if (data.topics && data.topics.length) {
    m.addedTopics = data.topics.map(function (t, i) {
      return {
        id: i + 1,
        title: t.title,
        type: t.type || 'discussion',
        decisionType: t.type === 'decision' ? (t.decisionType || 'simple') : 'none',
        options: t.type === 'decision' ? (t.options || []) : []
      };
    });
  }
  m.noticeDraft = H.buildCommitteeNoticeDraft(m);
  m._materials = [];
  mock.committeeMeetings.unshift(m);
  mock.committeeStats.preparing = mock.committeeMeetings.filter(function (x) { return x.stage === C.STAGE.PREPARING; }).length;
  return { id: id };
}

function handleRemove(meetingId) {
  mock.committeeMeetings = mock.committeeMeetings.filter(function (m) { return m.id !== meetingId; });
  mock.committeeStats.preparing = mock.committeeMeetings.filter(function (m) { return m.stage === C.STAGE.PREPARING; }).length;
  mock.committeeStats.ongoing = mock.committeeMeetings.filter(function (m) { return m.stage === C.STAGE.ONGOING; }).length;
  mock.committeeStats.ended = mock.committeeMeetings.filter(function (m) { return m.stage === C.STAGE.ENDED; }).length;
  return {};
}

function handleAddEvidence(meetingId, path) {
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  if (!m) return {};
  var evs = ensureCommitteeEvidences(m);
  var ev = {
    id: H.nextId(evs),
    fileName: H.qs(path, 'fileName') || '纸质佐证.jpg',
    fileType: H.qs(path, 'fileType') || '照片'
  };
  evs.push(ev);
  return ev;
}

function handleToggleFlag(meetingId, path) {
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  if (!m) return {};
  var flag = H.qs(path, 'flag');
  if (flag === 'hasMajorIssue') {
    m.hasMajorIssue = !m.hasMajorIssue;
    if (!m.hasMajorIssue) m.juweiSigned = false;
  }
  return {};
}

function handleToggleJuwei(meetingId) {
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  if (!m) return {};
  m.hasMajorIssue = true;
  m.juweiSigned = !m.juweiSigned;
  return {};
}

function ensureTopics(m) {
  if (!m.addedTopics) m.addedTopics = [];
  return m.addedTopics;
}

function handleAddTopic(meetingId, path) {
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  if (!m) return {};
  var topics = ensureTopics(m);
  var id = Math.max.apply(null, topics.map(function (t) { return t.id; }).concat([3])) + 1;
  var dt = H.qs(path, 'decisionType') || 'simple';
  var optionsJson = H.qs(path, 'options');
  var topic = {
    id: id,
    title: H.qs(path, 'title') || '新增议题',
    type: H.qs(path, 'type') || 'decision',
    decisionType: dt
  };
  if (dt === 'multi_choice' && optionsJson) {
    try { topic.options = JSON.parse(decodeURIComponent(optionsJson)); } catch (e) { topic.options = []; }
  }
  topics.push(topic);
  return topic;
}

function handleRemoveTopicEv(meetingId, topicId) {
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  if (!m) return {};
  if (m.addedTopics) m.addedTopics = m.addedTopics.filter(function (t) { return t.id !== topicId; });
  return {};
}

function handleRemoveEvidence(meetingId, evId) {
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  if (!m) return {};
  m.evidences = (m.evidences || []).filter(function (e) { return e.id !== evId; });
  return {};
}

// ── 主路由器 ──
// 返回 undefined 表示未匹配，交给下一个 handler

function handle(method, path, data) {
  var basePath = path.split('?')[0];

  // 列表
  if (method === 'GET' && basePath === '/api/committees') {
    // 资料库：只取已归档
    if (H.qs(path, 'archived') === 'true') {
      return injectPersonalState(mock.committeeMeetings.filter(function (m) { return m.archived; }));
    }
    var stage = H.qs(path, 'stage');
    var list = stage
      ? mock.committeeMeetings.filter(function (m) { return m.stage === stage; })
      : [].concat(mock.committeeMeetings);
    // 已归档的会议从会议管理列表移除（只在资料库可见）
    list = list.filter(function (m) { return !m.archived; });
    return injectPersonalState(list);
  }
  if (method === 'GET' && basePath === '/api/committees/members') return getMockCommitteeMembers();
  if (method === 'GET' && basePath === '/api/committees/stats') return mock.committeeStats;
  if (method === 'GET' && basePath === '/api/committees/publish-score') return mock.publishScore;

  // 纪要
  var minutesMatch = path.match(/\/api\/committees\/(\d+)\/minutes/);
  if (minutesMatch && method === 'GET' && /\/minutes$/.test(basePath)) {
    var mid = parseInt(minutesMatch[1]);
    var mt = mock.committeeMeetings.find(function (x) { return x.id === mid; });
    var app2 = getApp();
    var activeRole = (app2 && app2.globalData && app2.globalData.activeRole) ? app2.globalData.activeRole : { role: '委员', id: 1 };
    var external = activeRole.role === '业主' || activeRole.role === '物业';
    var publicMinutes = mt && mt.stage === C.STAGE.ENDED && mt.compliance !== C.COMPLIANCE.INVALID &&
      mt.publish && mt.publish.published;
    if (external && !publicMinutes) throw new Error('会议纪要尚未公示，暂不可查看');
    if (mt && mt._minutes) return mt._minutes;
    var detail = mockDetail(mt);
    var lines = [];
    lines.push('业主委员会会议纪要');
    lines.push('');
    lines.push('会议名称：' + (mt ? mt.title : ''));
    lines.push('会议时间：' + (mt ? mt.meetingDate : '') + ' ' + (mt ? mt.meetingTime : ''));
    lines.push('会议地点：' + (mt ? mt.location : ''));
    lines.push('');
    lines.push('出席情况：确认参会 ' + (detail.record ? detail.record.signedInCount : '?') + '/7 人');
    lines.push('');
    if (detail.record && detail.record.topics && detail.record.topics.length) {
      lines.push('表决议题：');
      detail.record.topics.forEach(function (t) {
        lines.push('  ' + (t.passed ? '✓' : '✕') + ' ' + t.title + '（赞成' + t.forVotes + ' 反对' + t.agVotes + ' 弃权' + t.abVotes + '）');
      });
      lines.push('');
    }
    lines.push('会议结论：' + (mt && mt.compliance === 'valid' ? '会议有效' : mt && mt.compliance === 'flawed' ? '会议有效（带说明归档）' : mt && mt.compliance === 'invalid' ? '会议无效' : '待判定'));
    if (mt) {
      mt._minutes = lines.join('\n');
    }
    return lines.join('\n');
  }

  // 纪要修订版本历史
  var minutesRevMatch = path.match(/\/api\/committees\/(\d+)\/minutes\/revisions/);
  if (minutesRevMatch && method === 'GET') {
    var mrm = mock.committeeMeetings.find(function (x) { return x.id === parseInt(minutesRevMatch[1]); });
    return mrm ? ensureMinutesRevisions(mrm) : [];
  }

  // 编辑纪要（手动保存）
  if (minutesMatch && method === 'PUT') {
    var mid2 = parseInt(minutesMatch[1]);
    var mt2 = mock.committeeMeetings.find(function (x) { return x.id === mid2; });
    if (mt2 && data && data.text) {
      // 已公示或已归档：正式内容不可直接改，走"修订版本"（必填原因，保留版本对比）
      var locked = (mt2.publish && mt2.publish.published) || mt2.archived;
      if (locked) {
        var revReason = (data.reason || '').trim();
        if (!revReason) throw new Error('修订已归档/公示纪要必须填写修订原因');
        addMinutesRevision(mt2, data.text, revReason);
        return {};
      }
      mt2._minutes = data.text;
      return {};
    }
    return {};
  }

  // self toggle
  var selfMatch = path.match(/\/api\/committees\/(\d+)\/self/);
  if (selfMatch && method === 'PUT') return handleSelf(parseInt(selfMatch[1]), path);

  // proxy actions
  var proxyTargetsMatch = path.match(/\/api\/committees\/(\d+)\/proxy-targets/);
  if (proxyTargetsMatch && method === 'GET') return handleProxyTargets(parseInt(proxyTargetsMatch[1]), path);

  var proxyActionMatch = path.match(/\/api\/committees\/(\d+)\/proxy-actions/);
  if (proxyActionMatch && method === 'POST') return handleProxyAction(parseInt(proxyActionMatch[1]), data || {});

  // vote
  var voteMatch = path.match(/\/api\/committees\/(\d+)\/topics\/(\d+)\/vote/);
  if (voteMatch && method === 'PUT') return handleVote(parseInt(voteMatch[1]), parseInt(voteMatch[2]), path);

  // update-notice-draft (手动编辑通知草稿)
  var noticeEditMatch = path.match(/\/api\/committees\/(\d+)\/notice-draft/);
  if (noticeEditMatch && method === 'PUT') {
    var nem = mock.committeeMeetings.find(function (x) { return x.id === parseInt(noticeEditMatch[1]); });
    if (nem && data) {
      nem.noticeDraft = { title: data.title || '', content: data.content || '', status: 'edited' };
      return {};
    }
    return {};
  }

  // mark-read (委员打开详情回写已读)
  var readMatch = path.match(/\/api\/committees\/(\d+)\/delivery\/read/);
  if (readMatch && method === 'POST') {
    var rm = mock.committeeMeetings.find(function (x) { return x.id === parseInt(readMatch[1]); });
    if (rm) {
      var appR = getApp();
      var myId = (appR && appR.globalData && appR.globalData.activeRole) ? appR.globalData.activeRole.id : 1;
      var dsR = deliveryStates[rm.id];
      if (dsR && dsR[myId]) {
        if (dsR[myId].notice) dsR[myId].noticeRead = true;
        if (dsR[myId].material) dsR[myId].materialRead = true;
      }
    }
    return {};
  }

  // send-all (一键全部送达)
  var sendAllMatch = path.match(/\/api\/committees\/(\d+)\/delivery\/send-all/);
  if (sendAllMatch && method === 'POST') {
    var sa = mock.committeeMeetings.find(function (x) { return x.id === parseInt(sendAllMatch[1]); });
    if (sa) {
      var memberIds = data && data.memberIds ? data.memberIds.map(function (id) { return Number(id); }) : [];
      if (!memberIds.length) throw new Error('请选择通知对象');
      sa.memberIds = memberIds;
      deliveryStates[sa.id] = {};
      var ds = ensureDeliveryState(sa.id, memberIds);
      memberIds.forEach(function (uid) {
        var prev = ds[uid] || { notice: false, material: false };
        ds[uid] = { notice: true, material: true };
        // 同一场会只保留一条会议通知；材料送达时更新这条通知，而不是另发一条。
        if (!prev.notice || !prev.material) {
          upsertMeetingNotice(uid, sa, ds[uid]);
        }
      });
      sa.summaryLine = getDeliverySummary(sa);
      return {};
    }
    return {};
  }

  // toggle-delivery (逐人送达)
  var toggleDeliveryMatch = path.match(/\/api\/committees\/(\d+)\/delivery\/(\d+)/);
  if (toggleDeliveryMatch && method === 'PUT') {
    var td = mock.committeeMeetings.find(function (x) { return x.id === parseInt(toggleDeliveryMatch[1]); });
    if (td) {
      var uid2 = parseInt(toggleDeliveryMatch[2]);
      var field2 = H.qs(path, 'field');
      var memberIds2 = getMeetingMemberIds(td);
      var ds2 = ensureDeliveryState(td.id, memberIds2);
      if (!ds2[uid2]) ds2[uid2] = { notice: false, material: false };
      var wasDelivered = ds2[uid2][field2];
      if (field2 === 'notice') ds2[uid2].notice = !ds2[uid2].notice;
      if (field2 === 'material') ds2[uid2].material = !ds2[uid2].material;
      // 首次送达发通知
      if (!wasDelivered && ds2[uid2][field2]) {
        upsertMeetingNotice(uid2, td, ds2[uid2]);
      }
      td.summaryLine = getDeliverySummary(td);
      return {};
    }
    return {};
  }

  // advance
  var advMatch = path.match(/\/api\/committees\/(\d+)\/advance/);
  if (advMatch && method === 'POST') return handleAdvance(parseInt(advMatch[1]), path);

  var flagMatch = path.match(/\/api\/committees\/(\d+)\/flags/);
  if (flagMatch && method === 'PUT') return handleToggleFlag(parseInt(flagMatch[1]), path);

  var juweiMatch = path.match(/\/api\/committees\/(\d+)\/juwei/);
  if (juweiMatch && method === 'POST') return handleToggleJuwei(parseInt(juweiMatch[1]));

  // compliance (标记会议有效/无效)
  var compMatch = path.match(/\/api\/committees\/(\d+)\/compliance/);
  if (compMatch && method === 'PUT') return handleCompliance(parseInt(compMatch[1]), H.qs(path, 'status'));

  // 撤回公示（必填原因，回到待公示，可修订后重新公示）— 必须在 publish 之前匹配
  var withdrawMatch = basePath.match(/\/api\/committees\/(\d+)\/publish\/withdraw$/);
  if (withdrawMatch && method === 'POST') {
    var wm = mock.committeeMeetings.find(function (x) { return x.id === parseInt(withdrawMatch[1]); });
    if (wm) {
      if (!wm.publish || !wm.publish.published) throw new Error('当前没有处于公示中的纪要');
      var wReason = ((data && data.reason) || H.qs(path, 'reason') || '').trim();
      if (!wReason) throw new Error('撤回公示必须填写原因');
      wm.publish = {
        published: false, withdrawn: true,
        withdrawnBy: currentOperatorName(), withdrawReason: wReason,
        withdrawnAt: new Date().toISOString(),
        publishDate: wm.publish.publishDate
      };
      // 撤回公示仍保留归档态（留在资料库）：可修订后重新公示，或再走「撤销归档」彻底撤出
      pushArchiveLog(wm, '撤回公示', wReason);
      return {};
    }
    return {};
  }

  // publish (发起公示，仅有效/瑕疵会议可公示)
  var pubMatch = path.match(/\/api\/committees\/(\d+)\/publish/);
  if (pubMatch && method === 'POST') return handlePublish(parseInt(pubMatch[1]));

  // 撤销归档（仅误归档用，门槛高）— 必须在 archive 之前匹配
  var revokeMatch = basePath.match(/\/api\/committees\/(\d+)\/archive\/revoke$/);
  if (revokeMatch && method === 'POST') {
    var rm = mock.committeeMeetings.find(function (x) { return x.id === parseInt(revokeMatch[1]); });
    if (rm) {
      var p = require('../perm');
      if (!p.isChair()) throw new Error('仅主任/副主任可撤销归档');
      var rReason = ((data && data.reason) || H.qs(path, 'reason') || '').trim();
      if (!rReason) throw new Error('撤销归档必须填写原因');
      if (rm.publish && rm.publish.published) throw new Error('已公示会议请先撤回公示，再撤销归档');
      if (!rm.archived) throw new Error('该会议未归档');
      rm.archived = false; // 回到归档前阶段（ended·待公示/待归档）
      rm.publish = null;   // 清掉公示/撤回标记，回到干净的待公示状态
      rm._revokeArchiveReason = rReason;
      pushArchiveLog(rm, '撤销归档', rReason);
      return {};
    }
    return {};
  }

  // archive (直接归档，不公示)
  var archiveMatch = basePath.match(/\/api\/committees\/(\d+)\/archive$/);
  if (archiveMatch && method === 'POST') return handleArchive(parseInt(archiveMatch[1]));

  // create
  if (method === 'POST' && basePath === '/api/committees') return handleCreate(data || {});

  // update (准备阶段编辑基本信息)
  var updateMatch = path.match(/\/api\/committees\/(\d+)$/);
  if (updateMatch && method === 'PUT') {
    var um = mock.committeeMeetings.find(function (x) { return x.id === parseInt(updateMatch[1]); });
    if (um && data) {
      // 归档后默认不可编辑原记录：正式内容需走"修订版本"，撤销归档后才可改基本信息
      if (um.archived || (um.publish && um.publish.published)) {
        throw new Error('已归档/公示会议不可直接编辑，请走修订或先撤销归档');
      }
      if (data.title) um.title = data.title;
      if (data.meetingDate) um.meetingDate = data.meetingDate;
      if (data.meetingTime) um.meetingTime = data.meetingTime;
      if (data.location) um.location = data.location;
      if (typeof data.description !== 'undefined') um.description = data.description;
      // 重新生成通知草稿
      um.noticeDraft = H.buildCommitteeNoticeDraft(um);
      return {};
    }
    return {};
  }

  // remove
  var removeMatch = path.match(/\/api\/committees\/(\d+)$/);
  if (removeMatch && method === 'DELETE') return handleRemove(parseInt(removeMatch[1]));

  // evidence
  var evMatch = path.match(/\/api\/committees\/(\d+)\/evidences\/(\d+)/);
  if (evMatch && method === 'DELETE') return handleRemoveEvidence(parseInt(evMatch[1]), parseInt(evMatch[2]));

  var evAddMatch = path.match(/\/api\/committees\/(\d+)\/evidences/);
  if (evAddMatch && method === 'POST') return handleAddEvidence(parseInt(evAddMatch[1]), path);

  // materials（会前主任上传 + 进行中任意参会人上传，记录上传人）
  var matAddMatch = path.match(/\/api\/committees\/(\d+)\/materials$/);
  if (matAddMatch && method === 'POST') {
    var matm = mock.committeeMeetings.find(function (x) { return x.id === parseInt(matAddMatch[1]); });
    if (matm) {
      if (!matm._materials) matm._materials = [];
      var matApp = getApp();
      var matUploader = (matApp && matApp.globalData && matApp.globalData.activeRole && matApp.globalData.activeRole.realName) || '参会人';
      matm._materials.push({
        name: H.qs(path, 'fileName') || '未命名材料',
        sizeText: H.qs(path, 'sizeText') || '',
        uploaderName: matUploader,
        content: '（上传文件，暂无预览内容）'
      });
      return {};
    }
    return {};
  }

  var matRemoveMatch = path.match(/\/api\/committees\/(\d+)\/materials\/(\d+)$/);
  if (matRemoveMatch && method === 'DELETE') {
    var matrm = mock.committeeMeetings.find(function (x) { return x.id === parseInt(matRemoveMatch[1]); });
    if (matrm && matrm._materials) {
      var matIdx = parseInt(matRemoveMatch[2]);
      matrm._materials = matrm._materials.filter(function (_, i) { return i !== matIdx; });
    }
    return {};
  }

  // 录音负责人：认领 / 转交
  var recClaimMatch = path.match(/\/api\/committees\/(\d+)\/quick\/recorder\/claim/);
  if (recClaimMatch && method === 'POST') {
    var rcm = mock.committeeMeetings.find(function (x) { return x.id === parseInt(recClaimMatch[1]); });
    if (rcm) {
      var rcApp = getApp();
      rcm._recorderRoleId = (rcApp && rcApp.globalData && rcApp.globalData.activeRole) ? rcApp.globalData.activeRole.id : 1;
    }
    return {};
  }
  var recResetMatch = path.match(/\/api\/committees\/(\d+)\/quick\/recorder\/reset/);
  if (recResetMatch && method === 'POST') {
    var rrm = mock.committeeMeetings.find(function (x) { return x.id === parseInt(recResetMatch[1]); });
    if (rrm) rrm._recorderRoleId = null;
    return {};
  }

  // 导出签到名单（CSV 文本，仅姓名为主，不含房号）
  var exportMatch = path.match(/\/api\/committees\/(\d+)\/attendance\/export/);
  if (exportMatch && method === 'GET') {
    var exm = mock.committeeMeetings.find(function (x) { return x.id === parseInt(exportMatch[1]); });
    var det = exm ? mockDetail(exm) : null;
    var atts = (det && det.record && det.record.attendances) ? det.record.attendances.filter(function (a) { return a.signedIn; }) : [];
    var csv = '﻿序号,姓名,角色,确认参会\n';
    atts.forEach(function (a, i) {
      csv += (i + 1) + ',' + (a.name || '') + ',' + (a.role || '') + ',已确认\n';
    });
    return { fileName: ((exm && exm.title) || '会议') + '-签到名单.csv', content: csv, count: atts.length };
  }

  // archive extras (归档后补充材料，只追加，不改原归档内容)
  var archiveExtraMatch = basePath.match(/\/api\/committees\/(\d+)\/archive-extras$/);
  if (archiveExtraMatch && method === 'POST') {
    var aem = mock.committeeMeetings.find(function (x) { return x.id === parseInt(archiveExtraMatch[1]); });
    if (aem) {
      if (!aem.archived && aem.stage !== C.STAGE.ENDED) throw new Error('仅已结束或已归档会议可补充材料');
      var app2 = getApp();
      var operator = app2 && app2.globalData && app2.globalData.activeRole ? app2.globalData.activeRole.realName : '主任';
      var extras = ensureArchiveExtras(aem);
      extras.push({
        id: H.nextId(extras),
        fileName: H.qs(path, 'fileName') || '归档后补充材料',
        sizeText: H.qs(path, 'sizeText') || '',
        reason: H.qs(path, 'reason') || '补充归档资料',
        addedBy: operator,
        addedAt: new Date().toISOString(),
        content: '（归档后补充材料，暂无预览内容）'
      });
      aem.archived = true;
      return {};
    }
    return {};
  }

  // topic add/remove
  var topicRemoveMatch = path.match(/\/api\/committees\/(\d+)\/topics\/(\d+)$/);
  if (topicRemoveMatch && method === 'DELETE') return handleRemoveTopicEv(parseInt(topicRemoveMatch[1]), parseInt(topicRemoveMatch[2]));

  var topicAddMatch = path.match(/\/api\/committees\/(\d+)\/topics$/);
  if (topicAddMatch && method === 'POST') return handleAddTopic(parseInt(topicAddMatch[1]), path);

  // detail (must be last to not shadow /committees/N/subpath)
  var detailMatch = path.match(/\/api\/committees\/(\d+)$/);
  if (detailMatch && method === 'GET') {
    var m = mock.committeeMeetings.find(function (x) { return x.id === parseInt(detailMatch[1]); });
    if (m) return mockDetail(m);
    return null;
  }

  return undefined; // 未匹配
}

module.exports = { handle: handle };
