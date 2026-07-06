<template>
  <div v-if="topic" class="ts-mask" @click="$emit('close')">
    <div class="ts-sheet" :class="{ 'is-notice': topic.type === 'notice' }" @click.stop>
      <div class="ts-handle"></div>
      <div class="ts-head">
        <span class="ts-titlewrap"><span class="ts-title">{{ topic.title }}</span><span class="ts-tag" :class="tagClass">{{ tagLabel }}</span></span>
        <span class="ts-close" @click="$emit('close')">×</span>
      </div>

      <!-- 可滚动区：表决 + 意见汇总（输入框固定在底部，这里滚动看更多意见） -->
      <div class="ts-scroll">
      <!-- 表决区（仅表决类议题）：分「我的表决」和「全体表决情况」两块，避免个人/全体状态挤在一起 -->
      <div v-if="topic.voteRequired" class="ts-vote">
        <!-- ① 我的表决：投票按钮（按钮含义自明，不加标签）+ 我的状态 -->
        <div class="ts-vote-mine">
          <!-- 简单表决：三颗大按钮；已投后锁定高亮 -->
          <template v-if="(topic.decisionType || 'simple') !== 'multi_choice'">
            <div class="ts-vote-btns">
              <button class="ts-vote-btn agree" :class="{ on: topic.myVote === 'for_vote', off: lockedOther('for_vote') }" @click="castVote('for_vote')">同意</button>
              <button class="ts-vote-btn against" :class="{ on: topic.myVote === 'against', off: lockedOther('against') }" @click="castVote('against')">不同意</button>
              <button class="ts-vote-btn abstain" :class="{ on: topic.myVote === 'abstain', off: lockedOther('abstain') }" @click="castVote('abstain')">弃权</button>
            </div>
          </template>
          <!-- 多选项表决：选项行（右侧票数=全体该项得票） -->
          <template v-else>
            <div class="ts-opt" v-for="o in (topic.options || [])" :key="o.id"
                 :class="{ on: String(topic.myVote) === String(o.id), off: topic.myVote && String(topic.myVote) !== String(o.id) }"
                 @click="castVote(null, o)">
              <span class="ts-opt-label">{{ o.label }}</span>
              <span class="ts-opt-votes" v-if="o.votes != null">{{ o.votes }} 票</span>
            </div>
          </template>
          <!-- 已投后不再显示文字提示：按钮已高亮锁定，含义自明 -->
          <div v-if="!topic.myVote && !interactive" class="ts-vote-hint">会议进行中才可表决</div>
          <div v-else-if="!topic.myVote && !signedIn" class="ts-vote-hint">签到后即可表决</div>
        </div>

        <!-- ② 全体表决情况：汇总计票，独立浅底卡片；与个人区拉开距离，避免误认成个人结果 -->
        <div class="ts-vote-all">
          <div class="ts-tally">
            <span class="ts-tally-scope">全体</span>
            <span class="ts-tally-total">已表决 {{ topic.voted != null ? topic.voted : 0 }}/{{ topic.total || 0 }}</span>
            <template v-if="(topic.decisionType || 'simple') !== 'multi_choice'">
              <span class="ts-tally-stat agree">同意 {{ topic.forVotes || 0 }}</span>
              <span class="ts-tally-stat against">不同意 {{ topic.agVotes || 0 }}</span>
              <span class="ts-tally-stat abstain">弃权 {{ topic.abVotes || 0 }}</span>
            </template>
          </div>
        </div>
      </div>

      <!-- 通报区（仅通报类议题）：展示通知正文 + 已通报状态 + 「已宣读」 -->
      <div v-if="topic.type === 'notice'" class="ts-notice">
        <div class="ts-notice-label">通知内容</div>
        <div class="ts-notice-body">{{ topic.content || '（暂无通知正文）' }}</div>
        <div class="ts-notice-foot">
          <span class="ts-notice-status" :class="{ done: topic.notified }">{{ topic.notified ? '✓ 已通报' : '待通报' }}</span>
          <button v-if="interactive && signedIn && !topic.notified" class="ts-notice-read" @click="markNoticeRead">已宣读</button>
        </div>
      </div>

      <!-- 意见区 -->
      <div class="ts-ops">
        <div class="ts-ops-head">意见汇总<span v-if="opinions.length">（{{ opinions.length }}）</span></div>
        <div v-if="loading" class="ts-empty">加载中…</div>
        <div v-else-if="!opinions.length" class="ts-empty">还没有人发表意见</div>
        <div v-else class="ts-op" v-for="op in opinions" :key="op.id">
          <div class="ts-op-meta">
            <span class="ts-op-name">{{ op.name }}</span>
            <span v-if="op.role" class="ts-op-role">{{ op.role }}</span>
            <span class="ts-op-src" :class="op.source">{{ srcLabel(op.source) }}</span>
            <span v-if="op.claimable" class="ts-op-claim-tag" :class="{ on: claimShowId === op.id }"
                  @click="toggleClaimRow(op)">未认领</span>
            <span class="ts-op-time">{{ fmtTime(op.createdAt) }}</span>
            <span v-if="op.canDelete" class="ts-op-del" @click="removeOpinion(op)">删除</span>
          </div>
          <div class="ts-op-content">{{ op.content }}</div>
          <!-- AI 从现场发言提炼、还没归属到人：认领按钮默认藏着，点"未认领"标签才展开 -->
          <div v-if="op.claimable && claimShowId === op.id" class="ts-op-claimrow">
            <button class="ts-op-claim-btn" @click="claimOpinion(op)">🙋 是我说的</button>
          </div>
        </div>
      </div>
      </div><!-- /ts-scroll -->

      <!-- 输入区：固定在弹层底部 -->
      <template v-if="interactive && signedIn">
        <!-- 语音条：录音中 / 识别中（识别完文字回到对应输入框，可改再发表） -->
        <div v-if="voiceOn" class="ts-voicebar">
          <template v-if="!voiceBusy">
            <span class="ts-voice-dot"></span>
            <span class="ts-voice-txt">正在听你说… {{ recTimeText }}</span>
            <button class="ts-voice-cancel" @click="cancelVoice">取消</button>
            <button class="ts-voice-done" @click="finishVoice">说完了</button>
          </template>
          <template v-else>
            <span class="ts-voice-txt busy">正在把语音变成文字…</span>
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
          </div>
        </div>
        <!-- 常规输入行 + AI 助手行 -->
        <template v-else>
          <div class="ts-input">
            <textarea v-model="draft" class="ts-ta" rows="1" placeholder="说点什么…" @input="autoGrow" ref="taEl"></textarea>
            <button class="ts-send" :disabled="!draft.trim() || sending" @click="submitOpinion">发表</button>
          </div>
          <div class="ts-ai-row">
            <button class="ts-mic" @click="startVoice('draft')">🎤</button>
            <button v-if="draft.trim()" class="ts-ai-btn" :disabled="aiBusy" @click="polishByAi">{{ aiBusy ? '✨ AI 正在润色…' : '✨ AI 帮我润色' }}</button>
            <button v-else class="ts-ai-btn" :disabled="aiBusy" @click="onHelpWrite">{{ helpWriteLabel }}</button>
            <span v-if="aiTokens" class="ts-ai-token">本次消耗 {{ aiTokens.toLocaleString() }} token</span>
          </div>
        </template>
      </template>
      <div v-else-if="interactive && !signedIn" class="ts-input-hint">签到后可发表意见</div>

      <!-- 上一个 / 下一个议题：处理完当前议题直接切换，不用先关弹层 -->
      <div v-if="hasPrev || hasNext" class="ts-nav-row">
        <button v-if="hasPrev" class="ts-nav-btn" @click="$emit('prev')">‹ 上一个议题</button>
        <button v-if="hasNext" class="ts-nav-btn" @click="$emit('next')">下一个议题 ›</button>
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

