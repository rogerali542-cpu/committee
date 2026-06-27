// ═══════════════════════════════════════════════
// API — 小区公告
// ═══════════════════════════════════════════════

import core from '@/api/core';

export default {
  // 居民端：小区首页公告卡片（仅已发布）
  noticeList: function () {
    return core.request('GET', '/api/notices');
  },
  // 管理端（主任/副主任）：全部公告，含未发布
  noticeManageList: function () {
    return core.request('GET', '/api/notices/manage');
  },
  noticeCreate: function (data) {
    return core.request('POST', '/api/notices', data);
  },
  noticeUpdate: function (id, data) {
    return core.request('PUT', '/api/notices/' + id, data);
  },
  noticeRemove: function (id) {
    return core.request('DELETE', '/api/notices/' + id);
  }
};
