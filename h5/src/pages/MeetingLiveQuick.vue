<template>
  <div class="live-page" style="overflow-y:auto;" v-if="detail">

    <PageNav title="会议进行" style="margin:-3.2vw -3.2vw 0;" />

    <!-- AI 工作中：选片转写(豆包 ASR)等待时显"识别转写"态；完成后出确认按钮 -->
    <AiWorkingOverlay :active="polling || extracting" phase="asr" />

    <div class="lp-stepper">
      <template v-for="(s, index) in steps" :key="s.key">
        <div class="lp-step" :class="currentStep > index + 1 ? 'done' : (currentStep === index + 1 ? 'active' : 'locked')">
          <span class="lp-step-num">{{ currentStep > index + 1 ? '✓' : index + 1 }}</span>
          <span class="lp-step-label">{{ s.label }}</span>
        </div>
        <div v-if="index < steps.length - 1" class="lp-step-line" :class="currentStep > index + 1 ? 'done' : ''"></div>
      </template>
    </div>

    <div class="lp-info-card">
      <span class="lp-info-title">{{ detail.title }}</span>
      <div class="lp-info-row top"><span class="lp-info-k">议题</span>
        <div class="lp-agenda">
          <template v-if="detail.record.topics.length">
            <div class="lp-agenda-item" v-for="(item, index) in detail.record.topics" :key="item.id">
              <span class="lp-agenda-idx">{{ index + 1 }}</span>
              <span class="lp-agenda-title">{{ item.title }}</span>
              <span class="lp-agenda-tag" :class="item.type === 'major' ? 'major' : (item.voteRequired ? 'vote' : '')">{{ item.type === 'major' ? '重大' : (item.voteRequired ? '表决' : '通报') }}</span>
            </div>
          </template>
          <span v-else class="lp-agenda-empty">暂无议题</span>
        </div>
      </div>
    </div>

    <div class="lp-card" v-if="currentStep === 1">
      <span class="lp-card-step">第 1 步，共 4 步</span>
      <span class="lp-card-title">入会签到</span>
      <span class="lp-card-desc">确认参会后再进行录音，会议有效性会以确认参会人数为基础核查。</span>
      <div class="qk-sign-card" :class="signedIn ? 'on' : ''">
        <span class="qk-sign-main">{{ signedIn ? '已确认参会' : '待确认参会' }}</span>
        <span class="qk-sign-sub">{{ selfAttendance ? selfAttendance.name + ' · ' + selfAttendance.role : '当前登录身份' }}</span>
      </div>
      <button class="lp-primary-btn" @click="confirmSignIn">{{ signedIn ? '进入录音' : '确认参会' }}</button>
    </div>

    <div class="lp-card" v-if="currentStep === 2">
      <span class="lp-card-step">第 2 步，共 4 步</span>
      <span class="lp-card-title">会议录音</span>
      <span class="lp-card-desc">录音后上传保存，多条录音都会保留。下一步可勾选多条一起转写，自动合并为本次会议记录。</span>

      <!-- 录音控件：任意已签到参会人可用 -->
      <div class="qk-recorder">
        <div class="qk-rec-dot" :class="recDotOn ? 'on' : ''"></div>
        <span class="qk-rec-time">{{ timeText }}</span>
        <span class="qk-rec-status">{{ recStatusText }}</span>
      </div>
      <!-- 暂停态：继续录音（并入当前段，仍是同一个文件）+ 重新录音，并排橙色窄按钮（主文案大字 + 小字说明） -->
      <div v-if="isPaused" class="qk-rec-actions">
        <button class="lp-primary-btn qk-rec-act" @click="resumeRecording" :disabled="uploading || polling || extracting">
          <span class="qra-main">继续录音</span>
          <span class="qra-sub">保存当前段</span>
        </button>
        <button class="lp-primary-btn qk-rec-act" @click="restartRecording" :disabled="uploading || polling || extracting">
          <span class="qra-main">重新录音</span>
          <span class="qra-sub">重头开始</span>
        </button>
      </div>
      <button v-else class="lp-primary-btn" @click="toggleRecord" :disabled="uploading || polling || extracting">
        {{ recBtnLabel }}
      </button>
      <button class="lp-ghost-btn" @click="finishRecord" :disabled="uploading || polling || extracting">
        结束录音并上传
      </button>
      <div class="qk-divider"><span class="qk-divider-text">或</span></div>
      <button class="lp-ghost-btn qk-rec-narrow" @click="chooseAudioFile" :disabled="uploading || polling || extracting">
        选择已有录音文件上传
      </button>
      <input ref="audioFileInput" type="file" accept="audio/*" style="display:none" @change="onAudioFileChange" />
      <div class="qk-note">上传后只保存、不自动转写；下一步可勾选多条一起转写并合并。</div>

      <!-- 会议资料：人人可传/可看 -->
      <div class="qk-grp-title">会议资料（{{ materials.length }}）<span class="qk-link" @click="uploadMaterial">+ 上传资料</span></div>
      <div v-if="materials.length === 0" class="lp-empty">暂无资料，任意参会人都可上传共享</div>
      <div v-else>
        <div class="qk-mat-item" v-for="(item, index) in materials" :key="item.name" @click="previewMaterial(index)">
          <span class="qk-mat-name">{{ item.name }}</span>
          <span class="qk-mat-meta">{{ item.uploaderName || '' }}{{ item.sizeText ? ' · ' + item.sizeText : '' }}</span>
          <span class="qk-mat-arrow">›</span>
        </div>
      </div>

      <!-- 实时添加议题（主任/副主任）-->
      <div v-if="isChair" class="qk-rr-transfer"><span class="qk-link" @click="openAddTopic">+ 实时添加议题</span></div>
    </div>

    <div class="lp-card" v-if="currentStep === 3">
      <span class="lp-card-step">第 3 步，共 4 步</span>
      <span class="lp-card-title">录音转写</span>
      <span class="lp-card-desc">勾选要转写的录音（可多条），转写后文字会自动合并为本次会议记录，供议题匹配。</span>

      <!-- 已上传录音列表：可多选，已转写的自动并入会议记录 -->
      <div v-if="recordings.length" class="qk-pick-list">
        <div class="qk-pick-item"
             :class="[item.id === _transcribingRecordingId ? 'transcribing' : '', isPicked(item.id) ? 'picked' : '']"
             v-for="item in recordings" :key="item.id"
             @click="onPickRowTap(item)">
          <span class="qk-pick-check" :class="isPicked(item.id) ? 'on' : ''">{{ isPicked(item.id) ? '✓' : '' }}</span>
          <div class="qk-pick-info">
            <span class="qk-pick-name">{{ item.uploaderName || '未知' }} · {{ item.fileName || '录音' }}</span>
            <span class="qk-pick-meta">{{ item.createdAt || '' }} · {{ item.id === _transcribingRecordingId ? '转写中…' : (item.asrStatus === 'done' ? '已转写 · 勾选可重新转写' : '待转写') }}</span>
          </div>
          <span v-if="item.asrStatus === 'done'" class="qk-pick-done">✓ 已转写</span>
        </div>
      </div>
      <div v-else class="lp-empty">暂无录音，请返回上一步录制或上传</div>

      <div v-if="recordings.length && !polling && !extracting" class="qk-pick-hint">勾选要转写的录音可多选；转写后自动合并为一份会议记录，已转写的会自动并入。</div>
      <button v-if="recordings.length" class="lp-primary-btn" @click="transcribeSelected" :disabled="polling || extracting || (!pickedIds.length && !hasDoneRecordings)">
        {{ pickedIds.length > 1 ? ('转写所选 ' + pickedIds.length + ' 条并合并') : '转写所选录音' }}
      </button>
      <div class="qk-back-rec" @click="backToRecord">＋ 返回录音页，再录或再传一段</div>

      <!-- 转写状态 -->
      <div class="qk-gen" v-if="polling || extracting">
        <div class="qk-spinner"></div>
        <span class="qk-gen-text">{{ processText }}</span>
      </div>

      <div class="qk-gen" v-if="generated">
        <div class="qk-gen-icon">✓</div>
        <span class="qk-gen-text">{{ finishText }}</span>
      </div>

      <div v-if="generated" class="qk-transcript-card">
        <div class="qk-transcript-head">
          <div>
            <span class="qk-transcript-title">语音转录文本</span>
            <span class="qk-transcript-meta">{{ transcript.length }} 段 · {{ transcriptCharCount }} 字</span>
          </div>
          <span class="qk-transcript-action" @click="openTranscript('short')">查看</span>
        </div>
        <span class="qk-transcript-preview">{{ transcriptPreview || '暂无可预览的转写文本' }}</span>
      </div>

      <div v-if="asrStatus === 'failed'" class="qk-note">转写失败，可重新选片或返回上一步录制新音频。</div>
      <div v-if="asrStatus === 'empty'" class="qk-note warn">未检测到有效语音：这条录音可能是静音、杂音或太短。请换一条录音，或返回上一步重新录音。</div>
      <button v-if="asrStatus === 'failed' || asrStatus === 'empty'" class="lp-ghost-btn" @click="retryToStep2">返回录音</button>

      <button class="lp-primary-btn" @click="goConfirm" :disabled="!generated">下一步：匹配议题并生成纪要依据</button>
    </div>

    <div class="lp-card" v-if="currentStep === 4">
      <span class="lp-card-step">最后一步</span>
      <span class="lp-card-title">整理会议纪要</span>
      <span class="lp-card-desc">AI 已根据录音识别各议题的表决结果。请核对下面标注「待核对」的项目，然后用 AI 生成会议纪要。</span>

      <div class="qk-transcript-card compact">
        <div class="qk-transcript-head">
          <div>
            <span class="qk-transcript-title">转录文本底稿</span>
            <span class="qk-transcript-meta">{{ transcript.length }} 段 · 用于议题匹配</span>
          </div>
          <span class="qk-transcript-action" @click="openTranscript('short')">查看文本</span>
        </div>
        <span class="qk-transcript-preview">{{ transcriptPreview || '暂无可预览的转写文本' }}</span>
      </div>

      <div class="qk-grp-title">议题与表决结果（AI 识别）</div>
      <div v-if="presetTopics.length === 0" class="lp-empty">未读取到预设议题，请返回会议详情检查创建信息</div>
      <div class="qk-rev" :class="t.needsReview ? 'warn' : ''" v-for="(t, ti) in presetTopics" :key="t.id">
        <div class="qk-rev-head">
          <span class="qk-rev-no">{{ ti + 1 }}</span>
          <div class="qk-rev-main">
            <span class="qk-rev-title">{{ t.title }}</span>
            <div class="qk-rev-meta">
              <span class="qk-tag-type">{{ t.typeLabel }}</span>
              <template v-if="t.voteRequired">
                <span class="qk-rev-result" :class="t.result">{{ t.resultLabel }}</span>
                <span class="qk-rev-counts">同意{{ t.voteFor }}·反对{{ t.voteAgainst }}·弃权{{ t.voteAbstain }}</span>
              </template>
              <span v-else class="qk-rev-result recorded">已记录</span>
            </div>
          </div>
          <span v-if="isChair && t.voteRequired" class="qk-rev-btn" @click.stop="toggleReview(ti)">{{ t._reviewOpen ? '收起' : '核对' }}</span>
        </div>
        <span v-if="t.needsReview && !t._reviewOpen" class="qk-rev-tip">⚠ AI 未能确定结果，请点「核对」确认票数</span>

        <div v-if="t._reviewOpen" class="qk-rev-box">
          <span v-if="t.aiVoteLabel" class="qk-vote-ai">AI 识别来源：{{ t.aiVoteLabel }}</span>
          <span class="qk-vote-rule">实到 {{ voteTotal }} 人，过半需 {{ voteNeed }} 票</span>
          <div class="qk-vote-counts">
            <div class="qk-vote-counter">
              <span>同意</span>
              <div><span @click.stop="changeVoteCount('preset', ti, 'voteFor', -1)">-</span><span>{{ t.voteFor }}</span><span @click.stop="changeVoteCount('preset', ti, 'voteFor', 1)">+</span></div>
            </div>
            <div class="qk-vote-counter">
              <span>反对</span>
              <div><span @click.stop="changeVoteCount('preset', ti, 'voteAgainst', -1)">-</span><span>{{ t.voteAgainst }}</span><span @click.stop="changeVoteCount('preset', ti, 'voteAgainst', 1)">+</span></div>
            </div>
            <div class="qk-vote-counter">
              <span>弃权</span>
              <div><span @click.stop="changeVoteCount('preset', ti, 'voteAbstain', -1)">-</span><span>{{ t.voteAbstain }}</span><span @click.stop="changeVoteCount('preset', ti, 'voteAbstain', 1)">+</span></div>
            </div>
          </div>
          <div class="qk-result-pick">
            <span :class="t.result === 'passed' ? 'on' : ''" @click.stop="pickTopicResult('preset', ti, 'passed')">通过</span>
            <span :class="t.result === 'rejected' ? 'on' : ''" @click.stop="pickTopicResult('preset', ti, 'rejected')">未通过</span>
            <span :class="t.result === 'unclear' ? 'on' : ''" @click.stop="pickTopicResult('preset', ti, 'unclear')">待定</span>
          </div>
        </div>
      </div>

      <!-- AI 额外发现（议程外的疑似新议题，默认折叠不打扰） -->
      <div v-if="aiTopics.length" class="qk-extra">
        <div class="qk-extra-head" @click="toggleExtra">
          <span>AI 额外发现 · {{ aiTopics.length }} 项可能的新议题</span>
          <span class="qk-extra-toggle">{{ extraOpen ? '收起 ▲' : '展开 ▼' }}</span>
        </div>
        <div v-if="extraOpen" class="qk-extra-list">
          <div class="qk-extra-item" v-for="(c, ci) in aiTopics" :key="c.id">
            <span class="qk-extra-title">{{ c.title }}</span>
            <div class="qk-extra-acts" v-if="isChair">
              <span class="qk-cand-btn primary" @click.stop="adoptCandidate(ci)">采纳</span>
              <span class="qk-cand-btn" @click.stop="ignoreCandidate(ci)">忽略</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 居委会签字（重大事项时，合规所需，保留） -->
      <div v-if="detail.record.hasMajorIssue" class="qk-juwei">
        <span class="qk-juwei-name">居委会委员：{{ detail.record.juweiName }}</span>
        <span class="qk-juwei-chip" :class="detail.record.juweiSigned ? 'on' : ''" @click="toggleJuwei">{{ detail.record.juweiSigned ? '✓ 已签字' : '签字' }}</span>
      </div>

      <!-- 生成纪要 / 结束会议 -->
      <template v-if="isChair">
        <button v-if="!minutesGenerated" class="lp-primary-btn" @click="generateMinutes" :disabled="generatingMinutes">{{ generatingMinutes ? 'AI 生成中…请稍候' : '用 AI 生成会议纪要（推荐）' }}</button>
        <div v-if="!minutesGenerated" class="qk-minor-links">
          <span class="qk-minor-link" @click="writeMinutes">手写会议纪要</span>
          <span class="qk-minor-link" @click="endThenSupplement">先结束会议，纪要后续补充</span>
        </div>

        <template v-else>
          <div class="qk-gen"><div class="qk-gen-icon">✓</div><span class="qk-gen-text">AI 会议纪要已生成，可在纪要页核对 / 编辑</span></div>
          <div class="qk-sub-actions">
            <span class="qk-sub-link" @click="viewMinutes">在纪要页查看 / 编辑</span>
            <span class="qk-sub-sep">·</span>
            <span class="qk-sub-link" @click="generateMinutes">重新生成</span>
          </div>
          <button class="lp-primary-btn" @click="confirmEndMeeting" :disabled="ending">{{ ending ? '结束中…' : '确认无误，结束会议' }}</button>
        </template>
      </template>
      <div v-else class="qk-wait-tip">表决核对与结束会议由主任/副主任拍板，请等待主持人操作。</div>
    </div>

    <div class="lp-nav">
      <button class="lp-nav-btn ghost" @click="exitLive">退出</button>
      <button v-if="isChair" class="lp-nav-btn ghost" @click="exportAttendance">导出签到名单</button>
    </div>

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
        <input class="qk-modal-input" placeholder="议题内容" v-model="newTopicForm.title" />
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
        <span class="qk-modal-note">重大事项不可现场新增，请列入会前通知或下次会议。</span>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onActivated, onUnmounted, onDeactivated } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast, showModal, showActionSheet } from '@/utils/ui'