const tagClass = computed(() => {
  const t = props.topic && props.topic.type
  return t === 'decision' ? 'vote' : (t === 'notice' ? 'notice' : 'discuss')
})
const tagLabel = computed(() => {
  const t = props.topic && props.topic.type
  return t === 'decision' ? '表决' : (t === 'notice' ? '通报' : '讨论')
})

// ── 语音输入意见：useRecorder 录音 → 后端 ASR 转文字 → 填入输入框（可改）→ 发表 ──
// 注意声明须在下面 immediate watch 之前（watch 首跑就会调 cancelVoice）
const rec = useRecorder()
const recTimeText = rec.timeText
const voiceOn = ref(false)      // 语音条显示中（录音/识别）
const voiceBusy = ref(false)    // 识别中
const voiceTarget = ref('draft') // 识别结果回填目标：draft=意见输入框 / helper=小助手输入框
const draftFromVoice = ref(false) // 本条草稿是否来自语音（发表时 source 用 voice）

// ── AI 助手：润色已有意见 / 小助手代拟发言 ──
const helperOn = ref(false)     // 小助手面板
const helperDraft = ref('')     // 小助手里的"随便说说"
const aiBusy = ref(false)       // AI 生成中
const aiTokens = ref(0)         // 最近一次 AI 消耗 token（低调展示）
const polishUndo = ref(null)    // 润色前的原文（可还原）；null=没有可还原的
const claimShowId = ref(null)   // 认领按钮默认藏着：点"未认领"标签展开的那条意见 id（声明须在下方 immediate watch 之前）

