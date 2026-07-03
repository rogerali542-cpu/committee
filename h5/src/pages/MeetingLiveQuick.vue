<template>
  <div class="live-page" style="overflow-y:auto;" v-if="detail">

    <!-- 页面对所有身份统一：主任/副主任可操作录音等，其余身份只读+表决/意见 -->
    <PageNav :title="isChair ? '会议录音' : '会议进行'" style="margin:-3.2vw -3.2vw 0;" />

    <!-- AI 工作中：一个遮罩连续覆盖 转写(asr) → 生成纪要(gen)；全部完成后显"已生成会议纪要"、点击进纪要页 -->
    <AiWorkingOverlay :active="polling || extracting || generatingMinutes" :phase="overlayPhase" @confirm="onAiWorkDone" @close="onAiWorkClose" :audioDurSec="asrAudioDurSec" :audioFileSizeByte="asrFileSizeBytes" />

    <!-- 首屏（步骤条已删）：议题 + 签到/录音。撑满一屏高度，把参会名单顶到首屏之下（需要时往下拉才看到） -->
    <div class="lp-fold">

    <div class="lp-info-card">
      <div class="lp-info-head">
        <span class="lp-info-title">会议议题</span>
        <!-- 实时添加议题（主任/副主任）：从录音卡挪进议题卡，弱化成小链接 -->
        <span v-if="isChair" class="lp-add-topic" @click="openAddTopic">+ 临时添加</span>
      </div>
      <div class="lp-info-row top">
        <!-- 固定高度：议题多了先自动缩字号(最多3号)，仍放不下则本区内下拉滚动，卡片大小不变 -->
        <div class="lp-agenda" :class="'lp-agenda--fs' + agendaFontLevel" ref="agendaEl">
          <template v-if="detail.record && detail.record.topics && detail.record.topics.length">
            <div class="lp-agenda-item" v-for="(item, index) in detail.record.topics" :key="item.id" @click="openTopicSheet(item)">
              <span class="lp-agenda-idx">{{ index + 1 }}</span>
              <span class="lp-agenda-title">{{ item.title }}</span>
              <span class="lp-agenda-badge" :class="topicBadgeDone(item) ? 'done' : 'wait'">{{ topicBadgeText(item) }}</span>
              <span class="lp-agenda-arrow">›</span>
            </div>
          </template>
          <span v-else class="lp-agenda-empty">暂无议题</span>
        </div>
      </div>
      <!-- 提示（所有身份）：告知点击议题可表决/发表意见；点它直达第一个待办议题（优先没投票的表决项） -->
      <button v-if="detail.record && detail.record.topics && detail.record.topics.length"
              class="lp-topics-cta" @click="openFirstPendingTopic">💬 点击议题，可表决或发表意见</button>
    </div>

    <!-- 签到卡（精简版，无标题）：一颗签到按钮 + 一行提示（文案按角色） -->
    <div class="lp-card lp-sign" v-if="currentStep === 1">
      <button class="lp-primary-btn narrow" @click="confirmSignIn">{{ signedIn ? (isChair ? '进入录音' : '进入会议') : '签到' }}</button>
      <span class="qk-sign-tip">{{ signedIn ? (isChair ? '已签到，可以开始录音了' : '已签到') : (isChair ? '签到后即可开始录音' : '签到后可表决、发表意见') }}</span>
    </div>

    <!-- 录音操作卡：仅主任/副主任（其余身份只在底部看录音列表、可试听） -->
    <div class="lp-card lp-rec" v-if="currentStep === 2 && isChair">
      <span class="lp-card-title">会议录音</span>

      <!-- 圆圈即录音按钮：灰=点击开始，红(呼吸)=录音中点击暂停；暂停/上传后显示"继续" -->
      <div class="qk-recorder">
        <button class="qk-rec-circle" :class="{ on: recDotOn }" @click="onCircleTap" :disabled="uploading || polling || extracting"><span class="qrc-txt">{{ recCircleLabel }}</span></button>
        <span class="qk-rec-time">{{ timeText }}</span>
      </div>
      <!-- 暂停态补充操作：重新录音（"继续"由圆圈承担；窄款居中） -->
      <div v-if="isPaused" class="qk-rec-actions">
        <button class="lp-primary-btn qk-rec-act narrow40" @click="restartRecording" :disabled="uploading || polling || extracting">
          <span class="qra-main">重新录音</span>
        </button>
      </div>
      <!-- 上传后空闲：先「上传录音」给大模型识别（转写+提炼表决），识别完成后变「继续生成会议纪要」 -->
      <div v-else-if="idleAfterUpload" class="qk-rec-actions">
        <button v-if="needRecognize" class="lp-primary-btn qk-rec-act gen-minutes" @click="uploadAndRecognize" :disabled="uploading || polling || extracting || generatingMinutes">
          <span class="qra-main">上传录音</span>
        </button>
        <button v-else class="lp-primary-btn qk-rec-act gen-minutes" @click="continueGenerateMinutes(false)" :disabled="uploading || polling || extracting || generatingMinutes">
          <span class="qra-main">继续生成会议纪要</span>
        </button>
      </div>
      <!-- 上传中提示 -->
      <div v-if="uploading" class="qk-seg-hint">⏳ 正在上传录音…</div>
      <!-- "结束录音并上传"：上传后空闲态不显示（改用上面的"继续上传录音/开始转写"） -->
      <button v-if="!idleAfterUpload" class="lp-ghost-btn finish-upload" :class="{ muted: !canUpload }" @click="finishRecord" :disabled="uploading || polling || extracting">
        结束录音并上传
      </button>

      <!-- 只要有已上传的录音就常驻「生成会议纪要」入口；未识别则先识别再生成，已识别则直接生成。
           idleAfterUpload 且已识别时由上面的"继续生成会议纪要"承担，避免重复。 -->
      <button v-if="hasSavedRecordings && !recActive && !isPaused && !uploading && !generatingMinutes && !(idleAfterUpload && !needRecognize)"
        class="lp-primary-btn gen-standalone" @click="generateMinutesFromRecording" :disabled="polling || extracting">
        <span class="qra-main">生成会议纪要</span>
      </button>

    </div>

    <!-- 原「录音转写」(step3 选片转写) 和「整理会议纪要」(step4 核对/AI生成/手写) 两步已并入录音页的一键「生成会议纪要」，均删除 -->

    <!-- 人员名单（仅逐人状态，无标题/进度条）：首屏可见，录音人带红点标签 -->
    <div class="lp-card lp-roster" v-if="signinStats.total">
      <div class="lr-list">
        <div class="ls-pop-item" v-for="a in signinStats.list" :key="a.userRoleId">
          <span class="ls-dot" :class="a.signedIn ? 'on' : (a.declined ? 'off' : 'wait')"></span>
          <span class="ls-name">{{ a.name }}</span>
          <span class="ls-role">{{ a.role }}</span>
          <span v-if="a.isSelf && (recActive || isPaused)" class="lr-rec-tag" :class="{ paused: isPaused }"><span class="lr-rec-dot"></span>{{ recActive ? '正在录音' : '已暂停' }}</span>
          <span class="ls-state" :class="a.signedIn ? 'on' : (a.declined ? 'off' : 'wait')">{{ a.signedIn ? '已签到' : (a.declined ? '缺席' : '未签到') }}</span>
        </div>
      </div>
    </div>

    </div><!-- /lp-fold -->

    <!-- 已录制的录音文件（可试听/删除）：沉到页面底部，不占首屏、不挡人员列表 -->
    <div class="lp-card qk-rec-list" v-if="currentStep === 2 && recordings.length">
      <div class="qk-rec-list-head">已录制 {{ recordings.length }} 段</div>
      <div class="qk-rec-list-item" v-for="(item, idx) in recordings" :key="item.id">
        <span class="qrl-idx">{{ idx + 1 }}</span>
        <div class="qrl-info">
          <span class="qrl-name">第 {{ idx + 1 }} 段 · {{ fmtDur(item.durationSec) }}</span>
          <span class="qrl-meta">{{ fmtTime(item.createdAt) }}</span>
        </div>
        <span class="qrl-play" :class="{ on: playingId === item.id }" @click="togglePlay(item)">{{ playingId === item.id ? '⏸' : '▶' }}</span>
        <span v-if="isChair && !polling && !extracting" class="qrl-del" @click="deleteRecording(item, idx)">删除</span>
      </div>
    </div>

    <!-- 签到标题+进度条+统计：沉在最底下，需要时往下拉查看 -->
    <div class="lp-card lp-roster-stats" v-if="signinStats.total">
      <div class="ls-head">
        <span class="lp-card-title">参会人员</span>
        <span class="ls-count" :class="signinLevel" style="margin-left:auto;"><b>{{ signinStats.signedCount }}</b>/{{ signinStats.total }} 已签到</span>
      </div>
      <div class="ls-bar"><div class="ls-fill" :class="signinLevel" :style="{ width: signinStats.pct + '%' }"></div></div>
    </div>

    <div class="lp-nav">
      <button class="lp-nav-btn ghost" @click="exitLive">退出</button>
      <button v-if="isChair" class="lp-nav-btn ghost" @click="exportAttendance">导出签到名单</button>
    </div>

    <!-- 议题弹层：表决 + 意见（点议题行打开）；下一个议题直接切换 -->
    <TopicSheet :meeting-id="meetingId" :topic="sheetTopic" :interactive="detail.stage === 'ongoing'"
                :signed-in="signedIn" :is-chair="isChair" :has-prev="sheetHasPrev" :has-next="sheetHasNext"
                @close="sheetTopicId = null" @changed="loadDetail" @prev="gotoPrevTopic" @next="gotoNextTopic" />

    <!-- 转录文本查看 -->
    <div v-if="transcriptVisible" class="qk-modal-mask" @click="closeTranscript">
      <div class="qk-transcript-sheet" @click.stop="noop">
        <div class="qk-sheet-head">
          <span class="qk-modal-title">语音转录文本</span>
          <span class="qk-sheet-close" @click="closeTranscript">×</span>
        </div>
        <div class="qk-transcript-tabs">
          <span class="qk-transcript-tab" :class="transcriptMode === 'short' ? 'on' : ''" @click="switchTranscriptMode('short')">摘要</span>
          <span class="qk-transcript-tab" :class="transcriptMode === 'full' ? 'on' : ''" @click="switchTranscriptMode('full')">全文</span>
        </div>
        <div class="qk-transcript-scroll" style="overflow-y:auto;">
          <div v-if="transcriptMode === 'short'">
            <span class="qk-transcript-body">{{ transcriptPreview || '暂无摘要文本' }}</span>
            <div class="qk-note">摘要用于快速判断转写是否完成；正式匹配仍以全文分段为依据。</div>
          </div>
          <div v-else>
            <div v-if="transcript.length === 0" class="lp-empty">暂无转写原文</div>
            <div class="qk-tr-list" v-else>
              <div class="qk-tr-seg" v-for="seg in transcript" :key="seg.id">
                <div class="qk-tr-meta"><span class="qk-tr-spk">{{ seg.speaker }}</span><span class="qk-tr-time">{{ seg.time }}</span></div>
                <span class="qk-tr-text">{{ seg.text }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 实时添加议题弹窗 -->
    <div v-if="addTopicVisible" class="qk-modal-mask" @click="closeAddTopic">
      <div class="qk-modal" @click.stop="noop">
        <span class="qk-modal-title">实时添加议题</span>
        <div class="qk-input-row">
          <input class="qk-modal-input" placeholder="议题内容" v-model="newTopicForm.title" />
          <button class="voice-input-btn" :class="{ on: voiceOn }" @click="startTopicVoice">🎤 语音输入</button>
        </div>
        <div class="qk-modal-label">议题类型</div>
        <div class="qk-modal-types">
          <span class="qk-type" :class="newTopicForm.type === 'notice' ? 'on' : ''" @click="pickTopicType('notice')">通报事项</span>
          <span class="qk-type" :class="newTopicForm.type === 'discussion' ? 'on' : ''" @click="pickTopicType('discussion')">讨论事项</span>
          <span class="qk-type" :class="newTopicForm.type === 'decision' ? 'on' : ''" @click="pickTopicType('decision')">表决事项</span>
        </div>
        <template v-if="newTopicForm.type === 'decision'">
          <div class="qk-modal-label">表决方式</div>
          <div class="qk-modal-types">
            <span class="qk-type" :class="newTopicForm.decisionType === 'simple' ? 'on' : ''" @click="pickDecisionType('simple')">是 / 否</span>
            <span class="qk-type" :class="newTopicForm.decisionType === 'multi_choice' ? 'on' : ''" @click="pickDecisionType('multi_choice')">多选一</span>
          </div>
        </template>
        <template v-if="newTopicForm.type === 'decision' && newTopicForm.decisionType === 'multi_choice'">
          <div class="qk-modal-label">选项（至少两个）</div>
          <div v-for="(opt, oi) in newTopicForm.options" :key="opt.id" class="qk-opt-row">
            <span class="qk-opt-num">{{ oi + 1 }}.</span>
            <input class="qk-modal-input qk-opt-input" :placeholder="'选项' + (oi + 1)" v-model="opt.label" />
            <span v-if="newTopicForm.options.length > 1" class="qk-opt-del" @click="removeTopicOption(oi)">×</span>
          </div>
          <span class="qk-link" @click="addTopicOption" style="display:block;margin-bottom:14rpx;">+ 添加选项</span>
        </template>
        <div class="qk-modal-btns">
          <button class="lp-ghost-btn" @click="closeAddTopic">取消</button>
          <button class="lp-primary-btn" @click="submitAddTopic">添加</button>
        </div>
        <span class="qk-modal-note">重大议题无法在会议进行中添加</span>
      </div>
    </div>

    <!-- 议题语音输入弹层（Web Speech 流式识别，确认后写入议题内容框；与创建页同款交互） -->
    <div v-if="voiceOn" class="voice-modal-mask">
      <div class="voice-modal">
        <div class="vm-body">
          <div class="vm-wave"><span></span><span></span><span></span><span></span><span></span></div>
          <div class="vm-text">
            <span v-if="voiceFinal || voiceInterim">{{ voiceFinal }}<span class="vm-interim">{{ voiceInterim }}</span></span>
            <span v-else class="vm-placeholder">请说话输入议题</span>
          </div>
        </div>
        <div class="vm-actions">
          <button class="btn btn-ghost" @click="cancelTopicVoice">取消</button>
          <button class="btn btn-ghost" @click="retryTopicVoice">重新输入</button>
          <button class="btn btn-primary" @click="confirmTopicVoice">确认</button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted, onActivated, onUnmounted, onDeactivated } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast, showModal, showActionSheet } from '@/utils/ui'
import { navigateTo, redirectTo, navigateBack } from '@/utils/navigate'
import { getStorage, setStorage, removeStorage } from '@/utils/storage'
import { useRecorder } from '@/composables/useRecorder'
import { applyHotwords } from '@/utils/helpers'
import PageNav from '@/components/PageNav.vue'
import AiWorkingOverlay from '@/components/AiWorkingOverlay.vue'
import TopicSheet from '@/components/TopicSheet.vue'

const route = useRoute()
const rec = useRecorder()

const POLL_INTERVAL = 1500
const MAX_POLL_COUNT = 120
const QUICK_STATE_PREFIX = 'committee_quick_meeting_state_'

// ═══════════════════════════════════════════════
// 顶层纯函数（自 meeting-live-quick.js 1:1 迁移）
// ═══════════════════════════════════════════════
function voteHintText(hint) {
  if (!hint) return '需人工确认'
  if (hint.result === 'passed') return '建议通过'
  if (hint.result === 'rejected') return '建议不通过'
  return '需人工确认'
}

function resultLabel(result) {
  if (result === 'passed') return '通过'
  if (result === 'rejected') return '未通过'
  if (result === 'abstain') return '弃权'
  return '需人工确认'
}

function topicTypeLabel(type) {
  if (type === 'notice') return '通报事项'
  if (type === 'discussion') return '讨论事项'
  if (type === 'major') return '重大表决'
  return '表决事项'
}

function reviewLevelLabel(level) {
  if (level === 'auto') return '建议匹配'
  if (level === 'review') return '疑似匹配'
  return '待人工处理'
}

function asPercent(value) {
  if (typeof value !== 'number') return null
  return Math.round(value > 1 ? value : value * 100)
}

function voteResultLabel(result) {
  if (result === 'passed') return '通过'
  if (result === 'rejected') return '否决'
  return '不明确'
}

