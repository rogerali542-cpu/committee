// ═══════════════════════════════════════════════
// API 统一入口 — require('../../utils/api') 自动解析到此文件
// ═══════════════════════════════════════════════

var core = require('./core');

// 合并所有领域模块的导出
var auth = require('./auth');
var dashboard = require('./dashboard');
var committee = require('./committee');
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
  committeeArchiveList: committee.committeeArchiveList,
  committeeArchive: committee.committeeArchive,
  committeeRevokeArchive: committee.committeeRevokeArchive,
  committeeAddArchiveExtra: committee.committeeAddArchiveExtra,
  committeeDetail: committee.committeeDetail,
  committeeMembers: committee.committeeMembers,
  committeeCreate: committee.committeeCreate,
  committeeUpdate: committee.committeeUpdate,
  committeeAdvance: committee.committeeAdvance,
  committeeToggleDelivery: committee.committeeToggleDelivery,
  committeeMarkDeliveryRead: committee.committeeMarkDeliveryRead,
  committeeSendAll: committee.committeeSendAll,
  committeeToggleAttend: committee.committeeToggleAttend,
  committeeSelfToggle: committee.committeeSelfToggle,
  committeeSignAll: committee.committeeSignAll,
  committeeExportAttendance: committee.committeeExportAttendance,
  committeeClaimRecorder: committee.committeeClaimRecorder,
  committeeResetRecorder: committee.committeeResetRecorder,
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
  committeeWithdrawPublish: committee.committeeWithdrawPublish,
  committeeMinutes: committee.committeeMinutes,
  committeeMinutesRevisions: committee.committeeMinutesRevisions,
  committeeUpdateMinutes: committee.committeeUpdateMinutes,
  committeeStats: committee.committeeStats,
  committeePublishScore: committee.committeePublishScore,
  committeeRemove: committee.committeeRemove,
  committeeCompliance: committee.committeeCompliance,
  committeeAddMaterial: committee.committeeAddMaterial,
  committeeRemoveMaterial: committee.committeeRemoveMaterial,
  committeeUpdateNotice: committee.committeeUpdateNotice,
  committeeQuickUploadRecording: committee.committeeQuickUploadRecording,
  committeeQuickRecordingStatus: committee.committeeQuickRecordingStatus,
  committeeQuickExtract: committee.committeeQuickExtract,
  committeeQuickTranscript: committee.committeeQuickTranscript,
  committeeQuickConfirm: committee.committeeQuickConfirm,
  committeeQuickPolish: committee.committeeQuickPolish,
  committeeQuickTopicSummary: committee.committeeQuickTopicSummary,
  committeeQuickTopicSummaryTask: committee.committeeQuickTopicSummaryTask,
  committeeQuickTopicSummaryTaskStatus: committee.committeeQuickTopicSummaryTaskStatus,

  // Reception
  receptionSystem: reception.receptionSystem,
  receptionUpdateSystem: reception.receptionUpdateSystem,
  receptionRecords: reception.receptionRecords,
  receptionCreate: reception.receptionCreate,
  receptionUpdateResolution: reception.receptionUpdateResolution,
  receptionToggleFollow: reception.receptionToggleFollow,
  receptionDispatch: reception.receptionDispatch,
  receptionPropertyStart: reception.receptionPropertyStart,
  receptionPropertyReply: reception.receptionPropertyReply,
  receptionPropertyTasks: reception.receptionPropertyTasks,
  receptionPropertyPublic: reception.receptionPropertyPublic,
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

  // Notifications
  notificationList: require('./notifications').notificationList,
  notificationRead: require('./notifications').notificationRead,
  notificationReadAll: require('./notifications').notificationReadAll,

  // Public Info
  publicInfo: publicInfo.publicInfo
};