import { navigateTo, redirectTo, navigateBack } from '@/utils/navigate'
import { getStorage, setStorage, removeStorage } from '@/utils/storage'
import { useRecorder } from '@/composables/useRecorder'
import PageNav from '@/components/PageNav.vue'
import AiWorkingOverlay from '@/components/AiWorkingOverlay.vue'

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
const steps = ref([
  { key: 'sign', label: '人员签到' },
  { key: 'record', label: '会议录音' },
  { key: 'pick', label: '录音转写' },
  { key: 'confirm', label: '会议纪要' }
])
const currentStep = ref(1)
const signedIn = ref(false)
const selfAttendance = ref(null)

// 录音计时显示直接复用 useRecorder（recording/paused/hasRecording 在脚本里用 rec.* 读取）
const timeText = rec.timeText
// useRecorder 会话模型 → 原四态展示。注意：暂停时 recording 仍为 true，需先判 paused。
// 录音中(未停) = recording.value；活动录制(出红点) = recording && !paused。
const recActive = computed(() => rec.recording.value && !rec.paused.value)
const recDotOn = computed(() => recActive.value)
// 状态文案：活动录制 → 录音中；暂停 → 已暂停；已出可上传录音 → 待上传；否则 尚未开始
const recStatusText = computed(() => {
  if (recActive.value) return '录音中'
  if (rec.recording.value && rec.paused.value) return '已暂停'
  if (rec.hasRecording.value) return '录音已保存，待上传'
  return '尚未开始'
})
// 是否已有“上传保存”的录音（决定主按钮是“新增录音”还是“开始/重新录音”）
const hasSavedRecordings = computed(() => (recordings.value || []).length > 0)
// 按钮文案：活动录制 → 暂停；已上传过录音 → 新增录音；本地有未上传录音 → 重新录音；否则 开始录音（暂停态另用并排按钮）
const recBtnLabel = computed(() => {
  if (recActive.value) return '暂停录音'
  if (hasSavedRecordings.value) return '新增录音'   // 已存过录音，本次为追加一段（旧的已存服务器，不会覆盖）
  if (rec.hasRecording.value) return '重新录音'
  return '开始录音'
})
// 暂停态：显示"继续录音/重新录音"并排按钮（继续=恢复同一会话，自然合并成一个文件）
const isPaused = computed(() => rec.recording.value && rec.paused.value)

