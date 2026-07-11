<template>
  <div v-if="topic" class="ts-mask" @click="$emit('close')">
    <div class="ts-sheet" :class="{ 'is-notice': topic.type === 'notice', 'is-vote': topic.voteRequired }" @click.stop>
      <div class="ts-handle"></div>
      <div class="ts-head">
        <span class="ts-titlewrap"><span class="ts-title">{{ topic.title }}</span><span class="ts-tag" :class="tagClass">{{ tagLabel }}</span></span>
        <span class="ts-close" @click="$emit('close')">×</span>
      </div>

      <!-- 指令条：一句话说清此刻该做什么，按类型变色（研究快赢①：命中"进来不知道干嘛"） -->
      <div v-if="guideText" class="ts-guide" :class="guideType">{{ guideText }}</div>

      <!-- 可滚动区：表决 + 意见汇总（输入框固定在底部，这里滚动看更多意见） -->
      <div class="ts-scroll">
      <!-- 表决区（仅表决类议题）：分「我的表决」和「全体表决情况」两块，避免个人/全体状态挤在一起 -->
      <div v-if="topic.voteRequired" class="ts-vote">
        <div class="ts-vote-question">请表决</div>
        <!-- ① 我的表决：投票按钮（按钮含义自明，不加标签）+ 我的状态 -->
        <div class="ts-vote-mine">
          <!-- 研究P1「先选后交」：未表决且未结束时点选=高亮预览(可反复改)，点「确认提交」才二次确认落库 -->
          <template v-if="!topic.myVote && !topic.voteClosed">
            <template v-if="(topic.decisionType || 'simple') !== 'multi_choice'">
              <div class="ts-vote-btns">
                <button class="ts-vote-btn agree" :class="{ on: pendingVote === 'for_vote' }" @click="pickVote('for_vote')"><span class="ts-radio"></span><span>同意</span></button>
                <button class="ts-vote-btn against" :class="{ on: pendingVote === 'against' }" @click="pickVote('against')"><span class="ts-radio"></span><span>不同意</span></button>
                <button class="ts-vote-btn abstain" :class="{ on: pendingVote === 'abstain' }" @click="pickVote('abstain')"><span class="ts-radio"></span><span>弃权</span></button>
              </div>
            </template>
            <template v-else>
              <div class="ts-opt" v-for="o in (topic.options || [])" :key="o.id"
                   :class="{ on: pendingVote != null && String(pendingVote) === String(o.id) }"
                   @click="pickVote(o.id, o)">
                <span class="ts-opt-label">{{ o.label }}</span>
              </div>
            </template>
            <!-- 确认提交：选好才可点；"提交后不可改"静态可见（研究快赢③+P1） -->
            <button v-if="interactive && signedIn" class="ts-vote-submit" :disabled="pendingVote == null" @click="submitVote">
              {{ pendingVote == null ? '请选择一项' : '确认表决' }}
            </button>
            <div class="ts-vote-locktip">提交后不可修改</div>
          </template>
          <!-- 已表决：结果卡整块替换选择区（研究P1：占屏结果态明确"做完了"，不靠按钮变淡） -->
          <div v-else-if="topic.myVote" class="ts-vote-done"><span class="ts-vote-done-mark">✓</span>您已投「{{ myVoteLabel }}」<span class="ts-vote-done-lock">已提交 · 不可修改</span></div>
          <!-- 表决已结束但本人未投：明确告知（此后不再显示投票按钮） -->
          <div v-else class="ts-vote-missed">本议题表决已结束，您未参与投票</div>
        </div>

        <!-- ② 全体表决情况：进行中只给参与进度(防从众)；主任「结束表决」或会议结束后才揭晓票数明细+白话结论 -->
        <div v-if="showVoteSummary" class="ts-vote-all">
          <div class="ts-tally-top">
            <span class="ts-tally-scope">表决进度</span>
            <span class="ts-tally-total">{{ topic.voted != null ? topic.voted : 0 }}/{{ topic.total || 0 }} 人已投</span>
          </div>

          <!-- 已揭晓：白话结论 + 进度条 + 逐项(未投独立成行) -->
          <template v-if="voteRevealed">
            <template v-if="(topic.decisionType || 'simple') !== 'multi_choice'">
              <div class="ts-verdict" :class="topic.passed ? 'pass' : 'fail'">
                <span class="ts-verdict-mark">{{ topic.passed ? '✓' : '✕' }}</span>
                <span class="ts-verdict-word">{{ topic.passed ? '通过' : '未通过' }}</span>
                <span class="ts-verdict-sub">{{ verdictSub }}</span>
              </div>
              <div class="ts-bar">
                <div v-if="topic.forVotes" class="ts-bar-seg agree" :style="{ width: barPct(topic.forVotes) }"></div>
                <div v-if="topic.agVotes" class="ts-bar-seg against" :style="{ width: barPct(topic.agVotes) }"></div>
                <div v-if="topic.abVotes" class="ts-bar-seg abstain" :style="{ width: barPct(topic.abVotes) }"></div>
                <div v-if="notVoted" class="ts-bar-seg none" :style="{ width: barPct(notVoted) }"></div>
              </div>
              <div class="ts-tally-rows">
                <div class="ts-tally-row"><span class="ts-dot agree"></span><span class="ts-row-label">同意</span><span class="ts-row-num">{{ topic.forVotes || 0 }}</span></div>
                <div class="ts-tally-row"><span class="ts-dot against"></span><span class="ts-row-label">不同意</span><span class="ts-row-num">{{ topic.agVotes || 0 }}</span></div>
                <div class="ts-tally-row"><span class="ts-dot abstain"></span><span class="ts-row-label">弃权</span><span class="ts-row-num">{{ topic.abVotes || 0 }}</span></div>
                <div class="ts-tally-row none"><span class="ts-dot none"></span><span class="ts-row-label">未投</span><span class="ts-row-num">{{ notVoted }}</span></div>
              </div>
            </template>
            <!-- 多选项：揭晓后按选项列票数，未投单列 -->
            <div v-else class="ts-tally-rows">
              <div class="ts-tally-row" v-for="o in (topic.options || [])" :key="o.id"><span class="ts-row-label">{{ o.label }}</span><span class="ts-row-num">{{ o.votes || 0 }}</span></div>
              <div class="ts-tally-row none"><span class="ts-row-label">未投</span><span class="ts-row-num">{{ notVoted }}</span></div>
            </div>
          </template>

          <!-- 表决进行中：主任确认完成后，系统统计并显示结果 -->
          <template v-else>
            <div class="ts-tally-veilbox">表决完成后显示统计结果</div>
            <button v-if="isChair && interactive && !topic.voteClosed" class="ts-close-vote" :disabled="closingVote" @click="closeVote">
              {{ closingVote ? '统计中...' : '完成本项表决' }}
            </button>
          </template>
        </div>
        <button v-if="canDiscuss" class="ts-op-entry" :class="{ open: opinionOpen }" @click="opinionOpen = !opinionOpen">
          {{ opinionOpen ? '收起补充意见' : '补充意见（可选）' }}
        </button>
      </div>

      <!-- 通报区（仅通报类议题）：正文 + 已读进度 + 本人「我已读」 + (主任)标记全体已通报 -->
      <div v-if="topic.type === 'notice'" class="ts-notice">
        <div class="ts-notice-label">通知内容</div>
        <div class="ts-notice-body">{{ topic.content || '（暂无通知正文）' }}</div>
        <div class="ts-notice-foot">
          <!-- 研究P1#8：状态改"已读 X/Y 人"进度；凑齐全体自动「已通报」 -->
          <span class="ts-notice-status" :class="{ done: topic.notified }">{{ topic.notified ? '✓ 全体已通报' : ('已读 ' + (topic.viewedCount || 0) + '/' + (topic.signedInCount || 0) + ' 人') }}</span>
          <!-- 委员/主任本人：只确认自己「我已读」，不再一人点就全体已通报 -->
          <button v-if="interactive && signedIn && !topic.viewedByMe && !topic.notified" class="ts-notice-read" @click="markMyRead">我已读</button>
          <span v-else-if="interactive && signedIn && topic.viewedByMe && !topic.notified" class="ts-notice-mine">✓ 您已确认</span>
        </div>
        <!-- 主任人工推进：无需逐个等待，直接标记全体已通报 -->
        <div v-if="isChair && interactive && !topic.notified" class="ts-notice-chair">
          <button class="ts-notice-forceall" @click="markNoticeRead">标记全体已通报</button>
          <span class="ts-notice-chair-tip">无需逐个等待，主任可直接确认全体已知悉</span>
        </div>
      </div>

      <!-- 意见区 -->
      <div v-if="showOpinionSection" class="ts-ops">
        <div class="ts-ops-head">{{ topic.voteRequired ? '补充意见' : '意见汇总' }}<span v-if="opinions.length">（{{ opinions.length }}）</span></div>
        <div v-if="loading" class="ts-empty">加载中…</div>
        <div v-else-if="!opinions.length" class="ts-empty">还没有人发表意见</div>
        <div v-else class="ts-op" :class="{ open: openOpIds.has(op.id) }" v-for="op in opinions" :key="op.id" @click="toggleOp(op)">
          <div class="ts-op-l1">
            <span class="ts-op-name">{{ op.name }}</span>
            <span v-if="opVote(op)" class="ts-op-vote" :class="opVote(op).cls">{{ opVote(op).text }}</span>
            <span v-if="op.claimable" class="ts-op-claim-tag" :class="{ on: claimShowId === op.id }"
                  @click.stop="toggleClaimRow(op)">未认领</span>
            <span class="ts-op-time">{{ fmtTime(op.createdAt) }}</span>
          </div>
          <div class="ts-op-l2">
            <span class="ts-op-sum">{{ op.content }}</span>
            <span class="ts-op-toggle">{{ openOpIds.has(op.id) ? '收起' : '查看' }}</span>
          </div>
          <!-- 展开后才显示删除，避免和"查看"挤在一排 -->
          <div v-if="openOpIds.has(op.id) && op.canDelete" class="ts-op-actions" @click.stop>
            <button class="ts-op-del" @click="removeOpinion(op)">删除</button>
          </div>
          <!-- AI 从现场发言提炼、还没归属到人：认领按钮默认藏着，点"未认领"标签才展开 -->
          <div v-if="op.claimable && claimShowId === op.id" class="ts-op-claimrow" @click.stop>
            <button class="ts-op-claim-btn" @click="claimOpinion(op)">🙋 是我说的</button>
          </div>
        </div>
      </div>
      </div><!-- /ts-scroll -->

      <!-- 输入区：固定在弹层底部 -->
      <template v-if="showComposer">
        <!-- 语音条：听写中(边说边出字) / 整理中 / 完成待确认 -->
        <div v-if="voiceOn" class="ts-voicebar" :class="'stage-' + voiceStage">
          <!-- 听写中：实时出字 -->
          <template v-if="voiceStage === 'listening'">
            <div class="ts-voice-live">
              <span class="ts-voice-dot"></span>
              <span class="ts-voice-live-txt" :class="{ ph: voiceFallback || !asrLive }">{{ voiceFallback ? '正在听你说…' : (asrLive || '请开始说话，文字会实时显示…') }}</span>
            </div>
            <div class="ts-voice-foot">
              <span class="ts-voice-time">{{ voiceFallback ? recTimeText : asrTime }}</span>
              <button class="ts-voice-cancel" @click="cancelVoice">取消</button>
              <button class="ts-voice-done" @click="finishVoice">说完了</button>
            </div>
          </template>
          <!-- 整理中 -->
          <template v-else-if="voiceStage === 'finishing'">
            <span class="ts-voice-txt busy"><span class="ts-voice-spin"></span>正在整理文字…</span>
          </template>
          <!-- 完成：确认 / 取消 -->
          <template v-else>
            <div class="ts-voice-result">{{ voiceResult }}</div>
            <div class="ts-voice-foot">
              <button class="ts-voice-cancel" @click="cancelVoice">取消</button>
              <button class="ts-voice-done" @click="confirmVoice">确认使用</button>
            </div>
          </template>
        </div>
        <!-- AI 小助手：不知道怎么说时，先随便说说，AI 代拟正式发言 -->
        <div v-else-if="helperOn" class="ts-helper">
          <div class="ts-helper-head">🤖 AI 小助手<span class="ts-helper-close" @click="helperOn = false">×</span></div>
          <div class="ts-helper-q">想对这个议题说点什么？不用讲究措辞，随便说说，我来帮你整理成正式发言。</div>
          <textarea v-model="helperDraft" class="ts-ta ts-helper-ta" rows="2" placeholder="比如：我觉得方案挺好，就是钱有点多…"></textarea>
          <div class="ts-helper-btns">
            <button class="ts-helper-voice" :disabled="aiBusy" @click="startVoice('helper')">🎤 用说的</button>
            <button class="ts-helper-go" :disabled="!helperDraft.trim() || aiBusy" @click="draftByAi">{{ aiBusy ? 'AI 正在写…' : '帮我写好' }}</button>
            <span v-if="aiBusy" class="ts-ai-prog"><span class="ts-ai-spin"></span></span>
          </div>
        </div>
        <!-- 常规输入行 + AI 助手行 -->
        <template v-else>
          <div class="ts-compose">
            <div class="ts-input">
              <textarea v-model="draft" class="ts-ta" rows="1" placeholder="说点什么…" @input="autoGrow" ref="taEl"></textarea>
              <button class="ts-send" :disabled="!draft.trim() || sending" @click="submitOpinion">发表</button>
            </div>
            <div class="ts-ai-row">
              <button v-if="draft.trim()" class="ts-ai-btn ai" :disabled="aiBusy" @click="polishByAi"><span v-if="aiBusy" class="ts-ai-spin"></span>{{ aiBusy ? 'AI 润色中…' : 'AI 润色' }}</button>
              <button v-else class="ts-ai-btn ai" :disabled="aiBusy" @click="onHelpWrite"><span v-if="aiBusy" class="ts-ai-spin"></span>{{ aiBusy ? 'AI 写作中…' : 'AI 帮写' }}</button>
              <button class="ts-ai-btn voice" :disabled="aiBusy" @click="startVoice('draft')">语音输入</button>
            </div>
          </div>
        </template>
      </template>
      <div v-else-if="canDiscuss && interactive && !signedIn && (!topic.voteRequired || opinionOpen)" class="ts-input-hint">签到后可发表意见</div>

      <!-- 上一个 / 下一个议题：处理完当前议题直接切换，不用先关弹层 -->
      <div v-if="hasPrev || hasNext" class="ts-nav-row">
        <button v-if="hasPrev" class="ts-nav-btn prev" @click="$emit('prev')">‹ 上一个议题</button>
        <button v-if="hasNext" class="ts-nav-btn next" @click="$emit('next')">下一个议题 ›</button>
        <!-- 最后一个议题：右侧改为「完成」，点了收起弹层 -->
        <button v-else class="ts-nav-btn done" @click="$emit('close')">完成</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import api from '@/api'
