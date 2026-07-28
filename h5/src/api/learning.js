// ═══════════════════════════════════════════════
// API — 业务学习
// ═══════════════════════════════════════════════

import core from '@/api/core';

export default {
  learningList: function (type, stage) {
    return core.request('GET', '/api/learning?type=' + type + (stage ? '&stage=' + stage : ''));
  },
  learningCounts: function (type) {
    return core.request('GET', '/api/learning/counts?type=' + type);
  },
  learningStart: function (id) {
    return core.request('PUT', '/api/learning/' + id + '/start');
  },
  learningFinish: function (id) {
    return core.request('PUT', '/api/learning/' + id + '/finish');
  },
  // 修改分类：内部学习(internal) / 外部培训(external)
  learningSetCategory: function (id, category) {
    return core.request('PUT', '/api/learning/' + id + '/category?category=' + category);
  },
  learningCreate: function (data) {
    return core.request('POST', '/api/learning', data);
  },
  learningRemove: function (id) {
    return core.request('DELETE', '/api/learning/' + id);
  },
  learningAddEvidence: function (id, fileName, fileType, fileUrl) {
    return core.request('POST', '/api/learning/' + id + '/evidences?fileName=' + encodeURIComponent(fileName) + '&fileType=' + fileType + (fileUrl ? '&fileUrl=' + encodeURIComponent(fileUrl) : ''));
  },
  learningRemoveEvidence: function (id, evId) {
    return core.request('DELETE', '/api/learning/' + id + '/evidences/' + evId);
  },
  learningSignIn: function (id) {
    return core.request('PUT', '/api/learning/' + id + '/sign-in');
  },
  learningSetAttendance: function (id, attendedNames) {
    return core.request('PUT', '/api/learning/' + id + '/attendance', { attendedNames: attendedNames });
  },
  // names 可选：通知页选定的参加人员，准备阶段随通知落库
  learningNotifyAll: function (id, names) {
    return core.request('POST', '/api/learning/' + id + '/notify-all', names && names.length ? { names: names } : {});
  }
};
