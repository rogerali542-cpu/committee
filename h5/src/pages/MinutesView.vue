<template>
  <div class="mv-page detail-minutes-view">
    <PageNav title="会议纪要" style="margin:-24rpx -24rpx 0;">
      <template #left><div class="nav-back-btn" @click="backFromView">‹</div></template>
      <template #right><button class="nav-home-btn" @click="goModuleHome('meeting')">首页</button></template>
    </PageNav>

    <div v-if="loading" class="doc mv-loading">正在加载会议纪要…</div>

    <!-- 服务端任务生成中：不显示"暂无"误导，轮询到正文出现自动切换 -->
    <div v-else-if="generating" class="mv-empty">
      <div class="mv-empty-ico">🤖</div>
      <div class="mv-empty-title">会议纪要生成中</div>
      <div class="mv-empty-sub">AI 正在整理会议内容，完成后将自动显示，请稍候…</div>
    </div>

    <!-- 正文格式与老纪要页一致；未公示、未归档时可在当前页直接编辑 -->
    <div v-else-if="text" class="doc">
      <div class="minutes-letterhead">
        <span v-if="editing" :ref="bindMeetingNameEditor" class="minutes-meeting-name editable" contenteditable="true" @input="onMeetingNameInput"></span>
        <span v-else class="minutes-meeting-name">{{ documentParts.meetingName }}</span>
      </div>
      <div v-if="editing" :ref="bindBodyEditor" class="doc-body editable" contenteditable="true" @input="onBodyInput"></div>
      <div v-else class="doc-body"><p v-for="(ln, i) in bodyLines" :key="i" class="doc-ln" :class="ln.cls">{{ ln.text }}</p></div>
      <div v-if="!bodyHasSignoff" class="mv-signature">阳光花园业主委员会</div>
      <div v-if="editing" class="mv-editor-actions">
        <button class="mv-cancel" :disabled="saving" @click="cancelEdit">取消</button>
        <button class="mv-save" :disabled="saving" @click="saveEdit">{{ saving ? '保存中…' : '确定' }}</button>
      </div>
      <div v-else-if="canEdit" class="mv-view-actions">
        <button class="mv-edit-btn" @click="beginInlineEdit">编辑纪要</button>
        <div class="mv-links">
          <span class="mv-link" @click="copyAll">复制全文</span>
          <span class="mv-link" @click="viewTodos">待办事项</span>
        </div>
      </div>
      <div v-else class="mv-links">
        <span class="mv-link" @click="copyAll">复制全文</span>
        <span class="mv-link" @click="viewTodos">待办事项</span>
      </div>
    </div>

    <div v-else class="mv-empty">
      <div class="mv-empty-ico">📄</div>
      <div class="mv-empty-title">暂无会议纪要</div>
      <div class="mv-empty-sub">本次会议还没有生成会议纪要，可在会议录音页生成后再来查看。</div>
    </div>
  </div>
</template>

<script setup>
// 会议详情专用的会议纪要页：查看与编辑合并，避免再跳回旧纪要编辑页。
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import perm from '@/utils/perm'
import { toast } from '@/utils/ui'
import { navigateTo, redirectTo, navigateBack, goModuleHome } from '@/utils/navigate'
import PageNav from '@/components/PageNav.vue'

const route = useRoute()
const loading = ref(true)
const text = ref('')
const canEdit = ref(false)
const editing = ref(false)
const editableMeetingName = ref('')
const editableBody = ref('')
const meetingNameEditor = ref(null)
const bodyEditor = ref(null)
const saving = ref(false)
let meetingId = null
const COMMUNITY_NAME = '阳光花园'
const SIGNATURE = COMMUNITY_NAME + '业主委员会'

