// ═══════════════════════════════════════════════
// API — 印章管理（用印申请 + 用印台账）
// ═══════════════════════════════════════════════
import core from '@/api/core';

export default {
  // 印章清单（固定三枚）
  sealList: function () {
    return core.request('GET', '/api/seals/list');
  },
  // 用印台账。filter = all | pending | approved
  sealRecords: function (filter) {
    return core.request('GET', '/api/seals/records?filter=' + (filter || 'all'));
  },
  sealStats: function () {
    return core.request('GET', '/api/seals/stats');
  },
  // 申请用印：{ sealType, useDate:'yyyy-MM-dd', purpose, attachments: [{url,name,type,size}] }（0728：文件名并入用途，附件另传）
  sealApply: function (data) {
    return core.request('POST', '/api/seals/records', data);
  },
  // 保管人确认用印（盖章留档）
  sealConfirm: function (id) {
    return core.request('PUT', '/api/seals/records/' + id + '/confirm');
  },
  // 保管人驳回
  sealReject: function (id, reason) {
    return core.request('PUT', '/api/seals/records/' + id + '/reject' + (reason ? '?reason=' + encodeURIComponent(reason) : ''));
  },
  sealRemove: function (id) {
    return core.request('DELETE', '/api/seals/records/' + id);
  }
};
