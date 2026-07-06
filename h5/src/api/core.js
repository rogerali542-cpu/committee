// 网络层：1:1 复刻 miniapp/utils/api/core.js
// - 注入 Authorization: Bearer <dev-token> + X-Active-Role-Id 头
// - 后端统一 { code, message, data } 包装：code===200 解包 data；401/403 跳登录；其余 reject(message)
// - validateStatus 全 resolve，由 body.code 决定成败（对齐小程序 wx.request：不看 HTTP 状态）
import axios from 'axios'
import router from '@/router'
import { getStorage } from '@/utils/storage'
import { toast } from '@/utils/ui'

const BASE = import.meta.env.VITE_API_BASE || '' // 留空走相对 /api（vite 代理 / Nginx 反代）

function buildAuthHeaders() {
  const headers = {}
  let token = getStorage('token', '')
  const activeRole = getStorage('activeRole', null)
  if (!token && activeRole && activeRole.id) token = 'dev-token-' + activeRole.id
  if (token) headers['Authorization'] = 'Bearer ' + token
  if (activeRole && activeRole.id) headers['X-Active-Role-Id'] = String(activeRole.id)
  return headers
}

const instance = axios.create({
  baseURL: BASE,
  timeout: 30000,
  validateStatus: () => true
})

instance.interceptors.request.use((cfg) => {
  Object.assign(cfg.headers, buildAuthHeaders())
  return cfg
})

function unwrap(res) {
  const body = res.data
  if (body && body.code === 200) return body.data
  if (body && (body.code === 401 || body.code === 403)) {
    if (router.currentRoute.value.path !== '/login') router.replace('/login')
  }
  const msg = body && body.message ? body.message : ('HTTP ' + (res.status || 'error'))
  return Promise.reject(new Error(msg))
}

export function request(method, path, data, options = {}) {
  return instance({ method, url: path, data, timeout: options.timeout || 30000 }).then(
    unwrap,
    (err) => { toast({ title: '网络异常', icon: 'none' }); return Promise.reject(err) }
  )
}

// 与小程序 realRequest 对齐：支持 options.timeout（LLM 长耗时如润色 210s）
export function realRequest(method, path, data, options) {
  return request(method, path, data, options)
}

// 上传：H5 用 FormData + Blob/File，字段名默认 'file'（后端 multipart 接口契约不变）
// options.timeout：慢接口（如 OCR+大模型抽取）可放开，默认沿用实例 30s
export function uploadFile(path, fileOrBlob, name = 'file', formData = {}, options = {}) {
  const fd = new FormData()
  fd.append(name, fileOrBlob, (fileOrBlob && fileOrBlob.name) || 'upload')
  Object.keys(formData || {}).forEach((k) => fd.append(k, formData[k]))
  return instance.post(path, fd, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: options.timeout || 30000, onUploadProgress: options.onUploadProgress }).then(
    unwrap,
    (err) => { toast({ title: '上传失败', icon: 'none' }); return Promise.reject(err) }
  )
}

// 多文件上传：同一字段名 append 多个 File（后端用 MultipartFile[] 接）。
export function uploadFiles(path, files, name = 'files', formData = {}, options = {}) {
  const fd = new FormData()
  ;(files || []).forEach((f) => fd.append(name, f, (f && f.name) || 'upload'))
  Object.keys(formData || {}).forEach((k) => fd.append(k, formData[k]))
  return instance.post(path, fd, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: options.timeout || 30000 }).then(
    unwrap,
    (err) => { toast({ title: '上传失败', icon: 'none' }); return Promise.reject(err) }
  )
}

export const get = (p, d) => request('GET', p, d)
export const post = (p, d) => request('POST', p, d)
export const put = (p, d) => request('PUT', p, d)
export const del = (p, d) => request('DELETE', p, d)

export default { request, realRequest, uploadFile, uploadFiles, get, post, put, delete: del, BASE }
