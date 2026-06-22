// ═══════════════════════════════════════════════
// API Core — HTTP 请求层 + mock 开关
// ═══════════════════════════════════════════════

var mockRouter = require('../mocks/index');
var BASE = 'http://127.0.0.1:8080';
var USE_MOCK = true; // 开发模式：true=本地mock，false=连接后端

function request(method, path, data) {
  return new Promise(function (resolve, reject) {
    // 开发模式：直接返回本地 mock
    if (USE_MOCK) {
      var result = mockRouter.mockRequest(method, path, data);
      setTimeout(function () { resolve(result); }, 100);
      return;
    }

    // 生产模式：真实 HTTP 请求
    var app = getApp();
    var header = {};
    if (app.globalData.token) {
      header['Authorization'] = 'Bearer ' + app.globalData.token;
    }
    if (app.globalData.activeRole) {
      header['X-Active-Role-Id'] = String(app.globalData.activeRole.id);
    }

    wx.request({
      url: BASE + path,
      method: method,
      header: header,
      data: data,
      success: function (res) {
        if (res.data && res.data.code === 200) {
          resolve(res.data.data);
        } else if (res.data && res.data.code === 401) {
          wx.navigateTo({ url: '/pages/login/login' });
          reject(new Error(res.data.message || '未登录'));
        } else {
          reject(new Error(res.data ? res.data.message : '请求失败'));
        }
      },
      fail: function (err) {
        wx.showToast({ title: '网络异常', icon: 'none' });
        reject(err);
      }
    });
  });
}

// 真实后端请求（无视 USE_MOCK）——快速会议的录音/ASR/纪要必须打真后端，mock 没意义
function realRequest(method, path, data) {
  return new Promise(function (resolve, reject) {
    var app = getApp();
    var header = {};
    if (app.globalData.token) header['Authorization'] = 'Bearer ' + app.globalData.token;
    if (app.globalData.activeRole) header['X-Active-Role-Id'] = String(app.globalData.activeRole.id);
    wx.request({
      url: BASE + path, method: method, header: header, data: data,
      success: function (res) {
        if (res.data && res.data.code === 200) resolve(res.data.data);
        else reject(new Error(res.data ? res.data.message : '请求失败'));
      },
      fail: function (err) { wx.showToast({ title: '网络异常', icon: 'none' }); reject(err); }
    });
  });
}

// 上传文件（录音/音频）到真实后端
function uploadFile(path, filePath, name, formData) {
  return new Promise(function (resolve, reject) {
    var app = getApp();
    var header = {};
    if (app.globalData.token) header['Authorization'] = 'Bearer ' + app.globalData.token;
    if (app.globalData.activeRole) header['X-Active-Role-Id'] = String(app.globalData.activeRole.id);
    wx.uploadFile({
      url: BASE + path, filePath: filePath, name: name || 'file',
      header: header, formData: formData || {},
      success: function (res) {
        try {
          var body = JSON.parse(res.data);
          if (body && body.code === 200) resolve(body.data);
          else reject(new Error(body ? body.message : '上传失败'));
        } catch (e) { reject(new Error('上传响应解析失败')); }
      },
      fail: function (err) { wx.showToast({ title: '上传失败', icon: 'none' }); reject(err); }
    });
  });
}

// 便捷方法
function get(path, data) { return request('GET', path, data); }
function post(path, data) { return request('POST', path, data); }
function put(path, data) { return request('PUT', path, data); }
function del(path, data) { return request('DELETE', path, data); }

module.exports = {
  request: request,
  realRequest: realRequest,
  uploadFile: uploadFile,
  get: get,
  post: post,
  put: put,
  delete: del,
  BASE: BASE,
  USE_MOCK: USE_MOCK
};
