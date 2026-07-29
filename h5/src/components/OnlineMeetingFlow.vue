<template>
  <div class="omf" :class="{ 'omf--has-footer': view === 'vote' && !cardMode }">
    <div class="omf-head">
      <div class="omf-head-main">
        <div class="omf-title-row">
          <h2>{{ detail.title || '本次会议' }}</h2>
          <span class="omf-method-tag">线上会议</span>
        </div>
        <!-- 签到名单不再展示(0725 用户定:简单点)——只报本人状态,进度看各题「已填 X/Y 人」 -->
        <!-- 已签到状态行:只在线上会议页显示(签到页卡内已有同信息,议题处理页 0725 用户定不显示) -->
        <div v-if="!cardMode && selfPresent && !meetingEnded && view === 'main'" class="signed-line">
          <span class="signed-tag">✓ 已签到</span>
          <span class="signed-count">{{ presentCount }}/{{ attendance.length }} 人已签到</span>
        </div>
      </div>
    </div>

    <!-- 流程链(0725 用户定):让所有人知道当前到第几步;样式对齐线下会议阶段条 -->
    <div v-if="!cardMode" class="omf-flow">
      <div class="omf-flow-step" :class="stepClass(1)">
        <span class="omf-flow-dot"><template v-if="flowStep > 1">✓</template><template v-else>1</template></span>
        <span class="omf-flow-label">会议签到</span>
      </div>
      <span class="omf-flow-line" :class="{ done: flowStep > 1 }"></span>
      <div class="omf-flow-step" :class="stepClass(2)">
        <span class="omf-flow-dot"><template v-if="flowStep > 2">✓</template><template v-else>2</template></span>
        <span class="omf-flow-label">线上会议</span>
      </div>
      <span class="omf-flow-line" :class="{ done: flowStep > 2 }"></span>
      <div class="omf-flow-step" :class="stepClass(3)">
        <span class="omf-flow-dot"><template v-if="flowStep > 3">✓</template><template v-else>3</template></span>
        <span class="omf-flow-label">议题处理</span>
      </div>
      <span class="omf-flow-line" :class="{ done: flowStep > 3 }"></span>
      <div class="omf-flow-step" :class="stepClass(4)">
        <span class="omf-flow-dot">4</span>
        <span class="omf-flow-label">材料整理</span>
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

    <!-- ① 签到页:未签到=签到按钮;已签到(返回键退回来看的)=已签到状态+进入会议 -->
    <template v-else-if="view === 'signin'">
      <section class="omf-card signin-card">
        <h3>会议签到</h3>
        <p class="signin-meta">{{ detail.meetingDate }} {{ String(detail.meetingTime || '').slice(0, 5) }} · 线上召开</p>
        <template v-if="!selfPresent">
          <p class="omf-desc">本次会议在微信工作群中进行。请先签到确认参会，再回微信群开会；开完会后回来填写表决结果和意见。</p>
          <button class="omf-primary" :disabled="busy" @click="selfSignIn">{{ busy ? '正在签到…' : '我已参会，线上签到' }}</button>
        </template>
        <template v-else>
          <p class="omf-desc">您已完成签到，签到不会因返回本页而取消。</p>
          <button class="omf-primary" @click="view = 'main'">进入会议</button>
        </template>
        <div class="signin-count">已有 {{ presentCount }}/{{ attendance.length }} 位委员签到</div>
      </section>
    </template>

    <!-- ② 线上会议页:微信群提示+名单+去表决入口(0725 用户定:表决登记放下一页) -->
    <template v-else-if="view === 'main'">
      <section v-if="!meetingEnded" class="omf-card wx-hint">
        <div class="wx-hint-title">会议在微信工作群进行</div>
        <p class="omf-desc">请回到微信群参加会议。<b>开完会后</b>回到本页，进入议题处理填写您的表决结果和意见。</p>
      </section>

      <!-- 参会名单:与线下会议签到页同款折叠条(0725 用户定:简化保留),默认收起 -->
      <div class="omf-roster">
        <div class="omf-roster-bar" @click="rosterOpen = !rosterOpen">
          <span class="omf-roster-title">参会名单</span>
          <span class="omf-roster-summary">已有{{ presentCount }}人签到</span>
          <span class="omf-roster-caret">{{ rosterOpen ? '收起 ▲' : '展开 ▾' }}</span>
        </div>
        <div v-if="rosterOpen" class="omf-roster-body">
          <div v-for="member in attendance" :key="member.userRoleId" class="omf-roster-row">
            <span class="orr-name">{{ member.name }}<small class="orr-role">{{ member.role }}</small></span>
            <span class="orr-state" :class="member.signedIn ? 'on' : 'wait'">{{ member.signedIn ? '已签到' : '未签到' }}</span>
          </div>
        </div>
      </div>

      <!-- 议题处理入口 -->
      <section class="omf-card vote-entry">
        <h3>议题处理</h3>
        <p class="omf-desc">{{ meetingEnded ? '会议已结束，表决结果已定稿。' : '开完会后，请各位委员在此填写自己的表决结果和意见。' }}</p>
        <button class="omf-primary" @click="view = 'vote'">{{ meetingEnded ? '查看表决结果' : '进入议题处理' }}</button>
      </section>
    </template>

    <!-- ③ 表决登记页:各委员填自己的表决结果+意见;主任结束会议时统一定稿 -->
    <template v-else>
      <section class="omf-card">
        <div class="core-head">
          <span class="core-title">{{ meetingEnded ? '议题表决结果' : '会议议题' }}</span>
          <span v-if="meetingTopics.length" class="core-count">共{{ meetingTopics.length }}项</span>
        </div>
        <!-- 0729 用户定:线上表决改用线下同款「议题列表 → 点去表决/去讨论/去通知 → TopicSheet」模式 -->
        <div v-if="meetingTopics.length" class="core-list">
          <div class="core-topic" v-for="(item, index) in meetingTopics" :key="'topic-' + item.id">
            <span class="core-topic-no">{{ index + 1 }}</span>
            <div class="core-topic-main">
              <span class="core-topic-title">{{ item.title }}</span>
              <div class="core-topic-tags">
                <span class="core-topic-type" :class="topicActionType(item)">{{ topicActionName(item) }}</span>
                <!-- 本人进度小标:列表上直接看出"我投过/发过言没",不用逐条点开确认 -->
                <span v-if="myMarkText(item)" class="core-topic-mine">✓ {{ myMarkText(item) }}</span>
              </div>
            </div>
            <button type="button" class="core-topic-btn" :class="[topicActionType(item), { done: topicRowDone(item) }]" @click="openTopicSheet(item)">
              {{ topicActionButton(item) }}
            </button>
          </div>
        </div>
        <div v-else class="core-empty">本次会议暂无议题</div>
      </section>

      <!-- 固定底栏(0729 用户定):页面出口=结束会议——会议到议题处理为止,材料整理是会后由主任/秘书
           少数人做的事。主任常驻实心主按钮(不再按"过完一遍"升降级:该状态存内存,刷新即丢,出口会凭空消失);
           点击后有确认弹窗兜底(未收齐票数会提示)。 -->
      <div class="omf-vote-footer">
        <button v-if="isChair && !meetingEnded" type="button" class="omf-primary end-to-review" :disabled="busy" @click="endMeeting">结束会议，进入材料整理</button>
        <div v-else-if="!meetingEnded" class="member-wait-hint">各议题填写完成即可；主任结束会议后，材料整理由主任、秘书处理</div>
        <button v-else type="button" class="omf-primary" @click="endMeeting">查看会议详情</button>
      </div>

      <!-- 议题弹层(与线下同款):点列表某条 → 表决/发言/通知都在此完成 -->
      <TopicSheet v-if="sheetTopic" :meeting-id="meetingId" :topic="sheetTopic"
                  :interactive="!meetingEnded" :signed-in="selfPresent" :is-chair="isChair"
                  :has-prev="sheetHasPrev" :has-next="sheetHasNext"
                  @close="sheetTopicId = null" @changed="emitReload" @prev="gotoPrevTopic" @next="gotoNextTopic" />
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/committee'
import { toast, showModal } from '@/utils/ui'
import TopicSheet from './TopicSheet.vue'

