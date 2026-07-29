<template>
  <div class="page docp-page">
    <PageNav :title="titleText" style="margin:-24rpx -24rpx 0;">
      <template #left><div class="docp-back" @click="goBack">‹</div></template>
      <template #right>
        <button class="nav-home" @click="goHome">首页</button>
      </template>
    </PageNav>

    <div v-if="loading" class="docp-state">正在整理{{ titleText }}，请稍候…</div>
    <div v-else-if="error" class="docp-state">
      <span class="docp-err">{{ error }}</span>
      <button class="docp-retry" @click="load">重试</button>
    </div>
    <template v-else>
      <!-- 页内公文排版预览（0723 用户定：不再嵌 PDF 查看器）；导出 PDF 与本页同一份内容装配 -->
      <div class="docp-scroll">
        <div class="docp-paper">
          <h1 class="docp-title">{{ docTitle }}</h1>
          <p v-for="(ln, i) in bodyLines" :key="i" class="docp-ln" :class="ln.cls">{{ ln.text }}</p>
        </div>
      </div>
      <div class="docp-foot">
        <span class="docp-tip">导出的 PDF 文件与本页内容一致</span>
        <button class="docp-export" :disabled="exporting" @click="download">{{ exporting ? '正在导出…' : '导出 PDF' }}</button>
      </div>
    </template>
  </div>
</template>

<script setup>
// 会议记录 / 会议纪要 独立查看页（0722 用户定：查看=进新页；0723 改版：页内直接排正文，
// 不再 iframe 嵌浏览器 PDF 查看器——那看起来还是"文件预览模式"，且手机浏览器多半直接触发下载）。
// 正文来源：记录=后端 meeting-record-text（与 PDF 同一份装配）；纪要=GET /minutes 原文按公文规则排版。
// kind=record|minutes；「导出 PDF」按需下载后端生成的正式文件。
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast } from '@/utils/ui'
import { navigateBack, goModuleHome } from '@/utils/navigate'
import PageNav from '@/components/PageNav.vue'

const route = useRoute()
const meetingId = parseInt(route.query.meetingId)
const kind = String(route.query.kind || 'record')

const titleText = computed(() => (kind === 'minutes' ? '会议纪要' : '会议记录'))
const loading = ref(true)
const error = ref('')
const exporting = ref(false)
const docTitle = ref('')
const bodyLines = ref([])

// 标题去重（与 Minutes.vue / 后端 generateMinutesPdf 同规则）：避免「××会议会议纪要」
function joinMinutesTitle(meetingTitle) {
  return String(meetingTitle || '').replace(/会议纪要$/, '').replace(/会议$/, '') + '会议纪要'
}

const HEAD_RE = /^[一二三四五六七八九十]+、/
// 记录表格版式（0723 仿工作手册）的分区标签行
const RECORD_HEAD_RE = /^(会议内容：|会议有关决定及表决结果|会议决定、决议公告的时间|出席成员名单及签章|附页：会议结果)/
// 落款/日期行：右对齐、不缩进。落款统一简化为「阳光花园业主委员会」（0729 用户定：不要区划/届别长名）
const DATE_RE = /^\d{4}\s*年\s*\d{1,2}\s*月\s*\d{1,2}\s*日$/
const ORG_RE = /^.{0,30}业主委员会(（第.{0,6}届）)?$/
const SIGN_ORG = '阳光花园业主委员会'

