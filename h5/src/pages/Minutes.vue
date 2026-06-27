<template>
  <div class="page minutes-page">
    <PageNav title="会议纪要" style="display:block;margin:-24rpx -24rpx 0;" />

    <!-- AI 工作中：纪要页 gen=1 大模型生成等待时显"生成纪要"态；完成后出确认按钮（覆盖原"生成中"提示） -->
    <AiWorkingOverlay :active="aiGenerating" phase="gen" />

    <div v-if="accessDenied" class="access-card">
      <div class="access-icon">!</div>
      <span class="access-title">{{ accessTitle }}</span>
      <span class="access-text">{{ accessText }}</span>
    </div>

    <!-- AI 生成中：整屏只显示"生成中"，不展示任何已有/合成内容 -->
    <div v-else-if="aiGenerating" class="doc">
      <div class="ai-gen-banner">
        <div class="ai-gen-dot"></div>
        <span class="ai-gen-text">AI 正在生成会议纪要，请稍候…</span>
      </div>
      <span class="gen-hint">通常需要十几秒到一分钟，生成后会自动显示，请勿离开本页面。</span>
    </div>

    <!-- 首次创建纪要（手写 / 尚无正式纪要时编辑）：整屏只显示干净编辑器，不预填合成内容 -->
    <div v-else-if="editMode && !hasServerMinutes && !isOwner" class="doc">
      <span class="doc-title">业主委员会会议纪要</span>
      <div class="edit-block">
        <textarea
          class="edit-textarea"
          v-model="editText"
          placeholder="请输入会议纪要内容…（也可返回上一页改用 AI 自动生成）"
        ></textarea>
        <div class="edit-actions">
          <button class="btn btn-ghost" @click="cancelEdit">取消</button>
          <button class="btn btn-primary" @click="saveEdit">保存并确认</button>
        </div>
      </div>
    </div>

    <!-- 业主大会纪要（纯文本） -->
    <div v-else-if="isOwner && plainText" class="doc">
      <span class="doc-title">业主大会会议纪要</span>
      <span class="doc-body">{{ plainText }}</span>
      <div v-if="editMode" class="edit-block">
        <textarea
          class="edit-textarea"
          v-model="editText"
          placeholder="编辑纪要全文..."
        ></textarea>
        <div class="edit-actions">
          <button class="btn btn-ghost" @click="cancelEdit">取消</button>
          <button class="btn btn-primary" @click="saveEdit">保存纪要</button>
        </div>
      </div>
      <div class="minutes-actions" v-if="!aiGenerating">
        <button v-if="canEditMinutes && minutes && minutes.draft" class="end-meeting-btn" @click="endMeetingFromMinutes">确认纪要无误，结束会议</button>
        <button v-if="canEditMinutes && !editMode" class="edit-minutes-btn" :class="{ ghost: minutes && minutes.draft }" @click="startEdit">编辑纪要</button>
        <button class="copy-btn" @click="copyText">复制全文</button>
      </div>
    </div>

    <!-- 业委会纪要：优先展示后端保存的大模型/人工编辑正文 -->
    <div v-else-if="!isOwner && hasServerMinutes && plainText" class="doc">
      <div v-if="minutes && minutes.draft" class="draft-banner">草稿 · 会议进行中，结束后定稿</div>
      <span class="doc-title">业主委员会会议纪要</span>
      <span class="doc-body">{{ plainText }}</span>
      <div v-if="editMode" class="edit-block">
        <span v-if="reviseMode" class="revise-hint">修订模式：原归档版本将保留，本次修改作为新版本生效，提交时需填写修订原因。</span>
        <textarea
          class="edit-textarea"
          v-model="editText"
          placeholder="编辑纪要全文..."
        ></textarea>
        <div class="edit-actions">
          <button class="btn btn-ghost" @click="cancelEdit">取消</button>
          <button class="btn btn-primary" @click="saveEdit">{{ reviseMode ? '提交修订' : '保存并确认' }}</button>
        </div>
      </div>
      <div class="minutes-actions" v-if="!aiGenerating">
        <!-- 两个主操作：确认(结束会议) / 编辑(改完再确认结束) -->
        <button v-if="canEditMinutes && minutes && minutes.draft" class="end-meeting-btn" @click="endMeetingFromMinutes">确认纪要无误，结束会议</button>
        <button v-if="canEditMinutes && !editMode" class="edit-minutes-btn" @click="startEdit">编辑纪要</button>
        <!-- 次要操作：复制全文 / 待办 / 内部报告 / 修订 -->
        <div class="more-links">
          <span class="more-link" @click="copyText">复制全文</span>
          <span v-if="canViewInternalArtifacts" class="more-link primary-link" @click="viewTodoList">待办事项</span>
          <span v-if="canViewInternalArtifacts" class="more-link" @click="viewInternalTopicReport">内部议题报告</span>
          <span v-if="canReviseMinutes" class="more-link" @click="startRevise">发起修订</span>
          <span v-if="canReviseMinutes" class="more-link" @click="viewRevisions">修订历史</span>
        </div>
      </div>
    </div>

    <!-- 委员会纪要（结构化） -->
    <div v-else-if="minutes && !isOwner" class="doc">
      <!-- 草稿标识 -->
      <div v-if="minutes.draft" class="draft-banner">草稿 · 会议进行中，结束后定稿</div>

      <!-- 标题 -->
      <span class="doc-title">业主委员会会议纪要</span>

      <!-- 头部信息 -->
      <div class="doc-head">
        <div class="dh-row"><span class="dh-key">会议名称</span><span class="dh-val">{{ minutes.meetingTitle }}</span></div>
        <div class="dh-row"><span class="dh-key">会议时间</span><span class="dh-val">{{ minutes.meetingDate }} {{ minutes.meetingTime }}</span></div>
        <div class="dh-row"><span class="dh-key">会议地点</span><span class="dh-val">{{ minutes.location }}</span></div>
        <div class="dh-row"><span class="dh-key">主持人</span><span class="dh-val">{{ minutes.host }}</span></div>
        <div class="dh-row"><span class="dh-key">记录人</span><span class="dh-val">秘书小李</span></div>
      </div>

      <!-- 一、参会情况 -->
      <div class="doc-sec">
        <span class="sec-title">一、参会情况</span>
        <span class="sec-text">应到委员 <span class="b">{{ minutes.totalMembers }}</span> 人，实到 <span class="b">{{ minutes.presentCount }}</span> 人，<span :class="minutes.quorumMet ? 'tag-ok' : 'tag-no'">{{ minutes.quorumMet ? '已过半，达到法定人数' : '未过半，未达法定人数' }}</span>。</span>
        <span class="sec-text"><span class="sec-k">出席：</span>{{ minutes.presentNames }}</span>
        <span class="sec-text" v-if="minutes.absentNames"><span class="sec-k">缺席：</span>{{ minutes.absentNames }}</span>
        <span class="sec-text" v-if="minutes.juweiName"><span class="sec-k">列席：</span>{{ minutes.juweiName }}（居委会委员）</span>
      </div>

      <!-- 二、会议议题 -->
      <div class="doc-sec">
        <span class="sec-title">二、会议议题</span>
        <span class="sec-text">{{ minutes.description || '（无）' }}</span>
      </div>

      <!-- 三、表决情况 -->
      <div class="doc-sec" v-if="minutes.topics && minutes.topics.length">
        <span class="sec-title">三、表决情况</span>
        <div class="topic-item" v-for="(item, index) in minutes.topics" :key="item.id">
          <span class="ti-title">{{ index + 1 }}. <span class="ti-tag" :class="{ major: item.type === 'major' }">{{ item.type === 'major' ? '重大事项' : '决定事项' }}</span> {{ item.title }}</span>
          <span class="ti-result">赞成 <span class="b">{{ item.forVotes }}</span> · 反对 <span class="b">{{ item.agVotes }}</span> · 弃权 <span class="b">{{ item.abVotes }}</span>（赞成需≥{{ item.need }}）</span>
          <span class="ti-conclusion" :class="item.passed ? 'pass' : 'fail'">表决结果：{{ item.text }}</span>
        </div>
      </div>

      <!-- 四、参会确认 -->
      <div class="doc-sec">
        <span class="sec-title">{{ minutes.topics.length ? '四' : '三' }}、参会确认</span>
        <span class="sec-text">本次会议经 <span class="b">{{ minutes.presentCount }}/{{ minutes.totalMembers }}</span> 名委员确认参会。</span>
        <span class="sec-text" v-if="minutes.juweiName">重大事项 <span :class="minutes.juweiSigned ? 'tag-ok' : 'tag-no'">{{ minutes.juweiSigned ? '已' : '尚未' }}</span> 由居委会委员（{{ minutes.juweiName }}）签字。</span>
      </div>

      <!-- 五、会议结论 -->
      <div class="doc-sec">
        <span class="sec-title">{{ minutes.topics.length ? '五' : '四' }}、会议结论</span>
        <div class="conclusion-box" :class="minutes.conclusionLevel">
          <span class="cb-text">{{ minutes.conclusionText }}</span>
        </div>
        <div class="check-grid">
          <div class="cg-row"><span class="cg-label">会议有效性</span><span class="cg-value" :class="minutes.meetingValid ? 'ok' : 'bad'">{{ minutes.meetingValid ? '会议有效' : '会议无效' }}</span></div>
          <div class="cg-row"><span class="cg-label">记录完整性</span><span class="cg-value" :class="minutes.recordLevel === 'complete' ? 'ok' : minutes.recordLevel === 'minor' ? 'warn' : 'bad'">{{ minutes.recordText }}</span></div>
          <div class="cg-row"><span class="cg-label">决议有效性</span><span class="cg-value" :class="minutes.resolutionLevel === 'passed' || minutes.resolutionLevel === 'none' ? 'ok' : minutes.resolutionLevel === 'invalid' ? 'bad' : 'warn'">{{ minutes.resolutionText }}</span></div>
        </div>
        <div v-if="minutes.notes && minutes.notes.length" class="notes-list">
          <span v-for="(item, ni) in minutes.notes" :key="ni" class="note-item">· {{ item }}</span>
        </div>
      </div>

      <!-- 附：线下佐证 -->
      <div class="doc-sec" v-if="minutes.evidences && minutes.evidences.length">
        <span class="sec-title">附：线下佐证</span>
        <span class="sec-text" v-for="(item, index) in minutes.evidences" :key="item.id">{{ index + 1 }}. {{ item.fileName }}</span>
      </div>

      <!-- 底部 -->
      <span class="doc-foot">本纪要由系统根据会议记录自动生成 · {{ minutes.draft ? '草稿' : '已定稿' }}</span>

      <!-- 编辑模式 -->
      <div v-if="editMode" class="edit-block">
        <span v-if="reviseMode" class="revise-hint">修订模式：原归档版本将保留，本次修改作为新版本生效，提交时需填写修订原因。</span>
        <textarea
          class="edit-textarea"
          v-model="editText"
          placeholder="编辑纪要全文..."
        ></textarea>
        <div class="edit-actions">
          <button class="btn btn-ghost" @click="cancelEdit">取消</button>
          <button class="btn btn-primary" @click="saveEdit">{{ reviseMode ? '提交修订' : '保存并确认' }}</button>
        </div>
      </div>

      <div class="minutes-actions" v-if="!aiGenerating">
        <!-- 两个主操作：确认(结束会议) / 编辑(改完再确认结束) -->
        <button v-if="canEditMinutes && minutes.draft" class="end-meeting-btn" @click="endMeetingFromMinutes">确认纪要无误，结束会议</button>
        <button v-if="canEditMinutes && !editMode" class="edit-minutes-btn" @click="startEdit">编辑纪要</button>
        <!-- 次要操作：复制全文 / 待办 / 内部报告 / 修订 -->
        <div class="more-links">
          <span class="more-link" @click="copyText">复制全文</span>
          <span v-if="canViewInternalArtifacts" class="more-link primary-link" @click="viewTodoList">待办事项</span>
          <span v-if="canViewInternalArtifacts" class="more-link" @click="viewInternalTopicReport">内部议题报告</span>
          <span v-if="canReviseMinutes" class="more-link" @click="startRevise">发起修订</span>
          <span v-if="canReviseMinutes" class="more-link" @click="viewRevisions">修订历史</span>
        </div>
      </div>
    </div>

    <div v-else class="empty-state"><span>加载中...</span></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import perm from '@/utils/perm'