// 打开（topic 切换/出现）时拉取本议题意见；关闭/切议题时收掉语音条（释放麦克风）和 AI 状态
watch(() => props.topic && props.topic.id, (id) => {
  cancelVoice()
  helperOn.value = false; helperDraft.value = ''; aiBusy.value = false
  aiTokens.value = 0; polishUndo.value = null; claimShowId.value = null
  if (id) { draft.value = ''; draftFromVoice.value = false; loadOpinions(); recordNoticeView() }
}, { immediate: true })
onBeforeUnmount(cancelVoice)

// 打开通报类议题即记录"本人已看过"；全体已签到委员都看过时后端会自动标记已通报。
async function recordNoticeView() {
  const t = props.topic
  if (!t || t.type !== 'notice') return
  if (!props.interactive || !props.signedIn) return
  if (t.viewedByMe) return // 已看过，不再重复上报
  try {
    await api.committeeNoticeView(props.meetingId, t.id)
    emit('changed') // 刷新状态（可能刚好凑齐"全体已看"→已通报）
  } catch (e) { /* 静默 */ }
}

// 「已宣读」：任一委员/主任点了 → 该通报议题标记已通报。
async function markNoticeRead() {
  const t = props.topic
  if (!t || t.type !== 'notice') return
  try {
    await api.committeeNoticeRead(props.meetingId, t.id)
    toast({ title: '已标记为已通报', icon: 'success' })
    emit('changed')
  } catch (e) { toast({ title: (e && e.message) || '操作失败', icon: 'none' }) }
}

