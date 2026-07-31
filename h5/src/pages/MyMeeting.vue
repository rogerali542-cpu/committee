<template>
  <div class="mm" :class="{ 'mm--with-footer': stage === 'preparing' }">
    <PageNav title="会议通知" style="margin:-12px -12px 0;">
      <template #right><button class="nav-home-btn" @click="goModuleHome('meeting')">首页</button></template>
    </PageNav>
    <!-- 会议通知：准备阶段用完整通知卡（与主任通知页一致），其余阶段用简要信息卡 -->
    <div v-if="stage === 'preparing'" class="notice-card">
      <div class="nc-title">{{ title }}</div>
      <div class="nc-para">{{ noticeText }}</div>
      <div class="nc-sign">业主委员会</div>
    </div>
    <div v-else class="mm-card">
      <span class="mm-title">{{ title }}</span>
      <div class="mm-meta">
        <span class="mm-meta-row">🕒 {{ dateText }}</span>
        <span class="mm-meta-row">📍 {{ location }}</span>
      </div>
      <span v-if="description" class="mm-desc">{{ description }}</span>
    </div>

    <!-- 进行中已统一走「会议进行」页（loadDetail 里重定向），本页不再渲染进行中议题卡 -->

    <!-- 进行中：录音 + 材料 -->
    <div v-if="stage === 'ongoing'" class="mm-card">
      <span class="mm-section-title">会议录音</span>
      <span class="mm-section-desc">录音用于帮助记录员整理会议内容，只保存不自动处理</span>

      <!-- 录音控件（本人已签到后显示；signedIn 来自后端、会议开始时已清空，按用户区分） -->
      <div v-if="signedIn">
        <div class="mm-recorder">
          <div class="mm-rec-dot" :class="{ on: recording }"></div>
          <span class="mm-rec-time">{{ timeText }}</span>
          <span class="mm-rec-status">{{ recording ? '录音中' : (tempFilePath ? '已保存，待上传' : (hasRecording ? '已暂停' : '尚未开始')) }}</span>
        </div>
        <button class="mm-rec-btn" @click="toggleRecord" :disabled="uploading">
          {{ recording ? '暂停录音' : (recorderStarted ? '继续录音' : (tempFilePath ? '重新录音' : '开始录音')) }}
        </button>
        <button class="mm-rec-btn ghost" @click="finishRecord" :disabled="uploading">
          结束录音并上传
        </button>
        <div class="mm-divider"><span class="mm-divider-text">或</span></div>
        <button class="mm-rec-btn ghost" @click="chooseAudioFile" :disabled="uploading">
          选择已有文件上传
        </button>
        <span v-if="uploading" class="mm-uploading">上传中…</span>
      </div>
      <!-- 未签到：先点签到（会前"确认参加"已被开始会议清空，需会上再签一次） -->
      <div v-else class="mm-sign-block">
        <div class="mm-big-btn" @click="confirmAttend">
          <span class="mm-big-ico">✍️</span>
          <span class="mm-big-text">签到</span>
        </div>
        <span class="mm-sign-hint">签到后即可录制备份录音</span>
      </div>
    </div>

    <!-- 已结束 -->
    <div v-if="stage === 'ended'" class="mm-card">
      <div class="mm-result">
        <span class="mm-result-ico">📋</span>
        <span class="mm-result-text">{{ resultText }}</span>
      </div>
      <div v-if="topics.length" class="mm-topics">
        <div class="mm-topic" v-for="(item, index) in topics" :key="item.title + '_' + index" @click="openTopicSheetById(item.id)">
          <span class="mm-topic-title">{{ item.title }}</span>
          <span class="mm-topic-result">{{ item.resultText }}</span>
        </div>
      </div>
      <div class="mm-big-btn" @click="viewResult">
        <span class="mm-big-ico">📄</span>
        <span class="mm-big-text">查看会议记录</span>
      </div>
    </div>

    <!-- 已上传录音列表（进行中+已结束阶段显示） -->
    <div v-if="recordings.length" class="mm-card">
      <span class="mm-section-title">已上传录音（{{ recordings.length }}）</span>
      <div class="mm-rec-item" v-for="(item, index) in recordings" :key="item.id">
        <div class="mm-rec-item-left">
          <span class="mm-rec-item-name">{{ item.uploaderName || '未知' }} · <span class="mm-rec-item-time">{{ item.createdAt || '' }}</span></span>
          <span class="mm-rec-item-status">{{ item.asrStatus === 'done' ? '已转写' : item.asrStatus === 'processing' ? '转写中' : '已保存' }}</span>
        </div>
        <span class="mm-rec-item-play" @click="togglePlay(index)">{{ playingIdx === index ? '⏸' : '▶' }}</span>
      </div>
    </div>

    <!-- 会议材料（各阶段可看，点开即真实查看文件——图片/PDF 全屏预览） -->
    <div v-if="hasMaterials" class="mm-card mm-mat-card">
      <span class="mm-section-title">会议材料（{{ materials.length }}）</span>
      <div class="mm-mat-item" v-for="(item, index) in materials" :key="item.id || item.fileName || item.name || index" @click="openMaterial(item)">
        <span class="mm-mat-ico">📎</span>
        <span class="mm-mat-name">{{ item.fileName || item.name || '会议材料' }}</span>
        <span class="mm-mat-arrow">›</span>
      </div>
    </div>

    <!-- 准备阶段：确认参会固定在屏幕底部拇指区，方便手机点击 -->
    <div v-if="stage === 'preparing'" class="mm-footer">
      <div v-if="signedIn" class="mm-done">
        <div class="mm-done-row">
          <span class="mm-done-ico">✓</span>
          <span class="mm-done-text">已确认参加</span>
        </div>
        <span class="mm-cancel" @click="cancelAttend">取消参加</span>
      </div>
      <template v-else>
        <div v-if="declined" class="mm-declined">已登记：因故缺席（如仍可参加，请点下方确认）</div>
        <div class="mm-big-btn narrow" @click="confirmAttend">
          <span class="mm-big-ico">✅</span>
          <span class="mm-big-text">确认参加</span>
        </div>
        <div v-if="!declined" class="mm-decline-wrap">
          <span class="mm-decline-pill" @click="declineAttend">无法参会</span>
        </div>
      </template>
    </div>

    <!-- 议题弹层：表决 + 意见 -->
    <TopicSheet v-if="meetingIdRef" :meeting-id="meetingIdRef" :topic="sheetTopic" :interactive="stage === 'ongoing'"
                :signed-in="signedIn" :has-prev="sheetHasPrev" :has-next="sheetHasNext"
                @close="sheetTopicId = null" @changed="loadDetail" @prev="gotoPrevTopic" @next="gotoNextTopic" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onMounted, onActivated, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast, showModal } from '@/utils/ui'
