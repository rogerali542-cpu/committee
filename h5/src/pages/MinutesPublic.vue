<template>
  <div class="pub-page minutes-public-page">
    <PublishNav title="事项公示材料" :on-back="backToDetail" />

    <div class="pub-wrap">
      <div v-if="loading" class="pub-loading">正在整理事项公示材料…</div>

      <div v-else-if="!detail" class="pub-card pub-empty">
        <div class="empty-icon">!</div>
        <span class="empty-title">暂无法查看会议公示</span>
        <span class="empty-text">{{ emptyText }}</span>
      </div>

      <template v-else>
        <section class="pub-card notice-paper">
          <span class="pub-tag">事项公示</span>
          <span class="pub-badge" :class="badgeClass">{{ badgeText }}</span>
          <h1 class="notice-title">{{ publicTitle }}</h1>
          <div class="notice-content-body">{{ publicContent }}</div>
        </section>

        <section class="pub-card">
          <header class="section-head">
            <span class="section-index">附件一</span>
            <div><h2>会议纪要</h2><p>与本次事项公示相关的会议决定摘要</p></div>
          </header>
          <div v-if="minutesText" class="minutes-body">{{ minutesText }}</div>
          <div v-else class="section-empty">正式会议纪要尚未生成</div>
        </section>

        <section class="pub-card" v-if="relatedMaterials.length">
          <header class="section-head">
            <span class="section-index">附件二</span>
            <div><h2>相关材料与公示留痕</h2><p>会议附件及公示照片</p></div>
          </header>
          <div class="material-list">
            <a v-for="item in relatedMaterials" :key="item.id || item.url || item.fileName" class="material-row" :href="item.url || item.fileUrl" target="_blank">
              <span class="material-icon">附件</span>
              <span class="material-name">{{ item.fileName || item.name || '相关材料' }}</span>
              <span class="material-open">查看 ›</span>
            </a>
          </div>
        </section>

        <section class="pub-card feedback-card">
          <header class="section-head">
            <span class="section-index">反馈</span>
            <div><h2>意见反馈</h2><p>对公示事项提出意见或建议</p></div>
          </header>
          <div class="feedback-copy">
            <b>请通过业主接待渠道提交书面意见</b>
            <span>意见将作为本事项后续处理和归档的组成材料。</span>
          </div>
        </section>

        <div class="public-note">
          <b>公示说明</b>
          <span>本页公开事项公示、会议纪要及相关附件。签到明细、个人意见、完整投票明细、录音转写和内部待办仅作内部归档。</span>
        </div>
        <button class="pub-btn" @click="copyText">复制公示正文</button>
        <span class="pub-foot">本页为面向本小区业主发布的事项公示材料</span>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast } from '@/utils/ui'
import { redirectTo, navigateBack } from '@/utils/navigate'
import PublishNav from '@/components/PublishNav.vue'

const route = useRoute()

// 返回明确指回会议详情（本页常经硬跳兜底进入，历史栈不可靠）；无 meetingId 时退回历史回退
function backToDetail() {
  if (!meetingId) { navigateBack(); return }
  redirectTo('/pages/committee-detail/committee-detail?id=' + meetingId + '&from=minutes-public')
  setTimeout(() => {
    if (document.querySelector('.minutes-public-page')) window.location.replace('/committee-detail?id=' + meetingId + '&from=minutes-public')
  }, 400)
}
const loading = ref(true)
const detail = ref(null)
const minutesText = ref('')
const opinions = ref([])
const todos = ref([])
const emptyText = ref('')
let meetingId = null

