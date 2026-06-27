// ═══════════════════════════════════════════════
// API — 认证
// ═══════════════════════════════════════════════

import core from '@/api/core';

export default {
  login: function (code, nickName, avatarUrl) {
    return core.request('POST', '/api/auth/login', { code: code, nickName: nickName, avatarUrl: avatarUrl });
  },
  refreshMe: function () {
    return core.request('GET', '/api/auth/me');
  }
};