import { navigateTo, redirectTo, goModuleHome } from '@/utils/navigate'
import { pickFile } from '@/utils/upload'
import { useRecorder } from '@/composables/useRecorder'
import { openMaterialViewer } from '@/composables/materialViewer'
import PageNav from '@/components/PageNav.vue'
import TopicSheet from '@/components/TopicSheet.vue'

function timeStr(s) {
  const m = Math.floor(s / 60)
  return String(m).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0')
}

function topicResult(t) {
  if (t.voteRequired === false) return '已完成'
  if (t.passed || t.status === 'passed') return '通过'
  if (t.status === 'failed') return '未通过'
  return '待定'
}

function meetingResult(d) {
  if (d.compliance === 'invalid') return '会议无效'
  if (d.compliance === 'flawed') return '会议有效（有说明）'
  if (d.compliance === 'valid') return '会议有效'
  return '会议已结束'
}

const route = useRoute()

let meetingId = null

const loading = ref(true)
const title = ref('')
const dateText = ref('')
const meetingDate = ref('')
const meetingTime = ref('')
const location = ref('')
const meetingMethod = ref('offline')
const description = ref('')
const stage = ref('') // preparing / ongoing / ended
const signedIn = ref(false) // 后端持久态：会议开始时已清空，ongoing 阶段即"本人会上是否已签到"（按用户区分）
const declined = ref(false) // 因故缺席
const materials = ref([])
const hasMaterials = ref(false)

