<template>
  <div class="todos-page" style="overflow-y:auto;">
    <PageNav title="待办事项" />
    <div v-if="loading" class="empty-state"><span>加载中...</span></div>

    <div v-else-if="emptyText" class="access-card">
      <div class="access-icon">{{ /待主任确认|加载失败/.test(emptyText) ? '📋' : '✓' }}</div>
      <span class="access-title">{{ emptyText }}</span>
    </div>

    <!-- 人工判断确认清单（0724 领导意见#4）：AI 只给建议，主任勾选/编辑/增补后才生成正式待办 -->
    <template v-else-if="reviewMode">
      <div class="review-intro">
        <div class="review-title">确认待办事项</div>
        <div class="review-sub">以下是系统从会议中识别的待办<b>建议</b>，请勾选保留、修改或补充后确认。未勾选的不会加入待办。</div>
      </div>
      <div class="review-card" v-for="(r, i) in reviewRows" :key="i" :class="{ off: !r.keep }">
        <div class="review-head">
          <label class="review-keep"><input type="checkbox" v-model="r.keep" /><span>保留此项</span></label>
          <button class="review-del" @click="reviewRows.splice(i, 1)">删除</button>
        </div>
        <textarea class="review-input title" v-model="r.title" rows="2" placeholder="待办内容"></textarea>
        <div class="review-row2">
          <input class="review-input" v-model="r.owner" placeholder="负责人（选填）" />
          <input class="review-input" v-model="r.due" placeholder="截止时间（选填）" />
        </div>
      </div>
      <button class="review-add" @click="addReviewRow">＋ 手动添加待办</button>
      <button class="review-confirm" :disabled="confirming" @click="confirmReview">{{ confirming ? '保存中…' : '确认待办清单' }}</button>
    </template>

    <template v-else>
      <div class="todo-card" v-for="(item, index) in cards" :key="item.id || index">
        <div class="todo-top">
          <span class="todo-idx">{{ index + 1 }}</span>
          <span class="todo-title">{{ item.title }}</span>
          <div class="todo-head-actions">
            <span class="status-tag" :class="'tag-' + item.status">{{ statusLabel(item.status) }}</span>
            <button v-if="canPushTicket && !item.ticketNo" class="delete-btn" :disabled="item.deleting" @click="deleteTodo(item)">删除</button>
          </div>
        </div>
        <div class="todo-meta" v-if="item.owner || item.dueText">
          <div class="meta-item" v-if="item.owner"><span class="meta-label">负责人</span><span class="meta-val">{{ item.owner }}</span></div>
          <div class="meta-item" v-if="item.dueText"><span class="meta-label">截止</span><span class="meta-val" :class="{ 'due-urgent': item.dueUrgent }">{{ item.dueText }}{{ item.dueUrgent ? ' ⚠' : '' }}</span></div>
        </div>

        <div class="todo-trace" v-if="item.lastActorName">{{ item.lastActorName }} · {{ item.updatedAt }} 更新</div>
        <div v-if="item.id" class="todo-footer">
          <span v-if="item.ticketNo" class="ticket-synced">工单 {{ item.ticketNo }} ›</span>
          <template v-if="item.status === 'todo'">
            <button class="self-handle-btn" :disabled="item.statusUpdating" @click="advanceStatus(item)">业委会自行处理</button>
            <button v-if="canPushTicket && !item.ticketNo" class="primary-btn primary-ticket" :disabled="item.ticketPushing" @click="pushTicket(item)">
              {{ item.ticketPushing ? '正在创建工单…' : '发工单处理' }}
            </button>
          </template>
          <template v-else-if="item.status === 'doing'">
            <button class="rollback-btn" :disabled="item.statusUpdating" @click="rollbackStatus(item, 'todo')">退回待处理</button>
            <button class="primary-btn primary-doing" :disabled="item.statusUpdating" @click="advanceStatus(item)">
              {{ item.statusUpdating ? '更新中…' : '标记完成' }}
            </button>
          </template>
          <template v-else>
            <button class="rollback-btn reopen-btn" :disabled="item.statusUpdating" @click="rollbackStatus(item, 'doing')">重新打开</button>
            <span class="completed-mark">✓ 已完成</span>
          </template>
        </div>
      </div>

      <!-- 解析失败兜底：整段原文 -->
      <div class="doc" v-if="rawText">
        <span class="doc-body">{{ rawText }}</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast, showModal } from '@/utils/ui'
