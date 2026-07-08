<template>
  <div class="live-page" :class="{ 'lp-signin': currentStep === 1 }" style="overflow-y:auto;" v-if="detail">

    <!-- 页面对所有身份统一：主任/副主任可操作录音等，其余身份只读+表决/意见 -->
    <PageNav :title="currentStep === 1 ? '会议签到' : (isChair ? '会议录音' : '会议进行')" style="margin:-3.2vw -3.2vw 0;">
      <template #left>
        <div class="mlq-back" @click="onNavBack">‹</div>
      </template>
      <template #right>
        <button class="nav-home" @click="goHome">首页</button>
      </template>
    </PageNav>

    <!-- 腾讯会议式录音悬浮标签：录音进行时固定悬浮，窄条两行（状态 + 录音人），放在录音卡右侧空白处 -->
    <div v-if="isChair && currentStep === 2 && (recActive || isPaused)" ref="recFloatEl" class="rec-floating" :class="{ paused: isPaused, dragging: recDragging }" :style="recFloatStyle" @touchstart="onRecDragStart" @mousedown="onRecDragStart">
      <span class="rec-fl-dot"></span>
      <span class="rec-fl-txt">
        <span class="rec-fl-l1">{{ isPaused ? '录音已暂停' : '正在录音' }}</span>
        <span class="rec-fl-name">{{ recorderName }}</span>
      </span>
    </div>

    <!-- AI 工作中：一个遮罩连续覆盖 转写(asr) → 生成纪要(gen)；全部完成后显"已生成会议纪要"、点击进纪要页 -->
    <!-- 遮罩只在「生成会议纪要」阶段弹；上传/转写在后台静默进行，靠录音卡下方行内提示 -->
    <AiWorkingOverlay :active="generatingMinutes" :phase="overlayPhase" @confirm="onAiWorkDone" @close="onAiWorkClose" :audioDurSec="asrAudioDurSec" :audioFileSizeByte="asrFileSizeBytes" />

    <!-- 首屏（步骤条已删）：议题 + 签到/录音。撑满一屏高度，把参会名单顶到首屏之下（需要时往下拉才看到） -->
    <!-- 阶段条：①签到 ②会议录音 ③会议纪要 -->
    <div class="lp-flow">
      <div class="lp-flow-step" :class="flowStep > 1 ? 'done' : (flowStep === 1 ? 'on' : '')">
        <span class="lp-flow-dot"><template v-if="flowStep > 1">✓</template><template v-else>1</template></span>
        <span class="lp-flow-label">签到</span>
      </div>
      <span class="lp-flow-line" :class="{ done: flowStep > 1 }"></span>
      <div class="lp-flow-step" :class="flowStep > 2 ? 'done' : (flowStep === 2 ? 'on' : '')">
        <span class="lp-flow-dot"><template v-if="flowStep > 2">✓</template><template v-else>2</template></span>
        <span class="lp-flow-label">会议录音</span>
      </div>
      <span class="lp-flow-line" :class="{ done: flowStep > 2 }"></span>
      <div class="lp-flow-step" :class="flowStep >= 3 ? 'on' : ''">
        <span class="lp-flow-dot">3</span>
        <span class="lp-flow-label">会议纪要</span>
      </div>
    </div>

    <!-- ========== 步骤1：签到页（顶部精简会议卡·点开看议题 → 参会名单·默认收起 → 底部大签到钮·拇指区） ========== -->
    <template v-if="currentStep === 1">
      <div class="signin-page">
        <!-- 顶部精简会议卡：名称/时间/地点，点击展开议题 -->
        <div class="si-meet-card" @click="siMeetOpen = !siMeetOpen">
          <div class="si-meet-main">
            <div class="si-meet-title">{{ detail.title || '本次会议' }}</div>
            <div class="si-meet-meta">
              <span class="si-meet-row">🕒 {{ detail.meetingDate }} {{ detail.meetingTime }}</span>
              <span v-if="detail.location" class="si-meet-row">📍 {{ detail.location }}</span>
            </div>
          </div>
          <span class="si-meet-caret">{{ siMeetOpen ? '收起 ▲' : '议题 ▾' }}</span>
        </div>
        <div v-if="siMeetOpen" class="si-meet-topics">
          <template v-if="detail.record && detail.record.topics && detail.record.topics.length">
            <div class="si-topic-item" v-for="(item, index) in detail.record.topics" :key="item.id">
              <span class="si-topic-idx">{{ index + 1 }}</span>
              <span class="si-topic-title">{{ item.title }}</span>
            </div>
          </template>
          <span v-else class="si-topic-empty">暂无议题</span>
        </div>

        <!-- 参会名单：默认收起，点击展开 -->
        <div v-if="signinStats.total" class="si-roster" :class="{ open: siRosterOpen }">
          <div class="si-roster-bar" @click="siRosterOpen = !siRosterOpen">
            <span class="si-roster-title">参会名单</span>
            <span class="si-roster-count"><b>{{ signinStats.signedCount }}</b> / {{ signinStats.total }} 已签到</span>
            <span class="si-roster-caret">{{ siRosterOpen ? '收起 ▲' : '展开 ▾' }}</span>
          </div>
          <div v-if="siRosterOpen" class="si-roster-body">
            <div class="signin-roster-row" v-for="a in signinStats.list" :key="a.userRoleId">
              <span class="srr-name">{{ a.name }}</span>
              <span class="srr-role">{{ a.role }}</span>
              <span class="srr-state" :class="a.signedIn ? 'on' : (a.declined ? 'off' : 'wait')">{{ a.signedIn ? '已签到' : (a.declined ? '缺席' : '未签到') }}</span>
            </div>
          </div>
        </div>

        <!-- 弹性占位：把签到钮压到底部拇指区 -->
        <div class="si-spacer"></div>

        <!-- 底部大签到按钮 -->
        <div class="si-bottom">
          <button class="lp-primary-btn signin-big-btn" @click="confirmSignIn">{{ signedIn ? (isChair ? '进入录音' : '进入会议') : '签到' }}</button>
          <div class="signin-page-tip">{{ signedIn ? '你已签到，点击进入' : '到会后请点此签到' }}</div>
        </div>
      </div>
    </template>

    <!-- ========== 步骤2：录音 ========== -->
    <template v-else>
      <!-- 录音主卡（仅主任/副主任）：圆圈 + 时长 + 工具(重录/上传文件) + 已录列表(折叠) -->
      <div class="rec-hero" v-if="isChair">
        <!-- 上传录音文件：右上角小角标（方案A，弱化——少用的备选路径，别抢录音钮的焦点） -->
        <button v-if="!recDotOn && !isPaused && !uploading && !polling && !extracting && !generatingMinutes" class="rec-upload-corner" @click="chooseAudioFile" aria-label="上传录音文件">
          <svg class="rec-upload-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 15V4M8 8l4-4 4 4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/></svg>上传
        </button>
        <div class="qk-recorder">
          <button class="qk-rec-circle" :class="{ on: recDotOn }" @click="onCircleTap" :disabled="uploading"><span class="qrc-txt">{{ recCircleLabel }}</span></button>
          <span class="qk-rec-time">{{ timeText }}</span>
        </div>
        <div class="rec-hero-tools" v-if="isPaused && !uploading && !polling && !extracting && !generatingMinutes">
          <button class="rec-tool-btn" @click="restartRecording">重新录音</button>
        </div>
        <input ref="audioFileInput" type="file" accept="audio/*" multiple style="display:none" @change="onAudioFileChange" />
        <div v-if="recordings.length" class="rec-list">
          <div class="rec-list-head" @click="recListOpen = !recListOpen">
            <span>已录 {{ recordings.length }} 段</span>
            <span class="rec-list-toggle">{{ recListOpen ? '收起 ▲' : '展开 ▾' }}</span>
          </div>
          <div v-if="recListOpen" class="rec-list-body">
            <div class="qk-rec-list-item" v-for="(item, idx) in recordings" :key="item.id">
              <span class="qrl-idx">{{ idx + 1 }}</span>
              <div class="qrl-info">
                <span class="qrl-name">第 {{ idx + 1 }} 段 · {{ fmtDur(item.durationSec) }}</span>
                <span class="qrl-meta">{{ fmtTimeRange(item) }}</span>
                <span v-if="hasTranscript" class="qrl-view" @click="openTranscript('short')">查看内容 ›</span>
              </div>
              <span class="qrl-play" :class="{ on: playingId === item.id }" @click="togglePlay(item)">{{ playingId === item.id ? '⏸' : '▶' }}</span>
              <span v-if="!polling && !extracting" class="qrl-del" @click="deleteRecording(item, idx)">删除</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 下一步：状态提示 + 单一主按钮（仅主任）——随阶段变：上传识别 → 生成纪要 → 查看纪要 -->
      <div class="rec-action" v-if="isChair">
        <div v-if="uploading" class="rec-status"><span class="qk-up-spin"></span>正在上传录音…<span v-if="uploadPct > 0"> {{ uploadPct }}%</span></div>
        <div v-else-if="asrStatus === 'empty' || asrStatus === 'failed'" class="rec-status err">⚠ {{ asrErrorText }}</div>
        <template v-if="!uploading && !generatingMinutes">
          <!-- 内存后台任务因硬跳/刷新丢失后，据服务端+本地 durable 标记兜底恢复的「生成中」入口，点此进纪要页看进度/结果 -->
          <button v-if="minutesResuming || bgMinutesGenerating" class="lp-primary-btn rec-main" @click="viewMinutes">会议纪要生成中…点此查看</button>
          <!-- 纪要已生成 → 查看 / 重新生成；若有补录未上传，再给个上传次按钮，避免新录音卡住 -->
          <template v-else-if="minutesGenerated">
            <button class="lp-primary-btn rec-main" @click="viewMinutes">查看会议纪要</button>
            <button class="lp-primary-btn rec-main" @click="regenerateMinutes">重新生成纪要</button>
            <button v-if="canUpload" class="lp-primary-btn rec-main" :disabled="freshRecEmpty" @click="uploadRecordingStep">上传并识别新录音</button>
          </template>
          <template v-else>
            <!-- ① 只要有任一段已转写出内容 →「生成会议纪要」即可点；转写中置灰并显示「正在转写，请稍候…」。
                 ② 点击时若还有未上传的录音，先弹框问「停止并上传转写 / 继续录音」（见 generateNow）。 -->
            <button v-if="hasAnyTranscribed" class="lp-primary-btn rec-main" :disabled="polling || extracting" @click="generateNow">{{ (polling || extracting) ? '正在转写，请稍候…' : '生成会议纪要' }}</button>
            <!-- 手里有未上传录音：始终用「生成会议纪要」同款主按钮（不再降级成灰色小字），提示补录需先上传识别 -->
            <button v-if="canUpload" class="lp-primary-btn rec-main" :disabled="freshRecEmpty" @click="uploadRecordingStep">上传并识别录音</button>
            <!-- 有已上传录音但还没转写出内容、手里也没在录 → 转写中/失败，常驻一个置灰按钮 -->
            <button v-else-if="!hasAnyTranscribed && hasSavedRecordings" class="lp-primary-btn rec-main" disabled>{{ (polling || extracting) ? '正在转写，请稍候…' : '生成会议纪要' }}</button>
          </template>
        </template>
        <!-- 识别中：主按钮已置灰，这里补一句可继续录音的说明 -->
        <div v-if="(polling || extracting) && !uploading" class="rec-status"><span class="qk-up-spin"></span>录音识别中，可继续录音，识别完即可点击生成</div>
      </div>

      <!-- 议题卡 -->
      <div class="lp-info-card">
        <div class="lp-info-head"><span class="lp-info-title">会议议题</span></div>
        <div class="lp-agenda" :class="'lp-agenda--fs' + agendaFontLevel" ref="agendaEl">
          <template v-if="detail.record && detail.record.topics && detail.record.topics.length">
            <div class="lp-agenda-item" v-for="(item, index) in detail.record.topics" :key="item.id">
              <span class="lp-agenda-idx">{{ index + 1 }}</span>
              <span class="lp-agenda-title">{{ item.title }}</span>
              <button class="lp-agenda-pill" :class="topicBadgeDone(item) ? 'done' : topicPillType(item)" @click="openTopicSheet(item)">{{ topicPillLabel(item) }}<span class="lp-pill-chev">{{ topicBadgeDone(item) ? '✓' : '›' }}</span></button>
            </div>
          </template>
          <span v-else class="lp-agenda-empty">暂无议题</span>
        </div>
        <div v-if="isChair" class="lp-add-topic-row">
          <button class="lp-add-topic" @click="openAddTopic">+ 临时添加议题</button>
        </div>
      </div>
    </template>

    <!-- 非主任：录音试听列表（主任的录音列表已并入录音卡） -->
    <div class="lp-card qk-rec-list" v-if="currentStep === 2 && !isChair && recordings.length">
      <div class="qk-rec-list-head">会议录音 {{ recordings.length }} 段</div>
      <div class="qk-rec-list-item" v-for="(item, idx) in recordings" :key="item.id">
        <span class="qrl-idx">{{ idx + 1 }}</span>
        <div class="qrl-info">
          <span class="qrl-name">第 {{ idx + 1 }} 段 · {{ fmtDur(item.durationSec) }}</span>
          <span class="qrl-meta">{{ fmtTimeRange(item) }}</span>
        </div>
        <span class="qrl-play" :class="{ on: playingId === item.id }" @click="togglePlay(item)">{{ playingId === item.id ? '⏸' : '▶' }}</span>
      </div>
    </div>

    <!-- 弹性占位：把参会人员卡顶到首屏之下，默认看不到，需向下拉才显示 -->
    <div class="attend-spacer" v-if="currentStep !== 1 && signinStats.total"></div>
    <!-- 参会人员（合并：计数 + 进度条 + 逐人名单，可展开）——默认收起、顶到底部 -->
    <div class="lp-card attend-card" :class="{ open: attendOpen }" v-if="currentStep !== 1 && signinStats.total">
      <div class="attend-bar" @click="attendOpen = !attendOpen">
        <span class="lp-card-title">参会人员</span>
        <span class="ls-count" :class="signinLevel"><b>{{ signinStats.signedCount }}</b>/{{ signinStats.total }} 已签到</span>
        <span class="attend-caret">{{ attendOpen ? '收起 ▲' : '展开 ▾' }}</span>
      </div>
      <div v-if="attendOpen" class="attend-detail">
        <div class="ls-bar"><div class="ls-fill" :class="signinLevel" :style="{ width: signinStats.pct + '%' }"></div></div>
        <div class="lr-list">
          <div class="ls-pop-item" v-for="a in signinStats.list" :key="a.userRoleId">
            <span class="ls-dot" :class="a.signedIn ? 'on' : (a.declined ? 'off' : 'wait')"></span>
            <span class="ls-name">{{ a.name }}</span>
            <span class="ls-role">{{ a.role }}</span>
            <span class="ls-state" :class="a.signedIn ? 'on' : (a.declined ? 'off' : 'wait')">{{ a.signedIn ? '已签到' : (a.declined ? '缺席' : '未签到') }}</span>
          </div>
        </div>
      </div>
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
          <input class="qk-modal-input" placeholder="输入议题" v-model="newTopicForm.title" />
          <button class="voice-input-btn" :class="{ on: voiceOn }" @click="startTopicVoice">🎤 语音输入</button>
        </div>
        <div class="qk-modal-label">议题类型</div>
        <div class="qk-modal-types">
          <span class="qk-type" :class="newTopicForm.type === 'notice' ? 'on' : ''" @click="pickTopicType('notice')">通报事项</span>
          <span class="qk-type" :class="newTopicForm.type === 'discussion' ? 'on' : ''" @click="pickTopicType('discussion')">讨论事项</span>
          <span class="qk-type" :class="newTopicForm.type === 'decision' ? 'on' : ''" @click="pickTopicType('decision')">表决事项</span>
        </div>
        <template v-if="newTopicForm.type === 'notice'">
          <div class="qk-modal-label">通知正文</div>
          <textarea class="qk-modal-input qk-modal-textarea" placeholder="请输入内容" v-model="newTopicForm.content" rows="3"></textarea>
        </template>
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

    <!-- 签到 → 录音 跳转动画：签到成功后短暂全屏，勾选动画 + 提示，随后进入录音步 -->
    <div v-if="signinFx" class="signin-fx">
      <div class="signin-fx-card">
        <div class="signin-fx-check">✓</div>
        <span class="signin-fx-title">签到成功</span>
        <span class="signin-fx-sub">正在进入录音…</span>
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
import { aiTask, startAiTask, finishAiTask, failAiTask, clearAiTask } from '@/composables/aiTask'
import { getStorage, setStorage, removeStorage } from '@/utils/storage'
import { useRecorder } from '@/composables/useRecorder'
import { applyHotwords } from '@/utils/helpers'
import PageNav from '@/components/PageNav.vue'
import AiWorkingOverlay from '@/components/AiWorkingOverlay.vue'
import TopicSheet from '@/components/TopicSheet.vue'

