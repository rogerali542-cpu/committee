<template>
  <div class="omf">
    <div class="omf-head">
      <div>
        <span class="omf-kicker">线上会议</span>
        <h2>{{ detail.title || '本次会议' }}</h2>
      </div>
    </div>

    <!-- 委员端·会议进行中且本人未签到:先各自签到(0725 用户定),签到后才进入结果确认 -->
    <template v-if="!isChair && !meetingEnded && !selfPresent">
      <section class="omf-card signin-card">
        <h3>会议签到</h3>
        <p class="omf-desc">本次会议以线上方式召开，请确认您正在参加本次会议。</p>
        <button class="omf-primary" :disabled="busy" @click="selfSignIn">{{ busy ? '正在签到…' : '我已参会，线上签到' }}</button>
      </section>
    </template>

    <template v-else-if="cardMode || !isChair">
      <section class="omf-card result-card">
        <div class="result-seal">会议结果确认卡</div>
        <div class="result-meta">{{ detail.meetingDate }} {{ detail.meetingTime }} · 线上召开</div>
        <div class="result-attendance">
          <b>实际参会</b>
          <span>{{ presentNames || '尚未登记' }}</span>
        </div>
        <div v-for="(topic, index) in topics" :key="topic.id" class="result-topic">
          <div class="result-topic-title">{{ index + 1 }}. {{ topic.title }}</div>
          <div class="result-topic-text">{{ topic.summaryDraft || '主持人尚未填写会议结果' }}</div>
          <div v-if="topic.voteRequired && topic.decisionType === 'multi_choice'" class="result-votes">
            {{ topic.options.map(option => option.label + ' ' + option.votes + '票').join(' · ') }}
          </div>
          <div v-else-if="topic.voteRequired" class="result-votes">
            赞成 {{ topic.voteFor }} · 反对 {{ topic.voteAgainst }} · 弃权 {{ topic.voteAbstain }}
          </div>
        </div>
        <div class="result-note">本人已核对以上线上会议结果，确认内容无误。</div>
        <button v-if="!selfSigned" class="omf-primary" :disabled="busy || !selfPresent" @click="confirmResultCard">
          {{ selfPresent ? '确认会议结果' : '未登记参会，无法确认' }}
        </button>
        <div v-else class="result-confirmed">✓ 已确认，效力等同本次线上会议签字</div>
        <button v-if="isChair" class="result-edit-btn" @click="cardMode = false">返回修改会议结果</button>
      </section>
    </template>

    <template v-else>
      <div class="omf-steps">
        <span :class="{ on: step === 1, done: step > 1 }">1 参会人员</span>
        <i></i>
        <span :class="{ on: step === 2, done: step > 2 }">2 议题结果</span>
        <i></i>
        <span :class="{ on: step === 3 }">3 结果确认</span>
      </div>

      <!-- 主持人第一步(0725 用户定):只管自己签到 + 查看各委员签到状态,不做代录 -->
      <section v-if="step === 1" class="omf-card">
        <h3 class="attendance-title">签到情况</h3>
        <button v-if="!selfPresent" type="button" class="omf-primary chair-self-sign" :disabled="busy"
                @click="selfSignIn">{{ busy ? '正在签到…' : '我已参会，线上签到' }}</button>
        <p class="omf-desc">各位委员在自己手机上签到后，这里会实时显示。</p>
        <div v-for="member in attendance" :key="member.userRoleId" class="member-row">
          <span class="member-name">{{ member.name }}</span>
          <span class="member-role">{{ member.role }}</span>
          <span class="member-state" :class="{ on: member.signedIn }">{{ member.signedIn ? '已签到' : '未签到' }}</span>
        </div>
        <button class="omf-primary" :disabled="busy || !presentCount" @click="step = 2">
          下一步，填写议题结果
        </button>
      </section>

      <section v-if="step === 2" class="omf-card">
        <div class="omf-section-head">
          <div>
            <h3>填写议题结果</h3>
          </div>
        </div>
        <div v-for="(topic, index) in topics" :key="topic.id" class="topic-form">
          <div class="topic-form-head">
            <span class="topic-no">{{ index + 1 }}</span>
            <div class="topic-heading">
              <b>{{ topic.title }}</b>
              <!-- 0722 用户定：类型写清楚——通知/讨论分开显示（同色） -->
              <span class="topic-kind" :class="topic.voteRequired ? 'vote' : 'discussion'">
                {{ topic.voteRequired ? '表决' : (topic.type === 'notice' ? '通知' : '讨论') }}
              </span>
            </div>
          </div>
          <div class="topic-input-wrap">
            <textarea v-model="topic.summaryDraft" rows="4"></textarea>
            <button class="voice-fill" :class="{ recording: voiceTopicId === topic.id }"
                    :disabled="voiceBusy && voiceTopicId !== topic.id" @click="toggleVoice(topic)">
              <span class="voice-fill-icon">{{ voiceTopicId === topic.id ? '■' : '🎙' }}</span>
              {{ voiceTopicId === topic.id ? '结束口述' : (voiceBusy ? '识别中…' : '口述填写') }}
            </button>
          </div>
          <div v-if="topic.voteRequired && topic.decisionType === 'multi_choice'" class="option-vote-list">
            <label v-for="option in topic.options" :key="option.id">
              <span>{{ option.label }}</span>
              <input v-model.number="option.votes" type="number" min="0" />
              <em>票</em>
            </label>
          </div>
          <div v-else-if="topic.voteRequired" class="vote-grid">
            <label><span>赞成</span><input v-model.number="topic.voteFor" type="number" min="0" /></label>
            <label><span>反对</span><input v-model.number="topic.voteAgainst" type="number" min="0" /></label>
            <label><span>弃权</span><input v-model.number="topic.voteAbstain" type="number" min="0" /></label>
          </div>
        </div>
        <div class="omf-actions">
          <button class="omf-secondary" @click="step = 1">上一步</button>
          <button class="omf-primary" :disabled="busy" @click="saveResults">生成会议结果卡</button>
        </div>
      </section>

      <section v-if="step === 3" class="omf-card share-card">
        <div class="share-icon">✓</div>
        <h3>会议结果卡已生成</h3>
        <p class="share-instruction">请转发到微信群让参会委员各自点击确认会议结果</p>
        <div class="wechat-share-preview" @click="shareResultCard">
          <div class="wsp-thumb">
            <span class="wsp-emblem">议</span>
            <span class="wsp-lines"></span>
          </div>
          <div class="wsp-content">
            <b>{{ detail.title || '线上会议结果确认' }}</b>
            <span>会议结果已生成，请参会委员点击确认</span>
            <small>业委会会议助手</small>
          </div>
        </div>
        <button class="omf-primary wechat-share-btn" @click="shareResultCard">
          转发到微信
        </button>
        <button class="omf-secondary full" @click="cardMode = true">预览结果卡</button>
        <div class="confirm-progress">
          <span>委员确认进度</span>
          <b>{{ signedCount }} / {{ presentCount }}</b>
        </div>
        <button class="omf-end" :disabled="busy" @click="endMeeting">
          {{ meetingEnded ? '查看会议详情' : '结束会议' }}
        </button>
        <button class="omf-link" @click="step = 2">返回修改</button>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/committee'