import { toast, hideToast, showModal, showLoading, hideLoading } from '@/utils/ui'
import { navigateTo, redirectTo } from '@/utils/navigate'
import AiWorkingOverlay from '@/components/AiWorkingOverlay.vue'

const route = useRoute()

// data
const minutes = ref(null)
const plainText = ref('')
const editMode = ref(false)
const editText = ref('')
const isChair = ref(false)
const isOwner = ref(false)
const isExternalRole = ref(false)
const canEditMinutes = ref(false)
const canReviseMinutes = ref(false)
const canViewInternalArtifacts = ref(false)
const reviseMode = ref(false)
const hasServerMinutes = ref(false)
const aiGenerating = ref(false) // AI 纪要后台生成中（显示"请稍候"提示）
const isPublished = ref(false)
const isArchived = ref(false)
const accessDenied = ref(false)
const accessTitle = ref('')
const accessText = ref('')

// 非响应式实例状态
let meetingId = null
let genAi = false
let viewFirst = false
let _destroyed = false
let _aiTimer = null
let _aiTries = 0
let _genRunning = false

onMounted(() => {
  const options = route.query
  console.log('[minutes] onLoad 构建标记=BUILD-B（结束按钮已修），options=', options)
  const id = parseInt(options.meetingId)
  const from = options.from || ''
  const owner = from === 'owner' || from === 'owner-detail'
  const external = perm.isExternal()
  isChair.value = perm.isChair() || perm.can('committee.publish')
  isOwner.value = owner
  isExternalRole.value = external
  canEditMinutes.value = false
  canReviseMinutes.value = false
  canViewInternalArtifacts.value = false
  reviseMode.value = false
  isPublished.value = false
  isArchived.value = false
  accessDenied.value = false
  accessTitle.value = ''
  accessText.value = ''
  meetingId = id
  // gen=1：进入纪要页时直接调大模型生成纪要（整屏显示"生成中"，出文后展示）
  genAi = options.gen === '1'
  // 进入即先把界面切成"生成中"，避免 loadMinutes 期间闪现结构化/旧内容
  if (genAi) aiGenerating.value = true
  // view=1（AI 生成跳转 / 「查看」链接）进入时先看正文；其它渠道进入则直接编辑
  viewFirst = options.view === '1'
  if (id) owner ? loadOwnerMinutes(id) : loadMinutes(id)
})

