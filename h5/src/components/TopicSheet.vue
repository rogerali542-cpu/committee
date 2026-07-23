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

      <!-- 表决页顶部意见摘要：仅在已有意见时置顶（没意见就不占顶部，免得空占位把投票挤到底） -->
      <div v-if="opinionSummaryMode && hasOpinions" class="ts-ops summary ts-top-summary">
        <div class="ts-ops-title-row">
          <div class="ts-ops-head">意见汇总<span v-if="opinions.length">（{{ opinions.length }}）</span></div>
          <button v-if="opinions.length" class="ts-ops-expand" @click.stop="opinionListOpen = true">查看</button>
        </div>
        <div v-if="loading" class="ts-empty">加载中…</div>
        <div v-else-if="!opinions.length" class="ts-empty">还没有人发表意见</div>
        <div v-else class="ts-op" v-for="op in visibleOpinions" :key="op.id">
          <div class="ts-op-l1">
            <span class="ts-op-name">{{ op.name }}</span>
            <span v-if="opVote(op)" class="ts-op-vote" :class="opVote(op).cls">{{ opVote(op).text }}</span>
            <span class="ts-op-time">{{ fmtTime(op.createdAt) }}</span>
          </div>
          <div class="ts-op-l2">
            <span class="ts-op-sum">{{ op.content }}</span>
          </div>
          <!-- 删除移到卡片右下角 -->
          <div v-if="op.isSelf && op.canDelete" class="ts-op-foot">
            <button class="ts-op-del-inline" @click.stop="removeOpinion(op)">删除</button>
          </div>
        </div>
        <div v-if="opinions.length > visibleOpinions.length" class="ts-op-more">还有 {{ opinions.length - visibleOpinions.length }} 条，点击“补充意见”查看</div>
        <!-- 补充意见：意见卡片正下方的显眼按钮（0722 用户定）。会后只读（interactive=false）隐藏写入口 -->
        <button v-if="interactive" class="ts-op-entry in-summary" @click.stop="opinionOpen = true">补充意见</button>
      </div>

      <!-- 可滚动区：表决 + 意见汇总（输入框固定在底部，这里滚动看更多意见） -->
      <div class="ts-scroll">
      <!-- 表决区（仅表决类议题）：分「我的表决」和「全体表决情况」两块，避免个人/全体状态挤在一起 -->
      <div v-if="topic.voteRequired" class="ts-vote" :class="{ 'opinion-mode': opinionOpen, 'has-ops': hasOpinions }">
        <!-- ① 我的表决：投票按钮（按钮含义自明，不加标签）+ 我的状态 -->
        <div v-show="!opinionOpen" class="ts-vote-mine">
          <!-- 表决进行中(会议 ongoing 且未结束)选项常驻：已投项高亮，点其他选项后轻确认改票；
               会议结束后(voteRevealed)自动落到下方"您已投/未投"，不再显示投票按钮（方案A） -->
          <template v-if="!voteRevealed">
            <!-- 已投后选项收起（voteCollapsed），状态行「已投：X」+「改票/撤回」接管；点改票再展开。
                 代投面板展开时整块隐藏（0722 用户定：代投时不显示自己的投票） -->
            <template v-if="!voteCollapsed && !proxyOpen">
              <template v-if="(topic.decisionType || 'simple') !== 'multi_choice'">
                <div class="ts-vote-btns">
                  <button class="ts-vote-btn agree" :class="{ on: displayVote === 'for_vote' }" :disabled="voteSubmitting" @click="pickVote('for_vote')">
                    <span class="ts-radio"></span><span>同意</span>
                  </button>
                  <button class="ts-vote-btn against" :class="{ on: displayVote === 'against' }" :disabled="voteSubmitting" @click="pickVote('against')">
                    <span class="ts-radio"></span><span>不同意</span>
                  </button>
                  <button class="ts-vote-btn abstain" :class="{ on: displayVote === 'abstain' }" :disabled="voteSubmitting" @click="pickVote('abstain')">
                    <span class="ts-radio"></span><span>弃权</span>
                  </button>
                </div>
              </template>
              <template v-else>
                <div class="ts-opt" v-for="o in (topic.options || [])" :key="o.id"
                     :class="{ on: displayVote != null && String(displayVote) === String(o.id) }"
                     @click="pickVote(o.id, o)">
                  <span class="ts-opt-label">{{ o.label }}</span>
                </div>
              </template>
            </template>
            <button v-if="pendingVote != null && !proxyOpen" class="ts-vote-submit" :disabled="voteSubmitting" @click="submitVote">
              {{ voteSubmitting ? '提交中...' : '确认提交' }}
            </button>
            <!-- 已投收起态的状态并入下方票数卡；这行只在 选中未提交/改票展开 时出现 -->
            <div v-if="voteFeedbackText && !voteCollapsed && !proxyOpen" class="ts-vote-status-row">
              <div class="ts-vote-feedback" :class="[voteFeedbackClass, { pending: voteFeedbackPending }]">
                <span v-if="!voteFeedbackPending" class="ts-vote-feedback-mark">✓</span>
                <span>{{ voteFeedbackText }}</span>
              </div>
              <button v-if="showRetract" class="ts-vote-retract" :disabled="voteSubmitting" @click="retractVote">撤回</button>
            </div>
            <!-- 表决进行中：实时票数明细，只报数不下"通过/未通过"结论(没结束不算数)。0722 用户定：不怕从众 -->
            <div v-if="showLiveTally" class="ts-vote-all compact">
              <div class="ts-result-line live">
                <span class="ts-result-badge">进行中</span>
                <span class="ts-result-nums"><span class="rn-part progress">已投 {{ tallyProgress.voted }}/{{ tallyProgress.total }}</span><template v-for="(p, i) in breakdownParts" :key="i"><span v-if="i > 0" class="rn-sep"> · </span><span class="rn-part" :class="p.cls">{{ p.text }}</span></template><span v-if="notVoted > 0" class="rn-part faint">（未投 {{ notVoted }}）</span></span>
              </div>
              <!-- 我的投票并入卡内一行（0722 用户定）：状态 + 小号改票/撤回 -->
              <div v-if="voteCollapsed && !proxyOpen" class="ts-my-line">
                <span class="ts-my-vote">✓ 已投：{{ myVoteLabel || localVoteLabel }}</span>
                <button class="ts-mini-act" :disabled="voteSubmitting" @click="changeVoteOpen = true">改票</button>
                <button class="ts-mini-act" :disabled="voteSubmitting" @click="retractVote">撤回</button>
              </div>
            </div>
            <!-- 代委员投票（仅主持人、会后整理阶段）：现场会议结束后，主任为忘投/不会用手机的委员补录并留凭证审计。
                 现场会议进行中不出现——委员应本人投票（0723 用户定） -->
            <div v-if="isChair && interactive && allowProxy" class="ts-proxy">
              <button v-if="!proxyOpen" class="ts-proxy-entry" @click="openProxy">代委员投票</button>
              <div v-else class="ts-proxy-panel" @click="proxyMenuOpen = false">
                <div class="ts-proxy-title">代投对象（已签到、未投票）<span class="ts-proxy-close" @click="proxyOpen = false">×</span></div>
                <div v-if="proxyLoading" class="ts-empty">加载中…</div>
                <div v-else-if="!proxyTargets.length" class="ts-empty">没有已签到且未投票的委员</div>
                <template v-else>
                  <!-- 委员选择：与签到状态同款 ▾ 悬浮下拉（0722 用户定），单选、选完自动收起 -->
                  <div class="ts-proxy-pick" @click.stop="proxyMenuOpen = !proxyMenuOpen">
                    <span class="ts-proxy-pick-label" :class="{ ph: !proxySelected.size }">{{ proxySelectedNames || '选择委员' }}</span>
                    <span class="ts-proxy-pick-arrow" :class="{ on: proxyMenuOpen }">▾</span>
                    <div v-if="proxyMenuOpen" class="ts-proxy-menu">
                      <div v-for="p in proxyTargets" :key="p.memberId" class="ts-proxy-menu-item"
                           :class="{ cur: proxySelected.has(p.memberId) }"
                           @click.stop="toggleProxyMember(p.memberId)">{{ p.name }}</div>
                    </div>
                  </div>
                  <div class="ts-proxy-choices">
                    <template v-if="(topic.decisionType || 'simple') !== 'multi_choice'">
                      <span v-for="c in ['for_vote','against','abstain']" :key="c" class="ts-proxy-choice"
                            :class="{ on: proxyChoice === c }" @click="proxyChoice = c">{{ { for_vote: '同意', against: '不同意', abstain: '弃权' }[c] }}</span>
                    </template>
                    <template v-else>
                      <span v-for="o in (topic.options || [])" :key="o.id" class="ts-proxy-choice"
                            :class="{ on: String(proxyOptId) === String(o.id) }" @click="proxyOptId = o.id">{{ o.label }}</span>
                    </template>
                  </div>
                  <div class="ts-proxy-proof">
                    <button class="ts-proxy-proof-btn" :class="{ ok: proxyProofUrl }" :disabled="proxyUploading" @click="pickProxyProof">
                      {{ proxyUploading ? '上传中…' : (proxyProofUrl ? '✓ 已传凭证' : '凭证照片（选填）') }}
                    </button>
                    <span class="ts-proxy-proof-tip">纸质表决单或聊天记录截图</span>
                  </div>
                  <button class="ts-proxy-submit" :disabled="!canSubmitProxy || proxySubmitting" @click="submitProxy">
                    {{ proxySubmitting ? '提交中…' : '确认代投' }}
                  </button>
                </template>
              </div>
            </div>
          </template>
          <!-- 已表决：结果卡整块替换选择区（研究P1：占屏结果态明确"做完了"，不靠按钮变淡） -->
          <div v-else-if="topic.myVote" class="ts-vote-done"><span class="ts-vote-done-mark">✓</span>您已投「{{ myVoteLabel }}」</div>
          <!-- 表决已结束但本人未投：明确告知（此后不再显示投票按钮） -->
          <div v-else class="ts-vote-missed">本议题表决已结束，您未参与投票</div>
        </div>

        <!-- ② 揭晓（主任结束表决/会议结束后）：紧凑一行——结论徽标 + 票数统计
             （0722 用户定：看结果=自己的票+票数统计即可，不做大结论卡/进度条占屏） -->
        <div v-if="showVoteSummary && voteRevealed" class="ts-vote-all compact">
          <div class="ts-result-line" :class="topic.passed ? 'pass' : 'fail'">
            <span class="ts-result-badge">{{ topic.passed ? '✓ 通过' : '✕ 未通过' }}</span>
            <span class="ts-result-nums"><span class="rn-part progress">已投 {{ tallyProgress.voted }}/{{ tallyProgress.total }}</span><template v-for="(p, i) in breakdownParts" :key="i"><span v-if="i > 0" class="rn-sep"> · </span><span class="rn-part" :class="p.cls">{{ p.text }}</span></template><span v-if="notVoted > 0" class="rn-part faint">（未投 {{ notVoted }}）</span></span>
          </div>
        </div>
        <!-- 有意见时入口已在意见汇总标题行；这里只兜底"还没人发言"的场景 -->
        <button v-if="canDiscuss && interactive && !opinionOpen && !hasOpinions" class="ts-op-entry" @click="opinionOpen = true">
          补充意见
        </button>
      </div>

      <!-- 通报区（仅通报类议题）：正文 + 已读进度 + 本人「我已读」 + (主任)标记全体已通报 -->
      <div v-if="topic.type === 'notice'" class="ts-notice">
        <div class="ts-notice-label">通知内容</div>
        <div class="ts-notice-body">{{ topic.content || '（暂无通知正文）' }}</div>
        <div class="ts-notice-foot">
          <!-- 状态显示"已读 X/Y 人"；Y 为会议参会名单人数，凑齐全体自动「已通报」 -->
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
      <div v-if="showOpinionSection && !opinionSummaryMode" class="ts-ops" :class="{ summary: opinionSummaryMode }">
        <div class="ts-ops-head">{{ opinionSummaryMode ? '意见汇总' : (topic.voteRequired ? '补充意见' : '意见汇总') }}<span v-if="opinions.length">（{{ opinions.length }}）</span></div>
        <div v-if="loading" class="ts-empty">加载中…</div>
        <div v-else-if="!opinions.length" class="ts-empty">还没有人发表意见</div>
        <div v-else class="ts-op" :class="{ open: !opinionSummaryMode && openOpIds.has(op.id) }" v-for="op in visibleOpinions" :key="op.id" @click="toggleOp(op)">
          <div class="ts-op-l1">
            <span class="ts-op-name">{{ op.name }}</span>
            <span v-if="opVote(op)" class="ts-op-vote" :class="opVote(op).cls">{{ opVote(op).text }}</span>
            <span v-if="op.claimable" class="ts-op-claim-tag" :class="{ on: claimShowId === op.id }"
                  @click.stop="toggleClaimRow(op)">未认领</span>
            <span class="ts-op-time">{{ fmtTime(op.createdAt) }}</span>
            <button v-if="op.isSelf && op.canDelete" class="ts-op-del-inline" @click.stop="removeOpinion(op)">删除</button>
          </div>
          <div class="ts-op-l2">
            <span class="ts-op-sum">{{ op.content }}</span>
            <span v-if="!opinionSummaryMode" class="ts-op-toggle">{{ openOpIds.has(op.id) ? '收起' : '查看' }}</span>
          </div>
          <!-- 展开后才显示删除，避免和"查看"挤在一排 -->
          <div v-if="!opinionSummaryMode && openOpIds.has(op.id) && op.canDelete && !op.isSelf" class="ts-op-actions" @click.stop>
            <button class="ts-op-del" @click="removeOpinion(op)">删除</button>
          </div>
          <!-- AI 从现场发言提炼、还没归属到人：认领按钮默认藏着，点"未认领"标签才展开 -->
          <div v-if="!opinionSummaryMode && op.claimable && claimShowId === op.id" class="ts-op-claimrow" @click.stop>
            <button class="ts-op-claim-btn" @click="claimOpinion(op)">🙋 是我说的</button>
          </div>
        </div>
        <div v-if="opinionSummaryMode && opinions.length > visibleOpinions.length" class="ts-op-more">还有 {{ opinions.length - visibleOpinions.length }} 条，点击“补充意见”查看</div>
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
          <!-- 完成：识别文字可直接修改，再确认使用 -->
          <template v-else>
            <textarea v-model="voiceResult" class="ts-voice-result ts-voice-result-edit" rows="4" placeholder="识别文字可在这里修改"></textarea>
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
            <button class="ts-helper-voice" :disabled="aiBusy" @click="startVoice('helper')">🎤 语音描述想法</button>
            <button class="ts-helper-go" :disabled="!helperDraft.trim() || aiBusy" @click="draftByAi">{{ aiBusy ? 'AI 正在写…' : '帮我写好' }}</button>
            <span v-if="aiBusy" class="ts-ai-prog"><span class="ts-ai-spin"></span></span>
          </div>
        </div>
        <!-- 常规输入行 + AI 助手行 -->
        <template v-else>
          <div class="ts-compose">
            <div class="ts-input">
              <textarea v-model="draft" class="ts-ta" rows="3" placeholder="说点什么…" @input="autoGrow" ref="taEl"></textarea>
              <button class="ts-send" :disabled="!draft.trim() || sending" @click="submitOpinion">发表</button>
            </div>
            <div class="ts-ai-row">
              <button v-if="draft.trim()" class="ts-ai-btn ai" :disabled="aiBusy" @click="polishByAi"><span v-if="aiBusy" class="ts-ai-spin"></span>{{ aiBusy ? 'AI 润色中…' : 'AI 润色' }}</button>
              <button v-else class="ts-ai-btn ai" :disabled="aiBusy" @click="onHelpWrite"><span v-if="aiBusy" class="ts-ai-spin"></span>{{ aiBusy ? 'AI 写作中…' : 'AI 帮写' }}</button>
              <button class="ts-ai-btn voice" :disabled="aiBusy" @click="startVoice('draft')">语音转文字</button>
            </div>
          </div>
        </template>
      </template>
      <div v-else-if="canDiscuss && interactive && !signedIn && (!topic.voteRequired || opinionOpen)" class="ts-input-hint">签到后可发表意见</div>

      <!-- 上一个 / 下一个议题：处理完当前议题直接切换，不用先关弹层 -->
      <div v-if="opinionOpen || hasPrev || hasNext" class="ts-nav-row">
        <button v-if="opinionOpen" class="ts-op-collapse" :class="{ warm: opinions.length }" @click="opinionOpen = false">收起</button>
        <button v-if="hasPrev && !opinionOpen" class="ts-nav-btn prev" @click="$emit('prev')">‹ 上一个议题</button>
        <!-- 第一个议题没有「上一个」：左下角补「返回」，避免左侧空着不对称（点了收起弹层回列表） -->
        <button v-else-if="!opinionOpen && hasNext" class="ts-nav-btn back" @click="$emit('close')">返回</button>
        <div v-if="hasNext" class="ts-next-action" :class="{ disabled: !canGoNext }" @click="onNextTap">
          <button class="ts-nav-btn next" :disabled="!canGoNext">下一个议题 <span class="ts-next-arrow">›</span></button>
        </div>
        <!-- 最后一个议题：右侧改为「完成」，点了收起弹层 -->
        <button v-else class="ts-nav-btn done" @click="$emit('close')">完成</button>
      </div>
    </div>

    <!-- 全部意见：叠加在当前议题弹层之上的大窗口，列表区域可独立下拉 -->
    <div v-if="opinionListOpen" class="ts-all-mask" @click.stop="opinionListOpen = false">
      <div class="ts-all-sheet" @click.stop>
        <div class="ts-all-handle"></div>
        <div class="ts-all-head">
          <span>全部意见（{{ opinions.length }}）</span>
          <button class="ts-all-close" @click="opinionListOpen = false">×</button>
        </div>
        <div class="ts-all-list">
          <div v-for="op in opinions" :key="op.id" class="ts-all-op">
            <div class="ts-op-l1">
              <span class="ts-op-name">{{ op.name }}</span>
              <span v-if="opVote(op)" class="ts-op-vote" :class="opVote(op).cls">{{ opVote(op).text }}</span>
              <span class="ts-op-time">{{ fmtTime(op.createdAt) }}</span>
            </div>
            <div class="ts-all-content">{{ op.content }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import api from '@/api'
import { toast, showModal, showActionSheet, showLoading, hideLoading } from '@/utils/ui'
import { getStorage } from '@/utils/storage'
import { useRecorder } from '@/composables/useRecorder'
import { useAsrStream } from '@/composables/useAsrStream'
import { applyHotwords } from '@/utils/helpers'

const props = defineProps({
  meetingId: { type: [String, Number], required: true },
  topic: { type: Object, default: null },        // detail.record.topics 里的一项（TopicVO）
  interactive: { type: Boolean, default: false }, // 会议进行中（可表决/发言）
  allowProxy: { type: Boolean, default: false },  // 是否允许代委员投票——仅现场会议结束后的会后整理阶段（补录未投委员）
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
const opinionListOpen = ref(false)

// 0717 用户定：通知并入讨论，notice/discussion 对外统一叫「通知和讨论」、同一配色；
// 底层 type 值不动，通报卡（正文+已读进度）仍按 type==='notice' 生效
const tagClass = computed(() => {
  const t = props.topic && props.topic.type
  return t === 'decision' ? 'vote' : 'discuss'
})
const tagLabel = computed(() => {
  const t = props.topic && props.topic.type
  // 0722 用户定：类型写清楚——通知/讨论分开显示（配色仍同一组）
  return t === 'decision' ? '表决' : (t === 'notice' ? '通知' : '讨论')
})
const canDiscuss = computed(() => props.topic && props.topic.type !== 'notice')
const showOpinionSection = computed(() => {
  if (!canDiscuss.value) return false
  return true
})
const opinionSummaryMode = computed(() => {
  return !!(props.topic && props.topic.voteRequired && !opinionOpen.value)
})
// 是否已有意见（用 opinionCount 立即判断，避免异步拉取意见后布局跳动）：
// 有意见→意见汇总置顶、投票区钉底(拇指区)；没意见→不占顶部，投票区直接排在指令条下(一眼看到要投票)
const hasOpinions = computed(() => {
  if (opinions.value.length > 0) return true
  return !!(props.topic && (props.topic.opinionCount || 0) > 0)
})
const visibleOpinions = computed(() => {
  if (!opinionSummaryMode.value) return opinions.value
  return opinions.value.slice(0, 3)
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
    if (t.myVote) return '已投票'
    if (!props.signedIn) return '签到后即可对本议题表决'
    return '请选择表决意见'
  }
  if (props.interactive && !props.signedIn) return '签到后可发表意见'
  return '可发表您的看法，也可直接去下一项'
})
// 研究P1「先选后交」：本地暂存"选中但未提交"的表决，可反复改；点「确认提交」才落库
const pendingVote = ref(null)      // 简单表决=choice字符串 / 多选=选项id
const pendingOption = ref(null)    // 多选时记住选项对象
const changeVoteOpen = ref(false)  // 已投后选项默认收起；点「改票」临时展开
const voteSubmitting = ref(false)
const localVoteValue = ref(null)
const localVoteLabel = ref('')
const localRetracted = ref(false) // 本地撤回态：撤回后先本地回到"未投"，等父组件刷新后端票再对齐
const VOTE_LABELS = { for_vote: '同意', against: '不同意', abstain: '弃权' }
const committedVote = computed(() => {
  const t = props.topic
  if (localRetracted.value) return null
  if (localVoteValue.value != null) return localVoteValue.value
  return t && t.myVote != null ? t.myVote : null
})
const displayVote = computed(() => {
  if (pendingVote.value != null) return pendingVote.value
  return committedVote.value
})
// 已投且没有待提交的新选择时，收起投票选项（0722 用户定）；「改票」可临时展开
const voteCollapsed = computed(() => committedVote.value != null && pendingVote.value == null && !changeVoteOpen.value)
const pendingLabel = computed(() => {
  if (pendingVote.value == null) return ''
  if (pendingOption.value) return pendingOption.value.label
  return VOTE_LABELS[pendingVote.value] || ''
})
const myVoteLabel = computed(() => {
  const t = props.topic
  const v = committedVote.value
  if (!t || v == null) return ''
  if ((t.decisionType || 'simple') === 'multi_choice') {
    const opt = (t.options || []).find(o => String(o.id) === String(v))
    return opt ? opt.label : '已投票'
  }
  return VOTE_LABELS[v] || ''
})
const displayVoteLabel = computed(() => {
  const t = props.topic
  const v = displayVote.value
  if (!t || v == null) return ''
  if ((t.decisionType || 'simple') === 'multi_choice') {
    const opt = (t.options || []).find(o => String(o.id) === String(v))
    return opt ? opt.label : ''
  }
  return VOTE_LABELS[v] || ''
})
const voteFeedbackClass = computed(() => {
  const v = displayVote.value
  if (v === 'for_vote') return 'agree'
  if (v === 'against') return 'against'
  if (v === 'abstain') return 'abstain'
  return ''
})
const voteCompleted = computed(() => {
  const t = props.topic
  if (!t || !t.voteRequired) return true
  if (pendingVote.value != null) return false
  return !!t.voteClosed || committedVote.value != null || !!localVoteLabel.value
})
// 0722 用户定：「已投」只在提交成功后显示；选中未提交显示"已选…点确认提交生效"，避免误以为投完
const voteFeedbackPending = computed(() => pendingVote.value != null)
const voteFeedbackText = computed(() => {
  const t = props.topic
  if (!t || !t.voteRequired) return ''
  if (voteFeedbackPending.value) return '已选：' + (pendingLabel.value || '')
  const label = myVoteLabel.value || localVoteLabel.value
  return label ? ('已投：' + label) : ''
})
const canGoNext = computed(() => !props.topic || !props.topic.voteRequired || voteCompleted.value)

// ── 表决进行中即亮实时票数明细(0722 用户定：不怕从众，透明优先)；结束后才下"通过/未通过"结论 ──
const voteRevealed = computed(() => {
  const t = props.topic
  if (!t) return false
  return !!t.voteClosed || !props.interactive
})
// 进行中的实时票数：表决还开着(会议进行中且未结束)就对所有人显示，只报数不下结论
const showLiveTally = computed(() => {
  const t = props.topic
  if (!t || !t.voteRequired) return false
  if (t.voteClosed || !props.interactive) return false
  return !opinionOpen.value
})
// 未投人数 = 应到 − 已投
const notVoted = computed(() => {
  const t = props.topic
  if (!t) return 0
  return Math.max(0, (t.total || 0) - (t.voted || 0))
})
// 总进度：已投 X / 应到 Y（放最前，避免把"未投"误解成还没签到）
const tallyProgress = computed(() => {
  const t = props.topic
  if (!t) return { voted: 0, total: 0 }
  return { voted: t.voted || 0, total: t.total || 0 }
})
// 票数明细分项（带颜色）：简单表决=同意绿/不同意红/弃权灰；多选项=各项中性。未投单独弱化显示
const breakdownParts = computed(() => {
  const t = props.topic
  if (!t) return []
  if ((t.decisionType || 'simple') === 'multi_choice') {
    return (t.options || []).map(o => ({ text: (o.label || '') + ' ' + (o.votes || 0), cls: 'opt' }))
  }
  return [
    { text: '同意 ' + (t.forVotes || 0), cls: 'agree' },
    { text: '不同意 ' + (t.agVotes || 0), cls: 'against' },
    { text: '弃权 ' + (t.abVotes || 0), cls: 'abstain' },
  ]
})

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
  if (opinionSummaryMode.value) return
  const s = new Set(openOpIds.value)
  s.has(op.id) ? s.delete(op.id) : s.add(op.id)
  openOpIds.value = s
}

// ── 代委员投票状态（仅主持人）：委员忘投/不会用手机时，主任代录。后端留 operator/isProxy/凭证 审计。
// 声明须在下方 immediate watch 之前（watch 首跑就会调 resetProxy）
const proxyOpen = ref(false)
const proxyLoading = ref(false)
const proxyTargets = ref([])        // 已签到且本议题未投票的委员（不含自己）
const proxySelected = ref(new Set())
const proxyMenuOpen = ref(false)    // 委员选择 ▾ 悬浮菜单开关
const proxyChoice = ref(null)       // simple: for_vote/against/abstain
const proxyOptId = ref(null)        // multi_choice: 选项 id
const proxyProofUrl = ref('')
const proxyUploading = ref(false)
const proxySubmitting = ref(false)
let _proxyProofInput = null
// 凭证测试期选填（0722 用户定，上线前再定是否必填——见 docs/上线前TODO.md）
const canSubmitProxy = computed(() =>
  proxySelected.value.size > 0
  && ((props.topic && (props.topic.decisionType || 'simple') === 'multi_choice') ? proxyOptId.value != null : !!proxyChoice.value)
  && !proxyUploading.value)
const proxySelectedNames = computed(() =>
  proxyTargets.value.filter(p => proxySelected.value.has(p.memberId)).map(p => p.name).join('、'))
function resetProxy() {
  proxyOpen.value = false; proxyLoading.value = false; proxyTargets.value = []
  proxyMenuOpen.value = false
  proxySelected.value = new Set(); proxyChoice.value = null; proxyOptId.value = null
  proxyProofUrl.value = ''; proxyUploading.value = false; proxySubmitting.value = false
}

// 打开（topic 切换/出现）时拉取本议题意见；关闭/切议题时收掉语音条（释放麦克风）和 AI 状态
watch(() => props.topic && props.topic.id, (id) => {
  cancelVoice()
  helperOn.value = false; helperDraft.value = ''; aiBusy.value = false
  aiTokens.value = 0; polishUndo.value = null; claimShowId.value = null; openOpIds.value = new Set()
  opinionOpen.value = false
  opinionListOpen.value = false
  pendingVote.value = null; pendingOption.value = null; voteSubmitting.value = false; localVoteValue.value = null; localVoteLabel.value = ''; localRetracted.value = false
  changeVoteOpen.value = false
  resetProxy()
  if (id) { draft.value = ''; draftFromVoice.value = false; loadOpinions() }
}, { immediate: true })
onBeforeUnmount(() => { cancelVoice(); clearInterval(aiProgTimer) })

// 研究P1「已宣读降级」#8：委员本人显式确认「我已读」（只记自己，不再一人点就全体已通报）。
// 会议参会名单中的全体委员都确认后，后端自动把该通报标记「全体已通报」。
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
    // 点击“说完了”即刻停止麦克风与 WebSocket，不再等待服务端继续追加识别文字。
    text = String(asrLive.value || '').trim()
    asr.cancel()
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
  if (mode === 'polish') {
    showModal({
      title: '',
      content: '已润色，可继续修改',
      size: 'aicard',
      showCancel: false,
      confirmText: '查看'
    })
    return
  }
  showModal({
    title: '',
    content: '已按你的想法拟好，可修改',
    size: 'aicard',
    showCancel: false,
    confirmText: '查看'
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

// 先点选项高亮，再点小确认提交；已投后点其他选项先确认改票意图。
async function pickVote(choice, option) {
  const t = props.topic
  if (!t || t.voteClosed) return
  if (voteSubmitting.value) return
  if (!props.interactive) { toast({ title: '会议进行中才可表决', icon: 'none' }); return }
  if (!props.signedIn) { toast({ title: '请先完成签到', icon: 'none' }); return }
  const nextVote = option ? option.id : choice
  const currentVote = committedVote.value
  if (currentVote != null) {
    if (String(currentVote) === String(nextVote)) {
      pendingVote.value = null; pendingOption.value = null
      changeVoteOpen.value = false // 改票时又点回原选项 = 不改了，收回选项区
      return
    }
    const nextLabel = option ? option.label : (VOTE_LABELS[choice] || '该选项')
    const res = await showModal({
      title: '',
      content: '改为「' + nextLabel + '」吗？',
      confirmText: '确认',
      cancelText: '取消',
      size: 'vote'
    })
    if (!res.confirm) return
    // 改票弹窗的「确认」已是明确意图，直接提交，不再要求二次点「确认提交」
    pendingVote.value = option ? option.id : choice
    pendingOption.value = option || null
    await submitVote()
    return
  }
  pendingVote.value = option ? option.id : choice
  pendingOption.value = option || null
}
async function submitVote() {
  const t = props.topic
  if (!t || t.voteClosed || pendingVote.value == null) return
  if (!props.interactive) { toast({ title: '会议进行中才可表决', icon: 'none' }); return }
  if (!props.signedIn) { toast({ title: '请先完成签到', icon: 'none' }); return }
  const opt = pendingOption.value
  const label = pendingLabel.value
  const isChangingVote = committedVote.value != null
  voteSubmitting.value = true
  try {
    const nextValue = opt ? opt.id : pendingVote.value
    await api.committeeVote(props.meetingId, t.id, opt ? null : pendingVote.value, opt ? opt.id : null)
    toast({ title: '已提交', icon: 'success' })
    localVoteValue.value = nextValue
    localVoteLabel.value = label
    localRetracted.value = false // 重新投票后清掉撤回态
    changeVoteOpen.value = false // 提交后选项收回
    pendingVote.value = null; pendingOption.value = null
    // 改票后立即同步本人已发表意见旁的标签，避免父组件刷新与意见请求竞态时仍显示第一次投票。
    opinions.value = opinions.value.map((opinion) => {
      if (!opinion || !opinion.isSelf) return opinion
      if (opt) return Object.assign({}, opinion, { voteLabel: label, voteChoice: null })
      return Object.assign({}, opinion, { voteLabel: null, voteChoice: pendingVoteValueForLabel(nextValue) })
    })
    emit('changed')
    // 提交后清空 pendingVote，确认提交按钮随即收起；保留在当前议题页，
    // 方便用户继续补充意见或前往下一个议题。
    if (isChangingVote) {
      await loadOpinions() // 再以后端当前票为准校准一次
      await handleOpinionAfterVoteChange(nextValue, opt)
    }
  } catch (e) {
    toast({ title: (e && e.message) || '提交失败，请稍后重试', icon: 'none' })
  }
  finally { voteSubmitting.value = false }
}

function pendingVoteValueForLabel(value) {
  return value === 'for_vote' || value === 'against' || value === 'abstain' ? value : null
}

// 撤回投票入口：表决未结束、本人已投、且当前不在改票选中态时显示
const showRetract = computed(() => {
  const t = props.topic
  if (!t || !t.voteRequired || t.voteClosed) return false
  if (!props.interactive || !props.signedIn) return false
  return committedVote.value != null && pendingVote.value == null
})
// 撤回本人投票（表决未结束前）：回到"未投"，可重新投票
async function retractVote() {
  const t = props.topic
  if (!t || t.voteClosed || voteSubmitting.value) return
  if (!props.interactive) { toast({ title: '会议进行中才可撤回', icon: 'none' }); return }
  if (committedVote.value == null) return
  const res = await showModal({
    title: '撤回投票',
    content: '撤回后可重新投票',
    confirmText: '撤回',
    cancelText: '取消'
  })
  if (!res.confirm) return
  voteSubmitting.value = true
  try {
    await api.committeeRetractVote(props.meetingId, t.id)
    localRetracted.value = true
    localVoteValue.value = null; localVoteLabel.value = ''
    pendingVote.value = null; pendingOption.value = null
    changeVoteOpen.value = false
    // 同步已发表意见旁的投票标签，避免仍显示撤回前的选择
    opinions.value = opinions.value.map((opinion) =>
      (opinion && opinion.isSelf) ? Object.assign({}, opinion, { voteLabel: null, voteChoice: null }) : opinion)
    toast({ title: '已撤回', icon: 'success' })
    emit('changed')
    await loadOpinions()
  } catch (e) {
    toast({ title: (e && e.message) || '撤回失败，请重试', icon: 'none' })
  } finally { voteSubmitting.value = false }
}

async function openProxy() {
  const t = props.topic
  if (!t) return
  proxyOpen.value = true
  proxyLoading.value = true
  try {
    const list = await api.committeeProxyTargets(props.meetingId)
    const me = getStorage('activeRole', null) || {}
    proxyTargets.value = (list || []).filter(p =>
      p.signedIn
      && !(p.votedTopicIds || []).some(id => String(id) === String(t.id))
      && String(p.memberId) !== String(me.id || ''))
  } catch (e) {
    toast({ title: (e && e.message) || '名单加载失败', icon: 'none' })
    proxyOpen.value = false
  } finally { proxyLoading.value = false }
}
// 单选（0722 用户定）：一次只代一个人，选完即收起下拉；换人就再点开重选
function toggleProxyMember(id) {
  proxySelected.value = new Set([id])
  proxyMenuOpen.value = false
}
function pickProxyProof() {
  if (!_proxyProofInput) {
    _proxyProofInput = document.createElement('input')
    _proxyProofInput.type = 'file'
    _proxyProofInput.accept = 'image/*'
    _proxyProofInput.style.display = 'none'
    _proxyProofInput.addEventListener('change', onProxyProofChange)
    document.body.appendChild(_proxyProofInput)
  }
  _proxyProofInput.value = ''
  _proxyProofInput.click()
}
async function onProxyProofChange(e) {
  const f = (e.target.files || [])[0]
  if (!f) return
  proxyUploading.value = true
  try {
    const res = await api.uploadAttachment(f)
    proxyProofUrl.value = (res && res.url) || ''
    if (!proxyProofUrl.value) throw new Error('上传失败')
  } catch (err) {
    toast({ title: (err && err.message) || '凭证上传失败，请重试', icon: 'none' })
  } finally { proxyUploading.value = false }
}
async function submitProxy() {
  const t = props.topic
  if (!t || !canSubmitProxy.value || proxySubmitting.value) return
  const names = proxyTargets.value.filter(p => proxySelected.value.has(p.memberId)).map(p => p.name).join('、')
  const isMulti = (t.decisionType || 'simple') === 'multi_choice'
  const label = isMulti
    ? (((t.options || []).find(o => String(o.id) === String(proxyOptId.value)) || {}).label || '')
    : ({ for_vote: '同意', against: '不同意', abstain: '弃权' }[proxyChoice.value] || '')
  // 按钮本身就叫「确认代投」，不再二次弹窗（0722 用户定）；成功 toast 里带上代了谁投了什么
  proxySubmitting.value = true
  try {
    await api.committeeProxySubmit(props.meetingId, {
      actionType: 'vote',
      topicId: t.id,
      memberIds: Array.from(proxySelected.value),
      choice: isMulti ? null : proxyChoice.value,
      selectedId: isMulti ? proxyOptId.value : null,
      proofUrl: proxyProofUrl.value || null
    })
    toast({ title: '已代 ' + names + ' 投「' + label + '」', icon: 'success' })
    resetProxy()
    emit('changed')
  } catch (e) {
    toast({ title: (e && e.message) || '代投失败，请重试', icon: 'none' })
  } finally { proxySubmitting.value = false }
}

// 改票后，已有意见可能与新立场冲突：让委员明确选择如何处理最近一条本人意见。
async function handleOpinionAfterVoteChange(voteValue, option) {
  const own = opinions.value.filter(op => op && op.isSelf && op.canEdit)
  if (!own.length) return
  const target = own[own.length - 1]
  // 循环：编辑/重写弹窗点「取消」返回三选一，而不是直接关闭整个流程（0723 用户定）
  while (true) {
    const sheet = await showActionSheet({
      title: '表决已修改，原意见怎么处理？',
      variant: 'opinion-change',
      itemList: [
        { icon: '✎', label: '自己修改', tone: 'edit' },
        { icon: 'AI', label: 'AI 按新表决重写', tone: 'ai' },
        { icon: '删', label: '删除原意见', tone: 'danger' }
      ]
    })
    if (!sheet || sheet.tapIndex == null || sheet.tapIndex < 0) return // 三选一本身取消=退出
    if (sheet.tapIndex === 0) {
      const edited = await showModal({
        title: '修改原意见', content: target.content || '', editable: true,
        placeholderText: '请输入修改后的意见', confirmText: '确认', cancelText: '取消', size: 'large'
      })
      if (!edited.confirm) continue // 取消→返回三选一
      const content = String(edited.content || '').trim()
      if (!content) continue
      const updated = await api.committeeUpdateOpinion(props.meetingId, target.id, content)
      opinions.value = opinions.value.map(op => op.id === target.id ? updated : op)
      toast({ title: '意见已修改', icon: 'success' })
      emit('changed')
      return
    }
    if (sheet.tapIndex === 1) {
      const seed = voteStanceSeed(voteValue, option)
      if (!seed) return
      try {
        showLoading({ title: 'AI 正在按新表决重写，请稍等…' })
        const generated = await api.committeeOpinionAssist(props.meetingId, props.topic.id, 'draft', seed)
        hideLoading()
        if (!generated || !generated.text) { toast({ title: 'AI 没写出来，请重试', icon: 'none' }); continue }
        const review = await showModal({
          title: '确认重写意见', content: generated.text, editable: true,
          placeholderText: '可修改 AI 生成的意见', confirmText: '替换', cancelText: '取消', size: 'large'
        })
        if (!review.confirm) continue // 取消→返回三选一
        const content = String(review.content || '').trim()
        if (!content) continue
        const updated = await api.committeeUpdateOpinion(props.meetingId, target.id, content)
        opinions.value = opinions.value.map(op => op.id === target.id ? updated : op)
        toast({ title: '意见已按新投票重写', icon: 'success' })
        emit('changed')
        return
      } catch (e) {
        hideLoading()
        toast({ title: (e && e.message) || 'AI 重写失败，请稍后重试', icon: 'none' })
        return
      }
    }
    if (sheet.tapIndex === 2) {
      await removeOpinion(target)
      return
    }
  }
}

function onNextTap() {
  if (!canGoNext.value) {
    toast({ title: '请先完成本项表决', icon: 'none' })
    return
  }
  emit('next')
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
.ts-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.45); z-index: 220; display: flex; flex-direction: column; justify-content: flex-end; } /* 高于会后整理页(180)：整理页点议题行也能弹出 */
/* 弹层：flex 列——把手/标题固定在上，意见汇总区(.ts-scroll)独占中间可滚动，输入区固定在底部 */
.ts-sheet { background: #fff; border-radius: 28rpx 28rpx 0 0; padding: 14rpx 30rpx calc(24rpx + env(safe-area-inset-bottom)); height: 88vh; max-height: 92vh; overflow: hidden; display: flex; flex-direction: column; }
.ts-handle { flex-shrink: 0; width: 72rpx; height: 8rpx; border-radius: 4rpx; background: #E4E6EA; margin: 0 auto 16rpx; }
.ts-head { flex-shrink: 0; display: flex; align-items: flex-start; gap: 12rpx; margin-bottom: 20rpx; }
.ts-titlewrap { flex: 1; min-width: 0; display: flex; align-items: flex-start; flex-wrap: wrap; column-gap: 12rpx; row-gap: 4rpx; max-height: 108rpx; overflow: hidden; padding-right: 4rpx; }
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
.ts-tag.discuss { background: #EAF6EE; color: #2E8B57; }
.ts-tag.vote { background: #FFF0E5; color: #D56A16; }
/* .ts-tag.notice 已删（0717 通知并入讨论，tagClass 不再产出 notice） */
.ts-close { flex-shrink: 0; width: 56rpx; height: 56rpx; line-height: 52rpx; text-align: center; font-size: 44rpx; color: #999; margin: -8rpx -12rpx 0 0; }

/* 全部意见大窗口：覆盖在原议题弹层之上，正文列表独立滚动 */
.ts-all-mask { position: fixed; inset: 0; z-index: 2; display: flex; flex-direction: column; justify-content: flex-end; background: rgba(0,0,0,.38); }
.ts-all-sheet { height: 88vh; max-height: 92vh; box-sizing: border-box; display: flex; flex-direction: column; overflow: hidden; background: #fff; border-radius: 28rpx 28rpx 0 0; padding: 14rpx 32rpx calc(28rpx + env(safe-area-inset-bottom)); box-shadow: 0 -10rpx 40rpx rgba(0,0,0,.16); }
.ts-all-handle { flex-shrink: 0; width: 72rpx; height: 8rpx; border-radius: 4rpx; background: #D9DDE3; margin: 0 auto 16rpx; }
.ts-all-head { flex-shrink: 0; display: flex; align-items: center; justify-content: space-between; padding-bottom: 18rpx; border-bottom: 2rpx solid #EEF0F3; font-size: 34rpx; font-weight: 800; color: #1f2329; }
.ts-all-close { width: 56rpx; height: 56rpx; border: 0; background: transparent; color: #8A8F98; font-size: 44rpx; line-height: 1; }
.ts-all-list { flex: 1; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 6rpx 0 24rpx; }
.ts-all-op { padding: 24rpx 4rpx; border-bottom: 2rpx solid #F0F1F3; }
.ts-all-op:last-child { border-bottom: 0; }
.ts-all-content { margin-top: 10rpx; color: #33373D; font-size: 29rpx; line-height: 1.65; white-space: pre-wrap; word-break: break-word; }

.ts-sheet.is-vote { width: 100%; height: 100vh; max-height: 100vh; box-sizing: border-box; border-radius: 0; padding: calc(18rpx + env(safe-area-inset-top)) 34rpx calc(24rpx + env(safe-area-inset-bottom)); background: #fff; }
.ts-sheet.is-vote .ts-handle { display: none; }
.ts-sheet.is-vote .ts-head { margin-bottom: 12rpx; align-items: flex-start; }
.ts-sheet.is-vote .ts-titlewrap { display: block; max-height: 168rpx; overflow:hidden; padding-right: 8rpx; }
.ts-sheet.is-vote .ts-title { display: block; font-size: 38rpx; line-height: 1.35; font-weight: 800; text-align: left; }
.ts-sheet.is-vote .ts-tag { display: none; }
.ts-sheet.is-vote .ts-guide { display: none; }
.ts-sheet.is-vote .ts-scroll { display: flex; flex-direction: column; }
.ts-sheet.is-vote .ts-vote { margin: 0; }
.ts-sheet.is-vote .ts-vote:not(.opinion-mode) { flex: 1 1 auto; min-height: 0; display: flex; flex-direction: column; }
/* 有意见时投票区钉底(拇指区)；没意见时(无 .has-ops)不钉底，投票区直接排在标题下方 */
.ts-sheet.is-vote .ts-vote.has-ops:not(.opinion-mode) .ts-vote-mine { margin-top: auto; }
.ts-sheet.is-vote .ts-vote.opinion-mode { min-height: 0; }
.ts-sheet.is-vote .ts-vote-btns { display: flex; flex-direction: column; gap: 16rpx; }
.ts-sheet.is-vote .ts-vote-btn { display: flex; align-items: center; justify-content: flex-start; gap: 18rpx; border-radius: 16rpx; border: 2rpx solid #E1E4E8; background: #fff; color: #1f2329; font-size: 34rpx; font-weight: 700; padding: 26rpx 24rpx; text-align: left; box-shadow: none; }
.ts-sheet.is-vote .ts-vote-btn.agree,
.ts-sheet.is-vote .ts-vote-btn.against,
.ts-sheet.is-vote .ts-vote-btn.abstain { background: #fff; border-color: #E1E4E8; color: #1f2329; }
.ts-radio { flex-shrink: 0; width: 34rpx; height: 34rpx; border-radius: 50%; border: 3rpx solid #C5CBD3; box-sizing: border-box; background: #fff; }
.ts-sheet.is-vote .ts-vote-btn.agree.on { border-color: #69B95B; background: #F0FAED; color: #2E7D32; box-shadow: 0 0 0 4rpx rgba(62,155,52,0.10); }
.ts-sheet.is-vote .ts-vote-btn.against.on { border-color: #ED7B70; background: #FFF3F1; color: #C0392B; box-shadow: 0 0 0 4rpx rgba(226,75,58,0.10); }
.ts-sheet.is-vote .ts-vote-btn.abstain.on { border-color: #8D949D; background: #F4F5F7; color: #4D5158; box-shadow: 0 0 0 4rpx rgba(77,81,88,0.10); }
.ts-sheet.is-vote .ts-vote-btn.agree.on .ts-radio { border-color: #3E9B34; box-shadow: inset 0 0 0 8rpx #fff; background: #3E9B34; }
.ts-sheet.is-vote .ts-vote-btn.against.on .ts-radio { border-color: #E24B3A; box-shadow: inset 0 0 0 8rpx #fff; background: #E24B3A; }
.ts-sheet.is-vote .ts-vote-btn.abstain.on .ts-radio { border-color: #4D5158; box-shadow: inset 0 0 0 8rpx #fff; background: #4D5158; }
.ts-sheet.is-vote .ts-vote-submit { width: 60%; min-height: 88rpx; margin: 20rpx auto 0; border-radius: 16rpx; padding: 20rpx 32rpx; font-size: 30rpx; background: var(--c-primary-dark, #A85800); }
.ts-sheet.is-vote .ts-vote-submit:active { background: var(--c-primary-strong, #8A4A00); }
.ts-sheet.is-vote .ts-vote-submit[disabled] { background: #D8C3AB; color: #fff; }
.ts-vote-locktip { margin-top: 12rpx; text-align: center; font-size: 24rpx; color: #9AA0A6; }
.ts-sheet.is-vote .ts-vote-submit-tip { display: none; }
.ts-sheet.is-vote .ts-vote-all { margin-top: 22rpx; padding-top: 18rpx; border-top: 2rpx solid #F0F1F3; }
.ts-op-entry.in-summary { margin: 18rpx auto 4rpx; }
.ts-op-entry { display: flex; align-items: center; justify-content: center; width: 51%; min-height: 80rpx; box-sizing: border-box; margin: 20rpx auto 0; border: 2rpx solid #A9CBEA; border-radius: 16rpx; background: #EAF3FC; color: #1F6FB2; font-size: 32rpx; font-weight: 700; padding: 14rpx 24rpx; font-family: inherit; line-height: 1.2; box-shadow: 0 4rpx 12rpx rgba(31,111,178,0.12); }
.ts-op-entry.open { background: #E4F1FC; color: #185A91; border-color: #8EC0EA; }

.ts-vote { margin-top: 16rpx; margin-bottom: 24rpx; } /* 标题与投票按钮之间多留 8px */
.ts-vote-status-row { display: flex; align-items: center; gap: 14rpx; margin-top: 16rpx; }
.ts-vote-btns { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 14rpx; }
.ts-vote-btn { border: 2rpx solid #D8DBE0; border-radius: 16rpx; background: #fff; color: #444; font-size: 32rpx; font-weight: 700; padding: 22rpx 0; }
/* 方案B：默认就带语义色(浅底+彩边+彩字)，同意绿/不同意红/弃权灰，一眼分清 */
.ts-vote-btn.agree { background: #EAF6E5; border-color: #52A344; color: #2E7D32; }
.ts-vote-btn.against { background: #FDECEA; border-color: #E74C3C; color: #C0392B; }
.ts-vote-btn.abstain { background: #F2F2F4; border-color: #9AA0A6; color: #5F6570; }
/* 选中态：加深为实心白字 + 色环，与默认浅底拉开层次 */
.ts-vote-btn.agree.on { background: #F0FAED; border-color: #69B95B; color: #2E7D32; box-shadow: 0 0 0 4rpx rgba(62,155,52,0.10); }
.ts-vote-btn.against.on { background: #FFF3F1; border-color: #ED7B70; color: #C0392B; box-shadow: 0 0 0 4rpx rgba(226,75,58,0.10); }
.ts-vote-btn.abstain.on { background: #F4F5F7; border-color: #8D949D; color: #4D5158; box-shadow: 0 0 0 4rpx rgba(77,81,88,0.10); }
.ts-vote-btn.off { opacity: 0.35; }
.ts-vote-btn:active { transform: scale(0.97); }
.ts-opt { display: flex; align-items: center; justify-content: space-between; border: 2rpx solid #D8DBE0; border-radius: 16rpx; padding: 22rpx 24rpx; margin-bottom: 14rpx; font-size: 32rpx; color: #333; }
.ts-opt.on { background: #FFF6E8; border-color: #FFA800; color: #9A6A00; font-weight: 700; }
.ts-opt.off { opacity: 0.4; }
.ts-opt-votes { font-size: 26rpx; color: #999; }
/* 全体计票：独立浅底卡片，与个人投票区拉开距离(避免误认成个人结果)；"全体"前缀点明范围 */
.ts-vote-all { margin-top: 44rpx; }
/* 紧凑揭晓（0722）：一行=结论徽标+票数统计，不占大区域 */
.ts-vote-all.compact { margin-top: 20rpx; }
.ts-result-line { display: flex; align-items: center; flex-wrap: wrap; gap: 8rpx 14rpx; padding: 14rpx 18rpx; border-radius: 14rpx; background: #F7F8FA; border: 2rpx solid #EDEFF2; }
.ts-result-badge { flex-shrink: 0; font-size: 26rpx; font-weight: 800; padding: 4rpx 16rpx; border-radius: 999rpx; }
.ts-result-line.pass .ts-result-badge { background: #EAF6E5; color: #2E7D32; border: 2rpx solid #B8DFAF; }
.ts-result-line.fail .ts-result-badge { background: #FDECEA; color: #C0392B; border: 2rpx solid #F0B3AB; }
/* 进行中(实时票数)：中性蓝，区别于已揭晓的绿/红结论 */
.ts-result-line.live { background: #F4F8FF; border-color: #DCE8FB; }
.ts-result-line.live .ts-result-badge { background: #E7F0FF; color: #2F6BD8; border: 2rpx solid #C6DBF7; }
.ts-result-nums { font-size: 25rpx; font-weight: 600; color: #5F6673; font-variant-numeric: tabular-nums; }
/* 票数分项上色：同意绿 / 不同意红 / 弃权灰（0722 用户定） */
.rn-part.agree { color: #2E7D32; }
.rn-part.against { color: #C0392B; }
.rn-part.abstain { color: #5F6673; }
.rn-part.opt { color: #5F6673; }
.rn-sep { color: #C4C9D0; }
/* 总进度「已投 X/Y」放最前、加重（关键信息）；后跟一段间距再列明细 */
.rn-part.progress { color: #2A2E35; font-weight: 800; margin-right: 16rpx; }
/* 未投弱化：小字浅灰括注，避免被误解成"还没签到" */
.rn-part.faint { color: #B6BBC3; font-size: 22rpx; margin-left: 12rpx; }
/* 我的投票并入票数卡内一行：细分隔线 + 状态绿字 + 右侧小号改票/撤回 */
.ts-my-line { display: flex; align-items: center; gap: 12rpx; margin-top: 14rpx; padding: 14rpx 18rpx 0; border-top: 2rpx solid #E3ECF9; } /* 左右各 18rpx 与蓝卡内容对齐 */
.ts-my-vote { flex: 1; min-width: 0; font-size: 25rpx; font-weight: 700; color: #2E7D32; }
.ts-mini-act { flex-shrink: 0; border: 2rpx solid #D8DBE0; background: #fff; color: #6B7078; font-size: 23rpx; font-weight: 600; border-radius: 999rpx; padding: 6rpx 20rpx; font-family: inherit; line-height: 1.3; }
.ts-mini-act:active { background: #F1F2F4; }
.ts-mini-act:disabled { opacity: .5; }
.ts-vote-hint { font-size: 24rpx; color: #9AA0A6; margin-top: 10rpx; }
.ts-vote-hint.mine { color: #2E7D32; font-weight: 600; }
/* 先选后交（研究P1）：确认提交按钮——选好才亮，带"提交后不可改"静态提示 */
.ts-vote-submit { display: flex; align-items: center; justify-content: center; box-sizing: border-box; width: 60%; min-height: 88rpx; margin: 20rpx auto 0; border: 0; border-radius: 16rpx; background: var(--c-primary-dark, #A85800); color: #fff; font-size: 30rpx; font-weight: 800; padding: 20rpx 32rpx; font-family: inherit; line-height: 1.2; box-shadow: 0 6rpx 16rpx rgba(168,88,0,0.22); white-space: nowrap; }
.ts-vote-submit:active { background: var(--c-primary-strong, #8A4A00); }
.ts-vote-submit[disabled] { background: #D8C3AB; color: #fff; box-shadow: none; }
.ts-vote-submit-tip { font-size: 24rpx; font-weight: 400; opacity: 0.92; margin-left: 4rpx; }
.ts-vote-feedback { display:inline-flex; align-items:center; gap:8rpx; padding:10rpx 16rpx; border-radius:999rpx; background:#EAF6E5; border:2rpx solid #B8DFAF; color:#2E7D32; font-size:25rpx; font-weight:800; }
/* 选中未提交：暖橙提示色，与提交成功的绿色明确区分（0722：别让"已选"看着像"已投"） */
.ts-vote-feedback-mark { width:28rpx; height:28rpx; border-radius:50%; background:#2E9E4B; color:#fff; display:inline-flex; align-items:center; justify-content:center; font-size:18rpx; flex-shrink:0; }
.ts-vote-feedback.against { background:#FFF3F1; border-color:#F1B4AD; color:#C0392B; }
.ts-vote-feedback.against .ts-vote-feedback-mark { background:#E24B3A; }
.ts-vote-feedback.abstain { background:#F4F5F7; border-color:#D4D8DE; color:#4D5158; }
.ts-vote-feedback.abstain .ts-vote-feedback-mark { background:#6B7078; }
/* 已选未提交：中性灰（0722 用户定：不用暖棕，用原灰色）；放最后确保覆盖 agree/against/abstain 配色 */
.ts-vote-feedback.pending { background:#F4F5F7; border-color:#D4D8DE; color:#4D5158; }
/* 已投收起态的「改票/撤回」：小号按钮靠右并排；改票略强（常用），撤回弱化（少用） */
.ts-vote-change { margin-left:auto; padding:8rpx 22rpx; border-radius:999rpx; background:#fff; border:2rpx solid #C9CED6; color:#4D5158; font-size:24rpx; font-weight:650; font-family:inherit; white-space:nowrap; }
.ts-vote-change:active { background:#F1F2F4; }
.ts-vote-change:disabled { opacity:.5; }
.ts-vote-retract { margin-left:auto; padding:8rpx 20rpx; border-radius:999rpx; background:#fff; border:2rpx solid #D8DBE0; color:#8A9099; font-size:24rpx; font-weight:600; font-family:inherit; white-space:nowrap; }
.ts-vote-change ~ .ts-vote-retract { margin-left:12rpx; } /* 与改票并排时不再各自撑开 */
.ts-vote-retract:disabled { opacity:.5; }
/* 代委员投票（仅主持人）：入口小字按钮；面板浅底卡片内选人/选项/凭证/提交 */
.ts-proxy { margin-top:26rpx; }
/* 入口=弹层里最显眼的实心按钮（0722 用户定：不自动展开面板，用醒目入口引导） */
/* 次级按钮层级（Ant/WeUI 惯例：一个弹层只留一个彩色填充按钮）：白底描边+主题色文字，
   靠宽度和居中位置保持醒目，不靠色块抢戏 */
.ts-proxy-entry { display:flex; align-items:center; justify-content:center; width:51%; min-height:80rpx; box-sizing:border-box; margin:0 auto; border:2rpx solid #7FB5AE; border-radius:16rpx; background:#E6F4F2; color:#0F766E; font-size:29rpx; font-weight:700; font-family:inherit; padding:16rpx 24rpx; box-shadow:0 4rpx 12rpx rgba(15,118,110,0.14); }
.ts-proxy-entry:active { background:#D5EBE8; }
.ts-proxy-panel { background:#F8F9FB; border:2rpx solid #ECEEF2; border-radius:14rpx; padding:18rpx; }
.ts-proxy-title { display:flex; align-items:center; justify-content:space-between; font-size:25rpx; font-weight:700; color:#3C434B; }
.ts-proxy-close { color:#98A2B3; font-size:34rpx; line-height:1; padding:0 8rpx; }
/* 委员选择：与签到状态同款 ▾ 悬浮下拉。触发行=已选名单+小箭头，菜单悬浮不占位 */
.ts-proxy-pick { position:relative; margin-top:14rpx; width:30%; min-width:224rpx; box-sizing:border-box; display:flex; align-items:center; gap:10rpx; background:#fff; border:2rpx solid #ECEEF2; border-radius:12rpx; padding:12rpx 14rpx; }
.ts-proxy-pick-label { flex:1; min-width:0; font-size:26rpx; font-weight:600; color:#1F2329; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.ts-proxy-pick-label.ph { color:#A0A5AD; font-weight:500; }
.ts-proxy-pick-arrow { flex-shrink:0; width:44rpx; height:44rpx; display:flex; align-items:center; justify-content:center; border-radius:10rpx; color:#A0A5AD; font-size:24rpx; background:#F4F5F7; }
.ts-proxy-pick-arrow.on { background:#E8EAED; color:#5F6673; }
.ts-proxy-menu { position:absolute; right:0; top:calc(100% + 4rpx); z-index:40; display:flex; flex-direction:column; min-width:184rpx; padding:8rpx; background:#fff; border:2rpx solid #E8EAED; border-radius:14rpx; box-shadow:0 10rpx 28rpx rgba(31,35,41,0.16); }
.ts-proxy-menu-item { padding:14rpx 22rpx; font-size:25rpx; font-weight:600; color:#42464D; border-radius:10rpx; line-height:1.3; }
.ts-proxy-menu-item:active { background:#F1F2F4; }
.ts-proxy-menu-item.cur { color:#0F766E; background:#EFF6F5; }
.ts-proxy-choices { margin-top:14rpx; display:flex; flex-wrap:wrap; gap:12rpx; }
.ts-proxy-choice { padding:10rpx 24rpx; border-radius:12rpx; border:2rpx solid #D8DBE0; background:#fff; color:#55585E; font-size:25rpx; font-weight:600; }
.ts-proxy-choice.on { border-color:#B26A19; background:#FFF6E8; color:#B26A19; }
.ts-proxy-proof { margin-top:14rpx; display:flex; align-items:center; gap:12rpx; }
.ts-proxy-proof-btn { border:2rpx dashed #C9CED6; background:#fff; color:#7A7F87; font-size:23rpx; font-weight:500; border-radius:10rpx; padding:10rpx 20rpx; font-family:inherit; }
.ts-proxy-proof-btn.ok { border-style:solid; border-color:#B8DFAF; background:#F2FAEF; color:#2E7D32; }
.ts-proxy-proof-tip { font-size:21rpx; color:#A0A5AD; }
.ts-proxy-submit { display:block; width:60%; margin:16rpx auto 0; border:0; border-radius:999rpx; background:#0F766E; color:#fff; font-size:26rpx; font-weight:700; padding:14rpx 0; font-family:inherit; }
.ts-proxy-submit:disabled { background:#C7D1D5; }
/* 已表决：结果卡整块替换选择区（占屏结果态，明确"做完了·不可改"） */
.ts-vote-done { display: flex; align-items: center; flex-wrap: wrap; gap: 6rpx 12rpx; background: #EAF6E5; border: 2rpx solid #9FD290; border-radius: 16rpx; padding: 24rpx; font-size: 31rpx; font-weight: 800; color: #2E7D32; }
.ts-vote-done-mark { display: inline-flex; align-items: center; justify-content: center; width: 40rpx; height: 40rpx; border-radius: 50%; background: #2E9E4B; color: #fff; font-size: 26rpx; margin-right: 4rpx; }
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
.ts-notice-read { border: none; background: #2E8B57; color: #fff; font-size: 30rpx; font-weight: 700; border-radius: 14rpx; padding: 20rpx 40rpx; }
.ts-notice-read:active { background: #256F45; }
/* #8：本人已确认「我已读」的状态标 */
.ts-notice-mine { flex-shrink: 0; font-size: 26rpx; font-weight: 700; color: #2E7D32; }
/* #8：主任「标记全体已通报」——单独一行、描边弱化，与委员本人确认区分开 */
.ts-notice-chair { display: flex; align-items: center; gap: 16rpx; margin-top: 16rpx; padding-top: 16rpx; border-top: 2rpx dashed #EBD9B8; }
.ts-notice-forceall { flex-shrink: 0; border: 2rpx solid #C76A00; background: #FFF6EC; color: #A85800; font-size: 26rpx; font-weight: 700; border-radius: 14rpx; padding: 12rpx 24rpx; }
.ts-notice-forceall:active { background: #FBEAD6; }
.ts-notice-chair-tip { font-size: 22rpx; color: #B79A6A; line-height: 1.4; }
.ts-ops { border-top: 2rpx solid #F2F2F4; padding-top: 18rpx; }
.ts-ops.summary { margin-top: 18rpx; padding-top: 14rpx; }
.ts-top-summary { flex-shrink: 0; margin-top: 0; margin-bottom: 12rpx; }
.ts-ops-title-row { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; margin-bottom: 14rpx; }
.ts-ops-title-row .ts-ops-head { margin-bottom: 0; }
.ts-ops-head { font-size: 30rpx; font-weight: 700; color: #1f2329; margin-bottom: 14rpx; }
.ts-ops-expand { flex-shrink: 0; border: 0; background: transparent; color: #1F6FB2; font-size: 25rpx; font-weight: 600; padding: 8rpx 4rpx 8rpx 18rpx; }
.ts-ops-expand:active { opacity: .6; }
/* 查看与意见卡内"删除"右缘对齐：卡片内边距 18rpx，查看按钮右移到同一竖线 */
.ts-ops.summary .ts-ops-expand { padding-right: 0; margin-right: 18rpx; }
.ts-op-foot .ts-op-del-inline { padding-right: 0; }
.ts-ops.summary .ts-ops-head { font-size: 26rpx; margin-bottom: 8rpx; color: #5F6570; }
.ts-ops.summary .ts-ops-title-row .ts-ops-head { margin-bottom: 0; }
.ts-empty { font-size: 28rpx; color: #9AA0A6; padding: 18rpx 0 24rpx; }
/* 意见条（方案C 极简两行式）：第一行 姓名+表决标签+时间，第二行 意见摘要+查看；点击整条展开全文 */
.ts-op { padding: 16rpx 0; border-bottom: 2rpx solid #F2F0EC; cursor: pointer; }
/* 意见汇总：每条意见做浅白背景小卡片，删除按钮在卡片右下角（0722 用户定） */
.ts-ops.summary .ts-op { padding: 16rpx 18rpx; cursor: default; background: #F8F9FB; border: 2rpx solid #ECEEF2; border-radius: 14rpx; margin-bottom: 12rpx; }
.ts-ops.summary .ts-op:last-child { margin-bottom: 0; }
.ts-op-foot { display: flex; justify-content: flex-end; margin-top: 6rpx; }
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
.ts-op-del-inline { flex-shrink:0; margin-left:10rpx; border:0; background:transparent; color:#C9483D; font-size:23rpx; padding:6rpx 8rpx; font-family:inherit; }
.ts-op-del-inline:active { background:#FDEDEC; border-radius:8rpx; }
.ts-op-l2 { display: flex; align-items: baseline; gap: 12rpx; }
.ts-op-sum { flex: 1; min-width: 0; font-size: 26rpx; color: #7A756E; line-height: 1.5; word-break: break-all;
  overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; }
.ts-ops.summary .ts-op-sum { font-size: 24rpx; color: #8A8F98; line-height: 1.45; }
.ts-op.open .ts-op-sum { -webkit-line-clamp: unset; color: #1f2329; font-size: 30rpx; }
.ts-op-toggle { flex-shrink: 0; font-size: 24rpx; color: #C76A00; font-weight: 600; }
.ts-op-actions { margin-top: 10rpx; text-align: right; }
.ts-op-actions .ts-op-del { font-size: 24rpx; color: #E74C3C; padding: 6rpx 10rpx; background: none; border: none; }
.ts-op-more { padding: 8rpx 0 0; font-size: 23rpx; color: #A0A6AE; text-align: center; }

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
.ts-input { flex-shrink: 0; display: flex; align-items: flex-end; gap: 14rpx; background: transparent; }
/* 补充意见输入框默认给足高度（约3行），别只留一行让人不敢展开写；输入更多会自增到 max-height */
.ts-input .ts-ta { min-height: 132rpx; }
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
.ts-voice-result-edit { width:100%; min-height:180rpx; max-height:280rpx; box-sizing:border-box; resize:none; border:2rpx solid #D7DEE6; border-radius:16rpx; background:#fff; padding:18rpx 20rpx; font-family:inherit; outline:none; }
.ts-voice-result-edit:focus { border-color:#4B8FD8; box-shadow:0 0 0 5rpx rgba(75,143,216,0.10); }
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
.ts-nav-row { flex-shrink: 0; display: flex; align-items: center; gap: 14rpx; margin-top: 20rpx; padding-top:18rpx; border-top:2rpx solid #EEF1F4; }
.ts-nav-btn { box-sizing: border-box; border: 2rpx solid #D8DBE0; border-radius: 16rpx; background: #F7F8FA; color: #444; font-size: 29rpx; font-weight: 700; padding: 22rpx 34rpx; }
.ts-nav-btn:active { background: #ECEEF1; }
.ts-op-collapse { flex-shrink: 0; border: 2rpx solid #C7D4E2; background: #F4F8FC; color: #3F566E; font-size: 28rpx; font-weight: 800; padding: 16rpx 28rpx; border-radius: 999rpx; font-family: inherit; box-shadow: 0 4rpx 10rpx rgba(63,86,110,0.08); }
.ts-op-collapse:active { background: #E8F0F8; border-color: #9FB4C9; color: #26394D; }
.ts-op-collapse.warm { border-color: #E7B36A; background: #FFF4E6; color: #A85800; box-shadow: 0 5rpx 14rpx rgba(168,88,0,0.12); }
.ts-op-collapse.warm:active { background: #FBE7CC; border-color: #D88900; color: #874600; }
/* 上一个议题：规整的白底描边次要按钮，靠左；把右侧主按钮顶到最右 */
.ts-nav-btn.prev { margin-right: auto; background: #fff; border-color: #D8DBE0; color: #55585E; padding: 18rpx 30rpx; }
.ts-nav-btn.prev:active { background: #EEF0F3; color: #3A3F47; }
/* 返回：与「上一个议题」同款白底描边次要按钮，靠左，把右侧主按钮顶到最右 */
.ts-nav-btn.back { margin-right: auto; background: #fff; border-color: #D8DBE0; color: #55585E; padding: 18rpx 34rpx; }
.ts-nav-btn.back:active { background: #EEF0F3; color: #3A3F47; }
.ts-next-action { margin-left:auto; display:flex; align-items:center; padding:10rpx; border:2rpx solid #B9D8D5; background:#F0FAF8; border-radius:22rpx; box-shadow:0 6rpx 18rpx rgba(15,118,110,0.10); }
.ts-next-action.disabled { border-color:#E1E5EA; background:#F6F7F9; box-shadow:none; }
.ts-nav-btn.next { min-width:0; padding:18rpx 28rpx 18rpx 32rpx; border-radius:16rpx; background:#0F766E; border-color:#0F766E; color:#fff; font-size:30rpx; box-shadow:none; }
.ts-nav-btn.next:active { background:#0B5F59; border-color:#0B5F59; }
.ts-nav-btn.next[disabled] { background:#D8DDE3; border-color:#D8DDE3; color:#8B949E; }
.ts-next-arrow { font-size:34rpx; line-height:1; margin-left:4rpx; }
/* 最后一个议题的「完成」：绿色实心(收尾/成功语义)，同样去掉发光 */
.ts-nav-btn.done { margin-left:auto; background: #1F9D57; border-color: #1F9D57; color: #fff; box-shadow: 0 4rpx 12rpx rgba(31,157,87,0.22); }
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