import { toast, showModal } from '@/utils/ui'

const props = defineProps({
  detail: { type: Object, required: true },
  meetingId: { type: [String, Number], required: true },
  isChair: { type: Boolean, default: false }
})
const emit = defineEmits(['reload'])

const busy = ref(false)
const step = ref(1)
const cardMode = ref(typeof location !== 'undefined' && new URLSearchParams(location.search).get('card') === '1')
const topics = reactive([])
const liveAttendance = ref([])
const voiceTopicId = ref(null)
const voiceBusy = ref(false)
let voiceRecorder = null
let voiceStream = null
let voiceChunks = []

const attendance = computed(() => liveAttendance.value.length
  ? liveAttendance.value
  : ((props.detail.record && props.detail.record.attendances) || []))
const present = computed(() => attendance.value.filter(item => item.signedIn))
const presentCount = computed(() => present.value.length)
const signedCount = computed(() => present.value.filter(item => item.signed).length)
const presentNames = computed(() => present.value.map(item => item.name).join('、'))
const selfAttendance = computed(() => attendance.value.find(item => item.isSelf) || null)
const selfPresent = computed(() => !!(selfAttendance.value && selfAttendance.value.signedIn))
const selfSigned = computed(() => !!(selfAttendance.value && selfAttendance.value.signed))
const meetingEnded = computed(() => props.detail.stage !== 'ongoing')