const record = computed(() => (detail.value && detail.value.record) || {})
const topics = computed(() => (record.value.topics || []).map(topic => ({
  ...topic,
  type: topic.voteRequired ? 'vote' : (topic.type === 'discussion' ? 'discussion' : 'notice')
})))
const recordChecks = computed(() => record.value.checks || [])
const attendance = computed(() => {
  const list = record.value.attendances || []
  const total = list.length
  const present = list.filter(item => item.signedIn).length
  return { total, present, valid: total > 0 && present >= Math.floor(total / 2) + 1 }
})
const isDraft = computed(() => !detail.value || detail.value.stage !== 'ended')
const isPublished = computed(() => !!(detail.value && detail.value.publish && detail.value.publish.published))
const badgeText = computed(() => isDraft.value ? '草稿' : isPublished.value ? '已公示' : '待公示')
const badgeClass = computed(() => isDraft.value ? 'is-draft' : isPublished.value ? 'is-pub' : 'is-wait')
const meetingTimeText = computed(() => {
  if (!detail.value) return '未记录'
  return [detail.value.meetingDate, detail.value.meetingTime].filter(Boolean).join(' ') || '未记录'
})
const publicTitle = computed(() => {
  const saved = detail.value && detail.value.publish && detail.value.publish.publicTitle
  if (saved) return saved
  const subject = topics.value.length === 1 ? topics.value[0].title : (detail.value && detail.value.title)
  const clean = String(subject || '本次会议有关事项').replace(/^关于/, '').replace(/(的)?(会议|议题)$/, '')
  return '关于' + clean + '的公示'
})
const publicContent = computed(() => {
  const saved = detail.value && detail.value.publish && detail.value.publish.publicContent
  if (saved) return saved
  const lines = ['根据相关规定，经阳光花园业主委员会会议研究，现将有关事项公示如下：', '']
  topics.value.forEach((topic, index) => {
    const result = topic.type === 'notice' ? '有关情况已在会议中通报'
      : topic.type === 'discussion' ? '有关意见已在会议中讨论并记录'
        : (topic.text || resultText(topic))
    lines.push(`${index + 1}. ${topic.title}：${result}。`)
  })
  lines.push('', '相关会议纪要及附件一并公示。如有意见或建议，请通过业主接待渠道以书面形式反馈。', '', '阳光花园业主委员会')
  if (detail.value && detail.value.publish && detail.value.publish.publishDate) lines.push(detail.value.publish.publishDate)
  return lines.join('\n')
})
const relatedMaterials = computed(() => {
  if (!detail.value) return []
  return [...(detail.value.materials || []), ...(detail.value.archiveExtras || [])]
    .filter(item => item && (item.url || item.fileUrl))
})
const processSteps = computed(() => [
  { label: '会议通知', done: !!detail.value.notifiedAt },
  { label: '委员签到', done: attendance.value.present > 0 },
  { label: '议题审议', done: topics.value.length > 0 },
  { label: '形成记录', done: !!minutesText.value },
  { label: '会议公示', done: isPublished.value }
])

function topicTypeText(type) {
  // 0717 用户定：通知并入讨论，非表决类统一「通知和讨论」
  return type === 'vote' ? '表决' : '通知和讨论'
}
function opinionsFor(topicId) {
  return opinions.value.filter(item => Number(item.topicId) === Number(topicId) && item.content)
}
function notVoted(topic) {
  return Math.max(0, Number(topic.total || 0) - Number(topic.voted || 0))
}
function resultText(topic) {
  if (topic.status === 'passed') return '表决通过'
  if (topic.status === 'failed') return '表决未通过'
  return '表决结果待确认'
}
function todoStatusText(status) {
  return status === 'done' ? '已完成' : status === 'doing' ? '处理中' : '待处理'
}

async function load() {
  loading.value = true
  try {
    detail.value = await api.committeeDetail(meetingId)
    const [minutesResult] = await Promise.allSettled([api.committeeMinutes(meetingId)])
    if (minutesResult.status === 'fulfilled') minutesText.value = String(minutesResult.value || '').trim()
  } catch (e) {
    detail.value = null
    emptyText.value = (e && e.message) || '事项公示材料暂不可用'
  } finally {
    loading.value = false
  }
}

async function copyText() {
  const lines = [publicTitle.value, publicContent.value]
  if (minutesText.value) lines.push('附件：会议纪要\n' + minutesText.value)
  try {
    await navigator.clipboard.writeText(lines.filter(Boolean).join('\n\n'))
    toast({ title: '公示正文已复制' })
  } catch (e) { toast({ title: '复制失败', icon: 'none' }) }
}

onMounted(() => {
  meetingId = parseInt(route.query.meetingId)
  if (meetingId) load()
  else { loading.value = false; emptyText.value = '缺少会议参数' }
})
</script>

