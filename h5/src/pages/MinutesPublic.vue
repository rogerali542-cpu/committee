<template>
  <div class="pub-page minutes-public-page">
    <PublishNav title="会议结果公示" :on-back="backToDetail" />

    <div class="pub-wrap">
      <div v-if="loading" class="pub-loading">正在整理会议公示内容…</div>

      <div v-else-if="!detail" class="pub-card pub-empty">
        <div class="empty-icon">!</div>
        <span class="empty-title">暂无法查看会议公示</span>
        <span class="empty-text">{{ emptyText }}</span>
      </div>

      <template v-else>
        <section class="pub-card hero-card">
          <span class="pub-tag">业委会会议</span>
          <span class="pub-badge" :class="badgeClass">{{ badgeText }}</span>
          <h1 class="pub-title">{{ detail.title || '业主委员会会议' }}</h1>
          <div class="pub-meta-grid">
            <div><span>会议时间</span><b>{{ meetingTimeText }}</b></div>
            <div><span>会议地点</span><b>{{ detail.location || '未记录' }}</b></div>
            <div><span>应到委员</span><b>{{ attendance.total }} 人</b></div>
            <div><span>实到委员</span><b>{{ attendance.present }} 人</b></div>
          </div>
          <div class="validity" :class="attendance.valid ? 'ok' : 'warn'">
            {{ attendance.valid ? '本次会议达到有效出席人数' : '本次会议出席人数未达到过半条件' }}
          </div>
        </section>

        <section class="pub-card">
          <header class="section-head">
            <span class="section-index">01</span>
            <div><h2>会议召开流程</h2><p>展示会议程序及记录完整情况</p></div>
          </header>
          <div class="process-line">
            <div v-for="(step, index) in processSteps" :key="step.label" class="process-step" :class="{ done: step.done }">
              <i>{{ step.done ? '✓' : index + 1 }}</i><span>{{ step.label }}</span>
            </div>
          </div>
          <div v-if="recordChecks.length" class="check-list">
            <div v-for="check in recordChecks" :key="check.label" class="check-row">
              <span>{{ check.label }}</span><b :class="check.ok ? 'is-ok' : 'is-warn'">{{ check.detail }}</b>
            </div>
          </div>
        </section>

        <section class="pub-card">
          <header class="section-head">
            <span class="section-index">02</span>
            <div><h2>议题审议结果</h2><p>共 {{ topics.length }} 项议题，按会议顺序公开</p></div>
          </header>

          <div v-if="!topics.length" class="section-empty">本次会议暂无结构化议题记录</div>
          <article v-for="(topic, index) in topics" :key="topic.id" class="topic-card" :class="'type-' + topic.type">
            <div class="topic-top">
              <span class="topic-no">议题 {{ index + 1 }}</span>
              <span class="topic-type">{{ topicTypeText(topic.type) }}</span>
            </div>
            <h3>{{ topic.title }}</h3>

            <template v-if="topic.type === 'vote'">
              <div v-if="topic.decisionType === 'multi_choice'" class="option-list">
                <div v-for="option in topic.options || []" :key="option.id" class="option-row">
                  <span>{{ option.label }}</span><b>{{ option.votes || 0 }} 票</b>
                </div>
              </div>
              <div v-else class="vote-grid">
                <div><b>{{ topic.forVotes || 0 }}</b><span>同意</span></div>
                <div><b>{{ topic.agVotes || 0 }}</b><span>反对</span></div>
                <div><b>{{ topic.abVotes || 0 }}</b><span>弃权</span></div>
                <div><b>{{ notVoted(topic) }}</b><span>未表决</span></div>
              </div>
              <div class="topic-result" :class="topic.status">{{ topic.text || resultText(topic) }}</div>
              <p class="basis">通过条件：全体委员 {{ topic.total || 0 }} 人，需至少 {{ topic.need || 0 }} 票同意</p>
            </template>

            <template v-else-if="topic.type === 'discussion'">
              <div class="opinion-summary">
                <strong>主要讨论意见</strong>
                <ul v-if="opinionsFor(topic.id).length">
                  <li v-for="op in opinionsFor(topic.id)" :key="op.id">{{ op.content }}</li>
                </ul>
                <p v-else>本议题未形成单独的书面讨论意见，具体审议情况见会议纪要。</p>
              </div>
              <div class="topic-result recorded">{{ topic.text || '讨论情况已记录' }}</div>
            </template>

            <template v-else>
              <p class="notice-content">{{ topic.content || '该事项已在会议中完成通报。' }}</p>
              <div class="topic-result recorded">{{ topic.notified ? '已完成通报' : '通报事项已记录' }}</div>
            </template>
          </article>
        </section>

        <section class="pub-card">
          <header class="section-head">
            <span class="section-index">03</span>
            <div><h2>正式会议纪要</h2><p>经整理确认的会议内容与决定</p></div>
          </header>
          <div v-if="minutesText" class="minutes-body">{{ minutesText }}</div>
          <div v-else class="section-empty">正式会议纪要尚未生成</div>
        </section>

        <section class="pub-card">
          <header class="section-head">
            <span class="section-index">04</span>
            <div><h2>会后事项跟进</h2><p>会议决定的后续执行情况</p></div>
          </header>
          <div v-if="todos.length" class="todo-list">
            <div v-for="todo in todos" :key="todo.id || todo.title" class="todo-row">
              <div class="todo-main"><b>{{ todo.title }}</b><span>{{ todo.owner || '责任主体待明确' }} · {{ todo.dueText || '完成时间待明确' }}</span></div>
              <em :class="'todo-' + todo.status">{{ todoStatusText(todo.status) }}</em>
            </div>
          </div>
          <div v-else class="section-empty">本次会议暂无需要会后继续办理的事项</div>
        </section>

        <div class="public-note">
          <b>公示说明</b>
          <span>本页根据会议记录、录音转写、委员确认意见及表决数据整理。录音、逐字稿、签名页和个人信息仅作内部归档，不在公示页面公开。</span>
        </div>
        <button class="pub-btn" @click="copyText">复制公示内容</button>
        <span class="pub-foot">本页为面向本小区业主的会议结果公示</span>
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
const processSteps = computed(() => [
  { label: '会议通知', done: !!detail.value.notifiedAt },
  { label: '委员签到', done: attendance.value.present > 0 },
  { label: '议题审议', done: topics.value.length > 0 },
  { label: '形成记录', done: !!minutesText.value },
  { label: '会议公示', done: isPublished.value }
])

