// API — 通知
import core from '@/api/core';

export default {
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
