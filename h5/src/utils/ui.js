// 命令式 UI：替代 wx.showToast/showModal/showLoading/showActionSheet。
// uiState 由挂在 App 根部的 <UiHost> 消费渲染；这里只维护状态 + 暴露命令函数。
import { reactive } from 'vue'

let _seq = 0

export const uiState = reactive({
  toasts: [],                       // [{ id, title, icon }]
  loading: { show: false, title: '' },
  modal: null,                      // { title, content, confirmText, cancelText, showCancel, editable, placeholderText, _resolve }
  actionSheet: null                 // { itemList, _resolve }
})

// toast：icon 'none' | 'success'
export function toast({ title = '', icon = 'none', duration = 1500 } = {}) {
  const id = ++_seq
  uiState.toasts.push({ id, title, icon })
  setTimeout(() => {
    const i = uiState.toasts.findIndex((t) => t.id === id)
    if (i >= 0) uiState.toasts.splice(i, 1)
  }, duration)
}
export function hideToast() { uiState.toasts.splice(0) }

export function showLoading({ title = '' } = {}) { uiState.loading = { show: true, title } }
export function hideLoading() { uiState.loading = { show: false, title: '' } }

// 返回 Promise，resolve({ confirm, content })，对齐 wx.showModal 的 res
export function showModal(opts = {}) {
  return new Promise((resolve) => {
    uiState.modal = {
      title: opts.title !== undefined ? opts.title : '提示',  // 显式传 '' 可无标题
      content: opts.content || '',
      confirmText: opts.confirmText || '确定',
      cancelText: opts.cancelText || '取消',
      showCancel: opts.showCancel !== false,
      editable: !!opts.editable,
      placeholderText: opts.placeholderText || '',
      size: opts.size || '',   // 'large' = 加大版（识别结果等重要确认框用）
      contentBold: !!opts.contentBold,       // 正文加粗加深（黑体）
      emphasizeConfirm: !!opts.emphasizeConfirm, // 确认按钮加宽、取消收窄，突出确认动作
      showClose: !!opts.showClose, // 右上角 ×：单纯关闭，resolve {close:true}（区别于 cancel 按钮的动作）
      _resolve: resolve
    }
  })
}
export function resolveModal(result) {
  const m = uiState.modal
  uiState.modal = null
  if (m && m._resolve) m._resolve(result)
}

// 返回 Promise，resolve({ tapIndex })，对齐 wx.showActionSheet
export function showActionSheet(opts = {}) {
  return new Promise((resolve) => {
    uiState.actionSheet = { itemList: opts.itemList || [], _resolve: resolve }
  })
}
export function resolveActionSheet(result) {
  const a = uiState.actionSheet
  uiState.actionSheet = null
  if (a && a._resolve) a._resolve(result)
}

export default { toast, hideToast, showLoading, hideLoading, showModal, showActionSheet }
