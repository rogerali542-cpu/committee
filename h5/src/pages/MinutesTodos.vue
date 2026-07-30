<template>
  <div class="todos-page" :class="{ 'agg-page': aggMode }" style="overflow-y:auto;">
    <!-- 双模式（0730 用户定）：带 meetingId＝单场会议的待办（原有流程不变）；
         不带参数＝业委会整体待办（0731 设计师稿：中性深灰页头+三页签+只读卡片点击分流详情） -->
    <PageNav v-if="!aggMode" title="待办事项" />
    <div v-else class="agg-hd">
      <div class="agg-hd-row">
        <div class="agg-back" @click="goBack">‹</div>
        <span class="agg-hd-title">待办事项</span>
      </div>
      <div class="agg-hd-sub">{{ aggPendingCount }} 项待办</div>
    </div>
    <div v-if="loading" class="empty-state"><span>加载中...</span></div>

    <!-- 错误/等待态（加载失败/缺参/待主任确认）：只提示，不给增删 -->
    <div v-else-if="isErrorEmpty" class="access-card">
      <div class="access-icon">📋</div>
      <span class="access-title">{{ emptyText }}</span>
    </div>

    <!-- 人工判断确认清单（0724 领导意见#4）：AI 只给建议，主任勾选/编辑/增补后才生成正式待办 -->
    <template v-else-if="reviewMode">
      <div class="review-intro">
        <div class="review-title">确认待办事项</div>
        <div class="review-sub">以下是系统从会议中识别的待办<b>建议</b>，可修改内容、删除不需要的、或手动补充，确认后才生成正式待办。</div>
      </div>
      <div class="review-card" v-for="(r, i) in reviewRows" :key="i">
        <span class="review-idx">{{ i + 1 }}</span>
        <button class="review-del" @click="reviewRows.splice(i, 1)" aria-label="删除此项">×</button>
        <textarea class="review-input title" v-model="r.title" rows="2"></textarea>
        <div class="review-row2">
          <!-- 负责人与手动新增同口径：从委员下拉选择；AI 识别出的名字不在名单时保留为一个选项，不丢失 -->
          <select class="review-input review-select" :class="{ 'is-placeholder': !r.owner }" v-model="r.owner">
            <option value="">负责人（选填）</option>
            <option v-if="r.owner && !members.some(m => m.name === r.owner)" :value="r.owner">{{ r.owner }}</option>
            <option v-for="m in members" :key="m.userRoleId || m.name" :value="m.name">{{ m.name }}<template v-if="m.role">（{{ m.role }}）</template></option>
          </select>
          <input class="review-input" v-model="r.due" placeholder="截止时间（选填）" />
        </div>
      </div>
      <div class="review-actions">
        <button class="review-add" @click="addReviewRow">＋ 手动添加待办</button>
        <button class="review-confirm" :disabled="confirming" @click="confirmReview">{{ confirming ? '保存中…' : '确认待办清单' }}</button>
      </div>
    </template>

    <!-- 业委会整体待办（0731 设计师稿）：三页签过滤；卡片只读——标题+状态（待跟进灰/处理中蓝）、
         日期+来源+进度两列副区；点击分流到能操作的详情页（会议→该场待办页、接待→接待详情）。
         已办事项不在本页，走底部「档案馆 · 已办事项」 -->
    <template v-else-if="aggMode">
      <div class="agg-tabs">
        <div v-for="t in AGG_TABS" :key="t.key" class="agg-tab" :class="{ active: aggTab === t.key }" @click="aggTab = t.key">{{ t.label }}</div>
      </div>
      <div class="agg-body">
        <div class="agg-card" v-for="it in aggList" :key="it.key" @click="it.onTap()">
          <div class="agg-line1">
            <span class="agg-title">{{ it.title }}</span>
            <span class="agg-status" :class="it.doing ? 'st-doing' : 'st-todo'">{{ it.doing ? '处理中' : '待跟进' }}</span>
          </div>
          <div class="agg-line2">
            <span class="agg-date">{{ it.dateText }}</span>
            <div class="agg-srccol">
              <span>{{ it.source }}</span>
              <span v-if="it.progress">{{ it.progress }}</span>
            </div>
          </div>
        </div>
        <div v-if="!aggList.length" class="access-card">
          <div class="access-icon">✓</div>
          <span class="access-title">当前没有待办事项。</span>
        </div>
        <!-- 已办事项收进档案馆（0731 设计师稿），本页只留未办 -->
        <div class="agg-arch" @click="goArchiveDone">
          <span class="agg-arch-text"><b>历史记录</b> · 已办事项 {{ aggDoneCount }} 项</span>
          <i class="agg-arch-arr"></i>
        </div>
      </div>
    </template>

    <template v-else>
      <!-- 无 AI 待办（无卡片）时的提示；主任仍可在下方手动增补。新增表单打开时收起，避免「无待办」与「正在新增」同屏矛盾 -->
      <div v-if="!cards.length && !rawText && !manualForm.open" class="access-card">
        <div class="access-icon">✓</div>
        <span class="access-title">本次会议无明确待办事项。</span>
      </div>
      <!-- 已完成沉底：未办的排前面，完成一条自动沉到底部 -->
      <div class="todo-card" v-for="(item, index) in sortedCards" :key="item.id || index">
        <!-- 编辑态：整卡切换为修改表单（复用新增表单样式） -->
        <template v-if="editForm.id && editForm.id === item.id">
          <div class="manual-form-title">修改待办</div>
          <textarea class="manual-input title" v-model="editForm.title" rows="2"></textarea>
          <select class="manual-input manual-select" :class="{ 'is-placeholder': !editForm.owner }" v-model="editForm.owner">
            <option value="">负责人（选填）</option>
            <option v-if="editForm.owner && !members.some(m => m.name === editForm.owner)" :value="editForm.owner">{{ editForm.owner }}</option>
            <option v-for="m in members" :key="m.userRoleId || m.name" :value="m.name">{{ m.name }}<template v-if="m.role">（{{ m.role }}）</template></option>
          </select>
          <div class="manual-form-acts">
            <button class="manual-cancel" :disabled="editForm.saving" @click="cancelEdit">取消</button>
            <button class="manual-save" :disabled="editForm.saving" @click="saveEdit(item)">{{ editForm.saving ? '保存中…' : '保存' }}</button>
          </div>
        </template>
        <template v-else>
        <div class="todo-top">
          <span class="todo-idx">{{ index + 1 }}</span>
          <span class="todo-title">{{ item.title }}</span>
          <div class="todo-head-actions">
            <span class="status-tag" :class="'tag-' + item.status">{{ statusLabel(item.status) }}</span>
            <button v-if="canPushTicket && !item.ticketNo && item.id" class="edit-btn" @click="beginEdit(item)">编辑</button>
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
        </template>
      </div>

      <!-- 解析失败兜底：整段原文 -->
      <div class="doc" v-if="rawText">
        <span class="doc-body">{{ rawText }}</span>
      </div>

      <!-- 手动添加（主任）：AI 待办边界难界定，除识别外还需人工增补/删除（删除在每条卡片上） -->
      <div v-if="isChair" class="manual-zone">
        <button v-if="!manualForm.open" class="manual-add-btn" @click="openManual">＋ 手动添加待办</button>
        <div v-else class="manual-form">
          <div class="manual-form-title">新增待办</div>
          <textarea class="manual-input title" v-model="manualForm.title" rows="2"></textarea>
          <select class="manual-input manual-select" :class="{ 'is-placeholder': !manualForm.owner }" v-model="manualForm.owner">
            <option value="">负责人（选填）</option>
            <option v-for="m in members" :key="m.userRoleId || m.name" :value="m.name">{{ m.name }}<template v-if="m.role">（{{ m.role }}）</template></option>
          </select>
          <div class="manual-form-acts">
            <button class="manual-cancel" :disabled="manualForm.saving" @click="closeManual">取消</button>
            <!-- 不按「内容为空」置灰：灰按钮对老人像坏了；常亮，点了空内容由 saveManual 弹「请输入待办内容」说明原因 -->
            <button class="manual-save" :disabled="manualForm.saving" @click="saveManual">{{ manualForm.saving ? '保存中…' : '保存' }}</button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast, showModal } from '@/utils/ui'
