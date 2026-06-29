<template>
  <div class="mm">
    <PageNav title="我的会议" style="display:block;margin:-28rpx -24rpx 0;" />
    <!-- 会议信息 -->
    <div class="mm-card">
      <span class="mm-title">{{ title }}</span>
      <div class="mm-meta">
        <span class="mm-meta-row">🕒 {{ dateText }}</span>
        <span class="mm-meta-row">📍 {{ location }}</span>
      </div>
      <span v-if="description" class="mm-desc">{{ description }}</span>
    </div>

    <!-- 阶段主区：准备→确认参加 / 进行中→录音+材料 / 已结束→查看结果 -->
    <div v-if="stage === 'preparing'" class="mm-card">
      <div v-if="signedIn" class="mm-done">
        <div class="mm-done-row">
          <span class="mm-done-ico">✓</span>
          <span class="mm-done-text">已确认参加</span>
        </div>
        <span class="mm-cancel" @click="cancelAttend">取消参加</span>
      </div>
      <template v-else>
        <div v-if="declined" class="mm-declined">已登记：因故缺席（如仍可参加，请点下方确认）</div>
        <div class="mm-big-btn" @click="confirmAttend">
          <span class="mm-big-ico">✅</span>
          <span class="mm-big-text">确认参加</span>
        </div>
        <div v-if="!declined" class="mm-decline-wrap">
          <span class="mm-decline-pill" @click="declineAttend">无法参会</span>
        </div>
      </template>
    </div>

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
        <div class="mm-topic" v-for="(item, index) in topics" :key="item.title + '_' + index">
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

    <!-- 会议材料（各阶段可看） -->
    <div v-if="hasMaterials" class="mm-mat" @click="viewMaterials">
      <span class="mm-mat-ico">📎</span>
      <span class="mm-mat-text">查看会议材料（{{ materials.length }}）</span>
      <span class="mm-mat-arrow">›</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onMounted, onActivated, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast, showModal } from '@/utils/ui'
import { navigateTo } from '@/utils/navigate'
import { pickFile } from '@/utils/upload'
import { useRecorder } from '@/composables/useRecorder'
import PageNav from '@/components/PageNav.vue'

function timeStr(s) {
  const m = Math.floor(s / 60)
  return String(m).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0')
}

function topicResult(t) {
  if (t.voteRequired === false) return '已记录'
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
const location = ref('')
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
    const record = d.record || {}
    const me = (record.attendances || []).find(a => a.isSelf) || {}
    const mats = d.materials || []
    const tps = (record.topics || []).map(t => ({
      title: t.title,
      resultText: topicResult(t)
    }))
    // 录音列表
    const recs = record.recordings || []
    loading.value = false
    title.value = d.title || ''
    dateText.value = ((d.meetingDate || '') + ' ' + (d.meetingTime || '')).trim()
    location.value = d.location || ''
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

function viewMaterials() {
  const names = materials.value
    .map((m, i) => (i + 1) + '. ' + (m.fileName || m.name || '材料'))
    .join('\n')
  showModal({ title: '会议材料', content: names || '暂无材料', showCancel: false })
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
.mm { padding: 28rpx 24rpx calc(60rpx + env(safe-area-inset-bottom)); background: #f4f5f7; min-height: 100vh; }

/* 卡片通用 */
.mm-card {
  background: #fff; border-radius: 24rpx;
  padding: 36rpx 32rpx; margin-bottom: 28rpx;
  box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06);
}

/* 会议信息 */
.mm-title { display: block; font-size: 52rpx; font-weight: 700; color: #1f2329; line-height: 1.35; }
.mm-meta { margin-top: 20rpx; display: flex; flex-direction: column; }
.mm-meta-row { font-size: 36rpx; color: #4a5560; margin-top: 10rpx; }
.mm-desc { display: block; margin-top: 20rpx; font-size: 32rpx; color: #6b7785; line-height: 1.5; }

/* 章节标题 */
.mm-section-title { display: block; font-size: 40rpx; font-weight: 700; color: #1f2329; margin-bottom: 8rpx; }
.mm-section-desc { display: block; font-size: 28rpx; color: #666; margin-bottom: 24rpx; line-height: 1.5; }

/* 大按钮 */
.mm-big-btn {
  display: flex; align-items: center; justify-content: center;
  height: 150rpx; border-radius: 22rpx; background: #FFA800;
  box-shadow: 0 8rpx 22rpx rgba(255,168,0,0.34);
}
.mm-big-btn:active { opacity: 0.88; }
.mm-big-ico { font-size: 56rpx; margin-right: 16rpx; }
.mm-big-text { font-size: 52rpx; font-weight: 700; color: #fff; }

/* 已确认参加 */
.mm-done { display: flex; flex-direction: column; align-items: center; }
.mm-done-row {
  display: flex; align-items: center; justify-content: center;
  height: 130rpx; width: 100%;
  background: #EAF9EE; border-radius: 22rpx;
}
.mm-done-ico {
  width: 56rpx; height: 56rpx; border-radius: 50%;
  background: #27AE60; color: #fff; font-size: 36rpx; font-weight: 700;
  text-align: center; line-height: 56rpx; margin-right: 16rpx;
}
.mm-done-text { font-size: 46rpx; font-weight: 700; color: #1E8449; }
.mm-cancel { margin-top: 28rpx; font-size: 30rpx; color: #666; padding: 8rpx; }
.mm-declined { display: block; text-align: center; font-size: 30rpx; color: #E67E22; margin-bottom: 20rpx; line-height: 1.5; }
.mm-decline-wrap { text-align: center; margin-top: 28rpx; }
.mm-decline-pill { display: inline-block; min-height: 84rpx; line-height: 84rpx; padding: 0 72rpx; border-radius: 42rpx; background: #eef0f3; color: #5b6673; font-size: 32rpx; font-weight: 600; }

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
  width: 100%; height: 88rpx; border-radius: 16rpx;
  background: #FFA800; color: #fff; font-size: 36rpx; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 16rpx;
  border: none;
}
.mm-rec-btn.ghost { background: #f0f2f5; color: #4a5560; }
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
</style>