// 录音（H5：useRecorder 替代 wx.getRecorderManager）
const rec = useRecorder()
const { timeText } = rec
const recording = computed(() => rec.recording.value && !rec.paused.value)   // 正在录（非暂停）
const recorderStarted = computed(() => rec.recording.value)                   // 会话进行中（含暂停）
const hasRecording = computed(() => rec.recording.value && rec.paused.value)  // 已暂停
const tempFilePath = ref('')  // H5 不落临时文件路径，保留占位（不用“已保存待上传”中间态）
const uploading = ref(false)
const recordings = ref([]) // 已上传的录音列表

// 已结束
const resultText = ref('')
const topics = ref([])

// ── 议题弹层（表决+意见）──
const rawTopics = ref([])            // 完整 TopicVO 列表（弹层/角标用）
const meetingIdRef = ref(null)       // meetingId 的响应式镜像（传给弹层）
const sheetTopicId = ref(null)
const sheetTopic = computed(() => rawTopics.value.find(t => t.id === sheetTopicId.value) || null)
function openTopicSheetById(id) { if (id != null) sheetTopicId.value = id }
// 弹层"上一个/下一个议题"
const sheetHasPrev = computed(() => rawTopics.value.findIndex(t => t.id === sheetTopicId.value) > 0)
const sheetHasNext = computed(() => { const i = rawTopics.value.findIndex(t => t.id === sheetTopicId.value); return i >= 0 && i < rawTopics.value.length - 1 })
function gotoPrevTopic() { const i = rawTopics.value.findIndex(t => t.id === sheetTopicId.value); if (i > 0) sheetTopicId.value = rawTopics.value[i - 1].id }
function gotoNextTopic() { const i = rawTopics.value.findIndex(t => t.id === sheetTopicId.value); if (i >= 0 && i < rawTopics.value.length - 1) sheetTopicId.value = rawTopics.value[i + 1].id }

// ── 会议通知正文（与主任通知页一致：微信口吻整段）──
function fmtHm(t) { return String(t || '').slice(0, 5) }
function fmtCnDate(s) {
  const m = String(s || '').match(/^(\d{4})-(\d{2})-(\d{2})/)
  return m ? (Number(m[1]) + '年' + Number(m[2]) + '月' + Number(m[3]) + '日') : (s || '')
}
const noticeTopicsText = computed(() => {
  const titles = topics.value.map((t) => (t && t.title) || '').filter(Boolean)
  if (!titles.length) return '（待定）'
  if (titles.length <= 2) return titles.join('、')
  return titles.slice(0, 2).join('、') + ' 等'
})
const noticeText = computed(() => {
  const place = meetingMethod.value === 'online'
    ? '以线上方式召开本次会议，线上平台为' + (location.value || '微信工作群')
    : '在' + (location.value || '') + '召开本次会议'
  return '各位委员：现拟于 ' + fmtCnDate(meetingDate.value) + ' ' + fmtHm(meetingTime.value) +
    ' ' + place + '，主要议题：' + noticeTopicsText.value + '，请准时参加。'
})

const playingIdx = ref(-1)
let _mmAudio = null // 录音回放用的 HTMLAudioElement（替代 wx.createInnerAudioContext）

// ── 录音管理（上传/回放已迁移；toggleRecord/finishRecord 的页内实时录音非本页核心，仍占位）──

async function toggleRecord() {
  if (uploading.value) return
  try {
    if (!rec.recording.value) { await rec.start() }   // 开始
    else if (rec.paused.value) { rec.resume() }        // 继续
    else { rec.pause() }                               // 暂停
  } catch (e) {
    toast({ title: (e && e.message) || '无法录音（需允许麦克风）', icon: 'none' })
  }
}

