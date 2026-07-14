// 全局后台 AI 任务（生成会议纪要 / 生成党建新闻）。
// 状态挂模块单例，由 App 根部的 <AiTaskHost> 消费渲染悬浮指示——切到任何页面都在、
// 都能收到完成提示。发起页仍可用自己的全屏遮罩（在发起页时悬浮"生成中"条自动隐藏，避免重复）。
import { reactive } from 'vue'
import { navigateTo, toRoute } from '@/utils/navigate'

// 悬浮条点击直达目标页：软路由偶发「URL 变了视图不切」→ 软跳后校验目标页根节点，未挂载则 location 硬跳兜底
const TARGET_ROOT_SEL = {
  '/minutes-view': '.mv-page',
  '/news': '.news-page',
  '/meeting-live-quick': '.live-page'
}
function openTaskTarget(url) {
  const r = toRoute(url)
  const qs = Object.keys(r.query).map((k) => k + '=' + encodeURIComponent(r.query[k])).join('&')
  const browserUrl = r.path + (qs ? '?' + qs : '')
  try { navigateTo(url) } catch (e) { console.error('[AI悬浮条] 软跳 reject：', e) }
  const sel = TARGET_ROOT_SEL[r.path]
  setTimeout(() => {
    const arrived = sel ? !!document.querySelector(sel) : (window.location.pathname === r.path)
    if (!arrived) {
      console.warn('[AI悬浮条] 软跳未挂载目标页，硬导航兜底 →', browserUrl)
      window.location.href = browserUrl
    }
  }, 500)
}

export const aiTask = reactive({
  active: false,     // 有任务在后台跑（生成中）
  label: '',         // 生成中文案，如「会议纪要生成中…」
  originPath: '',    // 发起页 path（失败时点「重试」回这里）
  targetPath: '',    // 生成中/完成后点击直达的路由（含 meetingId）
  // 发起页的全屏遮罩此刻是否正盖着这个任务：为真时隐藏悬浮条（避免与遮罩重复）；
  // 为假时（切走了 / 遮罩没恢复 / 落到无遮罩的详情页）→ 悬浮条负责兜底，保证「生成中」永远有入口。
  overlayShown: false,
  done: false,       // 已完成、待用户点击查看
  doneLabel: '',     // 「会议纪要已生成」
  failed: false,     // 失败、待用户点击重试
  failLabel: ''
})

export function startAiTask(o = {}) {
  aiTask.active = true
  aiTask.label = o.label || 'AI 处理中…'
  aiTask.originPath = o.originPath || ''
  aiTask.targetPath = o.targetPath || ''
  aiTask.done = false; aiTask.doneLabel = ''
  aiTask.failed = false; aiTask.failLabel = ''
}

export function finishAiTask(o = {}) {
  aiTask.active = false
  aiTask.done = true
  aiTask.doneLabel = o.doneLabel || '已完成'
  if (o.targetPath) aiTask.targetPath = o.targetPath
}

export function failAiTask(o = {}) {
  aiTask.active = false
  aiTask.failed = true
  aiTask.failLabel = o.failLabel || 'AI 处理失败'
}

export function clearAiTask() {
  aiTask.active = false; aiTask.done = false; aiTask.failed = false
  aiTask.label = ''; aiTask.doneLabel = ''; aiTask.failLabel = ''
  aiTask.originPath = ''; aiTask.targetPath = ''; aiTask.overlayShown = false
}

// 点「生成中」悬浮条 → 直达目标页（任务仍在跑，不清空；到了目标页看进度/结果）
export function peekAiTask() {
  if (aiTask.targetPath) openTaskTarget(aiTask.targetPath)
}

// 点击"已完成"条 → 直达目标页并清空
export function openAiTaskTarget() {
  const t = aiTask.targetPath
  clearAiTask()
  if (t) openTaskTarget(t)
}