onUnmounted(() => {
  _destroyed = true
  if (_aiTimer) { clearTimeout(_aiTimer); _aiTimer = null }
})

// 轮询后端 AI 纪要：结束/一键生成后大模型在后台跑，跑完自动把正文换成 AI 版本
function startAwaitAiMinutes() {
  if (_aiTimer) return // 避免重复启动
  _aiTries = 0
  const maxTries = 20 // 最多约 20 × 6s = 120s，覆盖大模型较慢的情况
  const intervalMs = 6000
  const tick = async () => {
    _aiTimer = null
    if (_destroyed) return
    _aiTries++
    try {
      const txt = await api.committeeMinutes(meetingId)
      if (txt && String(txt).trim()) {
        if (_destroyed) return
        plainText.value = txt
        hasServerMinutes.value = true
        aiGenerating.value = false
        toast({ title: '会议纪要已生成', icon: 'none' })
        return // 拿到 AI 正文，停止轮询
      }
    } catch (e) { /* 忽略，继续重试 */ }
    if (!_destroyed && _aiTries < maxTries) {
      _aiTimer = setTimeout(tick, intervalMs)
    } else if (!_destroyed) {
      // 超时仍未拿到：撤掉"生成中"提示，先展示已有(结构化)纪要，提示可稍后刷新
      aiGenerating.value = false
      toast({ title: '生成较慢，可稍后下拉刷新查看', icon: 'none' })
    }
  }
  _aiTimer = setTimeout(tick, intervalMs)
}

