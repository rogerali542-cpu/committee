// ═══════════════════════════════════════════════
// API — 委员会会议
// ═══════════════════════════════════════════════

import core from '@/api/core';

export default {
  committeeList: function (stage) {
    return core.request('GET', '/api/committees' + (stage ? '?stage=' + stage : ''));
  },
  // 资料库：已归档会议
  committeeArchiveList: function () {
    return core.request('GET', '/api/committees?archived=true');
  },
  // 直接归档（不公示）
  committeeArchive: function (id) {
    return core.request('POST', '/api/committees/' + id + '/archive');
  },
  // 撤销归档（仅误归档用，必填原因；已公示需先撤回公示）
  committeeRevokeArchive: function (id, reason) {
    return core.request('POST', '/api/committees/' + id + '/archive/revoke', { reason: reason });
  },
  committeeAddArchiveExtra: function (id, fileName, sizeText, fileType, reason, fileUrl) {
    var p = '?fileName=' + encodeURIComponent(fileName) + '&sizeText=' + encodeURIComponent(sizeText || '') + '&reason=' + encodeURIComponent(reason || '');
    if (fileType) p += '&fileType=' + fileType;
    if (fileUrl) p += '&fileUrl=' + encodeURIComponent(fileUrl);
    return core.request('POST', '/api/committees/' + id + '/archive-extras' + p);
  },
  committeeDetail: function (id) {
    return core.request('GET', '/api/committees/' + id);
  },
  committeeMembers: function () {
    return core.request('GET', '/api/committees/members');
  },
  committeeCreate: function (data) {
    return core.request('POST', '/api/committees', data);
  },
  committeeUpdate: function (id, data) {
    return core.request('PUT', '/api/committees/' + id, data);
  },
  committeeAdvance: function (id, action, mode) {
    return core.realRequest('POST', '/api/committees/' + id + '/advance?action=' + action + (mode ? '&mode=' + mode : ''));
  },
  committeeToggleDelivery: function (id, userRoleId, field) {
    return core.request('PUT', '/api/committees/' + id + '/delivery/' + userRoleId + '?field=' + field);
  },
  committeeSendAll: function (id, memberIds) {
    return core.request('POST', '/api/committees/' + id + '/delivery/send-all', { memberIds: memberIds || [] });
  },
  // 委员打开详情时回写"已读"（仅对已送达内容生效）
  committeeMarkDeliveryRead: function (id) {
    return core.request('POST', '/api/committees/' + id + '/delivery/read');
  },
  committeeToggleAttend: function (id, userRoleId, field) {
    return core.request('PUT', '/api/committees/' + id + '/attendance/' + userRoleId + '?field=' + field);
  },
  committeeSelfToggle: function (id, field) {
    return core.realRequest('PUT', '/api/committees/' + id + '/self?field=' + field);
  },
  committeeSignAll: function (id) {
    return core.realRequest('POST', '/api/committees/' + id + '/attendance/sign-all');
  },
  // 导出签到名单（返回 { fileName, content(CSV) }）
  committeeExportAttendance: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/attendance/export');
  },
  // 录音多条：获取会议全部录音列表
  committeeRecordings: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/recordings');
  },
  // 上传录音（纯存，不自动转写）；durationSec 为录音时长（秒），用于转写页展示
  committeeUploadRecording: function (id, filePath, durationSec) {
    var q = (durationSec != null && durationSec > 0) ? ('?durationSec=' + Math.round(durationSec)) : '';
    return core.uploadFile('/api/committees/' + id + '/quick/recording/upload' + q, filePath, 'file');
  },
  // 删除一条录音（转写页删废录/多余段）
  committeeDeleteRecording: function (id, recordingId) {
    return core.realRequest('DELETE', '/api/committees/' + id + '/quick/recordings/' + recordingId);
  },
  // 主任选片触发转写
  committeeTranscribeRecording: function (id, recordingId) {
    return core.realRequest('POST', '/api/committees/' + id + '/quick/recordings/' + recordingId + '/transcribe');
  },
  committeeAddTopic: function (id, title, type, decisionType, optionsJson, realNameVote) {
    var params = '?title=' + encodeURIComponent(title) + '&type=' + type;
    if (decisionType) params += '&decisionType=' + decisionType;
    if (optionsJson) params += '&options=' + encodeURIComponent(optionsJson);
    if (realNameVote) params += '&realNameVote=true';
    return core.realRequest('POST', '/api/committees/' + id + '/topics' + params);
  },
  committeeRemoveTopic: function (id, topicId) {
    return core.request('DELETE', '/api/committees/' + id + '/topics/' + topicId);
  },
  committeeRenameTopic: function (id, topicId, title) {
    return core.realRequest('PUT', '/api/committees/' + id + '/topics/' + topicId + '/title?title=' + encodeURIComponent(title));
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
    return core.realRequest('POST', '/api/committees/' + id + '/juwei');
  },
  committeeAddEvidence: function (id, fileName, fileType, fileUrl) {
    return core.realRequest('POST', '/api/committees/' + id + '/evidences?fileName=' + encodeURIComponent(fileName) + '&fileType=' + fileType + (fileUrl ? '&fileUrl=' + encodeURIComponent(fileUrl) : ''));
  },
  committeeRemoveEvidence: function (id, evId) {
    return core.realRequest('DELETE', '/api/committees/' + id + '/evidences/' + evId);
  },
  committeePublish: function (id) {
    return core.request('POST', '/api/committees/' + id + '/publish');
  },
  committeeWithdrawPublish: function (id, reason) {
    return core.request('POST', '/api/committees/' + id + '/publish/withdraw', { reason: reason });
  },
  committeeMinutes: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/minutes');
  },
  committeeMinutesRevisions: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/minutes/revisions');
  },
  committeeUpdateMinutes: function (id, text, reason) {
    return core.realRequest('PUT', '/api/committees/' + id + '/minutes', { text: text, reason: reason });
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
  },
  committeeAddMaterial: function (id, fileName, sizeText, fileType, fileUrl) {
    var p = '?fileName=' + encodeURIComponent(fileName) + '&sizeText=' + encodeURIComponent(sizeText || '');
    if (fileType) p += '&fileType=' + fileType;
    if (fileUrl) p += '&fileUrl=' + encodeURIComponent(fileUrl);
    return core.realRequest('POST', '/api/committees/' + id + '/materials' + p);
  },
  committeeRemoveMaterial: function (id, materialId) {
    return core.request('DELETE', '/api/committees/' + id + '/materials/' + materialId);
  },
  committeeUpdateNotice: function (id, title, content) {
    return core.request('PUT', '/api/committees/' + id + '/notice-draft', { title: title, content: content });
  },
  committeeQuickUploadRecording: function (id, filePath) {
    return core.uploadFile('/api/committees/' + id + '/quick/recording/upload', filePath, 'file');
  },
  committeeQuickRecordingStatus: function (id, taskId) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/recording/status?taskId=' + encodeURIComponent(taskId));
  },
  committeeQuickExtract: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/extract');
  },
  committeeQuickTranscript: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/transcript');
  },
  committeeQuickConfirm: function (id, data) {
    return core.realRequest('POST', '/api/committees/' + id + '/quick/confirm', data);
  },
  committeeQuickPolish: function (id, data) {
    // 大模型生成纪要较慢，放开默认 60s 超时（后端 LLM 上限 180s）
    return core.realRequest('POST', '/api/committees/' + id + '/quick/polish', data, { timeout: 210000 });
  },
  committeeQuickTopicReport: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/topic-report');
  },
  committeeQuickTodos: function (id) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/todos');
  },
  committeeQuickTopicSummary: function (id, data) {
    return core.realRequest('POST', '/api/committees/' + id + '/quick/topic-summary', data);
  },
  committeeQuickTopicSummaryTask: function (id, data) {
    return core.realRequest('POST', '/api/committees/' + id + '/quick/topic-summary/tasks', data);
  },
  committeeQuickTopicSummaryTaskStatus: function (id, taskId) {
    return core.realRequest('GET', '/api/committees/' + id + '/quick/topic-summary/tasks/' + encodeURIComponent(taskId));
  }
};