function initFromDetail() {
  liveAttendance.value = ((props.detail.record && props.detail.record.attendances) || []).slice()
  const raw = (props.detail.record && props.detail.record.topics) || []
  topics.splice(0, topics.length, ...raw.map(item => ({
    id: item.id,
    title: item.title,
    type: item.type,
    voteRequired: !!item.voteRequired,
    decisionType: item.decisionType || 'simple',
    options: (item.options || []).map(option => ({
      id: option.id,
      label: option.label,
      votes: Number(option.votes) || 0
    })),
    summaryDraft: item.summaryDraft || item.summary || item.content || '',
    voteFor: Number(item.forVotes != null ? item.forVotes : item.voteFor) || 0,
    voteAgainst: Number(item.agVotes != null ? item.agVotes : item.voteAgainst) || 0,
    voteAbstain: Number(item.abVotes != null ? item.abVotes : item.voteAbstain) || 0
  })))
  if (meetingEnded.value || cardMode.value) step.value = 3
}
watch(() => props.detail, initFromDetail, { immediate: true })

// 委员各自签到(0725 用户定):线上会议记远程参会;签到后进入结果确认卡
async function selfSignIn() {
  busy.value = true
  try {
    await api.committeeSelfAttend(props.meetingId, 'remote', false)
    await emitReload()
    toast({ title: '签到成功', icon: 'success' })
  } catch (e) {
    toast({ title: e.message || '签到失败', icon: 'none' })
  } finally { busy.value = false }
}

// saveAttendance/名单代录已删(0725 用户定):主持人只管自己签到、查看别人签到;实到=真实签到集合

function resultCode(topic) {
  if (!topic.voteRequired) return 'recorded'
  if (topic.decisionType === 'multi_choice') {
    const leading = Math.max(0, ...topic.options.map(option => Number(option.votes) || 0))
    return leading >= Math.floor(presentCount.value / 2) + 1 ? 'passed' : 'rejected'
  }
  const threshold = Math.floor(presentCount.value / 2) + 1
  if (Number(topic.voteFor) >= threshold) return 'passed'
  const counted = Number(topic.voteFor) + Number(topic.voteAgainst) + Number(topic.voteAbstain)
  return counted >= presentCount.value ? 'rejected' : 'unclear'
}

function resultPayload() {
  return {
    topics: topics.map(topic => ({
      topicId: topic.id,
      result: resultCode(topic),
      confirmed: true,
      summaryDraft: (topic.summaryDraft || '').trim(),
      segmentIndexes: [],
      forVotes: topic.voteRequired ? Number(topic.voteFor) || 0 : null,
      agVotes: topic.voteRequired ? Number(topic.voteAgainst) || 0 : null,
      abVotes: topic.voteRequired ? Number(topic.voteAbstain) || 0 : null,
      totalVotes: topic.voteRequired
        ? (topic.decisionType === 'multi_choice'
          ? topic.options.reduce((sum, option) => sum + (Number(option.votes) || 0), 0)
          : (Number(topic.voteFor) || 0) + (Number(topic.voteAgainst) || 0) + (Number(topic.voteAbstain) || 0))
        : null,
      optionVotes: topic.decisionType === 'multi_choice'
        ? Object.fromEntries(topic.options.map(option => [option.id, Number(option.votes) || 0]))
        : null
    })),
    ignoredSegmentIndexes: []
  }
}