import * as perm from '@/utils/perm'
import PageNav from '@/components/PageNav.vue'

// 待办独立页：优先用后端结构化待办（每条带 id、可点按钮改状态、留痕操作人）。
// 首次未固化时，复用下方解析逻辑把 AI 待办文本拆成卡片、回传后端固化，拿到带 id 的记录。
// 两者都解析不出时整段原文兜底。

const canPushTicket = perm.isChair()
const isChair = canPushTicket

// 人工判断（0724 领导意见#4）：AI 判断"是否待办"的边界不准，改为只出建议，
// 主任勾选保留/编辑/增补后才固化入库，不再静默自动生成待办。
const reviewMode = ref(false)
const reviewRows = ref([])
const confirming = ref(false)
function addReviewRow() {
  reviewRows.value.push({ keep: true, title: '', owner: '', due: '', status: '', source: '' })
}
async function confirmReview() {
  const rows = reviewRows.value.filter(r => r.keep && String(r.title || '').trim())
  if (!rows.length) { toast({ title: '请至少保留或添加一条待办', icon: 'none' }); return }
  confirming.value = true
  try {
    // sourceRef 携带来源议题（仅存档追溯，界面不展示）
    const payload = rows.map(r => ({ title: r.title.trim(), owner: r.owner, dueText: r.due, status: r.status, sourceRef: r.source }))
    const items = (await api.committeeTodoInit(meetingId, payload)) || []
    reviewMode.value = false
    cards.value = markDueUrgent(items)
    emptyText.value = items.length ? '' : '本次会议无明确待办事项。'
    toast({ title: '待办清单已确认', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '保存失败，请重试', icon: 'none' })
  } finally { confirming.value = false }
}

function statusLabel(status) {
  return status === 'done' ? '已完成' : status === 'doing' ? '处理中' : '待处理'
}

function statusType(s) {
  if (!s) return 'pending'
  if (/(已完成|已办结|已处理|完成)/.test(s) && !/(待|未|进行)/.test(s)) return 'done'
  if (/(进行|处理中)/.test(s)) return 'doing'
  return 'pending'
}
function statusKey(s) {
  const t = statusType(s)
  return t === 'done' ? 'done' : t === 'doing' ? 'doing' : 'todo'
}

// "未明确说明 / 无 / — / -" 等占位值视为空，卡片上不显示
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
  if (keys.indexOf('title') < 0) return null   // 没识别出"事项"列，不按表格处理
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

// —— 编号 + "字段：值" 列表解析（规则兜底格式）——
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
    // 无"事项："标签时，剥掉已识别字段段，剩下的主体作为标题
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

// 截止日期临近（≤3 天）标红警示。dueText 为文本，能解析成日期才判定。
function markDueUrgent(list) {
  const today = new Date(); today.setHours(0, 0, 0, 0)
  list.forEach(c => {
    if (c.dueText) { const d = new Date(c.dueText); c.dueUrgent = !isNaN(d) && (d - today) / 86400000 <= 3 }
  })
  return list
}