import { toast, showModal } from '@/utils/ui'
import { useRecorder } from '@/composables/useRecorder'
import { useAsrStream } from '@/composables/useAsrStream'
import { applyHotwords } from '@/utils/helpers'

const props = defineProps({
  meetingId: { type: [String, Number], required: true },
  topic: { type: Object, default: null },        // detail.record.topics 里的一项（TopicVO）
  interactive: { type: Boolean, default: false }, // 会议进行中（可表决/发言）
  signedIn: { type: Boolean, default: false },
  isChair: { type: Boolean, default: false },
  hasPrev: { type: Boolean, default: false }, // 是否有上一个议题（父组件按列表算）
  hasNext: { type: Boolean, default: false }  // 是否有下一个议题
})
const emit = defineEmits(['close', 'changed', 'prev', 'next'])

const opinions = ref([])
const loading = ref(false)
const draft = ref('')
const sending = ref(false)
const taEl = ref(null)
const opinionOpen = ref(false)

const tagClass = computed(() => {
  const t = props.topic && props.topic.type
  return t === 'decision' ? 'vote' : (t === 'notice' ? 'notice' : 'discuss')
})
const tagLabel = computed(() => {
  const t = props.topic && props.topic.type
  return t === 'decision' ? '表决' : (t === 'notice' ? '通报' : '讨论')
})
const canDiscuss = computed(() => props.topic && props.topic.type !== 'notice')
const showOpinionSection = computed(() => {
  if (!canDiscuss.value) return false
  if (!props.topic || !props.topic.voteRequired) return true
  return opinionOpen.value
})
const showComposer = computed(() => {
  if (!canDiscuss.value || !props.interactive || !props.signedIn) return false
  if (!props.topic || !props.topic.voteRequired) return true
  return opinionOpen.value
})
const showVoteSummary = computed(() => {
  const t = props.topic
  if (!t || !t.voteRequired) return false
  return voteRevealed.value || !!t.myVote || (props.isChair && ((t.voted || 0) > 0 || t.voteClosed))
})

