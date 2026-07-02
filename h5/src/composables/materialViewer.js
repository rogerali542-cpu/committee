// 全局会议材料预览器：任意页面调用 openMaterialViewer(item) 即弹出全屏预览，
// 关闭后回到原页（无路由跳转、不进历史栈，天然"从哪进就回哪"，对微信/老年人最友好）。
import { reactive } from 'vue'

const state = reactive({
  visible: false,
  url: '',
  name: '',
  fileType: ''
})

// item 可为材料对象（含 url/name/fileType）或存档补充材料（含 fileUrl/fileName/fileType）
export function openMaterialViewer(item) {
  if (!item) return
  const url = item.url || item.fileUrl || ''
  if (!url) return
  state.url = url
  state.name = item.name || item.fileName || '会议材料'
  state.fileType = item.fileType || ''
  state.visible = true
}

export function closeMaterialViewer() {
  state.visible = false
  state.url = ''
  state.name = ''
  state.fileType = ''
}

export function useMaterialViewer() {
  return state
}