import * as perm from '@/utils/perm'
import { navigateTo, navigateBack } from '@/utils/navigate'
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
  reviewRows.value.push({ title: '', owner: '', due: '', status: '', source: '' })
}
async function confirmReview() {
  // 卡片留下即保留、删除即弃用（0724：去掉重复的「保留」勾选）；空标题的行忽略
  const rows = reviewRows.value.filter(r => String(r.title || '').trim())
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
// 独立聚合模式（0730 用户定；0731 设计师稿）：URL 不带 meetingId＝业委会整体待办。
// 本页只读总览：会议待办来自跨会议聚合接口，接待待办来自接待记录未办结项；
// 操作分流到各自详情页（会议→该场待办页，接待→接待详情）。已办事项收进档案馆「已办」页签。
const aggMode = ref(false)
const AGG_TABS = [
  { key: 'all', label: '全部' },
  { key: 'reception', label: '业主接待' },
  { key: 'meeting', label: '会议议题' }
]
const aggTab = ref('all')
const aggMeeting = ref([])    // 跨会议待办原始 VO（含 done，档案馆计数用）
const aggReception = ref([])  // 接待记录原始数据（含 done）
function fmtAggDate(s) {
  const p = String(s || '').split('-')
  return p.length === 3 ? (Number(p[1]) + '月' + Number(p[2]) + '日') : String(s || '')
}
function shortMeetingName(title) {
  const m = String(title || '').match(/第\s*(\d+)\s*次/)
  return m ? ('第' + m[1] + '次例会') : String(title || '')
}
const aggItems = computed(() => {
  const meeting = (aggMeeting.value || []).filter(t => t.status !== 'done').map(t => ({
    key: 'm-' + t.id, kind: 'meeting',
    title: t.title,
    date: t.meetingDate || '',
    dateText: fmtAggDate(t.meetingDate),
    // 来源行「第2次例会 · 议题」＋进度行（有啥显啥：负责人/截止）
    source: [shortMeetingName(t.meetingTitle), t.sourceRef ? String(t.sourceRef).slice(0, 12) : ''].filter(Boolean).join(' · '),
    progress: t.owner ? ('负责人 ' + t.owner) : (t.dueText ? ('截止 ' + t.dueText) : ''),
    doing: t.status === 'doing',
    onTap: () => goMeetingTodos(t.meetingId)
  }))
  const rec = (aggReception.value || []).filter(r => !r.done && r.visitorName !== '无人来访').map(r => ({
    key: 'r-' + r.id, kind: 'reception',
    title: String(r.content || '').slice(0, 20) || '来访事项',
    date: r.date || '',
    dateText: fmtAggDate(r.date),
    source: '接待' + (r.visitorName ? (' · ' + r.visitorName + ' 反映') : ''),
    progress: r.propertyTransferred ? '已交物业' : (r.ticketPushed ? '已派单' : '尚未派单'),
    doing: !!(r.propertyTransferred || r.ticketPushed),
    onTap: () => goReception(r)
  }))
  // 旧在前（欠账优先）：拖得最久的排最上
  return [...meeting, ...rec].sort((a, b) => String(a.date).localeCompare(String(b.date)))
})
const aggList = computed(() => aggTab.value === 'all' ? aggItems.value : aggItems.value.filter(i => i.kind === aggTab.value))
const aggPendingCount = computed(() => aggItems.value.length)
const aggDoneCount = computed(() =>
  (aggMeeting.value || []).filter(t => t.status === 'done').length +
  (aggReception.value || []).filter(r => r.done && r.visitorName !== '无人来访').length)
async function loadAll() {
  try {
    const [meeting, reception] = await Promise.all([
      api.committeeTodosOverview().catch(() => []),
      api.receptionRecords('all').catch(() => [])
    ])
    aggMeeting.value = meeting || []
    aggReception.value = reception || []
    emptyText.value = ''
  } catch (e) {
    emptyText.value = '加载失败，请稍后重试'
  }
  loading.value = false
}
function goBack() { navigateBack() }
// 同组件带参自跳（/minutes-todos → /minutes-todos?meetingId=N）：vue-router 复用实例不触发
// onMounted，直接整页跳转最稳
function goMeetingTodos(id) { window.location.href = '/minutes-todos?meetingId=' + id }
function goReception(r) {
  navigateTo('/pages/reception-detail/reception-detail?id=' + r.id)
  // 软路由偶发不切视图（本仓已知坑）：0.3s 后没见到接待详情哨兵就硬跳
  setTimeout(() => {
    if (!document.querySelector('.recep-detail')) window.location.href = '/reception-detail?id=' + r.id
  }, 300)
}
function goArchiveDone() {
  navigateTo('/library?tab=done')
  setTimeout(() => {
    if (!document.querySelector('.arch-page')) window.location.href = '/library?tab=done'
  }, 300)
}

// 错误/等待态（加载失败/缺参/委员等主任确认）：只提示，不给增删；genuine 空("无明确待办")不算，主任可增补
const isErrorEmpty = computed(() => /加载失败|缺少会议参数|待主任确认整理后查看/.test(emptyText.value))

// 手动添加待办（0729 用户定：AI 边界难界定，需人工增补）。负责人从业委会委员下拉选择。
const members = ref([])
let membersLoaded = false
async function loadMembers() {
  if (membersLoaded) return
  membersLoaded = true
  try { members.value = (await api.committeeMembers()) || [] } catch (e) { membersLoaded = false }
}
const manualForm = ref({ open: false, title: '', owner: '', saving: false })
function openManual() { manualForm.value = { open: true, title: '', owner: '', saving: false }; loadMembers() }
function closeManual() { manualForm.value.open = false }
async function saveManual() {
  const title = String(manualForm.value.title || '').trim()
  if (!title) { toast({ title: '请输入待办内容', icon: 'none' }); return }
  manualForm.value.saving = true
  try {
    const item = await api.committeeTodoAdd(meetingId, { title, owner: manualForm.value.owner, status: 'todo' })
    if (item) { cards.value = markDueUrgent([...cards.value, item]); emptyText.value = '' }
    manualForm.value.open = false
    toast({ title: '已添加待办', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '添加失败，请重试', icon: 'none' })
  } finally { manualForm.value.saving = false }
}

// 展示排序：已完成沉底（未办的保持原顺序在前），标记完成后自动沉下去
const sortedCards = computed(() => [
  ...cards.value.filter(c => c.status !== 'done'),
  ...cards.value.filter(c => c.status === 'done')
])

// 编辑已有待办（0729 用户定：写错只能删了重加太糙）。已发工单的不可改，入口同删除一起隐藏。
const editForm = ref({ id: null, title: '', owner: '', saving: false })
function beginEdit(item) {
  editForm.value = { id: item.id, title: item.title || '', owner: item.owner || '', saving: false }
  loadMembers()
}
function cancelEdit() { editForm.value = { id: null, title: '', owner: '', saving: false } }
async function saveEdit(item) {
  const title = String(editForm.value.title || '').trim()
  if (!title) { toast({ title: '请输入待办内容', icon: 'none' }); return }
  editForm.value.saving = true
  try {
    const res = await api.committeeTodoUpdate(item.meetingId || meetingId, item.id, { title, owner: editForm.value.owner })
    if (res) { item.title = res.title; item.owner = res.owner; item.lastActorName = res.lastActorName; item.updatedAt = res.updatedAt }
    cancelEdit()
    toast({ title: '已保存修改', icon: 'success' })
  } catch (e) {
    editForm.value.saving = false
    toast({ title: (e && e.message) || '保存失败，请重试', icon: 'none' })
  }
}

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
          reviewRows.value = parsed.map(c => ({ title: c.title, owner: c.owner, due: c.due, status: c.status, source: c.source }))
          reviewMode.value = true
          loadMembers()   // 确认清单里的负责人下拉要用
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
    // 聚合模式下每条自带来源会议 id；单会议模式退回页面级 meetingId
    const res = await api.committeeTodoStatus(item.meetingId || meetingId, item.id, key)
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
    const res = await api.committeeTodoPushTicket(item.meetingId || meetingId, item.id)
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
    await api.committeeTodoDelete(item.meetingId || meetingId, item.id)
    cards.value = cards.value.filter(c => c.id !== item.id)
    if (!cards.value.length && !aggMode.value) emptyText.value = '本次会议无明确待办事项。'
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
    // 不带 meetingId＝独立聚合页（0730 用户定），不再是「缺少会议参数」错误
    aggMode.value = true
    loadAll()
    return
  }
  load()
})
</script>