// ── 顶部指令条：一句话说清"此刻该做什么"，按议题类型/状态变色（研究第一条快赢：命中"进来不知道干嘛"）──
const guideType = computed(() => {
  const t = props.topic
  if (!t) return 'discuss'
  if (t.type === 'notice') return 'notice'
  if (t.voteRequired) return 'vote'
  return 'discuss'
})
const guideText = computed(() => {
  const t = props.topic
  if (!t) return ''
  if (t.type === 'notice') return t.notified ? '本通报已全体知悉（已通报）' : '请阅读下面的通报内容，读完点「我已读」'
  if (t.voteRequired) {
    // 已揭晓（主任已结束表决 / 会议已结束）：给白话结论
    if (t.voteClosed) return t.passed ? '本议题表决已结束：决议通过' : '本议题表决已结束：未通过'
    if (!props.interactive) {
      // 会议已结束但未正式点结束表决：仍按结果提示；准备阶段则提示暂不可表决
      if (t.myVote != null || (t.voted || 0) > 0) return t.passed ? '本议题表决已结束：决议通过' : '本议题表决已结束：未通过'
      return '本次会议未在进行中，暂不可表决'
    }
    // 表决进行中
    if (t.myVote) return props.isChair ? '已完成表决，可结束本项' : '您已完成表决'
    if (!props.signedIn) return '签到后即可对本议题表决'
    return '请选择表决意见'
  }
  if (props.interactive && !props.signedIn) return '签到后可发表意见'
  return '可发表您的看法，也可直接去下一项'
})
// 研究P1「先选后交」：本地暂存"选中但未提交"的表决，可反复改；点「确认提交」才落库
const pendingVote = ref(null)      // 简单表决=choice字符串 / 多选=选项id
const pendingOption = ref(null)    // 多选时记住选项对象
const VOTE_LABELS = { for_vote: '同意', against: '不同意', abstain: '弃权' }
const pendingLabel = computed(() => {
  if (pendingVote.value == null) return ''
  if (pendingOption.value) return pendingOption.value.label
  return VOTE_LABELS[pendingVote.value] || ''
})
const myVoteLabel = computed(() => {
  const t = props.topic
  if (!t || !t.myVote) return ''
  if ((t.decisionType || 'simple') === 'multi_choice') {
    const opt = (t.options || []).find(o => String(o.id) === String(t.myVote))
    return opt ? opt.label : '已投票'
  }
  return VOTE_LABELS[t.myVote] || ''
})

// ── 研究P1「结束表决揭晓」：表决进行中对委员隐藏票数明细(防从众)；主任点「结束表决」(voteClosed) 或会议已结束(!interactive) 后才揭晓 ──
const closingVote = ref(false)
const voteRevealed = computed(() => {
  const t = props.topic
  if (!t) return false
  return !!t.voteClosed || !props.interactive
})
// 未投人数 = 应到 − 已投
const notVoted = computed(() => {
  const t = props.topic
  if (!t) return 0
  return Math.max(0, (t.total || 0) - (t.voted || 0))
})
// 距过半还差几票（need = 应到/2+1，后端已算）
const shortBy = computed(() => {
  const t = props.topic
  if (!t) return 0
  return Math.max(0, (t.need || 0) - (t.forVotes || 0))
})
// 白话结论副文案：通过=同意已过半；未通过=还差X票
const verdictSub = computed(() => {
  const t = props.topic
  if (!t) return ''
  const f = t.forVotes || 0, need = t.need || 0
  return t.passed
    ? '同意 ' + f + ' 票，已过半（需 ' + need + ' 票）'
    : '同意 ' + f + ' 票，还差 ' + shortBy.value + ' 票（需 ' + need + ' 票）'
})
// 进度条各段宽度：按应到人数占比
function barPct(n) {
  const t = props.topic
  const total = (t && t.total) || 0
  if (!total) return '0%'
  return Math.round((n || 0) / total * 100) + '%'
}

// ── 语音输入意见：useRecorder 录音 → 后端 ASR 转文字 → 填入输入框（可改）→ 发表 ──
// 注意声明须在下面 immediate watch 之前（watch 首跑就会调 cancelVoice）
const rec = useRecorder()        // 降级：流式不可用/连不上时的整段录音 + 一次性识别
const recTimeText = rec.timeText
const asr = useAsrStream()        // 首选：真·实时流式识别（边说边出字）
const asrLive = asr.liveText      // 实时累积的识别文字
const asrTime = asr.timeText
const voiceOn = ref(false)        // 语音条显示中
const voiceStage = ref('listening') // listening=听写中 / finishing=整理中 / confirm=待确认
const voiceResult = ref('')       // 识别结果（待用户确认的文本）
const voiceTarget = ref('draft')  // 识别结果回填目标：draft=意见输入框 / helper=小助手输入框
const voiceFallback = ref(false)  // 本次是否走了降级录音路径
const draftFromVoice = ref(false) // 本条草稿是否来自语音（发表时 source 用 voice）

// ── AI 助手：润色已有意见 / 小助手代拟发言 ──
const helperOn = ref(false)     // 小助手面板
const helperDraft = ref('')     // 小助手里的"随便说说"
const aiBusy = ref(false)       // AI 生成中
const aiTokens = ref(0)         // 最近一次 AI 消耗 token（低调展示）
// AI 生成时的"假进度"：转圈图标旁滚动到 ~97% 制造"正在写"的观感（真实完成靠 aiBusy 收尾）
const aiProgress = ref(0)
let aiProgTimer = null
watch(aiBusy, (busy) => {
  clearInterval(aiProgTimer); aiProgTimer = null
  if (busy) {
    aiProgress.value = 4
    aiProgTimer = setInterval(() => {
      const step = Math.max(1, Math.round((97 - aiProgress.value) * 0.14)) // 越接近 97 走得越慢
      aiProgress.value = Math.min(97, aiProgress.value + step)
    }, 240)
  } else {
    aiProgress.value = 0
  }
})
const polishUndo = ref(null)    // 润色前的原文（可还原）；null=没有可还原的
const claimShowId = ref(null)   // 认领按钮默认藏着：点"未认领"标签展开的那条意见 id（声明须在下方 immediate watch 之前）
const openOpIds = ref(new Set())  // 意见正文默认折叠，点"查看"展开的意见 id 集合
function toggleOp(op) {
  const s = new Set(openOpIds.value)
  s.has(op.id) ? s.delete(op.id) : s.add(op.id)
  openOpIds.value = s
}

// 打开（topic 切换/出现）时拉取本议题意见；关闭/切议题时收掉语音条（释放麦克风）和 AI 状态
watch(() => props.topic && props.topic.id, (id) => {
  cancelVoice()
  helperOn.value = false; helperDraft.value = ''; aiBusy.value = false
  aiTokens.value = 0; polishUndo.value = null; claimShowId.value = null; openOpIds.value = new Set()
  opinionOpen.value = false
  pendingVote.value = null; pendingOption.value = null  // 切议题清掉未提交的选择
  if (id) { draft.value = ''; draftFromVoice.value = false; loadOpinions() }
}, { immediate: true })
onBeforeUnmount(() => { cancelVoice(); clearInterval(aiProgTimer) })

// 研究P1「已宣读降级」#8：委员本人显式确认「我已读」（只记自己，不再一人点就全体已通报）。
// 全体已签到委员都确认后，后端自动把该通报标记「全体已通报」。
async function markMyRead() {
  const t = props.topic
  if (!t || t.type !== 'notice') return
  if (!props.interactive || !props.signedIn) return
  if (t.viewedByMe) return
  try {
    await api.committeeNoticeView(props.meetingId, t.id)
    toast({ title: '已确认「我已读」', icon: 'success' })
    emit('changed') // 刷新（可能刚好凑齐"全体已读"→已通报）
  } catch (e) { toast({ title: (e && e.message) || '操作失败', icon: 'none' }) }
}

// 「标记全体已通报」（仅主任/副主任）：人工推进，无需逐个等待，直接把该通报视为全体已知悉。
async function markNoticeRead() {
  const t = props.topic
  if (!t || t.type !== 'notice') return
  const res = await showModal({
    title: '标记全体已通报',
    content: '确认全体委员均已知悉本通报？标记后本议题即视为「已通报」，无需再逐个确认。',
    confirmText: '确认已通报',
    cancelText: '再等等'
  })
  if (!res.confirm) return
  try {
    await api.committeeNoticeRead(props.meetingId, t.id)
    toast({ title: '已标记全体已通报', icon: 'success' })
    emit('changed')
  } catch (e) { toast({ title: (e && e.message) || '操作失败', icon: 'none' }) }
}