// 旧纪要中的连续问号来自小区名称尚未带入时的占位乱码；本项目小区名称已确定为“阳光花园”。
// AI 正文若自带独立落款行，也在这里移除，交由页面底部统一展示一次。
function normalizeMinutesText(value) {
  return String(value || '')
    .replace(/[?？]{2,}/g, COMMUNITY_NAME)
    .replace(/^\s*阳光花园业主委员会\s*$/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trimEnd()
}

// 去掉 AI 纪要里的 Markdown 标题标记（行首 #），纯文本更干净；首行作标题，其余作正文
const pretty = computed(() => normalizeMinutesText(text.value)
  .replace(/^[ \t]*#{1,6}[ \t]*/gm, '')
  // 纯文本展示：去掉偶尔漏进来的 HTML 标签（如旧纪要里的 <center>），否则会当字面量显示出来
  .replace(/<\/?center>/gi, '')
  .replace(/^[^\r\n]*[（(]草稿[）)][ \t]*\r?\n+/, '')
  // AI 偶尔把“某某业主委员会”单独作为首行署名；展示时去掉，下一行纪要标题自动顶上。
  .replace(/^[^\r\n]*业主委员会[ \t]*\r?\n+/, '')
  .replace(/(^|\n)一、会议基本情况[ \t]*\r?\n/, '$1')
  .replace(/(^|\n)二、议题审议情况/g, '$1一、议题审议情况')
  .replace(/(^|\n)三、会议结论与后续安排/g, '$1二、会议结论与后续安排'))
const documentParts = computed(() => {
  const lines = pretty.value.split('\n')
  const marker = lines.findIndex(line => line.trim() === '会议纪要')
  if (marker >= 0) {
    const meetingName = lines.slice(0, marker).filter(line => line.trim()).join('\n').trim()
    // 期号行（第N期）已从公文格式中移除：老纪要里若存有该行，按普通正文首行显示，编辑时可自行删除
    return { meetingName: (meetingName || '业委会') + '会议纪要', body: lines.slice(marker + 1).join('\n').replace(/^\s*\n/, '') }
  }
  return { meetingName: (lines[0] || '').trim(), body: lines.slice(1).join('\n').replace(/^\s*\n/, '') }
})
function withSignature(value) {
  const content = normalizeMinutesText(value)
  return content + '\n\n' + SIGNATURE
}
// 正文按公文格式排版（仅影响展示，编辑/复制仍用原文）：
// 普通段落首行缩进两字；落款统一简化为「阳光花园业主委员会」（去掉区划/届别长名、多处去重）+ 日期，右对齐右缩进两字。
const DATE_RE = /^\d{4}\s*年\s*\d{1,2}\s*月\s*\d{1,2}\s*日$/
const ORG_RE = /^.{0,30}业主委员会(（第.{0,6}届）)?$/
const SIGN_ORG = '阳光花园业主委员会'
const HEAD_RE = /^[一二三四五六七八九十]+、/
const bodyLines = computed(() => {
  const out = []
  let orgDone = false
  for (const raw of documentParts.value.body.split('\n')) {
    const t = raw.trim()
    if (!t) { out.push({ text: '', cls: 'blank' }); continue }
    if (DATE_RE.test(t)) { out.push({ text: t, cls: 'sign' }); continue }                 // 日期：右对齐
    if (ORG_RE.test(t)) { if (!orgDone) { orgDone = true; out.push({ text: SIGN_ORG, cls: 'sign' }) } continue } // 落款：简化+去重
    out.push({ text: '　　' + t, cls: HEAD_RE.test(t) ? 'head' : '' })                     // 普通段落：首行缩进两字
  }
  return out
})
// 正文里已含落款（AI 长名已简化到位）时，底部不再重复署名
const bodyHasSignoff = computed(() => bodyLines.value.some(l => l.cls === 'sign' && l.text === SIGN_ORG))

function beginInlineEdit() {
  editableMeetingName.value = documentParts.value.meetingName
  editableBody.value = normalizeMinutesText(documentParts.value.body)
  editing.value = true
  // innerText 由下面两个函数式 ref 在元素挂载瞬间写入——不依赖 nextTick 时序，避开
  // 「editing 与 loading 同批次刷新时 ref 还没挂上、赋值被静默跳过」这类竞态。
}

// 函数式 ref：元素插入时 Vue 用真实 DOM 回调，此刻一次性灌入初始正文；
// dataset.inited 保证只灌一次，后续重渲染不会覆盖用户已输入的内容。
function bindMeetingNameEditor(el) {
  meetingNameEditor.value = el
  if (el && !el.dataset.inited) { el.innerText = editableMeetingName.value; el.dataset.inited = '1' }
}

function bindBodyEditor(el) {
  bodyEditor.value = el
  if (el && !el.dataset.inited) { el.innerText = editableBody.value; el.dataset.inited = '1' }
}

function onMeetingNameInput(event) {
  editableMeetingName.value = event.currentTarget.innerText
}

function onBodyInput(event) {
  editableBody.value = event.currentTarget.innerText
}

async function load() {
  loading.value = true
  try {
    const [txt, detail] = await Promise.all([
      api.committeeMinutes(meetingId),
      api.committeeDetail(meetingId)
    ])
    text.value = txt && String(txt).trim() ? txt : ''
    const published = !!(detail && detail.publish && detail.publish.published)
    const archived = !!(detail && detail._archived)
    canEdit.value = (perm.isChair() || perm.can('committee.publish')) && !published && !archived
    // 默认进"查看"（公文格式：首行缩进、落款/日期右对齐）——可编辑者点「编辑纪要」再进编辑态。
    // 原来 canEdit 直接进编辑态(contenteditable 显示原文)，看不到排版，用户反馈"格式没变"。
  } catch (e) {
    text.value = ''
    canEdit.value = false
  } finally {
    loading.value = false
  }
  // 正文还没有（包括 /minutes 因无纪要而报错的情况）：可能服务端任务正在生成 → 显示"生成中"并轮询接续
  if (!text.value) checkGenerating()
}

// ── 服务端纪要任务生成中：显示"生成中"占位并轮询，正文落库后自动切换成正式纪要 ──
const generating = ref(false)
let _genPollTimer = null
async function checkGenerating() {
  if (typeof api.committeeMinutesStatus !== 'function') return
  let st = null
  try { st = await api.committeeMinutesStatus(meetingId) } catch (e) { return }
  const s = st && st.status
  if (s !== 'running' && s !== 'success') return
  generating.value = true
  const deadline = Date.now() + 5 * 60 * 1000
  clearInterval(_genPollTimer)
  _genPollTimer = setInterval(async () => {
    try {
      const t = await api.committeeMinutes(meetingId)
      if (t && String(t).trim()) {
        clearInterval(_genPollTimer)
        generating.value = false
        await load() // 复用正常加载：正文/编辑态一次到位
        return
      }
    } catch (e) { /* 单次失败继续轮询 */ }
    if (Date.now() > deadline) { clearInterval(_genPollTimer); generating.value = false } // 超时回落"暂无"，用户可回录音页重试
  }, 4000)
}
onUnmounted(() => clearInterval(_genPollTimer))

function cancelEdit() {
  // 编辑值尚未写入 text，退出编辑态即可恢复到上一次“确定”保存的内容。
  editableMeetingName.value = documentParts.value.meetingName
  editableBody.value = normalizeMinutesText(documentParts.value.body)
  editing.value = false
}

function backToDetail() {
  const q = 'id=' + meetingId + '&from=minutes'
  redirectTo('/pages/committee-detail/committee-detail?' + q)
  setTimeout(() => {
    if (document.querySelector('.detail-minutes-view')) window.location.replace('/committee-detail?' + q)
  }, 300)
}
// 导航新规(0725 用户定):返回=历史上一页(详情/进行页进来的都天然回来处);
// from=committee(首页查看进来)确定性回业委会首页——真机残留历史会让历史回退窜到接待首页(用户报的 bug)
function backFromView() {
  const params = new URLSearchParams(window.location.search)
  const from = params.get('from')
  if (from === 'committee') {
    redirectTo('/main?home=tabs')
    setTimeout(() => { if (document.querySelector('.detail-minutes-view')) window.location.replace('/main?home=tabs') }, 300)
    return
  }
  // 会后整理页进来的：确定性回会后整理页（redirect 链路下历史盲退会窜到驾驶舱/首页，0729 BUG1 同款）
  const mid = params.get('meetingId')
  if (from === 'meeting-live-quick' && mid) {
    const q = 'meetingId=' + mid
    redirectTo('/pages/meeting-live-quick/meeting-live-quick?' + q)
    setTimeout(() => { if (document.querySelector('.detail-minutes-view')) window.location.replace('/meeting-live-quick?' + q) }, 300)
    return
  }
  navigateBack()
}

async function saveEdit() {
  if (!editableBody.value.trim()) { toast({ title: '纪要内容不能为空', icon: 'none' }); return }
  const rawTitle = editableMeetingName.value.trim() || '阳光花园业委会会议纪要'
  const header = rawTitle.endsWith('会议纪要') ? rawTitle : rawTitle + '会议纪要'
  const value = withSignature(header + '\n\n' + editableBody.value.trim())
  saving.value = true
  try {
    await api.committeeUpdateMinutes(meetingId, value)
    text.value = value
    editing.value = false
    toast({ title: '纪要已保存', icon: 'success' })
    // 保存成功即完成本页任务：固定回本会议详情页（历史回退不可靠——栈里常残留已结束会议的进行页，会退到签到步）
    setTimeout(() => backToDetail(), 500)
  } catch (e) {
    toast({ title: (e && e.message) || '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

async function copyAll() {
  try {
    await navigator.clipboard.writeText(withSignature(text.value))
    toast({ title: '已复制' })
  } catch (e) {
    toast({ title: '复制失败', icon: 'none' })
  }
}

// 待办事项独立页；软路由偶发不切换 → 硬导航兜底
function viewTodos() {
  const q = 'meetingId=' + meetingId
  navigateTo('/pages/minutes-todos/minutes-todos?' + q)
  setTimeout(() => {
    if (document.querySelector('.detail-minutes-view')) window.location.href = '/minutes-todos?' + q
  }, 300)
}

onMounted(() => {
  meetingId = parseInt(route.query.meetingId)
  if (meetingId) load()
  else loading.value = false
})
</script>

<style scoped>
/* 顶栏统一为纯深橙（覆盖 PageNav 默认渐变） */
:deep(.page-nav) { background: var(--c-primary-dark); }
.mv-page { min-height: 100vh; background: #f4f5f7; padding: 24rpx 24rpx calc(40rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
/* 正文格式与老纪要页保持一致 */
.doc { background: #fff; border-radius: 24rpx; padding: 36rpx 32rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.mv-loading { color: #888; text-align: center; font-size: 30rpx; }
.minutes-letterhead { display:flex; flex-direction:column; align-items:center; text-align:center; padding:18rpx 10rpx 34rpx; }
.minutes-meeting-name { font-size:42rpx; font-weight:700; color:#161616; line-height:1.45; white-space:pre-wrap; }
.doc-body { display: block; font-size: 34rpx; color: #33373d; line-height: 1.9; white-space: pre-wrap; padding: 24rpx 0; }
/* 查看态逐段排版：普通段落首行缩进（正文里已前置两个全角空格）；落款/日期右对齐、右缩进两字 */
.doc-ln { margin: 0; white-space: pre-wrap; }
.doc-ln.head { font-weight: 700; color: #1E2430; margin-top: 12rpx; }
.doc-ln.sign { text-align: right; padding-right: 2em; }
.doc-ln.blank { height: 20rpx; }
.editable { outline: none; border-radius: 8rpx; transition: background .15s; }
.editable:focus { background: #fffaf2; box-shadow: 0 0 0 2rpx rgba(198,106,0,.18); }
.minutes-meeting-name.editable { min-width: 60%; }
.doc-body.editable { min-height: 52vh; }
/* 复制/待办：在老页小链接基础上加大，方便点（老页 28rpx → 34rpx，加内边距 + 分隔线） */
.mv-links { display: flex; flex-wrap: wrap; justify-content: center; gap: 12rpx 34rpx; margin-top: 12rpx; padding-top: 18rpx; border-top: 2rpx solid #f0f0f0; }
.mv-link { font-size: 26rpx; font-weight: 400; color: #858b92; padding: 10rpx 16rpx; }
.mv-link:active { opacity: 0.6; }
.mv-signature { margin-top: 36rpx; text-align: right; padding-right: 2em; font-size: 32rpx; line-height: 1.8; color: #33373d; }
.mv-editor-actions { display: flex; gap: 20rpx; margin-top: 34rpx; padding-top: 22rpx; border-top: 2rpx solid #f0f0f0; }
.mv-editor-actions.single { justify-content: center; }
.mv-editor-actions.single button { flex: 0 0 60%; }
.mv-editor-actions button { flex: 1; height: 84rpx; border-radius: 42rpx; font-size: 30rpx; border: 0; }
.mv-cancel { background: #f1f2f4; color: #555; }
.mv-save { background: var(--c-primary-dark); color: #fff; }
.mv-editor-actions button:disabled { opacity: .55; }
/* 查看态（可编辑者）：编辑纪要主按钮 + 复制/待办链接 */
.mv-view-actions { margin-top: 34rpx; padding-top: 22rpx; border-top: 2rpx solid #f0f0f0; display: flex; flex-direction: column; align-items: center; gap: 12rpx; }
.mv-edit-btn { width: 60%; height: 84rpx; border-radius: 42rpx; border: 0; background: var(--c-primary-dark); color: #fff; font-size: 30rpx; font-weight: 700; }
.mv-edit-btn:active { background: var(--c-primary-strong, #8f4a06); }
.mv-view-actions .mv-links { margin-top: 0; padding-top: 6rpx; border-top: 0; }
/* 无纪要时的空状态 */
.mv-empty { background: #fff; border-radius: 24rpx; padding: 80rpx 40rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); text-align: center; }
.mv-empty-ico { font-size: 72rpx; margin-bottom: 16rpx; }
.mv-empty-title { font-size: 34rpx; font-weight: 700; color: #1a1a1a; margin-bottom: 12rpx; }
.mv-empty-sub { font-size: 28rpx; color: #888; line-height: 1.7; }
/* 自定义顶栏返回箭头:与 PageNav 默认样式一致(插槽替换后默认样式不生效) */
.nav-back-btn { width: 96rpx; height: 124rpx; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 66rpx; font-weight: 700; }
.nav-home-btn { display: inline-flex; align-items: center; height: 64rpx; margin-right: 20rpx; padding: 0 24rpx; border: 2rpx solid rgba(255,255,255,0.6); border-radius: 34rpx; background: rgba(255,255,255,0.12); color: #fff; font-size: 30rpx; font-weight: 600; line-height: 1; }
.nav-home-btn:active { background: rgba(255,255,255,0.28); }
</style>