const props = defineProps({
  detail: { type: Object, required: true },
  meetingId: { type: [String, Number], required: true },
  isChair: { type: Boolean, default: false }
})
const emit = defineEmits(['reload', 'end-review'])

const busy = ref(false)
const rosterOpen = ref(false)

// 页内视图机(0725 用户定):签到页(signin)→线上会议页(main)→表决登记页(vote)。
// 返回键逐级回退(由宿主页 MeetingLiveQuick 调 handleBack);签到页再返回=离开会议。
const view = ref('')
function handleBack() {
  if (cardMode.value) return false
  if (view.value === 'vote') { view.value = 'main'; return true }
  if (view.value === 'main') { view.value = 'signin'; return true }
  return false
}
defineExpose({ handleBack })
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

// 流程链(0725 用户定):签到→微信群开会→表决登记→材料整理。
// ②→③没有系统信号(会在微信群里开),以"本人填过任一表决/意见"视为已回来登记。
const flowStep = computed(() => {
  if (meetingEnded.value) return 4
  if (!selfPresent.value) return 1
  if (view.value === 'vote') return 3   // 人已在议题处理页,步骤条直接亮第3步,不再等"填过才算"
  const filled = topics.some(t => t.voteRequired && (t.myVote || t.mySelectedId))
    || opinions.value.some(op => op.isSelf)
  return filled ? 3 : 2
})
function stepClass(n) {
  return flowStep.value > n ? 'done' : (flowStep.value === n ? 'on' : '')
}

