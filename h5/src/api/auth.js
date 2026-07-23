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
  },
  // 测试期身份名单（免登录）：登录页/个人中心切换身份用，名单以数据库 user_roles 为准
  devRoles: function () {
    return core.request('GET', '/api/auth/dev-roles');
  }
};
