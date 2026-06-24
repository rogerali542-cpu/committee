var mockRouter = require('../mocks/index');

var BASE = 'http://192.168.0.51:8080';
var USE_MOCK = false;

function buildHeader() {
  var app = getApp();
  var header = {};
  var globalData = app.globalData || {};
  var token = globalData.token || wx.getStorageSync('token');
  var activeRole = globalData.activeRole || wx.getStorageSync('activeRole');

  if (token && !globalData.token) globalData.token = token;
  if (activeRole && !globalData.activeRole) globalData.activeRole = activeRole;
  if (!token && activeRole && activeRole.id) token = 'dev-token-' + activeRole.id;

  if (token) header.Authorization = 'Bearer ' + token;
  if (activeRole && activeRole.id) header['X-Active-Role-Id'] = String(activeRole.id);
  return header;
}

function handleResponse(res, resolve, reject) {
  var body = res.data;
  if (body && body.code === 200) {
    resolve(body.data);
    return;
  }
  if (body && (body.code === 401 || body.code === 403)) {
    wx.navigateTo({ url: '/pages/login/login' });
  }
  var msg = body && body.message ? body.message : ('HTTP ' + (res.statusCode || 'error'));
  reject(new Error(msg));
}

function request(method, path, data) {
  return new Promise(function (resolve, reject) {
    if (USE_MOCK) {
      var result = mockRouter.mockRequest(method, path, data);
      setTimeout(function () { resolve(result); }, 100);
      return;
    }

    wx.request({
      url: BASE + path,
      method: method,
      header: buildHeader(),
      data: data,
      success: function (res) { handleResponse(res, resolve, reject); },
      fail: function (err) {
        wx.showToast({ title: '网络异常', icon: 'none' });
        reject(err);
      }
    });
  });
}

function realRequest(method, path, data, options) {
  return new Promise(function (resolve, reject) {
    var req = {
      url: BASE + path,
      method: method,
      header: buildHeader(),
      data: data,
      success: function (res) { handleResponse(res, resolve, reject); },
      fail: function (err) {
        wx.showToast({ title: '网络异常', icon: 'none' });
        reject(err);
      }
    };
    if (options && options.timeout) req.timeout = options.timeout;
    wx.request(req);
  });
}

function uploadFile(path, filePath, name, formData) {
  return new Promise(function (resolve, reject) {
    wx.uploadFile({
      url: BASE + path,
      filePath: filePath,
      name: name || 'file',
      header: buildHeader(),
      formData: formData || {},
      success: function (res) {
        try {
          var body = JSON.parse(res.data);
          if (body && body.code === 200) {
            resolve(body.data);
          } else {
            reject(new Error(body && body.message ? body.message : '上传失败'));
          }
        } catch (e) {
          reject(new Error('上传响应解析失败'));
        }
      },
      fail: function (err) {
        wx.showToast({ title: '上传失败', icon: 'none' });
        reject(err);
      }
    });
  });
}

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