// 议题意见:仅用于流程链"已回来登记"判定与列表「我已发言」小标;提交/删除/AI 助手都在 TopicSheet 内完成
const opinions = ref([])
async function loadOpinions() {
  try { opinions.value = (await api.committeeOpinions(props.meetingId)) || [] } catch (e) { /* 下次轮询重试 */ }
}

// ── 0729 用户定「学一学线下」:线上表决改用线下同款「议题列表 → 点去表决/去讨论 → TopicSheet」模式 ──
// 列表和弹层都用 detail.record.topics 原始数据(含 status/opinionCount 等 TopicSheet 需要的完整字段)
const meetingTopics = computed(() => (props.detail.record && props.detail.record.topics) || [])
const sheetTopicId = ref(null)
const sheetTopic = computed(() => meetingTopics.value.find(t => t.id === sheetTopicId.value) || null)
function _sheetIdx() { const list = meetingTopics.value; return { list, i: list.findIndex(t => t.id === sheetTopicId.value) } }
const sheetHasPrev = computed(() => _sheetIdx().i > 0)
const sheetHasNext = computed(() => { const { list, i } = _sheetIdx(); return i >= 0 && i < list.length - 1 })
function gotoPrevTopic() { const { list, i } = _sheetIdx(); if (i > 0) sheetTopicId.value = list[i - 1].id }
function gotoNextTopic() { const { list, i } = _sheetIdx(); if (i >= 0 && i < list.length - 1) sheetTopicId.value = list[i + 1].id }
function openTopicSheet(item) {
  if (!selfPresent.value) { toast({ title: '请先返回签到，签到后即可表决/发言', icon: 'none' }); return }
  sheetTopicId.value = item.id
}
// 行「完成感」:表决→表决已结束/会议已结束;通知→已通报或被提及;讨论→有意见
function topicRowBadgeDone(item) {
  if (item.voteRequired) return item.status === 'passed' || item.status === 'failed'
  if (item.type === 'notice') return !!item.notified || (item.opinionCount || 0) > 0
  return (item.opinionCount || 0) > 0
}
function topicActionType(item) { return item.voteRequired ? 'vote' : 'discuss' } // 通知也走 discuss 配色(与线下一致)
function topicActionName(item) { return item.type === 'notice' ? '通知' : (item.voteRequired ? '表决' : '讨论') }
// 讨论不存在「讨论完」（0729 用户定）：有意见≠讨论结束，会议结束才结束（同表决方案A）；通知仍按「已通报」
function topicRowDone(item) {
  if (item.voteRequired) return !!item.voteClosed || meetingEnded.value
  if (item.type === 'notice') return topicRowBadgeDone(item)
  return meetingEnded.value
}
function topicActionButton(item) {
  if (item.voteRequired) return topicRowDone(item) ? '看结果' : '去表决'
  if (item.type === 'notice') return topicRowBadgeDone(item) ? '已通报' : (props.isChair ? '去通知' : '查看通知')
  return meetingEnded.value ? '已讨论' : '去讨论'
}
// 本人进度小标(0729):表决题「我已表决」、讨论题「我已发言」;会议结束后不再显示(行进入结束态)
const myOpinionTopicIds = computed(() => new Set(opinions.value.filter(op => op.isSelf).map(op => Number(op.topicId))))
function myMarkText(item) {
  if (meetingEnded.value) return ''
  if (item.voteRequired) return (item.myVote || item.mySelectedId != null) ? '我已表决' : ''
  if (item.type === 'notice') return ''
  return myOpinionTopicIds.value.has(Number(item.id)) ? '我已发言' : ''
}