<style scoped>
.pub-loading { text-align:center; color:var(--pub-sub); font-size:32rpx; padding:120rpx 0; }
.pub-empty { text-align:center; padding:72rpx 36rpx; }
.empty-icon { width:96rpx; height:96rpx; border-radius:50%; background:var(--pub-blue-soft); color:var(--pub-blue); display:flex; align-items:center; justify-content:center; margin:0 auto 24rpx; font-size:52rpx; font-weight:800; }
.empty-title { display:block; font-size:38rpx; color:var(--pub-ink); font-weight:700; margin-bottom:14rpx; }
.empty-text { display:block; font-size:30rpx; color:var(--pub-sub); line-height:1.7; }
.hero-card { overflow:hidden; }
.notice-paper { position:relative; padding-top:44rpx; }
.notice-title { margin:44rpx auto 40rpx; max-width:92%; text-align:center; color:#17191d; font-size:42rpx; line-height:1.5; font-weight:800; }
.notice-content-body { padding:12rpx 8rpx 26rpx; white-space:pre-wrap; color:#30343a; font-size:32rpx; line-height:2; text-align:justify; }
.material-list { display:flex; flex-direction:column; gap:14rpx; }
.material-row { display:flex; align-items:center; gap:18rpx; padding:22rpx; border:2rpx solid #e8ebef; border-radius:16rpx; color:inherit; text-decoration:none; background:#fafbfc; }
.material-icon { flex-shrink:0; padding:7rpx 10rpx; border-radius:8rpx; background:var(--pub-blue-soft); color:var(--pub-blue); font-size:22rpx; font-weight:700; }
.material-name { flex:1; min-width:0; color:var(--pub-ink); font-size:29rpx; font-weight:600; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.material-open { flex-shrink:0; color:var(--pub-blue); font-size:25rpx; }
.feedback-copy { display:flex; flex-direction:column; gap:10rpx; padding:20rpx 22rpx; border-radius:14rpx; background:#f5f8fc; }
.feedback-copy b { color:var(--pub-ink); font-size:30rpx; }
.feedback-copy span { color:var(--pub-sub); font-size:27rpx; line-height:1.65; }
.pub-title { padding-right:110rpx; }
.pub-meta-grid { display:grid; grid-template-columns:1fr 1fr; gap:18rpx; margin-top:24rpx; }
.pub-meta-grid div { background:#f7f9fc; border-radius:14rpx; padding:18rpx; }
.pub-meta-grid span,.pub-meta-grid b { display:block; }
.pub-meta-grid span { color:var(--pub-sub); font-size:25rpx; margin-bottom:6rpx; }
.pub-meta-grid b { color:var(--pub-text); font-size:29rpx; line-height:1.45; }
.validity { margin-top:22rpx; padding:18rpx 22rpx; border-radius:14rpx; font-size:28rpx; font-weight:600; }
.validity.ok { background:var(--pub-green-soft); color:var(--pub-green); }
.validity.warn { background:var(--pub-amber-soft); color:var(--pub-amber); }
.section-head { display:flex; align-items:center; gap:18rpx; margin-bottom:28rpx; }
.section-index { color:var(--pub-blue); font-size:26rpx; font-weight:800; border-right:3rpx solid var(--pub-blue-line); padding-right:16rpx; }
.section-head h2 { margin:0; color:var(--pub-ink); font-size:36rpx; }
.section-head p { margin:5rpx 0 0; color:var(--pub-sub); font-size:25rpx; }
.process-line { display:flex; justify-content:space-between; position:relative; margin:28rpx 0; }
.process-line::before { content:''; position:absolute; left:8%; right:8%; top:23rpx; height:3rpx; background:var(--pub-blue-line); }
.process-step { z-index:1; width:20%; text-align:center; color:var(--pub-sub); font-size:23rpx; }
.process-step i { display:flex; width:46rpx; height:46rpx; align-items:center; justify-content:center; margin:0 auto 10rpx; border-radius:50%; background:#e8ebef; color:#7a8490; font-style:normal; font-weight:700; }
.process-step.done i { background:var(--pub-blue); color:white; }
.check-list { border-top:2rpx solid #edf0f4; padding-top:12rpx; }
.check-row { display:flex; justify-content:space-between; gap:16rpx; padding:13rpx 0; font-size:27rpx; }
.check-row span { color:var(--pub-text); }.check-row b { text-align:right; }.is-ok { color:var(--pub-green); }.is-warn { color:var(--pub-amber); }
.topic-card { border:2rpx solid #e4e9ef; border-left:8rpx solid var(--pub-blue); border-radius:18rpx; padding:25rpx 24rpx; margin-top:20rpx; }
.topic-card.type-discussion { border-left-color:#a26432; }.topic-card.type-notice { border-left-color:#317f6d; }
.topic-top { display:flex; justify-content:space-between; align-items:center; }.topic-no { color:var(--pub-sub); font-size:24rpx; }.topic-type { color:var(--pub-blue); background:var(--pub-blue-soft); border-radius:999rpx; padding:6rpx 16rpx; font-size:24rpx; }
.topic-card h3 { margin:14rpx 0 20rpx; color:var(--pub-ink); font-size:32rpx; line-height:1.55; }
.vote-grid { display:grid; grid-template-columns:repeat(4,1fr); background:#f6f8fb; border-radius:14rpx; padding:18rpx 8rpx; }
.vote-grid div { text-align:center; border-right:2rpx solid #e5e9ee; }.vote-grid div:last-child { border:0; }.vote-grid b,.vote-grid span { display:block; }.vote-grid b { font-size:34rpx; color:var(--pub-ink); }.vote-grid span { font-size:23rpx; color:var(--pub-sub); margin-top:4rpx; }
.option-list { background:#f6f8fb; border-radius:14rpx; padding:8rpx 20rpx; }.option-row { display:flex; justify-content:space-between; padding:15rpx 0; border-bottom:2rpx solid #e5e9ee; font-size:28rpx; }.option-row:last-child { border:0; }
.topic-result { margin-top:18rpx; padding:16rpx 20rpx; border-radius:12rpx; font-size:28rpx; font-weight:700; background:#eef1f5; color:var(--pub-sub); }.topic-result.passed,.topic-result.recorded { background:var(--pub-green-soft); color:var(--pub-green); }.topic-result.failed { background:#fff0f0; color:#b33b3b; }.topic-result.pending { background:var(--pub-amber-soft); color:var(--pub-amber); }
.basis { color:var(--pub-sub); font-size:24rpx; margin:13rpx 0 0; }.opinion-summary strong { color:var(--pub-text); font-size:27rpx; }.opinion-summary ul { padding-left:36rpx; margin:12rpx 0; }.opinion-summary li,.opinion-summary p,.notice-content { color:var(--pub-text); font-size:28rpx; line-height:1.75; margin:8rpx 0; }
.minutes-body { white-space:pre-wrap; color:var(--pub-text); font-size:30rpx; line-height:1.9; }
.section-empty { text-align:center; color:var(--pub-sub); font-size:28rpx; padding:30rpx 0; }
.todo-row { display:flex; align-items:center; justify-content:space-between; gap:16rpx; padding:20rpx 0; border-bottom:2rpx solid #edf0f4; }.todo-row:last-child { border:0; }.todo-main { min-width:0; }.todo-main b,.todo-main span { display:block; }.todo-main b { color:var(--pub-ink); font-size:29rpx; }.todo-main span { color:var(--pub-sub); font-size:24rpx; margin-top:7rpx; }.todo-row em { flex:none; border-radius:999rpx; padding:8rpx 16rpx; font-size:24rpx; font-style:normal; }.todo-todo { background:var(--pub-amber-soft); color:var(--pub-amber); }.todo-doing { background:var(--pub-blue-soft); color:var(--pub-blue); }.todo-done { background:var(--pub-green-soft); color:var(--pub-green); }
.public-note { background:#e9eef4; border-radius:18rpx; padding:25rpx 28rpx; color:var(--pub-sub); font-size:26rpx; line-height:1.7; }.public-note b,.public-note span { display:block; }.public-note b { color:var(--pub-ink); margin-bottom:6rpx; }
</style>