function isPublicCommitteeMinutes(detail) {
  return detail && detail.stage === 'ended' && detail.compliance !== 'invalid' &&
    detail.publish && detail.publish.published
}

function isPublicOwnerMinutes(detail) {
  return detail && detail.stage === 'ended' && detail.compliance !== 'invalid' &&
    detail.publishInfo && detail.publishInfo.published
}

function denyAccess(title, text) {
  accessDenied.value = true
  accessTitle.value = title
  accessText.value = text
  minutes.value = null
  plainText.value = ''
  editMode.value = false
  aiGenerating.value = false
  canEditMinutes.value = false
  isPublished.value = false
}

async function loadMinutes(id) {
  try {
    const detail = await api.committeeDetail(id)
    if (!detail) return
    const published = isPublicCommitteeMinutes(detail)
    if (isExternalRole.value && !published) {
      denyAccess('纪要待公示', '会议纪要公示后将作为正式公开文件展示。当前仍属于内部会议记录，暂不可查看。')
      return
    }
    let serverText = ''
    try { serverText = await api.committeeMinutes(id) } catch (e) {}

    const record = detail.record || {}
    if (!record.attendances) {
      denyAccess('暂无可查看纪要', '当前会议记录尚未生成或暂不可公开。')
      return
    }
    const attendances = record.attendances || []
    const topics = record.topics || []
    const total = attendances.length || 7
    const need = Math.ceil(total / 2)
    const present = attendances.filter((a) => a.signedIn)
    const absent = attendances.filter((a) => !a.signedIn)
    const host = (attendances.find((a) => a.role === '主任') || attendances[0] || {}).name || ''

    // 表决结论
    let resolutionLevel = 'none', resolutionText = '本次会议无表决议题'
    if (topics.length) {
      const passed = topics.filter((t) => t.passed).length
      if (passed === topics.length) { resolutionLevel = 'passed'; resolutionText = topics.length + '项议题全部通过' }
      else if (passed > 0) { resolutionLevel = 'partial'; resolutionText = passed + '项通过/' + (topics.length - passed) + '项未通过' }
      else { resolutionLevel = 'failed'; resolutionText = '均未通过，未形成有效决议' }
    }

    // 结论
    let conclusionLevel = 'valid', conclusionText = '会议有效，记录已归档。'
    const signedInN = attendances.filter((a) => a.signedIn).length
    if (detail.stage !== 'ended') {
      conclusionLevel = 'valid'
      conclusionText = '会议进行中，纪要尚未定稿。'
    } else if (detail.compliance === 'invalid') {
      conclusionLevel = 'invalid'
      conclusionText = '会议无效，本次决议不生效。'
    } else if (detail.compliance === 'flawed') {
      conclusionLevel = 'flawed'
      conclusionText = '会议有效，但存在需说明的记录或决议事项。'
    }

    // 记录级别
    const checks = record.checks || []
    const recordLevel = detail.stage !== 'ended' ? 'minor'
      : checks.every((c) => c.ok) ? 'complete'
      : checks.filter((c) => c.ok).length >= (checks.length - 1) ? 'minor' : 'incomplete'
    const recordText = recordLevel === 'complete' ? '记录已归档'
      : recordLevel === 'minor' ? '记录存在瑕疵' : '记录待补正'

    // 构建纪要对象
    const minutesObj = {
      draft: detail.stage !== 'ended',
      meetingTitle: detail.title,
      meetingDate: detail.meetingDate,
      meetingTime: detail.meetingTime,
      location: detail.location,
      host: host,
      totalMembers: total,
      presentCount: present.length,
      presentNames: present.map((a) => a.name).join('、') || '无',
      absentNames: absent.map((a) => a.name).join('、') || '',
      quorumMet: present.length >= need,
      description: detail.description,
      topics: topics,
      juweiName: record.hasMajorIssue ? record.juweiName : '',
      juweiSigned: record.juweiSigned,
      conclusionLevel: conclusionLevel,
      conclusionText: conclusionText,
      meetingValid: detail.compliance !== 'invalid',
      recordLevel: recordLevel,
      recordText: recordText,
      resolutionLevel: resolutionLevel,
      resolutionText: resolutionText,
      notes: detail.invalidNotes || [],
      evidences: record.evidences || [],
      published: published
    }

    // 生成纯文本用于复制
    const L = []
    L.push('业主委员会会议纪要')
    L.push('')
    L.push('会议名称：' + detail.title)
    L.push('会议时间：' + detail.meetingDate + ' ' + detail.meetingTime)
    L.push('会议地点：' + detail.location)
    L.push('主持人：' + host)
    L.push('记录人：业委会委员')
    L.push('')
    L.push('一、参会情况')
    L.push('应到委员 ' + total + ' 人，实到 ' + present.length + ' 人，' + (present.length >= need ? '已过半，达到法定人数' : '未过半，未达法定人数') + '。')
    L.push('出席：' + (present.map((a) => a.name).join('、') || '无'))
    if (absent.length) L.push('缺席：' + absent.map((a) => a.name).join('、'))
    L.push('')
    L.push('二、会议议题')
    L.push(detail.description || '（无）')
    if (topics.length) {
      L.push('')
      L.push('三、表决情况')
      topics.forEach((t, i) => {
        L.push((i + 1) + '. 【' + (t.type === 'major' ? '重大事项' : '决定事项') + '】' + t.title)
        L.push('   赞成 ' + t.forVotes + ' 票，反对 ' + t.agVotes + ' 票，弃权 ' + t.abVotes + ' 票（赞成需≥' + need + '）。表决结果：' + t.text + '。')
      })
    }
    L.push('')
    L.push((topics.length ? '四' : '三') + '、参会确认')
    L.push('本次会议经 ' + present.length + '/' + total + ' 名委员确认参会。')
    if (record.hasMajorIssue && record.juweiName) {
      L.push('重大事项' + (record.juweiSigned ? '已' : '尚未') + '由居委会委员（' + record.juweiName + '）签字。')
    }
    L.push('')
    L.push((topics.length ? '五' : '四') + '、会议结论')
    L.push(conclusionText)

    const archived = !!detail._archived
    const serverHas = !!(serverText && String(serverText).trim())
    minutes.value = minutesObj
    plainText.value = serverText || L.join('\n')
    hasServerMinutes.value = serverHas
    accessDenied.value = false
    accessTitle.value = ''
    accessText.value = ''
    isPublished.value = published
    isArchived.value = archived
    canViewInternalArtifacts.value = !isExternalRole.value && !isOwner.value
    // 未公示未归档：可直接编辑；已公示或已归档：只能走修订版本
    // 主任在未公示/未归档时即可编辑（含 AI 草稿生成后、会议还没结束时；后端 updateMinutes 只拦"已公示"）
    canEditMinutes.value = isChair.value && !published && !archived
    canReviseMinutes.value = isChair.value && (published || archived) && !minutesObj.draft

    // gen=1：在本页直接(重新)生成 AI 纪要——无论是否已有旧文本都重新调大模型，显示"生成中"，出文后展示
    if (genAi) {
      genAi = false
      regenerateAiMinutes()
    } else if (canEditMinutes.value && !viewFirst) {
      // 编辑/核对/确认为主：可编辑且非"先看正文"入口时，进入即直接打开编辑器
      startEdit()
    }
  } catch (e) {
    console.log('Minutes load error', e)
    denyAccess('纪要暂不可查看', e.message || '请稍后再试。')
  }
}

