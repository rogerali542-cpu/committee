<template>
  <div class="omf">
    <div class="omf-head">
      <div>
        <span class="omf-kicker">线上会议</span>
        <h2>{{ detail.title || '本次会议' }}</h2>
      </div>
    </div>

    <!-- 结果确认卡:仅供分享链接(?card=1)打开时确认/查看,常规流程已不经过这里 -->
    <template v-if="cardMode">
      <section class="omf-card result-card">
        <div class="result-seal">会议结果确认卡</div>
        <div class="result-meta">{{ detail.meetingDate }} {{ detail.meetingTime }} · 线上召开</div>
        <div class="result-attendance">
          <b>实际参会</b>
          <span>{{ presentNames || '尚未登记' }}</span>
        </div>
        <div v-for="(topic, index) in topics" :key="topic.id" class="result-topic">
          <div class="result-topic-title">{{ index + 1 }}. {{ topic.title }}</div>
          <div v-if="topic.summaryDraft" class="result-topic-text">{{ topic.summaryDraft }}</div>
          <div v-if="topic.voteRequired && topic.decisionType === 'multi_choice'" class="result-votes">
            {{ topic.options.map(option => option.label + ' ' + (option.votes || 0) + '票').join(' · ') }}
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
      </section>
    </template>

    <!-- ① 签到页:未签到时整页只有签到,签到后才进入签到情况+表决 -->
    <template v-else-if="!meetingEnded && !selfPresent">
      <section class="omf-card signin-card">
        <div class="signin-badge">签</div>
        <h3>会议签到</h3>
        <p class="signin-meta">{{ detail.meetingDate }} {{ detail.meetingTime }} · 线上召开</p>
        <p class="omf-desc">本次会议在微信工作群中进行。请先签到确认参会，签到后进入议题表决页面。</p>
        <button class="omf-primary" :disabled="busy" @click="selfSignIn">{{ busy ? '正在签到…' : '我已参会，线上签到' }}</button>
        <div class="signin-count">已有 {{ presentCount }}/{{ attendance.length }} 位委员签到</div>
      </section>
    </template>

    <template v-else>
      <!-- 签到情况:所有人可见,只读(0725 用户定:主持人也只管自己签到、查看别人) -->
      <section class="omf-card">
        <h3 class="attendance-title">签到情况<small class="att-count">{{ presentCount }}/{{ attendance.length }} 人</small></h3>
        <div v-for="member in attendance" :key="member.userRoleId" class="member-row">
          <span class="member-name">{{ member.name }}</span>
          <span class="member-role">{{ member.role }}</span>
          <span class="member-state" :class="{ on: member.signedIn }">{{ member.signedIn ? '已签到' : '未签到' }}</span>
        </div>
      </section>

      <!-- ② 表决与讨论(0725 用户定):讨论在微信群进行,表决各自在此投票;主持人逐题结束表决揭晓 -->
      <section class="omf-card">
        <h3>议题表决与讨论</h3>
        <p class="omf-desc">议题讨论请在微信工作群进行；表决议题请各位委员在下方各自投票。</p>
        <div v-for="(topic, index) in topics" :key="topic.id" class="topic-block">
          <div class="topic-form-head">
            <span class="topic-no">{{ index + 1 }}</span>
            <div class="topic-heading">
              <b>{{ topic.title }}</b>
              <span class="topic-kind" :class="topic.voteRequired ? 'vote' : 'discussion'">
                {{ topic.voteRequired ? '表决' : (topic.type === 'notice' ? '通知' : '讨论') }}
              </span>
            </div>
          </div>

          <!-- 非表决题:群里讨论即可 -->
          <div v-if="!topic.voteRequired" class="topic-discuss-hint">本议题在微信群中讨论，无需在此操作</div>

          <!-- 表决题·进行中:本人投票(可改票);主持人另有「结束表决」 -->
          <template v-else-if="!topic.voteClosed">
            <div v-if="topic.decisionType === 'multi_choice'" class="vote-options">
              <button v-for="option in topic.options" :key="option.id" type="button" class="vote-opt"
                      :class="{ on: topic.mySelectedId === option.id }" :disabled="busy"
                      @click="castVote(topic, null, option.id)">{{ option.label }}</button>
            </div>
            <div v-else class="vote-choice-row">
              <button type="button" class="vote-opt yes" :class="{ on: topic.myVote === 'for_vote' }" :disabled="busy" @click="castVote(topic, 'for_vote')">赞成</button>
              <button type="button" class="vote-opt no" :class="{ on: topic.myVote === 'against' }" :disabled="busy" @click="castVote(topic, 'against')">反对</button>
              <button type="button" class="vote-opt ab" :class="{ on: topic.myVote === 'abstain' }" :disabled="busy" @click="castVote(topic, 'abstain')">弃权</button>
            </div>
            <div class="vote-progress">
              <span>已表决 {{ topic.voted || 0 }}/{{ presentCount }} 人</span>
              <span v-if="topic.myVote || topic.mySelectedId" class="voted-tag">已投，可改票</span>
            </div>
            <button v-if="isChair" type="button" class="close-vote-btn" :disabled="busy" @click="closeVoteTopic(topic)">
              结束本题表决，揭晓结果
            </button>
          </template>

          <!-- 表决题·已揭晓:公布票数与结果 -->
          <template v-else>
            <div v-if="topic.decisionType === 'multi_choice'" class="result-votes">
              {{ topic.options.map(option => option.label + ' ' + (option.votes || 0) + '票').join(' · ') }}
            </div>
            <div v-else class="result-votes">
              赞成 {{ topic.voteFor }} · 反对 {{ topic.voteAgainst }} · 弃权 {{ topic.voteAbstain }}
            </div>
            <div class="vote-closed-tag" :class="{ pass: topic.passed }">{{ topic.passed ? '已通过' : '未通过' }} · 表决已结束</div>
          </template>
        </div>

        <!-- 主持人:全部表决题揭晓后可结束会议,进入材料整理 -->
        <template v-if="isChair && !meetingEnded">
          <button class="omf-primary end-to-review" :disabled="busy || !allVotesClosed" @click="endMeeting">
            {{ allVotesClosed ? '结束会议，进入材料整理' : '请先逐题结束表决' }}
          </button>
        </template>
        <div v-else-if="!meetingEnded" class="member-wait-hint">表决完成后请回微信群继续讨论，等待主持人结束会议</div>
        <button v-else class="omf-primary" @click="endMeeting">查看会议详情</button>
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
const cardMode = ref(typeof location !== 'undefined' && new URLSearchParams(location.search).get('card') === '1')
const topics = reactive([])
const liveAttendance = ref([])

