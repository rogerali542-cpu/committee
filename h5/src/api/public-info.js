// ═══════════════════════════════════════════════
// API — 信息公开
// ═══════════════════════════════════════════════

import core from '@/api/core';

export default {
  publicInfo: function () {
    return core.request('GET', '/api/public-info');
  }
};