// 开始语音输入：首选真·实时流式（边说边出字）；连不上/不支持时降级到整段录音+一次性识别。
async function startVoice(target) {
  voiceTarget.value = target === 'helper' ? 'helper' : 'draft'
  voiceResult.value = ''
  voiceFallback.value = false
  if (asr.supported.value) {
    try {
      voiceStage.value = 'listening'
      voiceOn.value = true
      await asr.start()
      return
    } catch (e) {
      // 麦克风被拒 → 明确提示，不必再降级（降级同样拿不到麦克风）
      const name = e && (e.name || '')
      if (name === 'NotAllowedError' || name === 'NotFoundError' || String(e && e.message).includes('Permission')) {
        voiceOn.value = false
        toast({ title: '无法使用麦克风，请在浏览器/微信里允许麦克风后再试', icon: 'none' })
        return
      }
      asr.cancel() // WS 连不上等 → 落到下面的降级录音
    }
  }
  if (!rec.supported.value) {
    voiceOn.value = false
    toast({ title: '当前浏览器不支持录音（需 HTTPS 且允许麦克风）', icon: 'none' })
    return
  }
  try {
    voiceFallback.value = true
    voiceStage.value = 'listening'
    voiceOn.value = true
    await rec.start()
  } catch (e) {
    voiceOn.value = false
    toast({ title: '无法使用麦克风，请在浏览器允许麦克风后再试', icon: 'none' })
  }
}

function cancelVoice() {
  try { asr.cancel() } catch (e) {}
  try { rec.reset() } catch (e) {}
  voiceOn.value = false
  voiceStage.value = 'listening'
  voiceResult.value = ''
  voiceFallback.value = false
}

// 未检测到声音：明确提示（老人常见——离麦克风远/没说话/权限给了但静音）
function noSoundPrompt() {
  toast({ title: '未检测到声音，请靠近麦克风，慢一点再说一次', icon: 'none' })
  cancelVoice()
}

// 说完了：停止采集，取到最终文字后进入「确认/取消」；识别不到则提示未检测到声音。
async function finishVoice() {
  if (voiceStage.value !== 'listening') return
  voiceStage.value = 'finishing'
  let text = ''
  if (!voiceFallback.value) {
    const r = await asr.stop()
    if (r.noSound) { noSoundPrompt(); return }
    if (r.serviceError) { toast({ title: '语音识别失败：' + r.serviceError, icon: 'none' }); cancelVoice(); return }
    text = (r.text || '').trim()
  } else {
    try {
      const out = await rec.stop()
      if (!out || !out.blob || out.blob.size === 0) { noSoundPrompt(); return }
      const file = new File([out.blob], 'voice.' + out.ext, { type: out.mimeType })
      const res = await api.committeeVoiceToText(props.meetingId, file)
      text = ((res && res.text) || '').trim()
    } catch (e) { toast({ title: '语音识别失败，请再试一次', icon: 'none' }); cancelVoice(); return }
  }
  if (!text) { noSoundPrompt(); return }
  voiceResult.value = applyHotwords(text)
  voiceStage.value = 'confirm'
}

// 确认使用：把识别文字填入目标输入框（可再手改），关闭语音条。
function confirmVoice() {
  const text = (voiceResult.value || '').trim()
  if (!text) { cancelVoice(); return }
  if (voiceTarget.value === 'helper') {
    helperDraft.value = helperDraft.value ? (helperDraft.value + text) : text
  } else {
    draft.value = draft.value ? (draft.value + text) : text
    nextTick(autoGrow)
  }
  draftFromVoice.value = true
  cancelVoice()
}

// ── AI 润色 / 代拟 ──
// AI 完成后弹卡片：告知已生成、耗时、消耗 token
function showAiDoneCard(mode) {
  // 研究快赢②：删掉"耗时/token"这类噪音，只给老人一句"办好了"
  if (mode === 'polish') {
    showModal({
      title: '',
      content: '已经帮你润色好啦，满意吗？',
      size: 'aicard',
      showCancel: false,
      confirmText: '好的'
    })
    return
  }
  // 代拟/帮写：只告知已写好、提示去查看内容。此刻用户还没看到正文，不追问是否润色；
  // 想润色让用户看完后自己点下方「AI 润色」（帮写后该按钮本就从「AI 帮写」变成「AI 润色」）。
  showModal({
    title: '',
    content: '已经帮你写好啦，请查看内容',
    size: 'aicard',
    showCancel: false,
    confirmText: '好的'
  })
}
async function polishByAi() {
  const text = draft.value.trim()
  if (!text || aiBusy.value) return
  aiBusy.value = true
  const t0 = Date.now()
  try {
    const res = await api.committeeOpinionAssist(props.meetingId, props.topic.id, 'polish', text)
    if (!res || !res.text) { toast({ title: 'AI 没写出来，请重试', icon: 'none' }); return }
    polishUndo.value = text
    draft.value = res.text
    aiTokens.value = Number(res.tokens) || 0
    nextTick(autoGrow)
    showAiDoneCard('polish', t0, aiTokens.value)
  } catch (e) {
    toast({ title: (e && e.message) || 'AI 助手开小差了，请重试', icon: 'none' })
  } finally { aiBusy.value = false }
}
function undoPolish() {
  if (polishUndo.value === null) return
  draft.value = polishUndo.value
  polishUndo.value = null
  aiTokens.value = 0
  nextTick(autoGrow)
}
async function draftByAi() {
  const text = helperDraft.value.trim()
  if (!text || aiBusy.value) return
  aiBusy.value = true
  const t0 = Date.now()
  try {
    const res = await api.committeeOpinionAssist(props.meetingId, props.topic.id, 'draft', text)
    if (!res || !res.text) { toast({ title: 'AI 没写出来，请重试', icon: 'none' }); return }
    draft.value = res.text
    aiTokens.value = Number(res.tokens) || 0
    polishUndo.value = null
    helperOn.value = false
    helperDraft.value = ''
    nextTick(autoGrow)
    showAiDoneCard('draft', t0, aiTokens.value)
  } catch (e) {
    toast({ title: (e && e.message) || 'AI 助手开小差了，请重试', icon: 'none' })
  } finally { aiBusy.value = false }
}

// 「AI 帮我写」按钮点击：已表决 → 无需输入，直接按身份+表决结果代拟；未表决 → 打开小助手手动说
function onHelpWrite() {
  if (props.topic && props.topic.myVote) { draftFromVote(); return }
  helperOn.value = true
}
// 由本人表决结果生成一句"口头表态"喂给 draft（后端已带委员身份 speaker_role，AI 据此写正式发言）。
// voteVal/option 可显式传入刚投的票（投票成功后立刻用，不必等 prop 回刷）；不传则读 topic.myVote。
function voteStanceSeed(voteVal, option) {
  const t = props.topic
  const mv = voteVal != null ? voteVal : (t && t.myVote)
  if (!t || !mv) return ''
  if ((t.decisionType || 'simple') === 'multi_choice') {
    const opt = option || (t.options || []).find(o => String(o.id) === String(mv))
    return opt ? ('我在这个议题上选择了「' + opt.label + '」，请据此帮我写一段简短的表态发言。') : ''
  }
  if (mv === 'for_vote') return '我对这个议题投了赞成票，总体认同这个方案，支持通过。'
  if (mv === 'against') return '我对这个议题投了反对票，对这个方案还有顾虑，暂不赞成。'
  if (mv === 'abstain') return '我对这个议题投了弃权票，还想再多了解一些情况，暂不表态。'
  return ''
}
// 表决后一键代拟：不用输入，按身份+表决态度生成发言，填入输入框
async function draftFromVote(voteVal, option) {
  if (aiBusy.value) return
  const seed = voteStanceSeed(voteVal, option)
  if (!seed) { helperOn.value = true; return } // 拿不到表决结果就退回手动
  aiBusy.value = true
  const t0 = Date.now()
  try {
    const res = await api.committeeOpinionAssist(props.meetingId, props.topic.id, 'draft', seed)
    if (!res || !res.text) { toast({ title: 'AI 没写出来，请重试', icon: 'none' }); return }
    draft.value = res.text
    aiTokens.value = Number(res.tokens) || 0
    polishUndo.value = null
    nextTick(autoGrow)
    showAiDoneCard('draft', t0, aiTokens.value)
  } catch (e) {
    toast({ title: (e && e.message) || 'AI 助手开小差了，请重试', icon: 'none' })
  } finally { aiBusy.value = false }
}

async function loadOpinions() {
  loading.value = true
  try {
    const all = await api.committeeOpinions(props.meetingId)
    opinions.value = (all || []).filter(op => String(op.topicId) === String(props.topic.id))
  } catch (e) { /* request 已 toast */ } finally { loading.value = false }
}

