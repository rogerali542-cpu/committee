// ═══════════════════════════════════════════════
// API — 接待记录
// ═══════════════════════════════════════════════

import core from '@/api/core';

export default {
  receptionSystem: function () {
    return core.request('GET', '/api/receptions/system');
  },
  receptionUpdateSystem: function (data) {
    return core.request('PUT', '/api/receptions/system', data);
  },
  receptionRecords: function (filter) {
    return core.request('GET', '/api/receptions/records?filter=' + filter);
  },
  receptionCreate: function (data) {
    return core.request('POST', '/api/receptions/records', data);
  },
  receptionUpdateResolution: function (id, resolution) {
    return core.request('PUT', '/api/receptions/records/' + id + '/resolution?resolution=' + encodeURIComponent(resolution));
  },
  receptionToggleFollow: function (id, field) {
    return core.request('PUT', '/api/receptions/records/' + id + '/follow?field=' + field);
  },
  // 业委会：把物业类诉求转给物业
  receptionDispatch: function (id) {
    return core.request('POST', '/api/receptions/records/' + id + '/dispatch');
  },
  // 物业：开始处理（未处理→处理中）
  receptionPropertyStart: function (id) {
    return core.request('POST', '/api/receptions/records/' + id + '/property-start');
  },
  // 物业：回填处理结果（需先上传佐证）
  receptionPropertyReply: function (id, reply) {
    return core.request('POST', '/api/receptions/records/' + id + '/property-reply', { reply: reply });
  },
  // 物业工作台：派给物业的工单
  receptionPropertyTasks: function (status) {
    return core.request('GET', '/api/receptions/property-tasks' + (status ? '?status=' + status : ''));
  },
  receptionStats: function () {
    return core.request('GET', '/api/receptions/stats');
  },
  receptionRemove: function (id) {
    return core.request('DELETE', '/api/receptions/records/' + id);
  },
  receptionAddEvidence: function (id, fileName, fileType, fileUrl) {
    return core.request('POST', '/api/receptions/records/' + id + '/evidences?fileName=' + encodeURIComponent(fileName) + '&fileType=' + fileType + (fileUrl ? '&fileUrl=' + encodeURIComponent(fileUrl) : ''));
  },
  receptionRemoveEvidence: function (id, evId) {
    return core.request('DELETE', '/api/receptions/records/' + id + '/evidences/' + evId);
  }
};