async function saveResults() {
  const invalidVote = topics.find(topic => {
    if (!topic.voteRequired) return false
    const total = topic.decisionType === 'multi_choice'
      ? topic.options.reduce((sum, option) => sum + (Number(option.votes) || 0), 0)
      : Number(topic.voteFor) + Number(topic.voteAgainst) + Number(topic.voteAbstain)
    return total !== presentCount.value
  })
  if (invalidVote) {
    toast({ title: '“' + invalidVote.title + '”票数合计应等于实到人数', icon: 'none' })
    return
  }
  busy.value = true
  try {
    await api.committeeQuickConfirm(props.meetingId, resultPayload())
    step.value = 3
    toast({ title: '会议结果卡已生成', icon: 'success' })
  } catch (e) {
    toast({ title: e.message || '会议结果保存失败', icon: 'none' })
  } finally { busy.value = false }
}

async function toggleVoice(topic) {
  if (voiceTopicId.value === topic.id) {
    if (voiceRecorder && voiceRecorder.state !== 'inactive') voiceRecorder.stop()
    return
  }
  if (!navigator.mediaDevices || !window.MediaRecorder) {
    toast({ title: '当前环境不支持语音输入，请手动填写', icon: 'none' })
    return
  }
  try {
    voiceStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    voiceChunks = []
    voiceRecorder = new MediaRecorder(voiceStream)
    voiceRecorder.ondataavailable = event => { if (event.data && event.data.size) voiceChunks.push(event.data) }
    voiceRecorder.onstop = async () => {
      voiceTopicId.value = null
      voiceBusy.value = true
      try {
        const blob = new Blob(voiceChunks, { type: voiceRecorder.mimeType || 'audio/webm' })
        const file = new File([blob], 'online-result.webm', { type: blob.type })
        const res = await api.committeeVoiceToText(props.meetingId, file)
        const text = ((res && res.text) || '').trim()
        if (text) topic.summaryDraft = topic.summaryDraft ? topic.summaryDraft + '\n' + text : text
        else toast({ title: '没有识别到内容', icon: 'none' })
      } catch (e) {
        toast({ title: e.message || '语音识别失败，请手动填写', icon: 'none' })
      } finally {
        voiceBusy.value = false
        stopVoiceStream()
      }
    }
    voiceRecorder.start()
    voiceTopicId.value = topic.id
  } catch (e) {
    stopVoiceStream()
    toast({ title: '无法使用麦克风，请检查权限', icon: 'none' })
  }
}

function stopVoiceStream() {
  if (voiceStream) voiceStream.getTracks().forEach(track => track.stop())
  voiceStream = null
}

function resultLink() {
  return location.origin + '/api/share/committee-result/' + encodeURIComponent(props.meetingId)
}

async function copyResultLink() {
  try {
    await navigator.clipboard.writeText(resultLink())
    toast({ title: '链接已复制，可转发到微信工作群', icon: 'success' })
  } catch (e) {
    const input = document.createElement('textarea')
    input.value = resultLink()
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    input.remove()
    toast({ title: '链接已复制', icon: 'success' })
  }
}