function fmtTime(iso) { return iso && iso.length >= 16 ? iso.slice(11, 16) : '' }
// 该委员对本议题的表决结果标签。首选意见自带的 voteChoice/voteLabel（后端按作者查票，
// 所有表决议题都有）；旧后端没这俩字段时退回 voterChoices（仅实名表决）按姓名匹配。
function opVote(op) {
  if (op && op.voteLabel) return { text: op.voteLabel, cls: 'opt' } // 多选项表决：显示所选选项
  if (op && op.voteChoice === 'for_vote') return { text: '同意', cls: 'agree' }
  if (op && op.voteChoice === 'against') return { text: '不同意', cls: 'against' }
  if (op && op.voteChoice === 'abstain') return { text: '弃权', cls: 'abstain' }
  const vc = props.topic && props.topic.voterChoices
  if (!Array.isArray(vc) || !vc.length || !op || !op.name) return null
  const hit = vc.find(v => v && v.name === op.name)
  if (!hit) return null
  if (hit.label) return { text: hit.label, cls: 'opt' }
  if (hit.choice === 'for_vote') return { text: '同意', cls: 'agree' }
  if (hit.choice === 'against') return { text: '不同意', cls: 'against' }
  if (hit.choice === 'abstain') return { text: '弃权', cls: 'abstain' }
  return null
}

