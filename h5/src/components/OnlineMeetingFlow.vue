<template>
  <div class="omf" :class="{ 'omf--has-footer': view === 'vote' && !cardMode }">
    <div class="omf-head">
      <div class="omf-head-main">
        <div class="omf-title-row">
          <h2>{{ detail.title || '本次会议' }}</h2>
          <span class="omf-method-tag">线上会议</span>
        </div>
        <!-- 签到名单不再展示(0725 用户定:简单点)——只报本人状态,进度看各题「已填 X/Y 人」 -->
        <!-- 已签到状态行:议题处理页不显示(0725 用户定) -->
        <div v-if="!cardMode && selfPresent && !meetingEnded && view !== 'vote'" class="signed-line">
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
        <div class="topic-head-row">
          <h3>{{ meetingEnded ? '议题表决结果' : '议题处理' }}</h3>
          <span v-if="topics.length" class="topic-pager-ind">议题 {{ topicIndex + 1 }}/{{ topics.length }}</span>
        </div>
        <!-- 每页只显示一个议题(0725 用户定),上一题/下一题翻页 -->
        <div v-for="topic in (currentTopic ? [currentTopic] : [])" :key="topic.id" class="topic-block">
          <div class="topic-form-head">
            <span class="topic-no">{{ topicIndex + 1 }}</span>
            <div class="topic-heading">
              <b>{{ topic.title }}</b>
              <span class="topic-kind" :class="topic.voteRequired ? 'vote' : 'discussion'">
                {{ topic.voteRequired ? '表决' : (topic.type === 'notice' ? '通知' : '讨论') }}
              </span>
            </div>
          </div>

          <!-- 表决题·未定稿:先选后交(与线下一致)——点选项仅选中,「确认提交」才落库;
               已投后收起成「已投:X + 改票/撤回」,改票需再点确认提交 -->
          <template v-if="topic.voteRequired && !topic.voteClosed">
            <template v-if="!voteCollapsed(topic)">
              <div v-if="topic.decisionType === 'multi_choice'" class="vote-options">
                <button v-for="option in topic.options" :key="option.id" type="button" class="vote-opt"
                        :class="{ on: isPicked(topic, option.id) }" :disabled="busy"
                        @click="pickVote(topic, null, option)">{{ option.label }}</button>
              </div>
              <div v-else class="vote-choice-row">
                <button type="button" class="vote-opt yes" :class="{ on: isPicked(topic, 'for_vote') }" :disabled="busy" @click="pickVote(topic, 'for_vote')">赞成</button>
                <button type="button" class="vote-opt no" :class="{ on: isPicked(topic, 'against') }" :disabled="busy" @click="pickVote(topic, 'against')">反对</button>
                <button type="button" class="vote-opt ab" :class="{ on: isPicked(topic, 'abstain') }" :disabled="busy" @click="pickVote(topic, 'abstain')">弃权</button>
              </div>
              <button v-if="pendingVotes[topic.id]" type="button" class="vote-submit" :disabled="busy" @click="submitVote(topic)">
                {{ busy ? '提交中…' : '确认提交' }}
              </button>
            </template>
            <div v-if="voteCollapsed(topic)" class="vote-progress">
              <span class="voted-tag">✓ 已投：{{ myVoteLabel(topic) }}</span>
              <button type="button" class="mini-act" :disabled="busy" @click="changeVoteOpen[topic.id] = true">改票</button>
              <button type="button" class="mini-act" :disabled="busy" @click="retractMyVote(topic)">撤回</button>
            </div>
          </template>

          <!-- 表决题·已定稿(会议结束):公布票数与结果 -->
          <template v-else-if="topic.voteRequired">
            <div v-if="topic.decisionType === 'multi_choice'" class="result-votes">
              {{ topic.options.map(option => option.label + ' ' + (option.votes || 0) + '票').join(' · ') }}
            </div>
            <div v-else class="result-votes">
              赞成 {{ topic.voteFor }} · 反对 {{ topic.voteAgainst }} · 弃权 {{ topic.voteAbstain }}
            </div>
            <div class="vote-closed-tag" :class="{ pass: topic.passed }">{{ topic.passed ? '已通过' : '未通过' }}</div>
          </template>

          <!-- 意见:所有议题都可补充书面意见,记入会议记录;列表收在「查看详情」里(0725 用户定) -->
          <div v-if="topicOpinions(topic.id).length" class="op-detail-row">
            <button type="button" class="op-detail-toggle" @click="opsOpen = !opsOpen">
              {{ opsOpen ? '收起意见 ▲' : '查看详情（' + topicOpinions(topic.id).length + ' 条意见）▾' }}
            </button>
          </div>
          <div v-if="opsOpen && topicOpinions(topic.id).length" class="topic-opinions">
            <div v-for="op in topicOpinions(topic.id)" :key="op.id" class="op-row">
              <b>{{ op.name }}</b>
              <span v-if="opVoteTag(op)" class="op-vote-tag" :class="opVoteClass(op)">{{ opVoteTag(op) }}</span>
              <span class="op-text">{{ op.content }}</span>
              <button v-if="op.canDelete && !meetingEnded" type="button" class="op-del" @click="removeOpinion(op)">删除</button>
            </div>
          </div>
          <!-- 表决题:先投票才能填意见(0725 用户定),意见带作者表决标签 -->
          <div v-if="!meetingEnded && topic.voteRequired && !hasMyVote(topic)" class="op-need-vote">
            请先完成上方表决，再补充意见
          </div>
          <div v-else-if="!meetingEnded" class="op-input">
            <textarea v-model="opinionDrafts[topic.id]" rows="2" placeholder="补充意见（可选）"></textarea>
            <div class="op-btn-row">
              <button v-if="String(opinionDrafts[topic.id] || '').trim()" type="button" class="op-ai-btn"
                      :disabled="aiBusyMap[topic.id]" @click="polishOpinion(topic)">{{ aiBusyMap[topic.id] ? 'AI 润色中…' : 'AI 润色' }}</button>
              <button v-else type="button" class="op-ai-btn"
                      :disabled="aiBusyMap[topic.id]" @click="helpWriteOpinion(topic)">{{ aiBusyMap[topic.id] ? 'AI 写作中…' : 'AI 帮写' }}</button>
              <button v-if="polishUndoMap[topic.id] != null" type="button" class="mini-act" @click="undoPolish(topic)">还原</button>
              <button type="button" class="op-submit" :disabled="busy" @click="submitOpinion(topic)">提交意见</button>
            </div>
          </div>
        </div>

        <div v-if="!topics.length" class="topic-empty">本次会议暂无议题</div>

        <!-- 翻页(0725 用户定:放回卡片内);没有对应方向的议题就隐藏按钮,不置灰占位;各占半宽 -->
        <div v-if="topics.length > 1" class="topic-pager">
          <button v-if="topicIndex > 0" type="button" class="pager-prev" @click="prevTopic">‹ 上一议题</button>
          <button v-if="topicIndex < topics.length - 1" type="button" class="pager-next" @click="nextTopic">下一议题 ›</button>
        </div>
      </section>

      <!-- 固定底栏(0725 用户定):结束会议与上方内容拉开,避免误点 -->
      <div class="omf-vote-footer">
        <!-- 主任:结束会议→表决定稿→进入材料整理;委员填完等待即可 -->
        <template v-if="isChair && !meetingEnded">
          <button class="omf-primary end-to-review" :disabled="busy" @click="endMeeting">结束会议，进入材料整理</button>
        </template>
        <div v-else-if="!meetingEnded" class="member-wait-hint">表决和意见填写完成后，等待主任结束会议、进入材料整理</div>
        <button v-else class="omf-primary" @click="endMeeting">查看会议详情</button>
      </div>
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
const rosterOpen = ref(false)

