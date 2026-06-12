// ═══════════════════════════════════════════════
// API — 委员会会议
// ═══════════════════════════════════════════════

var core = require('./core');

module.exports = {
  committeeList: function (stage) {
    return core.request('GET', '/api/committees' + (stage ? '?stage=' + stage : ''));
  },
  committeeDetail: function (id) {
    return core.request('GET', '/api/committees/' + id);
  },
  committeeCreate: function (data) {
    return core.request('POST', '/api/committees', data);
  },
  committeeAdvance: function (id, action) {
    return core.request('POST', '/api/committees/' + id + '/advance?action=' + action);
  },
  committeeToggleDelivery: function (id, userRoleId, field) {
    return core.request('PUT', '/api/committees/' + id + '/delivery/' + userRoleId + '?field=' + field);
  },
  committeeSendAll: function (id) {
    return core.request('POST', '/api/committees/' + id + '/delivery/send-all');
  },
  committeeToggleAttend: function (id, userRoleId, field) {
    return core.request('PUT', '/api/committees/' + id + '/attendance/' + userRoleId + '?field=' + field);
  },
  committeeSelfToggle: function (id, field) {
    return core.request('PUT', '/api/committees/' + id + '/self?field=' + field);
  },
  committeeSignAll: function (id) {
    return core.request('POST', '/api/committees/' + id + '/attendance/sign-all');
  },
  committeeAddTopic: function (id, title, type, decisionType, optionsJson) {
    var params = '?title=' + encodeURIComponent(title) + '&type=' + type;
    if (decisionType) params += '&decisionType=' + decisionType;
    if (optionsJson) params += '&options=' + encodeURIComponent(optionsJson);
    return core.request('POST', '/api/committees/' + id + '/topics' + params);
  },
  committeeRemoveTopic: function (id, topicId) {
    return core.request('DELETE', '/api/committees/' + id + '/topics/' + topicId);
  },
  committeeVote: function (id, topicId, choice, selectedId) {
    var params = choice ? '?choice=' + choice : '';
    if (selectedId) params += (params ? '&' : '?') + 'selectedId=' + selectedId;
    return core.request('PUT', '/api/committees/' + id + '/topics/' + topicId + '/vote' + params);
  },
  committeeProxyTargets: function (id, keyword) {
    var params = keyword ? '?keyword=' + encodeURIComponent(keyword) : '';
    return core.request('GET', '/api/committees/' + id + '/proxy-targets' + params);
  },
  committeeProxySubmit: function (id, data) {
    return core.request('POST', '/api/committees/' + id + '/proxy-actions', data);
  },
  committeeToggleFlag: function (id, flag) {
    return core.request('PUT', '/api/committees/' + id + '/flags?flag=' + flag);
  },
  committeeToggleJuwei: function (id) {
    return core.request('POST', '/api/committees/' + id + '/juwei');
  },
  committeeAddEvidence: function (id, fileName, fileType) {
    return core.request('POST', '/api/committees/' + id + '/evidences?fileName=' + encodeURIComponent(fileName) + '&fileType=' + fileType);
  },
  committeeRemoveEvidence: function (id, evId) {
    return core.request('DELETE', '/api/committees/' + id + '/evidences/' + evId);
  },
  committeePublish: function (id) {
    return core.request('POST', '/api/committees/' + id + '/publish');
  },
  committeeMinutes: function (id) {
    return core.request('GET', '/api/committees/' + id + '/minutes');
  },
  committeeStats: function () {
    return core.request('GET', '/api/committees/stats');
  },
  committeePublishScore: function () {
    return core.request('GET', '/api/committees/publish-score');
  },
  committeeRemove: function (id) {
    return core.request('DELETE', '/api/committees/' + id);
  },
  committeeCompliance: function (id, status) {
    return core.request('PUT', '/api/committees/' + id + '/compliance?status=' + status);
  }
};