function autoGrow() {
  const el = taEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

// 研究P1「先选后交」①点选：只本地暂存(可反复改)，不落库、不弹窗
function pickVote(choice, option) {
  const t = props.topic
  if (!t || t.myVote) return
  if (!props.interactive) { toast({ title: '会议进行中才可表决', icon: 'none' }); return }
  if (!props.signedIn) { toast({ title: '请先完成签到', icon: 'none' }); return }
  pendingVote.value = option ? option.id : choice
  pendingOption.value = option || null
}
// ②确认提交：二次确认后才真正投票落库。研究P1：砍掉投票后"要不要AI帮写意见"的追问弹窗
async function submitVote() {
  const t = props.topic
  if (!t || t.myVote || pendingVote.value == null) return
  if (!props.interactive) { toast({ title: '会议进行中才可表决', icon: 'none' }); return }
  if (!props.signedIn) { toast({ title: '请先完成签到', icon: 'none' }); return }
  const opt = pendingOption.value
  const res = await showModal({
    title: '',
    content: '您投的是「' + pendingLabel.value + '」，提交后不能修改。确认提交？',
    confirmText: '确认提交',
    cancelText: '再想想',
    size: 'vote'
  })
  if (!res.confirm) return
  try {
    await api.committeeVote(props.meetingId, t.id, opt ? null : pendingVote.value, opt ? opt.id : null)
    toast({ title: '已提交', icon: 'success' })
    pendingVote.value = null; pendingOption.value = null
    emit('changed')
    loadOpinions() // 刷新意见列表：自己此前发的意见旁立刻带上刚投的表决标签
  } catch (e) { /* 已 toast */ }
}

// 主任确认本项表决完成后，后端关闭本议题投票并展示票数。
async function closeVote() {
  const t = props.topic
  if (!t || closingVote.value) return
  const notYet = notVoted.value
  const res = await showModal({
    title: '完成本项表决',
    content: notYet > 0
      ? '还有 ' + notYet + ' 人未投票。完成后将统计票数，且不能再补投或修改。确定完成吗？'
      : '完成后将统计票数，且不能再补投或修改。确定完成吗？',
    confirmText: '确认完成',
    cancelText: '再等等'
  })
  if (!res.confirm) return
  closingVote.value = true
  try {
    await api.committeeCloseVote(props.meetingId, t.id)
    toast({ title: '表决已完成', icon: 'success' })
    emit('changed')
  } catch (e) {
    toast({ title: (e && e.message) || '操作失败', icon: 'none' })
  } finally {
    closingVote.value = false
  }
}

async function submitOpinion() {
  const content = draft.value.trim()
  if (!content || sending.value) return
  sending.value = true
  try {
    const created = await api.committeeAddOpinion(props.meetingId, props.topic.id, content, draftFromVoice.value ? 'voice' : 'text')
    opinions.value = opinions.value.concat([created])
    draft.value = ''
    draftFromVoice.value = false
    polishUndo.value = null
    aiTokens.value = 0
    if (taEl.value) taEl.value.style.height = 'auto'
    emit('changed')
  } catch (e) { /* 已 toast */ } finally { sending.value = false }
}

// 认领按钮默认隐藏：点"未认领"标签才对该条展开（再点收起）
function toggleClaimRow(op) {
  claimShowId.value = claimShowId.value === op.id ? null : op.id
}

// 认领 AI 提炼的现场意见：归属到自己名下（之后可自行修改/删除）
async function claimOpinion(op) {
  const res = await showModal({
    title: '认领这条发言？',
    content: '确认是你在会上说的，认领后会记在你名下：\n' + (op.content.length > 60 ? op.content.slice(0, 60) + '…' : op.content),
    confirmText: '是我说的',
    cancelText: '不是'
  })
  if (!res.confirm) return
  try {
    const updated = await api.committeeClaimOpinion(props.meetingId, op.id)
    opinions.value = opinions.value.map(o => o.id === op.id ? updated : o)
    claimShowId.value = null
    toast({ title: '已认领', icon: 'success' })
    emit('changed')
  } catch (e) { toast({ title: (e && e.message) || '认领失败', icon: 'none' }) }
}

async function removeOpinion(op) {
  const res = await showModal({ title: '删除这条意见？', content: op.content.length > 40 ? op.content.slice(0, 40) + '…' : op.content, confirmText: '删除', cancelText: '取消' })
  if (!res.confirm) return
  try {
    await api.committeeRemoveOpinion(props.meetingId, op.id)
    opinions.value = opinions.value.filter(o => o.id !== op.id)
    emit('changed')
  } catch (e) { /* 已 toast */ }
}
</script>

<style scoped>
.ts-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.45); z-index: 120; display: flex; flex-direction: column; justify-content: flex-end; }
/* 弹层：flex 列——把手/标题固定在上，意见汇总区(.ts-scroll)独占中间可滚动，输入区固定在底部 */
.ts-sheet { background: #fff; border-radius: 28rpx 28rpx 0 0; padding: 14rpx 30rpx calc(24rpx + env(safe-area-inset-bottom)); height: 88vh; max-height: 92vh; overflow: hidden; display: flex; flex-direction: column; }
.ts-handle { flex-shrink: 0; width: 72rpx; height: 8rpx; border-radius: 4rpx; background: #E4E6EA; margin: 0 auto 16rpx; }
.ts-head { flex-shrink: 0; display: flex; align-items: flex-start; gap: 12rpx; margin-bottom: 20rpx; }
.ts-titlewrap { flex: 1; min-width: 0; display: flex; align-items: flex-start; flex-wrap: wrap; column-gap: 12rpx; row-gap: 4rpx; max-height: 108rpx; overflow-y: auto; -webkit-overflow-scrolling: touch; padding-right: 4rpx; }
/* 中间可滚动区：意见多了在这里滚，输入框始终露在底部 */
.ts-scroll { flex: 1 1 auto; min-height: 0; overflow-y: auto; }
.ts-title { min-width: 0; font-size: 34rpx; font-weight: 700; color: #1f2329; line-height: 1.4; word-break: break-all; }
/* 表决标签：紧跟标题之后（内联），不再顶到右上角 */
.ts-tag { flex-shrink: 0; font-size: 24rpx; font-weight: 600; padding: 4rpx 14rpx; border-radius: 10rpx; background: #F2F2F4; color: #666; white-space: nowrap; }
/* 指令条（研究快赢①）：左侧粗色条 + 浅色底，一句话说清此刻该做什么，按议题类型变色 */
.ts-guide { flex-shrink: 0; margin: -4rpx 0 18rpx; padding: 16rpx 20rpx; border-radius: 12rpx; font-size: 27rpx; font-weight: 600; line-height: 1.45; border-left: 8rpx solid #9AA0A6; background: #F3F4F6; color: #4A515A; }
.ts-guide.vote { border-left-color: #C76A00; background: #FFF4E8; color: #9A5200; }
.ts-guide.notice { border-left-color: #7C5CC4; background: #F4F0FC; color: #5B3FA8; }
.ts-guide.discuss { border-left-color: #1F6FB2; background: #EFF6FC; color: #1A5C93; }
/* 三类议题各一专属色（浅底彩字，方案A）——讨论蓝 / 表决橙 / 通报紫，刻意避开绿(=同意票色) */
.ts-tag.discuss { background: #E9F2FB; color: #1F6FB2; }
.ts-tag.vote { background: #FFF1E2; color: #C76A00; }
.ts-tag.notice { background: #F1EBFB; color: #6D3FC4; }
.ts-close { flex-shrink: 0; width: 56rpx; height: 56rpx; line-height: 52rpx; text-align: center; font-size: 44rpx; color: #999; margin: -8rpx -12rpx 0 0; }

.ts-sheet.is-vote { height: auto; max-height: 82vh; padding: 18rpx 34rpx calc(24rpx + env(safe-area-inset-bottom)); background: #fff; }
.ts-sheet.is-vote .ts-head { margin-bottom: 12rpx; align-items: flex-start; }
.ts-sheet.is-vote .ts-titlewrap { display: block; max-height: 168rpx; padding-right: 8rpx; }
.ts-sheet.is-vote .ts-title { display: block; font-size: 38rpx; line-height: 1.35; font-weight: 800; text-align: left; }
.ts-sheet.is-vote .ts-tag { display: none; }
.ts-sheet.is-vote .ts-guide { display: none; }
.ts-sheet.is-vote .ts-vote { margin: 0; }
.ts-vote-question { margin: 4rpx 0 18rpx; font-size: 30rpx; font-weight: 700; color: #6B7078; }
.ts-sheet.is-vote .ts-vote-btns { display: flex; flex-direction: column; gap: 16rpx; }
.ts-sheet.is-vote .ts-vote-btn { display: flex; align-items: center; justify-content: flex-start; gap: 18rpx; border-radius: 16rpx; border: 2rpx solid #E1E4E8; background: #fff; color: #1f2329; font-size: 34rpx; font-weight: 700; padding: 26rpx 24rpx; text-align: left; box-shadow: none; }
.ts-sheet.is-vote .ts-vote-btn.agree,
.ts-sheet.is-vote .ts-vote-btn.against,
.ts-sheet.is-vote .ts-vote-btn.abstain { background: #fff; border-color: #E1E4E8; color: #1f2329; }
.ts-radio { flex-shrink: 0; width: 34rpx; height: 34rpx; border-radius: 50%; border: 3rpx solid #C5CBD3; box-sizing: border-box; background: #fff; }
.ts-sheet.is-vote .ts-vote-btn.on { border-color: #1F6FB2; background: #F1F7FD; color: #1f2329; box-shadow: 0 0 0 4rpx rgba(31,111,178,0.08); }
.ts-sheet.is-vote .ts-vote-btn.on .ts-radio { border-color: #1F6FB2; box-shadow: inset 0 0 0 8rpx #fff; background: #1F6FB2; }
.ts-sheet.is-vote .ts-vote-btn.agree.on,
.ts-sheet.is-vote .ts-vote-btn.against.on,
.ts-sheet.is-vote .ts-vote-btn.abstain.on { border-color: #1F6FB2; background: #F1F7FD; color: #1f2329; }
.ts-sheet.is-vote .ts-vote-submit { border-radius: 16rpx; margin-top: 24rpx; padding: 28rpx 0; font-size: 34rpx; background: #1F6FB2; }
.ts-sheet.is-vote .ts-vote-submit:active { background: #185A91; }
.ts-sheet.is-vote .ts-vote-submit[disabled] { background: #E5E7EB; color: #9AA0A6; }
.ts-vote-locktip { margin-top: 12rpx; text-align: center; font-size: 24rpx; color: #9AA0A6; }
.ts-sheet.is-vote .ts-vote-submit-tip { display: none; }
.ts-sheet.is-vote .ts-vote-all { margin-top: 22rpx; padding-top: 18rpx; border-top: 2rpx solid #F0F1F3; }
.ts-sheet.is-vote .ts-tally-top { margin-bottom: 0; justify-content: center; }
.ts-sheet.is-vote .ts-tally-scope { background: transparent; color: #8A8F98; padding: 0; font-weight: 600; }
.ts-sheet.is-vote .ts-tally-total { color: #6B7078; font-size: 25rpx; font-weight: 600; }
.ts-sheet.is-vote .ts-tally-veilbox { margin-top: 10rpx; padding: 12rpx 0 0; border: 0; background: transparent; color: #A0A5AD; font-size: 24rpx; }
.ts-op-entry { display: block; width: 100%; box-sizing: border-box; margin-top: 22rpx; border: 2rpx solid #E5E7EB; border-radius: 18rpx; background: #fff; color: #5F6570; font-size: 28rpx; font-weight: 700; padding: 18rpx 0; font-family: inherit; }
.ts-op-entry.open { background: #F8FAFC; color: #1F6FB2; border-color: #D7E6F6; }

.ts-vote { margin-top: 16rpx; margin-bottom: 24rpx; } /* 标题与投票按钮之间多留 8px */
.ts-vote-btns { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 14rpx; }
.ts-vote-btn { border: 2rpx solid #D8DBE0; border-radius: 16rpx; background: #fff; color: #444; font-size: 32rpx; font-weight: 700; padding: 22rpx 0; }
/* 方案B：默认就带语义色(浅底+彩边+彩字)，同意绿/不同意红/弃权灰，一眼分清 */
.ts-vote-btn.agree { background: #EAF6E5; border-color: #52A344; color: #2E7D32; }
.ts-vote-btn.against { background: #FDECEA; border-color: #E74C3C; color: #C0392B; }
.ts-vote-btn.abstain { background: #F2F2F4; border-color: #9AA0A6; color: #5F6570; }
/* 选中态：加深为实心白字 + 色环，与默认浅底拉开层次 */
.ts-vote-btn.agree.on { background: #3E9B34; border-color: #3E9B34; color: #fff; box-shadow: 0 0 0 4rpx rgba(62,155,52,0.22); }
.ts-vote-btn.against.on { background: #E24B3A; border-color: #E24B3A; color: #fff; box-shadow: 0 0 0 4rpx rgba(226,75,58,0.22); }
.ts-vote-btn.abstain.on { background: #4D5158; border-color: #4D5158; color: #fff; box-shadow: 0 0 0 4rpx rgba(77,81,88,0.28); }
.ts-vote-btn.off { opacity: 0.35; }
.ts-vote-btn:active { transform: scale(0.97); }
.ts-opt { display: flex; align-items: center; justify-content: space-between; border: 2rpx solid #D8DBE0; border-radius: 16rpx; padding: 22rpx 24rpx; margin-bottom: 14rpx; font-size: 32rpx; color: #333; }
.ts-opt.on { background: #FFF6E8; border-color: #FFA800; color: #9A6A00; font-weight: 700; }
.ts-opt.off { opacity: 0.4; }
.ts-opt-votes { font-size: 26rpx; color: #999; }
/* 全体计票：独立浅底卡片，与个人投票区拉开距离(避免误认成个人结果)；"全体"前缀点明范围 */
.ts-vote-all { margin-top: 44rpx; }
/* 顶部：全体 + 参与进度（票数明细/结论只在揭晓后出现） */
.ts-tally-top { display: flex; align-items: center; gap: 12rpx; margin-bottom: 16rpx; }
.ts-tally-scope { font-weight: 700; color: #6B7078; background: #E9EBEF; border-radius: 8rpx; padding: 2rpx 12rpx; font-size: 24rpx; }
.ts-tally-total { font-weight: 700; color: #1f2329; font-size: 27rpx; }
/* 白话结论卡：通过=绿 / 未通过=红 */
.ts-verdict { display: flex; align-items: center; flex-wrap: wrap; gap: 4rpx 14rpx; border-radius: 16rpx; padding: 20rpx 22rpx; margin-bottom: 16rpx; }
.ts-verdict.pass { background: #EAF6E5; border: 2rpx solid #9FD290; }
.ts-verdict.fail { background: #FDECEA; border: 2rpx solid #F0B3AB; }
.ts-verdict-mark { display: inline-flex; align-items: center; justify-content: center; width: 44rpx; height: 44rpx; border-radius: 50%; color: #fff; font-size: 28rpx; font-weight: 700; }
.ts-verdict.pass .ts-verdict-mark { background: #2E9E4B; }
.ts-verdict.fail .ts-verdict-mark { background: #E24B3A; }
.ts-verdict-word { font-size: 36rpx; font-weight: 800; }
.ts-verdict.pass .ts-verdict-word { color: #2E7D32; }
.ts-verdict.fail .ts-verdict-word { color: #C0392B; }
.ts-verdict-sub { flex-basis: 100%; font-size: 25rpx; font-weight: 600; color: #6B7078; margin-top: 2rpx; }
/* 进度条：同意/不同意/弃权/未投 按应到人数占比拼接 */
.ts-bar { display: flex; height: 22rpx; border-radius: 11rpx; overflow: hidden; background: #EDEFF2; margin-bottom: 18rpx; }
.ts-bar-seg { height: 100%; }
.ts-bar-seg.agree { background: #3E9B34; }
.ts-bar-seg.against { background: #E24B3A; }
.ts-bar-seg.abstain { background: #9AA0A6; }
.ts-bar-seg.none { background: #D6DAE0; }
/* 逐项：每项独立成行，未投单列 */
.ts-tally-rows { display: flex; flex-direction: column; gap: 2rpx; }
.ts-tally-row { display: flex; align-items: center; gap: 14rpx; padding: 12rpx 6rpx; border-bottom: 2rpx solid #F2F2F4; font-size: 29rpx; }
.ts-tally-row:last-child { border-bottom: 0; }
.ts-tally-row.none .ts-row-label { color: #8A8F98; }
.ts-dot { flex-shrink: 0; width: 18rpx; height: 18rpx; border-radius: 50%; }
.ts-dot.agree { background: #3E9B34; }
.ts-dot.against { background: #E24B3A; }
.ts-dot.abstain { background: #9AA0A6; }
.ts-dot.none { background: #D6DAE0; }
.ts-row-label { color: #444; font-weight: 600; }
.ts-row-num { margin-left: auto; font-weight: 800; color: #1f2329; font-variant-numeric: tabular-nums; }
/* 未揭晓：遮罩 + 主任「结束表决」按钮 */
.ts-tally-veilbox { background: #F6F7F9; border: 2rpx dashed #D8DBE0; border-radius: 14rpx; padding: 22rpx; text-align: center; font-size: 26rpx; color: #8A8F98; }
.ts-close-vote { display: block; width: 100%; box-sizing: border-box; margin-top: 16rpx; border: 0; border-radius: 16rpx; background: #C76A00; color: #fff; font-size: 31rpx; font-weight: 700; padding: 22rpx 0; font-family: inherit; }
.ts-close-vote:active { background: #A85800; }
.ts-close-vote[disabled] { background: #E7D8C6; }
.ts-vote-hint { font-size: 24rpx; color: #9AA0A6; margin-top: 10rpx; }
.ts-vote-hint.mine { color: #2E7D32; font-weight: 600; }
/* 先选后交（研究P1）：确认提交按钮——选好才亮，带"提交后不可改"静态提示 */
.ts-vote-submit { display: block; width: 100%; box-sizing: border-box; margin-top: 22rpx; border: 0; border-radius: 16rpx; background: #C76A00; color: #fff; font-size: 31rpx; font-weight: 700; padding: 22rpx 0; font-family: inherit; }
.ts-vote-submit:active { background: #A85800; }
.ts-vote-submit[disabled] { background: #E7D8C6; color: #fff; }
.ts-vote-submit-tip { font-size: 24rpx; font-weight: 400; opacity: 0.92; margin-left: 4rpx; }
/* 已表决：结果卡整块替换选择区（占屏结果态，明确"做完了·不可改"） */
.ts-vote-done { display: flex; align-items: center; flex-wrap: wrap; gap: 6rpx 12rpx; background: #EAF6E5; border: 2rpx solid #9FD290; border-radius: 16rpx; padding: 24rpx; font-size: 31rpx; font-weight: 800; color: #2E7D32; }
.ts-vote-done-mark { display: inline-flex; align-items: center; justify-content: center; width: 40rpx; height: 40rpx; border-radius: 50%; background: #2E9E4B; color: #fff; font-size: 26rpx; margin-right: 4rpx; }
.ts-vote-done-lock { font-size: 24rpx; font-weight: 600; color: #5E8F55; margin-left: auto; }
/* 表决已结束但本人未投 */
.ts-vote-missed { background: #F6F7F9; border: 2rpx solid #E4E6EA; border-radius: 16rpx; padding: 22rpx; font-size: 28rpx; font-weight: 600; color: #8A8F98; text-align: center; }
/* 防从众（研究P1）：表决进行中票数处的占位文案 */
.ts-tally-veil { font-size: 24rpx; color: #9AA0A6; }

/* 通报类议题：通知正文 + 已通报状态 */
.ts-notice { background: #FFFBF3; border: 2rpx solid #F1E2C6; border-radius: 16rpx; padding: 22rpx 22rpx 18rpx; margin-bottom: 18rpx; }
.ts-notice-label { font-size: 26rpx; font-weight: 700; color: #A85800; margin-bottom: 12rpx; }
.ts-notice-body { font-size: 32rpx; color: #1f2329; line-height: 1.7; white-space: pre-wrap; }
.ts-notice-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 18rpx; }
.ts-notice-status { font-size: 26rpx; color: #9AA0A6; font-weight: 600; }
.ts-notice-status.done { color: #2E7D32; }
.ts-notice-read { border: none; background: #3E9B34; color: #fff; font-size: 28rpx; font-weight: 700; border-radius: 14rpx; padding: 14rpx 34rpx; }
.ts-notice-read:active { background: #34842C; }
/* #8：本人已确认「我已读」的状态标 */
.ts-notice-mine { flex-shrink: 0; font-size: 26rpx; font-weight: 700; color: #2E7D32; }
/* #8：主任「标记全体已通报」——单独一行、描边弱化，与委员本人确认区分开 */
.ts-notice-chair { display: flex; align-items: center; gap: 16rpx; margin-top: 16rpx; padding-top: 16rpx; border-top: 2rpx dashed #EBD9B8; }
.ts-notice-forceall { flex-shrink: 0; border: 2rpx solid #C76A00; background: #FFF6EC; color: #A85800; font-size: 26rpx; font-weight: 700; border-radius: 14rpx; padding: 12rpx 24rpx; }
.ts-notice-forceall:active { background: #FBEAD6; }
.ts-notice-chair-tip { font-size: 22rpx; color: #B79A6A; line-height: 1.4; }
.ts-ops { border-top: 2rpx solid #F2F2F4; padding-top: 18rpx; }
.ts-ops-head { font-size: 30rpx; font-weight: 700; color: #1f2329; margin-bottom: 14rpx; }
.ts-empty { font-size: 28rpx; color: #9AA0A6; padding: 18rpx 0 24rpx; }
/* 意见条（方案C 极简两行式）：第一行 姓名+表决标签+时间，第二行 意见摘要+查看；点击整条展开全文 */
.ts-op { padding: 16rpx 0; border-bottom: 2rpx solid #F2F0EC; cursor: pointer; }
.ts-op:last-child { border-bottom: 0; }
.ts-op-l1 { display: flex; align-items: center; gap: 12rpx; margin-bottom: 8rpx; }
.ts-op-name { font-size: 28rpx; font-weight: 600; color: #333; }
/* 表决结果标签：同意绿 / 不同意红 / 弃权灰 / 多选项蓝，与表决按钮同一套语义色 */
.ts-op-vote { flex-shrink: 0; font-size: 22rpx; font-weight: 600; padding: 2rpx 12rpx; border-radius: 8rpx; }
.ts-op-vote.agree { background: #EAF6E5; color: #2E7D32; }
.ts-op-vote.against { background: #FDECEA; color: #C0392B; }
.ts-op-vote.abstain { background: #F2F2F4; color: #5F6570; }
.ts-op-vote.opt { background: #EAF2FD; color: #1F6FB2; }
.ts-op-time { font-size: 22rpx; color: #BBB; margin-left: auto; }
.ts-op-l2 { display: flex; align-items: baseline; gap: 12rpx; }
.ts-op-sum { flex: 1; min-width: 0; font-size: 26rpx; color: #7A756E; line-height: 1.5; word-break: break-all;
  overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; }
.ts-op.open .ts-op-sum { -webkit-line-clamp: unset; color: #1f2329; font-size: 30rpx; }
.ts-op-toggle { flex-shrink: 0; font-size: 24rpx; color: #C76A00; font-weight: 600; }
.ts-op-actions { margin-top: 10rpx; text-align: right; }
.ts-op-actions .ts-op-del { font-size: 24rpx; color: #E74C3C; padding: 6rpx 10rpx; background: none; border: none; }

/* 通报类议题：标题/正文加大两号、间距拉大，只保留宣读确认 */
.ts-sheet.is-notice .ts-titlewrap { max-height: 126rpx; }
.ts-sheet.is-notice .ts-title { font-size: 42rpx; line-height: 1.5; }
.ts-sheet.is-notice .ts-notice { padding: 30rpx 26rpx 26rpx; margin-bottom: 26rpx; }
.ts-sheet.is-notice .ts-notice-label { font-size: 30rpx; margin-bottom: 18rpx; }
.ts-sheet.is-notice .ts-notice-body { font-size: 40rpx; line-height: 1.95; }
.ts-sheet.is-notice .ts-notice-foot { margin-top: 28rpx; }
.ts-sheet.is-notice .ts-notice-status { font-size: 28rpx; }
.ts-op-claim-tag { font-size: 22rpx; padding: 2rpx 10rpx; border-radius: 8rpx; background: #FDECEA; color: #C0392B; cursor: pointer; }
.ts-op-claim-tag:active { opacity: 0.7; }
.ts-op-claim-tag.on { background: #C0392B; color: #fff; }
.ts-op-claimrow { margin-top: 10rpx; }
.ts-op-claim-btn { border: 2rpx solid #F0D9B8; border-radius: 14rpx; background: #FFF9F0; color: #B06A00; font-size: 26rpx; padding: 10rpx 22rpx; }
.ts-op-claim-btn:active { background: #FFF1DC; }

/* 发表意见卡片：暖米底把"写意见"整块框起来，与下方导航区分开 */
.ts-compose { flex-shrink: 0; background: #FCF9F3; border: 2rpx solid #F0EAE0; border-radius: 20rpx; padding: 18rpx 18rpx 16rpx; margin-top: 10rpx; }
.ts-input { flex-shrink: 0; display: flex; align-items: center; gap: 14rpx; background: transparent; }
/* 语音条：录音中/识别中占满输入区，大按钮 */
.ts-voicebar { flex-shrink: 0; padding: 18rpx 6rpx 10rpx; border-top: 2rpx solid #F2F2F4; margin-top: 8rpx; background: #fff; box-sizing: border-box; }
/* 听写中：实时出字区（可滚动，长句不撑破输入区） */
.ts-voice-live { display: flex; align-items: flex-start; gap: 14rpx; max-height: 200rpx; overflow-y: auto; }
.ts-voice-dot { flex-shrink: 0; width: 20rpx; height: 20rpx; margin-top: 12rpx; border-radius: 50%; background: #E74C3C; animation: ts-blink 1s infinite; }
@keyframes ts-blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.25; } }
.ts-voice-live-txt { flex: 1; min-width: 0; font-size: 34rpx; line-height: 1.5; color: #1a1a1a; word-break: break-word; }
.ts-voice-live-txt.ph { color: #9AA0A6; }
.ts-voice-foot { display: flex; align-items: center; gap: 16rpx; margin-top: 16rpx; }
.ts-voice-time { flex: 1; font-size: 26rpx; color: #999; }
/* 完成待确认：结果预览 */
.ts-voice-result { font-size: 34rpx; line-height: 1.5; color: #1a1a1a; max-height: 220rpx; overflow-y: auto; word-break: break-word; padding: 2rpx; }
.ts-voice-txt { font-size: 30rpx; color: #333; }
.ts-voice-txt.busy { display: flex; align-items: center; justify-content: center; gap: 14rpx; color: #E8890C; font-weight: 600; padding: 16rpx 0; }
.ts-voice-spin { width: 28rpx; height: 28rpx; border: 4rpx solid #F0D9BC; border-top-color: #E8890C; border-radius: 50%; animation: ts-ai-spin 0.7s linear infinite; }
.ts-voice-cancel { flex-shrink: 0; border: 2rpx solid #D8DBE0; border-radius: 16rpx; background: #fff; color: #666; font-size: 30rpx; padding: 16rpx 34rpx; }
.ts-voice-done { flex-shrink: 0; border: 0; border-radius: 16rpx; background: var(--c-primary-dark, #E8890C); color: #fff; font-size: 30rpx; font-weight: 700; padding: 18rpx 44rpx; }
.ts-ta { flex: 1; border: 2rpx solid #D8DBE0; border-radius: 18rpx; padding: 16rpx 20rpx; font-size: 30rpx; line-height: 1.4; resize: none; box-sizing: border-box; max-height: 160px; font-family: inherit; }
.ts-ta:focus { border-color: #FFA800; outline: none; }
.ts-send { flex-shrink: 0; background: var(--c-primary-dark, #E8890C); color: #fff; border: 0; border-radius: 18rpx; font-size: 30rpx; font-weight: 700; padding: 18rpx 34rpx; }
.ts-send[disabled] { background: #E3D5C3; }
.ts-input-hint { flex-shrink: 0; font-size: 26rpx; color: #9AA0A6; text-align: center; padding: 16rpx 0 4rpx; border-top: 2rpx solid #F2F2F4; margin-top: 8rpx; }
/* 导航：在输入卡片之外、弹层最底部。上一个=白底描边次要按钮靠左，下一个/完成=实心主按钮+呼吸发光靠右，两端隔开（方案B） */
.ts-nav-row { flex-shrink: 0; display: flex; align-items: center; gap: 14rpx; margin-top: 18rpx; }
.ts-nav-btn { box-sizing: border-box; border: 2rpx solid #D8DBE0; border-radius: 18rpx; background: #F7F8FA; color: #444; font-size: 30rpx; font-weight: 600; padding: 20rpx 44rpx; }
.ts-nav-btn:active { background: #ECEEF1; }
/* 上一个议题：规整的白底描边次要按钮，靠左；把右侧主按钮顶到最右 */
.ts-nav-btn.prev { margin-right: auto; background: #fff; border-color: #D8DBE0; color: #55585E; padding: 20rpx 34rpx; }
.ts-nav-btn.prev:active { background: #EEF0F3; color: #3A3F47; }
/* 下一个议题 / 完成：内容宽度(不占满)、靠右 */
.ts-nav-btn.next, .ts-nav-btn.done { margin-left: auto; }
/* 下一个议题：深橙实心，靠填充/颜色对比突出"主推进"，不再呼吸发光（研究快赢②：发光像"提交/结束"会误导老人，它只是切换页） */
.ts-nav-btn.next { background: #C76A00; border-color: #C76A00; color: #fff; box-shadow: 0 4rpx 12rpx rgba(199,106,0,0.22); }
.ts-nav-btn.next:active { background: #A85800; border-color: #A85800; }
/* 最后一个议题的「完成」：绿色实心(收尾/成功语义)，同样去掉发光 */
.ts-nav-btn.done { background: #1F9D57; border-color: #1F9D57; color: #fff; box-shadow: 0 4rpx 12rpx rgba(31,157,87,0.22); }
.ts-nav-btn.done:active { background: #1A8449; border-color: #1A8449; }

/* AI 助手行：AI 帮写(橙) / 语音输入(蓝) 两个等宽按钮并排，卡片内文本框下方，双色区分 */
.ts-ai-row { flex-shrink: 0; display: flex; align-items: stretch; gap: 16rpx; padding: 12rpx 0 0; background: transparent; }
.ts-ai-btn { flex: 1; display: inline-flex; align-items: center; justify-content: center; gap: 10rpx; border: 2rpx solid transparent; border-radius: 14rpx; font-size: 28rpx; font-weight: 600; padding: 18rpx 12rpx; font-variant-numeric: tabular-nums; }
.ts-ai-btn.ai { background: #EEF6E2; border-color: #BAD79A; color: #4F8B34; }
.ts-ai-btn.ai:active { background: #E3F0D2; }
.ts-ai-btn.voice { background: #EAF3FC; border-color: #C6DDF3; color: #1F6FB2; }
.ts-ai-btn.voice:active { background: #DCEAF8; }
.ts-ai-btn[disabled] { opacity: 0.55; }
.ts-ai-undo { font-size: 26rpx; color: #1A73E8; text-decoration: underline; padding: 4rpx; }
.ts-ai-tokenline { flex-shrink: 0; text-align: right; font-size: 22rpx; color: #C2C6CC; padding: 8rpx 2rpx 0; }
.ts-ai-token { margin-left: auto; font-size: 22rpx; color: #C2C6CC; }
/* AI 生成中：转圈图标 + 假进度百分比 */
.ts-ai-prog { display: inline-flex; align-items: center; gap: 8rpx; font-size: 24rpx; font-weight: 600; color: #B06A00; font-variant-numeric: tabular-nums; }
.ts-ai-spin { width: 26rpx; height: 26rpx; border: 4rpx solid #CDE3B4; border-top-color: #4F8B34; border-radius: 50%; animation: ts-ai-spin 0.7s linear infinite; }
@keyframes ts-ai-spin { to { transform: rotate(360deg); } }

/* AI 小助手面板 */
.ts-helper { flex-shrink: 0; border: 2rpx solid #F0D9B8; border-radius: 18rpx; background: #FFFDF8; padding: 20rpx 22rpx; margin-top: 10rpx; }
.ts-helper-head { display: flex; align-items: center; font-size: 30rpx; font-weight: 700; color: #B06A00; margin-bottom: 8rpx; }
.ts-helper-close { margin-left: auto; width: 52rpx; height: 52rpx; line-height: 48rpx; text-align: center; font-size: 40rpx; color: #999; }
.ts-helper-q { font-size: 27rpx; color: #6B5A3E; line-height: 1.55; margin-bottom: 14rpx; }
.ts-helper-ta { width: 100%; background: #fff; }
.ts-helper-btns { display: flex; gap: 16rpx; margin-top: 14rpx; }
.ts-helper-voice { flex: 1; border: 2rpx solid #D8DBE0; border-radius: 16rpx; background: #fff; color: #444; font-size: 29rpx; padding: 18rpx 0; }
.ts-helper-go { flex: 1.4; border: 0; border-radius: 16rpx; background: var(--c-primary-dark, #E8890C); color: #fff; font-size: 29rpx; font-weight: 700; padding: 18rpx 0; }
.ts-helper-go[disabled] { background: #E3D5C3; }
</style>
