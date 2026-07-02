<template>
  <div v-if="topic" class="ts-mask" @click="$emit('close')">
    <div class="ts-sheet" @click.stop>
      <div class="ts-handle"></div>
      <div class="ts-head">
        <span class="ts-title">{{ topic.title }}</span>
        <span class="ts-tag" :class="tagClass">{{ tagLabel }}</span>
        <span class="ts-close" @click="$emit('close')">×</span>
      </div>

      <!-- 表决区（仅表决类议题） -->
      <div v-if="topic.voteRequired" class="ts-vote">
        <!-- 简单表决：三颗大按钮；已投后锁定高亮 -->
        <template v-if="(topic.decisionType || 'simple') !== 'multi_choice'">
          <div class="ts-vote-btns">
            <button class="ts-vote-btn agree" :class="{ on: topic.myVote === 'for_vote', off: lockedOther('for_vote') }" @click="castVote('for_vote')">同意</button>
            <button class="ts-vote-btn against" :class="{ on: topic.myVote === 'against', off: lockedOther('against') }" @click="castVote('against')">不同意</button>
            <button class="ts-vote-btn abstain" :class="{ on: topic.myVote === 'abstain', off: lockedOther('abstain') }" @click="castVote('abstain')">弃权</button>
          </div>
        </template>
        <!-- 多选项表决：选项行 -->
        <template v-else>
          <div class="ts-opt" v-for="o in (topic.options || [])" :key="o.id"
               :class="{ on: String(topic.myVote) === String(o.id), off: topic.myVote && String(topic.myVote) !== String(o.id) }"
               @click="castVote(null, o)">
            <span class="ts-opt-label">{{ o.label }}</span>
            <span class="ts-opt-votes" v-if="o.votes != null">{{ o.votes }} 票</span>
          </div>
        </template>
        <div class="ts-tally">
          已表决 {{ topic.voted != null ? topic.voted : 0 }}/{{ topic.total || 0 }}
          <template v-if="(topic.decisionType || 'simple') !== 'multi_choice'">
            · 同意 {{ topic.forVotes || 0 }} · 不同意 {{ topic.agVotes || 0 }} · 弃权 {{ topic.abVotes || 0 }}
          </template>
        </div>
        <div v-if="topic.myVote" class="ts-vote-hint">已表决，不可更改</div>
        <div v-else-if="!interactive" class="ts-vote-hint">会议进行中才可表决</div>
        <div v-else-if="!signedIn" class="ts-vote-hint">签到后即可表决</div>
      </div>

      <!-- 意见区 -->
      <div class="ts-ops">
        <div class="ts-ops-head">意见<span v-if="opinions.length">（{{ opinions.length }}）</span></div>
        <div v-if="loading" class="ts-empty">加载中…</div>
        <div v-else-if="!opinions.length" class="ts-empty">还没有人发表意见</div>
        <div v-else class="ts-op" v-for="op in opinions" :key="op.id">
          <div class="ts-op-meta">
            <span class="ts-op-name">{{ op.name }}</span>
            <span v-if="op.role" class="ts-op-role">{{ op.role }}</span>
            <span class="ts-op-src" :class="op.source">{{ srcLabel(op.source) }}</span>
            <span class="ts-op-time">{{ fmtTime(op.createdAt) }}</span>
            <span v-if="op.canDelete" class="ts-op-del" @click="removeOpinion(op)">删除</span>
          </div>
          <div class="ts-op-content">{{ op.content }}</div>
        </div>
      </div>

      <!-- 输入区：会议进行中且已签到 -->
      <div v-if="interactive && signedIn" class="ts-input">
        <textarea v-model="draft" class="ts-ta" rows="1" placeholder="说点什么…" @input="autoGrow" ref="taEl"></textarea>
        <button class="ts-send" :disabled="!draft.trim() || sending" @click="submitOpinion">发表</button>
      </div>
      <div v-else-if="interactive && !signedIn" class="ts-input-hint">签到后可发表意见</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import api from '@/api'
import { toast, showModal } from '@/utils/ui'

const props = defineProps({
  meetingId: { type: [String, Number], required: true },
  topic: { type: Object, default: null },        // detail.record.topics 里的一项（TopicVO）
  interactive: { type: Boolean, default: false }, // 会议进行中（可表决/发言）
  signedIn: { type: Boolean, default: false },
  isChair: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'changed'])

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