const attendance = computed(() => liveAttendance.value.length
  ? liveAttendance.value
  : ((props.detail.record && props.detail.record.attendances) || []))
const present = computed(() => attendance.value.filter(item => item.signedIn))
const presentCount = computed(() => present.value.length)
const presentNames = computed(() => present.value.map(item => item.name).join('、'))
const selfAttendance = computed(() => attendance.value.find(item => item.isSelf) || null)
const selfPresent = computed(() => !!(selfAttendance.value && selfAttendance.value.signedIn))
const selfSigned = computed(() => !!(selfAttendance.value && selfAttendance.value.signed))
const meetingEnded = computed(() => props.detail.stage !== 'ongoing')
const allVotesClosed = computed(() => topics.filter(t => t.voteRequired).every(t => t.voteClosed))

function initFromDetail() {
  liveAttendance.value = ((props.detail.record && props.detail.record.attendances) || []).slice()
  applyTopics((props.detail.record && props.detail.record.topics) || [])
}
function applyTopics(raw) {
  topics.splice(0, topics.length, ...raw.map(item => ({
    id: item.id,
    title: item.title,
    type: item.type,
    voteRequired: !!item.voteRequired,
    decisionType: item.decisionType || 'simple',
    voteClosed: !!item.voteClosed,
    passed: !!item.passed,
    voted: Number(item.voted) || 0,
    myVote: item.myVote || '',
    mySelectedId: item.mySelectedId != null ? Number(item.mySelectedId) : null,
    options: (item.options || []).map(option => ({
      id: option.id, label: option.label, votes: Number(option.votes) || 0
    })),
    summaryDraft: item.summaryDraft || item.summary || '',
    voteFor: Number(item.forVotes) || 0,
    voteAgainst: Number(item.agVotes) || 0,
    voteAbstain: Number(item.abVotes) || 0
  })))
}
watch(() => props.detail, initFromDetail, { immediate: true })

// 各自签到(0725 用户定):线上会议一律记远程参会
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

// 本人投票/改票(表决未揭晓前可改;后端 vote 为覆盖式更新)
async function castVote(topic, choice, selectedId) {
  busy.value = true
  try {
    await api.committeeVote(props.meetingId, topic.id, choice, selectedId)
    await refreshLive()
  } catch (e) {
    toast({ title: e.message || '投票失败', icon: 'none' })
  } finally { busy.value = false }
}

