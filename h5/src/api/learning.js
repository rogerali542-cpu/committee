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
  learningNotifyAll: function (id) {
    return core.request('POST', '/api/learning/' + id + '/notify-all');
  }
};
