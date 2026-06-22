// ═══════════════════════════════════════════════
// API — Dashboard
// ═══════════════════════════════════════════════

var core = require('./core');

module.exports = {
  dashboardStats: function () {
    return core.request('GET', '/api/dashboard/stats');
  }
};