function initFromDetail() {
  liveAttendance.value = ((props.detail.record && props.detail.record.attendances) || []).slice()
  applyTopics((props.detail.record && props.detail.record.topics) || [])
  // 首次进入定初始视图:未签到落签到页,已签到直达线上会议页(签过到不重复走签到)
  if (!view.value) view.value = selfPresent.value || meetingEnded.value ? 'main' : 'signin'
}
function applyTopics(raw) {
  topics.splice(0, topics.length, ...raw.map(item => ({
    id: item.id,
    title: item.title,
    type: item.type,
    content: item.content || '',        // 通知类议题正文
    notified: !!item.notified,          // 是否已通知全体
    viewedByMe: !!item.viewedByMe,      // 本人是否已确认收到
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
    view.value = 'main'
    toast({ title: '签到成功', icon: 'success' })
  } catch (e) {
    toast({ title: e.message || '签到失败', icon: 'none' })
  } finally { busy.value = false }
}

// （旧页内翻页式的 表决/意见/通知/AI助手 代码已删：0729 改为「议题列表 → TopicSheet」后全部由弹层承接）

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

// 结束会议→材料整理(0725 用户定):主任结束时各题表决统一定稿(冻结+揭晓),
// 结果按各委员的真实填报自动落库,随后进入会议详情整理材料/生成纪要(同线下)
async function endMeeting() {
  if (meetingEnded.value) {
    location.href = '/committee-detail?id=' + encodeURIComponent(props.meetingId)
    return
  }
  const voteTopics = topics.filter(t => t.voteRequired)
  const incomplete = voteTopics.filter(t => (Number(t.voted) || 0) < presentCount.value)
  const warn = incomplete.length
    ? '还有 ' + incomplete.length + ' 题未收齐（未填的不计票）。'
    : ''
  const res = await showModal({
    title: '结束线上会议',
    content: warn + '结束后表决定稿、进入材料整理。确定结束？',
    confirmText: '结束会议',
    cancelText: '再等等'
  })
  if (!res.confirm) return
  busy.value = true
  try {
    // 逐题定稿:冻结投票并揭晓票数,再按最终票数落结果(会议保持 ongoing,以便会后整理页上传材料)
    for (const t of voteTopics) {
      if (!t.voteClosed) await api.committeeCloseVote(props.meetingId, t.id)
    }
    await refreshLive()
    await api.committeeQuickConfirm(props.meetingId, resultPayload())
    // 进入会后整理页(0725 用户定:线上也要拍照/上传材料/核对结果);advance('end') 推迟到"完成整理"时
    emit('end-review')
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
    await loadOpinions()
  } catch (e) {
    // 轮询失败不打断当前操作，下次自动重试。
  }
}

onMounted(() => {
  loadOpinions()
  progressTimer = window.setInterval(() => { if (!meetingEnded.value || cardMode.value) refreshLive() }, 5000)
})

onBeforeUnmount(() => {
  if (progressTimer) window.clearInterval(progressTimer)
})
</script>

<style scoped>
.omf{padding:24rpx 8rpx 60rpx;color:#243746}.omf-head{display:flex;justify-content:space-between;align-items:flex-start;margin:12rpx 8rpx 26rpx}.omf-head-main{min-width:0;flex:1}
.omf-title-row{display:flex;align-items:center;gap:16rpx}.omf-title-row h2{margin:0;font-size:38rpx;min-width:0}
.omf-method-tag{flex:none;padding:5rpx 16rpx;border-radius:999rpx;background:#EAF2FF;color:#2676D9;font-size:23rpx;font-weight:600}.omf-card{padding:30rpx 28rpx;border:2rpx solid #e0e7eb;border-radius:22rpx;background:#fff;box-shadow:0 8rpx 28rpx rgba(45,66,80,.07);margin-bottom:48rpx}.omf-card h3{margin:0;font-size:31rpx}.omf-desc{margin:12rpx 0 20rpx;color:#71808b;font-size:24rpx;line-height:1.65}
/* 流程链:样式对齐线下会议阶段条 .lp-flow(紧凑版尺寸) */
.omf-flow{display:flex;align-items:center;padding:16rpx 24rpx 52rpx;margin:12rpx 8rpx 20rpx}
.omf-flow-step{position:relative;flex-shrink:0}
.omf-flow-dot{width:44rpx;height:44rpx;border-radius:50%;background:#E4E6EA;color:#9AA0A6;font-size:23rpx;font-weight:700;display:flex;align-items:center;justify-content:center;transition:all .2s}
.omf-flow-label{position:absolute;top:calc(100% + 7rpx);left:50%;transform:translateX(-50%);font-size:22rpx;color:#9AA0A6;white-space:nowrap}
.omf-flow-step.on .omf-flow-dot{background:var(--c-primary-dark,#A85800);color:#fff;box-shadow:0 0 0 5rpx rgba(168,88,0,0.18)}
.omf-flow-step.on .omf-flow-label{color:var(--c-primary-dark,#A85800);font-weight:600}
.omf-flow-step.done .omf-flow-dot{background:#2E8B57;color:#fff}
.omf-flow-step.done .omf-flow-label{color:#2E8B57}
.omf-flow-line{flex:1;height:4rpx;background:#E4E6EA;margin:0 10rpx;border-radius:2rpx}
.omf-flow-line.done{background:#2E8B57}

/* 参会名单折叠条:配色/行样式对齐线下会议签到页 .si-roster / .signin-roster-row */
.omf-roster{background:#fff;border:2rpx solid #e0e7eb;border-radius:22rpx;box-shadow:0 8rpx 28rpx rgba(45,66,80,.07);padding:0 28rpx;margin-bottom:48rpx}
.omf-roster-bar{display:flex;align-items:center;gap:14rpx;padding:24rpx 0}
.omf-roster-title{font-size:29rpx;font-weight:700;color:#1f2329}
.omf-roster-summary{flex:1;margin-left:8rpx;color:#8A5A2B;font-size:24rpx;font-weight:550}
.omf-roster-caret{flex-shrink:0;color:#7A4C22;font-size:24rpx;font-weight:600}
.omf-roster-body{padding-bottom:10rpx;border-top:2rpx solid #F2F2F4}
.omf-roster-row{display:flex;align-items:center;justify-content:space-between;gap:18rpx;padding:16rpx 0;border-bottom:2rpx solid #F6F6F8}
.omf-roster-row:last-child{border-bottom:0}
.orr-name{font-size:28rpx;color:#1f2329;font-weight:600}.orr-role{margin-left:12rpx;color:#84929b;font-size:22rpx;font-weight:400}
.orr-state{font-size:24rpx;font-weight:600;padding:4rpx 16rpx;border-radius:12rpx;flex-shrink:0}
.orr-state.on{color:#2E8B57;background:#E8F7EE}.orr-state.wait{color:#6b7078;background:#EDEEF0}
.signed-line{display:flex;align-items:center;gap:16rpx;margin-top:14rpx}
.signed-tag{padding:4rpx 16rpx;border-radius:999rpx;background:#e4f2e9;color:#43815b;font-size:23rpx;font-weight:600}
.signed-count{color:#84929b;font-size:23rpx}
.signin-card{text-align:center;padding:56rpx 40rpx 48rpx;margin-top:20rpx}.signin-card h3{font-size:40rpx}
.signin-card .omf-desc{margin:20rpx 0 8rpx;font-size:28rpx;color:#5c6b78}
.signin-meta{margin:14rpx 0 0;color:#6b7a87;font-size:28rpx}
.signin-card .omf-primary{height:96rpx;font-size:33rpx;font-weight:600;margin-top:36rpx;border-radius:18rpx}
.signin-count{margin-top:26rpx;color:#7a8894;font-size:26rpx}
.omf-primary{border:0;border-radius:14rpx;height:76rpx;font-size:27rpx;width:100%;margin-top:28rpx;background:#416f8b;color:#fff}.omf-primary:disabled{opacity:.45}
.vote-entry .omf-primary{display:block;width:80%;margin-left:auto;margin-right:auto;font-size:29rpx;font-weight:500}
/* 固定底栏:与卡片内的 AI/提交意见拉开,避免误点 */
.omf--has-footer{padding-bottom:170rpx}
.omf-vote-footer{position:fixed;left:0;right:0;bottom:0;z-index:60;padding:18rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));background:rgba(255,255,255,.97);border-top:2rpx solid #eceef1;backdrop-filter:blur(8px)}
.omf-vote-footer .omf-primary,.omf-vote-footer .end-to-review{margin-top:0}
.omf-vote-footer .member-wait-hint{margin-top:0}
/* 0729 用户定「学一学线下」:议题列表(与线下 .core-* 同款) */
.core-head{display:flex;align-items:center;justify-content:space-between;gap:18rpx;margin-bottom:22rpx}
.core-title{font-size:34rpx;font-weight:700;color:#1F2024;line-height:1.35}
.core-count{flex:none;color:#8A8F98;font-size:25rpx}
.core-list{border:2rpx solid #EEF1F3;border-radius:20rpx;padding:4rpx 22rpx;background:#FAFBFC}
.core-topic{display:flex;align-items:center;gap:16rpx;padding:20rpx 0;border-top:2rpx solid rgba(31,36,42,0.06)}
.core-topic:first-child{border-top:0}
/* 序号改深蓝但不抢眼（0729 用户定）：浅蓝底 + 委员会深蓝字（slate #43546F）+ 淡蓝描边，替代原橙色 */
.core-topic-no{flex-shrink:0;width:44rpx;height:44rpx;border-radius:50%;background:#EEF2F8;color:#43546F;border:2rpx solid #D3DCEA;display:flex;align-items:center;justify-content:center;font-size:26rpx;font-weight:700}
.core-topic-main{flex:1;min-width:0;display:flex;flex-direction:column;gap:6rpx}
.core-topic-title{flex:1;min-width:0;color:#1F2024;font-size:31rpx;line-height:1.45;word-break:break-all;overflow:hidden;text-overflow:ellipsis;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical}
.core-topic-type{align-self:flex-start;font-size:25rpx;font-weight:700;border-radius:8rpx;padding:4rpx 13rpx;line-height:1.4}
.core-topic-type.notice{background:#E6F4FB;color:#1677B8}
.core-topic-type.vote{background:#FFF0E5;color:#D56A16}
.core-topic-type.discuss{background:#EAF6EE;color:#2E8B57}
.core-topic-tags{display:flex;align-items:center;gap:10rpx;flex-wrap:wrap}
/* 本人进度小标:绿底轻量,提示"这条我处理过了" */
.core-topic-mine{font-size:23rpx;font-weight:700;color:#2E7D32;background:#E8F6EC;border-radius:8rpx;padding:4rpx 12rpx;line-height:1.4}
.core-topic-btn{flex-shrink:0;min-width:128rpx;min-height:80rpx;display:inline-flex;align-items:center;justify-content:center;border-radius:999rpx;padding:8rpx 24rpx;font-size:28rpx;font-weight:800;border:0;color:#fff;font-family:inherit}
.core-topic-btn.notice{background:#1677B8}
.core-topic-btn.vote{background:#D56A16}
.core-topic-btn.discuss{background:#2E8B57}
.core-topic-btn.done{background:#E8F6EC;color:#2E7D32;border:2rpx solid #BFE0B2}
.core-empty{text-align:center;color:#8A8F98;font-size:30rpx;padding:36rpx 0}
.wx-hint{background:#f7f4ec;border-color:#e5dcc4}.wx-hint-title{font-size:30rpx;font-weight:700;color:#6d5a2e}.wx-hint .omf-desc{margin-bottom:0}.wx-hint b{color:#6d5a2e}
/* （旧页内表决/意见/通知 UI 的样式已随死代码一并清除,交互都在 TopicSheet 内） */
.result-votes{margin:14rpx 0 0 52rpx;color:#49718a;font-size:25rpx}
.end-to-review{margin-top:30rpx;background:#A85800}.end-to-review:active{background:#8F4A06}
.member-wait-hint{margin-top:26rpx;text-align:center;color:#84929b;font-size:23rpx}
.result-card{text-align:left}.result-seal{text-align:center;font-size:34rpx;font-weight:700;color:#274c63}.result-meta{text-align:center;margin:10rpx 0 26rpx;color:#798892;font-size:23rpx}.result-attendance{padding:18rpx;border-radius:12rpx;background:#f4f7f8}.result-attendance b{display:block;margin-bottom:8rpx}.result-topic{padding:22rpx 0;border-bottom:2rpx solid #edf1f3}.result-topic-title{font-weight:700}.result-topic-text{margin-top:10rpx;color:#526570;line-height:1.65;white-space:pre-wrap}.result-note{margin:24rpx 0 0;color:#75858f;font-size:23rpx}.result-confirmed{margin-top:26rpx;padding:20rpx;border-radius:14rpx;background:#e9f5ed;color:#43815b;text-align:center}
.result-card .omf-primary{display:block;width:70%;margin-left:auto;margin-right:auto}
</style>