// 打开（topic 切换/出现）时拉取本议题意见
watch(() => props.topic && props.topic.id, (id) => {
  if (id) { draft.value = ''; loadOpinions() }
}, { immediate: true })

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
  if (!props.signedIn) { toast({ title: '请先签到再表决', icon: 'none' }); return }
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
    const created = await api.committeeAddOpinion(props.meetingId, props.topic.id, content, 'text')
    opinions.value = opinions.value.concat([created])
    draft.value = ''
    if (taEl.value) taEl.value.style.height = 'auto'
    emit('changed')
  } catch (e) { /* 已 toast */ } finally { sending.value = false }
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
.ts-sheet { background: #fff; border-radius: 28rpx 28rpx 0 0; padding: 14rpx 30rpx calc(24rpx + env(safe-area-inset-bottom)); max-height: 78vh; overflow-y: auto; }
.ts-handle { width: 72rpx; height: 8rpx; border-radius: 4rpx; background: #E4E6EA; margin: 0 auto 16rpx; }
.ts-head { display: flex; align-items: flex-start; gap: 12rpx; margin-bottom: 20rpx; }
.ts-title { flex: 1; font-size: 34rpx; font-weight: 700; color: #1f2329; line-height: 1.4; }
.ts-tag { flex-shrink: 0; font-size: 24rpx; padding: 4rpx 14rpx; border-radius: 10rpx; background: #F2F2F4; color: #666; margin-top: 4rpx; }
.ts-tag.vote { background: #FFF3E0; color: #E67E22; }
.ts-close { flex-shrink: 0; width: 56rpx; height: 56rpx; line-height: 52rpx; text-align: center; font-size: 44rpx; color: #999; margin: -8rpx -12rpx 0 0; }

.ts-vote { margin-bottom: 24rpx; }
.ts-vote-btns { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 14rpx; }
.ts-vote-btn { border: 2rpx solid #D8DBE0; border-radius: 16rpx; background: #fff; color: #444; font-size: 32rpx; font-weight: 700; padding: 22rpx 0; }
.ts-vote-btn.agree.on { background: #EAF6E5; border-color: #52A344; color: #2E7D32; }
.ts-vote-btn.against.on { background: #FDECEA; border-color: #E74C3C; color: #C0392B; }
.ts-vote-btn.abstain.on { background: #F2F2F4; border-color: #9AA0A6; color: #555; }
.ts-vote-btn.off { opacity: 0.35; }
.ts-vote-btn:active { transform: scale(0.97); }
.ts-opt { display: flex; align-items: center; justify-content: space-between; border: 2rpx solid #D8DBE0; border-radius: 16rpx; padding: 22rpx 24rpx; margin-bottom: 14rpx; font-size: 32rpx; color: #333; }
.ts-opt.on { background: #FFF6E8; border-color: #FFA800; color: #9A6A00; font-weight: 700; }
.ts-opt.off { opacity: 0.4; }
.ts-opt-votes { font-size: 26rpx; color: #999; }
.ts-tally { font-size: 26rpx; color: #666; margin-top: 14rpx; }
.ts-vote-hint { font-size: 24rpx; color: #9AA0A6; margin-top: 6rpx; }

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

.ts-input { display: flex; align-items: flex-end; gap: 14rpx; padding-top: 16rpx; border-top: 2rpx solid #F2F2F4; margin-top: 8rpx; position: sticky; bottom: 0; background: #fff; }
.ts-ta { flex: 1; border: 2rpx solid #D8DBE0; border-radius: 18rpx; padding: 16rpx 20rpx; font-size: 30rpx; line-height: 1.4; resize: none; box-sizing: border-box; max-height: 160px; font-family: inherit; }
.ts-ta:focus { border-color: #FFA800; outline: none; }
.ts-send { flex-shrink: 0; background: var(--c-primary-dark, #E8890C); color: #fff; border: 0; border-radius: 18rpx; font-size: 30rpx; font-weight: 700; padding: 18rpx 34rpx; }
.ts-send[disabled] { background: #E3D5C3; }
.ts-input-hint { font-size: 26rpx; color: #9AA0A6; text-align: center; padding: 16rpx 0 4rpx; border-top: 2rpx solid #F2F2F4; margin-top: 8rpx; }
</style>