<style scoped>
:deep(.page-nav) { background: #2f5f9e; }  /* 会议模块页头（规范三色制） */
.todos-page { min-height: 100vh; background: #f4f5f7; padding: 24rpx 24rpx 100rpx; box-sizing: border-box; }

/* 人工确认清单（0724）：AI 建议 → 主任编辑/删除/增补后固化。留下即保留、删除即弃用（去掉重复的「保留」勾选） */
.review-intro { background: #fff; border-radius: 18rpx; padding: 26rpx 28rpx; margin-bottom: 22rpx; box-shadow: 0 4rpx 14rpx rgba(20,42,58,0.05); }
.review-title { font-size: 36rpx; font-weight: 800; color: #1f2329; }
.review-sub { margin-top: 12rpx; font-size: 27rpx; color: #667B88; line-height: 1.55; }
.review-sub b { color: #C77800; }
.review-card { position: relative; background: #fff; border-radius: 18rpx; padding: 26rpx 24rpx 24rpx; margin-bottom: 18rpx; box-shadow: 0 4rpx 14rpx rgba(20,42,58,0.05); }
/* 序号角标：左上角小圆，给卡片一点条理感 */
.review-idx { position: absolute; top: 22rpx; left: 24rpx; width: 40rpx; height: 40rpx; border-radius: 50%; background: #EEF2F6; color: #526774; font-size: 24rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; }
/* 删除：右上角圆形 ×，替代「保留/删除」的重复设计 */
.review-del { position: absolute; top: 16rpx; right: 16rpx; width: 52rpx; height: 52rpx; border: 0; border-radius: 50%; background: #F4F6F8; color: #98A2AE; font-size: 40rpx; line-height: 1; display: flex; align-items: center; justify-content: center; padding: 0; }
.review-del:active { background: #FBE6E2; color: #B0463A; }
.review-input { width: 100%; box-sizing: border-box; border: 2rpx solid #E5E9ED; border-radius: 14rpx; padding: 18rpx 18rpx; font-size: 29rpx; color: #24364B; background: #FAFBFC; }
.review-input.title { margin-top: 48rpx; font-weight: 600; resize: none; line-height: 1.45; }
.review-row2 { display: flex; gap: 14rpx; margin-top: 14rpx; }
.review-row2 .review-input { flex: 1; min-width: 0; }
.review-select { height: 84rpx; padding: 0 18rpx; }
.review-select.is-placeholder { color: #9AA3AD; }
/* 底部两按钮：各 70% 宽居中；手动添加升为清晰的描边色块（不再是若隐若现的虚线） */
.review-actions { display: flex; flex-direction: column; align-items: center; gap: 18rpx; margin-top: 26rpx; }
.review-add { width: 70%; min-height: 88rpx; border: 2rpx solid var(--c-primary); border-radius: 16rpx; background: #FFF6EC; color: #C2410C; font-size: 30rpx; font-weight: 700; }
.review-add:active { background: #FDEBD8; }
.review-confirm { width: 70%; min-height: 92rpx; border: 0; border-radius: 16rpx; background: var(--c-primary-dark); color: #fff; font-size: 32rpx; font-weight: 700; box-shadow: 0 8rpx 20rpx rgba(168,88,0,0.22); }
.review-confirm:disabled { opacity: 0.6; box-shadow: none; }
.review-confirm:active { opacity: 0.9; }
/* 手动添加待办（0729）：入口按钮 + 展开的输入表单，与确认清单同款视觉 */
.manual-zone { margin-top: 22rpx; }
.manual-add-btn { display: block; width: 70%; margin: 0 auto; min-height: 92rpx; border: 0; border-radius: 16rpx; background: var(--c-primary-dark); color: #fff; font-size: 30rpx; font-weight: 700; }
.manual-add-btn:active { background: var(--c-primary-strong); }
.manual-form { background: #fff; border-radius: 18rpx; padding: 26rpx 24rpx; box-shadow: 0 4rpx 14rpx rgba(20,42,58,0.05); }
.manual-form-title { font-size: 32rpx; font-weight: 800; color: #1f2329; margin-bottom: 18rpx; }
.manual-input { width: 100%; box-sizing: border-box; border: 2rpx solid #E5E9ED; border-radius: 14rpx; padding: 18rpx; font-size: 29rpx; color: #24364B; background: #FAFBFC; }
.manual-input.title { resize: none; line-height: 1.45; font-weight: 600; }
.manual-select { margin-top: 14rpx; height: 88rpx; padding: 0 18rpx; }
.manual-select.is-placeholder { color: #9AA3AD; }
.manual-form-acts { display: flex; gap: 16rpx; margin-top: 22rpx; }
.manual-cancel { flex: 1; min-height: 84rpx; border: 2rpx solid #D8DBE0; border-radius: 14rpx; background: #fff; color: #55585E; font-size: 30rpx; font-weight: 600; }
.manual-save { flex: 1.4; min-height: 84rpx; border: 0; border-radius: 14rpx; background: var(--c-primary-dark); color: #fff; font-size: 30rpx; font-weight: 700; }
.manual-save:disabled { opacity: 0.55; }
.todo-card { background: #fff; border-radius: 16px; padding: 22px 20px; margin-bottom: 18px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.todo-top { display: flex; align-items: flex-start; position: relative; }
.todo-idx { flex: none; width: 34px; height: 34px; line-height: 34px; text-align: center; border-radius: 50%; background: #1A4A8A; color: #fff; font-size: 18px; font-weight: 700; margin-right: 14px; margin-top: 2px; }
.todo-title { flex: 1; font-size: 22px; font-weight: 700; color: #1a1a1a; line-height: 1.55; }
.todo-head-actions { flex:none; display:flex; align-items:center; gap:7px; margin-left:10px; position:relative; }
.status-tag { height:30px; line-height:30px; padding:0 12px; border-radius:15px; font-size:15px; font-weight:700; white-space:nowrap; }
/* 三级状态色（0730 设计师定，全 app 通用）：待处理=常态灰、处理中=进行中蓝、已完成=常态灰。
   只有逾期才给暖色胶囊——待办本身不带逾期态，故此处无暖色 */
.tag-todo { background:transparent; color:#6B7280; }
.tag-doing { background:transparent; color:#2F5F9E; }
.tag-done { background:transparent; color:#6B7280; }
.delete-btn { height:32px; padding:0 8px; border:0; background:transparent; color:#B42318; font-size:15px; font-weight:700; }
.delete-btn:disabled { opacity:.5; }
.edit-btn { height:32px; padding:0 8px; border:0; background:transparent; color:#2464B4; font-size:15px; font-weight:700; }
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
.completed-mark { margin-left:auto; min-width:120px; height:46px; line-height:46px; border-radius:12px; background:transparent; color:#6B7280; text-align:center; font-size:18px; font-weight:700; }

@media (max-width: 420px) {
  .todo-card { padding:18px 16px; }
  .todo-title { font-size:20px; }
  .status-tag { padding:0 10px; font-size:14px; }
  .todo-meta,.todo-trace,.todo-footer { margin-left:0; }
  .todo-footer { flex-wrap:wrap; }
  .self-handle-btn,.primary-btn { flex:1; min-width:130px; }
  .rollback-btn { flex:0 0 auto; }
}

/* ── 业委会整体待办（0731 设计师稿）──
   中性深灰页头（跨模块页不用三色，同档案馆）+ 三页签下划线式 + 只读白卡 + 档案馆已办行 */
.todos-page.agg-page { padding: 0 0 60rpx; }
.agg-hd { background: linear-gradient(160deg, #55606e 0%, #434d5a 100%); padding: calc(env(safe-area-inset-top) + 10rpx) 24rpx 22rpx 12rpx; }
.agg-hd-row { display: flex; align-items: center; }
.agg-back { width: 72rpx; height: 72rpx; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 60rpx; font-weight: 700; cursor: pointer; }
.agg-hd-title { color: #fff; font-size: 40rpx; font-weight: 700; }
.agg-hd-sub { margin: 4rpx 0 0 72rpx; color: rgba(255,255,255,.72); font-size: 27rpx; }
.agg-tabs { display: flex; background: #fff; border-bottom: 2rpx solid #E2E5EA; }
.agg-tab { flex: 1; text-align: center; padding: 26rpx 0 20rpx; font-size: 30rpx; font-weight: 600; color: #6B7280; border-bottom: 6rpx solid transparent; cursor: pointer; }
.agg-tab.active { color: #1F2937; font-weight: 700; border-bottom-color: #3F4A5E; }
.agg-body { padding: 24rpx; }
.agg-card { background: #fff; border-radius: 20rpx; padding: 26rpx 28rpx; margin-bottom: 20rpx; box-shadow: 0 2rpx 6rpx rgba(31,41,55,.04), 0 8rpx 20rpx rgba(31,41,55,.06); cursor: pointer; }
.agg-card:active { background: #F7F9FB; }
.agg-line1 { display: flex; align-items: baseline; justify-content: space-between; gap: 16rpx; }
.agg-title { flex: 1; min-width: 0; font-size: 33rpx; font-weight: 700; color: #1F2937; line-height: 1.4; }
/* 状态三级：待跟进=常态灰、处理中=蓝（跨模块页不随模块变色） */
.agg-status { flex-shrink: 0; font-size: 26rpx; white-space: nowrap; }
.agg-status.st-todo { color: #6B7280; font-weight: 500; }
.agg-status.st-doing { color: #2F5F9E; font-weight: 700; }
.agg-line2 { display: flex; gap: 26rpx; margin-top: 14rpx; }
.agg-date { flex-shrink: 0; font-size: 27rpx; color: #6B7280; }
.agg-srccol { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6rpx; font-size: 27rpx; color: #6B7280; }
.agg-srccol span { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
/* 档案馆已办行：轻列表（透明底+分隔线），「档案馆」深色、说明灰 */
.agg-arch { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; min-height: 96rpx; margin-top: 6rpx; border-top: 2rpx solid #E2E5EA; cursor: pointer; }
.agg-arch:active { opacity: .65; }
.agg-arch-text { font-size: 28rpx; font-weight: 500; color: #6B7280; }
.agg-arch-text b { font-weight: 700; color: #1F2937; }
.agg-arch-arr { flex-shrink: 0; display: inline-block; width: 14rpx; height: 14rpx; border-right: 3rpx solid #B4BCC7; border-bottom: 3rpx solid #B4BCC7; transform: rotate(-45deg); }

.access-card { background: #fff; border-radius: 24rpx; padding: 64rpx 36rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); text-align: center; }
.access-icon { width: 96rpx; height: 96rpx; border-radius: 50%; background: #E8F7EC; color: #2B9E55; display: flex; align-items: center; justify-content: center; margin: 0 auto 24rpx; font-size: 52rpx; font-weight: 700; }
.access-title { display: block; font-size: 38rpx; color: #1f2329; font-weight: 700; margin-bottom: 14rpx; }

.doc { background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; margin-top: 8rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.doc-body { display: block; font-size: 32rpx; color: #33373d; line-height: 1.9; white-space: pre-wrap; word-break: break-word; }

.empty-state { text-align: center; color: #666; font-size: 32rpx; padding: 80rpx 0; }
</style>
