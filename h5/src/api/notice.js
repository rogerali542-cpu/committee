// ═══════════════════════════════════════════════
// API — 小区公告
// ═══════════════════════════════════════════════

import core from '@/api/core';

export default {
  // 居民端：小区首页公告卡片（仅已发布）
  noticeList: function () {
    return core.request('GET', '/api/notices');
  }
};
