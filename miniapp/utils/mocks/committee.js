// ═══════════════════════════════════════════════
// Mock 处理器 — 委员会会议
// ═══════════════════════════════════════════════

var mock = require('../mock');
var H = require('../api/helpers');
var C = require('../constants');

var proxyStates = {};

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

// GET 列表 + 注入个人状态
function injectPersonalState(list) {
  return list.map(function (m) {
    var st = H.getMyState(m.id);
    var prog = H.getPersonalProgress(m, st);
    return {
      id: m.id,
      title: m.title,
      meetingDate: m.meetingDate,
      meetingTime: m.meetingTime,
      location: m.location,
      stage: m.stage,
      compliance: m.compliance,
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
  var members = ['张建国', '李秀英', '王志强', '赵丽娟', '刘海涛', '陈晓梅', '杨国华'];
  var roles = ['主任', '副主任', '委员', '委员', '委员', '委员', '委员'];
  var rooms = ['1号楼101', '1号楼102', '2号楼201', '2号楼202', '3号楼301', '3号楼302', '4号楼401'];
  var app2 = getApp();
  var activeRole = (app2 && app2.globalData && app2.globalData.activeRole) ? app2.globalData.activeRole : { role: '委员', id: 1 };
  var role = activeRole.role;
  var myRoleId = activeRole.id;
  var myIndex = (myRoleId >= 1 && myRoleId <= 7) ? myRoleId - 1 : 0;

  var memberDeliveries = members.map(function (name, i) {
    return { userRoleId: i + 1, name: name, role: roles[i], noticeDelivered: i < 4, materialDelivered: i < 2 };
  });

  var attendances = members.map(function (name, i) {
    var pst = getProxyState(m.id, i + 1);
    var signedInDefault = i === myIndex ? st.signedIn : (i < 5);
    var signedDefault = i === myIndex ? st.signed : (i < 3);
    return {
      userRoleId: i + 1, name: name, role: roles[i], roomNumber: rooms[i],
      signedIn: typeof pst.signedIn === 'boolean' ? pst.signedIn : signedInDefault,
      signed: typeof pst.signed === 'boolean' ? pst.signed : signedDefault,
      isSelf: i === myIndex,
      isProxy: !!pst.proofUrl,
      operatorName: pst.operatorName,
      proofUrl: pst.proofUrl
    };
  });

  var builtInTopics = [
    { id: 1, title: '公共设施维修专项资金使用方案', type: 'decision', decisionType: 'simple' },
    { id: 2, title: '绿化养护承包单位续聘', type: 'decision', decisionType: 'simple' },
    { id: 3, title: '提请业主大会审议电梯更新事项', type: 'major', decisionType: 'simple' },
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
        id: t.id, title: t.title, type: t.type, decisionType: dt,
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
      id: t.id, title: t.title, type: t.type, decisionType: dt,
      forVotes: forV, agVotes: agV,
      abVotes: abV,
      total: 7, need: 4, passed: simplePassed,
      status: simplePassed ? 'passed' : (simpleVoted < 7 ? 'pending' : 'failed'),
      text: simplePassed ? '决议通过' : '待继续表决', myVote: myV
    };
  }) : [];

  var checks = m.stage === C.STAGE.ONGOING ? [
    { label: '委员签到过半', detail: '5/7（需≥4）', ok: true },
    { label: '会议记录委员签字过半', detail: '3/7（需≥4）', ok: false },
    { label: '线下佐证归档', detail: '待上传', ok: false }
  ] : [];

  var isCompleted = st.signedIn && st.signed && Object.keys(st.topicVotes).length >= 3;
  var taskLevel = m.stage === C.STAGE.PREPARING ? 'wait' : m.stage === C.STAGE.ONGOING ? (isCompleted ? 'ok' : 'todo') : 'ok';
  var taskLabels = [];
  if (m.stage === C.STAGE.ONGOING && !isCompleted) {
    if (!st.signedIn) taskLabels.push('我要签到');
    if (st.signedIn && !st.signed) taskLabels.push('我要签字');
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
    location: m.location, description: m.description || '会议说明（离线模式）', stage: m.stage, compliance: m.compliance,
    noticeDraft: m.noticeDraft || H.buildCommitteeNoticeDraft(m),
    userRole: role, userView: userView,
    taskLevel: taskLevel, taskTitle: taskLevel === 'todo' ? '待处理' : '待关注',
    taskItems: taskItems, taskItemsText: taskItems.join(' / '),
    taskHint: '', flowNodeText: m.stage === C.STAGE.PREPARING ? '准备阶段·待开始' : m.stage === C.STAGE.ONGOING ? '进行中·签到/签字确认中' : '会议结束',
    delivery: m.stage === C.STAGE.PREPARING ? {
      deadlineStr: '2026-06-08', daysLeft: -2,
      allDone: !!m._allSent, noDate: false,
      total: 7, memberDeliveries: memberDeliveries
    } : null,
    record: m.stage !== C.STAGE.PREPARING ? {
      hasDecision: true, hasMajorIssue: false, juweiName: '王红梅（社区居委会）', juweiSigned: false,
      attendances: attendances, topics: topics, evidences: ensureCommitteeEvidences(m), checks: checks,
      recordLevel: 'minor', recordText: '记录有瑕疵',
      signedInCount: attendances.filter(function (a) { return a.signedIn; }).length,
      signedCount: attendances.filter(function (a) { return a.signed; }).length,
      signedInPct: Math.round(attendances.filter(function (a) { return a.signedIn; }).length / 7 * 100),
      signedPct: Math.round(attendances.filter(function (a) { return a.signed; }).length / 7 * 100)
    } : null,
    publish: (m.stage === C.STAGE.ENDED && m.publish) ? m.publish : null,
    complianceReason: m._complianceReason || '',
    materials: m.stage === C.STAGE.PREPARING ? (m._materials || [
      { name: '议程说明.pdf', sizeText: '1.2MB' },
      { name: '预算方案.docx', sizeText: '856KB' }
    ]) : null,
    members: members.map(function (name, i) { return { userRoleId: i + 1, name: name, role: roles[i] }; }),
    perms: perms
  };
}

// ── 佐证辅助 ──

function ensureCommitteeEvidences(m) {
  if (!m.evidences) m.evidences = [];
  return m.evidences;
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
  st.completed = st.signedIn && st.signed && Object.keys(st.topicVotes).length >= topics;
  return {};
}

function handleVote(meetingId, topicId, path) {
  var st = H.getMyState(meetingId);
  var c = H.qs(path, 'choice');
  var sid = H.qs(path, 'selectedId');
  st.signedIn = true;
  // multi_choice: 记录 selectedId，simple: 记录 choice
  var voteValue = sid || c;
  if (voteValue && !st.topicVotes[topicId]) st.topicVotes[topicId] = voteValue;
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  if (m && m.stage === C.STAGE.ONGOING) {
    st.completed = st.signedIn && st.signed && Object.keys(st.topicVotes).length >= 3;
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
  ids.forEach(function (id) {
    var st = getProxyState(meetingId, id);
    if (data.actionType === 'signIn') {
      st.signedIn = true;
      st.proofUrl = data.proofUrl || '';
      st.operatorName = operator;
    } else if (data.actionType === 'vote' && data.topicId) {
      st.topicVotes[data.topicId] = data.selectedId || data.choice;
    }
  });
  return {};
}

function handleAdvance(meetingId, path) {
  var m = mock.committeeMeetings.find(function (x) { return x.id === meetingId; });
  var a = H.qs(path, 'action');
  if (m && a) {
    if (a === C.ADVANCE_ACTION.START) m.stage = C.STAGE.ONGOING;
    if (a === C.ADVANCE_ACTION.END) {
      m.stage = C.STAGE.ENDED;
      // 自动判定：基于签到和签字数据
      m._signInCount = 5; // 模拟：签到 5/7
      m._signCount = 3;    // 模拟：签字 3/7
      m.compliance = autoCompliance(m);
      m.publish = null;
    }
  }
  return {};
}

function autoCompliance(m) {
  // 签到过半 + 签字过半 → valid
  // 签到过半 + 签字未过半 → flawed
  // 签到未过半 → invalid
  if (m._signInCount >= 4 && m._signCount >= 4) {
    m._complianceReason = '签到和签字均过半，会议满足法定要求';
    return C.COMPLIANCE.VALID;
  }
  if (m._signInCount >= 4) {
    m._complianceReason = '签到已过半，但签字未过半，存在记录瑕疵';
    return C.COMPLIANCE.FLAWED;
  }
  m._complianceReason = '签到未过半，未达法定人数';
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
  }
  return {};
}

function handleCreate(data) {
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
    summaryLine: '通知 0/7 · 材料 0/7'
  };
  // 存储用户创建的议题（含 decisionType 和 options）
  if (data.topics && data.topics.length) {
    m.addedTopics = data.topics.map(function (t, i) {
      return {
        id: i + 1,
        title: t.title,
        type: t.type || 'decision',
        decisionType: t.decisionType || 'simple',
        options: t.options || []
      };
    });
  }
  m.noticeDraft = H.buildCommitteeNoticeDraft(m);
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
    var stage = H.qs(path, 'stage');
    if (stage) return injectPersonalState(mock.committeeMeetings.filter(function (m) { return m.stage === stage; }));
    return injectPersonalState([].concat(mock.committeeMeetings));
  }
  if (method === 'GET' && basePath === '/api/committees/stats') return mock.committeeStats;
  if (method === 'GET' && basePath === '/api/committees/publish-score') return mock.publishScore;

  // 纪要
  var minutesMatch = path.match(/\/api\/committees\/(\d+)\/minutes/);
  if (minutesMatch && method === 'GET') {
    var mid = parseInt(minutesMatch[1]);
    var mt = mock.committeeMeetings.find(function (x) { return x.id === mid; });
    if (mt && mt._minutes) return mt._minutes;
    var detail = mockDetail(mt);
    var lines = [];
    lines.push('业主委员会会议纪要');
    lines.push('');
    lines.push('会议名称：' + (mt ? mt.title : ''));
    lines.push('会议时间：' + (mt ? mt.meetingDate : '') + ' ' + (mt ? mt.meetingTime : ''));
    lines.push('会议地点：' + (mt ? mt.location : ''));
    lines.push('');
    lines.push('出席情况：签到 ' + (detail.record ? detail.record.signedInCount : '?') + '/7 人，签字 ' + (detail.record ? detail.record.signedCount : '?') + '/7 人');
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

  // send-all (一键全部送达)
  var sendAllMatch = path.match(/\/api\/committees\/(\d+)\/delivery\/send-all/);
  if (sendAllMatch && method === 'POST') {
    var sa = mock.committeeMeetings.find(function (x) { return x.id === parseInt(sendAllMatch[1]); });
    if (sa) {
      sa._allSent = true;
      return {};
    }
    return {};
  }

  // advance
  var advMatch = path.match(/\/api\/committees\/(\d+)\/advance/);
  if (advMatch && method === 'POST') return handleAdvance(parseInt(advMatch[1]), path);

  // compliance (标记会议有效/无效)
  var compMatch = path.match(/\/api\/committees\/(\d+)\/compliance/);
  if (compMatch && method === 'PUT') return handleCompliance(parseInt(compMatch[1]), H.qs(path, 'status'));

  // publish (发起公示，仅有效/瑕疵会议可公示)
  var pubMatch = path.match(/\/api\/committees\/(\d+)\/publish/);
  if (pubMatch && method === 'POST') return handlePublish(parseInt(pubMatch[1]));

  // create
  if (method === 'POST' && basePath === '/api/committees') return handleCreate(data || {});

  // remove
  var removeMatch = path.match(/\/api\/committees\/(\d+)$/);
  if (removeMatch && method === 'DELETE') return handleRemove(parseInt(removeMatch[1]));

  // evidence
  var evMatch = path.match(/\/api\/committees\/(\d+)\/evidences\/(\d+)/);
  if (evMatch && method === 'DELETE') return handleRemoveEvidence(parseInt(evMatch[1]), parseInt(evMatch[2]));

  var evAddMatch = path.match(/\/api\/committees\/(\d+)\/evidences/);
  if (evAddMatch && method === 'POST') return handleAddEvidence(parseInt(evAddMatch[1]), path);

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
