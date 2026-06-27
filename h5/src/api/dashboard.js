// ═══════════════════════════════════════════════
// API — Dashboard
// ═══════════════════════════════════════════════

import core from '@/api/core';

export default {
  dashboardStats: function () {
    return core.request('GET', '/api/dashboard/stats');
  }
};