async function loadOwnerMinutes(id) {
  denyAccess('业主大会已停用', '当前产品仅供业委会内部使用，不再提供业主大会纪要。')
}

async function copyText() {
  // wx.setClipboardData 在小程序里成功后自动 toast；H5 需手动补
  // TODO(第三期) 部分浏览器/非 HTTPS 环境 navigator.clipboard 不可用，目前直接尝试
  try {
    await navigator.clipboard.writeText(plainText.value)
    toast({ title: '已复制' })
  } catch (e) {
    toast({ title: '复制失败', icon: 'none' })
  }
}

// (重新)生成 AI 会议纪要：在本页直接调大模型润色（用已保存的确认数据），显示"生成中"，出文后展示
async function regenerateAiMinutes() {
  if (_genRunning) return
  _genRunning = true
  aiGenerating.value = true
  let txt = ''
  try {
    if (typeof api.committeeQuickPolish === 'function') {
      const r = await api.committeeQuickPolish(meetingId)
      txt = (r && (r.minutesMarkdown || r.minutes)) || ''
    }
  } catch (e) { /* 失败，下面再尝试取已存正文 */ }
  if (!txt) {
    try { txt = await api.committeeMinutes(meetingId) } catch (e) {}
  }
  _genRunning = false
  if (_destroyed) return
  if (txt && String(txt).trim()) {
    plainText.value = txt
    hasServerMinutes.value = true
    aiGenerating.value = false
    toast({ title: '会议纪要已生成', icon: 'none' })
  } else {
    aiGenerating.value = false
    showModal({ title: '生成未完成', content: '大模型生成较慢或暂不可用，可稍后再点「重新生成」重试。', showCancel: false })
  }
}