const route = useRoute()
const rec = useRecorder()

const POLL_INTERVAL = 1500
const MAX_POLL_COUNT = 120        // 兜底：时长未知时至少轮询这么多次(≈3 分钟)
const QUICK_STATE_PREFIX = 'committee_quick_meeting_state_'

// 轮询上限按待转写音频时长动态给足余量。
// 豆包大模型录音识别是异步的，处理耗时随录音时长增长；固定 3 分钟会在豆包仍在
// 正常转写长录音时被误判为「转写超时」。这里用「基础 5 分钟 + 时长×2」估算预算，
// 封顶 30 分钟，换算成轮询次数。时长未知时回退到 MAX_POLL_COUNT。
function maxPollCount() {
  const durSec = asrAudioDurSec.value || 0
  if (durSec <= 0) return MAX_POLL_COUNT
  const budgetSec = Math.min(1800, Math.max(300, 300 + durSec * 2))
  return Math.max(MAX_POLL_COUNT, Math.ceil(budgetSec * 1000 / POLL_INTERVAL))
}

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
function openTopicSheet(item) {
  // 未签到不进表决/意见弹层：在点击时就提醒签到，而不是进去后再提示
  if (!signedIn.value) {
    toast({ title: '请先在下方签到，签到后即可表决/发言', icon: 'none' })
    return
  }
  sheetTopicId.value = item.id
}
// 弹层"上一个/下一个议题"：当前议题在列表中的位置 + 切换
function _sheetTopicIndex() {
  const list = (detail.value && detail.value.record && detail.value.record.topics) || []
  return { list, i: list.findIndex(t => t.id === sheetTopicId.value) }
}
const sheetHasPrev = computed(() => _sheetTopicIndex().i > 0)
const sheetHasNext = computed(() => { const { list, i } = _sheetTopicIndex(); return i >= 0 && i < list.length - 1 })
function gotoPrevTopic() { const { list, i } = _sheetTopicIndex(); if (i > 0) sheetTopicId.value = list[i - 1].id }
function gotoNextTopic() { const { list, i } = _sheetTopicIndex(); if (i >= 0 && i < list.length - 1) sheetTopicId.value = list[i + 1].id }
// 议题状态标签：显示"待/已"状态而非属性。状态由后端 TopicVO 字段驱动，
// 录音经 ASR 识别+确认后 status/opinionCount 会更新，loadDetail 刷新后标签自动翻成"已"。
function topicBadgeDone(item) {
  if (item.voteRequired) return item.status === 'passed' || item.status === 'failed' // 表决达成(含ASR识别票数确认后)
  if (item.type === 'notice') return !!item.notified || (item.opinionCount || 0) > 0 // 通报：已宣读/全体已看，或录音里被提到
  return (item.opinionCount || 0) > 0 // 讨论：录音里被提到/有意见 = 已处理
}
function topicBadgeText(item) {
  const done = topicBadgeDone(item)
  if (item.voteRequired) return done ? '已表决' : '待表决'
  if (item.type === 'notice') return done ? '已通报' : '待通报'
  return done ? '已讨论' : '待讨论'
}
// 胶囊按钮文案：待办用动词(去表决/去通报/去讨论)增强"可点"召唤；已办沿用状态词(已表决…)
function topicPillLabel(item) {
  if (topicBadgeDone(item)) return topicBadgeText(item)
  if (item.voteRequired) return '去表决'
  if (item.type === 'notice') return '去通报'
  return '去讨论'
}
// 胶囊按钮按议题类型着色，与议题弹层标签同一套：表决橙 / 通报紫 / 讨论蓝
function topicPillType(item) {
  if (item.voteRequired) return 'vote'
  if (item.type === 'notice') return 'notice'
  return 'discuss'
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
// 悬浮窗显示的录音人姓名（本人＝当前录音的主任/副主任）
const recorderName = computed(() => {
  const role = getStorage('activeRole', null) || {}
  return (selfAttendance.value && selfAttendance.value.name) || role.realName || '本人'
})
// ── 悬浮录音标签：可拖动（手机触摸 + 桌面鼠标）──
// 默认走 CSS 的 top/right 定位；拖过一次后切成 left/top 像素定位并记住位置（限制在屏内）。
const recFloatEl = ref(null)
const recDragging = ref(false)
const recDragPos = ref(null) // { left, top } px；null=没拖过，用 CSS 默认位
const recFloatStyle = computed(() => recDragPos.value
  ? { left: recDragPos.value.left + 'px', top: recDragPos.value.top + 'px', right: 'auto', bottom: 'auto' }
  : null)
let _recDrag = null // 拖动会话：{ dx, dy, w, h }
function _recPoint(e) {
  const t = (e.touches && e.touches[0]) || (e.changedTouches && e.changedTouches[0]) || e
  return { x: t.clientX, y: t.clientY }
}
function onRecDragStart(e) {
  const el = recFloatEl.value
  if (!el) return
  const r = el.getBoundingClientRect()
  const p = _recPoint(e)
  _recDrag = { dx: p.x - r.left, dy: p.y - r.top, w: r.width, h: r.height }
  recDragging.value = true
  window.addEventListener('touchmove', onRecDragMove, { passive: false })
  window.addEventListener('touchend', onRecDragEnd)
  window.addEventListener('touchcancel', onRecDragEnd)
  window.addEventListener('mousemove', onRecDragMove)
  window.addEventListener('mouseup', onRecDragEnd)
}
function onRecDragMove(e) {
  if (!_recDrag) return
  if (e.cancelable) e.preventDefault() // 拖动时别让页面跟着滚
  const p = _recPoint(e)
  const margin = 6
  const maxLeft = Math.max(margin, window.innerWidth - _recDrag.w - margin)
  const maxTop = Math.max(margin, window.innerHeight - _recDrag.h - margin)
  recDragPos.value = {
    left: Math.min(Math.max(margin, p.x - _recDrag.dx), maxLeft),
    top: Math.min(Math.max(margin, p.y - _recDrag.dy), maxTop),
  }
}
function onRecDragEnd() {
  recDragging.value = false
  _recDrag = null
  if (recDragPos.value) setStorage('recFloatPos', recDragPos.value) // 记住拖到的位置（跨会话）
  window.removeEventListener('touchmove', onRecDragMove, { passive: false })
  window.removeEventListener('touchend', onRecDragEnd)
  window.removeEventListener('touchcancel', onRecDragEnd)
  window.removeEventListener('mousemove', onRecDragMove)
  window.removeEventListener('mouseup', onRecDragEnd)
}
onUnmounted(onRecDragEnd) // 卸载兜底：清掉可能残留的全局监听

// 位置记忆：恢复上次拖到的位置（跨会话）。恢复的是绝对像素，卡片显示时再按当前视口夹紧，
// 防止换设备/横竖屏后跑到屏幕外。
const _savedRecFloatPos = getStorage('recFloatPos', null)
if (_savedRecFloatPos && typeof _savedRecFloatPos.left === 'number' && typeof _savedRecFloatPos.top === 'number') {
  recDragPos.value = { left: _savedRecFloatPos.left, top: _savedRecFloatPos.top }
}
function clampRecFloatIntoView() {
  const el = recFloatEl.value
  if (!el || !recDragPos.value) return
  const r = el.getBoundingClientRect()
  const margin = 6
  const maxLeft = Math.max(margin, window.innerWidth - r.width - margin)
  const maxTop = Math.max(margin, window.innerHeight - r.height - margin)
  recDragPos.value = {
    left: Math.min(Math.max(margin, recDragPos.value.left), maxLeft),
    top: Math.min(Math.max(margin, recDragPos.value.top), maxTop),
  }
}
// 卡片出现（开始/继续录音）时，按当前视口把记忆位置夹回屏内
watch(() => recActive.value || isPaused.value, async (vis) => {
  if (vis && recDragPos.value) { await nextTick(); clampRecFloatIntoView() }
})
// 当前是否有"可上传的新内容"（正在录 / 暂停中 / 内存里有还没上传的录音）。
// 上传成功后已 rec.reset()，此值变 false → "结束录音并上传"置灰，避免重复上传同一段。
const canUpload = computed(() => recActive.value || isPaused.value || rec.hasRecording.value)
// 手里这段录音时长为 0（还没录到内容）→ 禁用「上传并识别录音」，避免上传空录音（火山必判静音失败）
const freshRecEmpty = computed(() => (rec.seconds.value || 0) < 1)
// 已上传过录音、且当前没有新录音在手 → 上传后的"空闲"态，引导继续录下一段
const idleAfterUpload = computed(() => !rec.recording.value && !rec.hasRecording.value && hasSavedRecordings.value)
// 本轮识别已覆盖的录音 id（识别成功/恢复历史转写时回填）——用它判断是否还有新录音没识别，
// 不依赖 recordings.asrStatus（桩模式不落该字段）
const recognizedIds = ref([])
// 还有录音没经大模型识别 → 按钮显示「上传录音」；识别完变「继续生成会议纪要」
const needRecognize = computed(() => !generated.value
  || (recordings.value || []).some(r => r.asrStatus !== 'done' && !recognizedIds.value.includes(r.id)))
// 有任一段"已转写出内容"（done 或本轮已识别）→ 生成按钮即可用，不再要求全部段都识别完
const hasAnyTranscribed = computed(() => (recordings.value || [])
  .some(r => r.asrStatus === 'done' || recognizedIds.value.includes(r.id)))
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
const uploadPct = ref(0)   // 上传录音的实时进度百分比（0=未知/刚开始，靠 axios onUploadProgress 更新）
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
// 识别失败/空转写的行内提示文案（后台转写不弹遮罩，失败靠这条明确告知 + 引导重试）
const asrErrorText = computed(() => {
  if (asrStatus.value === 'empty') return '没识别到说话声，可能录到了静音或太轻。请点上方圆圈重新录音，靠近麦克风、说清楚些再试。'
  if (asrStatus.value === 'failed') return (processText.value || '录音识别失败') + '　可点「上传录音」重试'
  return ''
})
const extraction = ref(null)

const presetTopics = ref([])
const aiTopics = ref([])
const transcript = ref([])
const transcriptPreview = ref('')
const transcriptFullText = ref('')
const transcriptCharCount = ref(0)
// 是否已有转写内容（转写完成后录音行才显示「查看内容」入口）
const hasTranscript = computed(() => !!(transcriptFullText.value || '').trim() || (transcript.value || []).length > 0)
const transcriptVisible = ref(false)
const transcriptMode = ref('short')
const ending = ref(false)
// 重做后的「最后一步」：AI 纪要审核
const minutesGenerated = ref(false)   // 是否已生成 AI 纪要草稿
const generatingMinutes = ref(false)  // 生成中（按钮 loading 态）
const minutesGenAt = ref(0)           // 生成纪要发起时刻(ms)；>0且较近=生成在途。整页刷新/硬跳会丢内存 aiTask，用它+服务端从durable信号重建入口
const minutesResuming = ref(false)    // 内存任务已丢、据 durable 标记+服务端轮询恢复出的「生成中」入口
// 后台生成跨页恢复用（本页无 keep-alive，切走再回来是全新实例、generatingMinutes 会丢）：
let _leftWhileGenerating = false // 生成中切走过本页 → 完成时该走全局悬浮条兜底，而非误判「仍在前台看遮罩」
let _bgRehydrated = false        // 本页遮罩是「切回时按后台任务恢复」出来的（非本实例发起）→ 由 watch(aiTask) 收尾

// 切回本会议页时按全局后台任务恢复界面：仍在生成→重亮遮罩；已生成→显示「查看纪要」态。
// 不恢复的话，切走再回来遮罩没了、悬浮条又在发起页隐藏，用户会以为任务中止了。
function resumeBgAiTask() {
  const mine = aiTask.targetPath && aiTask.targetPath.indexOf('meetingId=' + meetingId.value) >= 0
  if (!mine) return
  if (aiTask.active) {
    overlayPhase.value = 'gen'
    generatingMinutes.value = true
    _bgRehydrated = true
  } else if (aiTask.done) {
    minutesGenerated.value = true
  }
}

// 后台生成结束（完成/失败/清除）→ 收起「切回恢复」出来的遮罩，并把纪要状态反映到本页。
watch(() => aiTask.active, (act) => {
  if (act || !_bgRehydrated) return
  _bgRehydrated = false
  generatingMinutes.value = false
  if (aiTask.done) { minutesGenerated.value = true; persistQuickState() }
  else loadDetail() // 被清除/失败 → 重新拉真实状态
})
// 本页全屏遮罩(AiWorkingOverlay :active=generatingMinutes)是否正盖着这个任务 → 同步给全局。
// 遮罩在=悬浮胶囊隐藏(免重复)；遮罩不在(切走/resume 没恢复)=胶囊自动出来当兜底入口。
watch(generatingMinutes, (v) => { aiTask.overlayShown = v }, { immediate: true })
// 全局后台纪要任务是否属于本会议（与首页悬浮条同源）——内存任务还在时用它兜底显示「生成中」入口
const bgMinutesGenerating = computed(() => aiTask.active && !!aiTask.targetPath && aiTask.targetPath.indexOf('meetingId=' + meetingId.value) >= 0)
// —— 重做：阶段条 + 折叠态 + 签到跳转动画 ——
// 阶段：1=签到 2=录音 3=生成会议纪要（已生成即到第3步）
const flowStep = computed(() => minutesGenerated.value ? 3 : (currentStep.value === 1 ? 1 : 2))
const recListOpen = ref(false)   // 录音卡内「已录N段」列表是否展开
const attendOpen = ref(false)    // 参会名单是否展开
const siMeetOpen = ref(false)    // 签到页：会议卡是否展开(看议题)
const siRosterOpen = ref(false)  // 签到页：参会名单是否展开
const signinFx = ref(false)      // 签到→录音 跳转动画遮罩
function playSigninFx() {
  signinFx.value = true
  setTimeout(() => { currentStep.value = 2; persistQuickState() }, 220) // 遮罩下快速切到录音步
  setTimeout(() => { signinFx.value = false }, 520)                     // 一闪而过，别停留
}
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
const newTopicForm = reactive({ title: '', type: 'discussion', decisionType: 'none', options: [], content: '' })

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
let _recognizeAfterUpload = false // 「上传录音」标记：上传成功后只识别、不生成
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
  if (generatingMinutes.value) _leftWhileGenerating = true // 生成中切走 → 完成走全局悬浮条兜底
  aiTask.overlayShown = false // 本页遮罩随本页销毁 → 交还给全局悬浮胶囊兜底（保证切走后有入口）
  persistQuickState()
  clearPoll()
  clearMinutesPoll()
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
    minutesGenerated: minutesGenerated.value, // 纪要已生成标志：持久化，避免回首页再进来退回「生成会议纪要」单键
    minutesGenAt: minutesGenAt.value,         // 生成发起时刻：整页刷新丢了内存任务后，据此判断"生成在途"并轮询服务端恢复入口
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
  minutesGenerated.value = !!saved.minutesGenerated // 恢复「纪要已生成」→ 显示 查看纪要/重新生成 两键，而非「生成会议纪要」
  minutesGenAt.value = saved.minutesGenAt || 0      // 恢复「生成发起时刻」→ reconcileMinutesState 据此兜底恢复"生成中"入口
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
      resumeBgAiTask() // 切回本页时恢复后台生成的遮罩/完成态（内存 aiTask 还在时）
      reconcileMinutesState() // 内存任务丢失(硬跳/刷新)兜底：从服务端+本地durable标记重建"生成中/查看"入口
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

// —— 纪要状态兜底恢复：内存里的后台 aiTask 一旦因整页刷新/硬跳丢失，切回本页就没了「生成中/查看」入口。
//    这里改从两处 durable 信号重建，不依赖内存单例：①服务端是否已有纪要 ②本地持久的"生成发起时刻"。
let _minutesPollTimer = null
function clearMinutesPoll() { if (_minutesPollTimer) { clearInterval(_minutesPollTimer); _minutesPollTimer = null } }
async function reconcileMinutesState() {
  if (!meetingId.value || minutesGenerated.value) return
  if (typeof api.committeeMinutes !== 'function') return
  // ① 服务端已有纪要 → 直接给「查看纪要」入口（最可靠，硬跳/换实例都不丢）
  let hasServer = false
  try { const t = await api.committeeMinutes(meetingId.value); hasServer = !!(t && String(t).trim()) } catch (e) {}
  if (hasServer) { minutesGenerated.value = true; minutesGenAt.value = 0; minutesResuming.value = false; persistQuickState(); return }
  // 内存任务仍在(遮罩/悬浮条已接管)则不插手
  if (generatingMinutes.value || bgMinutesGenerating.value) return
  // ② 问服务端任务状态（权威，跨刷新/换设备/隔天，不依赖前端内存单例）
  if (typeof api.committeeMinutesStatus === 'function') {
    let st = null
    try { st = await api.committeeMinutesStatus(meetingId.value) } catch (e) {}
    const s = st && st.status
    if (s === 'running' || s === 'success') { startMinutesResumePoll(); return } // 生成中/刚完成待落库 → 轮询到正文出现即转"查看"
    if (s === 'failed') { minutesGenAt.value = 0; persistQuickState(); return }    // 失败 → 让用户可重新生成
    if (s === 'none') { minutesGenAt.value = 0; persistQuickState(); return }      // 从没生成过 → 生成入口
  }
  // ③ 旧后端无 minutes-status 接口 → 退回本地"生成发起时刻"兜底
  const RECENT = 5 * 60 * 1000
  if (minutesGenAt.value && (Date.now() - minutesGenAt.value) < RECENT) startMinutesResumePoll()
  else if (minutesGenAt.value) { minutesGenAt.value = 0; persistQuickState() }
}
// 轮询服务端直到纪要出现(生成完成)或超时(放弃 → 用户可重新生成)。仅在内存任务已丢时用。
function startMinutesResumePoll() {
  minutesResuming.value = true
  clearMinutesPoll()
  const deadline = (minutesGenAt.value || Date.now()) + 5 * 60 * 1000
  const tick = async () => {
    if (minutesGenerated.value) { minutesResuming.value = false; clearMinutesPoll(); return }
    try {
      const t = await api.committeeMinutes(meetingId.value)
      if (t && String(t).trim()) { minutesGenerated.value = true; minutesGenAt.value = 0; minutesResuming.value = false; clearMinutesPoll(); persistQuickState(); return }
    } catch (e) {}
    if (Date.now() > deadline) { minutesResuming.value = false; minutesGenAt.value = 0; clearMinutesPoll(); persistQuickState() }
  }
  _minutesPollTimer = setInterval(tick, 4000)
  tick()
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
  // 身份提示：优先用本人在本会议的参会身份，回退到登录角色
  const role = getStorage('activeRole', null) || {}
  const who = (selfAttendance.value && selfAttendance.value.name) || role.realName || '本人'
  const roleName = (selfAttendance.value && selfAttendance.value.role) || role.role || ''
  const idText = '你是「' + who + (roleName ? ' · ' + roleName : '') + '」。\n'
  const res = await showModal({
    title: '入会签到',
    content: idText + (isChair.value
      ? '请确认本人已到会。签到后即可开始会议录音，并围绕会议议题进行讨论与表决。'
      : '请确认本人已到会。签到后即可围绕会议议题发表意见、参与表决。'),
    confirmText: '签到',
    cancelText: '再看看'
  })
  if (!res.confirm) return
  try {
    await api.committeeSelfToggle(meetingId.value, 'signedIn')
    signedIn.value = true
    loadDetail()
    playSigninFx()   // 播放「签到成功 → 进入录音」跳转动画，动画中途切到录音步
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
  if (uploading.value) return // 转写在后台跑不拦录音；仅上传中(占用同段)时不响应

  // 录音中且未暂停 → 暂停（个别 iOS 不支持暂停，pause 静默失败时提示用户）
  if (rec.recording.value && !rec.paused.value) {
    rec.pause()
    if (!rec.paused.value) {
      toast({ title: '本设备不支持暂停，可直接点“上传录音并生成会议纪要”', icon: 'none' })
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
  // 后台正在转写上一段 → 只启动新录音，绝不 clearPoll / 重置转写状态，避免打断后台转写
  const bgTranscribing = polling.value || extracting.value
  if (!bgTranscribing) {
    clearPoll()
    clearQuickState()
    _recognizeAfterUpload = false // 重新开录，清掉可能残留的"上传后自动识别"标记
    generated.value = false
    extraction.value = null
    aiTopics.value = []
    transcript.value = []
    transcriptPreview.value = ''
    transcriptFullText.value = ''
    transcriptCharCount.value = 0
    transcriptVisible.value = false
    uploading.value = false
    taskId.value = ''
    asrStatus.value = ''
  }
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
  if (uploading.value) return
  // 上一段还在后台识别 → 本段先留在本地(不并发第二个转写)，等识别完再传
  if (polling.value || extracting.value) {
    toast({ title: '上一段还在识别，识别完就能上传这段', icon: 'none' })
    return
  }

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
  if (uploading.value) return
  if (rec.recording.value && rec.paused.value) rec.resume()
}

// 重新录音：放弃当前这段，从头开始（需确认）
async function restartRecording() {
  if (uploading.value) return
  const res = await showModal({
    title: '',
    content: '将清除当前录音片段，确认重录？',
    confirmText: '重新录音',
    cancelText: '取消',
    contentBold: true,
    emphasizeConfirm: true
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
  const files = Array.from((e.target && e.target.files) || [])
  if (!files.length) {
    toast({ title: '未选择文件', icon: 'none' })
    return
  }
  // 支持一次多选：逐个上传保存（不自动识别），全部传完后由「生成会议纪要」统一识别
  let ok = 0
  for (const file of files) {
    asrFileSizeBytes.value = file.size
    const dur = await getAudioDuration(file) // 读取已选音频的时长（秒），用于转写页展示
    if (dur > 0) asrAudioDurSec.value = dur
    try {
      await uploadRecordingFile(file, dur)
      ok++
    } catch (err) { /* 单个失败 uploadRecordingFile 内已提示，继续传下一个 */ }
  }
  if (files.length > 1) toast({ title: '已上传 ' + ok + '/' + files.length + ' 个录音文件', icon: 'success' })
  // 全部文件上传完 → 自动统一识别（后台静默，不弹遮罩）；识别完成后「生成会议纪要」按钮自动变亮可点
  if (ok > 0) uploadAndRecognize()
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
  uploadPct.value = 0
  asrStatus.value = ''
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
    // 第4参：axios 上传进度回调，实时更新百分比（到 100% 后仍在等服务器保存/转码，转圈继续）
    await api.committeeUploadRecording(meetingId.value, file, durationSec, (e) => {
      if (e && e.total) uploadPct.value = Math.min(100, Math.round((e.loaded / e.total) * 100))
    })
    uploadPct.value = 100
    toast({ title: '录音已上传', icon: 'success' })
    uploading.value = false
    rec.reset() // 清空录音器内存：消除返回录音页时的残留时长，避免把同一段重复上传
    currentStep.value = 2 // 停在录音页：显示"上传录音并生成会议纪要"按钮
    await loadDetail() // 刷新录音列表（await 确保新录音进入列表后再自动识别）
    // 「上传录音」发起的上传 → 自动接着识别（不生成，识别完关遮罩露两键）
    if (_recognizeAfterUpload) { _recognizeAfterUpload = false; uploadAndRecognize() }
  } catch (e) {
    _recognizeAfterUpload = false
    uploading.value = false
    currentStep.value = 2 // 退回录音步，可复用已录音频重试
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
  if (generated.value) {
    recognizedIds.value = (recordings.value || []).map(r => r.id)
    // 后台识别完成（无遮罩）：给一条成功提示，引导去点「生成会议纪要」
    toast({ title: '录音识别完成，可生成会议纪要', icon: 'success' })
  }
}

// 「上传录音」：上传在手录音 → 识别（转写→提炼），不生成。识别完关遮罩，露出「继续上传录音 / 生成会议纪要」两键。
async function uploadRecordingStep() {
  if (!isChair.value) { toast({ title: '仅主任/副主任可操作', icon: 'none' }); return }
  if (uploading.value || polling.value || extracting.value || generatingMinutes.value) return
  if (rec.recording.value || rec.hasRecording.value) {
    _recognizeAfterUpload = true
    await finishRecord() // 上传成功后 → uploadRecordingFile 里触发 uploadAndRecognize
    return
  }
  if (!hasSavedRecordings.value) { toast({ title: '请先开始录音', icon: 'none' }); return }
  if (needRecognize.value) await uploadAndRecognize() // 已上传但未识别 → 直接识别
}

// 识别完成后的主按钮——「生成会议纪要」：表决核对（AI票数确认/未表决提示）→ 生成。
// （想再补录：直接点上方录音圆圈，此时它显示「继续录音」，录完会重新出现「上传并识别录音」）
async function generateNow() {
  // ② 还有未上传的录音（正在录 / 暂停 / 内存里未上传）→ 先问：停止并上传转写，还是继续录音
  if (canUpload.value) {
    const r = await showModal({
      title: '正在录音中',
      content: '当前还有未上传的录音。要先停止并把这段一起上传、转写吗？（转写完成后，再点「生成会议纪要」）',
      confirmText: '停止并上传转写',
      cancelText: '继续录音'
    })
    // 方案B：确认后只停录+上传+转写；识别完按钮自动变亮，由用户手动再点生成
    if (r.confirm) uploadRecordingStep()
    return
  }
  // ④ 有"未检测到说话声"的空段 → 提示是哪几段，确认后忽略空段、用其余已转写内容生成
  const empties = (recordings.value || []).filter(r => r.asrStatus === 'empty')
  if (empties.length) {
    const nums = empties.map(e => '第 ' + (((recordings.value || []).indexOf(e)) + 1) + ' 段').join('、')
    const r = await showModal({
      title: '部分录音没有声音',
      content: nums + ' 未检测到说话声（可能录到了静音或声音太轻）。确认后将忽略这些段，用其余已转写的内容生成会议纪要。',
      confirmText: '确认生成',
      cancelText: '取消'
    })
    if (!r.confirm) return
  }
  voteCheckFlow()
}

// 真实议题里"需要表决的"（用 detail.record.topics 的 voteRequired，不依赖 ASR 抽取的 presetTopics）
function realVoteTopics() {
  const raw = (detail.value && detail.value.record && detail.value.record.topics) || []
  return raw.filter(t => t.voteRequired)
}
// 真实议题里"还没完成表决的"：voteRequired 且没投票数(voted=0)、也没达成结论(status 非 passed/failed)
function unvotedRealTopics() {
  return realVoteTopics().filter(t => (t.voted || 0) === 0 && t.status !== 'passed' && t.status !== 'failed')
}

// 识别完成后的表决核对（由「上传录音并生成会议纪要」触发）：检查有没有未完成表决的议题，
// 有 → 弹提示；没有 → 直接生成纪要（进纪要页）。表决状态一律以真实议题为准，不靠 ASR 抽取。
// 1) 无表决议题 → 直接生成
// 2) AI 识别到票数 → 确认填写后生成（生成前守卫再提示其它仍未表决的议题）
// 3) 有表决议题但没识别到票数、也没 app 投票 → 弹「还有议题没有表决」提示
// 4) 有表决议题且都已表决 → 直接生成
async function voteCheckFlow() {
  try { await loadDetail() } catch (e) { /* 刷新失败不阻断核对 */ }

  // 1) 没有需要表决的议题 → 直接生成
  if (!realVoteTopics().length) { continueGenerateMinutes(true); return }

  const recognized = (presetTopics.value || []).filter(t => t.voteRequired && t.aiVote)

  // 2) AI 识别到票数 → 确认填写后生成
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
        await loadDetail()
      } catch (e) {
        toast({ title: (e && e.message) || '保存失败，请重试', icon: 'none' })
        return
      }
      continueGenerateMinutes(false) // 守卫再检查其它没被识别、也没 app 投票的表决议题
      return
    }
    toast({ title: '未填写表决结果，可点议题手动表决后再生成', icon: 'none' })
    return
  }

  // 3) 有表决议题、没识别到票数、也没 app 投票 → 弹提示（仍要生成 / 先去表决）
  const missing = unvotedRealTopics()
  if (missing.length) {
    const r = await showModal({
      title: '还有议题没有表决',
      content: '「' + missing[0].title + '」' + (missing.length > 1 ? '等 ' + missing.length + ' 个表决议题' : '') + '还没有表决结果。',
      confirmText: '仍要生成',
      cancelText: '先去表决'
    })
    if (r.confirm) continueGenerateMinutes(true)
    return
  }

  // 4) 有表决议题且都已有表决结果 → 直接生成
  continueGenerateMinutes(true)
}

// 「继续生成会议纪要」：确认票数落库 + 大模型生成（遮罩 gen）。skipGuard=true 表示表决核对刚做过，不再重复提醒
async function continueGenerateMinutes(skipGuard) {
  if (!isChair.value) { toast({ title: '仅主任/副主任可生成纪要', icon: 'none' }); return }
  if (uploading.value || polling.value || extracting.value || generatingMinutes.value) return
  if (!generated.value) { toast({ title: '请先上传录音完成识别', icon: 'none' }); return }
  if (!skipGuard) {
    const missing = unvotedRealTopics()
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
  const mid = meetingId.value
  minutesGenAt.value = Date.now() // durable「生成中」时刻：整页刷新/硬跳丢了内存 aiTask 后，切回本页据此从服务端恢复入口
  // 全局后台任务：切到别的页面时顶部悬浮「会议纪要生成中…」，完成后可点直达纪要页
  const minutesPath = '/pages/minutes/minutes?meetingId=' + mid + '&from=meeting-live-quick&view=1'
  startAiTask({ label: '会议纪要生成中…', originPath: window.location.pathname, targetPath: minutesPath })
  persistQuickState() // 立刻落盘生成标记（原先要等生成成功才 persist，中途刷新就丢了 → 重进无入口）
  try {
    await api.committeeQuickConfirm(mid, buildConfirmPayload())
    await api.committeeQuickPolish(mid) // 大模型生成纪要并落库（长请求，切到别的页面也不中断，后端继续跑）
    minutesGenerated.value = true
    persistQuickState()
    // 已切走/关掉遮罩（后台完成）→ 全局完成条（可点直达纪要页）；仍在前台看遮罩 → 遮罩完成态接管
    if (backgroundDone()) finishAiTask({ doneLabel: '会议纪要已生成' })
    else clearAiTask()
  } catch (e) {
    if (backgroundDone()) failAiTask({ failLabel: '会议纪要生成失败' })
    else { clearAiTask(); toast({ title: (e && e.message) || '生成纪要失败，请重试', icon: 'none' }) }
  } finally {
    generatingMinutes.value = false // 仍在前台看 → 遮罩转完成态「已生成会议纪要」
    minutesGenAt.value = 0           // 生成已结束(成功/失败)→ 清 durable 标记，避免重进误显示"生成中"
    persistQuickState()
  }
}

// 生成完成时用户是否已不在等这块遮罩：生成中切走过本页（_leftWhileGenerating）或点×关了遮罩（generatingMinutes=false）
// → 该用全局悬浮提示而非遮罩完成态。
// ⚠ 不能用 document.querySelector('.live-page') 判断：本页无 keep-alive，切走再回来是全新实例，
//   旧实例的 promise 完成时会误命中新实例的 .live-page，把「已生成」悬浮条错误清掉（=看着像中止）。
function backgroundDone() {
  return _leftWhileGenerating || !generatingMinutes.value
}

// 遮罩完成按钮：recognize 阶段 → 关遮罩，露出「继续上传录音 / 生成会议纪要」两键（不再自动生成）；
// gen 阶段（「查看会议纪要」）→ 进纪要页。软路由偶发不切换——加硬导航兜底。
function onAiWorkDone() {
  if (overlayPhase.value === 'recognize') return // 识别完成：遮罩自行收起，页面回到两键状态
  // view=1：进纪要页先看正文（不直接进编辑模式）
  const q = 'meetingId=' + meetingId.value + '&from=meeting-live-quick&view=1'
  navigateTo('/pages/minutes/minutes?' + q)
  setTimeout(() => {
    if (document.querySelector('.live-page')) {
      window.location.href = '/minutes?' + q
    }
  }, 500)
}

// 关闭「AI 工作中」遮罩：停止前端等待态，回到当前页（后台若已转完，下次进入会自动恢复）。
// 必须把 generatingMinutes 也置回 false——否则「生成会议纪要/继续上传/查看纪要」三组按钮
// 都带 !generatingMinutes 条件会被一直隐藏，录音页只剩暂停态的「重新录音」。中止生成后应
// 回到「生成会议纪要 + 继续上传录音」两键，方便重新生成。
function onAiWorkClose() {
  clearPoll()
  polling.value = false
  extracting.value = false
  generatingMinutes.value = false
  _transcribingRecordingId.value = ''
}

// 录音时长（秒）→ "MM:SS"；无时长显示占位
// 录音起止时间：createdAt=后端上传时刻(≈录音结束)，start=end−时长。显示 "MM-DD HH:MM:SS – HH:MM:SS"（近似，±上传耗时几秒）。
function fmtTimeRange(item) {
  const raw = item && item.createdAt
  const end = raw ? new Date(String(raw).replace(' ', 'T')) : null
  if (!end || isNaN(end.getTime())) return fmtTime(raw)
  const dur = Number(item.durationSec) || 0
  const start = new Date(end.getTime() - dur * 1000)
  const p = (n) => String(n).padStart(2, '0')
  const hms = (d) => p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds())
  return p(end.getMonth() + 1) + '-' + p(end.getDate()) + ' ' + hms(start) + ' – ' + hms(end)
}

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
    if (_pollCount > maxPollCount()) {
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
    if (_pollCount > maxPollCount()) {
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
  const m = String(message || '')
  if (m.indexOf('45000006') >= 0) return '音频文件读取失败，请返回上一步重新上传录音'
  if (/timeout|timed out|超时/i.test(m)) return '录音较大，转写提交超时了。建议分段录制、缩短单段时长后重试'
  if (/network|连接|refused|unreachable/i.test(m)) return '网络不稳定，转写没提交成功，请稍后重试'
  return m || '录音转写失败，请重试'
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
    generated.value = false
    asrStatus.value = 'failed'
    processText.value = '录音识别成功，但整理议题失败'
    toast({ title: e.message || '整理议题失败，请重试', icon: 'none' })
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
  const q = 'meetingId=' + meetingId.value + '&from=meeting-live-quick&view=1'
  navigateTo('/pages/minutes/minutes?' + q)
  // 软路由偶发不切换（URL 变了却停在录音页）→ 500ms 后仍在本页则硬导航兜底
  setTimeout(() => {
    if (document.querySelector('.live-page')) window.location.href = '/minutes?' + q
  }, 500)
}

// 重新生成纪要：会覆盖当前草稿，先确认再走生成流程（表决核对 → 生成）
async function regenerateMinutes() {
  const res = await showModal({
    title: '',
    content: '将重新生成会议纪要，覆盖当前草稿。确认重新生成？',
    confirmText: '重新生成',
    cancelText: '取消',
    contentBold: true,
    emphasizeConfirm: true
  })
  if (!res.confirm) return
  generateNow()
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
  newTopicForm.content = ''
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
    // 现场新增只允许通报/讨论/表决，重大事项后端会拦截；通报类带正文
    await api.committeeAddTopic(meetingId.value, f.title.trim(), f.type, dt, optionsJson, false, f.type === 'notice' ? (f.content || '').trim() : null)
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

// 顶栏右上「首页」：回业委会主页
function goHome() {
  redirectTo('/main')
  // 软路由偶发不切换（URL 变了却停在录音页）→ 500ms 后仍在本页则硬导航兜底
  setTimeout(() => {
    if (document.querySelector('.live-page')) window.location.replace('/main')
  }, 500)
}

// 顶栏左上返回：签到已拆成单独一步，录音步(step2)点返回=回上一步「签到页」并回到未签到态(方便重签/调试)；
// 已经在签到页(step1)则真正返回上一个页面。
async function onNavBack() {
  if (currentStep.value === 2) {
    try { if (signedIn.value) await api.committeeSelfToggle(meetingId.value, 'signedIn') } catch (e) { /* 回退失败不阻断 */ }
    // 本地驱动切回签到页；不调 loadDetail（其自动进步逻辑会因刚 un-sign 的读取竞态把我们弹回录音步）。
    signedIn.value = false
    signinFx.value = false
    currentStep.value = 1
    persistQuickState()
    refreshAttendance()   // 只刷新名单/进度，不动步骤机
  } else {
    // 签到页返回 → 回会议详情(来时那页)。⚠详情页读的是 query.id（不是 meetingId）；带 stay=1 让进行中会议别又弹回本页。软跳+硬导航兜底。
    const url = '/committee-detail?id=' + meetingId.value + '&stay=1'
    redirectTo(url)
    setTimeout(() => { if (document.querySelector('.live-page')) window.location.href = url }, 500)
  }
}
</script>

<style scoped>
/* 比一屏再高一截：配合弹性占位，把参会人员卡整条压到首屏之下（下拉才见） */
.live-page { min-height:calc(100vh + 320rpx); background:#f4f5f7; padding:24rpx; padding-bottom:48rpx; box-sizing:border-box; display:flex; flex-direction:column; }
.attend-spacer { flex:1 1 auto; min-height:32rpx; }
/* 仅本页：顶栏矮 24rpx(12px)，124→100rpx（PageNav 是共享组件，其他页不动） */
.live-page :deep(.page-nav) { height:calc(100rpx + env(safe-area-inset-top)); }
.live-page :deep(.page-nav .nav-back) { height:100rpx; }
/* 顶栏统一为纯深橙（与其他页一致，覆盖 PageNav 默认黄橙渐变） */
:deep(.page-nav) { background: var(--c-primary-dark); }

/* 步骤指示器 */
/* 首屏容器：至少撑满一屏（100vh 减 顶栏+页面上下留白），参会名单被顶到首屏之下，往下拉才看到 */
.lp-fold { display:flex; flex-direction:column; min-height:calc(100vh - 96rpx); }

/* 会议信息卡 */
.lp-info-card { background:#fff; border-radius:24rpx; padding:24rpx 28rpx 0; margin-top:24rpx; margin-bottom:28rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); } /* 与下方卡收紧(84→28)；议题区加大见 .lp-agenda */
.lp-info-head { display:flex; align-items:center; justify-content:space-between; margin-bottom:12rpx; }
.lp-info-title { display:block; font-size:34rpx; font-weight:700; color:#1F2024; line-height:1.35; } /* 标题缩两号(42→34) */
/* 临时添加议题：移到卡片底部，一条分隔线上方居中的蓝字按钮 */
.lp-add-topic-row { border-top:2rpx solid #F2F2F4; margin-top:10rpx; padding:16rpx 0 20rpx; display:flex; justify-content:center; }
.lp-add-topic { font-size:28rpx; color:#1A73E8; font-weight:600; background:#fff; border:2rpx solid #C9DCF8; border-radius:999rpx; padding:12rpx 32rpx; line-height:1.3; font-family:inherit; }
.lp-add-topic:active { background:#F0F6FF; }
/* 标题 + 引导语同排一组；+临时添加靠右 */
.lp-info-title-wrap { display:flex; align-items:baseline; gap:14rpx; min-width:0; flex:1; }
.lp-info-row { display:flex; align-items:flex-start; gap:18rpx; font-size:34rpx; color:#444; margin-bottom:6rpx; }
.lp-info-row.top { align-items:flex-start; }
.lp-info-k { color:#666; flex-shrink:0; width:80rpx; font-size:34rpx; }
.lp-info-v { flex:1; min-width:0; word-break:break-all; }
/* 议题区固定高度(约4行)：卡片大小恒定；放不下先缩字号(下面 fs 档)，仍放不下则本区下拉滚动 */
.lp-agenda { flex:1; min-width:0; max-height:296rpx; overflow-y:auto; } /* 议题卡加大20px(256→296)显示更多议题；议题少时贴合内容不留空白，多时封顶滚动 */
/* 字号自适应档位：每档缩一号(4rpx=2px)，最多缩到 28rpx(fs2)；高档同时压缩行距让更多议题露出 */
.lp-agenda--fs1 .lp-agenda-title { font-size:32rpx; }
.lp-agenda--fs2 .lp-agenda-title { font-size:28rpx; }
.lp-agenda--fs2 .lp-agenda-item { padding:12rpx 0; }
.lp-agenda-item { display:flex; align-items:center; gap:14rpx; padding:26rpx 0; border-bottom:2rpx solid #F2F2F4; } /* 行距加大，看起来没那么挤 */
.lp-agenda-item:last-child { border-bottom:0; padding-bottom:8rpx; } /* 最后一条：胶囊下方空白减小 */
/* 方案D：状态胶囊即按钮。待办=白底橙描边+轻投影(浮起来像按钮)、动词"去表决"+›；已办=白底绿描边+✓(仍可点看结果)。议题文字本身不可点 */
.lp-agenda-pill { flex-shrink:0; display:inline-flex; align-items:center; gap:2rpx; font-family:inherit;
  font-size:24rpx; font-weight:400; line-height:1.3; border-radius:999rpx; padding:8rpx 18rpx;
  background:#fff; border:3rpx solid var(--c-primary); color:var(--c-primary-dark);
  box-shadow:0 3rpx 10rpx rgba(199,106,0,0.22); }
.lp-agenda-pill:active { background:#FFF6EC; }
.lp-pill-chev { font-size:28rpx; line-height:1; margin-top:-2rpx; }
/* 按议题类型着色，与议题弹层标签同一套：表决橙 / 通报紫 / 讨论蓝 */
.lp-agenda-pill.vote { border-color:#E68A2E; color:#C76A00; box-shadow:0 3rpx 10rpx rgba(199,106,0,0.22); }
.lp-agenda-pill.notice { border-color:#9B6BE0; color:#6D3FC4; box-shadow:0 3rpx 10rpx rgba(109,63,196,0.20); }
.lp-agenda-pill.discuss { border-color:#3E8FCF; color:#1F6FB2; box-shadow:0 3rpx 10rpx rgba(31,111,178,0.20); }
.lp-agenda-pill.done { background:#fff; border-color:#BFE0B2; color:#2E7D32; box-shadow:0 3rpx 10rpx rgba(46,125,50,0.16); }
.lp-agenda-idx { width:42rpx; height:42rpx; flex-shrink:0; border-radius:50%; background:#F2F2F4; color:#666; font-size: 28rpx; text-align:center; line-height:42rpx; }
.lp-agenda-title { flex:1; min-width:0; color:#1F2024; word-break:break-all; font-size:34rpx; line-height:1.35; cursor:default; }
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
.lp-primary-btn.narrow { width:420rpx; margin-left:auto; margin-right:auto; font-size:42rpx; padding:20rpx 0; }
/* 录音卡两个按钮：宽度贴合文字、只比字宽一圈、居中 */
.lp-primary-btn[disabled] { background:#d8c3ab; color:#fff; }
.lp-ghost-btn { display:block; width:100%; box-sizing:border-box; background:#fff; color:#FFA800; border:2rpx solid #FFA800; border-radius:44rpx; font-size:30rpx; padding:22rpx 0; margin-top:16rpx; margin-bottom:8rpx; }
/* 没有新录音可传时，"结束录音并上传"弱化为不可用样子（仍可点，点了弹提示说明已上传） */
.lp-ghost-btn.muted { color:#BBB; border-color:#E2E2E2; background:#FAFAFA; }
/* 结束录音并上传：浅橙填充，明显一点（覆盖 muted 灰化，始终醒目） */
.lp-ghost-btn.finish-upload, .lp-ghost-btn.finish-upload.muted { background:#FFF1E0; color:var(--c-primary-dark); border:2rpx solid var(--c-primary-dark); font-weight:700; width:fit-content; margin-left:auto; margin-right:auto; margin-top:8rpx; padding:10rpx 34rpx; font-size:26rpx; } /* 上距收紧让录音卡更紧凑 */
/* 上传后空闲提示：已录段数会合并为一份 */
.qk-seg-hint { font-size:26rpx; color:#9A6A00; line-height:1.5; margin:6rpx 0 2rpx; background:#FFF8EC; border-radius:12rpx; padding:14rpx 18rpx; text-align:center; }
/* 上传录音中：转圈 + 实时进度百分比 */
.qk-seg-hint.uploading { display:flex; align-items:center; justify-content:center; gap:12rpx; font-weight:600; }
.qk-up-spin { width:30rpx; height:30rpx; border:5rpx solid #F0D9B8; border-top-color:#C76A00; border-radius:50%; animation:qk-up-spin 0.7s linear infinite; }
.qk-up-pct { color:#C76A00; font-variant-numeric:tabular-nums; }
@keyframes qk-up-spin { to { transform:rotate(360deg); } }
/* 识别失败/空转写：明确红色提示条 */
.qk-seg-hint.asr-error { background:#FDECEA; color:#C0392B; font-weight:600; text-align:left; }

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
.lp-rec { padding-bottom:22rpx; } /* 录音卡底部空白再收紧(34→22) */
.qk-recorder { display:flex; flex-direction:column; align-items:center; gap:4rpx; padding:2rpx 0 0; }
.qk-rec-circle { width:120rpx; height:120rpx; border-radius:50%; background:var(--c-primary); color:#fff; font-size:24rpx; font-weight:700; display:flex; align-items:center; justify-content:center; border:5rpx solid #FFF3E0; box-shadow:0 6rpx 16rpx rgba(199,106,0,0.24); box-sizing:border-box; } /* 方案A：大幅缩小 204→120、字36→24 */
/* 圈内文案固定两字一行（"开始/录音"两行） */
.qrc-txt { display:block; width:2em; line-height:1.35; text-align:center; word-break:break-all; }
.qk-rec-circle:active { transform:scale(0.95); }
.qk-rec-circle:disabled { background:#E5E8EC; color:#999; border-color:#F2F2F4; box-shadow:none; }
.qk-rec-circle.on { background:#E74C3C; border-color:#FDECEA; box-shadow:0 8rpx 22rpx rgba(231,76,60,0.30); animation:qkpulse 1.2s ease-in-out infinite; }
@keyframes qkpulse { 0%,100% { opacity:1; transform:scale(1); } 50% { opacity:.55; transform:scale(.92); } }
.qk-rec-time { font-size:36rpx; font-weight:700; color:#1f2329; letter-spacing:2rpx; margin-top:4rpx; font-variant-numeric:tabular-nums; font-feature-settings:'tnum' 1; } /* 计时用等宽数字(tabular)，秒数跳动不晃 */

.qk-note { font-size:28rpx; color:#666; line-height:1.6; margin-top:20rpx; background:#FAFBFC; border-radius:14rpx; padding:18rpx 20rpx; }
.qk-note.warn { color:#C77700; background:#FFF8EC; }

/* 入会签到：状态待签到（左）+ 身份（右）一行两端对齐 */
/* 签到卡（精简版）：无标题行，收窄上下留白 */
.lp-sign { padding:30rpx 32rpx 26rpx; }

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
.qk-rec-actions .lp-primary-btn.narrow40 { flex:0 0 60%; max-width:60%; margin:0 auto; padding:16rpx 0; }
.qk-rec-actions .lp-primary-btn.narrow40 .qra-main { font-size:30rpx; }
/* 生成会议纪要：与"结束录音并上传"同尺寸的小胶囊，并上移 16rpx(8px) */
.qk-rec-actions .lp-primary-btn.gen-minutes { flex:0 0 auto; width:fit-content; margin:-16rpx auto 0; padding:12rpx 36rpx; }
.gen-minutes .qra-main { font-size:26rpx; }
/* 常驻「生成会议纪要」：橙色小胶囊，居中，与录音卡内其它按钮呼应 */
.gen-standalone { width:60%; max-width:100%; margin:30rpx auto 0; padding:16rpx 0; display:flex; align-items:center; justify-content:center; }
.gen-standalone .qra-main { font-size:30rpx; font-weight:700; white-space:nowrap; }
/* 上传本地录音文件：低调次级入口(虚线框)，与现场录音圈区分；支持一次多选 */
.qk-import-row { display:flex; justify-content:center; margin-top:16rpx; }
/* 样式A 橙色描边胶囊：白底+橙边+橙字，与录音卡其它按钮同一橙色系 */
.qk-import-btn { display:inline-flex; align-items:center; gap:10rpx; background:#fff; border:3rpx solid var(--c-primary-dark, #E8890C); color:#C76A00; font-size:30rpx; font-weight:600; border-radius:999rpx; padding:20rpx 48rpx; }
.qk-import-btn:active { background:#FFF6E8; }
.qk-import-ico { width:34rpx; height:34rpx; color:var(--c-primary-dark, #E8890C); flex-shrink:0; }

/* ============ 重做：阶段条 / 录音卡 / 单一主按钮 / 合并名单 / 签到动画 ============ */
/* 阶段条 */
/* 顶栏右上「首页」按钮（插槽内容走本页作用域） */
/* 顶栏左上返回箭头（自定义 #left 插槽，走本页作用域） */
.mlq-back { width:96rpx; height:64rpx; display:flex; align-items:center; justify-content:center; color:#fff; font-size:52rpx; font-weight:700; line-height:1; }
.mlq-back:active { opacity:0.7; }
.nav-home { display:inline-flex; align-items:center; height:64rpx; margin-right:20rpx; padding:0 24rpx; border:2rpx solid rgba(255,255,255,0.6); border-radius:34rpx; background:rgba(255,255,255,0.12); color:#fff; font-size:30rpx; font-weight:600; line-height:1; }
.nav-home:active { background:rgba(255,255,255,0.28); }
/* 流程条：下移 10px；左右留白让首尾圆点不贴边；底部留白容纳绝对定位的文字 */
.lp-flow { display:flex; align-items:center; padding:20rpx 40rpx 52rpx; margin-top:20rpx; }
/* 步骤只由圆点决定宽度(文字绝对定位不撑宽)，三个步骤等宽 → 圆点等距对称 */
.lp-flow-step { position:relative; flex-shrink:0; }
.lp-flow-dot { width:64rpx; height:64rpx; border-radius:50%; background:#E4E6EA; color:#9AA0A6; font-size:34rpx; font-weight:700; display:flex; align-items:center; justify-content:center; transition:all .2s; }
/* 文字绝对定位在圆点正下方居中，不影响圆点水平位置 */
.lp-flow-label { position:absolute; top:calc(100% + 8rpx); left:50%; transform:translateX(-50%); font-size:24rpx; color:#9AA0A6; white-space:nowrap; }
.lp-flow-step.on .lp-flow-dot { background:var(--c-primary-dark, #E8890C); color:#fff; box-shadow:0 0 0 6rpx rgba(232,137,12,0.18); }
.lp-flow-step.on .lp-flow-label { color:var(--c-primary-dark, #E8890C); font-weight:600; }
.lp-flow-step.done .lp-flow-dot { background:#3E9B34; color:#fff; }
.lp-flow-step.done .lp-flow-label { color:#3E9B34; }
/* 连线与圆点同在 align-items:center 下自然居中(步骤已只有圆点高，无需再补 margin) */
.lp-flow-line { flex:1; height:4rpx; background:#E4E6EA; margin:0 12rpx; border-radius:2rpx; }
.lp-flow-line.done { background:#3E9B34; }

/* 录音主卡 */
.rec-hero { position:relative; background:#F6F7F9; border-radius:24rpx; padding:20rpx 22rpx 14rpx; margin-bottom:18rpx; box-shadow:0 3rpx 14rpx rgba(0,0,0,0.045); }
.rec-hero-tools { display:flex; justify-content:center; gap:20rpx; margin-top:16rpx; flex-wrap:wrap; }
.rec-tool-btn { display:inline-flex; align-items:center; gap:8rpx; background:#8C3A2A; border:0; color:#fff; font-size:26rpx; font-weight:600; border-radius:999rpx; padding:14rpx 30rpx; box-shadow:0 4rpx 12rpx rgba(140,58,42,0.22); }
.rec-tool-btn:active { background:#753023; }
/* 方案A：上传录音——右上角小角标，弱化不抢焦点 */
.rec-upload-corner { position:absolute; top:16rpx; right:16rpx; z-index:2; display:inline-flex; align-items:center; gap:6rpx; height:48rpx; padding:0 18rpx; border:1rpx solid #EAD9C4; border-radius:24rpx; background:#FBF6EF; color:#9C7A4A; font-size:22rpx; line-height:1; }
.rec-upload-corner:active { background:#F3E9DA; }
.rec-upload-ico { width:24rpx; height:24rpx; flex-shrink:0; }
.rec-tool-ico { width:30rpx; height:30rpx; color:var(--c-primary-dark, #E8890C); flex-shrink:0; }
/* 已录N段（折叠） */
.rec-list { margin-top:12rpx; border-top:2rpx solid #ECEDEF; padding-top:10rpx; }
.rec-list-head { display:flex; align-items:center; justify-content:space-between; font-size:24rpx; color:#5A6069; padding:4rpx 2rpx; }
.rec-list-toggle { font-size:22rpx; color:var(--c-primary-dark, #E8890C); }
.rec-list-body { margin-top:6rpx; }

/* 下一步：单一主按钮区 */
.rec-action { margin-bottom:24rpx; display:flex; flex-direction:column; align-items:center; gap:14rpx; }
.rec-status { display:flex; align-items:center; justify-content:center; gap:12rpx; width:100%; font-size:28rpx; color:#E8890C; font-weight:600; padding:6rpx 0; }
.rec-status.err { color:#C0392B; }
.rec-main { width:52% !important; max-width:360rpx; margin:0 auto !important; font-size:26rpx !important; font-weight:700; padding:16rpx 0 !important; box-shadow:0 6rpx 16rpx rgba(232,137,12,0.22); } /* 缩小约40% */
.rec-sub { background:none; border:0; color:#8A8F98; font-size:28rpx; padding:6rpx 20rpx; }
.rec-sub:active { color:#5A6069; }

/* 参会人员（默认收成一条可点窄条；点击展开进度条+名单；整体缩小、顶到底部） */
.attend-card { padding-top:18rpx !important; padding-bottom:18rpx !important; }
.attend-bar { display:flex; align-items:center; gap:12rpx; cursor:pointer; }
.attend-bar .ls-count { margin-left:auto; }
.attend-caret { font-size:24rpx; color:var(--c-primary-dark, #E8890C); flex-shrink:0; }
.attend-bar:active { opacity:0.6; }
.attend-detail { margin-top:16rpx; }
.attend-card .lr-list { margin-top:12rpx; border-top:2rpx solid #F2F2F4; padding-top:8rpx; }
/* 标题与文本整体缩小两号（仅本卡，不影响其它复用 .ls-* 的地方） */
.attend-card .lp-card-title { font-size:28rpx; }
.attend-card .ls-count { font-size:26rpx; }
.attend-card .ls-count b { font-size:32rpx; }
.attend-card .ls-name { font-size:26rpx; }
.attend-card .ls-role { font-size:24rpx; }
.attend-card .ls-state { font-size:24rpx; padding:3rpx 14rpx; }

/* 录音悬浮标签：窄条两行（状态 / 录音人），默认在录音卡右侧空白处；可拖动到任意位置（触摸/鼠标） */
.rec-floating { position:fixed; top:calc(env(safe-area-inset-top) + 330rpx); right:16rpx; z-index:400;
  display:flex; align-items:center; gap:10rpx; max-width:190rpx;
  background:rgba(22,24,28,0.86); color:#fff; padding:12rpx 16rpx; border-radius:16rpx;
  box-shadow:0 6rpx 18rpx rgba(0,0,0,0.26); line-height:1.3; -webkit-backdrop-filter:blur(6rpx); backdrop-filter:blur(6rpx);
  cursor:grab; touch-action:none; user-select:none; -webkit-user-select:none; }
/* 拖动中：抓手光标 + 轻微放大提亮，明确"抓住了" */
.rec-floating.dragging { cursor:grabbing; box-shadow:0 10rpx 26rpx rgba(0,0,0,0.34); transform:scale(1.04); }
.rec-fl-dot { width:14rpx; height:14rpx; border-radius:50%; background:#ff3b30; flex-shrink:0; box-shadow:0 0 0 0 rgba(255,59,48,0.55); animation:recFlPulse 1.3s ease-out infinite; }
.rec-floating.paused { background:rgba(60,50,30,0.9); }
.rec-floating.paused .rec-fl-dot { background:#F5A623; animation:none; }
.rec-fl-txt { display:flex; flex-direction:column; min-width:0; }
.rec-fl-l1 { font-size:22rpx; opacity:0.9; }
.rec-fl-name { font-size:24rpx; font-weight:700; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
@keyframes recFlPulse { 0%{ box-shadow:0 0 0 0 rgba(255,59,48,0.55);} 70%{ box-shadow:0 0 0 14rpx rgba(255,59,48,0);} 100%{ box-shadow:0 0 0 0 rgba(255,59,48,0);} }
@media (prefers-reduced-motion: reduce) { .rec-fl-dot { animation:none; } }
.attend-card .lr-rec-tag { font-size:22rpx; }

/* 签到 → 录音 跳转动画 */
/* 签到页：大签到按钮 + 下方签到情况名单 */
/* ===== 步骤1 签到页：会议卡 + 名单(默认收起) + 底部大钮(拇指区) ===== */
.live-page.lp-signin { min-height:100vh; }   /* 签到步按整屏排布，不要录音步那额外 320rpx */
.signin-page { flex:1 1 auto; display:flex; flex-direction:column; gap:20rpx; padding:8rpx 0; }
/* 顶部精简会议卡 */
.si-meet-card { display:flex; align-items:center; gap:18rpx; background:#fff; border-radius:26rpx; padding:38rpx 34rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); }
.si-meet-main { flex:1; min-width:0; }
.si-meet-title { font-size:38rpx; font-weight:700; color:#1F2024; line-height:1.45; }
.si-meet-meta { display:flex; flex-direction:column; gap:18rpx; margin-top:24rpx; }
.si-meet-row { font-size:29rpx; color:#61656C; line-height:1.55; }
.si-meet-caret { flex-shrink:0; font-size:26rpx; color:#E8890C; font-weight:600; }
.si-meet-topics { background:#fff; border-radius:24rpx; padding:12rpx 28rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); margin-top:-8rpx; }
.si-topic-item { display:flex; align-items:flex-start; gap:14rpx; padding:16rpx 0; border-bottom:2rpx solid #F4F4F6; }
.si-topic-item:last-child { border-bottom:0; }
.si-topic-idx { flex-shrink:0; width:40rpx; height:40rpx; border-radius:50%; background:#FFF1E0; color:#E8890C; font-size:26rpx; font-weight:700; display:flex; align-items:center; justify-content:center; }
.si-topic-title { flex:1; min-width:0; font-size:30rpx; color:#2B2E33; line-height:1.45; }
.si-topic-empty { display:block; text-align:center; color:#9AA0A6; font-size:28rpx; padding:16rpx 0; }
/* 参会名单：收起态一条，展开显示逐人 */
.si-roster { background:#fff; border-radius:24rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); padding:0 28rpx; }
.si-roster-bar { display:flex; align-items:center; gap:14rpx; padding:26rpx 0; }
.si-roster-title { font-size:30rpx; font-weight:700; color:#1f2329; }
.si-roster-count { flex:1; font-size:26rpx; color:#8A8F98; }
.si-roster-count b { font-size:30rpx; color:#27AE60; font-weight:800; }
.si-roster-caret { flex-shrink:0; font-size:26rpx; color:#8A8F98; }
.si-roster-body { padding-bottom:10rpx; border-top:2rpx solid #F2F2F4; }
/* 底部拇指区 */
.si-spacer { flex:1 1 auto; min-height:24rpx; }
.si-bottom { display:flex; flex-direction:column; align-items:center; gap:16rpx; padding-top:8rpx; }
.signin-big-btn { width:72% !important; max-width:500rpx; margin:0 auto !important; font-size:46rpx !important; font-weight:700; letter-spacing:4rpx; padding:30rpx 0 !important; border-radius:56rpx; box-shadow:0 8rpx 22rpx rgba(232,137,12,0.24); }
.signin-page-tip { font-size:28rpx; color:#8A8F98; }
/* 参会名单 */
.signin-roster { width:88%; max-width:640rpx; margin-top:14rpx; background:#fff; border-radius:20rpx; padding:20rpx 26rpx 8rpx; box-shadow:0 6rpx 20rpx rgba(0,0,0,0.05); box-sizing:border-box; }
.signin-roster-head { display:flex; align-items:center; justify-content:space-between; font-size:28rpx; font-weight:700; color:#1f2329; padding-bottom:12rpx; border-bottom:2rpx solid #F2F2F4; }
.signin-roster-count { font-size:26rpx; color:#8A8F98; font-weight:400; }
.signin-roster-count b { font-size:30rpx; color:#27AE60; font-weight:800; }
.signin-roster-row { display:flex; align-items:center; gap:14rpx; padding:16rpx 0; border-bottom:2rpx solid #F6F6F8; }
.signin-roster-row:last-child { border-bottom:0; }
.srr-name { font-size:28rpx; color:#1f2329; font-weight:600; flex-shrink:0; }
.srr-role { font-size:24rpx; color:#9AA0A6; flex:1; min-width:0; }
.srr-state { font-size:24rpx; font-weight:600; padding:4rpx 16rpx; border-radius:12rpx; flex-shrink:0; }
.srr-state.on { color:#27AE60; background:#E8F7EE; }
.srr-state.off { color:#E74C3C; background:#FDECEA; }
.srr-state.wait { color:#999; background:#F2F2F4; }
/* 签到成功 → 录音 的一闪而过动画（时长收短，别停留） */
.signin-fx { position:fixed; inset:0; z-index:1200; background:rgba(255,255,255,0.94); display:flex; align-items:center; justify-content:center; animation:sfxFade .14s ease; }
.signin-fx-card { display:flex; flex-direction:column; align-items:center; gap:18rpx; animation:sfxRise .24s cubic-bezier(.2,.8,.3,1); }
.signin-fx-check { width:150rpx; height:150rpx; border-radius:50%; background:#3E9B34; color:#fff; font-size:92rpx; font-weight:700; display:flex; align-items:center; justify-content:center; animation:sfxPop .3s cubic-bezier(.2,1.3,.4,1); box-shadow:0 12rpx 36rpx rgba(62,155,52,0.35); }
.signin-fx-title { font-size:40rpx; font-weight:700; color:#1a1a1a; }
.signin-fx-sub { font-size:28rpx; color:#8A8F98; }
@keyframes sfxFade { from { opacity:0; } to { opacity:1; } }
@keyframes sfxRise { from { opacity:0; transform:translateY(20rpx); } to { opacity:1; transform:translateY(0); } }
@keyframes sfxPop { 0% { transform:scale(0.3); opacity:0; } 60% { transform:scale(1.12); } 100% { transform:scale(1); opacity:1; } }
/* 识别完成后的两键：继续上传录音(浅) / 生成会议纪要(深)——缩小、拉开间距 */
/* 上下堆叠、居中、宽度 60%：主(生成纪要)实心在上，次(继续上传)描边在下 */
.qk-two-btns { display:flex; flex-direction:column; align-items:center; gap:14rpx; margin-top:14rpx; padding:0; }
.qk-two-btns .lp-primary-btn.qk-two-btn { width:60%; flex:none; margin-top:0; padding:20rpx 0; display:flex; align-items:center; justify-content:center; }
.qk-two-btn .qra-main { font-size:28rpx; font-weight:700; white-space:nowrap; }
.qk-two-btns .lp-primary-btn.qk-two-btn:not(.ghost) .qra-main { font-size:30rpx; }
.qk-two-btn.ghost { background:#fff; color:var(--c-primary-dark); border:2rpx solid var(--c-primary-dark); }
.qk-two-btn.ghost:active { background:#FFF6E8; }
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
.qk-report-btn.primary { background:var(--c-primary-dark); color:#fff; }
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
.qk-cand-btn.primary { background:var(--c-primary-dark); color:#fff; }
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
/* 查看内容：左对齐到正文左缘，主色链接样式，点击打开整场转写弹窗 */
.qrl-view { align-self:flex-start; margin-top:6rpx; font-size:26rpx; color:var(--c-primary-dark, #E8890C); font-weight:600; }
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
.qk-modal-textarea { height:auto; min-height:150rpx; line-height:1.6; padding:16rpx 20rpx; resize:none; font-family:inherit; }
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
