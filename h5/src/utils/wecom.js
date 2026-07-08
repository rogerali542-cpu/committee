// 企业微信（WeCom）JS-SDK 封装：在企业微信内置浏览器里调起相册多选 chooseImage。
// 背景：企业微信/微信的 X5 内核会拦截 <input type=file multiple> 退化成单选，
// 纯 HTML 无解，只能走 JS-SDK。本模块负责：注入 jweixin → 取后端签名 wx.config →
// chooseImage 拿 localIds → getLocalImgData 拿 base64 → 转成 File 交回既有上传流水线。
//
// 任一环节失败/未配置都抛错，调用方 catch 后回退系统文件选择器，绝不打断用户。
import api from '@/api'

const UA = navigator.userAgent || ''
// 企业微信 UA 含 "wxwork"（微信是 "MicroMessenger" 但不含 wxwork）
export function isWecom() {
  return /wxwork/i.test(UA)
}
const IS_IOS = /iphone|ipad|ipod/i.test(UA)
// iOS 企业微信 JS-SDK 签名要用「进入应用时的初始 URL」（SPA 内 pushState 后 iOS 仍认初始 URL）；
// 安卓认当前 URL。这里在模块首次加载时抓一次初始 URL。
const ENTRY_URL = location.href.split('#')[0]

function signUrl() {
  return IS_IOS ? ENTRY_URL : location.href.split('#')[0]
}

let sdkPromise = null
function loadJweixin() {
  if (window.wx && window.wx.config) return Promise.resolve(window.wx)
  if (sdkPromise) return sdkPromise
  sdkPromise = new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = 'https://res.wx.qq.com/open/js/jweixin-1.2.0.js'
    s.onload = () => resolve(window.wx)
    s.onerror = () => { sdkPromise = null; reject(new Error('jweixin 加载失败')) }
    document.head.appendChild(s)
  })
  return sdkPromise
}

let readyPromise = null
// 完成 wx.config 并等 wx.ready。同一页只配一次；失败清缓存以便下次重试。
function ensureConfig() {
  if (readyPromise) return readyPromise
  readyPromise = (async () => {
    const wx = await loadJweixin()
    const cfg = await api.get('/api/wecom/js-config?url=' + encodeURIComponent(signUrl()))
    // cfg = { corpId, timestamp, nonceStr, signature }
    return await new Promise((resolve, reject) => {
      wx.config({
        beta: true, // 企业微信部分接口需 beta
        debug: false,
        appId: cfg.corpId,
        timestamp: cfg.timestamp,
        nonceStr: cfg.nonceStr,
        signature: cfg.signature,
        jsApiList: ['chooseImage', 'getLocalImgData']
      })
      wx.ready(() => resolve(wx))
      wx.error((e) => { readyPromise = null; reject(e || new Error('wx.config 失败')) })
    })
  })().catch((e) => { readyPromise = null; throw e })
  return readyPromise
}

// getLocalImgData 的返回在不同机型/版本有差异：可能带 data:...;base64, 前缀、可能不带、
// 安卓老版本还会夹换行。这里统一规整成标准 base64 再转 File。
function localDataToFile(localData, name) {
  let b64 = String(localData || '')
  let mime = 'image/jpeg'
  if (b64.slice(0, 5) === 'data:') {
    const m = /^data:([^;]+);base64,/.exec(b64)
    if (m) mime = m[1]
    const comma = b64.indexOf(',')
    b64 = comma >= 0 ? b64.slice(comma + 1) : b64
  }
  b64 = b64.replace(/[\r\n\s]/g, '')
  const bin = atob(b64)
  const u8 = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) u8[i] = bin.charCodeAt(i)
  return new File([u8], name, { type: mime })
}

// 在企业微信里唤起相册多选，返回 File[]（用户取消返回 []）。非取消类错误会抛出，调用方回退。
export async function chooseWecomImages(count = 9) {
  const wx = await ensureConfig()
  const localIds = await new Promise((resolve, reject) => {
    wx.chooseImage({
      count,
      sizeType: ['original', 'compressed'],
      sourceType: ['album', 'camera'],
      success: (r) => resolve(r.localIds || []),
      fail: (e) => reject(e || new Error('chooseImage 失败'))
    })
  })
  const files = []
  for (let i = 0; i < localIds.length; i++) {
    const localData = await new Promise((resolve, reject) => {
      wx.getLocalImgData({
        localId: localIds[i],
        success: (r) => resolve(r.localData),
        fail: (e) => reject(e || new Error('getLocalImgData 失败'))
      })
    })
    files.push(localDataToFile(localData, '照片-' + (i + 1) + '.jpg'))
  }
  return files
}

// 判断错误是否为「用户主动取消」——取消时不该再弹系统选择器。
export function isWecomCancel(e) {
  const msg = (e && (e.errMsg || e.message)) || ''
  return /cancel/i.test(String(msg))
}