// rule_kb 抽取字段 -> 展示用列表（每项一行：字段名 + 文本 + 佐证片段）
function mapExtractedFields(hit) {
  const fields = (hit && hit.extractedFields) || []
  return fields.map(function (f) {
    const values = f.values || []
    let text
    if (f.type === 'keyword_mapping') {
      // 表决结果归一化为中文；资金来源/责任方直接用标签
      text = f.field === '表决结果' ? voteResultLabel(f.normalized) : (f.normalized || '')
    } else {
      // regex：把抽到的具体值去重拼接
      const seen = {}
      const distinct = []
      values.forEach(function (v) {
        if (v && v.value && !seen[v.value]) { seen[v.value] = 1; distinct.push(v.value) }
      })
      text = distinct.join('、')
    }
    return {
      field: f.field,
      text: text,
      evidences: values.map(function (v) { return { value: v.value, text: v.evidence || '' } })
    }
  }).filter(function (f) { return f.text })
}

function formatMs(ms) {
  const total = Math.floor((ms || 0) / 1000)
  const m = Math.floor(total / 60)
  const s = total % 60
  return String(m).padStart(2, '0') + ':' + String(s).padStart(2, '0')
}

function decorateEvidence(topic) {
  const matches = topic.segmentMatches || []
  const first = matches[0]
  const last = matches.length > 1 ? matches[matches.length - 1] : null
  const range = first
    ? (first.time || '') + (last && last.time && last.time !== first.time ? ' - ' + last.time : '')
    : ''
  const expanded = !!topic.evidenceExpanded
  return Object.assign({}, topic, {
    evidenceRange: range,
    evidencePreview: matches.slice(0, 2),
    visibleSegments: expanded ? matches : matches.slice(0, 2),
    evidenceHiddenCount: Math.max(0, matches.length - 2),
    evidenceExpanded: expanded
  })
}

function mapTopic(hit, fallback, voteTotalArg) {
  const fallbackTitle = typeof fallback === 'string' ? fallback : (fallback && fallback.title)
  // voteTotal: 实到人数，用于"全票/一致通过"按人数补齐预填票数
  const type = (hit && hit.type) || (fallback && fallback.type) || 'decision'
  const voteRequired = hit && typeof hit.voteRequired === 'boolean'
    ? hit.voteRequired
    : (fallback && typeof fallback.voteRequired === 'boolean' ? fallback.voteRequired : !(type === 'notice' || type === 'discussion'))
  const confidence = hit && hit.voteHint && typeof hit.voteHint.confidence === 'number'
    ? Math.round(hit.voteHint.confidence * 100)
    : null
  // 根据 voteHint 自动预填票数（人工可改、需确认）
  const vh = hit && hit.voteHint
  const total = Number(voteTotalArg) || 0
  let voteFor = 0, voteAgainst = 0, voteAbstain = 0, aiVote = '', aiVoteLabel = ''
  if (voteRequired && vh) {
    if (vh.source === 'explicit') {
      voteFor = Number(vh.forVotes) || 0; voteAgainst = Number(vh.agVotes) || 0; voteAbstain = Number(vh.abVotes) || 0
      aiVote = 'explicit'; aiVoteLabel = '按录音播报票数'
    } else if (vh.source === 'unanimous' && vh.unanimous && total > 0) {
      voteFor = total; voteAgainst = 0; voteAbstain = 0
      aiVote = 'unanimous'; aiVoteLabel = '录音为全票/一致通过'
    } else if (vh.source === 'counted') {
      voteFor = Number(vh.forVotes) || 0; voteAgainst = Number(vh.agVotes) || 0; voteAbstain = Number(vh.abVotes) || 0
      aiVote = 'counted'; aiVoteLabel = '按发言逐个计数(粗略)'
    }
  }
  let result = (vh && vh.result) || 'unclear'
  if (voteRequired && aiVote) {
    const need = total > 0 ? Math.floor(total / 2) + 1 : 1
    const counted = voteFor + voteAgainst + voteAbstain
    if (voteFor >= need) result = 'passed'
    else if (total > 0 && counted >= total) result = 'rejected'
    else result = 'unclear'
  }
  const matchConfidence = hit && typeof hit.confidence === 'number' ? asPercent(hit.confidence) : null
  const matchedSegments = (hit && hit.matchedSegments) || []
  const segmentMatches = ((hit && hit.segmentMatches) || []).map(function (m) {
    const reasons = m.reasons || []
    return {
      segmentIndex: m.segmentIndex,
      speaker: m.speaker || '',
      time: typeof m.startMs === 'number' ? formatMs(m.startMs) : '',
      text: m.text || '',
      score: typeof m.score === 'number' ? asPercent(m.score) : null,
      level: m.level || 'review',
      levelLabel: reviewLevelLabel(m.level),
      reasons: reasons,
      reasonText: reasons.join('；'),
      scoreDetail: m.scoreDetail || null
    }
  })
  return decorateEvidence({
    id: (hit && (hit.topicId || hit.tempId)) || fallbackTitle,
    title: (hit && hit.title) || fallbackTitle || '未命名议题',
    type: type,
    typeLabel: topicTypeLabel(type),
    voteRequired: voteRequired,
    suggest: voteRequired ? voteHintText(hit && hit.voteHint) : '记录要点',
    result: result,
    resultLabel: resultLabel(result),
    voteFor: voteFor,
    voteAgainst: voteAgainst,
    voteAbstain: voteAbstain,
    voteCounted: voteFor + voteAgainst + voteAbstain,
    aiVote: aiVote,
    aiVoteLabel: aiVoteLabel,
    // 表决类议题且 AI 未能确定（无票数来源或结果不明）时，提示主任核对
    needsReview: voteRequired && (result === 'unclear' || !aiVote),
    confidence: confidence,
    matchConfidence: matchConfidence,
    reviewLevel: (hit && hit.reviewLevel) || (matchedSegments.length ? 'review' : 'empty'),
    reviewLevelLabel: reviewLevelLabel(hit && hit.reviewLevel),
    needManualReview: hit && typeof hit.needManualReview === 'boolean' ? hit.needManualReview : true,
    summaryDraft: (hit && hit.summaryDraft) || '',
    extractedFields: mapExtractedFields(hit),
    segmentMatches: segmentMatches,
    matchedSegments: matchedSegments,
    matchedCount: segmentMatches.length || matchedSegments.length,
    evidenceExpanded: false,
    confirmed: false
  })
}

// ═══════════════════════════════════════════════
// data（自 Page.data 1:1 迁移到 ref）
// ═══════════════════════════════════════════════
const type = ref('committee')
const meetingId = ref(null)
const detail = ref(null)
// 议题弹层：存 id、从最新 detail 里取对象（轮询/刷新后计票等自动更新）
const sheetTopicId = ref(null)
const sheetTopic = computed(() => {
  const list = (detail.value && detail.value.record && detail.value.record.topics) || []
  return list.find(t => t.id === sheetTopicId.value) || null
})
function openTopicSheet(item) { sheetTopicId.value = item.id }
// 弹层"上一个/下一个议题"：当前议题在列表中的位置 + 切换
function _sheetTopicIndex() {
  const list = (detail.value && detail.value.record && detail.value.record.topics) || []
  return { list, i: list.findIndex(t => t.id === sheetTopicId.value) }
}
const sheetHasPrev = computed(() => _sheetTopicIndex().i > 0)
const sheetHasNext = computed(() => { const { list, i } = _sheetTopicIndex(); return i >= 0 && i < list.length - 1 })
function gotoPrevTopic() { const { list, i } = _sheetTopicIndex(); if (i > 0) sheetTopicId.value = list[i - 1].id }
function gotoNextTopic() { const { list, i } = _sheetTopicIndex(); if (i >= 0 && i < list.length - 1) sheetTopicId.value = list[i + 1].id }
// 委员引导按钮入口：优先打开还没投票的表决议题，其次第一个议题
function openFirstPendingTopic() {
  const list = (detail.value && detail.value.record && detail.value.record.topics) || []
  const pending = list.find(t => t.voteRequired && !t.myVote)
  const target = pending || list[0]
  if (target) sheetTopicId.value = target.id
}
// 议题状态标签：显示"待/已"状态而非属性。状态由后端 TopicVO 字段驱动，
// 录音经 ASR 识别+确认后 status/opinionCount 会更新，loadDetail 刷新后标签自动翻成"已"。
function topicBadgeDone(item) {
  if (item.voteRequired) return item.status === 'passed' || item.status === 'failed' // 表决达成(含ASR识别票数确认后)
  return (item.opinionCount || 0) > 0 // 通报/讨论：录音里被提到/有意见 = 已处理
}
function topicBadgeText(item) {
  const done = topicBadgeDone(item)
  if (item.voteRequired) return done ? '已表决' : '待表决'
  if (item.type === 'notice') return done ? '已通报' : '待通报'
  return done ? '已讨论' : '待讨论'
}

// 议题区固定高度，字体自适应：内容放不下时逐级缩小(最多3号)，仍放不下则本区下拉滚动（卡片大小不变）
const agendaEl = ref(null)
const agendaFontLevel = ref(0) // 0=原字号，1/2/3=各缩一号
// 用 rAF 递归逐档缩小：每档改字号后等下一帧重新测量，避免"固定高度样式未生效时误判放得下"
function fitAgenda() {
  const raf = (typeof requestAnimationFrame !== 'undefined') ? requestAnimationFrame : ((cb) => setTimeout(cb, 16))
  agendaFontLevel.value = 0
  const step = () => {
    const e = agendaEl.value
    if (!e) return
    if (agendaFontLevel.value < 2 && e.scrollHeight > e.clientHeight + 1) { // 最多缩到 fs2(28rpx)，再放不下就滚动
      agendaFontLevel.value += 1
      raf(step)
    }
  }
  raf(() => raf(step)) // 双帧：等固定高度布局稳定后再开始测量
}
// 议题数量变化时重新适配（flush:post 确保 DOM 已更新）
watch(() => (detail.value && detail.value.record && detail.value.record.topics
  ? detail.value.record.topics.length : 0), () => { fitAgenda() }, { flush: 'post' })
// 步骤条 UI 已删（steps 数组随之移除）；currentStep 仍驱动 签到卡(1)/录音卡(2) 的切换
const currentStep = ref(1)
const signedIn = ref(false)        // 后端持久态：会议开始时已清空，ongoing 阶段即"本人会上是否已签到"（按用户区分、服务器持久）
const selfAttendance = ref(null)

// 录音计时显示直接复用 useRecorder（recording/paused/hasRecording 在脚本里用 rec.* 读取）
const timeText = rec.timeText
// useRecorder 会话模型 → 原四态展示。注意：暂停时 recording 仍为 true，需先判 paused。
// 录音中(未停) = recording.value；活动录制(出红点) = recording && !paused。
const recActive = computed(() => rec.recording.value && !rec.paused.value)
const recDotOn = computed(() => recActive.value)
// 是否已有“上传保存”的录音（决定圆圈按钮是“继续”还是“开始/重录”）
const hasSavedRecordings = computed(() => (recordings.value || []).length > 0)
// 暂停态：显示"继续录音/重新录音"并排按钮（继续=恢复同一会话，自然合并成一个文件）
const isPaused = computed(() => rec.recording.value && rec.paused.value)
// 当前是否有"可上传的新内容"（正在录 / 暂停中 / 内存里有还没上传的录音）。
// 上传成功后已 rec.reset()，此值变 false → "结束录音并上传"置灰，避免重复上传同一段。
const canUpload = computed(() => recActive.value || isPaused.value || rec.hasRecording.value)
// 已上传过录音、且当前没有新录音在手 → 上传后的"空闲"态，引导继续录下一段
const idleAfterUpload = computed(() => !rec.recording.value && !rec.hasRecording.value && hasSavedRecordings.value)
// 本轮识别已覆盖的录音 id（识别成功/恢复历史转写时回填）——用它判断是否还有新录音没识别，
// 不依赖 recordings.asrStatus（桩模式不落该字段）
const recognizedIds = ref([])
// 还有录音没经大模型识别 → 按钮显示「上传录音」；识别完变「继续生成会议纪要」
const needRecognize = computed(() => !generated.value
  || (recordings.value || []).some(r => r.asrStatus !== 'done' && !recognizedIds.value.includes(r.id)))
// 圆圈按钮（圆圈即录音键）文案：四字状态、圈内两行显示（开始/录音 各占一行）
const recCircleLabel = computed(() => {
  if (recActive.value) return '暂停录音'
  if (isPaused.value) return '继续录音'
  if (idleAfterUpload.value) return '继续录音'
  if (rec.hasRecording.value) return '重新录音'
  return '开始录音'
})
// 圆圈点击：暂停态走恢复（toggleRecord 不处理暂停），其余状态交给 toggleRecord 原逻辑
function onCircleTap() {
  if (isPaused.value) { resumeRecording(); return }
  toggleRecord()
}

const uploading = ref(false)
const polling = ref(false)
const extracting = ref(false)
const overlayPhase = ref('asr') // AI 工作遮罩阶段：转写=asr / 生成纪要=gen（一键流程内切换）
const asrAudioDurSec = ref(0)    // 本次待转写录音总时长(秒)
const asrFileSizeBytes = ref(0)  // 本次待转写文件大小(bytes)，用于更准确估算处理耗时
const taskId = ref('')
const asrStatus = ref('')
const processText = ref('正在上传录音...')
const finishText = ref('录音已转写，规则抽取结果已生成')
const generated = ref(false)
const extraction = ref(null)

const presetTopics = ref([])
const aiTopics = ref([])
const transcript = ref([])
const transcriptPreview = ref('')
const transcriptFullText = ref('')
const transcriptCharCount = ref(0)
const transcriptVisible = ref(false)
const transcriptMode = ref('short')
const ending = ref(false)
// 重做后的「最后一步」：AI 纪要审核
const minutesGenerated = ref(false)   // 是否已生成 AI 纪要草稿
const generatingMinutes = ref(false)  // 生成中（按钮 loading 态）
const extraOpen = ref(false)          // 「AI 额外发现」是否展开

// 角色 / 资料 / 实时议题 / 录音列表
const isChair = ref(false)
const myRoleId = ref(null)
const recordings = ref([])
// 转写页录音回放：当前正在播放的录音 id（null=未播放）
const playingId = ref(null)
const materials = ref([])
const signedInList = ref([])
const voteTotal = ref(0)
const voteNeed = ref(0)
// 主任签到统计：全体参会名单（含未签到）+ 悬浮面板开关
const attendanceList = ref([])
const signinPanelOpen = ref(false)
const signinStats = computed(() => {
  const raw = attendanceList.value || []
  const total = raw.length
  const signedCount = raw.filter(a => a.signedIn).length
  // 正在录音时，把录音人(本人)排到名单最前，一眼看到谁在录
  let list = raw
  if (recActive.value || isPaused.value) {
    list = [...raw]
    const i = list.findIndex(a => a.isSelf)
    if (i > 0) { const [self] = list.splice(i, 1); list.unshift(self) }
  }
  const pct = total ? Math.round((signedCount / total) * 100) : 0
  return { total, signedCount, pct, list }
})
// 进度条颜色档位：全签到=绿，过半=黄，不足过半=红
const signinLevel = computed(() => {
  const s = signinStats.value
  if (!s.total) return 'low'
  if (s.signedCount >= s.total) return 'full'
  if (s.pct >= 50) return 'mid'
  return 'low'
})
const addTopicVisible = ref(false)
const newTopicForm = reactive({ title: '', type: 'discussion', decisionType: 'none', options: [] })

// 选片高亮（this._transcribingRecordingId）改为响应式以驱动样式
const _transcribingRecordingId = ref('')

// 转写页多选：已勾选待转写的录音 id；已转写(done)的会被后端自动并入，无需勾选
const pickedIds = ref([])
// 已知录音 id（非响应式）：用于在 loadDetail 时只对“新出现”的录音默认勾选，保留用户手动取消
let _knownRecordingIds = new Set()
const hasDoneRecordings = computed(() => (recordings.value || []).some(r => r.asrStatus === 'done'))

// 隐藏文件输入（chooseAudioFile 用）
const audioFileInput = ref(null)

// onLoad/this 级上下文
let _pollCount = 0
let _asrDoneHandled = false
let _pollTimer = null
let _booted = false
let _attendanceTimer = null // 主任签到进度的轻量轮询
let _playAudio = null       // 转写页录音回放用的 HTMLAudioElement

// ═══════════════════════════════════════════════
// 生命周期
// ═══════════════════════════════════════════════
onMounted(() => {
  // onLoad(options)
  type.value = route.query.type === 'owner' ? 'owner' : 'committee'
  meetingId.value = route.query.meetingId
  if (type.value === 'owner') {
    toast({ title: '业主大会模块已停用', icon: 'none' })
    setTimeout(function () { navigateBack() }, 500)
    return
  }
  _pollCount = 0
  _booted = true
  if (typeof window !== 'undefined') window.addEventListener('beforeunload', _beforeUnloadGuard)
  // useRecorder 内部已管理录音生命周期与计时，无需 initRecorder
  loadDetail()
  // 主任端每 12s 轻量刷新签到进度（委员陆续签到时进度自动增加）
  _attendanceTimer = setInterval(refreshAttendance, 12000)
  if (typeof window !== 'undefined') window.addEventListener('resize', fitAgenda)
})

