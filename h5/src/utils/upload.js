// H5 文件选择 + 上传工具：替代小程序 wx.chooseImage / wx.chooseMessageFile + wx.uploadFile。
// 统一走后端通用附件接口 POST /api/attachments/upload（multipart 字段 file），
// 返回 { url, fileName, fileType, fileSize }；前端拿 url 再调各业务"添加"接口。
import core from '@/api/core'

// 弹出系统文件选择框，返回选中的 File；用户取消返回 null。
// accept 例：'image/*'(选图) / 'image/*,application/pdf' / '*/*'(任意文件)。
export function pickFile(accept = '*/*') {
  return new Promise((resolve) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = accept
    input.style.position = 'fixed'
    input.style.left = '-9999px'
    let settled = false
    const done = (v) => {
      if (settled) return
      settled = true
      window.removeEventListener('focus', onFocus, true)
      try { document.body.removeChild(input) } catch (e) {}
      resolve(v)
    }
    const onFocus = () => {
      // 取消选择兜底：窗口重新聚焦后若仍无文件，判定为取消（部分浏览器无 cancel 事件）
      setTimeout(() => { if (!settled && (!input.files || !input.files.length)) done(null) }, 600)
    }
    input.addEventListener('change', () => done(input.files && input.files[0] ? input.files[0] : null))
    input.addEventListener('cancel', () => done(null))
    window.addEventListener('focus', onFocus, true)
    document.body.appendChild(input)
    input.click()
  })
}

// 上传单个文件到通用附件接口，返回 { url, fileName, fileType, fileSize }。
export function uploadAttachment(file) {
  return core.uploadFile('/api/attachments/upload', file, 'file')
}

// 选文件 + 上传一步到位。返回 { url, fileName, fileType, fileSize }；用户取消返回 null。
// 上传失败会 reject（core.uploadFile 已弹"上传失败"toast）。
export async function pickAndUpload(accept = '*/*') {
  const file = await pickFile(accept)
  if (!file) return null
  return uploadAttachment(file)
}

// 人类可读的文件大小（用于 sizeText 展示，如 "1.2MB"）。
export function humanSize(bytes) {
  if (!bytes && bytes !== 0) return ''
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1024 / 1024).toFixed(1) + 'MB'
}

export default { pickFile, uploadAttachment, pickAndUpload, humanSize }