async function startVoice(target) {
  if (!rec.supported.value) { toast({ title: '当前浏览器不支持录音（需 HTTPS 且允许麦克风）', icon: 'none' }); return }
  try {
    await rec.start()
    voiceTarget.value = target === 'helper' ? 'helper' : 'draft'
    voiceOn.value = true
  } catch (e) {
    // getUserMedia 的报错是英文（如 Permission denied），统一换成看得懂的提示
    toast({ title: '无法使用麦克风，请在浏览器允许麦克风后再试', icon: 'none' })
  }
}
function cancelVoice() {
  rec.reset()
  voiceOn.value = false
  voiceBusy.value = false
}
async function finishVoice() {
  if (voiceBusy.value) return
  voiceBusy.value = true
  try {
    const out = await rec.stop()
    if (!out || !out.blob || out.blob.size === 0) { toast({ title: '没有录到声音，再试一次', icon: 'none' }); return }
    const file = new File([out.blob], 'voice.' + out.ext, { type: out.mimeType })
    const res = await api.committeeVoiceToText(props.meetingId, file)
    const text = applyHotwords(((res && res.text) || '').trim())
    if (!text) { toast({ title: '没有识别到文字，请再说一次', icon: 'none' }); return }
    if (voiceTarget.value === 'helper') {
      helperDraft.value = helperDraft.value ? (helperDraft.value + text) : text
    } else {
      draft.value = draft.value ? (draft.value + text) : text
      nextTick(autoGrow)
    }
    draftFromVoice.value = true
  } catch (e) { /* uploadFile/request 已 toast */ } finally {
    rec.reset()
    voiceOn.value = false
    voiceBusy.value = false
  }
}