// onShow → onMounted 首跑 + onActivated（保持热切回页刷新；但避免与 onMounted 重复首跑）
onActivated(() => {
  if (!_booted) return
})

// onUnload → onUnmounted
onUnmounted(() => {
  persistQuickState()
  clearPoll()
  if (_attendanceTimer) { clearInterval(_attendanceTimer); _attendanceTimer = null }
  if (_playAudio) { try { _playAudio.pause() } catch (e) {} _playAudio = null }
  if (typeof window !== 'undefined') {
    window.removeEventListener('beforeunload', _beforeUnloadGuard)
    window.removeEventListener('resize', fitAgenda)
  }
  rec.reset() // 释放麦克风
})

// onHide → onDeactivated
onDeactivated(() => {
  persistQuickState()
})

// ═══════════════════════════════════════════════
// 方法（自 Page 方法 1:1 迁移）
// ═══════════════════════════════════════════════
function quickStateKey() {
  return QUICK_STATE_PREFIX + meetingId.value
}

function persistQuickState(extra) {
  if (!meetingId.value || !detail.value || detail.value.stage !== 'ongoing') return
  const state = Object.assign({
    meetingId: meetingId.value,
    savedAt: Date.now(),
    currentStep: currentStep.value,
    generated: generated.value,
    taskId: taskId.value,
    asrStatus: asrStatus.value,
    processText: processText.value,
    finishText: finishText.value,
    extraction: extraction.value,
    presetTopics: presetTopics.value,
    aiTopics: aiTopics.value,
    transcript: transcript.value,
    transcriptPreview: transcriptPreview.value,
    transcriptFullText: transcriptFullText.value,
    transcriptCharCount: transcriptCharCount.value
  }, extra || {})
  setStorage(quickStateKey(), state)
}

function restoreQuickState(signedInArg) {
  let saved = getStorage(quickStateKey(), null)
  if (!saved || String(saved.meetingId) !== String(meetingId.value)) return false

  const transcriptVal = saved.transcript || []
  const transcriptState = buildTranscriptState(transcriptVal)
  // 原 step3(录音转写)/step4(整理纪要) 已并入录音页一键「生成会议纪要」；页面只剩 签到(1)/录音(2) 两步。
  // 签到过就停录音页，否则回签到步——绝不再恢复到"三步全完成、只剩会议议题"的空页。
  const step = signedInArg ? 2 : 1

  currentStep.value = step
  generated.value = !!saved.generated
  if (saved.generated) recognizedIds.value = (recordings.value || []).map(r => r.id) // 恢复的转写已覆盖当前录音
  taskId.value = saved.taskId || ''
  asrStatus.value = saved.asrStatus || ''
  processText.value = saved.processText || processText.value
  finishText.value = saved.finishText || finishText.value
  extraction.value = saved.extraction || null
  presetTopics.value = saved.presetTopics && saved.presetTopics.length ? saved.presetTopics.map(decorateEvidence) : presetTopics.value
  aiTopics.value = (saved.aiTopics || []).map(decorateEvidence)
  transcript.value = transcriptVal
  transcriptVisible.value = false
  uploading.value = false
  polling.value = false
  extracting.value = false
  transcriptFullText.value = transcriptState.transcriptFullText || saved.transcriptFullText || ''
  transcriptPreview.value = transcriptState.transcriptPreview || saved.transcriptPreview || ''
  transcriptCharCount.value = transcriptState.transcriptCharCount || saved.transcriptCharCount || 0

  if (signedInArg && saved.taskId && !saved.generated && saved.asrStatus === 'done') {
    _asrDoneHandled = false
    handleAsrDone({ taskId: saved.taskId, status: 'done' })
  } else if (signedInArg && saved.taskId && !saved.generated && saved.asrStatus !== 'failed') {
    currentStep.value = 2 // 原 step3「录音转写」页已删；停在录音页，转写进度由 AI 工作遮罩展示
    polling.value = true
    processText.value = saved.processText || statusText(saved.asrStatus || 'pending')
    startPoll(saved.taskId)
  }
  return true
}

function clearQuickState() {
  if (!meetingId.value) return
  removeStorage(quickStateKey())
}

async function loadDetail() {
  try {
    const d = await api.committeeDetail(meetingId.value)
    const raw = (d.record && d.record.topics) || []
    const presets = raw.map(t => mapTopic(null, t))
    const self = getSelfAttendance(d)
    const isSigned = !!(self && self.signedIn)
    const role = getStorage('activeRole', null)
    const roleId = role && role.id ? role.id : null
    const recObj = d.record || {}
    const recs = recObj.recordings || []
    const signed = (recObj.attendances || []).filter(function (a) { return a.signedIn })
    const total = signed.length

    detail.value = d
    presetTopics.value = presets
    selfAttendance.value = self
    signedIn.value = isSigned
    isChair.value = d.userView === 'chair'
    myRoleId.value = roleId
    recordings.value = recs
    reconcilePickedIds(recs)
    materials.value = d.materials || []
    signedInList.value = signed
    attendanceList.value = recObj.attendances || []
    voteTotal.value = total
    voteNeed.value = Math.floor(total / 2) + 1
    // 本人已签到（后端 signedIn，会议开始时已清空、按用户区分）→ 自动进入录音步，无需重复签到
    currentStep.value = isSigned && currentStep.value === 1 ? 2 : currentStep.value

    if (d.stage === 'ongoing') {
      const restored = restoreQuickState(isSigned)
      if (isSigned && (!restored || (!generated.value && !taskId.value))) tryRestoreGeneratedFromServer()
    } else {
      clearQuickState()
    }
  } catch (e) {
    toast({ title: '加载失败', icon: 'none' })
  }
}

// 仅刷新签到名单/进度，不动步骤机与录音状态——供参会人查看实时签到（定时 + 手动）
async function refreshAttendance() {
  if (!meetingId.value) return
  if (!detail.value || detail.value.stage !== 'ongoing') return
  try {
    const d = await api.committeeDetail(meetingId.value)
    const atts = (d.record && d.record.attendances) || []
    attendanceList.value = atts
    const signed = atts.filter(function (a) { return a.signedIn })
    signedInList.value = signed
    voteTotal.value = signed.length
    voteNeed.value = Math.floor(signed.length / 2) + 1
  } catch (e) { /* 静默：签到刷新失败不打扰主任 */ }
}

async function tryRestoreGeneratedFromServer() {
  if (!meetingId.value || !signedIn.value || generated.value) return
  if (typeof api.committeeQuickTranscript !== 'function' || typeof api.committeeQuickExtract !== 'function') return
  try {
    const transcriptRaw = await api.committeeQuickTranscript(meetingId.value)
    const tr = mapTranscript(transcriptRaw)
    if (!tr.length) return
    const ext = await api.committeeQuickExtract(meetingId.value)
    const mapped = mapExtraction(ext)
    currentStep.value = 2 // 原 step4「整理纪要」已删；已有转写时停在录音页，用一键「生成会议纪要」继续
    extraction.value = ext
    presetTopics.value = mapped.presetTopics
    aiTopics.value = mapped.aiTopics
    transcript.value = tr
    transcriptVisible.value = false
    uploading.value = false
    polling.value = false
    extracting.value = false
    generated.value = true
    recognizedIds.value = (recordings.value || []).map(r => r.id) // 历史转写已覆盖当前录音
    finishText.value = '已恢复上次转写与议题匹配结果'
    const tState = buildTranscriptState(tr)
    transcriptFullText.value = tState.transcriptFullText
    transcriptPreview.value = tState.transcriptPreview
    transcriptCharCount.value = tState.transcriptCharCount
    persistQuickState()
  } catch (e) {}
}

function getSelfAttendance(d) {
  const attendances = d && d.record ? (d.record.attendances || []) : []
  return attendances.find(function (a) { return a.isSelf }) || null
}

async function confirmSignIn() {
  // 已签到（后端 signedIn=true）→ 直接进入录音步
  if (signedIn.value) {
    currentStep.value = 2
    persistQuickState()
    return
  }
  const res = await showModal({
    title: '入会签到',
    content: isChair.value
      ? '请确认本人已进入本次业委会会议。签到后将进入录音转写流程。'
      : '请确认本人已进入本次业委会会议。签到后即可对议题表决、发表意见。',
    confirmText: '签到',
    cancelText: '再看看'
  })
  if (!res.confirm) return
  try {
    await api.committeeSelfToggle(meetingId.value, 'signedIn')
    toast({ title: '已签到', icon: 'success' })
    signedIn.value = true
    currentStep.value = 2
    persistQuickState()
    loadDetail()
  } catch (e) {
    toast({ title: e.message || '确认失败', icon: 'none' })
  }
}

// 录音进行中（含暂停、尚未上传）时拦截刷新/关页，避免内存里的录音被丢弃
function _beforeUnloadGuard(e) {
  if (rec.recording.value) { e.preventDefault(); e.returnValue = '' }
}

async function toggleRecord() {
  if (type.value !== 'committee') {
    toast({ title: '快速录音暂先支持业委会会议', icon: 'none' })
    return
  }
  if (!signedIn.value) {
    toast({ title: '请先签到', icon: 'none' })
    return
  }
  if (!rec.supported.value) {
    toast({ title: '当前浏览器不支持录音（需 HTTPS 且允许麦克风）', icon: 'none' })
    return
  }
  if (uploading.value || polling.value || extracting.value) return

  // 录音中且未暂停 → 暂停（个别 iOS 不支持暂停，pause 静默失败时提示用户）
  if (rec.recording.value && !rec.paused.value) {
    rec.pause()
    if (!rec.paused.value) {
      toast({ title: '本设备不支持暂停，可直接点“结束录音并上传”', icon: 'none' })
    }
    return
  }
  // 暂停态的「继续录音/重新录音」由模板里并排按钮独立处理，本函数只在非暂停态被调用
  // 已上传过录音 → 本次是“新增录音”，旧录音已存服务器、不会丢，直接开录、无需覆盖确认
  if (hasSavedRecordings.value) {
    await startRecord()
    return
  }
  // 仅本地有未上传录音（极少见）→ 重录会丢失，需确认
  if (rec.hasRecording.value) {
    const res = await showModal({
      title: '重新录音',
      content: '已有一段录音，重新开始会覆盖当前录音。是否继续？',
      confirmText: '重新录音',
      cancelText: '取消'
    })
    if (res.confirm) await startRecord()
    return
  }
  await startRecord()
}

async function startRecord() {
  clearPoll()
  clearQuickState()
  generated.value = false
  extraction.value = null
  aiTopics.value = []
  transcript.value = []
  transcriptPreview.value = ''
  transcriptFullText.value = ''
  transcriptCharCount.value = 0
  transcriptVisible.value = false
  uploading.value = false
  polling.value = false
  extracting.value = false
  taskId.value = ''
  asrStatus.value = ''
  processText.value = '正在录音...'
  try {
    rec.reset()
    await rec.start()
  } catch (e) {
    toast({ title: '需 HTTPS 且允许麦克风', icon: 'none' })
  }
}

function fmt(s) {
  const m = Math.floor(s / 60)
  return String(m).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0')
}

async function finishRecord() {
  if (type.value !== 'committee') {
    toast({ title: '快速录音暂先支持业委会会议', icon: 'none' })
    return
  }
  if (!signedIn.value) {
    toast({ title: '请先签到', icon: 'none' })
    return
  }
  if (!rec.recording.value && !rec.hasRecording.value) {
    // 没有新录音可传：若已上传过则明确告知，避免重复上传同一段
    if (hasSavedRecordings.value) {
      showModal({
        title: '这段录音已上传',
        content: '当前没有新的录音内容。如需接着录，请点"继续录音"，转写时会和已上传的自动合并为一份。',
        showCancel: false,
        confirmText: '知道了'
      })
    } else {
      toast({ title: '请先开始录音', icon: 'none' })
    }
    return
  }
  if (uploading.value || polling.value || extracting.value) return

  // 仍在录音会话中 → 先停拿产出上传（对齐原 recorderStarted 分支：stop 后上传）
  if (rec.recording.value) {
    const r = await rec.stop()
    if (!r) {
      toast({ title: '录音为空，请重试', icon: 'none' })
      return
    }
    if (r.durationSec) asrAudioDurSec.value = r.durationSec
    if (r.blob) asrFileSizeBytes.value = r.blob.size
    await uploadRecording(r.blob, r.ext, r.durationSec)
    return
  }
  // 会话已停止但已有上次产出（对齐原 tempFilePath 分支）→ 直接复用已生成的 blob 上传
  const blob = rec.getBlob && rec.getBlob()
  if (blob && blob.size > 0) {
    await uploadRecording(blob, extFromBlob(blob), asrAudioDurSec.value)
    return
  }
  toast({ title: '录音为空，请重试', icon: 'none' })
}

// 继续录音：恢复同一录音会话（chunks 持续累积），整段始终是一个文件，不另存为新文件
function resumeRecording() {
  if (uploading.value || polling.value || extracting.value) return
  if (rec.recording.value && rec.paused.value) rec.resume()
}

// 重新录音：放弃当前这段，从头开始（需确认）
async function restartRecording() {
  if (uploading.value || polling.value || extracting.value) return
  const res = await showModal({
    title: '重新录音',
    content: '已有一段录音，重新开始会覆盖当前录音。是否继续？',
    confirmText: '重新录音',
    cancelText: '取消'
  })
  if (res.confirm) await startRecord()
}

// 从已生成 blob 的 mimeType 推扩展名（与 useRecorder.extFromMime 对齐）
function extFromBlob(blob) {
  const t = (blob && blob.type) || ''
  if (t.includes('mp4')) return 'm4a'
  if (t.includes('ogg')) return 'ogg'
  if (t.includes('mpeg')) return 'mp3'
  return 'webm'
}

// 不现场录音，直接选一个已有的音频文件上传
function chooseAudioFile() {
  if (type.value !== 'committee') {
    toast({ title: '快速录音暂先支持业委会会议', icon: 'none' })
    return
  }
  if (!signedIn.value) {
    toast({ title: '请先签到', icon: 'none' })
    return
  }
  if (uploading.value || polling.value || extracting.value) return
  if (audioFileInput.value) {
    audioFileInput.value.value = '' // 允许重复选同一文件
    audioFileInput.value.click()
  }
}

async function onAudioFileChange(e) {
  const file = e.target.files && e.target.files[0]
  if (!file) {
    toast({ title: '未选择文件', icon: 'none' })
    return
  }
  asrFileSizeBytes.value = file.size
  const dur = await getAudioDuration(file) // 读取已选音频的时长（秒），用于转写页展示
  if (dur > 0) asrAudioDurSec.value = dur
  await uploadRecordingFile(file, dur)
}

// 读取音频文件时长（秒）：临时 audio 元素读 metadata，失败返回 0
function getAudioDuration(file) {
  return new Promise((resolve) => {
    try {
      const url = URL.createObjectURL(file)
      const a = document.createElement('audio')
      a.preload = 'metadata'
      a.onloadedmetadata = () => { const d = a.duration; URL.revokeObjectURL(url); resolve(isFinite(d) ? Math.round(d) : 0) }
      a.onerror = () => { URL.revokeObjectURL(url); resolve(0) }
      a.src = url
    } catch (err) { resolve(0) }
  })
}

// 录音/选片上传：把 Blob 包成带正确文件名的 File，后端据扩展名识别格式并转 16k 单声道 mp3
async function uploadRecording(blob, ext, durationSec) {
  const file = new File([blob], 'recording.' + ext, { type: blob.type })
  await uploadRecordingFile(file, durationSec)
}

async function uploadRecordingFile(file, durationSec) {
  clearPoll()
  _asrDoneHandled = false
  // 上传期间留在录音页(step 2)，显示"正在上传录音…"，不再自动跳转写页
  uploading.value = true
  polling.value = false
  extracting.value = false
  generated.value = false
  transcript.value = []
  transcriptPreview.value = ''
  transcriptFullText.value = ''
  transcriptCharCount.value = 0
  transcriptVisible.value = false
  processText.value = '正在上传录音…（只保存，不自动转写）'

  persistQuickState()

  try {
    // 上传只存，不自动转写 — 返回录音记录信息（带上时长，供转写页展示）
    await api.committeeUploadRecording(meetingId.value, file, durationSec)
    toast({ title: '录音已上传', icon: 'success' })
    uploading.value = false
    rec.reset() // 清空录音器内存：消除返回录音页时的残留时长，避免把同一段重复上传
    currentStep.value = 2 // 停在录音页：显示"继续上传录音 / 开始转写"两个按钮
    loadDetail() // 刷新录音列表
  } catch (e) {
    uploading.value = false
    currentStep.value = 2 // 退回录音步，可在"结束录音并上传"复用已录音频重试
    processText.value = '上传失败，请重试'
    toast({ title: e.message || '上传失败', icon: 'none' })
  }
}