// 主持人逐题结束表决:揭晓票数与结果
async function closeVoteTopic(topic) {
  const res = await showModal({
    title: '结束本题表决',
    content: '「' + topic.title + '」已表决 ' + (topic.voted || 0) + '/' + presentCount.value + ' 人。结束后将揭晓票数，未投的委员不能再投。确定结束？',
    confirmText: '结束表决', cancelText: '再等等'
  })
  if (!res.confirm) return
  busy.value = true
  try {
    await api.committeeCloseVote(props.meetingId, topic.id)
    await refreshLive()
  } catch (e) {
    toast({ title: e.message || '操作失败', icon: 'none' })
  } finally { busy.value = false }
}

function resultCode(topic) {
  if (!topic.voteRequired) return 'recorded'
  if (topic.decisionType === 'multi_choice') {
    const leading = Math.max(0, ...topic.options.map(option => Number(option.votes) || 0))
    return leading >= Math.floor(presentCount.value / 2) + 1 ? 'passed' : 'rejected'
  }
  return Number(topic.voteFor) >= Math.floor(presentCount.value / 2) + 1 ? 'passed' : 'rejected'
}

function resultPayload() {
  return {
    topics: topics.map(topic => ({
      topicId: topic.id,
      result: topic.voteRequired ? (topic.passed ? 'passed' : resultCode(topic)) : 'recorded',
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

// 结束会议→材料整理(0725 用户定):表决结果按各自投票自动确认落库,随后进入会议详情整理材料/生成纪要
async function endMeeting() {
  if (meetingEnded.value) {
    location.href = '/committee-detail?id=' + encodeURIComponent(props.meetingId)
    return
  }
  const res = await showModal({
    title: '结束线上会议',
    content: '各议题表决结果将按各位委员的投票自动记录，随后进入材料整理。确定结束会议吗？',
    confirmText: '结束会议',
    cancelText: '再检查一下'
  })
  if (!res.confirm) return
  busy.value = true
  try {
    await api.committeeQuickConfirm(props.meetingId, resultPayload())
    await api.committeeAdvance(props.meetingId, 'end')
    location.href = '/committee-detail?id=' + encodeURIComponent(props.meetingId) + '&from=online-meeting'
  } catch (e) {
    toast({ title: e.message || '结束会议失败', icon: 'none' })
  } finally { busy.value = false }
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

async function emitReload() {
  emit('reload')
  await new Promise(resolve => setTimeout(resolve, 250))
}

// 会议进行中轮询:签到状态、各题票数/揭晓状态实时同步到所有人
let progressTimer = null
async function refreshLive() {
  if (!props.meetingId) return
  try {
    const latest = await api.committeeDetail(props.meetingId)
    liveAttendance.value = ((latest.record && latest.record.attendances) || []).slice()
    applyTopics((latest.record && latest.record.topics) || [])
  } catch (e) {
    // 轮询失败不打断当前操作，下次自动重试。
  }
}

onMounted(() => {
  progressTimer = window.setInterval(() => { if (!meetingEnded.value || cardMode.value) refreshLive() }, 5000)
})

onBeforeUnmount(() => {
  if (progressTimer) window.clearInterval(progressTimer)
})
</script>

<style scoped>
.omf{padding:24rpx 8rpx 60rpx;color:#243746}.omf-head{display:flex;justify-content:space-between;align-items:flex-start;margin:12rpx 8rpx 26rpx}.omf-kicker{font-size:27rpx;color:#62788a}.omf-head h2{margin:8rpx 0 0;font-size:38rpx}.omf-card{padding:30rpx 28rpx;border:2rpx solid #e0e7eb;border-radius:22rpx;background:#fff;box-shadow:0 8rpx 28rpx rgba(45,66,80,.07);margin-bottom:24rpx}.omf-card h3{margin:0;font-size:31rpx}.omf-desc{margin:12rpx 0 20rpx;color:#71808b;font-size:24rpx;line-height:1.65}
.member-row{height:86rpx;display:flex;align-items:center;border-bottom:2rpx solid #eef2f4}.member-row:last-child{border-bottom:0}.member-name{font-size:28rpx}.member-role{margin-left:auto;color:#84929b;font-size:23rpx}.member-state{margin-left:16rpx;padding:3rpx 14rpx;border-radius:999rpx;background:#f0f2f4;color:#8a95a0;font-size:22rpx;font-weight:600}.member-state.on{background:#e4f2e9;color:#43815b}
.signin-card{text-align:center;padding:56rpx 40rpx 48rpx;margin-top:20rpx}.signin-card h3{font-size:36rpx}.signin-card .omf-desc{margin:18rpx 0 8rpx}
.signin-badge{width:104rpx;height:104rpx;margin:0 auto 24rpx;border-radius:50%;background:#e7f0f5;color:#416f8b;font-size:46rpx;font-weight:700;line-height:104rpx}
.signin-meta{margin:10rpx 0 0;color:#798892;font-size:24rpx}
.signin-card .omf-primary{height:92rpx;font-size:31rpx;margin-top:34rpx;border-radius:18rpx}
.signin-count{margin-top:24rpx;color:#84929b;font-size:23rpx}
.attendance-title{display:flex;align-items:baseline;gap:14rpx}.att-count{color:#84929b;font-size:23rpx;font-weight:500}
.omf-primary{border:0;border-radius:14rpx;height:76rpx;font-size:27rpx;width:100%;margin-top:28rpx;background:#416f8b;color:#fff}.omf-primary:disabled{opacity:.45}
.topic-block{padding:24rpx 0;border-top:2rpx solid #edf1f3}.topic-block:first-of-type{border-top:0}
.topic-form-head{display:flex;gap:14rpx;align-items:flex-start}.topic-no{width:38rpx;height:38rpx;border-radius:50%;background:#e7f0f5;color:#416f8b;text-align:center;line-height:38rpx;flex:none}
.topic-heading{display:flex;align-items:center;gap:12rpx;min-width:0}.topic-heading b{min-width:0;font-size:27rpx}.topic-kind{flex:none;padding:4rpx 12rpx;border-radius:999rpx;font-size:20rpx;font-weight:600;line-height:1.4}.topic-kind.vote{background:#f7eadf;color:#9a5d2e}.topic-kind.discussion{background:#e7f0f6;color:#426f8c}
.topic-discuss-hint{margin:14rpx 0 0 52rpx;color:#84929b;font-size:23rpx}
.vote-choice-row{display:grid;grid-template-columns:repeat(3,1fr);gap:14rpx;margin:18rpx 0 0 52rpx}
.vote-options{display:flex;flex-direction:column;gap:12rpx;margin:18rpx 0 0 52rpx}
.vote-opt{height:70rpx;border:2rpx solid #cdd8df;border-radius:14rpx;background:#fff;color:#44586a;font-size:27rpx;font-weight:600}
.vote-opt.on{border-color:#416f8b;background:#e7f0f5;color:#28556f;font-weight:700}
.vote-opt.yes.on{border-color:#4d8a65;background:#e4f2e9;color:#35714d}
.vote-opt.no.on{border-color:#b45b4e;background:#f9e9e6;color:#984a3e}
.vote-opt:disabled{opacity:.6}
.vote-progress{display:flex;align-items:center;gap:16rpx;margin:14rpx 0 0 52rpx;color:#84929b;font-size:22rpx}
.voted-tag{color:#43815b}
.close-vote-btn{display:block;margin:16rpx 0 0 52rpx;height:60rpx;padding:0 24rpx;border:2rpx solid #b9c8d1;border-radius:12rpx;background:#fff;color:#496474;font-size:24rpx;font-weight:600}
.close-vote-btn:active{background:#eef3f6}.close-vote-btn:disabled{opacity:.5}
.result-votes{margin:14rpx 0 0 52rpx;color:#49718a;font-size:25rpx}
.vote-closed-tag{margin:10rpx 0 0 52rpx;display:inline-block;padding:3rpx 14rpx;border-radius:999rpx;background:#f0f2f4;color:#8a95a0;font-size:22rpx;font-weight:600}
.vote-closed-tag.pass{background:#e4f2e9;color:#43815b}
.end-to-review{margin-top:30rpx}
.member-wait-hint{margin-top:26rpx;text-align:center;color:#84929b;font-size:23rpx}
.result-card{text-align:left}.result-seal{text-align:center;font-size:34rpx;font-weight:700;color:#274c63}.result-meta{text-align:center;margin:10rpx 0 26rpx;color:#798892;font-size:23rpx}.result-attendance{padding:18rpx;border-radius:12rpx;background:#f4f7f8}.result-attendance b{display:block;margin-bottom:8rpx}.result-topic{padding:22rpx 0;border-bottom:2rpx solid #edf1f3}.result-topic-title{font-weight:700}.result-topic-text{margin-top:10rpx;color:#526570;line-height:1.65;white-space:pre-wrap}.result-note{margin:24rpx 0 0;color:#75858f;font-size:23rpx}.result-confirmed{margin-top:26rpx;padding:20rpx;border-radius:14rpx;background:#e9f5ed;color:#43815b;text-align:center}
.result-card .omf-primary{display:block;width:70%;margin-left:auto;margin-right:auto}
</style>
