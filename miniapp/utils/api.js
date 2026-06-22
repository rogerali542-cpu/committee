// 桥接文件 — 微信小程序 require 不支持目录自动解析 index.js
// 所有页面 require('../../utils/api') 先命中此文件
module.exports = require('./api/index');