// ── 转写页多选 ──
// loadDetail 时同步勾选状态：新出现的“未转写”录音默认勾选；保留用户已取消的；移除已转写/已删除的
function reconcilePickedIds(recs) {
  const list = recs || []
  const existing = new Set(list.map(r => r.id))
  const doneIds = new Set(list.filter(r => r.asrStatus === 'done').map(r => r.id))
  const next = (pickedIds.value || []).filter(id => existing.has(id) && !doneIds.has(id))
  list.forEach(r => {
    if (!_knownRecordingIds.has(r.id) && r.asrStatus !== 'done' && !next.includes(r.id)) next.push(r.id)
  })
  list.forEach(r => _knownRecordingIds.add(r.id))
  pickedIds.value = next
}
function isPicked(id) { return (pickedIds.value || []).includes(id) }
function togglePick(id) {
  pickedIds.value = isPicked(id)
    ? pickedIds.value.filter(x => x !== id)
    : pickedIds.value.concat([id])
}
// 点击整行切换勾选；已转写的行默认不勾选(自动并入)，但仍可勾选以重新转写
function onPickRowTap(item) {
  if (!item) return
  if (polling.value || extracting.value) return
  togglePick(item.id)
}

// 「上传录音」：只做 转写→提炼（遮罩 recognize），完成后由遮罩「下一步」进入表决核对（voteCheckFlow），
// 不再一键连做生成纪要——表决结果先经主任确认，再决定是否继续生成。
async function uploadAndRecognize() {
  if (!isChair.value) { toast({ title: '仅主任/副主任可操作', icon: 'none' }); return }
  if (uploading.value || polling.value || extracting.value || generatingMinutes.value) return
  if (!(recordings.value || []).length) { toast({ title: '还没有录音，请先录一段', icon: 'none' }); return }
  overlayPhase.value = 'recognize'
  pickedIds.value = (recordings.value || []).filter(r => r.asrStatus !== 'done').map(r => r.id)
  await transcribeSelected()
  if (generated.value) recognizedIds.value = (recordings.value || []).map(r => r.id)
  // 完成后遮罩切完成态（「录音识别完成 → 下一步」），点下一步走 onAiWorkDone → voteCheckFlow
}

// 常驻「生成会议纪要」按钮：只要有已上传录音就能点。
// 未识别 → 先走识别（后续由遮罩「下一步」进表决核对→继续生成，与「上传录音」同一条流程）；
// 已识别 → 直接进「继续生成会议纪要」（含表决未完成的提醒）。
async function generateMinutesFromRecording() {
  if (!isChair.value) { toast({ title: '仅主任/副主任可生成纪要', icon: 'none' }); return }
  if (uploading.value || polling.value || extracting.value || generatingMinutes.value) return
  if (!hasSavedRecordings.value) { toast({ title: '还没有上传录音', icon: 'none' }); return }
  if (needRecognize.value) { await uploadAndRecognize(); return }
  await continueGenerateMinutes(false)
}

// app 内逐人投票情况：topicId → 是否已有人投票（表决"是否已处理"的判断之一）
function _appVotedMap() {
  const m = {}
  const raw = (detail.value && detail.value.record && detail.value.record.topics) || []
  raw.forEach(t => { m[t.id] = (t.voted || 0) > 0 })
  return m
}

// 识别完成后的表决核对：
// 1) AI 识别到票数 → 询问确认后自动填写，再问是否继续生成纪要
// 2) 有表决议题但既无识别结果也无 app 投票 → 提醒先表决
// 3) 没有表决议题（或都已有 app 票）→ 直接询问是否继续生成
async function voteCheckFlow() {
  try { await loadDetail() } catch (e) { /* 刷新失败不阻断核对 */ }
  const voteTopics = (presetTopics.value || []).filter(t => t.voteRequired)
  const appVoted = _appVotedMap()
  const recognized = voteTopics.filter(t => t.aiVote)

  if (recognized.length) {
    const lines = recognized.map(t =>
      '「' + t.title + '」同意 ' + t.voteFor + ' · 反对 ' + t.voteAgainst + ' · 弃权 ' + t.voteAbstain
      + '（' + t.aiVoteLabel + '，建议：' + t.resultLabel + '）')
    const r = await showModal({
      title: 'AI 识别到表决结果',
      content: lines.join('\n') + '\n\n要按识别结果自动填写吗？填写后以此计入表决。',
      confirmText: '确认填写',
      cancelText: '暂不填写',
      size: 'large'
    })
    if (r.confirm) {
      recognized.forEach(t => { t.confirmed = true })
      try {
        await api.committeeQuickConfirm(meetingId.value, buildConfirmPayload())
        toast({ title: '已填写表决结果', icon: 'success' })
        loadDetail()
      } catch (e) {
        toast({ title: (e && e.message) || '保存失败，请重试', icon: 'none' })
        return
      }
      const g = await showModal({
        title: '继续生成会议纪要？',
        content: '表决结果已填写。现在就用 AI 生成会议纪要吗？',
        confirmText: '继续生成',
        cancelText: '稍后再说'
      })
      if (g.confirm) continueGenerateMinutes(true)
      return
    }
    toast({ title: '未填写。可点议题手动表决后，再点「继续生成会议纪要」', icon: 'none' })
    return
  }

  const missing = voteTopics.filter(t => !appVoted[t.id])
  if (missing.length) {
    await showModal({
      title: '还有议题没有表决',
      content: '「' + missing[0].title + '」' + (missing.length > 1 ? '等 ' + missing.length + ' 个表决议题' : '') +
        '还没有表决结果，录音里也没识别到票数。\n请点击上方议题完成表决（委员可在自己手机上表决），之后再点「继续生成会议纪要」。',
      confirmText: '知道了',
      showCancel: false
    })
    return
  }

  const g = await showModal({
    title: '继续生成会议纪要？',
    content: (voteTopics.length ? '表决议题已有表决结果。' : '本次会议没有需要表决的议题。') + '现在就用 AI 生成会议纪要吗？',
    confirmText: '继续生成',
    cancelText: '稍后再说'
  })
  if (g.confirm) continueGenerateMinutes(true)
}

// 「继续生成会议纪要」：确认票数落库 + 大模型生成（遮罩 gen）。skipGuard=true 表示表决核对刚做过，不再重复提醒
async function continueGenerateMinutes(skipGuard) {
  if (!isChair.value) { toast({ title: '仅主任/副主任可生成纪要', icon: 'none' }); return }
  if (uploading.value || polling.value || extracting.value || generatingMinutes.value) return
  if (!generated.value) { toast({ title: '请先点「上传录音」完成识别', icon: 'none' }); return }
  if (!skipGuard) {
    const appVoted = _appVotedMap()
    const missing = (presetTopics.value || []).filter(t => t.voteRequired && !t.confirmed && !t.aiVote && !appVoted[t.id])
    if (missing.length) {
      const r = await showModal({
        title: '还有议题没有表决',
        content: '「' + missing[0].title + '」' + (missing.length > 1 ? '等 ' + missing.length + ' 个表决议题' : '') + '还没有表决结果。建议先完成表决再生成纪要。',
        confirmText: '仍要生成',
        cancelText: '先去表决'
      })
      if (!r.confirm) return
    }
  }
  overlayPhase.value = 'gen'
  generatingMinutes.value = true
  try {
    await api.committeeQuickConfirm(meetingId.value, buildConfirmPayload())
    await api.committeeQuickPolish(meetingId.value) // 大模型生成纪要并落库
    minutesGenerated.value = true
    persistQuickState()
  } catch (e) {
    toast({ title: (e && e.message) || '生成纪要失败，请重试', icon: 'none' })
  } finally {
    generatingMinutes.value = false // → 遮罩完成态「已生成会议纪要」
  }
}

// 遮罩完成按钮：recognize 阶段（「下一步」）→ 表决核对流程；gen 阶段（「查看会议纪要」）→ 进纪要页
// 软路由 router.push 偶发不切换 router-view（URL 变了却仍停在录音页）——加硬导航兜底确保进入纪要页
function onAiWorkDone() {
  if (overlayPhase.value === 'recognize') { voteCheckFlow(); return }
  // view=1：进纪要页先看正文（不直接进编辑模式）
  const q = 'meetingId=' + meetingId.value + '&from=meeting-live-quick&view=1'
  navigateTo('/pages/minutes/minutes?' + q)
  setTimeout(() => {
    if (document.querySelector('.live-page')) {
      window.location.href = '/minutes?' + q
    }
  }, 500)
}

// 关闭「AI 工作中」遮罩：停止前端等待态，回到当前页（后台若已转完，下次进入会自动恢复）
function onAiWorkClose() {
  clearPoll()
  polling.value = false
  extracting.value = false
  _transcribingRecordingId.value = ''
}

