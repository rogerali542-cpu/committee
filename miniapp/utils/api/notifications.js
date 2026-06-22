// API — 通知
var core = require('./core');

module.exports = {
  notificationList: function () {
    return core.request('GET', '/api/notifications');
  },
  notificationRead: function (id) {
    return core.request('PUT', '/api/notifications/' + id + '/read');
  },
  notificationReadAll: function () {
    return core.request('PUT', '/api/notifications/read-all');
  }
};
