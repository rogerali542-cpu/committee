// ═══════════════════════════════════════════════
// API 统一入口 — require('../../utils/api') 自动解析到此文件
// ═══════════════════════════════════════════════

var core = require('./core');

// 合并所有领域模块的导出
var auth = require('./auth');
var dashboard = require('./dashboard');
var committee = require('./committee');
var ownerMeeting = require('./owner-meeting');
var reception = require('./reception');
var learning = require('./learning');
var publicInfo = require('./public-info');

module.exports = {
  // 便捷方法
  get: core.get,
  post: core.post,
  put: core.put,
  delete: core.delete,

  // Auth
  login: auth.login,
  refreshMe: auth.refreshMe,

  // Dashboard
  dashboardStats: dashboard.dashboardStats,

  // Committee
  committeeList: committee.committeeList,
  committeeDetail: committee.committeeDetail,
  committeeCreate: committee.committeeCreate,
  committeeAdvance: committee.committeeAdvance,
  committeeToggleDelivery: committee.committeeToggleDelivery,
  committeeSendAll: committee.committeeSendAll,
  committeeToggleAttend: committee.committeeToggleAttend,
  committeeSelfToggle: committee.committeeSelfToggle,
  committeeSignAll: committee.committeeSignAll,
  committeeAddTopic: committee.committeeAddTopic,
  committeeRemoveTopic: committee.committeeRemoveTopic,
  committeeVote: committee.committeeVote,
  committeeProxyTargets: committee.committeeProxyTargets,
  committeeProxySubmit: committee.committeeProxySubmit,
  committeeToggleFlag: committee.committeeToggleFlag,
  committeeToggleJuwei: committee.committeeToggleJuwei,
  committeeAddEvidence: committee.committeeAddEvidence,
  committeeRemoveEvidence: committee.committeeRemoveEvidence,
  committeePublish: committee.committeePublish,
  committeeMinutes: committee.committeeMinutes,
  committeeStats: committee.committeeStats,
  committeePublishScore: committee.committeePublishScore,
  committeeRemove: committee.committeeRemove,
  committeeCompliance: committee.committeeCompliance,

  // Owner Meetings
  omList: ownerMeeting.omList,
  omDetail: ownerMeeting.omDetail,
  omCreate: ownerMeeting.omCreate,
  omAdvance: ownerMeeting.omAdvance,
  omToggleNotify: ownerMeeting.omToggleNotify,
  omNotifyAll: ownerMeeting.omNotifyAll,
  omToggleBallot: ownerMeeting.omToggleBallot,
  omBallotAll: ownerMeeting.omBallotAll,
  omAdjustCount: ownerMeeting.omAdjustCount,
  omToggleSupervisor: ownerMeeting.omToggleSupervisor,
  omToggleProcess: ownerMeeting.omToggleProcess,
  omAddTopic: ownerMeeting.omAddTopic,
  omVoteAdj: ownerMeeting.omVoteAdj,
  omAddEvidence: ownerMeeting.omAddEvidence,
  omPublish: ownerMeeting.omPublish,
  omStats: ownerMeeting.omStats,
  omRemove: ownerMeeting.omRemove,
  omRemoveEvidence: ownerMeeting.omRemoveEvidence,

  // Reception
  receptionSystem: reception.receptionSystem,
  receptionUpdateSystem: reception.receptionUpdateSystem,
  receptionRecords: reception.receptionRecords,
  receptionCreate: reception.receptionCreate,
  receptionUpdateResolution: reception.receptionUpdateResolution,
  receptionToggleFollow: reception.receptionToggleFollow,
  receptionStats: reception.receptionStats,
  receptionRemove: reception.receptionRemove,
  receptionAddEvidence: reception.receptionAddEvidence,
  receptionRemoveEvidence: reception.receptionRemoveEvidence,

  // Learning
  learningList: learning.learningList,
  learningCounts: learning.learningCounts,
  learningStart: learning.learningStart,
  learningFinish: learning.learningFinish,
  learningCreate: learning.learningCreate,
  learningRemove: learning.learningRemove,
  learningAddEvidence: learning.learningAddEvidence,
  learningRemoveEvidence: learning.learningRemoveEvidence,
  learningSignIn: learning.learningSignIn,
  learningNotifyAll: learning.learningNotifyAll,

  // Public Info
  publicInfo: publicInfo.publicInfo
};