const uploading = ref(false)
const polling = ref(false)
const extracting = ref(false)
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
const materials = ref([])
const signedInList = ref([])
const voteTotal = ref(0)
const voteNeed = ref(0)
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
})

// onShow → onMounted 首跑 + onActivated（保持热切回页刷新；但避免与 onMounted 重复首跑）
onActivated(() => {
  if (!_booted) return
})

// onUnload → onUnmounted
onUnmounted(() => {
  persistQuickState()
  clearPoll()
  if (typeof window !== 'undefined') window.removeEventListener('beforeunload', _beforeUnloadGuard)
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
  let step = Number(saved.currentStep) || (saved.generated ? 4 : 2)
  if (!signedInArg) step = 1
  else if (step < 2) step = 2
  if (step > 4) step = 4
  if (!saved.generated && step > 3) step = 3

  currentStep.value = step
  generated.value = !!saved.generated
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
    currentStep.value = 3
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
    voteTotal.value = total
    voteNeed.value = Math.floor(total / 2) + 1
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

async function tryRestoreGeneratedFromServer() {
  if (!meetingId.value || !signedIn.value || generated.value) return
  if (typeof api.committeeQuickTranscript !== 'function' || typeof api.committeeQuickExtract !== 'function') return
  try {
    const transcriptRaw = await api.committeeQuickTranscript(meetingId.value)
    const tr = mapTranscript(transcriptRaw)
    if (!tr.length) return
    const ext = await api.committeeQuickExtract(meetingId.value)
    const mapped = mapExtraction(ext)
    currentStep.value = 4
    extraction.value = ext
    presetTopics.value = mapped.presetTopics
    aiTopics.value = mapped.aiTopics
    transcript.value = tr
    transcriptVisible.value = false
    uploading.value = false
    polling.value = false
    extracting.value = false
    generated.value = true
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
  if (signedIn.value) {
    currentStep.value = 2
    persistQuickState()
    return
  }
  const res = await showModal({
    title: '确认参加会议',
    content: '请确认本人已进入本次业委会会议。确认后将进入录音转写流程。',
    confirmText: '确认参会',
    cancelText: '再看看'
  })
  if (!res.confirm) return
  try {
    await api.committeeSelfToggle(meetingId.value, 'signedIn')
    toast({ title: '已确认参会', icon: 'success' })
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
    toast({ title: '请先确认参会', icon: 'none' })
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
    toast({ title: '请先确认参会', icon: 'none' })
    return
  }
  if (!rec.recording.value && !rec.hasRecording.value) {
    toast({ title: '请先开始录音', icon: 'none' })
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
    await uploadRecording(r.blob, r.ext)
    return
  }
  // 会话已停止但已有上次产出（对齐原 tempFilePath 分支）→ 直接复用已生成的 blob 上传
  const blob = rec.getBlob && rec.getBlob()
  if (blob && blob.size > 0) {
    await uploadRecording(blob, extFromBlob(blob))
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
    toast({ title: '请先确认参会', icon: 'none' })
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
  // File 自带 name/ext，直接上传
  await uploadRecordingFile(file)
}

// 录音/选片上传：把 Blob 包成带正确文件名的 File，后端据扩展名识别格式并转 16k 单声道 mp3
async function uploadRecording(blob, ext) {
  const file = new File([blob], 'recording.' + ext, { type: blob.type })
  await uploadRecordingFile(file)
}

async function uploadRecordingFile(file) {
  clearPoll()
  _asrDoneHandled = false
  currentStep.value = 3
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
    // 上传只存，不自动转写 — 返回录音记录信息
    await api.committeeUploadRecording(meetingId.value, file)
    toast({ title: '录音已上传', icon: 'success' })
    uploading.value = false
    currentStep.value = 3 // 进入"选片转写"步骤
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

// 返回录音页再录/再传一段（不清空已转写结果，回来后可继续合并）
function backToRecord() {
  clearPoll()
  currentStep.value = 2
  persistQuickState()
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

function goConfirm() {
  if (!generated.value) return
  currentStep.value = 4
  persistQuickState()
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
    toast({ title: '请先确认参会', icon: 'none' })
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
function closeAddTopic() { addTopicVisible.value = false }
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

/* 步骤指示器 */
.lp-stepper { display:flex; align-items:center; background:#fff; border-radius:24rpx; padding:28rpx 20rpx; margin-bottom:24rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); }
.lp-step { display:flex; flex-direction:column; align-items:center; gap:10rpx; flex-shrink:0; }
.lp-step-num { width:60rpx; height:60rpx; border-radius:50%; display:flex; align-items:center; justify-content:center; font-size:28rpx; font-weight:700; background:#E5E8EC; color:#666; }
.lp-step.active .lp-step-num { background:#FFA800; color:#fff; }
.lp-step.done .lp-step-num { background:#27AE60; color:#fff; }
.lp-step-label { font-size: 28rpx; color:#666; }
.lp-step.active .lp-step-label { color:#D88900; font-weight:700; }
.lp-step.done .lp-step-label { color:#27AE60; }
.lp-step-line { flex:1; height:4rpx; background:#E5E8EC; margin:0 8rpx; margin-bottom:36rpx; }
.lp-step-line.done { background:#27AE60; }

/* 会议信息卡 */
.lp-info-card { background:#fff; border-radius:24rpx; padding:28rpx 30rpx; margin-bottom:24rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); }
.lp-info-title { display:block; font-size:38rpx; font-weight:700; color:#1F2024; line-height:1.4; margin-bottom:18rpx; }
.lp-info-row { display:flex; align-items:flex-start; gap:18rpx; font-size:30rpx; color:#444; margin-bottom:14rpx; }
.lp-info-row.top { align-items:flex-start; }
.lp-info-k { color:#666; flex-shrink:0; width:72rpx; font-size:30rpx; }
.lp-info-v { flex:1; min-width:0; word-break:break-all; }
.lp-agenda { flex:1; min-width:0; }
.lp-agenda-item { display:flex; align-items:center; gap:14rpx; padding:12rpx 0; border-bottom:2rpx solid #F2F2F4; }
.lp-agenda-item:last-child { border-bottom:0; }
.lp-agenda-idx { width:44rpx; height:44rpx; flex-shrink:0; border-radius:50%; background:#F2F2F4; color:#666; font-size: 30rpx; text-align:center; line-height:44rpx; }
.lp-agenda-title { flex:1; min-width:0; color:#1F2024; word-break:break-all; font-size:34rpx; }
.lp-agenda-tag { flex-shrink:0; font-size: 28rpx; padding:4rpx 14rpx; border-radius:12rpx; background:#F2F2F4; color:#666; }
.lp-agenda-tag.vote { background:#FFF3E0; color:#E67E22; }
.lp-agenda-tag.major { background:#FDECEA; color:#E74C3C; }
.lp-agenda-empty { color:#666; font-size:28rpx; }

/* 步骤卡片 */
.lp-card { background:#fff; border-radius:24rpx; padding:32rpx 30rpx; margin-bottom:24rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); }
.lp-card-step { display:block; font-size:28rpx; color:#D88900; font-weight:700; margin-bottom:12rpx; }
.lp-card-title { display:block; font-size:40rpx; font-weight:700; color:#1f2329; line-height:1.4; }
.lp-card-desc { display:block; font-size:28rpx; color:#666; line-height:1.6; margin-top:10rpx; margin-bottom:24rpx; }

.lp-primary-btn { display:block; width:100%; box-sizing:border-box; background:#FFA800; color:#fff; border:0; border-radius:44rpx; font-size:34rpx; font-weight:700; padding:26rpx 0; margin-top:12rpx; }
.lp-primary-btn[disabled] { background:#e3cfa6; color:#fff; }
.lp-ghost-btn { display:block; width:100%; box-sizing:border-box; background:#fff; color:#FFA800; border:2rpx solid #FFA800; border-radius:44rpx; font-size:30rpx; padding:22rpx 0; margin-top:16rpx; margin-bottom:8rpx; }

/* 第4步底部：次要操作弱化为小链接 */
.qk-sub-actions { display:flex; align-items:center; justify-content:center; gap:18rpx; margin-top:18rpx; }
.qk-sub-link { font-size:28rpx; color:#666; padding:8rpx 4rpx; }
.qk-sub-sep { color:#666; }
/* 结束步：不显眼的次要入口（手写纪要 / 先结束后补） */
.qk-minor-links { display:flex; flex-direction:column; align-items:center; gap:8rpx; margin-top:20rpx; }
.qk-minor-link { font-size:28rpx; color:#777; padding:12rpx 16rpx; }

/* 录音控件 */
.qk-recorder { display:flex; flex-direction:column; align-items:center; gap:14rpx; padding:36rpx 0 28rpx; }
.qk-rec-dot { width:120rpx; height:120rpx; border-radius:50%; background:#E5E8EC; }
.qk-rec-dot.on { background:#E74C3C; animation:qkpulse 1.2s ease-in-out infinite; }
@keyframes qkpulse { 0%,100% { opacity:1; transform:scale(1); } 50% { opacity:.55; transform:scale(.92); } }
.qk-rec-time { font-size:64rpx; font-weight:700; color:#1f2329; letter-spacing:4rpx; }
.qk-rec-status { font-size:28rpx; color:#666; }

.qk-note { font-size:28rpx; color:#666; line-height:1.6; margin-top:20rpx; background:#FAFBFC; border-radius:14rpx; padding:18rpx 20rpx; }
.qk-note.warn { color:#C77700; background:#FFF8EC; }

/* 入会签到 */
.qk-sign-card { border:2rpx solid #EEF1F4; border-radius:18rpx; padding:28rpx; margin:24rpx 0 14rpx; background:#FAFBFC; }
.qk-sign-card.on { border-color:#BFE8CC; background:#F1FBF5; }
.qk-sign-main { display:block; font-size:36rpx; font-weight:700; color:#1f2329; }
.qk-sign-card.on .qk-sign-main { color:#1E8E4A; }
.qk-sign-sub { display:block; font-size:28rpx; color:#666; margin-top:10rpx; }

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
.lp-nav { display:flex; gap:18rpx; margin-top:8rpx; }
.lp-nav-btn { flex:1; border-radius:44rpx; font-size:30rpx; padding:22rpx 0; border:0; }
.lp-nav-btn.ghost { background:#fff; color:#555; border:2rpx solid #dfe3e8; }

/* 录音负责人 */
.qk-recorder-role { background:#FAFBFC; border-radius:18rpx; padding:22rpx 26rpx; margin:12rpx 0 18rpx; }
.qk-rr-status { display:block; font-size:28rpx; color:#6B6E76; margin-bottom:14rpx; }
.qk-rr-status.ok { color:#1D9E75; font-weight:600; }
.qk-rr-transfer { text-align:center; margin:18rpx 0 4rpx; }
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
.qk-modal { width:84%; max-height:84vh; overflow-y:auto; box-sizing:border-box; background:#fff; border-radius:24rpx; padding:32rpx; }
.qk-modal-title { display:block; font-size:34rpx; font-weight:700; color:#1F2024; margin-bottom:20rpx; }
.qk-modal-input { box-sizing:border-box; width:100%; height:88rpx; line-height:88rpx; background:#F6F6F8; border-radius:14rpx; padding:0 20rpx; font-size:30rpx; margin-bottom:18rpx; border:0; }
.qk-modal-types { display:flex; gap:14rpx; margin-bottom:18rpx; }
.qk-type { font-size:28rpx; padding:12rpx 26rpx; border-radius:24rpx; background:#F6F6F8; color:#6B6E76; border:2rpx solid #ECECEF; }
.qk-type.on { background:#FFF3E0; color:#E67E22; border-color:#F4D08A; }
.qk-modal-label { display:block; font-size:26rpx; color:#777; font-weight:600; margin-bottom:12rpx; }
.qk-opt-row { display:flex; align-items:center; gap:14rpx; margin-bottom:12rpx; }
.qk-opt-num { font-size:28rpx; color:#666; width:40rpx; text-align:right; flex-shrink:0; }
.qk-opt-input { flex:1; min-width:0; height:76rpx; line-height:76rpx; margin-bottom:0; }
.qk-opt-del { font-size:38rpx; color:#999; padding:0 8rpx; flex-shrink:0; }
.qk-modal-area { width:100%; box-sizing:border-box; background:#F6F6F8; border-radius:14rpx; padding:18rpx 20rpx; font-size:28rpx; height:160rpx; margin-bottom:18rpx; border:0; }
.qk-modal-btns { display:flex; gap:18rpx; }
.qk-modal-btns .lp-ghost-btn, .qk-modal-btns .lp-primary-btn { flex:1; margin:0; }
.qk-modal-note { display:block; font-size: 28rpx; color:#666; margin-top:18rpx; line-height:1.5; }

/* 第3步：选片列表 */
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
