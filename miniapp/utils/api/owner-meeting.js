// ═══════════════════════════════════════════════
// API — 业主大会
// ═══════════════════════════════════════════════

var core = require('./core');

module.exports = {
  omList: function (stage) {
    return core.request('GET', '/api/owner-meetings' + (stage ? '?stage=' + stage : ''));
  },
  omArchiveList: function () {
    return core.request('GET', '/api/owner-meetings?archived=true');
  },
  omDetail: function (id) {
    return core.request('GET', '/api/owner-meetings/' + id);
  },
  omCreate: function (data) {
    return core.request('POST', '/api/owner-meetings', data);
  },
  omUpdate: function (id, data) {
    return core.request('PUT', '/api/owner-meetings/' + id, data);
  },
  omAdvance: function (id, action, mode) {
    return core.request('POST', '/api/owner-meetings/' + id + '/advance?action=' + action + (mode ? '&mode=' + mode : ''));
  },
  omToggleNotify: function (id, field) {
    return core.request('PUT', '/api/owner-meetings/' + id + '/notify?field=' + field);
  },
  omNotifyAll: function (id) {
    return core.request('POST', '/api/owner-meetings/' + id + '/notify-all');
  },
  omToggleBallot: function (id, field) {
    return core.request('PUT', '/api/owner-meetings/' + id + '/ballot?field=' + field);
  },
  omBallotAll: function (id) {
    return core.request('POST', '/api/owner-meetings/' + id + '/ballot-all');
  },
  omAdjustCount: function (id, field, delta) {
    return core.request('PUT', '/api/owner-meetings/' + id + '/count?field=' + field + '&delta=' + delta);
  },
  omToggleSupervisor: function (id) {
    return core.request('POST', '/api/owner-meetings/' + id + '/supervisor');
  },
  omToggleProcess: function (id, key) {
    return core.request('PUT', '/api/owner-meetings/' + id + '/process/' + key);
  },
  omAddTopic: function (id, title, type) {
    return core.request('POST', '/api/owner-meetings/' + id + '/topics?title=' + encodeURIComponent(title) + '&type=' + type);
  },
  omVote: function (id, topicId, choice) {
    return core.request('PUT', '/api/owner-meetings/' + id + '/topics/' + topicId + '/vote?choice=' + choice);
  },
  omProxyVote: function (id, data) {
    return core.request('POST', '/api/owner-meetings/' + id + '/proxy-vote', data);
  },
  omVoteStatus: function (id) {
    return core.request('GET', '/api/owner-meetings/' + id + '/vote-status');
  },
  omAttendanceList: function (id) {
    return core.request('GET', '/api/owner-meetings/' + id + '/attendance');
  },
  omToggleAttendance: function (id, unitId) {
    return core.request('PUT', '/api/owner-meetings/' + id + '/attendance/' + unitId);
  },
  omVoteAdj: function (id, topicId, field, delta) {
    return core.request('PUT', '/api/owner-meetings/' + id + '/topics/' + topicId + '/vote-adj?field=' + field + '&delta=' + delta);
  },
  omAddEvidence: function (id, fileName, fileType) {
    return core.request('POST', '/api/owner-meetings/' + id + '/evidences?fileName=' + encodeURIComponent(fileName) + '&fileType=' + fileType);
  },
  omPublish: function (id) {
    return core.request('POST', '/api/owner-meetings/' + id + '/publish');
  },
  omWithdrawPublish: function (id, reason) {
    return core.request('POST', '/api/owner-meetings/' + id + '/publish/withdraw', { reason: reason });
  },
  omMinutesRevisions: function (id) {
    return core.request('GET', '/api/owner-meetings/' + id + '/minutes/revisions');
  },
  omStats: function () {
    return core.request('GET', '/api/owner-meetings/stats');
  },
  omRemove: function (id) {
    return core.request('DELETE', '/api/owner-meetings/' + id);
  },
  omRemoveEvidence: function (id, evId) {
    return core.request('DELETE', '/api/owner-meetings/' + id + '/evidences/' + evId);
  },
  omAddMaterial: function (id, fileName, sizeText) {
    return core.request('POST', '/api/owner-meetings/' + id + '/materials?fileName=' + encodeURIComponent(fileName) + '&sizeText=' + encodeURIComponent(sizeText || ''));
  },
  omRemoveMaterial: function (id, idx) {
    return core.request('DELETE', '/api/owner-meetings/' + id + '/materials/' + idx);
  },
  omUpdateNotice: function (id, title, content) {
    return core.request('PUT', '/api/owner-meetings/' + id + '/notice-draft', { title: title, content: content });
  },
  omToggleCompliance: function (id, status) {
    return core.request('PUT', '/api/owner-meetings/' + id + '/compliance?status=' + status);
  },
  omMinutes: function (id) {
    return core.request('GET', '/api/owner-meetings/' + id + '/minutes');
  },
  omUpdateMinutes: function (id, text) {
    return core.request('PUT', '/api/owner-meetings/' + id + '/minutes', { text: text });
  }
};
