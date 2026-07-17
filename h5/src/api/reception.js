// ═══════════════════════════════════════════════
// API — 接待记录
// 0716 重做（方案 A）：内部派单流的 4 个 API 已删
// （receptionDispatch / receptionPropertyStart / receptionPropertyReply / receptionPropertyTasks），
// 改为 receptionPushTicket 派到外部工单系统。receptionToggleFollow 一并删除
// （它写的 fedOwner 已不参与办结判定，办结改为「填了处理结果」）。原实现见 commit 6745a12。
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
  // 单条详情：接待处理页进来就拉这个
  receptionRecord: function (id) {
    return core.request('GET', '/api/receptions/records/' + id);
  },
  receptionCreate: function (data) {
    return core.request('POST', '/api/receptions/records', data);
  },
  // 填写处理结果 —— 这就是办结动作
  receptionUpdateResolution: function (id, resolution) {
    return core.request('PUT', '/api/receptions/records/' + id + '/resolution?resolution=' + encodeURIComponent(resolution));
  },
  // 派发到外部工单系统。幂等：重复调用返回对方已有工单，不会重复建单
  receptionPushTicket: function (id) {
    return core.request('POST', '/api/receptions/records/' + id + '/ticket');
  },
  // 转物业：只在本系统打标记，不发任何外部请求（与 receptionPushTicket 是两条路）。可来回切
  receptionSetPropertyTransferred: function (id, transferred) {
    return core.request('PUT', '/api/receptions/records/' + id + '/property-transfer?transferred=' + (transferred ? 'true' : 'false'));
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