// ── AI 润色 / 代拟 ──
// AI 完成后弹卡片：告知已生成、耗时、消耗 token
function showAiDoneCard(mode, t0, tokens) {
  const sec = Math.max(0.1, (Date.now() - t0) / 1000).toFixed(1)
  showModal({
    title: '✨ AI 已帮你' + (mode === 'polish' ? '润色意见' : '生成意见'),
    // 先耗时/token，再告知已填好；size:large → 大字纯黑
    content: '耗时 ' + sec + ' 秒 · 消耗 ' + (Number(tokens) || 0).toLocaleString() + ' token\n\n内容已经帮你填好，可修改后发表。',
    size: 'large',
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
// 已表决时的按钮文案：提示会按本人表决直接生成
const helpWriteLabel = computed(() => {
  if (aiBusy.value) return 'AI 正在写…'
  return (props.topic && props.topic.myVote) ? '✨ 按我的表决，帮我写发言' : '✨ 不会说？AI 帮我写'
})
// 由本人表决结果生成一句"口头表态"喂给 draft（后端已带委员身份 speaker_role，AI 据此写正式发言）
function voteStanceSeed() {
  const t = props.topic
  if (!t || !t.myVote) return ''
  if ((t.decisionType || 'simple') === 'multi_choice') {
    const opt = (t.options || []).find(o => String(o.id) === String(t.myVote))
    return opt ? ('我在这个议题上选择了「' + opt.label + '」，请据此帮我写一段简短的表态发言。') : ''
  }
  if (t.myVote === 'for_vote') return '我对这个议题投了赞成票，总体认同这个方案，支持通过。'
  if (t.myVote === 'against') return '我对这个议题投了反对票，对这个方案还有顾虑，暂不赞成。'
  if (t.myVote === 'abstain') return '我对这个议题投了弃权票，还想再多了解一些情况，暂不表态。'
  return ''
}
// 表决后一键代拟：不用输入，按身份+表决态度生成发言，填入输入框
async function draftFromVote() {
  if (aiBusy.value) return
  const seed = voteStanceSeed()
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

function srcLabel(s) { return s === 'voice' ? '语音' : (s === 'ai' ? '现场·AI' : '打字') }
function fmtTime(iso) { return iso && iso.length >= 16 ? iso.slice(11, 16) : '' }
function lockedOther(choice) { return !!props.topic.myVote && props.topic.myVote !== choice }

function autoGrow() {
  const el = taEl.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

async function castVote(choice, option) {
  const t = props.topic
  if (!t || t.myVote) return
  if (!props.interactive) { toast({ title: '会议进行中才可表决', icon: 'none' }); return }
  if (!props.signedIn) { toast({ title: '请先完成签到', icon: 'none' }); return }
  const label = option ? option.label : (choice === 'for_vote' ? '同意' : choice === 'against' ? '不同意' : '弃权')
  const res = await showModal({
    title: '确认你的选择',
    content: '你选择了「' + label + '」。确认后这项不能修改。',
    confirmText: '确认选择',
    cancelText: '再看看'
  })
  if (!res.confirm) return
  try {
    await api.committeeVote(props.meetingId, t.id, option ? null : choice, option ? option.id : null)
    toast({ title: '已表决', icon: 'success' })
    emit('changed')
  } catch (e) { /* 已 toast */ }
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
.ts-titlewrap { flex: 1; min-width: 0; }
/* 中间可滚动区：意见多了在这里滚，输入框始终露在底部 */
.ts-scroll { flex: 1 1 auto; min-height: 0; overflow-y: auto; }
.ts-title { font-size: 34rpx; font-weight: 700; color: #1f2329; line-height: 1.4; }
/* 表决标签：紧跟标题之后（内联），不再顶到右上角 */
.ts-tag { display: inline-block; margin-left: 12rpx; vertical-align: middle; font-size: 24rpx; padding: 4rpx 14rpx; border-radius: 10rpx; background: #F2F2F4; color: #666; white-space: nowrap; }
.ts-tag.vote { background: #FFF3E0; color: #E67E22; }
.ts-close { flex-shrink: 0; width: 56rpx; height: 56rpx; line-height: 52rpx; text-align: center; font-size: 44rpx; color: #999; margin: -8rpx -12rpx 0 0; }

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
.ts-vote-btn.abstain.on { background: #9AA0A6; border-color: #9AA0A6; color: #fff; box-shadow: 0 0 0 4rpx rgba(154,160,166,0.26); }
.ts-vote-btn.off { opacity: 0.35; }
.ts-vote-btn:active { transform: scale(0.97); }
.ts-opt { display: flex; align-items: center; justify-content: space-between; border: 2rpx solid #D8DBE0; border-radius: 16rpx; padding: 22rpx 24rpx; margin-bottom: 14rpx; font-size: 32rpx; color: #333; }
.ts-opt.on { background: #FFF6E8; border-color: #FFA800; color: #9A6A00; font-weight: 700; }
.ts-opt.off { opacity: 0.4; }
.ts-opt-votes { font-size: 26rpx; color: #999; }
/* 全体计票：独立浅底卡片，与个人投票区拉开距离(避免误认成个人结果)；"全体"前缀点明范围 */
.ts-vote-all { margin-top: 44rpx; }
.ts-tally { display: flex; flex-wrap: wrap; align-items: center; gap: 10rpx 18rpx; background: #F6F7F9; border-radius: 14rpx; padding: 18rpx 22rpx; font-size: 26rpx; color: #666; }
.ts-tally-scope { font-weight: 700; color: #6B7078; background: #E9EBEF; border-radius: 8rpx; padding: 2rpx 12rpx; font-size: 24rpx; }
.ts-tally-total { font-weight: 700; color: #1f2329; }
.ts-tally-stat { font-size: 26rpx; }
.ts-tally-stat.agree { color: #2E7D32; }
.ts-tally-stat.against { color: #C0392B; }
.ts-tally-stat.abstain { color: #7A7F87; }
.ts-vote-hint { font-size: 24rpx; color: #9AA0A6; margin-top: 10rpx; }
.ts-vote-hint.mine { color: #2E7D32; font-weight: 600; }

/* 通报类议题：通知正文 + 已通报状态 */
.ts-notice { background: #FFFBF3; border: 2rpx solid #F1E2C6; border-radius: 16rpx; padding: 22rpx 22rpx 18rpx; margin-bottom: 18rpx; }
.ts-notice-label { font-size: 26rpx; font-weight: 700; color: #B06A00; margin-bottom: 12rpx; }
.ts-notice-body { font-size: 32rpx; color: #1f2329; line-height: 1.7; white-space: pre-wrap; }
.ts-notice-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 18rpx; }
.ts-notice-status { font-size: 26rpx; color: #9AA0A6; font-weight: 600; }
.ts-notice-status.done { color: #2E7D32; }
.ts-notice-read { border: none; background: #FFA800; color: #fff; font-size: 28rpx; font-weight: 700; border-radius: 14rpx; padding: 14rpx 34rpx; }
.ts-notice-read:active { background: #F09600; }
.ts-ops { border-top: 2rpx solid #F2F2F4; padding-top: 18rpx; }
.ts-ops-head { font-size: 30rpx; font-weight: 700; color: #1f2329; margin-bottom: 14rpx; }
.ts-empty { font-size: 28rpx; color: #9AA0A6; padding: 18rpx 0 24rpx; }
.ts-op { padding: 12rpx 0 16rpx; border-bottom: 2rpx solid #F7F7F8; }
.ts-op:last-child { border-bottom: 0; }
.ts-op-meta { display: flex; align-items: center; gap: 10rpx; margin-bottom: 6rpx; flex-wrap: wrap; }
.ts-op-name { font-size: 28rpx; font-weight: 600; color: #333; }
.ts-op-role { font-size: 22rpx; color: #999; }
.ts-op-src { font-size: 22rpx; padding: 2rpx 10rpx; border-radius: 8rpx; background: #EAF2FD; color: #1A73E8; }
.ts-op-src.ai { background: #FFF3E0; color: #C77700; }
.ts-op-time { font-size: 22rpx; color: #BBB; margin-left: auto; }
.ts-op-del { font-size: 24rpx; color: #E74C3C; padding: 4rpx 8rpx; }
.ts-op-content { font-size: 30rpx; color: #1f2329; line-height: 1.55; word-break: break-all; }

/* 通报类议题：标题/正文加大两号、间距拉大，意见汇总区收小（通报一般不讨论） */
.ts-sheet.is-notice .ts-title { font-size: 42rpx; line-height: 1.5; }
.ts-sheet.is-notice .ts-notice { padding: 30rpx 26rpx 26rpx; margin-bottom: 26rpx; }
.ts-sheet.is-notice .ts-notice-label { font-size: 30rpx; margin-bottom: 18rpx; }
.ts-sheet.is-notice .ts-notice-body { font-size: 40rpx; line-height: 1.95; }
.ts-sheet.is-notice .ts-notice-foot { margin-top: 28rpx; }
.ts-sheet.is-notice .ts-notice-status { font-size: 28rpx; }
.ts-sheet.is-notice .ts-ops { padding-top: 14rpx; }
.ts-sheet.is-notice .ts-ops-head { font-size: 26rpx; color: #9AA0A6; margin-bottom: 10rpx; }
.ts-sheet.is-notice .ts-op-name { font-size: 26rpx; }
.ts-sheet.is-notice .ts-op-content { font-size: 27rpx; line-height: 1.5; }
.ts-sheet.is-notice .ts-empty { font-size: 25rpx; padding: 12rpx 0 16rpx; }
.ts-op-claim-tag { font-size: 22rpx; padding: 2rpx 10rpx; border-radius: 8rpx; background: #FDECEA; color: #C0392B; cursor: pointer; }
.ts-op-claim-tag:active { opacity: 0.7; }
.ts-op-claim-tag.on { background: #C0392B; color: #fff; }
.ts-op-claimrow { margin-top: 10rpx; }
.ts-op-claim-btn { border: 2rpx solid #F0D9B8; border-radius: 14rpx; background: #FFF9F0; color: #B06A00; font-size: 26rpx; padding: 10rpx 22rpx; }
.ts-op-claim-btn:active { background: #FFF1DC; }

.ts-input { flex-shrink: 0; display: flex; align-items: flex-end; gap: 14rpx; padding-top: 16rpx; border-top: 2rpx solid #F2F2F4; margin-top: 8rpx; background: #fff; }
.ts-mic { flex-shrink: 0; width: 84rpx; height: 84rpx; border: 2rpx solid #D8DBE0; border-radius: 50%; background: #fff; font-size: 40rpx; line-height: 1; padding: 0; }
.ts-mic:active { background: #FFF6E8; border-color: #FFA800; }
/* 语音条：录音中/识别中占满输入区，大按钮 */
.ts-voicebar { flex-shrink: 0; display: flex; align-items: center; gap: 16rpx; padding: 20rpx 4rpx 8rpx; border-top: 2rpx solid #F2F2F4; margin-top: 8rpx; background: #fff; min-height: 96rpx; box-sizing: border-box; }
.ts-voice-dot { flex-shrink: 0; width: 20rpx; height: 20rpx; border-radius: 50%; background: #E74C3C; animation: ts-blink 1s infinite; }
@keyframes ts-blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.25; } }
.ts-voice-txt { flex: 1; min-width: 0; font-size: 30rpx; color: #333; }
.ts-voice-txt.busy { text-align: center; color: #E8890C; font-weight: 600; }
.ts-voice-cancel { flex-shrink: 0; border: 2rpx solid #D8DBE0; border-radius: 16rpx; background: #fff; color: #666; font-size: 30rpx; padding: 16rpx 26rpx; }
.ts-voice-done { flex-shrink: 0; border: 0; border-radius: 16rpx; background: var(--c-primary-dark, #E8890C); color: #fff; font-size: 30rpx; font-weight: 700; padding: 18rpx 34rpx; }
.ts-ta { flex: 1; border: 2rpx solid #D8DBE0; border-radius: 18rpx; padding: 16rpx 20rpx; font-size: 30rpx; line-height: 1.4; resize: none; box-sizing: border-box; max-height: 160px; font-family: inherit; }
.ts-ta:focus { border-color: #FFA800; outline: none; }
.ts-send { flex-shrink: 0; background: var(--c-primary-dark, #E8890C); color: #fff; border: 0; border-radius: 18rpx; font-size: 30rpx; font-weight: 700; padding: 18rpx 34rpx; }
.ts-send[disabled] { background: #E3D5C3; }
.ts-input-hint { flex-shrink: 0; font-size: 26rpx; color: #9AA0A6; text-align: center; padding: 16rpx 0 4rpx; border-top: 2rpx solid #F2F2F4; margin-top: 8rpx; }
/* 上一个/下一个议题：固定在弹层最底部，一行两键（缺一个时另一个占满） */
.ts-nav-row { flex-shrink: 0; display: flex; gap: 14rpx; margin-top: 14rpx; }
.ts-nav-btn { flex: 1; box-sizing: border-box; border: 2rpx solid #D8DBE0; border-radius: 18rpx; background: #F7F8FA; color: #444; font-size: 30rpx; font-weight: 600; padding: 20rpx 0; }
.ts-nav-btn:active { background: #ECEEF1; }
/* 最后一个议题的「完成」：填充橙色，作为收尾动作更醒目 */
.ts-nav-btn.done { background: #FFA800; border-color: #FFA800; color: #fff; }
.ts-nav-btn.done:active { background: #F09600; }

/* AI 助手行：润色 / 帮我写 入口 + 还原 + token 低调提示 */
.ts-ai-row { flex-shrink: 0; display: flex; align-items: center; gap: 16rpx; padding: 12rpx 2rpx 2rpx; background: #fff; }
.ts-ai-btn { border: 2rpx solid #F0D9B8; border-radius: 14rpx; background: #FFF9F0; color: #B06A00; font-size: 26rpx; padding: 12rpx 22rpx; }
.ts-ai-btn:active { background: #FFF1DC; }
.ts-ai-btn[disabled] { opacity: 0.55; }
.ts-ai-undo { font-size: 26rpx; color: #1A73E8; text-decoration: underline; padding: 4rpx; }
.ts-ai-token { margin-left: auto; font-size: 22rpx; color: #C2C6CC; }

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
