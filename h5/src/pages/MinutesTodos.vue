<template>
  <div class="todos-page" style="overflow-y:auto;">
    <PageNav title="待办事项" style="display:block;margin:-24rpx -24rpx 0;" />
    <div v-if="loading" class="empty-state"><span>加载中...</span></div>

    <div v-else-if="emptyText" class="access-card">
      <div class="access-icon">✓</div>
      <span class="access-title">暂无待办</span>
      <span class="access-text">{{ emptyText }}</span>
    </div>

    <template v-else>
      <div class="info-banner">内部资料 · 会议待办事项，仅供业委会内部跟踪</div>

      <div class="todo-card" v-for="(item, index) in cards" :key="index">
        <div class="todo-top">
          <span class="todo-idx">{{ index + 1 }}</span>
          <span class="todo-title">{{ item.title }}</span>
        </div>
        <div class="todo-meta" v-if="item.owner || item.due">
          <div class="meta-item" v-if="item.owner"><span class="meta-label">负责人</span><span class="meta-val">{{ item.owner }}</span></div>
          <div class="meta-item" v-if="item.due"><span class="meta-label">截止</span><span class="meta-val">{{ item.due }}</span></div>
        </div>
        <span class="todo-source" v-if="item.source">来源 · {{ item.source }}</span>
        <span class="todo-status" :class="'status-' + item.statusType" v-if="item.status">{{ item.status }}</span>
      </div>

      <!-- 解析失败兜底：整段原文 -->
      <div class="doc" v-if="rawText">
        <span class="doc-body">{{ rawText }}</span>
      </div>

      <div class="actions">
        <button class="btn-primary copy-btn" @click="copyTodos">复制待办全文</button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast } from '@/utils/ui'
import PageNav from '@/components/PageNav.vue'

// 会议待办事项独立页：把后端 todoListText 解析成结构化卡片清单展示。
// 兼容两种后端格式：① Markdown 表格（LLM 常用）② 编号 + “字段：值” 列表（规则兜底）。
// 两者都解析不出时整段兜底，避免比 showModal 更糟。

function statusType(s) {
  if (!s) return 'pending'
  if (/(已完成|已办结|已处理|完成)/.test(s) && !/(待|未|进行)/.test(s)) return 'done'
  if (/(进行|处理中)/.test(s)) return 'doing'
  return 'pending'
}

// “未明确说明 / 无 / — / -” 等占位值视为空，卡片上不显示
const BLANK_RE = /^(未明确说明|未明确|未说明|暂无|无|—+|-+|\/|待定)$/
function cellVal(v) {
  const s = (v || '').trim().replace(/\*+/g, '')
  return BLANK_RE.test(s) ? '' : s
}

// —— Markdown 表格解析 ——
function splitCells(line) {
  let s = line.trim()
  if (s.charAt(0) === '|') s = s.slice(1)
  if (s.charAt(s.length - 1) === '|') s = s.slice(0, -1)
  return s.split('|').map(function (c) { return c.trim() })
}
function isDivider(cells) {
  return cells.every(function (c) { return c === '' || /^:?-{2,}:?$/.test(c) })
}
function colKey(name) {
  if (/(事项|待办|任务|工作内容|内容)/.test(name)) return 'title'
  if (/(负责|责任|承办|经办)/.test(name)) return 'owner'
  if (/(截止|完成时间|时限|期限|时间)/.test(name)) return 'due'
  if (/来源/.test(name)) return 'source'
  if (/(状态|进度)/.test(name)) return 'status'
  return ''
}
function parseTable(text) {
  const rows = text.split(/\r?\n/)
    .map(function (l) { return l.trim() })
    .filter(function (l) { return l.indexOf('|') >= 0 })
    .map(splitCells)
    .filter(function (cells) { return cells.length >= 2 })
  if (rows.length < 2) return null
  const keys = rows[0].map(colKey)
  if (keys.indexOf('title') < 0) return null   // 没识别出“事项”列，不按表格处理
  const cards = []
  for (let i = 1; i < rows.length; i++) {
    const cells = rows[i]
    if (isDivider(cells)) continue
    const card = { title: '', owner: '', due: '', status: '', source: '' }
    keys.forEach(function (k, idx) { if (k && cells[idx] != null) card[k] = cellVal(cells[idx]) })
    if (!card.title) {
      const first = cells.find(function (c) { return c && !/^:?-{2,}:?$/.test(c) })
      card.title = (first || '').replace(/\*+/g, '') || '待办事项'
    }
    if (card.title === '待办事项' && !card.owner && !card.due && !card.status && !card.source) continue
    card.statusType = statusType(card.status)
    cards.push(card)
  }
  return cards.length ? cards : null
}