// ── 议题翻页(0725 用户定):每页只显示一个议题;意见列表收在「查看详情」 ──
const topicIndex = ref(0)
const opsOpen = ref(false)
function prevTopic() { if (topicIndex.value > 0) { topicIndex.value--; opsOpen.value = false } }
function nextTopic() { if (topicIndex.value < topics.length - 1) { topicIndex.value++; opsOpen.value = false } }

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
const currentTopic = computed(() => topics[topicIndex.value] || null)
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
  const filled = topics.some(t => t.voteRequired && (t.myVote || t.mySelectedId))
    || opinions.value.some(op => op.isSelf)
  return filled ? 3 : 2
})
function stepClass(n) {
  return flowStep.value > n ? 'done' : (flowStep.value === n ? 'on' : '')
}

// 议题意见:开会期间各委员可按议题补充书面意见,记入会议记录
const opinions = ref([])
const opinionDrafts = reactive({})
function topicOpinions(topicId) {
  return opinions.value.filter(op => Number(op.topicId) === Number(topicId))
}
async function loadOpinions() {
  try { opinions.value = (await api.committeeOpinions(props.meetingId)) || [] } catch (e) { /* 下次轮询重试 */ }
}
// 意见作者的表决标签(后端 opinionToMap 带出):simple 题给 voteChoice,多选题给所选项 voteLabel
function opVoteTag(op) {
  if (op.voteLabel) return op.voteLabel
  if (op.voteChoice === 'for_vote') return '赞成'
  if (op.voteChoice === 'against') return '反对'
  if (op.voteChoice === 'abstain') return '弃权'
  return ''
}
function opVoteClass(op) {
  if (op.voteChoice === 'for_vote') return 'yes'
  if (op.voteChoice === 'against') return 'no'
  return 'ab'
}
async function submitOpinion(topic) {
  // 表决题必须先投票(0725 用户定):意见须带作者表决标签
  if (topic.voteRequired && !hasMyVote(topic)) { toast({ title: '请先完成表决，再提交意见', icon: 'none' }); return }
  const text = String(opinionDrafts[topic.id] || '').trim()
  if (!text) { toast({ title: '请先填写意见内容', icon: 'none' }); return }
  busy.value = true
  try {
    await api.committeeAddOpinion(props.meetingId, topic.id, text, 'text')
    opinionDrafts[topic.id] = ''
    await loadOpinions()
    toast({ title: '意见已提交', icon: 'success' })
  } catch (e) {
    toast({ title: e.message || '提交失败', icon: 'none' })
  } finally { busy.value = false }
}
async function removeOpinion(op) {
  const res = await showModal({ title: '删除意见', content: '删除这条意见吗？', confirmText: '删除', cancelText: '取消' })
  if (!res.confirm) return
  try {
    await api.committeeRemoveOpinion(props.meetingId, op.id)
    await loadOpinions()
  } catch (e) { toast({ title: e.message || '删除失败', icon: 'none' }) }
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
  if (topicIndex.value >= topics.length) topicIndex.value = Math.max(0, topics.length - 1)
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

// ── 表决:先选后交(操作逻辑对齐线下 TopicSheet) ──
// 点选项只是选中(pendingVotes),「确认提交」才调后端;已投后收起,「改票」重新展开、仍需确认提交
const pendingVotes = reactive({})
const changeVoteOpen = reactive({})
function hasMyVote(topic) { return !!(topic.myVote || topic.mySelectedId != null) }
function voteCollapsed(topic) { return hasMyVote(topic) && !changeVoteOpen[topic.id] && !pendingVotes[topic.id] }
function myVoteLabel(topic) {
  if (topic.decisionType === 'multi_choice') {
    const opt = (topic.options || []).find(o => Number(o.id) === Number(topic.mySelectedId))
    return opt ? opt.label : ''
  }
  return topic.myVote === 'for_vote' ? '赞成' : topic.myVote === 'against' ? '反对' : topic.myVote === 'abstain' ? '弃权' : ''
}
function isPicked(topic, val) {
  const p = pendingVotes[topic.id]
  if (p) return String(p.selectedId != null ? p.selectedId : p.choice) === String(val)
  return topic.decisionType === 'multi_choice'
    ? String(topic.mySelectedId) === String(val)
    : topic.myVote === val
}
async function pickVote(topic, choice, option) {
  const val = option ? option.id : choice
  const label = option ? option.label : (choice === 'for_vote' ? '赞成' : choice === 'against' ? '反对' : '弃权')
  // 改票模式(0725 用户定):点选项直接弹确认,不走待提交按钮;点原选项/取消=不改,收起
  if (changeVoteOpen[topic.id] && hasMyVote(topic)) {
    const curLabel = myVoteLabel(topic)
    if (isPicked(topic, val)) { changeVoteOpen[topic.id] = false; return }
    const res = await showModal({
      title: '',
      content: '把您的表决从「' + curLabel + '」改为「' + label + '」吗？',
      contentBold: true,
      confirmText: '确认修改', cancelText: '不改了'
    })
    changeVoteOpen[topic.id] = false
    if (!res.confirm) return
    busy.value = true
    try {
      await api.committeeVote(props.meetingId, topic.id, option ? null : choice, option ? option.id : null)
      await refreshLive()
      toast({ title: '已改票', icon: 'success' })
    } catch (e) {
      toast({ title: e.message || '改票失败', icon: 'none' })
    } finally { busy.value = false }
    return
  }
  // 首次投票:先选后交(与线下一致),点「确认提交」才落库
  if (isPicked(topic, val) && !pendingVotes[topic.id]) return
  pendingVotes[topic.id] = option ? { selectedId: option.id, label } : { choice, label }
}
async function submitVote(topic) {
  const p = pendingVotes[topic.id]
  if (!p) return
  busy.value = true
  try {
    await api.committeeVote(props.meetingId, topic.id, p.choice || null, p.selectedId != null ? p.selectedId : null)
    delete pendingVotes[topic.id]
    changeVoteOpen[topic.id] = false
    await refreshLive()
    toast({ title: '表决已提交', icon: 'success' })
  } catch (e) {
    toast({ title: e.message || '投票失败', icon: 'none' })
  } finally { busy.value = false }
}
async function retractMyVote(topic) {
  const res = await showModal({
    title: '撤回投票',
    content: '撤回后本议题回到「未投」状态，您可以重新表决。确定撤回吗？',
    confirmText: '撤回', cancelText: '取消'
  })
  if (!res.confirm) return
  busy.value = true
  try {
    await api.committeeRetractVote(props.meetingId, topic.id)
    delete pendingVotes[topic.id]
    changeVoteOpen[topic.id] = false
    await refreshLive()
    toast({ title: '已撤回', icon: 'success' })
  } catch (e) {
    toast({ title: e.message || '撤回失败', icon: 'none' })
  } finally { busy.value = false }
}

// ── 意见 AI 助手(操作逻辑对齐线下):草稿为空=AI帮写(已表决按表态代拟),有草稿=AI润色(可还原) ──
const aiBusyMap = reactive({})
const polishUndoMap = reactive({})
function voteStanceSeed(topic) {
  if (!topic.voteRequired || !hasMyVote(topic)) return ''
  if (topic.decisionType === 'multi_choice') {
    const label = myVoteLabel(topic)
    return label ? ('我在这个议题上选择了「' + label + '」，请据此帮我写一段简短的表态发言。') : ''
  }
  if (topic.myVote === 'for_vote') return '我对这个议题投了赞成票，总体认同这个方案，支持通过。'
  if (topic.myVote === 'against') return '我对这个议题投了反对票，对这个方案还有顾虑，暂不赞成。'
  if (topic.myVote === 'abstain') return '我对这个议题投了弃权票，还想再多了解一些情况，暂不表态。'
  return ''
}
async function helpWriteOpinion(topic) {
  if (aiBusyMap[topic.id]) return
  const seed = voteStanceSeed(topic)
  if (!seed) {
    toast({ title: '先在框里写几个字（或先表决），AI 再帮您成文', icon: 'none' })
    return
  }
  aiBusyMap[topic.id] = true
  try {
    const res = await api.committeeOpinionAssist(props.meetingId, topic.id, 'draft', seed)
    if (!res || !res.text) { toast({ title: 'AI 没写出来，请重试', icon: 'none' }); return }
    opinionDrafts[topic.id] = res.text
    polishUndoMap[topic.id] = null
    showModal({ title: '', content: '已按您的表决态度拟好，可修改', size: 'aicard', showCancel: false, confirmText: '查看' })
  } catch (e) {
    toast({ title: e.message || 'AI 助手开小差了，请重试', icon: 'none' })
  } finally { aiBusyMap[topic.id] = false }
}
async function polishOpinion(topic) {
  const text = String(opinionDrafts[topic.id] || '').trim()
  if (!text || aiBusyMap[topic.id]) return
  aiBusyMap[topic.id] = true
  try {
    const res = await api.committeeOpinionAssist(props.meetingId, topic.id, 'polish', text)
    if (!res || !res.text) { toast({ title: 'AI 没写出来，请重试', icon: 'none' }); return }
    polishUndoMap[topic.id] = text
    opinionDrafts[topic.id] = res.text
    showModal({ title: '', content: '已润色，可继续修改', size: 'aicard', showCancel: false, confirmText: '查看' })
  } catch (e) {
    toast({ title: e.message || 'AI 助手开小差了，请重试', icon: 'none' })
  } finally { aiBusyMap[topic.id] = false }
}
function undoPolish(topic) {
  if (polishUndoMap[topic.id] == null) return
  opinionDrafts[topic.id] = polishUndoMap[topic.id]
  polishUndoMap[topic.id] = null
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
    ? '还有 ' + incomplete.length + ' 个表决题未收齐全部委员的填报（未填的不计入票数）。'
    : '各议题表决已收齐。'
  const res = await showModal({
    title: '结束线上会议',
    content: warn + '结束后表决即定稿、揭晓票数，随后进入材料整理。确定结束会议吗？',
    confirmText: '结束会议',
    cancelText: '再等等'
  })
  if (!res.confirm) return
  busy.value = true
  try {
    // 逐题定稿:冻结投票并揭晓票数,再按最终票数落结果
    for (const t of voteTopics) {
      if (!t.voteClosed) await api.committeeCloseVote(props.meetingId, t.id)
    }
    await refreshLive()
    await api.committeeQuickConfirm(props.meetingId, resultPayload())
    await api.committeeAdvance(props.meetingId, 'end')
    // replace:会议已结束,线上会议页不留在历史里,详情页返回=历史上一页时直接回来处
    location.replace('/committee-detail?id=' + encodeURIComponent(props.meetingId) + '&from=online-meeting')
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
.topic-head-row{display:flex;align-items:center;justify-content:space-between}
.topic-pager-ind{color:#84929b;font-size:24rpx}
/* 固定底栏:与卡片内的 AI/提交意见拉开,避免误点 */
.omf--has-footer{padding-bottom:170rpx}
.omf-vote-footer{position:fixed;left:0;right:0;bottom:0;z-index:60;padding:18rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));background:rgba(255,255,255,.97);border-top:2rpx solid #eceef1;backdrop-filter:blur(8px)}
.topic-pager{display:flex;gap:16rpx;margin-top:26rpx}
.topic-pager button{flex:0 0 calc(50% - 8rpx);height:72rpx;border:2rpx solid #cdd8df;border-radius:14rpx;background:#fff;color:#44586a;font-size:27rpx;font-weight:600}
.topic-pager button:active{background:#eef3f6}
.topic-pager .pager-next{margin-left:auto}
.omf-vote-footer .omf-primary,.omf-vote-footer .end-to-review{margin-top:0}
.omf-vote-footer .member-wait-hint{margin-top:0}
.topic-empty{padding:60rpx 0;text-align:center;color:#8a95a0;font-size:26rpx}
.op-detail-row{margin:18rpx 0 0 52rpx}
.op-detail-toggle{border:0;background:none;padding:0;color:#416f8b;font-size:24rpx;font-weight:600}
.topic-block{padding:24rpx 0;border-top:2rpx solid #edf1f3}.topic-block:first-of-type{border-top:0}
.topic-form-head{display:flex;gap:14rpx;align-items:flex-start}.topic-no{width:38rpx;height:38rpx;border-radius:50%;background:#e7f0f5;color:#416f8b;text-align:center;line-height:38rpx;flex:none}
.topic-heading{display:flex;align-items:center;gap:12rpx;min-width:0}.topic-heading b{min-width:0;font-size:27rpx}.topic-kind{flex:none;padding:4rpx 12rpx;border-radius:999rpx;font-size:20rpx;font-weight:600;line-height:1.4}.topic-kind.vote{background:#f7eadf;color:#9a5d2e}.topic-kind.discussion{background:#e7f0f6;color:#426f8c}
.wx-hint{background:#f7f4ec;border-color:#e5dcc4}.wx-hint-title{font-size:30rpx;font-weight:700;color:#6d5a2e}.wx-hint .omf-desc{margin-bottom:0}.wx-hint b{color:#6d5a2e}
.topic-opinions{margin:18rpx 0 0 52rpx;padding:16rpx 18rpx;border-radius:12rpx;background:#f6f8f9}
.op-row{display:flex;align-items:flex-start;gap:10rpx;padding:8rpx 0;font-size:24rpx;line-height:1.6;color:#44586a}
.op-row b{flex:none;font-weight:600}.op-row .op-text{min-width:0;white-space:pre-wrap}
.op-vote-tag{flex:none;margin-top:2rpx;padding:1rpx 12rpx;border-radius:999rpx;font-size:20rpx;font-weight:600;background:#eef1f4;color:#5a6b7a}
.op-vote-tag.yes{background:#e4f2e9;color:#35714d}
.op-vote-tag.no{background:#f9e9e6;color:#984a3e}
.op-vote-tag.ab{background:#eef1f4;color:#5a6b7a}
.op-del{flex:none;margin-left:auto;border:0;background:none;color:#a4756a;font-size:22rpx;padding:0 4rpx}
.vote-submit{display:block;width:calc(100% - 52rpx);margin:18rpx 0 0 52rpx;height:72rpx;border:0;border-radius:14rpx;background:#416f8b;color:#fff;font-size:27rpx;font-weight:600}
.vote-submit:disabled{opacity:.5}
.mini-act{border:2rpx solid #cdd8df;border-radius:10rpx;background:#fff;color:#496474;font-size:22rpx;padding:4rpx 16rpx;line-height:1.5}
.mini-act:active{background:#eef3f6}.mini-act:disabled{opacity:.5}
.op-btn-row{display:flex;align-items:center;gap:16rpx;margin-top:6rpx}
.op-ai-btn{height:72rpx;padding:0 40rpx;border:2rpx solid #e0b98a;border-radius:14rpx;background:#fdf6ec;color:#9a5d2e;font-size:27rpx;font-weight:600}
.op-ai-btn:active{background:#f7ecdc}.op-ai-btn:disabled{opacity:.6}
.op-need-vote{margin:16rpx 0 0 52rpx;padding:18rpx 22rpx;border-radius:12rpx;background:#f7f4ec;border:2rpx dashed #dfd2b4;color:#8a6d35;font-size:24rpx;line-height:1.6}
.op-input{display:flex;flex-direction:column;gap:12rpx;margin:16rpx 0 0 52rpx}
.op-input textarea{width:100%;box-sizing:border-box;border:2rpx solid #d8e0e5;border-radius:12rpx;padding:14rpx 16rpx;font-size:25rpx;line-height:1.6;color:#33475a;background:#fbfcfd;resize:none;font-family:inherit}
.op-submit{margin-left:auto;height:72rpx;padding:0 44rpx;border:2rpx solid #b9c8d1;border-radius:14rpx;background:#fff;color:#496474;font-size:27rpx;font-weight:600}
.op-submit:active{background:#eef3f6}.op-submit:disabled{opacity:.5}
.vote-choice-row{display:grid;grid-template-columns:repeat(3,1fr);gap:14rpx;margin:18rpx 0 0 52rpx}
.vote-options{display:flex;flex-direction:column;gap:12rpx;margin:18rpx 0 0 52rpx}
.vote-opt{height:70rpx;border:2rpx solid #cdd8df;border-radius:14rpx;background:#fff;color:#44586a;font-size:27rpx;font-weight:600}
.vote-opt.on{border-color:#416f8b;background:#e7f0f5;color:#28556f;font-weight:700}
.vote-opt.yes.on{border-color:#4d8a65;background:#e4f2e9;color:#35714d}
.vote-opt.no.on{border-color:#b45b4e;background:#f9e9e6;color:#984a3e}
.vote-opt:disabled{opacity:.6}
.vote-progress{display:flex;align-items:center;gap:16rpx;margin:14rpx 0 0 52rpx;color:#84929b;font-size:22rpx}
.voted-tag{color:#43815b}
.result-votes{margin:14rpx 0 0 52rpx;color:#49718a;font-size:25rpx}
.vote-closed-tag{margin:10rpx 0 0 52rpx;display:inline-block;padding:3rpx 14rpx;border-radius:999rpx;background:#f0f2f4;color:#8a95a0;font-size:22rpx;font-weight:600}
.vote-closed-tag.pass{background:#e4f2e9;color:#43815b}
.end-to-review{margin-top:30rpx}
.member-wait-hint{margin-top:26rpx;text-align:center;color:#84929b;font-size:23rpx}
.result-card{text-align:left}.result-seal{text-align:center;font-size:34rpx;font-weight:700;color:#274c63}.result-meta{text-align:center;margin:10rpx 0 26rpx;color:#798892;font-size:23rpx}.result-attendance{padding:18rpx;border-radius:12rpx;background:#f4f7f8}.result-attendance b{display:block;margin-bottom:8rpx}.result-topic{padding:22rpx 0;border-bottom:2rpx solid #edf1f3}.result-topic-title{font-weight:700}.result-topic-text{margin-top:10rpx;color:#526570;line-height:1.65;white-space:pre-wrap}.result-note{margin:24rpx 0 0;color:#75858f;font-size:23rpx}.result-confirmed{margin-top:26rpx;padding:20rpx;border-radius:14rpx;background:#e9f5ed;color:#43815b;text-align:center}
.result-card .omf-primary{display:block;width:70%;margin-left:auto;margin-right:auto}
</style>
