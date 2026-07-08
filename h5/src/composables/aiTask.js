// 全局后台 AI 任务（生成会议纪要 / 生成党建新闻）。
// 状态挂模块单例，由 App 根部的 <AiTaskHost> 消费渲染悬浮指示——切到任何页面都在、
// 都能收到完成提示。发起页仍可用自己的全屏遮罩（在发起页时悬浮"生成中"条自动隐藏，避免重复）。
import { reactive } from 'vue'
import { navigateTo } from '@/utils/navigate'

export const aiTask = reactive({
  active: false,     // 有任务在后台跑（生成中）
  label: '',         // 生成中文案，如「会议纪要生成中…」
  originPath: '',    // 发起页 path（在该页时不显示悬浮"生成中"条，让页内遮罩负责）
  targetPath: '',    // 完成后点击直达的路由
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
  aiTask.originPath = ''; aiTask.targetPath = ''
}

// 点击"已完成"条 → 直达目标页并清空
export function openAiTaskTarget() {
  const t = aiTask.targetPath
  clearAiTask()
  if (t) navigateTo(t)
}
