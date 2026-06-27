// localStorage 封装，替代小程序 wx.getStorageSync/setStorageSync。
// 对象自动 JSON 序列化/反序列化；字符串（如 token='dev-token-1'）原样存取。

export function getStorage(key, def = null) {
  try {
    const v = localStorage.getItem(key)
    if (v === null || v === undefined) return def
    try { return JSON.parse(v) } catch { return v } // 非 JSON（纯字符串）原样返回
  } catch { return def }
}

export function setStorage(key, val) {
  try {
    localStorage.setItem(key, typeof val === 'string' ? val : JSON.stringify(val))
  } catch (e) { /* 忽略（隐私模式/超额） */ }
}

export function removeStorage(key) { try { localStorage.removeItem(key) } catch (e) {} }

export function clearStorage() { try { localStorage.clear() } catch (e) {} }

export default { getStorage, setStorage, removeStorage, clearStorage }