// —— 编号 + “字段：值” 列表解析（规则兜底格式）——
const STOP = '(?=[；;]|负责人[:：]|截止时间[:：]|截止[:：]|完成时间[:：]|来源议题[:：]|来源[:：]|状态[:：]|事项[:：]|$)'
const LABELS = ['负责人', '截止时间', '截止', '完成时间', '来源议题', '来源', '状态', '事项']
function pick(block, label) {
  const m = block.match(new RegExp(label + '[:：]\\s*(.+?)' + STOP))
  return m ? cellVal(m[1].replace(/[，,。；;]+$/, '').replace(/^[（(]+|[）)]+$/g, '')) : ''
}
function parseOne(block) {
  if (!block) return null
  const owner = pick(block, '负责人')
  const due = pick(block, '截止时间') || pick(block, '截止') || pick(block, '完成时间')
  const status = pick(block, '状态')
  const source = pick(block, '来源议题') || pick(block, '来源')
  let title = pick(block, '事项')
  if (!title) {
    // 无“事项：”标签时，剥掉已识别字段段，剩下的主体作为标题
    let t = block.replace(/\*+/g, '')
    LABELS.forEach(function (lab) { t = t.replace(new RegExp(lab + '[:：][^；;\\n]*[；;]?', 'g'), '') })
    title = t.replace(/[；;]+/g, ' ').trim()
  }
  title = (title || '').replace(/\*+/g, '').replace(/^[\s\-—·:：（(]+|[\s\-—·:：）)]+$/g, '').trim()
  if (!title) title = '待办事项'
  if (!owner && !due && !status && !source && title === '待办事项') return null
  return { title: title, owner: owner, due: due, status: status, source: source, statusType: statusType(status) }
}
function parseList(text) {
  const body = text.replace(/^[#\s]*待办事项(清单)?\s*[:：]?\s*\n?/, '')
  const startRe = /^\s*(?:\d+[.、)]|[-*•])\s*/
  const blocks = []
  let cur = null
  body.split(/\r?\n/).forEach(function (line) {
    if (!line.trim()) return
    if (startRe.test(line)) {
      if (cur != null) blocks.push(cur)
      cur = line.replace(startRe, '').trim()
    } else if (cur != null) {
      cur += ' ' + line.trim()
    } else {
      cur = line.trim()
    }
  })
  if (cur != null) blocks.push(cur)
  return blocks.map(parseOne).filter(Boolean)
}

function parseTodos(text) {
  if (!text) return []
  if (text.indexOf('|') >= 0) {
    const t = parseTable(text)
    if (t && t.length) return t
  }
  return parseList(text)
}

const route = useRoute()

const loading = ref(true)
const cards = ref([])
const rawText = ref('')
const emptyText = ref('')

let meetingId = null
let fullText = ''

async function load() {
  let text = ''
  try { text = await api.committeeQuickTodos(meetingId) } catch (e) {}
  text = text && String(text).trim()
  fullText = text || ''
  if (!text || /^[#\s]*(本次会议)?\s*无(明确)?待办/.test(text)) {
    loading.value = false
    cards.value = []
    rawText.value = ''
    emptyText.value = '本次会议无明确待办事项。'
    return
  }
  const result = parseTodos(text)
  loading.value = false
  cards.value = result
  rawText.value = result.length ? '' : text   // 解析不出卡片时整段兜底
  emptyText.value = ''
}

async function copyTodos() {
  const text = fullText
  if (!text) return
  await navigator.clipboard.writeText(text)
  toast({ title: '已复制' })
}

onMounted(() => {
  meetingId = parseInt(route.query.meetingId)
  if (!meetingId) {
    loading.value = false
    emptyText.value = '缺少会议参数'
    return
  }
  load()
})
</script>

<style scoped>
.todos-page { min-height: 100vh; background: #f4f5f7; padding: 24rpx 24rpx 100rpx; box-sizing: border-box; }

.info-banner { background: #F1EEFB; color: #6B4FBB; font-size: 28rpx; font-weight: 600; text-align: center; padding: 18rpx 20rpx; border-radius: 14rpx; margin-bottom: 20rpx; }

.todo-card { background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.todo-top { display: flex; align-items: flex-start; }
.todo-idx { flex: none; width: 44rpx; height: 44rpx; line-height: 44rpx; text-align: center; border-radius: 50%; background: #6B4FBB; color: #fff; font-size: 28rpx; margin-right: 16rpx; margin-top: 2rpx; }
.todo-title { flex: 1; font-size: 34rpx; font-weight: 600; color: #222; line-height: 1.5; }
.todo-meta { display: flex; flex-wrap: wrap; margin: 16rpx 0 0 60rpx; }
.meta-item { display: flex; align-items: center; font-size: 28rpx; margin-right: 32rpx; margin-top: 4rpx; }
.meta-label { color: #666; margin-right: 10rpx; }
.meta-val { color: #444; }
.todo-source { display: block; font-size: 28rpx; color: #666; margin: 12rpx 0 0 60rpx; }
.todo-status { display: inline-block; margin: 14rpx 0 0 60rpx; font-size: 28rpx; padding: 6rpx 18rpx; border-radius: 14rpx; }
.status-pending { background: #FFF3E0; color: #E67E22; }
.status-doing { background: #E8F1FF; color: #2F6BD8; }
.status-done { background: #E8F7EC; color: #2B9E55; }

.access-card { background: #fff; border-radius: 24rpx; padding: 64rpx 36rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); text-align: center; }
.access-icon { width: 96rpx; height: 96rpx; border-radius: 50%; background: #E8F7EC; color: #2B9E55; display: flex; align-items: center; justify-content: center; margin: 0 auto 24rpx; font-size: 52rpx; font-weight: 700; }
.access-title { display: block; font-size: 38rpx; color: #1f2329; font-weight: 700; margin-bottom: 14rpx; }
.access-text { display: block; font-size: 30rpx; color: #666; line-height: 1.7; }

.doc { background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; margin-top: 8rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.doc-body { display: block; font-size: 32rpx; color: #33373d; line-height: 1.9; white-space: pre-wrap; word-break: break-word; }

.actions { margin-top: 20rpx; }
.copy-btn { width: 100%; }
.btn-primary { background: #FFA800; color: #fff; border-radius: 18rpx; font-size: 34rpx; font-weight: 600; height: 96rpx; line-height: 96rpx; }

.empty-state { text-align: center; color: #666; font-size: 32rpx; padding: 80rpx 0; }
</style>