// 录音时长（秒）→ "MM:SS"；无时长显示占位
function fmtDur(sec) {
  const s = Number(sec)
  if (!s || s <= 0) return '未知'
  const m = Math.floor(s / 60)
  return m + ':' + String(s % 60).padStart(2, '0')
}
// 上传时间：后端 LocalDateTime（如 2026-06-29T10:01:23）→ "MM-DD HH:mm"
function fmtTime(iso) {
  if (!iso) return ''
  const m = String(iso).match(/(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/)
  return m ? (m[2] + '-' + m[3] + ' ' + m[4] + ':' + m[5]) : String(iso)
}

// 转写页录音回放：点播放试听这段录音，再点暂停；切到另一段会停掉上一段
function togglePlay(item) {
  const url = item && item.recordingUrl
  if (!url) { toast({ title: '该录音暂无音频', icon: 'none' }); return }
  // 再点同一段 = 暂停
  if (playingId.value === item.id && _playAudio) { try { _playAudio.pause() } catch (e) {} return }
  // 切到另一段：先静默停掉旧的（摘掉旧 handler 避免误触发）
  if (_playAudio) { _playAudio.onplay = _playAudio.onpause = _playAudio.onended = _playAudio.onerror = null; try { _playAudio.pause() } catch (e) {} }
  _playAudio = new Audio(url)
  _playAudio.onplay = () => { playingId.value = item.id }
  _playAudio.onpause = () => { if (playingId.value === item.id) playingId.value = null }
  _playAudio.onended = () => { if (playingId.value === item.id) playingId.value = null }
  _playAudio.onerror = () => { playingId.value = null; toast({ title: '播放失败', icon: 'none' }) }
  const p = _playAudio.play()
  if (p && p.catch) p.catch(() => { playingId.value = null; toast({ title: '播放失败', icon: 'none' }) })
}

// 删除一条录音（转写页删废录/多余段）：仅主任/副主任
async function deleteRecording(item, idx) {
  if (!isChair.value) { toast({ title: '仅主任/副主任可删除', icon: 'none' }); return }
  if (polling.value || extracting.value) return
  const res = await showModal({
    title: '删除这段录音',
    content: '确定删除「第 ' + (idx + 1) + ' 段 · 时长 ' + fmtDur(item.durationSec) + '」？删除后不可恢复，转写合并结果也会去掉这段。',
    confirmText: '删除',
    cancelText: '取消'
  })
  if (!res.confirm) return
  // 删的是正在播放的那段，先停掉回放
  if (playingId.value === item.id && _playAudio) { try { _playAudio.pause() } catch (e) {} }
  try {
    await api.committeeDeleteRecording(meetingId.value, item.id)
    toast({ title: '已删除', icon: 'success' })
    pickedIds.value = (pickedIds.value || []).filter(x => x !== item.id)
    _knownRecordingIds.delete(item.id)
    loadDetail() // 刷新录音列表
  } catch (e) {
    toast({ title: (e && e.message) || '删除失败', icon: 'none' })
  }
}

// 转写所选录音：逐条顺序转写，全部完成后统一抽取；已转写的由后端自动并入合并
async function transcribeSelected() {
  if (!isChair.value) { toast({ title: '仅主任/副主任可转写', icon: 'none' }); return }
  if (polling.value || extracting.value) return
  // 勾选的都转写（含被重新勾选的“已转写”项，按 recordingId 覆盖其缓存，不会重复）
  const ids = (recordings.value || [])
    .filter(r => isPicked(r.id))
    .map(r => r.id)
  // 没有勾选：若已有转写过的录音(缓存中)，直接重新拉取合并结果即可
  if (!ids.length) {
    if (!hasDoneRecordings.value) { toast({ title: '请先勾选要转写的录音', icon: 'none' }); return }
    _asrDoneHandled = false
    polling.value = false
    extracting.value = true
    await finalizeTranscription()
    return
  }
  clearPoll()
  _asrDoneHandled = false
  polling.value = true
  extracting.value = false
  generated.value = false
  transcript.value = []
  transcriptPreview.value = ''
  transcriptFullText.value = ''
  transcriptCharCount.value = 0
  transcriptVisible.value = false
  asrStatus.value = ''
  processText.value = '正在提交转写...'
  persistQuickState()
  try {
    for (let i = 0; i < ids.length; i++) {
      _transcribingRecordingId.value = ids[i]
      processText.value = ids.length > 1
        ? ('正在转写第 ' + (i + 1) + '/' + ids.length + ' 条录音…')
        : '正在转写录音…'
      await transcribeOneAwait(ids[i])
    }
    _transcribingRecordingId.value = ''
    await finalizeTranscription()
  } catch (e) {
    _transcribingRecordingId.value = ''
    polling.value = false
    extracting.value = false
    handleAsrFailed((e && e.message) || '转写失败')
  }
}

// 提交单条录音转写并等待其完成（done 时 resolve；失败/超时 reject）
function transcribeOneAwait(recordingId) {
  return new Promise((resolve, reject) => {
    api.committeeTranscribeRecording(meetingId.value, recordingId)
      .then((task) => {
        taskId.value = task.taskId
        asrStatus.value = task.status || 'pending'
        persistQuickState()
        if (task.status === 'done') return resolve(task)
        if (task.status === 'failed') return reject(new Error(asrFailMessage(task.message)))
        pollUntilDone(task.taskId, resolve, reject)
      })
      .catch(reject)
  })
}

// 轮询单条录音的转写状态直至 done（Promise 版，供顺序转写串联）
function pollUntilDone(tid, resolve, reject) {
  clearPoll()
  _pollCount = 0
  _pollTimer = setInterval(async () => {
    _pollCount += 1
    if (_pollCount > MAX_POLL_COUNT) {
      clearPoll()
      reject(new Error('转写超时，请稍后重试或换一条录音'))
      return
    }
    try {
      const task = await api.committeeQuickRecordingStatus(meetingId.value, tid)
      asrStatus.value = task.status || ''
      persistQuickState()
      if (task.status === 'done') { clearPoll(); resolve(task) }
      else if (task.status === 'failed') { clearPoll(); reject(new Error(asrFailMessage(task.message))) }
    } catch (e) {
      clearPoll()
      reject(e)
    }
  }, POLL_INTERVAL)
}

function startPoll(tid) {
  clearPoll()
  _pollCount = 0
  _pollTimer = setInterval(async () => {
    _pollCount += 1
    if (_pollCount > MAX_POLL_COUNT) {
      clearPoll()
      polling.value = false
      asrStatus.value = 'failed'
      processText.value = '转写超时'
      showModal({
        title: '转写超时',
        content: '这条录音长时间没有完成转写，可能是录音里没有有效语音或网络较慢。请换一条录音，或返回上一步重新录音。',
        confirmText: '知道了',
        showCancel: false
      })
      return
    }
    try {
      const task = await api.committeeQuickRecordingStatus(meetingId.value, tid)
      asrStatus.value = task.status || ''
      processText.value = statusText(task.status, task.message)
      persistQuickState()
      if (task.status === 'done') {
        handleAsrDone(task)
      }
      if (task.status === 'failed') handleAsrFailed(task.message)
    } catch (e) {
      clearPoll()
      polling.value = false
      processText.value = '查询转写状态失败'
      toast({ title: e.message || '查询失败', icon: 'none' })
    }
  }, POLL_INTERVAL)
}

function clearPoll() {
  if (_pollTimer) {
    clearInterval(_pollTimer)
    _pollTimer = null
  }
}

function statusText(status, message) {
  if (status === 'done') return '转写完成，正在抽取议题与表决提示...'
  if (status === 'failed') return asrFailMessage(message)
  if (status === 'processing') return '录音转写中...'
  if (status === 'pending') return '转写任务已提交，等待处理...'
  if (status === 'uploading') return '正在上传录音...'
  return '正在处理录音...'
}

function asrFailMessage(message) {
  if (message && String(message).indexOf('45000006') >= 0) {
    return '音频公网地址无法访问，请返回上一步重新上传录音。'
  }
  return message || '转写失败'
}

async function handleAsrDone(task) {
  if (_asrDoneHandled) return
  _asrDoneHandled = true
  asrStatus.value = (task && task.status) || 'done'
  await finalizeTranscription()
}

// 所选录音全部转写完成后：拉取(已按多条录音合并的)转写原文 → 判空 → 抽取议题 → 渲染。
// 单条转写(恢复态 handleAsrDone)与多条顺序转写(transcribeSelected)共用此收尾逻辑。
async function finalizeTranscription() {
  clearPoll()
  uploading.value = false
  polling.value = false
  extracting.value = true
  processText.value = '转写完成，正在抽取议题与表决提示...'
  persistQuickState()

  // 先取转写原文(已含多条录音合并)，判断是否「空转写」（录音里没有可识别的说话声）。
  // 豆包对静音/无效音频也会返回 done，但识别结果为空——此时必须提示用户，而不是继续抽取出空议题。
  let tr = null        // null = 取原文失败(网络等)，不据此判空，避免误报
  try {
    if (typeof api.committeeQuickTranscript === 'function') {
      const raw = await api.committeeQuickTranscript(meetingId.value)
      tr = mapTranscript(raw)
    }
  } catch (e) { tr = null }

  if (tr) {
    const hasText = tr.some(function (seg) { return seg.text && seg.text.trim() })
    if (!hasText) {
      handleAsrEmpty()
      return
    }
  }

  // 有文字（或取原文失败的兜底）→ 抽取议题与表决提示
  try {
    const ext = await api.committeeQuickExtract(meetingId.value)
    const mapped = mapExtraction(ext)
    extraction.value = ext
    presetTopics.value = mapped.presetTopics
    aiTopics.value = mapped.aiTopics
    extracting.value = false
    generated.value = true
    finishText.value = '转写完成，已合并为会议记录并生成待确认的议题'
    persistQuickState()
  } catch (e) {
    extracting.value = false
    processText.value = '规则抽取失败'
    toast({ title: e.message || '抽取失败', icon: 'none' })
    return
  }

  // 渲染转写原文（前面已取到则直接用）
  if (tr) {
    transcript.value = tr
    const tState = buildTranscriptState(tr)
    transcriptFullText.value = tState.transcriptFullText
    transcriptPreview.value = tState.transcriptPreview
    transcriptCharCount.value = tState.transcriptCharCount
    persistQuickState()
  }
}

// 空转写：录音里没有识别到可转写的语音——停止轮询、明确弹窗、允许换录音重试
function handleAsrEmpty() {
  clearPoll()
  _asrDoneHandled = false   // 允许换一条录音/重新录音后再次转写
  uploading.value = false
  polling.value = false
  extracting.value = false
  generated.value = false
  asrStatus.value = 'empty'
  taskId.value = ''
  transcript.value = []
  transcriptPreview.value = ''
  transcriptFullText.value = ''
  transcriptCharCount.value = 0
  processText.value = '未检测到有效语音'
  persistQuickState()
  showModal({
    title: '没有识别到语音',
    content: '这条录音里没有听到可转写的说话声，可能是录到了静音、杂音，或录音太短。请换一条录音，或返回上一步重新录音。',
    confirmText: '知道了',
    showCancel: false
  })
}

// 转写结果（说话人+时间+文本）转成可渲染列表
function mapTranscript(asr) {
  const segs = (asr && asr.segments) || []
  return segs.map((s, i) => ({
    id: i,
    speaker: s.speaker || '',
    time: fmt(Math.floor((s.startMs || 0) / 1000)),
    text: s.text || ''
  }))
}

function buildTranscriptState(tr) {
  const lines = (tr || []).map(function (seg) {
    var prefix = [seg.time, seg.speaker].filter(Boolean).join(' ')
    return (prefix ? prefix + '：' : '') + (seg.text || '')
  }).filter(Boolean)
  const fullText = lines.join('\n')
  // 摘要/字数只统计转写正文，不带时间戳与说话人前缀
  const body = (tr || []).map(function (seg) {
    return (seg.text || '').trim()
  }).filter(Boolean).join(' ').replace(/\s+/g, ' ').trim()
  return {
    transcriptFullText: fullText,
    transcriptPreview: body.length > 92 ? body.slice(0, 92) + '...' : body,
    transcriptCharCount: body.length
  }
}

function openTranscript(mode) {
  transcriptVisible.value = true
  transcriptMode.value = mode || 'short'
}

function closeTranscript() {
  transcriptVisible.value = false
}

function switchTranscriptMode(mode) {
  transcriptMode.value = mode || 'short'
}

function handleAsrFailed(message) {
  clearPoll()
  const text = asrFailMessage(message)
  uploading.value = false
  polling.value = false
  extracting.value = false
  generated.value = false
  asrStatus.value = 'failed'
  processText.value = text
  persistQuickState()
  toast({ title: text, icon: 'none' })
}

function retryToStep2() {
  clearPoll()
  currentStep.value = 2
  uploading.value = false
  polling.value = false
  extracting.value = false
  generated.value = false
  taskId.value = ''
  asrStatus.value = ''
  processText.value = '请重新上传录音'
  persistQuickState()
}

function mapExtraction(ext) {
  const hitList = (ext && ext.presetTopicHits) || []
  const candidateList = (ext && ext.candidateTopics) || []
  const fallbackPreset = presetTopics.value || []
  const total = voteTotal.value || 0
  const presets = hitList.length
    ? hitList.map(hit => mapTopic(hit, null, total))
    : fallbackPreset.map(t => Object.assign({}, t, { confirmed: false }))
  const ai = candidateList.map(hit => mapTopic(hit, null, total))
  return { presetTopics: presets, aiTopics: ai }
}

// 采纳疑似新议题：主任选类型 → 调用现场添加议题接口建真实议题 → 并入正式议题列表
async function adoptCandidate(idx) {
  if (!isChair.value) { toast({ title: '仅主任/副主任可立项', icon: 'none' }); return }
  const cand = (aiTopics.value || [])[idx]
  if (!cand) return
  const choices = [
    { label: '通报事项', type: 'notice', decisionType: 'none', voteRequired: false },
    { label: '讨论事项', type: 'discussion', decisionType: 'none', voteRequired: false },
    { label: '表决事项', type: 'decision', decisionType: 'simple', voteRequired: true }
  ]
  const sheet = await showActionSheet({ itemList: choices.map(function (c) { return c.label }) })
  const c = choices[sheet.tapIndex]
  if (!c) return
  try {
    const created = await api.committeeAddTopic(meetingId.value, cand.title, c.type, c.decisionType, null, false)
    const realId = created && created.id ? created.id : cand.id
    const adopted = decorateEvidence(Object.assign({}, cand, {
      id: realId,
      type: c.type,
      typeLabel: topicTypeLabel(c.type),
      voteRequired: c.voteRequired,
      suggest: c.voteRequired ? voteHintText(null) : '记录要点',
      result: 'unclear',
      resultLabel: resultLabel('unclear'),
      voteFor: 0, voteAgainst: 0, voteAbstain: 0, voteCounted: 0,
      confirmed: false
    }))
    const presets = (presetTopics.value || []).concat([adopted])
    const ai = (aiTopics.value || []).slice()
    ai.splice(idx, 1)
    presetTopics.value = presets
    aiTopics.value = ai
    persistQuickState()
    toast({ title: '已立项为' + c.label, icon: 'success' })
  } catch (err) {
    toast({ title: err.message || '立项失败', icon: 'none' })
  }
}

function ignoreCandidate(idx) {
  const ai = (aiTopics.value || []).slice()
  if (idx < 0 || idx >= ai.length) return
  ai.splice(idx, 1)
  aiTopics.value = ai
  persistQuickState()
}

function pickTopicResult(group, idx, result) {
  const key = group === 'ai' ? 'aiTopics' : 'presetTopics'
  const target = group === 'ai' ? aiTopics : presetTopics
  const list = target.value.slice()
  const res = result || 'unclear'
  list[idx] = Object.assign({}, list[idx], {
    result: res,
    resultLabel: resultLabel(res),
    needsReview: res === 'unclear',
    confirmed: false
  })
  target.value = list
  if (minutesGenerated.value) minutesGenerated.value = false // 结果已改，草稿作废
  persistQuickState()
}

function recalcVoteTopic(topic) {
  const voteFor = Number(topic.voteFor) || 0
  const voteAgainst = Number(topic.voteAgainst) || 0
  const voteAbstain = Number(topic.voteAbstain) || 0
  const counted = voteFor + voteAgainst + voteAbstain
  const need = voteNeed.value || 1
  const total = voteTotal.value || 0
  let result = 'unclear'
  if (voteFor >= need) result = 'passed'
  else if (total > 0 && counted >= total) result = 'rejected'
  return Object.assign({}, topic, {
    voteFor,
    voteAgainst,
    voteAbstain,
    voteCounted: counted,
    result,
    resultLabel: resultLabel(result),
    needsReview: result === 'unclear',
    confirmed: false
  })
}

function changeVoteCount(group, idx, field, delta) {
  const target = group === 'ai' ? aiTopics : presetTopics
  const d = Number(delta) || 0
  const list = target.value.slice()
  const topic = Object.assign({}, list[idx])
  const current = Number(topic[field]) || 0
  const next = Math.max(0, current + d)
  const total = voteTotal.value || 0
  const otherTotal = (Number(topic.voteFor) || 0) + (Number(topic.voteAgainst) || 0) + (Number(topic.voteAbstain) || 0) - current
  if (total > 0 && otherTotal + next > total) {
    toast({ title: '票数不能超过实到人数', icon: 'none' })
    return
  }
  topic[field] = next
  list[idx] = recalcVoteTopic(topic)
  target.value = list
  if (minutesGenerated.value) minutesGenerated.value = false // 票数已改，草稿作废
  persistQuickState()
}

function buildConfirmPayload() {
  return {
    topics: (presetTopics.value || []).map(function (t) {
      return {
        topicId: t.id,
        result: t.voteRequired ? t.result : 'recorded',
        confirmed: t.confirmed,
        summaryDraft: t.summaryDraft || '',
        segmentIndexes: (t.segmentMatches || []).map(function (seg) { return seg.segmentIndex }),
        forVotes: t.voteRequired ? (Number(t.voteFor) || 0) : null,
        agVotes: t.voteRequired ? (Number(t.voteAgainst) || 0) : null,
        abVotes: t.voteRequired ? (Number(t.voteAbstain) || 0) : null,
        totalVotes: t.voteRequired ? (Number(t.voteCounted) || 0) : null
      }
    }),
    ignoredSegmentIndexes: []
  }
}

// 仅查看已保存的纪要草稿，不触发重新生成
function viewMinutes() {
  navigateTo('/pages/minutes/minutes?meetingId=' + meetingId.value + '&from=meeting-live-quick&view=1')
}

// ── 重做后的「最后一步」：表决核对 / 生成纪要 / 结束会议 ──

// 展开/收起某个议题的票数核对
function toggleReview(idx) {
  const list = (presetTopics.value || []).slice()
  if (!list[idx]) return
  list[idx] = Object.assign({}, list[idx], { _reviewOpen: !list[idx]._reviewOpen })
  presetTopics.value = list
}

// 展开/收起「AI 额外发现」
function toggleExtra() {
  extraOpen.value = !extraOpen.value
}

// 用 AI 生成会议纪要草稿（主任主动点击；等待大模型属预期内，按钮显示「生成中」）
async function generateMinutes() {
  if (!isChair.value) { toast({ title: '仅主任/副主任可生成纪要', icon: 'none' }); return }
  if (!generated.value) { toast({ title: '请先完成录音转写', icon: 'none' }); return }
  generatingMinutes.value = true
  const payload = buildConfirmPayload()
  // 1) 先把(含已核对的)票数/结果快速落库（不依赖大模型）；失败则停下
  try {
    await api.committeeQuickConfirm(meetingId.value, payload)
  } catch (e) {
    generatingMinutes.value = false
    toast({ title: (e && e.message) || '保存失败', icon: 'none' })
    return
  }
  // 2) 跳转纪要页：由纪要页（gen=1）统一调用大模型生成并显示"生成中"，
  //    这里不再 fire 一次，避免重复生成 / 两份结果竞争写库。
  generatingMinutes.value = false
  minutesGenerated.value = true
  persistQuickState()
  navigateTo('/pages/minutes/minutes?meetingId=' + meetingId.value + '&from=meeting-live-quick&gen=1')
}

// 共用：落库当前结果 + 结束会议，然后跳转到指定页面（结束本身不再等大模型）
async function _doEndAndGo(navUrl) {
  ending.value = true
  try {
    // 把最新(可能刚核对过的)结果再存一次，确保归档与展示一致
    try { await api.committeeQuickConfirm(meetingId.value, buildConfirmPayload()) } catch (ce) { /* ignore */ }
    try {
      await api.committeeAdvance(meetingId.value, 'end')
    } catch (ae) {
      const msg = (ae && ae.message) || ''
      const alreadyEnded = msg.indexOf('仅进行中') >= 0 || msg.indexOf('已结束') >= 0 || msg.indexOf('ended') >= 0
      if (!alreadyEnded) {
        ending.value = false
        showModal({ title: '结束会议失败', content: msg || '请稍后重试', showCancel: false })
        return
      }
    }
    clearQuickState()
    redirectTo(navUrl)
  } catch (err) {
    ending.value = false
    showModal({ title: '结束会议失败', content: (err && err.message) || '请稍后重试', showCancel: false })
  }
}

// 确认无误，结束会议（AI 草稿已生成并落库，结束后直接看纪要）
async function confirmEndMeeting() {
  if (!isChair.value) { toast({ title: '仅主任/副主任可结束会议', icon: 'none' }); return }
  const res = await showModal({
    title: '结束会议',
    content: '确认结束本次会议并归档纪要吗？',
    confirmText: '确认结束',
    cancelText: '再想想'
  })
  // 结束后跳到会议详情页（公示页面），主任可在此「发起公示」
  if (res.confirm) _doEndAndGo('/pages/committee-detail/committee-detail?id=' + meetingId.value)
}

// 手写会议纪要（不显眼入口）：不结束会议，直接进纪要页手写/编辑。
function writeMinutes() {
  if (!isChair.value) { toast({ title: '仅主任/副主任可操作', icon: 'none' }); return }
  navigateTo('/pages/minutes/minutes?meetingId=' + meetingId.value + '&from=meeting-live-quick')
}

// 先结束会议，纪要后续补充（不显眼入口）：只结束会议，纪要稍后在纪要页补充/修改
async function endThenSupplement() {
  if (!isChair.value) { toast({ title: '仅主任/副主任可操作', icon: 'none' }); return }
  const res = await showModal({
    title: '先结束会议',
    content: '先结束本次会议，会议纪要可稍后在纪要页补充或修改。继续吗？',
    confirmText: '结束会议',
    cancelText: '再想想'
  })
  if (res.confirm) _doEndAndGo('/pages/minutes/minutes?meetingId=' + meetingId.value + '&from=meeting-live-quick&view=1')
}

// ── 会议资料：任意已签到参会人可上传/查看 ──
function formatSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + 'KB'
  return (bytes / 1024 / 1024).toFixed(1) + 'MB'
}

// 资料上传：H5 用隐藏 input[type=file] 选文件，登记元数据（与小程序契约一致，仅存 name/sizeText）
const materialFileInput = ref(null)
function uploadMaterial() {
  if (!signedIn.value) {
    toast({ title: '请先签到', icon: 'none' })
    return
  }
  if (!materialFileInput.value) {
    materialFileInput.value = document.createElement('input')
    materialFileInput.value.type = 'file'
    materialFileInput.value.multiple = true
    materialFileInput.value.style.display = 'none'
    materialFileInput.value.addEventListener('change', onMaterialFileChange)
    document.body.appendChild(materialFileInput.value)
  }
  materialFileInput.value.value = ''
  materialFileInput.value.click()
}

async function onMaterialFileChange(e) {
  const picked = Array.from(e.target.files || []).slice(0, 3)
  if (!picked.length) return
  const files = picked.map(function (f) {
    return { name: f.name || ('资料_' + Date.now()), sizeText: formatSize(f.size) }
  })
  try {
    await Promise.all(files.map(function (f) {
      return api.committeeAddMaterial(meetingId.value, f.name, f.sizeText)
    }))
    toast({ title: '已上传 ' + files.length + ' 份', icon: 'success' })
    loadDetail()
  } catch (err) {
    toast({ title: err.message || '上传失败', icon: 'none' })
  }
}

function previewMaterial(idx) {
  const list = materials.value || []
  const m = list[idx]
  if (!m) return
  showModal({ title: m.name, content: m.content || '（暂无预览内容）', showCancel: false, confirmText: '关闭' })
}

// ── 实时添加议题（主任/副主任）──
function openAddTopic() {
  if (!isChair.value) {
    toast({ title: '仅主任/副主任可添加议题', icon: 'none' })
    return
  }
  addTopicVisible.value = true
  newTopicForm.title = ''
  newTopicForm.type = 'discussion'
  newTopicForm.decisionType = 'none'
  newTopicForm.options = []
}
function closeAddTopic() { addTopicVisible.value = false; cancelTopicVoice() }