// 会议进行中：在纪要页确认纪要无误并结束会议
async function endMeetingFromMinutes() {
  if (!isChair.value) { toast({ title: '仅主任/副主任可结束会议', icon: 'none' }); return }
  // 真正执行结束：确认框正常弹出后走这里
  // 结束后跳到会议详情页（公示页面）：主任可在此「发起公示」
  const goPublish = function () {
    redirectTo('/pages/committee-detail/committee-detail?id=' + meetingId)
  }
  const doEnd = async function () {
    showLoading({ title: '正在结束…' })
    try {
      await api.committeeAdvance(meetingId, 'end')
      hideLoading()
      toast({ title: '会议已结束', icon: 'success' })
      setTimeout(goPublish, 600) // 让"会议已结束"提示显示一下，再跳到公示页
    } catch (e) {
      hideLoading()
      const msg = (e && e.message) || ''
      // 会议已是结束态（此前已结束 / 重复点击）：也直接去公示页
      const alreadyEnded = msg.indexOf('仅进行中') >= 0 || msg.indexOf('已结束') >= 0 || msg.indexOf('ended') >= 0
      if (alreadyEnded) {
        toast({ title: '会议已是结束状态', icon: 'none' })
        setTimeout(goPublish, 600)
      } else {
        // 其它错误（会议不存在 / 权限 / 网络等）：弹出真实原因，便于排查，绝不静默
        showModal({ title: '结束会议失败', content: msg || '请稍后重试，或返回上一页重新进入', showCancel: false })
      }
    }
  }
  hideToast() // 清掉可能残留的 toast（如"纪要已生成"），避免它把紧跟的确认框吞掉
  const res = await showModal({
    title: '结束会议',
    content: '确认会议纪要无误，结束本次会议并归档吗？',
    confirmText: '确认并结束',
    cancelText: '再想想'
  })
  console.log('[minutes] 确认框已弹出并响应，confirm=', res.confirm)
  if (res.confirm) doEnd()
}

function startEdit() {
  if (!canEditMinutes.value) {
    toast({ title: '已公示/已归档纪要需走修订', icon: 'none' })
    return
  }
  // 已有正式纪要（AI 生成 / 人工保存）→ 预填正文继续编辑；尚无正式纪要（首次手写）→ 空白
  const seed = hasServerMinutes.value ? plainText.value : ''
  editMode.value = true
  reviseMode.value = false
  editText.value = seed
}

// 发起修订：归档/公示后正式内容不可直接改，生成新版本（必填修订原因）
function startRevise() {
  if (!canReviseMinutes.value) return
  editMode.value = true
  reviseMode.value = true
  editText.value = plainText.value
}

function cancelEdit() {
  editMode.value = false
  reviseMode.value = false
  editText.value = ''
}