async function load() {
  try {
    const res = await api.committeeTodoList(meetingId)
    let items = (res && res.items) || []
    if (!items.length) {
      // 未固化：用 raw（AI 待办原文）解析后回传固化
      const text = res && res.raw ? String(res.raw).trim() : ''
      if (!text || /^[#\s]*(本次会议)?\s*无(明确)?待办/.test(text)) {
        loading.value = false; cards.value = []; rawText.value = ''
        emptyText.value = '本次会议无明确待办事项。'
        return
      }
      const parsed = parseTodos(text)
      if (parsed.length) {
        // 人工判断（0724）：不再静默自动固化。主任进入确认清单勾选/编辑/增补；委员先看占位。
        if (isChair) {
          reviewRows.value = parsed.map(c => ({ keep: true, title: c.title, owner: c.owner, due: c.due, status: c.status, source: c.source }))
          reviewMode.value = true
          loading.value = false
          return
        }
        loading.value = false; cards.value = []; rawText.value = ''
        emptyText.value = '待办清单待主任确认整理后查看。'
        return
      } else {
        loading.value = false; cards.value = []; rawText.value = text; emptyText.value = ''
        return
      }
    }
    cards.value = markDueUrgent(items)
    rawText.value = ''
    emptyText.value = items.length ? '' : '本次会议无明确待办事项。'
  } catch (e) {
    cards.value = []; rawText.value = ''
    emptyText.value = '加载失败，请稍后重试'
  }
  loading.value = false
}

// 委员点按钮改状态：乐观更新 + 失败回滚，成功后回填留痕信息。
async function setStatus(item, key) {
  if (!item.id || item.status === key) return
  const prev = item.status
  item.status = key
  try {
    const res = await api.committeeTodoStatus(meetingId, item.id, key)
    if (res) { item.lastActorName = res.lastActorName; item.updatedAt = res.updatedAt }
  } catch (e) {
    item.status = prev
    toast({ title: e.message || '更新失败', icon: 'none' })
  }
}

async function advanceStatus(item) {
  if (!item.id || item.statusUpdating || item.status === 'done') return
  item.statusUpdating = true
  try {
    await setStatus(item, item.status === 'doing' ? 'done' : 'doing')
  } finally {
    item.statusUpdating = false
  }
}

async function rollbackStatus(item, target) {
  if (!item.id || item.statusUpdating) return
  item.statusUpdating = true
  try {
    await setStatus(item, target)
  } finally {
    item.statusUpdating = false
  }
}

async function pushTicket(item) {
  if (!item.id || item.ticketPushing) return
  item.ticketPushing = true
  try {
    const res = await api.committeeTodoPushTicket(meetingId, item.id)
    item.externalTicketNo = res && res.externalTicketNo
    item.ticketNo = (res && res.ticketNo) || item.externalTicketNo
    item.ticketPushedAt = res && res.pushedAt
    if (item.status === 'todo') await setStatus(item, 'doing')
    toast({ title: res && res.created === false ? '工单已存在，已完成关联' : '工单创建成功', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '工单创建失败', icon: 'none' })
  } finally {
    item.ticketPushing = false
  }
}

async function deleteTodo(item) {
  if (!item.id || item.deleting) return
  const res = await showModal({
    title: '删除待办',
    content: '确认删除“' + item.title + '”？删除后不会进入工单系统。',
    confirmText: '删除',
    cancelText: '取消'
  })
  if (!res.confirm) return
  item.deleting = true
  try {
    await api.committeeTodoDelete(meetingId, item.id)
    cards.value = cards.value.filter(c => c.id !== item.id)
    if (!cards.value.length) emptyText.value = '本次会议无明确待办事项。'
    toast({ title: '待办已删除', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '删除失败', icon: 'none' })
  } finally {
    item.deleting = false
  }
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
:deep(.page-nav) { background: var(--c-primary-dark); }
.todos-page { min-height: 100vh; background: #f4f5f7; padding: 24rpx 24rpx 100rpx; box-sizing: border-box; }

/* 人工确认清单（0724）：AI 建议 → 主任勾选/编辑/增补后固化 */
.review-intro { background: #fff; border-radius: 16px; padding: 24rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.review-title { font-size: 34rpx; font-weight: 700; color: #1f2329; }
.review-sub { margin-top: 12rpx; font-size: 26rpx; color: #667B88; line-height: 1.5; }
.review-sub b { color: #C77800; }
.review-card { background: #fff; border-radius: 16px; padding: 20rpx 22rpx 24rpx; margin-bottom: 18rpx; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.review-card.off { opacity: 0.55; }
.review-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14rpx; }
.review-keep { display: flex; align-items: center; gap: 12rpx; font-size: 28rpx; color: #24364B; font-weight: 600; }
.review-keep input { width: 40rpx; height: 40rpx; }
.review-del { border: 0; background: transparent; color: #B0463A; font-size: 26rpx; padding: 6rpx 4rpx 6rpx 20rpx; }
.review-input { width: 100%; box-sizing: border-box; border: 2rpx solid #E2E5E9; border-radius: 12rpx; padding: 16rpx 18rpx; font-size: 29rpx; color: #24364B; background: #FAFBFC; }
.review-input.title { font-weight: 600; resize: none; line-height: 1.4; }
.review-row2 { display: flex; gap: 14rpx; margin-top: 14rpx; }
.review-row2 .review-input { flex: 1; }
.review-add { width: 100%; min-height: 84rpx; margin-top: 4rpx; border: 2rpx dashed #C9D0D6; border-radius: 16rpx; background: #F5F6F8; color: #526774; font-size: 29rpx; font-weight: 600; }
.review-add:active { background: #EAEDF0; }
.review-confirm { width: 100%; min-height: 92rpx; margin-top: 22rpx; border: 0; border-radius: 16rpx; background: var(--c-primary-dark); color: #fff; font-size: 32rpx; font-weight: 700; }
.review-confirm:disabled { opacity: 0.6; }
.review-confirm:active { opacity: 0.9; }
.todo-card { background: #fff; border-radius: 16px; padding: 22px 20px; margin-bottom: 18px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.todo-top { display: flex; align-items: flex-start; position: relative; }
.todo-idx { flex: none; width: 34px; height: 34px; line-height: 34px; text-align: center; border-radius: 50%; background: #1A4A8A; color: #fff; font-size: 18px; font-weight: 700; margin-right: 14px; margin-top: 2px; }
.todo-title { flex: 1; font-size: 22px; font-weight: 700; color: #1a1a1a; line-height: 1.55; }
.todo-head-actions { flex:none; display:flex; align-items:center; gap:7px; margin-left:10px; position:relative; }
.status-tag { height:30px; line-height:30px; padding:0 12px; border-radius:15px; font-size:15px; font-weight:700; white-space:nowrap; }
.tag-todo { background:#FFF0DC; color:#A65300; }
.tag-doing { background:#E4EEFF; color:#1857A6; }
.tag-done { background:#E3F5E9; color:#217246; }
.delete-btn { height:32px; padding:0 8px; border:0; background:transparent; color:#B42318; font-size:15px; font-weight:700; }
.delete-btn:disabled { opacity:.5; }
.todo-meta { display: flex; flex-direction: column; gap: 8px; margin: 14px 0 0 48px; }
.meta-item { display: flex; align-items: center; font-size: 19px; font-weight: 600; }
.meta-label { color: #555; margin-right: 8px; }
.meta-val { color: #222; }
.due-urgent { color: #CC0000; font-weight: 700; }

.todo-trace { font-size: 15px; color: #999; margin: 12px 0 0 48px; }
.todo-footer { display:flex; justify-content:flex-end; align-items:center; gap:12px; margin:18px 0 0 48px; padding-top:16px; border-top:1px solid #EEF0F2; }
.ticket-synced { margin-right:auto; color:#276A9E; font-size:16px; font-weight:700; }
.self-handle-btn { min-width:150px; height:48px; padding:0 17px; border:2px solid #C8D0D9; border-radius:12px; background:#fff; color:#4F5B67; font-size:16px; font-weight:700; }
.self-handle-btn:disabled { opacity:.6; }
.rollback-btn { height:48px; padding:0 16px; border:0; background:transparent; color:#66717D; font-size:16px; font-weight:700; white-space:nowrap; }
.rollback-btn:disabled { opacity:.6; }
.primary-btn { min-width:138px; height:50px; padding:0 22px; border:0; border-radius:13px; color:#fff; font-size:18px; font-weight:700; box-shadow:0 4px 10px rgba(26,74,138,.18); }
.primary-todo { background:#1A4A8A; }
.primary-ticket { background:#1A4A8A; min-width:150px; }
.primary-doing { background:#247A4A; }
.primary-btn:disabled { opacity:.6; }
.completed-mark { margin-left:auto; min-width:120px; height:46px; line-height:46px; border-radius:12px; background:#E3F5E9; color:#217246; text-align:center; font-size:18px; font-weight:700; }

@media (max-width: 420px) {
  .todo-card { padding:18px 16px; }
  .todo-title { font-size:20px; }
  .status-tag { padding:0 10px; font-size:14px; }
  .todo-meta,.todo-trace,.todo-footer { margin-left:0; }
  .todo-footer { flex-wrap:wrap; }
  .self-handle-btn,.primary-btn { flex:1; min-width:130px; }
  .rollback-btn { flex:0 0 auto; }
}

.access-card { background: #fff; border-radius: 24rpx; padding: 64rpx 36rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); text-align: center; }
.access-icon { width: 96rpx; height: 96rpx; border-radius: 50%; background: #E8F7EC; color: #2B9E55; display: flex; align-items: center; justify-content: center; margin: 0 auto 24rpx; font-size: 52rpx; font-weight: 700; }
.access-title { display: block; font-size: 38rpx; color: #1f2329; font-weight: 700; margin-bottom: 14rpx; }

.doc { background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; margin-top: 8rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.doc-body { display: block; font-size: 32rpx; color: #33373d; line-height: 1.9; white-space: pre-wrap; word-break: break-word; }

.empty-state { text-align: center; color: #666; font-size: 32rpx; padding: 80rpx 0; }
</style>