// 纪要正文 → 展示行：去 Markdown 记号、跳过开头重复标题、每段首行缩进两格（与导出 PDF 同规则）
function minutesLines(text, meetingTitle) {
  const full = joinMinutesTitle(meetingTitle)
  const out = []
  let bodyStarted = false
  let orgDone = false
  for (const raw of String(text).split(/\r?\n/)) {
    const t = raw.trim().replace(/^#{1,6}\s*/, '')
    if (!t) { if (bodyStarted) out.push({ text: '', cls: 'blank' }); continue }
    // 正文开头的标题行一律跳过（页顶已有标题）：含 AI 稿自带的「××业委会会议纪要」
    if (!bodyStarted && (t === '会议纪要' || t === meetingTitle || t === full || (t.length <= 30 && /会议纪要$/.test(t)))) continue
    bodyStarted = true
    if (DATE_RE.test(t)) { out.push({ text: t, cls: 'sign' }); continue }
    if (ORG_RE.test(t)) { // 落款单位：简化为短名，多处只留一处（去重旧稿里累积的长名/短名）
      if (!orgDone) { orgDone = true; out.push({ text: SIGN_ORG, cls: 'sign' }) }
      continue
    }
    out.push({ text: '　　' + t, cls: HEAD_RE.test(t) ? 'head' : '' })
  }
  return out
}

// 记录文本 → 展示行：第一行是居中的业委会行，分区标签行（会议内容/决定及表决结果等）加粗
function recordLines(lines) {
  return lines.map((raw, i) => {
    const t = String(raw).trimEnd()
    if (!t.trim()) return { text: '', cls: 'blank' }
    if (i === 0) return { text: t, cls: 'center' }
    return { text: t, cls: (RECORD_HEAD_RE.test(t.trim()) || HEAD_RE.test(t.trim())) ? 'head' : '' }
  })
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (kind === 'minutes') {
      const [detail, text] = await Promise.all([api.committeeDetail(meetingId), api.committeeMinutes(meetingId)])
      const t = String(text || '').trim()
      if (!t) throw new Error('还没有纪要正文，请先生成会议纪要')
      const meetingTitle = (detail && detail.title) || ''
      docTitle.value = joinMinutesTitle(meetingTitle)
      bodyLines.value = minutesLines(t, meetingTitle)
    } else {
      const text = String(await api.committeeMeetingRecordText(meetingId) || '').trim()
      if (!text) throw new Error('会议记录暂无内容')
      const lines = text.split(/\r?\n/)
      docTitle.value = (lines.shift() || '业主委员会会议记录').trim()
      bodyLines.value = recordLines(lines)
    }
  } catch (e) {
    error.value = (e && e.message) || titleText.value + '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function download() {
  if (exporting.value) return
  exporting.value = true
  try {
    const blob = kind === 'minutes'
      ? await api.committeeExportMinutesPdf(meetingId)
      : await api.committeeExportMeetingRecord(meetingId)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = titleText.value + '.pdf'
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => { try { URL.revokeObjectURL(url) } catch (e) {} }, 10000)
    toast({ title: titleText.value + '已导出', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '导出失败，请稍后重试', icon: 'none' })
  } finally {
    exporting.value = false
  }
}

function goBack() {
  // 历史优先(0725 导航审计):本页由会议详情或公示材料页进入,历史回退天然回来处;
  // 原先硬编码回详情,从公示页进来的会被跳过一层。软跳失败仍兜底硬跳详情。
  navigateBack()
  setTimeout(() => {
    if (document.querySelector('.docp-page') && meetingId) window.location.replace('/committee-detail?id=' + meetingId + '&from=doc-preview')
  }, 400)
}
function goHome() {
  goModuleHome('meeting')
}

onMounted(() => {
  if (!meetingId) { loading.value = false; error.value = '缺少会议参数'; return }
  load()
})
</script>

<style scoped>
.docp-page { display:flex; flex-direction:column; height:100vh; box-sizing:border-box; overflow:hidden; }
.docp-back { width:96rpx; height:124rpx; display:flex; align-items:center; justify-content:center; color:#fff; font-size:66rpx; font-weight:700; }
.docp-state { flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:26rpx; color:#7B818B; font-size:30rpx; padding:0 40rpx; text-align:center; line-height:1.7; }
.docp-err { color:#8A5A2B; }
.docp-retry { border:2rpx solid #D8DBE0; background:#fff; color:#55585E; font-size:27rpx; font-weight:600; border-radius:999rpx; padding:12rpx 44rpx; }
.docp-scroll { flex:1; overflow-y:auto; padding:20rpx 18rpx 30rpx; background:#F0F1F4; }
.docp-paper { background:#fff; border-radius:14rpx; box-shadow:0 6rpx 24rpx rgba(40,45,60,.08); padding:48rpx 36rpx 60rpx; }
.docp-title { text-align:center; font-size:38rpx; font-weight:800; color:#1E2430; margin:0 0 12rpx; line-height:1.5; }
/* overflow-wrap:anywhere（不用 word-break:break-all）：中文按字自然换行、长英文/数字(日期时间)只在放不下时才断，不再在「时/间」「2026-07-/31」字中间硬断 */
.docp-ln { margin:0; font-size:30rpx; line-height:1.9; color:#333A45; white-space:pre-wrap; overflow-wrap:anywhere; }
.docp-ln.center { text-align:center; color:#555C68; font-size:27rpx; }
.docp-ln.head { font-weight:700; color:#1E2430; margin-top:18rpx; }
.docp-ln.sign { text-align:right; padding-right:30rpx; margin-top:6rpx; }
.docp-ln.blank { height:20rpx; }
.docp-foot { display:flex; align-items:center; gap:16rpx; padding:14rpx 20rpx calc(14rpx + env(safe-area-inset-bottom)); background:#fff; border-top:2rpx solid #EEF0F3; }
.docp-tip { flex:1; font-size:25rpx; color:#8A9099; }
.docp-export { flex-shrink:0; border:0; border-radius:999rpx; background:#2F6FB2; color:#fff; font-size:30rpx; font-weight:600; padding:18rpx 48rpx; }
.docp-export:active { background:#265C96; }
.docp-export:disabled { opacity:.6; }
</style>