async function saveEdit() {
  if (!editText.value.trim()) {
    toast({ title: '纪要内容不能为空', icon: 'none' })
    return
  }
  // 修订模式：必填修订原因，原归档版本保留，生成新版本
  if (reviseMode.value) {
    const res = await showModal({
      title: '发起修订',
      content: '原归档版本将保留，本次修改作为新版本生效。请填写修订原因。',
      editable: true,
      placeholderText: '请填写修订原因（必填）'
    })
    if (!res.confirm) return
    const reason = (res.content || '').trim()
    if (!reason) { toast({ title: '修订必须填写原因', icon: 'none' }); return }
    try {
      await api.committeeUpdateMinutes(meetingId, editText.value, reason)
      toast({ title: '修订已生效', icon: 'success' })
      editMode.value = false
      reviseMode.value = false
      plainText.value = editText.value
    } catch (e) {
      toast({ title: e.message || '修订失败', icon: 'none' })
    }
    return
  }
  try {
    await api.committeeUpdateMinutes(meetingId, editText.value)
    toast({ title: '纪要已保存', icon: 'success' })
    // 注意：不要把 minutes.draft 改成 false。draft 反映"会议是否已结束"，
    // 仅由结束会议(advance 'end')决定；保存纪要不等于结束会议。否则绿色
    // "确认纪要无误，结束会议"按钮(canEditMinutes && minutes.draft)会在保存后消失。
    editMode.value = false
    plainText.value = editText.value
    hasServerMinutes.value = true
  } catch (e) {
    toast({ title: e.message || '保存失败', icon: 'none' })
  }
}