// ——— 议题内容语音输入（Web Speech 流式，与创建页同款；热词纠错走 helpers.applyHotwords） ———
const voiceOn = ref(false)
const voiceFinal = ref('')
const voiceInterim = ref('')
let _voiceRec = null
function _stopTopicVoice() {
  if (_voiceRec) { try { _voiceRec.stop() } catch (e) {} _voiceRec = null }
}
function startTopicVoice() {
  const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SpeechRec) { toast({ title: '当前浏览器不支持语音识别', icon: 'none' }); return }
  _stopTopicVoice()
  voiceFinal.value = ''
  voiceInterim.value = ''
  voiceOn.value = true
  const r = new SpeechRec()
  r.lang = 'zh-CN'; r.continuous = true; r.interimResults = true
  r.onresult = function (e) {
    let fin = '', inter = ''
    for (let i = e.resultIndex; i < e.results.length; i++) {
      const t = e.results[i][0].transcript
      if (e.results[i].isFinal) fin += t
      else inter += t
    }
    if (fin) voiceFinal.value += applyHotwords(fin)
    voiceInterim.value = inter
  }
  r.onerror = function () { _stopTopicVoice() }
  _voiceRec = r
  r.start()
}
function confirmTopicVoice() {
  const text = (voiceFinal.value + voiceInterim.value).trim()
  cancelTopicVoice()
  if (text) newTopicForm.title = text
}
function retryTopicVoice() { startTopicVoice() }
function cancelTopicVoice() {
  _stopTopicVoice()
  voiceOn.value = false
  voiceFinal.value = ''
  voiceInterim.value = ''
}
// 议题类型：通报/讨论/表决（与准备会议时一致）
function pickTopicType(t) {
  newTopicForm.type = t
  newTopicForm.decisionType = t === 'decision' ? 'simple' : 'none'
  newTopicForm.options = []
}
function pickDecisionType(t) {
  newTopicForm.decisionType = t
  newTopicForm.options = t === 'multi_choice' ? [{ id: 1, label: '' }, { id: 2, label: '' }] : []
}
function addTopicOption() {
  const options = newTopicForm.options || []
  const newId = options.length ? Math.max.apply(null, options.map(function (o) { return o.id })) + 1 : 1
  newTopicForm.options = options.concat([{ id: newId, label: '' }])
}
function removeTopicOption(i) {
  newTopicForm.options = newTopicForm.options.filter(function (_, idx) { return idx !== i })
}
async function submitAddTopic() {
  const f = newTopicForm
  if (!f.title.trim()) { toast({ title: '请输入议题内容', icon: 'none' }); return }
  let optionsJson = null
  if (f.type === 'decision' && f.decisionType === 'multi_choice') {
    const valid = (f.options || []).filter(function (o) { return o.label.trim() })
    if (valid.length < 2) { toast({ title: '多选一议题至少需要两个选项', icon: 'none' }); return }
    optionsJson = JSON.stringify(valid.map(function (o, i) { return { id: i + 1, label: o.label.trim() } }))
  }
  const dt = f.type === 'decision' ? f.decisionType : 'none'
  try {
    // 现场新增只允许通报/讨论/表决，重大事项后端会拦截
    await api.committeeAddTopic(meetingId.value, f.title.trim(), f.type, dt, optionsJson, false)
    toast({ title: '议题已添加', icon: 'success' })
    addTopicVisible.value = false
    loadDetail()
  } catch (e) { toast({ title: e.message || '添加失败', icon: 'none' }) }
}

// ── 居委会签字（结束步）──
async function toggleJuwei() {
  try { await api.committeeToggleJuwei(meetingId.value); loadDetail() }
  catch (e) { toast({ title: e.message || '操作失败', icon: 'none' }) }
}