async function finishRecord() {
  if (uploading.value) return
  if (!rec.recording.value) { toast({ title: '尚未开始录音', icon: 'none' }); return }
  const result = await rec.stop()
  if (!result || !result.blob) { toast({ title: '录音为空', icon: 'none' }); return }
  uploading.value = true
  try {
    const file = new File([result.blob], '会议录音.' + (result.ext || 'webm'), { type: result.blob.type || 'audio/webm' })
    await api.committeeUploadRecording(meetingId, file)
    toast({ title: '已上传', icon: 'success' })
    rec.reset()
    await loadDetail()
  } catch (e) {
    toast({ title: (e && e.message) || '上传失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

async function chooseAudioFile() {
  if (uploading.value) return
  const file = await pickFile('audio/*')
  if (!file) return // 用户取消
  uploading.value = true
  try {
    await api.committeeUploadRecording(meetingId, file)
    toast({ title: '已上传', icon: 'success' })
    await loadDetail()
  } catch (e) {
    toast({ title: (e && e.message) || '上传失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

// ── 会议详情 ──

async function loadDetail() {
  try {
    const d = await api.committeeDetail(meetingId)
    // 进行中会议：所有身份统一走「会议进行」页（议题表决/意见 + 录音查看；主任专属操作在那边按权限隐藏）
    if (d.stage === 'ongoing' && d.record) {
      redirectTo('/pages/meeting-live-quick/meeting-live-quick?type=committee&meetingId=' + meetingId)
      return
    }
    const record = d.record || {}
    const me = (record.attendances || []).find(a => a.isSelf) || {}
    const mats = d.materials || []
    const tps = (record.topics || []).map(t => ({
      id: t.id,
      title: t.title,
      resultText: topicResult(t)
    }))
    rawTopics.value = record.topics || []
    meetingIdRef.value = meetingId
    // 录音列表
    const recs = record.recordings || []
    loading.value = false
    title.value = d.title || ''
    dateText.value = ((d.meetingDate || '') + ' ' + (d.meetingTime || '')).trim()
    meetingDate.value = d.meetingDate || ''
    meetingTime.value = d.meetingTime || ''
    location.value = d.location || ''
    meetingMethod.value = d.meetingMethod || 'offline'
    description.value = d.description || ''
    stage.value = d.stage
    signedIn.value = !!me.signedIn
    declined.value = !!me.declined
    materials.value = mats
    hasMaterials.value = mats.length > 0
    topics.value = tps
    resultText.value = meetingResult(d)
    recordings.value = recs
  } catch (e) {
    loading.value = false
    toast({ title: '加载失败', icon: 'none' })
  }
}

async function confirmAttend() {
  if (signedIn.value) return
  const isOngoing = stage.value === 'ongoing'
  const res = await showModal({
    title: isOngoing ? '签到' : '确认参加',
    content: isOngoing ? '确认签到本次会议？' : '确认参加本次会议？',
    confirmText: isOngoing ? '签到' : '确认参加',
    confirmColor: '#FFA800'
  })
  if (!res.confirm) return
  try {
    await api.committeeSelfToggle(meetingId, 'signedIn')
    signedIn.value = true
    declined.value = false
    toast({ title: isOngoing ? '已签到' : '已确认参加', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '操作失败', icon: 'none' })
  }
}

async function declineAttend() {
  if (signedIn.value) return
  const res = await showModal({
    title: '无法参会',
    content: '确认本次会议无法参加？将登记为"因故缺席"。',
    confirmText: '无法参会',
    cancelText: '再想想'
  })
  if (!res.confirm) return
  try {
    await api.committeeSelfToggle(meetingId, 'declined')
    declined.value = true
    signedIn.value = false
    toast({ title: '已登记：无法参会', icon: 'none' })
  } catch (e) {
    toast({ title: (e && e.message) || '操作失败', icon: 'none' })
  }
}

async function cancelAttend() {
  if (!signedIn.value) return
  const res = await showModal({
    title: '取消参加',
    content: '确定取消参加本次会议？'
  })
  if (!res.confirm) return
  try {
    await api.committeeSelfToggle(meetingId, 'cancel')
    signedIn.value = false
    declined.value = false
  } catch (e) {
    toast({ title: (e && e.message) || '操作失败', icon: 'none' })
  }
}

// 点开某份材料：有文件 url 则全屏预览（图片/PDF），否则提示无可预览文件
function openMaterial(item) {
  if (item && (item.url || item.fileUrl)) {
    openMaterialViewer(item)
  } else {
    showModal({
      title: (item && (item.fileName || item.name)) || '会议材料',
      content: '该材料暂无可在线预览的文件',
      showCancel: false
    })
  }
}

function viewResult() {
  navigateTo('/pages/minutes-public/minutes-public?meetingId=' + meetingId)
}

// 试听录音（H5 HTMLAudioElement）
function togglePlay(idx) {
  const rec = recordings.value[idx]
  const url = rec && rec.recordingUrl
  if (!url) { toast({ title: '该录音暂无音频', icon: 'none' }); return }
  // 再次点同一条 = 暂停
  if (playingIdx.value === idx && _mmAudio) { try { _mmAudio.pause() } catch (e) {} return }
  // 切到另一条：先静默停掉旧的（摘掉旧 handler 避免误触发）
  if (_mmAudio) { _mmAudio.onplay = _mmAudio.onpause = _mmAudio.onended = _mmAudio.onerror = null; try { _mmAudio.pause() } catch (e) {} }
  _mmAudio = new Audio(url)
  _mmAudio.onplay = () => { playingIdx.value = idx }
  _mmAudio.onpause = () => { if (playingIdx.value === idx) playingIdx.value = -1 }
  _mmAudio.onended = () => { if (playingIdx.value === idx) playingIdx.value = -1 }
  _mmAudio.onerror = () => { playingIdx.value = -1; toast({ title: '播放失败', icon: 'none' }) }
  const p = _mmAudio.play()
  if (p && p.catch) p.catch(() => { playingIdx.value = -1; toast({ title: '播放失败', icon: 'none' }) })
}

onMounted(() => {
  meetingId = parseInt(route.query.id || route.query.meetingId)
  if (meetingId) loadDetail()
})

onActivated(() => {
  if (meetingId) loadDetail()
})

onUnmounted(() => {
  if (_mmAudio) { try { _mmAudio.pause() } catch (e) {} _mmAudio = null }
  try { rec.reset() } catch (e) {}
})
</script>

<style scoped>
/* 顶栏统一为纯深橙（与 CommitteeDetail 一致，覆盖 PageNav 默认黄橙渐变） */
:deep(.page-nav) { background: #2b5589; }  /* 会议模块页头（规范三色制） */
.mm { padding: 12px 12px calc(60rpx + env(safe-area-inset-bottom)); background: #f4f5f7; min-height: 100vh; }
/* 准备阶段有底部固定确认栏：留足底部空间，材料多时也不会被遮住 */
.mm--with-footer { padding-bottom: calc(320rpx + env(safe-area-inset-bottom)); }

/* 底部确认栏：浮动卡片（拇指区），宽度贴合按钮、只比按钮大一圈、居中不贴边 */
.mm-footer { position:fixed; bottom:calc(24rpx + env(safe-area-inset-bottom)); left:50%; transform:translateX(-50%); width:fit-content; z-index:100; box-sizing:border-box; background:#fff; border:1px solid #ECECEF; border-radius:20rpx; box-shadow:0 8rpx 26rpx rgba(0,0,0,0.12); padding:22rpx 28rpx; }

/* 卡片通用 */
.mm-card {
  background: #fff; border-radius: 24rpx;
  padding: 36rpx 32rpx; margin-bottom: 28rpx;
  box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06);
}

/* 会议通知卡（与主任通知页 CommitteeDetail 一致） */
.notice-card { background:#fff; border-radius:18px; overflow:hidden; box-shadow:0 6rpx 22rpx rgba(0,0,0,0.07); margin-top:32rpx; margin-bottom:28rpx; }
.nc-title { font-size:42rpx; font-weight:700; color:#1a1a1a; text-align:center; padding:44rpx 40rpx 10rpx; line-height:1.4; }
.nc-para { padding:8rpx 44rpx 4rpx; font-size:34rpx; color:#000; line-height:1.8; text-align:justify; text-indent:2em; }
.nc-sign { padding:6rpx 44rpx 34rpx; text-align:right; font-size:34rpx; font-weight:700; color:#1a1a1a; }

/* 会议信息 */
.mm-title { display: block; font-size: 52rpx; font-weight: 700; color: #1f2329; line-height: 1.35; }
.mm-meta { margin-top: 20rpx; display: flex; flex-direction: column; }
.mm-meta-row { font-size: 36rpx; color: #4a5560; margin-top: 10rpx; }
.mm-desc { display: block; margin-top: 20rpx; font-size: 32rpx; color: #6b7785; line-height: 1.5; }

/* 章节标题 */
.mm-section-title { display: block; font-size: 40rpx; font-weight: 700; color: #1f2329; margin-bottom: 8rpx; }
.mm-section-desc { display: block; font-size: 28rpx; color: #666; margin-bottom: 24rpx; line-height: 1.5; }

/* 大按钮：与 CommitteeDetail 的 pf-btn 一致（深橙胶囊） */
.mm-big-btn {
  display: flex; align-items: center; justify-content: center;
  height: 88rpx; border-radius: 44rpx; background: var(--c-primary-dark);
  border: none;
}
.mm-big-btn:active { background: var(--c-primary-strong); }
.mm-big-btn.narrow { width: 460rpx; margin: 0 auto; }   /* 确认参加：固定宽，供底栏卡片贴合 */
.mm-big-ico { font-size: 36rpx; margin-right: 12rpx; }
.mm-big-text { font-size: 32rpx; font-weight: 600; color: #fff; }

/* 已确认参加：绿色标签缩小到与确认按钮同尺寸（70% 宽·88rpx 胶囊） */
.mm-done { display: flex; flex-direction: column; align-items: center; }
.mm-done-row {
  display: flex; align-items: center; justify-content: center;
  height: 88rpx; width: 460rpx;
  background: #EAF9EE; border-radius: 44rpx;
}
.mm-done-ico {
  width: 40rpx; height: 40rpx; border-radius: 50%;
  background: #27AE60; color: #fff; font-size: 26rpx; font-weight: 700;
  text-align: center; line-height: 40rpx; margin-right: 12rpx;
}
.mm-done-text { font-size: 32rpx; font-weight: 700; color: #1E8449; }
.mm-cancel { margin-top: 16rpx; font-size: 28rpx; color: #666; padding: 8rpx; }
.mm-declined { display: block; text-align: center; font-size: 30rpx; color: #E67E22; margin-bottom: 20rpx; line-height: 1.5; }
.mm-decline-wrap { text-align: center; margin-top: 28rpx; }
.mm-decline-pill { display: inline-flex; align-items: center; justify-content: center; height: 88rpx; padding: 0 56rpx; border-radius: 44rpx; background: #f5f5f5; color: #777; font-size: 32rpx; font-weight: 600; border: none; }

/* 录音控件 */
.mm-recorder {
  display: flex; align-items: center;
  padding: 28rpx 20rpx; background: #fafafa; border-radius: 16rpx;
  margin-bottom: 20rpx;
}
.mm-rec-dot {
  width: 24rpx; height: 24rpx; border-radius: 50%; background: #999; margin-right: 16rpx;
}
.mm-rec-dot.on { background: #E74C3C; box-shadow: 0 0 12rpx rgba(231,76,60,0.4); }
.mm-rec-time { font-size: 44rpx; font-weight: 700; color: #1f2329; font-variant-numeric: tabular-nums; margin-right: 16rpx; }
.mm-rec-status { font-size: 28rpx; color: #666; }

.mm-rec-btn {
  width: 100%; height: 88rpx; border-radius: 44rpx;
  background: var(--c-primary-dark); color: #fff; font-size: 32rpx; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 16rpx;
  border: none;
}
.mm-rec-btn:active { background: var(--c-primary-strong); }
.mm-rec-btn.ghost { background: #f5f5f5; color: #777; }
.mm-rec-btn.ghost:active { background: #ececec; }
.mm-rec-btn[disabled] { opacity: 0.5; }

.mm-divider { display: flex; align-items: center; margin: 8rpx 0 16rpx; }
.mm-divider::before, .mm-divider::after { content: ''; flex: 1; height: 1rpx; background: #e8eaed; }
.mm-divider-text { padding: 0 20rpx; font-size: 28rpx; color: #666; }

.mm-uploading { display: block; text-align: center; font-size: 30rpx; color: #FFA800; padding: 12rpx; }

.mm-tip-block { padding: 48rpx 0; text-align: center; }
.mm-tip { font-size: 32rpx; color: #666; }
.mm-sign-block { display: flex; flex-direction: column; gap: 20rpx; }
.mm-sign-hint { display: block; text-align: center; font-size: 30rpx; color: #666; }

/* 录音列表 */
.mm-rec-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 24rpx 0; border-top: 1rpx solid #f0f2f5;
}
.mm-rec-item:first-child { border-top: none; }
.mm-rec-item-left { flex: 1; display: flex; flex-direction: column; }
.mm-rec-item-name { font-size: 32rpx; color: #3a434d; }
.mm-rec-item-time { color: #666; font-size: 28rpx; }
.mm-rec-item-status { font-size: 28rpx; color: #666; margin-top: 4rpx; }
.mm-rec-item-play { font-size: 48rpx; padding: 12rpx 16rpx; }

/* 已结束结果 */
.mm-result { display: flex; align-items: center; justify-content: center; margin-bottom: 28rpx; }
.mm-result-ico { font-size: 48rpx; margin-right: 14rpx; }
.mm-result-text { font-size: 48rpx; font-weight: 700; color: #1f2329; }
.mm-topics { margin-bottom: 28rpx; }
.mm-topic {
  display: flex; align-items: center; justify-content: space-between;
  padding: 24rpx 0; border-top: 1rpx solid #f0f2f5;
}
.mm-topic:first-child { border-top: none; }
.mm-topic-title { flex: 1; font-size: 34rpx; color: #3a434d; margin-right: 20rpx; }
.mm-topic-result { font-size: 34rpx; font-weight: 600; color: #FF8C00; }

/* 进行中议题卡样式已随统一「会议进行」页移除（本页 ongoing 直接重定向） */

/* 会议材料 */
.mm-mat {
  display: flex; align-items: center;
  background: #fff; border-radius: 20rpx; padding: 32rpx 28rpx;
  box-shadow: 0 6rpx 18rpx rgba(0,0,0,0.04);
}
.mm-mat:active { background: #fafafa; }
.mm-mat-ico { font-size: 40rpx; margin-right: 16rpx; }
.mm-mat-text { flex: 1; font-size: 36rpx; color: #3a434d; }
.mm-mat-arrow { font-size: 44rpx; color: #666; }
/* 会议材料列表：每份文件一行，点开全屏预览 */
.mm-mat-item {
  display: flex; align-items: center;
  padding: 26rpx 8rpx;
  border-top: 1rpx solid #f0f0f0;
}
.mm-mat-item:first-of-type { border-top: none; }
.mm-mat-item:active { background: #fafafa; }
.mm-mat-name {
  flex: 1; min-width: 0;
  font-size: 34rpx; color: #3a434d; line-height: 1.4;
  word-break: break-all;
}
.nav-home-btn { display: inline-flex; align-items: center; height: 64rpx; margin-right: 20rpx; padding: 0 24rpx; border: 2rpx solid rgba(255,255,255,0.6); border-radius: 34rpx; background: rgba(255,255,255,0.12); color: #fff; font-size: 30rpx; font-weight: 600; line-height: 1; }
.nav-home-btn:active { background: rgba(255,255,255,0.28); }
</style>