function topicTypeText(type) {
  return type === 'vote' ? '表决' : type === 'discussion' ? '讨论' : '通知'
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
    const [minutesResult, opinionResult, todoResult] = await Promise.allSettled([
      api.committeeMinutes(meetingId),
      api.committeeOpinions(meetingId),
      api.committeeTodoList(meetingId)
    ])
    if (minutesResult.status === 'fulfilled') minutesText.value = String(minutesResult.value || '').trim()
    if (opinionResult.status === 'fulfilled') opinions.value = Array.isArray(opinionResult.value) ? opinionResult.value : []
    if (todoResult.status === 'fulfilled') {
      const value = todoResult.value
      todos.value = Array.isArray(value) ? value : (value && Array.isArray(value.items) ? value.items : [])
    }
  } catch (e) {
    detail.value = null
    emptyText.value = (e && e.message) || '会议公示内容暂不可用'
  } finally {
    loading.value = false
  }
}

async function copyText() {
  const lines = [detail.value.title, meetingTimeText.value, '会议地点：' + (detail.value.location || '未记录')]
  topics.value.forEach((topic, index) => lines.push(`议题${index + 1}：${topic.title}\n${topic.text || resultText(topic)}`))
  if (minutesText.value) lines.push('正式会议纪要：\n' + minutesText.value)
  if (todos.value.length) lines.push('会后事项：\n' + todos.value.map(item => `${item.title}（${todoStatusText(item.status)}）`).join('\n'))
  try {
    await navigator.clipboard.writeText(lines.filter(Boolean).join('\n\n'))
    toast({ title: '公示内容已复制' })
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