async function shareResultCard() {
  const title = (props.detail.title || '线上会议') + '结果确认'
  const text = '会议结果已生成，请实际参会委员点击卡片确认。'
  if (navigator.share) {
    try {
      await navigator.share({ title, text, url: resultLink() })
      return
    } catch (e) {
      if (e && e.name === 'AbortError') return
    }
  }
  try {
    await navigator.clipboard.writeText(title + '\n' + text + '\n' + resultLink())
  } catch (e) {
    const input = document.createElement('textarea')
    input.value = title + '\n' + text + '\n' + resultLink()
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    input.remove()
  }
  toast({ title: '卡片内容已复制，请粘贴到微信工作群', icon: 'success' })
}

async function confirmResultCard() {
  busy.value = true
  try {
    await api.committeeSelfToggle(props.meetingId, 'signed')
    await emitReload()
    toast({ title: '会议结果已确认', icon: 'success' })
  } catch (e) {
    toast({ title: e.message || '确认失败', icon: 'none' })
  } finally { busy.value = false }
}

async function endMeeting() {
  if (meetingEnded.value) {
    location.href = '/committee-detail?id=' + encodeURIComponent(props.meetingId)
    return
  }
  const res = await showModal({
    title: '结束线上会议',
    content: signedCount.value < presentCount.value
      ? '仍有委员尚未确认结果。结束后结果卡仍可继续确认，确定结束吗？'
      : '参会人员和议题结果已保存，确定结束会议吗？',
    confirmText: '结束会议',
    cancelText: '再检查一下'
  })
  if (!res.confirm) return
  busy.value = true
  try {
    await api.committeeAdvance(props.meetingId, 'end')
    location.href = '/committee-detail?id=' + encodeURIComponent(props.meetingId) + '&from=online-meeting'
  } catch (e) {
    toast({ title: e.message || '结束会议失败', icon: 'none' })
  } finally { busy.value = false }
}

async function emitReload() {
  emit('reload')
  await new Promise(resolve => setTimeout(resolve, 250))
}

let progressTimer = null
async function refreshConfirmProgress() {
  // 第1步(主持人看签到进度)与第3步(确认进度)都轮询;第2步填写中不打扰
  if (!props.meetingId || (!cardMode.value && step.value !== 3 && step.value !== 1)) return
  try {
    const latest = await api.committeeDetail(props.meetingId)
    liveAttendance.value = ((latest.record && latest.record.attendances) || []).slice()
  } catch (e) {
    // 进度轮询失败不打断用户当前操作，下次自动重试。
  }
}

onMounted(() => {
  progressTimer = window.setInterval(refreshConfirmProgress, 6000)
})

onBeforeUnmount(() => {
  if (progressTimer) window.clearInterval(progressTimer)
  if (voiceRecorder && voiceRecorder.state !== 'inactive') voiceRecorder.stop()
  stopVoiceStream()
})
</script>

