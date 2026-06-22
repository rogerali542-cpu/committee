// ═══════════════════════════════════════════════
// API — 信息公开
// ═══════════════════════════════════════════════

var core = require('./core');

module.exports = {
  publicInfo: function () {
    return core.request('GET', '/api/public-info');
  }
};