async function viewRevisions() {
  try {
    const list = await api.committeeMinutesRevisions(meetingId)
    if (!list || !list.length) { toast({ title: '暂无修订记录', icon: 'none' }); return }
    const lines = list.map(function (r) {
      return 'v' + r.versionNo + ' · ' + (r.editorName || '—') + ' · ' + (r.createdAt || '') +
        (r.reason ? '\n  原因：' + r.reason : '')
    })
    showModal({ title: '纪要修订历史', content: lines.join('\n'), showCancel: false, confirmText: '关闭' })
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

function viewInternalTopicReport() {
  // 改为独立页展示，避免 showModal 截断长文本
  navigateTo('/pages/minutes-internal/minutes-internal?meetingId=' + meetingId)
}

function viewTodoList() {
  // 改为独立页结构化卡片展示，避免 showModal 截断/挤成一坨
  navigateTo('/pages/minutes-todos/minutes-todos?meetingId=' + meetingId)
}
</script>

<style scoped>
.page { min-height:100vh; background:#f4f5f7; padding:24rpx 24rpx 100rpx; box-sizing:border-box; }
.doc { background:#fff; border-radius:24rpx; padding:36rpx 32rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); }
.access-card { background:#fff; border-radius:24rpx; padding:64rpx 36rpx; box-shadow:0 8rpx 28rpx rgba(0,0,0,0.06); text-align:center; }
.access-icon { width:96rpx; height:96rpx; border-radius:50%; background:#FFF3E0; color:#E67E22; display:flex; align-items:center; justify-content:center; margin:0 auto 24rpx; font-size:52rpx; font-weight:700; }
.access-title { display:block; font-size:38rpx; color:#1f2329; font-weight:700; margin-bottom:14rpx; }
.access-text { display:block; font-size:30rpx; color:#666; line-height:1.7; }

.draft-banner { background:#FFF3E0; color:#E67E22; font-size:28rpx; font-weight:600; text-align:center; padding:16rpx 20rpx; border-radius:14rpx; margin-bottom:28rpx; }
/* AI 生成中提示 */
.ai-gen-banner { display:flex; align-items:center; justify-content:center; gap:16rpx; background:#FFF6E5; border:2rpx solid #FFE2A8; border-radius:16rpx; padding:28rpx 24rpx; margin-bottom:24rpx; }
.ai-gen-text { font-size:30rpx; color:#C77800; font-weight:600; }
.gen-hint { display:block; text-align:center; font-size:28rpx; color:#666; line-height:1.7; margin-top:8rpx; }
.ai-gen-dot { width:32rpx; height:32rpx; border-radius:50%; border:6rpx solid #FFE2A8; border-top-color:#FFA800; animation:aigenspin .8s linear infinite; flex-shrink:0; }
@keyframes aigenspin { to { transform:rotate(360deg); } }
.revise-hint { display:block; background:#FDF2F2; color:#C0392B; font-size:28rpx; line-height:1.6; padding:16rpx 20rpx; border-radius:14rpx; margin-bottom:18rpx; }

.doc-title { font-size:42rpx; font-weight:700; text-align:center; display:block; margin-bottom:28rpx; color:#1f2329; }

/* 文档正文（阅读友好） */
.doc-body { display:block; font-size:34rpx; color:#33373d; line-height:1.9; white-space:pre-wrap; padding:24rpx 0; }

.doc-head { margin-bottom:28rpx; }
.dh-row { display:flex; align-items:center; padding:14rpx 0; border-bottom:2rpx solid #f7f7f9; }
.dh-key { font-size:28rpx; color:#666; width:160rpx; flex-shrink:0; }
.dh-val { font-size:30rpx; color:#1f2329; font-weight:500; flex:1; }

.doc-sec { margin-bottom:28rpx; padding-top:24rpx; border-top:2rpx solid #f0f0f0; }
.sec-title { font-size:32rpx; font-weight:700; color:#1f2329; display:block; margin-bottom:16rpx; }
.sec-text { font-size:30rpx; color:#444; line-height:1.8; display:block; margin-bottom:8rpx; }
.sec-k { color:#666; }
.b { font-weight:700; color:#1f2329; }
.tag-ok { color:#27AE60; font-weight:600; }
.tag-no { color:#E74C3C; font-weight:600; }

/* 表决 */
.topic-item { background:#fafbfc; border-radius:16rpx; padding:20rpx; margin-bottom:14rpx; }
.ti-title { font-size:30rpx; font-weight:600; color:#1f2329; display:block; line-height:1.5; }
.ti-tag { font-size: 28rpx; padding:2rpx 14rpx; border-radius:10rpx; background:#FFF6E5; color:#C77800; }
.ti-tag.major { background:#FDECEA; color:#E74C3C; }
.ti-result { font-size:28rpx; color:#6b7785; display:block; margin-top:12rpx; }
.ti-conclusion { font-size:28rpx; font-weight:600; display:block; margin-top:8rpx; }
.ti-conclusion.pass { color:#27AE60; }
.ti-conclusion.fail { color:#E74C3C; }

/* 结论 */
.conclusion-box { border-radius:16rpx; padding:20rpx 24rpx; margin-bottom:18rpx; }
.conclusion-box.valid { background:#EAF7EF; }
.conclusion-box.flawed { background:#FFF4E5; }
.conclusion-box.invalid { background:#FDECEA; }
.cb-text { font-size:30rpx; font-weight:600; }
.conclusion-box.valid .cb-text { color:#27AE60; }
.conclusion-box.flawed .cb-text { color:#E67E22; }
.conclusion-box.invalid .cb-text { color:#E74C3C; }

.check-grid { margin-bottom:14rpx; }
.cg-row { display:flex; align-items:center; padding:10rpx 0; }
.cg-label { font-size:28rpx; color:#666; width:170rpx; flex-shrink:0; }
.cg-value { font-size:28rpx; font-weight:600; }
.cg-value.ok { color:#27AE60; }
.cg-value.warn { color:#E67E22; }
.cg-value.bad { color:#E74C3C; }

.notes-list { margin-top:14rpx; padding:16rpx 20rpx; background:#fafbfc; border-radius:14rpx; }
.note-item { font-size:28rpx; color:#666; display:block; padding:4rpx 0; line-height:1.6; }

.doc-foot { font-size: 28rpx; color:#666; text-align:center; display:block; margin:24rpx 0; }

/* 编辑区 */
.edit-block { margin-top:24rpx; }
.edit-textarea { width:100%; box-sizing:border-box; min-height:400rpx; background:#f6f6f8; border-radius:16rpx; padding:24rpx; font-size:32rpx; color:#1f2329; line-height:1.7; border:none; resize:vertical; font-family:inherit; }
.edit-textarea::placeholder { color:#666; }
.edit-actions { display:flex; gap:16rpx; margin-top:18rpx; }
.edit-actions .btn { flex:1; height:88rpx; line-height:88rpx; border-radius:44rpx; font-size:32rpx; font-weight:600; }

/* 操作区 */
.minutes-actions { display:flex; flex-direction:column; align-items:stretch; gap:16rpx; margin-top:28rpx; }
/* 会议进行中：确认无误并结束会议=主操作（绿色，强调"完成") */
.end-meeting-btn { width:100%; padding:24rpx 0; background:#27AE60; color:#fff; border:none; border-radius:44rpx; font-size:34rpx; font-weight:700; text-align:center; }
/* 编辑=主（橙色填充）；会议进行中时降为次要描边(.ghost) */
.edit-minutes-btn { width:100%; padding:24rpx 0; background:#FFA800; color:#fff; border:none; border-radius:44rpx; font-size:34rpx; font-weight:700; text-align:center; }
.edit-minutes-btn.ghost { background:#fff; color:#C77800; border:2rpx solid #FFA800; padding:22rpx 0; font-size:32rpx; font-weight:600; }
.copy-btn { width:100%; padding:22rpx 0; text-align:center; background:#fff; color:#C77800; border:2rpx solid #FFA800; border-radius:44rpx; font-size:30rpx; font-weight:600; }
.more-links { display:flex; flex-wrap:wrap; justify-content:center; gap:16rpx 28rpx; margin-top:12rpx; }
.more-link { font-size:28rpx; color:#666; padding:10rpx 8rpx; }
.more-link.primary-link { color:#C77800; font-weight:600; }
</style>