// ── 导出签到名单（H5：写 CSV → Blob 下载；失败回退复制到剪贴板）──
async function exportAttendance() {
  try {
    const res = await api.committeeExportAttendance(meetingId.value)
    const content = res.content || ''
    const fileName = res.fileName || 'attendance.csv'
    try {
      const blob = new Blob(['﻿' + content], { type: 'text/csv;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = fileName
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      setTimeout(function () { URL.revokeObjectURL(url) }, 1000)
    } catch (dl) {
      try {
        await navigator.clipboard.writeText(content)
        toast({ title: '名单已复制', icon: 'none' })
      } catch (cp) {
        toast({ title: '导出失败', icon: 'none' })
      }
    }
  } catch (e) { toast({ title: e.message || '导出失败', icon: 'none' }) }
}

function noop() {}

function exitLive() {
  navigateBack()
}
</script>

<style scoped>
.live-page { min-height:100vh; background:#f4f5f7; padding:24rpx; padding-bottom:48rpx; box-sizing:border-box; }
/* 仅本页：顶栏矮 24rpx(12px)，124→100rpx（PageNav 是共享组件，其他页不动） */
.live-page :deep(.page-nav) { height:calc(100rpx + env(safe-area-inset-top)); }
.live-page :deep(.page-nav .nav-back) { height:100rpx; }
/* 顶栏统一为纯深橙（与其他页一致，覆盖 PageNav 默认黄橙渐变） */
:deep(.page-nav) { background: var(--c-primary-dark); }

/* 步骤指示器 */
/* 首屏容器：至少撑满一屏（100vh 减 顶栏+页面上下留白），参会名单被顶到首屏之下，往下拉才看到 */
.lp-fold { display:flex; flex-direction:column; min-height:calc(100vh - 96rpx); }

/* 会议信息卡 */
.lp-info-card { background:#fff; border-radius:24rpx; padding:24rpx 28rpx 0; margin-top:24rpx; margin-bottom:44rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); } /* 卡间距 +8px(28→44)；卡片缩小一号(内边距收紧，内容不变) */
.lp-info-head { display:flex; align-items:center; justify-content:space-between; margin-bottom:12rpx; } /* 居中对齐：临时添加与标题齐平 */
.lp-info-title { display:block; font-size:34rpx; font-weight:700; color:#1F2024; line-height:1.35; } /* 标题缩两号(42→34) */
/* 临时添加：蓝字白底小按钮，与标题齐平、往右边缘挪(负右边距) */
.lp-add-topic { font-size:26rpx; color:#1A73E8; font-weight:600; background:#fff; border:2rpx solid #C9DCF8; border-radius:999rpx; padding:6rpx 18rpx; line-height:1.3; margin:0 -12rpx 0 0; }
.lp-add-topic:active { background:#F0F6FF; }
/* 委员引导按钮：柔和橙底，告知"议题可点"，点了直达第一个待办议题（卡片 padding-bottom 为 0，按钮自带下边距） */
.lp-topics-cta { display:block; width:100%; box-sizing:border-box; margin:6rpx 0 26rpx; border:2rpx solid #F0D9B8; border-radius:16rpx; background:#FFF9F0; color:#B06A00; font-size:29rpx; padding:18rpx 0; text-align:center; }
.lp-topics-cta:active { background:#FFF1DC; }
.lp-info-row { display:flex; align-items:flex-start; gap:18rpx; font-size:34rpx; color:#444; margin-bottom:6rpx; }
.lp-info-row.top { align-items:flex-start; }
.lp-info-k { color:#666; flex-shrink:0; width:80rpx; font-size:34rpx; }
.lp-info-v { flex:1; min-width:0; word-break:break-all; }
/* 议题区固定高度(约4行)：卡片大小恒定；放不下先缩字号(下面 fs 档)，仍放不下则本区下拉滚动 */
.lp-agenda { flex:1; min-width:0; height:256rpx; overflow-y:auto; } /* 再缩一号：议题区固定高度→256，腾空间给名单 */
/* 字号自适应档位：每档缩一号(4rpx=2px)，最多缩到 28rpx(fs2)；高档同时压缩行距让更多议题露出 */
.lp-agenda--fs1 .lp-agenda-title { font-size:32rpx; }
.lp-agenda--fs2 .lp-agenda-title { font-size:28rpx; }
.lp-agenda--fs2 .lp-agenda-item { padding:12rpx 0; }
.lp-agenda-item { display:flex; align-items:center; gap:16rpx; padding:18rpx 0; border-bottom:2rpx solid #F2F2F4; }
.lp-agenda-item:last-child { border-bottom:0; }
.lp-agenda-item:active { background:#FAFAFA; }
/* 议题行角标：显示待/已状态——待处理=橙(提醒)、已处理=绿；行尾小箭头提示可点 */
.lp-agenda-badge { flex-shrink:0; font-size:24rpx; color:#666; background:#F2F2F4; border-radius:999rpx; padding:6rpx 16rpx; line-height:1.3; font-weight:600; }
.lp-agenda-badge.wait { background:#FFF3E0; color:#C77700; }
.lp-agenda-badge.done { background:#EAF6E5; color:#2E7D32; }
.lp-agenda-arrow { flex-shrink:0; color:#C2C6CC; font-size:34rpx; margin-left:-6rpx; }
.lp-agenda-idx { width:44rpx; height:44rpx; flex-shrink:0; border-radius:50%; background:#F2F2F4; color:#666; font-size: 30rpx; text-align:center; line-height:44rpx; }
.lp-agenda-title { flex:1; min-width:0; color:#1F2024; word-break:break-all; font-size:36rpx; line-height:1.35; }
.lp-agenda-tag { flex-shrink:0; font-size: 30rpx; padding:4rpx 14rpx; border-radius:12rpx; background:#F2F2F4; color:#666; }
.lp-agenda-tag.vote { background:#FFF3E0; color:#E67E22; }
.lp-agenda-tag.major { background:#FDECEA; color:#E74C3C; }
.lp-agenda-empty { color:#666; font-size:28rpx; }

/* 主任：签到统计卡（点"查看名单"标签展开） */
.lp-signin { position:relative; background:#fff; border-radius:24rpx; padding:32rpx; margin-bottom:28rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); }
.lp-signin.open { box-shadow:0 10rpx 34rpx rgba(255,168,0,0.22); }
/* 弱化版（底部不显眼处）：融入页面背景、无卡片感、小字淡色细进度条 */
.lp-signin.faint { background:transparent; box-shadow:none; padding:6rpx 6rpx 2rpx; margin:8rpx 0 54rpx; }
.lp-signin.faint.open { box-shadow:none; }
.lp-signin.faint .ls-head { margin-bottom:10rpx; }
.lp-signin.faint .ls-label { font-size:28rpx; color:#9AA0A6; font-weight:600; }
.lp-signin.faint .ls-count { font-size:26rpx; color:#9AA0A6; }
.lp-signin.faint .ls-count b { font-size:30rpx; }
.lp-signin.faint .ls-tag { font-size:26rpx; padding:6rpx 16rpx; }
.lp-signin.faint .ls-bar { height:10rpx; }
.ls-head { display:flex; align-items:center; gap:16rpx; margin-bottom:22rpx; }
.ls-label { font-size:36rpx; font-weight:700; color:#1F2024; }
.ls-count { font-size:34rpx; color:#666; }
.ls-count b { font-size:42rpx; font-weight:800; }
.ls-count.low b { color:#E74C3C; }
.ls-count.mid b { color:#E6920A; }
.ls-count.full b { color:#27AE60; }
/* 查看名单小标签（手机端点击展开/收起） */
.ls-tag { margin-left:auto; flex-shrink:0; border:2rpx solid #FFD79A; background:#FFF6E6; color:#D88900; font-size:30rpx; font-weight:600; padding:8rpx 20rpx; border-radius:999rpx; line-height:1.3; }
.ls-tag.on { background:#FFA800; border-color:#FFA800; color:#fff; }
.ls-bar { height:18rpx; border-radius:9rpx; background:#F0F0F2; overflow:hidden; }
.ls-fill { height:100%; border-radius:9rpx; transition:width .35s ease, background .35s ease; }
/* 进度条颜色随签到比例变化：少→红，过半→黄，全签到→绿 */
.ls-fill.low { background:linear-gradient(90deg,#FF8A8A,#E74C3C); }
.ls-fill.mid { background:linear-gradient(90deg,#FFD24D,#F5A623); }
.ls-fill.full { background:linear-gradient(90deg,#5BD08A,#27AE60); }

/* 明细名单：仅由"查看名单"标签点击控制 (v-show) */
.ls-pop { position:absolute; left:0; right:0; top:calc(100% + 10rpx); z-index:30; background:#fff; border-radius:20rpx; box-shadow:0 16rpx 48rpx rgba(0,0,0,0.18); padding:20rpx 24rpx; }
.ls-pop::before { content:''; position:absolute; left:60rpx; top:-12rpx; width:24rpx; height:24rpx; background:#fff; transform:rotate(45deg); box-shadow:-3rpx -3rpx 8rpx rgba(0,0,0,0.04); }
/* 底部弱化版：明细向上弹出，避免遮挡底部按钮 */
.ls-pop.up { top:auto; bottom:calc(100% + 10rpx); }
.ls-pop.up::before { top:auto; bottom:-12rpx; box-shadow:3rpx 3rpx 8rpx rgba(0,0,0,0.04); }
.ls-pop-head { display:flex; justify-content:space-between; align-items:center; font-size:30rpx; font-weight:700; color:#1F2024; padding-bottom:14rpx; margin-bottom:10rpx; border-bottom:2rpx solid #F2F2F4; }
.ls-pop-sub { font-size:28rpx; color:#D88900; font-weight:800; }
.ls-pop-list { max-height:48vh; overflow-y:auto; }
.ls-pop-item { display:flex; align-items:center; gap:14rpx; padding:14rpx 0; border-bottom:2rpx solid #F6F6F8; }
.ls-pop-item:last-child { border-bottom:0; }
.ls-dot { width:18rpx; height:18rpx; border-radius:50%; flex-shrink:0; background:#D5D5DA; }
.ls-dot.on { background:#27AE60; }
.ls-dot.off { background:#E74C3C; }
.ls-dot.wait { background:#D5D5DA; }
.ls-name { font-size:32rpx; color:#1F2024; flex-shrink:0; }
.ls-me { font-size:26rpx; color:#999; }
.ls-role { font-size:28rpx; color:#999; flex:1; min-width:0; }
.ls-state { font-size:28rpx; font-weight:600; flex-shrink:0; padding:4rpx 16rpx; border-radius:12rpx; }
.ls-state.on { color:#27AE60; background:#E8F7EE; }
.ls-state.off { color:#E74C3C; background:#FDECEA; }
.ls-state.wait { color:#999; background:#F2F2F4; }

/* 参会人员名单卡：列表滚动区 */
/* 人员名单卡（仅列表）收窄留白；沉底的统计卡（标题+进度条） */
.lp-roster { padding:16rpx 32rpx; }
/* 签到统计卡整体缩小约 30%（卡片内边距 + 标题 + 数字 + 进度条一并缩） */
.lp-roster-stats { padding:27rpx 22rpx; }
.lp-roster-stats .ls-head { margin-bottom:11rpx; }
.lp-roster-stats .lp-card-title { font-size:28rpx; }
.lp-roster-stats .ls-count { font-size:24rpx; }
.lp-roster-stats .ls-count b { font-size:30rpx; }
.lp-roster-stats .ls-bar { height:13rpx; border-radius:7rpx; }
/* 名单完整展示（不做内部滚动）；首屏自然只露出前几行，往下滚页面看其余 */
/* 默认只露约 3-4 人，其余在本区下拉查看（不撑高卡片） */
.lr-list { margin-top:8rpx; max-height:300rpx; overflow-y:auto; }
/* 行内「正在录音」标签（并入右侧状态栏，未录音时隐藏；录音蓝 / 暂停黑，避免与缺席红混淆） */
.lr-rec-tag { display:inline-flex; align-items:center; gap:8rpx; flex-shrink:0; font-size:26rpx; font-weight:700; color:#2563EB; background:#E8F0FE; padding:4rpx 14rpx; border-radius:12rpx; margin-right:20rpx; }
.lr-rec-tag.paused { color:#1F2024; background:#EDEEF0; }
.lr-rec-dot { width:14rpx; height:14rpx; border-radius:50%; background:#2563EB; animation:qkpulse 1.2s ease-in-out infinite; }
.lr-rec-tag.paused .lr-rec-dot { animation:none; opacity:.85; background:#1F2024; }

/* 步骤卡片 */
.lp-card { background:#fff; border-radius:24rpx; padding:38rpx 32rpx; margin-bottom:44rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); } /* 卡间距 +8px(28→44) */
.lp-card-step { display:block; font-size:28rpx; color:#D88900; font-weight:700; margin-bottom:12rpx; }
.lp-card-title { display:block; font-size:40rpx; font-weight:700; color:#1f2329; line-height:1.4; }

.lp-primary-btn { display:block; width:100%; box-sizing:border-box; background:var(--c-primary-dark); color:#fff; border:0; border-radius:44rpx; font-size:32rpx; font-weight:600; padding:26rpx 0; margin-top:12rpx; }
.lp-primary-btn:active { background:var(--c-primary-strong); }
/* 签到按钮：窄一点居中（与其他页主按钮一致），别全宽显大 */
.lp-primary-btn.narrow { width:460rpx; margin-left:auto; margin-right:auto; font-size:34rpx; }
/* 录音卡两个按钮：宽度贴合文字、只比字宽一圈、居中 */
.lp-primary-btn[disabled] { background:#d8c3ab; color:#fff; }
.lp-ghost-btn { display:block; width:100%; box-sizing:border-box; background:#fff; color:#FFA800; border:2rpx solid #FFA800; border-radius:44rpx; font-size:30rpx; padding:22rpx 0; margin-top:16rpx; margin-bottom:8rpx; }
/* 没有新录音可传时，"结束录音并上传"弱化为不可用样子（仍可点，点了弹提示说明已上传） */
.lp-ghost-btn.muted { color:#BBB; border-color:#E2E2E2; background:#FAFAFA; }
/* 结束录音并上传：浅橙填充，明显一点（覆盖 muted 灰化，始终醒目） */
.lp-ghost-btn.finish-upload, .lp-ghost-btn.finish-upload.muted { background:#FFF1E0; color:var(--c-primary-dark); border:2rpx solid var(--c-primary-dark); font-weight:700; width:fit-content; margin-left:auto; margin-right:auto; margin-top:8rpx; padding:10rpx 34rpx; font-size:26rpx; } /* 上距收紧让录音卡更紧凑 */
/* 上传后空闲提示：已录段数会合并为一份 */
.qk-seg-hint { font-size:26rpx; color:#9A6A00; line-height:1.5; margin:6rpx 0 2rpx; background:#FFF8EC; border-radius:12rpx; padding:14rpx 18rpx; text-align:center; }

/* 第4步底部：次要操作弱化为小链接 */
.qk-sub-actions { display:flex; align-items:center; justify-content:center; gap:18rpx; margin-top:18rpx; }
.qk-sub-link { font-size:28rpx; color:#666; padding:8rpx 4rpx; }
.qk-sub-sep { color:#666; }
/* 结束步：不显眼的次要入口（手写纪要 / 先结束后补） */
.qk-minor-links { display:flex; flex-direction:column; align-items:center; gap:8rpx; margin-top:20rpx; }
.qk-minor-link { font-size:28rpx; color:#777; padding:12rpx 16rpx; }

/* 录音控件 */
/* 录音卡（精简版）：圆圈即录音按钮——橙芯白环=待录，红芯呼吸=录音中；无说明/状态小字。整体缩两号+紧凑 */
.lp-rec { padding:14rpx 26rpx 4rpx; } /* 卡片再缩一号：内边距进一步收紧(圆圈/字号不变) */
.lp-rec .lp-card-title { font-size:34rpx; } /* 标题缩一号(40→34)，比之前回大一点 */
.qk-recorder { display:flex; flex-direction:column; align-items:center; gap:6rpx; padding:6rpx 0 2rpx; }
.qk-rec-circle { width:204rpx; height:204rpx; border-radius:50%; background:var(--c-primary); color:#fff; font-size:36rpx; font-weight:700; display:flex; align-items:center; justify-content:center; border:8rpx solid #FFF3E0; box-shadow:0 8rpx 22rpx rgba(199,106,0,0.28); box-sizing:border-box; } /* 再缩一号：圆圈228→204、字40→36，腾空间给名单 */
/* 圈内文案固定两字一行（"开始/录音"两行） */
.qrc-txt { display:block; width:2em; line-height:1.35; text-align:center; word-break:break-all; }
.qk-rec-circle:active { transform:scale(0.95); }
.qk-rec-circle:disabled { background:#E5E8EC; color:#999; border-color:#F2F2F4; box-shadow:none; }
.qk-rec-circle.on { background:#E74C3C; border-color:#FDECEA; box-shadow:0 8rpx 22rpx rgba(231,76,60,0.30); animation:qkpulse 1.2s ease-in-out infinite; }
@keyframes qkpulse { 0%,100% { opacity:1; transform:scale(1); } 50% { opacity:.55; transform:scale(.92); } }
.qk-rec-time { font-size:40rpx; font-weight:700; color:#1f2329; letter-spacing:4rpx; margin-top:6rpx; } /* 计时字号不变(40)，上距再收紧 */

.qk-note { font-size:28rpx; color:#666; line-height:1.6; margin-top:20rpx; background:#FAFBFC; border-radius:14rpx; padding:18rpx 20rpx; }
.qk-note.warn { color:#C77700; background:#FFF8EC; }

/* 入会签到：状态待签到（左）+ 身份（右）一行两端对齐 */
/* 签到卡（精简版）：无标题行，收窄上下留白 */
.lp-sign { padding:30rpx 32rpx 26rpx; }
.qk-sign-tip { display:block; text-align:center; font-size:32rpx; color:#666; margin-top:20rpx; }

/* 转写原文 */
.qk-tr-list { background:#FAFBFC; border-radius:14rpx; padding:12rpx 20rpx; }
.qk-tr-seg { padding:16rpx 0; border-bottom:2rpx solid #EEF1F4; }
.qk-tr-seg:last-child { border-bottom:none; }
.qk-tr-meta { display:flex; align-items:center; gap:14rpx; margin-bottom:8rpx; }
.qk-tr-spk { font-size: 28rpx; color:#fff; background:#7C8AA0; border-radius:8rpx; padding:2rpx 14rpx; }
.qk-tr-time { font-size: 28rpx; color:#666; }
.qk-tr-text { font-size:30rpx; color:#333; line-height:1.7; }

.qk-transcript-card { border:2rpx solid #EEF1F4; background:#FAFBFC; border-radius:18rpx; padding:22rpx; margin:24rpx 0 8rpx; }
.qk-transcript-card.compact { margin:0 0 24rpx; }
.qk-transcript-head { display:flex; align-items:flex-start; justify-content:space-between; gap:18rpx; }
.qk-transcript-title { display:block; font-size:30rpx; font-weight:700; color:#1F2024; line-height:1.4; }
.qk-transcript-meta { display:block; font-size: 28rpx; color:#666; margin-top:4rpx; line-height:1.35; }
.qk-transcript-action { flex-shrink:0; color:#C77800; background:#FFF6E5; border:2rpx solid #FFE2A8; border-radius:16rpx; font-size:28rpx; padding:8rpx 20rpx; line-height:1.35; }
.qk-transcript-preview { display:block; margin-top:14rpx; color:#5F6673; font-size:28rpx; line-height:1.6; word-break:break-all; }
.qk-transcript-sheet { width:88%; max-height:82vh; background:#fff; border-radius:24rpx; padding:28rpx; }
.qk-sheet-head { display:flex; align-items:center; justify-content:space-between; gap:18rpx; margin-bottom:18rpx; }
.qk-sheet-close { font-size:44rpx; color:#666; padding:0 8rpx; }
.qk-transcript-tabs { display:flex; gap:14rpx; margin-bottom:18rpx; }
.qk-transcript-tab { flex:1; text-align:center; padding:16rpx 0; border-radius:16rpx; font-size:28rpx; color:#777; background:#F3F5F7; }
.qk-transcript-tab.on { color:#C77800; background:#FFF6E5; font-weight:700; }
.qk-transcript-scroll { max-height:58vh; }
.qk-transcript-body { display:block; color:#333; font-size:30rpx; line-height:1.7; word-break:break-all; white-space:pre-wrap; }

/* “或”分割线 */
/* 暂停态：继续录音 + 重新录音 并排橙色窄按钮 */
.qk-rec-actions { display:flex; gap:16rpx; margin-top:12rpx; }
.qk-rec-actions .lp-primary-btn { flex:1; width:auto; margin-top:0; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:2rpx; padding:16rpx 6rpx; line-height:1.25; }
/* 重新录音：窄款（约缩40%）居中 */
.qk-rec-actions .lp-primary-btn.narrow40 { flex:0 0 60%; max-width:60%; margin:0 auto; }
/* 生成会议纪要：与"结束录音并上传"同尺寸的小胶囊，并上移 16rpx(8px) */
.qk-rec-actions .lp-primary-btn.gen-minutes { flex:0 0 auto; width:fit-content; margin:-16rpx auto 0; padding:12rpx 36rpx; }
.gen-minutes .qra-main { font-size:26rpx; }
/* 常驻「生成会议纪要」：橙色小胶囊，居中，与录音卡内其它按钮呼应 */
.gen-standalone { width:fit-content; margin:14rpx auto 0; padding:16rpx 52rpx; display:flex; align-items:center; justify-content:center; }
.gen-standalone .qra-main { font-size:30rpx; font-weight:700; }
.qra-main { font-size:34rpx; font-weight:700; }
.qra-sub { font-size:24rpx; font-weight:400; opacity:0.92; }
/* 选择已有录音文件上传：窄一点、居中 */
.qk-rec-narrow { width:auto; max-width:fit-content; margin-left:auto; margin-right:auto; padding-left:44rpx; padding-right:44rpx; }
.qk-divider { display:flex; align-items:center; gap:18rpx; margin:24rpx 0 16rpx; }
.qk-divider::before, .qk-divider::after { content:''; flex:1; height:2rpx; background:#E5E8EC; }
.qk-divider-text { font-size:28rpx; color:#666; }
.lp-empty { color:#666; font-size:28rpx; text-align:center; padding:28rpx 0; }

/* 生成中 */
.qk-gen { display:flex; flex-direction:column; align-items:center; gap:20rpx; padding:36rpx 0; }
.qk-gen-icon { width:100rpx; height:100rpx; border-radius:50%; background:#27AE60; color:#fff; display:flex; align-items:center; justify-content:center; font-size:52rpx; }
.qk-gen-text { font-size:30rpx; font-weight:600; color:#333; }
.qk-spinner { width:80rpx; height:80rpx; border-radius:50%; border:8rpx solid #FFE2A8; border-top-color:#FFA800; animation:qkspin .8s linear infinite; }
@keyframes qkspin { to { transform:rotate(360deg); } }

/* 议题确认 */
.qk-grp-title { font-size:28rpx; font-weight:700; color:#555; margin:24rpx 0 14rpx; }
.qk-topic { display:flex; align-items:center; gap:18rpx; border:2rpx solid #eef0f3; border-radius:18rpx; padding:22rpx; margin-bottom:14rpx; }
.qk-topic-main { flex:1; }
.qk-topic-title-row { display:flex; align-items:flex-start; justify-content:space-between; gap:14rpx; }
.qk-topic-title { font-size:30rpx; color:#333; font-weight:600; line-height:1.45; flex:1; }
.qk-rename-link { flex-shrink:0; font-size:28rpx; color:#C77800; font-weight:600; padding:4rpx 8rpx; }
.qk-tag-new { font-size: 28rpx; color:#fff; background:#FFA800; border-radius:8rpx; padding:2rpx 12rpx; margin-right:10rpx; }
.qk-tag-type { font-size: 28rpx; color:#4D6B8A; background:#EDF5FB; border-radius:8rpx; padding:2rpx 12rpx; margin-right:10rpx; vertical-align:middle; }
.qk-match-line { display:block; font-size: 28rpx; color:#6B7A90; margin-top:10rpx; line-height:1.45; }
.qk-match-score { display:block; font-size: 28rpx; color:#9A6A1B; margin-top:8rpx; line-height:1.45; }
.qk-sug { display:block; font-size:28rpx; color:#666; margin-top:10rpx; }
.qk-report-actions { display:flex; flex-wrap:wrap; gap:14rpx; justify-content:flex-start; margin-top:16rpx; }
.qk-report-btn { display:inline-block; color:#C77800; background:#FFF3DC; border-radius:12rpx; padding:14rpx 22rpx; font-size:28rpx; font-weight:600; }
.qk-report-btn.disabled { opacity:0.55; }
.qk-report-btn.primary { background:#FFA800; color:#fff; }
.qk-report-edit { margin-top:16rpx; border:2rpx solid #FFE2A8; border-radius:14rpx; background:#fff; padding:18rpx 20rpx; }
.qk-report-textarea { width:100%; min-height:220rpx; box-sizing:border-box; font-size:28rpx; color:#374151; line-height:1.65; }
.qk-ph { color:#9aa4b2; }
.qk-report-edit-actions { display:flex; gap:14rpx; justify-content:flex-end; margin-top:14rpx; }
.qk-report { margin-top:16rpx; border:2rpx solid #E6EAF0; border-radius:14rpx; background:#FAFBFC; padding:18rpx 20rpx; }
.qk-report-title { display:block; font-size:28rpx; color:#1F2937; font-weight:700; margin-bottom:12rpx; }
.qk-report-body { display:block; font-size:28rpx; color:#374151; line-height:1.65; white-space:pre-line; word-break:break-word; }
.qk-fields { display:flex; flex-direction:column; gap:8rpx; margin-top:14rpx; background:#F4F9F4; border-radius:14rpx; padding:16rpx 18rpx; }
.qk-field { display:flex; align-items:flex-start; gap:14rpx; font-size:28rpx; line-height:1.5; }
.qk-field-name { flex:0 0 auto; min-width:110rpx; color:#3F7A4B; font-weight:600; }
.qk-field-val { flex:1; color:#1F2937; word-break:break-all; }
.qk-fields-hint { font-size: 28rpx; color:#8A9A8C; margin-top:6rpx; }
.qk-record-hint { display:block; font-size:28rpx; color:#6B7280; line-height:1.45; }
.qk-record-row { display:flex; align-items:center; justify-content:space-between; gap:18rpx; margin-top:14rpx; }
.qk-mini-action.disabled { opacity:0.5; }
.qk-vote-ai { display:block; font-size: 28rpx; color:#B8860B; margin-top:8rpx; line-height:1.45; }

/* 议题列表行 */
.qk-row { display:flex; align-items:center; gap:18rpx; background:#fff; border:2rpx solid #EEF0F2; border-radius:18rpx; padding:24rpx; margin-top:16rpx; }
.qk-row.done { border-color:#C7E3CD; background:#F6FBF7; }
.qk-row.candidate { border-style:dashed; border-color:#C9B07A; background:#FFFDF6; }
.qk-row-no { flex:0 0 auto; width:44rpx; height:44rpx; line-height:44rpx; text-align:center; font-size:28rpx; color:#6B7280; background:#F0F1F3; border-radius:50%; }
.qk-row-no.new { color:#fff; background:#FFA800; }
.qk-row-main { flex:1; min-width:0; }
.qk-row-title { display:block; font-size:32rpx; color:#1F2937; line-height:1.4; }
.qk-row-meta { display:flex; flex-wrap:wrap; align-items:center; gap:12rpx; margin-top:8rpx; }
.qk-row-sub { font-size: 28rpx; color:#666; }
.qk-row-status { flex:0 0 auto; font-size:28rpx; color:#B0863B; }
.qk-row-status.on { color:#3F7A4B; }
.qk-row-arrow { flex:0 0 auto; font-size:40rpx; color:#C2C7CC; }

/* 议题详情全屏面板 */
.qk-detail-sheet { position:absolute; left:0; right:0; bottom:0; top:10%; height:90vh; max-height:90vh; background:#F5F6F8; border-radius:24rpx 24rpx 0 0; display:flex; flex-direction:column; overflow:hidden; box-sizing:border-box; }
.qk-detail-scroll { flex:1; height:calc(90vh - 96rpx); min-height:0; padding:24rpx 28rpx 180rpx; box-sizing:border-box; }
.qk-detail-body { background:#fff; border-radius:18rpx; padding:24rpx; }
.qk-candidate { border:2rpx dashed #C9B07A; background:#FFFDF6; border-radius:18rpx; padding:22rpx; margin-top:18rpx; }
.qk-candidate-actions { display:flex; gap:18rpx; margin-top:18rpx; }
.qk-cand-btn { flex:1; text-align:center; font-size:28rpx; padding:18rpx 0; border-radius:14rpx; background:#F0F1F3; color:#4B5563; }
.qk-cand-btn.primary { background:#FFA800; color:#fff; }
.qk-evidence { display:flex; flex-direction:column; gap:12rpx; margin-top:16rpx; }
.qk-evidence-head { display:flex; justify-content:space-between; align-items:center; gap:14rpx; color:#6B7A90; font-size: 28rpx; line-height:1.4; }
.qk-evidence-toggle { color:#C77800; font-weight:600; flex-shrink:0; }
.qk-evidence-item { background:#fff; border:2rpx solid #EEF0F3; border-radius:14rpx; padding:16rpx 18rpx; }
.qk-evidence-meta { display:flex; justify-content:space-between; gap:14rpx; color:#8A93A3; font-size: 28rpx; margin-bottom:8rpx; line-height:1.35; }
.qk-evidence-text { display:block; color:#333; font-size:28rpx; line-height:1.55; word-break:break-all; }
.qk-evidence-reason { display:block; color:#6B7A90; font-size: 28rpx; margin-top:8rpx; line-height:1.4; }
.qk-evidence-more { display:block; color:#666; font-size: 28rpx; text-align:center; padding:4rpx 0; }
.qk-mini-action { display:inline-block; color:#6B7A90; font-size: 28rpx; margin-top:10rpx; padding:6rpx 0; }
.qk-mini-action.primary { color:#C77800; font-weight:600; }
.qk-result-row { display:flex; gap:12rpx; flex-wrap:wrap; margin-top:16rpx; }
.qk-result { font-size:28rpx; color:#777; background:#F3F5F7; border:2rpx solid #E4E8EC; border-radius:16rpx; padding:8rpx 18rpx; }
.qk-result.on { color:#C77800; background:#FFF6E5; border-color:#FFD98A; font-weight:700; }
.qk-vote-box { margin-top:16rpx; background:#FFFDF8; border:2rpx solid #F1E2C4; border-radius:14rpx; padding:18rpx 20rpx; }
.qk-vote-rule { display:block; color:#805A18; font-size: 28rpx; line-height:1.45; margin-bottom:14rpx; }
.qk-vote-counts { display:grid; grid-template-columns:repeat(3, 1fr); gap:12rpx; }
.qk-vote-counter { background:#fff; border:2rpx solid #EFE5D2; border-radius:14rpx; padding:14rpx; text-align:center; }
.qk-vote-counter > span { display:block; font-size: 28rpx; color:#777; margin-bottom:10rpx; }
.qk-vote-counter > div { display:flex; align-items:center; justify-content:space-between; gap:8rpx; }
.qk-vote-counter > div span { min-width:52rpx; height:52rpx; line-height:52rpx; border-radius:50%; font-size:30rpx; color:#333; background:#F6F6F8; }
.qk-vote-counter > div span:nth-child(2) { background:transparent; font-weight:700; color:#1F2024; }
.qk-confirm { display:block; text-align:center; margin-top:20rpx; flex-shrink:0; font-size:30rpx; font-weight:700; padding:20rpx 24rpx; border-radius:36rpx; border:2rpx solid #FFA800; color:#FFA800; background:#fff; }
.qk-confirm.on { background:#EAFAF1; border-color:#27AE60; color:#27AE60; }

/* 底部导航 */
.lp-nav { display:flex; gap:24rpx; margin-top:8rpx; justify-content:center; }
.lp-nav-btn { border-radius:44rpx; font-size:30rpx; font-weight:600; padding:22rpx 44rpx; border:0; }
.lp-nav-btn.ghost { background:#fff; color:#555; border:2rpx solid #dfe3e8; }

/* 录音负责人 */
.qk-recorder-role { background:#FAFBFC; border-radius:18rpx; padding:22rpx 26rpx; margin:12rpx 0 18rpx; }
.qk-rr-status { display:block; font-size:28rpx; color:#6B6E76; margin-bottom:14rpx; }
.qk-rr-status.ok { color:#1D9E75; font-weight:600; }

/* 录音卡里的「已录制」文件列表 */
/* 已有录音：独立卡片沉底（.lp-card 已提供底/留白） */
.qk-rec-list { padding-top:24rpx; padding-bottom:20rpx; }
.qk-rec-list-head { font-size:28rpx; color:#888; margin-bottom:12rpx; }
.qk-rec-list-item { display:flex; align-items:center; gap:16rpx; padding:16rpx 0; border-bottom:2rpx solid #F6F6F8; }
.qk-rec-list-item:last-child { border-bottom:0; }
.qrl-idx { width:44rpx; height:44rpx; flex-shrink:0; border-radius:50%; background:#FFF1E0; color:var(--c-primary-dark); font-size:28rpx; font-weight:700; text-align:center; line-height:44rpx; }
.qrl-info { flex:1; min-width:0; display:flex; flex-direction:column; gap:4rpx; }
.qrl-name { font-size:32rpx; color:#1F2024; font-weight:600; }
.qrl-meta { font-size:26rpx; color:#999; }
.qrl-play { flex-shrink:0; width:56rpx; height:56rpx; border-radius:50%; background:#FFF1E0; color:var(--c-primary-dark); font-size:30rpx; text-align:center; line-height:56rpx; }
.qrl-play.on { background:var(--c-primary-dark); color:#fff; }
.qrl-del { flex-shrink:0; font-size:26rpx; color:#C0392B; padding:6rpx 10rpx; }
.qk-link { font-size:28rpx; color:#FFA800; font-weight:600; }

/* 会议资料 / 佐证列表 */
.qk-mat-item { display:flex; align-items:center; gap:14rpx; padding:20rpx 22rpx; background:#FAFBFC; border-radius:14rpx; margin-bottom:12rpx; }
.qk-mat-name { flex:1; font-size:28rpx; color:#1F2024; min-width:0; word-break:break-all; }
.qk-mat-meta { font-size: 28rpx; color:#666; flex-shrink:0; }
.qk-mat-arrow { color:#666; font-size:32rpx; }
.qk-mat-del { color:#666; font-size:38rpx; padding:0 8rpx; }

/* 居委会签字 */
.qk-juwei { display:flex; align-items:center; justify-content:space-between; gap:18rpx; background:#EBF5FB; border-radius:14rpx; padding:20rpx 26rpx; margin:18rpx 0; }
.qk-juwei-name { font-size:28rpx; color:#355C7D; }
.qk-juwei-chip { font-size:28rpx; color:#C77800; background:#fff; border:2rpx solid #F4D08A; border-radius:24rpx; padding:8rpx 24rpx; }
.qk-juwei-chip.on { color:#1D9E75; background:#E1F5EE; border-color:#E1F5EE; }

/* 等待主任拍板 */
.qk-wait-tip { text-align:center; font-size:28rpx; color:#666; background:#FAFBFC; border-radius:18rpx; padding:26rpx; margin-top:12rpx; line-height:1.6; }

/* 实时添加议题弹窗 */
.qk-modal-mask { position:fixed; inset:0; background:rgba(0,0,0,0.45); display:flex; align-items:center; justify-content:center; z-index:50; }
.qk-modal { width:88%; max-height:84vh; overflow-y:auto; box-sizing:border-box; background:#fff; border-radius:24rpx; padding:54rpx 44rpx 48rpx; }
.qk-modal-title { display:block; font-size:34rpx; font-weight:700; color:#1F2024; margin-bottom:46rpx; }
.qk-modal-input { box-sizing:border-box; width:100%; height:88rpx; line-height:88rpx; background:#F6F6F8; border-radius:14rpx; padding:0 20rpx; font-size:30rpx; margin-bottom:18rpx; border:0; }
.qk-modal-types { display:flex; gap:18rpx; margin-bottom:46rpx; }
.qk-type { font-size:28rpx; padding:12rpx 26rpx; border-radius:24rpx; background:#F6F6F8; color:#6B6E76; border:2rpx solid #ECECEF; }
.qk-type.on { background:#FFF3E0; color:#E67E22; border-color:#F4D08A; }
.qk-modal-label { display:block; font-size:26rpx; color:#777; font-weight:600; margin-bottom:26rpx; }
.qk-opt-row { display:flex; align-items:center; gap:14rpx; margin-bottom:12rpx; }
.qk-opt-num { font-size:28rpx; color:#666; width:40rpx; text-align:right; flex-shrink:0; }
.qk-opt-input { flex:1; min-width:0; height:76rpx; line-height:76rpx; margin-bottom:0; }
.qk-opt-del { font-size:38rpx; color:#999; padding:0 8rpx; flex-shrink:0; }
.qk-modal-area { width:100%; box-sizing:border-box; background:#F6F6F8; border-radius:14rpx; padding:18rpx 20rpx; font-size:28rpx; height:160rpx; margin-bottom:18rpx; border:0; }
.qk-modal-btns { display:flex; gap:30rpx; margin-top:22rpx; }
/* 添加/取消 较原生尺寸缩小约30%（高约96→68rpx） */
.qk-modal-btns .lp-ghost-btn, .qk-modal-btns .lp-primary-btn { flex:1; margin:0; padding:15rpx 0; font-size:26rpx; border-radius:34rpx; }
.qk-modal-note { display:block; font-size:26rpx; color:#666; margin-top:36rpx; line-height:1.5; }
/* 议题内容 + 语音输入按钮 同行 */
.qk-input-row { display:flex; align-items:center; gap:14rpx; margin-bottom:46rpx; }
.qk-input-row .qk-modal-input { flex:1; min-width:0; margin-bottom:0; }
.voice-input-btn { flex-shrink:0; display:inline-flex; align-items:center; gap:6rpx; border:2rpx solid #FFD79A; background:#FFF6E6; color:#C76A00; font-size:26rpx; font-weight:600; padding:12rpx 18rpx; border-radius:999rpx; line-height:1.3; }
.voice-input-btn.on { background:#FFA800; border-color:#FFA800; color:#fff; }
/* 语音输入弹层（与创建页同款） */
.voice-modal-mask { position:fixed; inset:0; z-index:300; background:rgba(0,0,0,0.5); display:flex; align-items:flex-end; }
.voice-modal { width:100%; background:#fff; border-radius:32rpx 32rpx 0 0; padding:32rpx 28rpx calc(32rpx + env(safe-area-inset-bottom)); display:flex; flex-direction:column; gap:24rpx; }
.vm-body { background:#f7f8fa; border-radius:20rpx; padding:24rpx 20rpx; min-height:160rpx; display:flex; flex-direction:column; align-items:center; gap:18rpx; }
.vm-wave { display:flex; align-items:flex-end; gap:8rpx; height:48rpx; }
.vm-wave span { width:8rpx; border-radius:4rpx; background:#0051FF; animation:vm-bar 1.1s ease-in-out infinite; }
.vm-wave span:nth-child(1) { height:20rpx; animation-delay:0s; }
.vm-wave span:nth-child(2) { height:36rpx; animation-delay:0.15s; }
.vm-wave span:nth-child(3) { height:48rpx; animation-delay:0.3s; }
.vm-wave span:nth-child(4) { height:36rpx; animation-delay:0.45s; }
.vm-wave span:nth-child(5) { height:20rpx; animation-delay:0.6s; }
@keyframes vm-bar { 0%,100% { transform:scaleY(0.4); opacity:0.6; } 50% { transform:scaleY(1); opacity:1; } }
.vm-text { font-size:32rpx; color:#1f2329; line-height:1.6; text-align:center; width:100%; word-break:break-all; }
.vm-interim { color:#888; }
.vm-placeholder { color:#aaa; }
.vm-actions { display:flex; gap:20rpx; }
.vm-actions .btn { flex:1; height:88rpx; font-size:34rpx; border-radius:18rpx; }

/* 第3步：选片列表 */
/* 本次会议录音分组：把多段框成一个整体 */
.qk-seg-group { border:2rpx solid #FFE2B0; border-radius:18rpx; padding:18rpx; background:#FFFCF6; margin-bottom:8rpx; }
.qk-seg-group-head { display:flex; align-items:baseline; justify-content:space-between; margin-bottom:14rpx; }
.qk-seg-group-title { font-size:30rpx; font-weight:700; color:#1F2024; }
.qk-seg-group-sub { font-size:26rpx; color:#D88900; font-weight:600; }
.qk-pick-list { display:flex; flex-direction:column; gap:14rpx; margin-bottom:8rpx; }
.qk-pick-item { display:flex; align-items:center; gap:16rpx; background:#FAFBFC; border:2rpx solid #EEF1F4; border-radius:16rpx; padding:22rpx; }
.qk-pick-item.transcribing { border-color:#FFD98A; background:#FFFDF6; }
.qk-pick-item.picked { border-color:#FFD98A; background:#FFFDF6; }
.qk-pick-check { width:46rpx; height:46rpx; border-radius:12rpx; border:3rpx solid #C9CFD6; background:#fff; display:flex; align-items:center; justify-content:center; font-size:32rpx; font-weight:700; color:#fff; flex-shrink:0; }
.qk-pick-check.on { background:#FFA800; border-color:#FFA800; }
.qk-pick-hint { display:block; font-size:26rpx; color:#8A9099; line-height:1.55; margin:16rpx 0 14rpx; }
.qk-back-rec { display:block; text-align:center; font-size:28rpx; color:#D88900; padding:18rpx 0 4rpx; }
.qk-pick-info { flex:1; min-width:0; }
.qk-pick-name { display:block; font-size:30rpx; color:#1f2329; word-break:break-all; }
.qk-pick-meta { display:block; font-size: 28rpx; color:#666; margin-top:6rpx; }
.qk-pick-done { font-size:28rpx; color:#27AE60; font-weight:600; flex-shrink:0; }
.qk-pick-play { flex-shrink:0; width:60rpx; height:60rpx; box-sizing:border-box; display:flex; align-items:center; justify-content:center; font-size:28rpx; color:#0051FF; border:2rpx solid #BCD0FF; background:#EEF3FF; border-radius:50%; }
.qk-pick-play.on { color:#fff; background:#0051FF; border-color:#0051FF; }
.qk-pick-del { flex-shrink:0; font-size:26rpx; color:#E74C3C; border:2rpx solid #F3C2BD; background:#FDECEA; border-radius:999rpx; padding:8rpx 20rpx; line-height:1.3; }
.qk-pick-ing { font-size:28rpx; color:#E67E22; flex-shrink:0; }
.qk-pick-item .lp-ghost-btn { width:auto; flex-shrink:0; margin:0; padding:14rpx 32rpx; }

/* ── 重做后的「最后一步」：AI 纪要审核 ── */
.qk-rev { border:2rpx solid #EEF1F4; border-radius:16rpx; padding:20rpx 22rpx; margin-bottom:16rpx; background:#fff; }
.qk-rev.warn { border-color:#FFB020; background:#FFF8EC; }
.qk-rev-head { display:flex; align-items:flex-start; gap:14rpx; }
.qk-rev-no { width:44rpx; height:44rpx; border-radius:50%; background:#FFF0D6; color:#C77700; font-size:28rpx; font-weight:700; text-align:center; line-height:44rpx; flex-shrink:0; }
.qk-rev-main { flex:1; min-width:0; }
.qk-rev-title { font-size:32rpx; color:#1f2329; font-weight:600; display:block; }
.qk-rev-meta { display:flex; flex-wrap:wrap; align-items:center; gap:12rpx; margin-top:10rpx; }
.qk-rev-result { font-size:28rpx; font-weight:700; padding:2rpx 14rpx; border-radius:8rpx; }
.qk-rev-result.passed { color:#1B8A3A; background:#E6F6EA; }
.qk-rev-result.rejected { color:#C0341D; background:#FBE9E6; }
.qk-rev-result.unclear { color:#C77700; background:#FFF0D6; }
.qk-rev-result.recorded { color:#5a6573; background:#eef0f2; }
.qk-rev-counts { font-size:28rpx; color:#8a94a0; }
.qk-rev-btn { color:#C77700; font-size:28rpx; font-weight:600; padding:6rpx 22rpx; border:2rpx solid #F0C277; border-radius:32rpx; flex-shrink:0; }
.qk-rev-tip { display:block; margin-top:12rpx; color:#C77700; font-size:28rpx; }
.qk-rev-box { margin-top:18rpx; padding-top:18rpx; border-top:2rpx dashed #ead9b6; }
.qk-vote-rule { display:block; font-size: 28rpx; color:#666; margin:8rpx 0 14rpx; }
.qk-result-pick { display:flex; gap:14rpx; margin-top:16rpx; }
.qk-result-pick span { flex:1; text-align:center; padding:18rpx 0; border-radius:12rpx; border:2rpx solid #dfe3e8; color:#5a6573; font-size:28rpx; }
.qk-result-pick span.on { background:#FFA800; color:#fff; border-color:#FFA800; font-weight:700; }

/* AI 额外发现（折叠） */
.qk-extra { margin:4rpx 0 12rpx; border:2rpx solid #EEF1F4; border-radius:16rpx; overflow:hidden; }
.qk-extra-head { display:flex; justify-content:space-between; align-items:center; padding:22rpx; background:#FAFBFC; color:#5a6573; font-size:28rpx; }
.qk-extra-toggle { color:#C77700; flex-shrink:0; }
.qk-extra-list { padding:4rpx 22rpx 16rpx; }
.qk-extra-item { display:flex; justify-content:space-between; align-items:center; gap:14rpx; padding:18rpx 0; border-top:2rpx solid #F0F2F4; }
.qk-extra-title { flex:1; min-width:0; color:#1f2329; font-size:28rpx; }
.qk-extra-acts { display:flex; gap:12rpx; flex-shrink:0; }
.qk-extra-acts .qk-cand-btn { flex:0 0 auto; padding:10rpx 28rpx; }

/* AI 纪要草稿展示 */
.qk-minutes-draft { max-height:560rpx; background:#fff; border:2rpx solid #EEF1F4; border-radius:16rpx; padding:24rpx; margin:16rpx 0; }
.qk-minutes-text { font-size:28rpx; color:#1f2329; line-height:1.75; white-space:pre-wrap; word-break:break-word; }
</style>