<style scoped>
.omf{padding:24rpx 8rpx 60rpx;color:#243746}.omf-head{display:flex;justify-content:space-between;align-items:flex-start;margin:12rpx 8rpx 26rpx}.omf-kicker{font-size:27rpx;color:#62788a}.omf-head h2{margin:8rpx 0 0;font-size:38rpx}.omf-platform{padding:8rpx 16rpx;border-radius:999rpx;background:#edf4f8;color:#52748d;font-size:22rpx}.omf-steps{display:flex;align-items:center;margin:0 8rpx 24rpx}.omf-steps span{font-size:23rpx;color:#96a3ad;white-space:nowrap}.omf-steps span.on{color:#315f7d;font-weight:700}.omf-steps span.done{color:#5d8b72}.omf-steps i{height:2rpx;flex:1;margin:0 12rpx;background:#dce4e9}.omf-card{padding:30rpx 28rpx;border:2rpx solid #e0e7eb;border-radius:22rpx;background:#fff;box-shadow:0 8rpx 28rpx rgba(45,66,80,.07)}.omf-card h3{margin:0;font-size:31rpx}.omf-desc{margin:12rpx 0 24rpx;color:#71808b;font-size:24rpx;line-height:1.65}.member-row{height:86rpx;display:flex;align-items:center;border-bottom:2rpx solid #eef2f4}.member-row input{display:none}.member-check{width:38rpx;height:38rpx;margin-right:18rpx;border:2rpx solid #cbd6dd;border-radius:10rpx;color:transparent;text-align:center;line-height:36rpx}.member-check.checked{background:#5f879f;border-color:#5f879f;color:#fff}.member-name{font-size:28rpx}.member-role{margin-left:auto;color:#84929b;font-size:23rpx}.member-state{margin-left:16rpx;padding:3rpx 14rpx;border-radius:999rpx;background:#f0f2f4;color:#8a95a0;font-size:22rpx;font-weight:600}.member-state.on{background:#e4f2e9;color:#43815b}.signin-card{text-align:center}.signin-card .omf-desc{margin:14rpx 0 6rpx}.chair-self-sign{margin-top:18rpx}.omf-primary,.omf-secondary,.omf-end{border:0;border-radius:14rpx;height:76rpx;font-size:27rpx}.omf-primary{width:100%;margin-top:28rpx;background:#416f8b;color:#fff}.omf-primary:disabled{opacity:.45}.omf-secondary{background:#edf2f5;color:#5b6d78}.omf-actions{display:flex;gap:16rpx}.omf-actions .omf-secondary{width:30%;margin-top:28rpx}.omf-actions .omf-primary{width:70%}.topic-form{padding:24rpx 0;border-top:2rpx solid #edf1f3}.topic-form:first-of-type{border-top:0}.topic-form-head{display:flex;gap:14rpx;align-items:flex-start}.topic-no{width:38rpx;height:38rpx;border-radius:50%;background:#e7f0f5;color:#416f8b;text-align:center;line-height:38rpx}.topic-form-head b{display:block;font-size:27rpx}.topic-form-head small{display:block;margin-top:6rpx;color:#8a98a1}.topic-input-wrap{position:relative;margin-top:18rpx}.topic-input-wrap textarea{box-sizing:border-box;width:100%;padding:18rpx 18rpx 86rpx;border:2rpx solid #d9e2e7;border-radius:14rpx;font-size:26rpx;line-height:1.55;resize:none}.voice-fill{position:absolute;right:12rpx;bottom:12rpx;min-width:176rpx;height:62rpx;padding:0 24rpx;border:2rpx solid #bdd0dc;border-radius:14rpx;background:#dfeef6;color:#315f7a;font-size:25rpx;font-weight:600;box-shadow:0 4rpx 12rpx rgba(62,103,128,.13)}.voice-fill-icon{margin-right:8rpx}.voice-fill.recording{background:#f6dfdf;border-color:#e3bcbc;color:#a74343}.vote-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12rpx;margin-top:16rpx}.vote-grid label{display:flex;align-items:center;padding:10rpx 12rpx;border-radius:12rpx;background:#f5f7f8}.vote-grid span{font-size:22rpx;color:#687984}.vote-grid input{width:50rpx;margin-left:auto;border:0;background:transparent;text-align:center;font-size:27rpx}.share-card{text-align:center}.share-icon{width:76rpx;height:76rpx;margin:0 auto 18rpx;border-radius:50%;background:#e4f2e9;color:#4d8a65;font-size:40rpx;line-height:76rpx}.full{width:100%;margin-top:14rpx}.confirm-progress{display:flex;justify-content:space-between;margin:28rpx 0 16rpx;padding:20rpx;border-radius:14rpx;background:#f5f7f8}.omf-end{width:100%;background:#fff;border:2rpx solid #8b9ba5;color:#485e6b}.omf-link{margin-top:20rpx;border:0;background:transparent;color:#6b8291}.result-card{text-align:left}.result-seal{text-align:center;font-size:34rpx;font-weight:700;color:#274c63}.result-meta{text-align:center;margin:10rpx 0 26rpx;color:#798892;font-size:23rpx}.result-attendance{padding:18rpx;border-radius:12rpx;background:#f4f7f8}.result-attendance b{display:block;margin-bottom:8rpx}.result-topic{padding:22rpx 0;border-bottom:2rpx solid #edf1f3}.result-topic-title{font-weight:700}.result-topic-text{margin-top:10rpx;color:#526570;line-height:1.65;white-space:pre-wrap}.result-votes{margin-top:10rpx;color:#49718a}.result-note{margin:24rpx 0 0;color:#75858f;font-size:23rpx}.result-confirmed{margin-top:26rpx;padding:20rpx;border-radius:14rpx;background:#e9f5ed;color:#43815b;text-align:center}
.option-vote-list{display:flex;flex-direction:column;gap:10rpx;margin-top:16rpx}.option-vote-list label{display:flex;align-items:center;padding:14rpx;border-radius:12rpx;background:#f5f7f8}.option-vote-list span{flex:1;font-size:24rpx}.option-vote-list input{width:74rpx;border:0;background:#fff;text-align:center;font-size:27rpx}.option-vote-list em{margin-left:8rpx;color:#7a8992;font-style:normal;font-size:22rpx}
.topic-heading{display:flex;align-items:center;gap:12rpx;min-width:0}.topic-heading b{min-width:0}.topic-kind{flex:none;padding:4rpx 12rpx;border-radius:999rpx;font-size:20rpx;font-weight:600;line-height:1.4}.topic-kind.vote{background:#f7eadf;color:#9a5d2e}.topic-kind.notice{background:#eee8f6;color:#715692}.topic-kind.discussion{background:#e7f0f6;color:#426f8c}
.result-card .omf-primary{display:block;width:70%;margin-left:auto;margin-right:auto}.result-edit-btn{display:block;width:70%;height:76rpx;margin:16rpx auto 0;border:2rpx solid #b9c8d1;border-radius:14rpx;background:#fff;color:#496474;font-size:27rpx}
.omf-card .attendance-title{font-size:35rpx}
.wechat-share-preview{display:flex;box-sizing:border-box;width:100%;margin:22rpx 0 0;padding:18rpx;border:2rpx solid #dce3e7;border-radius:16rpx;background:#fff;text-align:left;box-shadow:0 5rpx 18rpx rgba(45,64,76,.08)}.wsp-thumb{position:relative;flex:0 0 126rpx;width:126rpx;height:104rpx;overflow:hidden;border-radius:12rpx;background:linear-gradient(145deg,#326b89,#79a8bd);color:#fff}.wsp-emblem{position:absolute;left:16rpx;top:14rpx;width:42rpx;height:42rpx;border:2rpx solid rgba(255,255,255,.75);border-radius:50%;font-size:24rpx;line-height:42rpx;text-align:center}.wsp-lines{position:absolute;left:16rpx;right:16rpx;bottom:17rpx;height:18rpx;border-top:3rpx solid rgba(255,255,255,.72);border-bottom:3rpx solid rgba(255,255,255,.5)}.wsp-content{display:flex;flex:1;min-width:0;padding-left:18rpx;flex-direction:column}.wsp-content b{overflow:hidden;color:#273a46;font-size:26rpx;line-height:1.35;text-overflow:ellipsis;white-space:nowrap}.wsp-content span{display:-webkit-box;overflow:hidden;margin-top:7rpx;color:#74828b;font-size:21rpx;line-height:1.35;-webkit-box-orient:vertical;-webkit-line-clamp:2}.wsp-content small{margin-top:auto;color:#a0a9af;font-size:19rpx}.wechat-share-btn{display:flex;align-items:center;justify-content:center;gap:10rpx}.wechat-mark{width:34rpx;height:34rpx;border-radius:9rpx;background:#fff;color:#416f8b;font-size:19rpx;line-height:34rpx}
.share-instruction{margin:14rpx 0 0;color:#263746;font-size:25rpx;line-height:1.6}
</style>
