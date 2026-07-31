<template>
  <div class="page minutes-page">
    <!-- 生成中和生成后是两个不同的页面态（0722 用户定）：顶栏标题跟着区分 -->
    <PageNav :title="aiGenerating ? '生成会议纪要' : '会议纪要'" style="margin:-24rpx -24rpx 0;">
      <template #left><div class="minutes-nav-back" @click="backFromMinutes">‹</div></template>
      <template #right>
        <button class="nav-home" @click="goHome">首页</button>
      </template>
    </PageNav>

    <!-- AI 工作中：纪要页 gen=1 大模型生成等待时显"生成纪要"态；完成后出确认按钮（覆盖原"生成中"提示） -->
    <!-- @close（中止）：必须处理，否则关掉遮罩后 aiGenerating 仍为 true，会把用户困在纪要页的"生成中"横幅上，回不到会后整理 -->
    <AiWorkingOverlay :active="aiGenerating" phase="gen" @confirm="openGeneratedMinutes" @close="onGenAbort" />

    <!-- 编辑纪要弹窗（有已有内容时；首次手写走下方整屏编辑器） -->
    <div v-if="editMode && (hasServerMinutes || isOwner)" class="edit-modal-mask">
      <div class="edit-modal">
        <div class="edit-modal-head">
          <button v-if="!reviseMode" class="edit-modal-back" @click="exitEditor">‹ 返回</button>
          <span class="edit-modal-title">{{ reviseMode ? '修订纪要' : '编辑纪要' }}</span>
          <span v-if="reviseMode" class="edit-modal-close" @click="cancelEdit">×</span>
          <span v-else class="edit-modal-head-space"></span>
        </div>
        <span v-if="reviseMode" class="revise-hint" style="margin-bottom:16rpx;display:block;">修订模式：原归档版本将保留，本次修改作为新版本生效，提交时需填写修订原因。</span>
        <textarea class="edit-modal-textarea" v-model="editText" placeholder="编辑纪要全文..."></textarea>
        <div class="edit-modal-actions weighted-actions">
          <button class="btn btn-ghost" @click="reviseMode ? cancelEdit() : exitEditor()">{{ reviseMode ? '取消' : '返回' }}</button>
          <button class="btn btn-save" @click="saveEdit">{{ reviseMode ? '提交修订' : '保存' }}</button>
          <button v-if="!reviseMode" class="btn btn-primary confirm-minutes-btn" @click="confirmMinutes">确认无误<span class="btn-arrow">›</span></button>
        </div>
      </div>
    </div>

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
      <span class="doc-title">会议纪要</span>
      <div class="edit-block">
        <textarea
          class="edit-textarea"
          v-model="editText"
          placeholder="请输入会议纪要内容…（也可返回上一页改用 AI 自动生成）"
        ></textarea>
        <div class="edit-actions weighted-actions">
          <button class="btn btn-ghost" @click="cancelEdit">取消</button>
          <button class="btn btn-primary" @click="saveEdit">保存并确认<span class="btn-arrow">›</span></button>
        </div>
      </div>
    </div>

    <!-- 业主大会纪要（纯文本） -->
    <div v-else-if="isOwner && plainText" class="doc">
      <span class="doc-title">业主大会会议纪要</span>
      <span class="doc-body">{{ indentParagraphs(prettyText) }}</span>
      <div class="minutes-actions" v-if="!aiGenerating">
        <button v-if="canEditMinutes && minutes && minutes.draft" class="end-meeting-btn" @click="endMeetingFromMinutes">确认无误</button>
        <button class="copy-btn" @click="copyText">复制全文</button>
      </div>
    </div>

    <!-- 业委会纪要：优先展示后端保存的大模型/人工编辑正文 -->
    <div v-else-if="!isOwner && hasServerMinutes && plainText" class="doc">
      <div class="minutes-letterhead">
        <span class="minutes-meeting-name">{{ documentParts.meetingName }}</span>
      </div>
      <span class="doc-body">{{ documentParts.body }}</span>
      <div class="minutes-actions" v-if="!aiGenerating">
        <!-- 两个主操作：编辑(左·浅色) / 确认无误(右·深色更醒目) -->
        <div class="action-row">
          <button v-if="canEditMinutes && minutes && minutes.draft" class="end-meeting-btn" @click="endMeetingFromMinutes">确认无误</button>
        </div>
        <!-- 次要操作：复制全文 / 待办 / 内部报告 / 修订 -->
        <div class="more-links">
          <span class="more-link primary-link" @click="copyText">复制全文</span>
          <span v-if="canViewInternalArtifacts" class="more-link primary-link" @click="viewTodoList">待办事项</span>
          <span v-if="canReviseMinutes" class="more-link" @click="startRevise">发起修订</span>
          <span v-if="canReviseMinutes" class="more-link" @click="viewRevisions">修订历史</span>
        </div>
      </div>
    </div>

    <!-- 尚无纪要正文：旧的结构化模板页已删（0722 用户定，只保留正文版纪要），给干净的引导 -->
    <div v-else-if="minutes && !isOwner" class="doc">
      <div class="minutes-letterhead">
        <span class="minutes-meeting-name">{{ joinMinutesTitle(minutes.meetingTitle) }}</span>
      </div>
      <div class="no-minutes-tip">还没有纪要正文。可返回会后整理页点「生成会议纪要」自动生成{{ canEditMinutes ? '，或在下方直接手写' : '' }}。</div>
      <div class="minutes-actions" v-if="canEditMinutes && !aiGenerating">
        <div class="action-row">
          <button class="end-meeting-btn" @click="editMode = true">手写纪要</button>
        </div>
      </div>
    </div>

    <div v-else class="empty-state"><span>加载中...</span></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import perm from '@/utils/perm'
import { toast, hideToast, showModal, showLoading, hideLoading } from '@/utils/ui'
import { navigateTo, redirectTo, navigateBack, goModuleHome } from '@/utils/navigate'
import AiWorkingOverlay from '@/components/AiWorkingOverlay.vue'
import PageNav from '@/components/PageNav.vue'
import { aiTask, startAiTask, finishAiTask, failAiTask, clearAiTask } from '@/composables/aiTask'

const route = useRoute()

// data
const minutes = ref(null)
const plainText = ref('')
// 正文展示：去掉 AI 纪要里的 Markdown 标题标记（行首的 # / ##），纯文本更干净
const prettyText = computed(() => normalizeLegacyBasicSection(String(plainText.value || '')
  .replace(/^[ \t]*#{1,6}[ \t]*/gm, '')
  .replace(/^[^\r\n]*[（(]草稿[）)][ \t]*\r?\n+/, '')
  .replace(/^[^\r\n]*业主委员会[ \t]*\r?\n+/, '')))
// 会议名 + 「会议纪要」拼标题：名字本身以「会议」结尾时去重，避免拼成「××会议会议纪要」
function joinMinutesTitle(name) {
  const n = String(name || '业委会').trim().replace(/会议纪要$/, '').replace(/会议$/, '')
  return n + '会议纪要'
}
// 正文每段首行缩进两个全角空格（0722 用户定，公文格式）；只影响展示，编辑/复制仍是原文
function indentParagraphs(text) {
  return String(text || '').split('\n')
    .map(l => { const t = l.trim(); return t ? '　　' + t : '' })
    .join('\n')
}
// 第一行（xxx会议纪要）作标题，大字加粗；其余作正文
const documentParts = computed(() => {
  const lines = String(prettyText.value || '').split('\n')
  const marker = lines.findIndex(line => line.trim() === '会议纪要')
  if (marker >= 0) {
    const meetingName = lines.slice(0, marker).filter(line => line.trim()).join('\n').trim()
    // 期号行（第N期）已从公文格式中移除：老纪要里若存有该行，按普通正文首行显示
    return { meetingName: joinMinutesTitle(meetingName), body: indentParagraphs(lines.slice(marker + 1).join('\n').replace(/^\s*\n/, '')) }
  }
  return { meetingName: (lines[0] || '').trim(), body: indentParagraphs(lines.slice(1).join('\n').replace(/^\s*\n/, '')) }
})
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
let fromPage = '' // 来处页面（如 meeting-live-quick）：中止生成时据此决定回哪儿
let genAi = false
let resumeAi = false
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
  fromPage = from
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
  resumeAi = options.resume === '1' || (aiTask.active && String(aiTask.targetPath || '').includes('meetingId=' + id))
  // 进入即先把界面切成"生成中"，避免 loadMinutes 期间闪现结构化/旧内容
  if (genAi || resumeAi) {
    aiGenerating.value = true
    aiTask.overlayShown = true
  }
  // view=1（AI 生成跳转 / 「查看」链接）进入时先看正文；其它渠道进入则直接编辑
  viewFirst = options.view === '1'
  if (id) owner ? loadOwnerMinutes(id) : loadMinutes(id)
})

onUnmounted(() => {
  _destroyed = true
  aiTask.overlayShown = false
  if (_aiTimer) { clearTimeout(_aiTimer); _aiTimer = null }
})

function backFromMinutes() {
  aiTask.overlayShown = false
  // 会后整理页跳来的（生成纪要/最小化后落在本页）：返回=确定性回会后整理页。
  // 这条链路是一串 redirect，浏览器历史下一层往往是驾驶舱/首页，盲退会把人甩出会议（0729 BUG1）
  if (fromPage === 'meeting-live-quick' && meetingId) {
    const q = 'meetingId=' + meetingId
    redirectTo('/pages/meeting-live-quick/meeting-live-quick?' + q)
    setTimeout(() => {
      if (document.querySelector('.minutes-page')) window.location.replace('/meeting-live-quick?' + q)
    }, 300)
    return
  }
  // 其余来处仍走历史回退（0725 导航新规）
  navigateBack()
}

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
    } else if (resumeAi) {
      resumeAi = false
      resumeBackgroundMinutes()
    } else if (canEditMinutes.value) {
      // 未公示、未归档的纪要进入即直接编辑，不再要求先点“编辑纪要”
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
  const taskPath = '/pages/minutes-view/minutes-view?meetingId=' + meetingId
  startAiTask({ label: '会议纪要生成中…', originPath: window.location.pathname, targetPath: taskPath })
  aiTask.overlayShown = true
  let txt = ''
  let requestError = null
  try {
    if (typeof api.committeeQuickPolish === 'function') {
      const r = await api.committeeQuickPolish(meetingId)
      txt = (r && (r.minutesMarkdown || r.minutes)) || ''
    }
  } catch (e) { requestError = e }
  if (!txt) {
    try { txt = await api.committeeMinutes(meetingId) } catch (e) {}
  }
  _genRunning = false
  if (_destroyed) {
    if (txt && String(txt).trim()) finishAiTask({ doneLabel: '会议纪要已生成', targetPath: taskPath })
    else failAiTask({ failLabel: (requestError && requestError.message) || '会议纪要生成失败' })
    aiTask.overlayShown = false
    return
  }
  if (txt && String(txt).trim()) {
    clearAiTask()
    plainText.value = stripDraftHeading(txt)
    hasServerMinutes.value = true
    aiGenerating.value = false
    toast({ title: '会议纪要已生成', icon: 'none' })
  } else {
    failAiTask({ failLabel: (requestError && requestError.message) || '会议纪要生成失败' })
    aiTask.overlayShown = false
    aiGenerating.value = false
    showModal({ title: '生成未完成', content: '大模型生成较慢或暂不可用，可稍后再点「重新生成」重试。', showCancel: false })
  }
}

function resumeBackgroundMinutes() {
  aiGenerating.value = true
  aiTask.overlayShown = true
  const tick = async () => {
    _aiTimer = null
    if (_destroyed) return
    try {
      const status = typeof api.committeeMinutesStatus === 'function'
        ? await api.committeeMinutesStatus(meetingId) : null
      if (status && status.status === 'failed') {
        aiGenerating.value = false
        failAiTask({ failLabel: '会议纪要生成失败' })
        aiTask.overlayShown = false
        return
      }
      if (!status || status.status === 'success') {
        const txt = await api.committeeMinutes(meetingId)
        if (txt && String(txt).trim()) {
          plainText.value = stripDraftHeading(txt)
          hasServerMinutes.value = true
          aiGenerating.value = false
          clearAiTask()
          return
        }
      }
    } catch (e) { /* 后台任务仍可能运行，继续轮询 */ }
    _aiTimer = setTimeout(tick, 3000)
  }
  tick()
}

// 蓝色 AI 完成页统一进入新的「查看会议纪要」页；旧 Minutes 页只保留生成过程兼容，不再作为生成后的落点。
function openGeneratedMinutes() {
  aiTask.overlayShown = false
  clearAiTask()
  const q = 'meetingId=' + meetingId
  redirectTo('/pages/minutes-view/minutes-view?' + q)
  setTimeout(() => {
    if (document.querySelector('.minutes-page')) window.location.replace('/minutes-view?' + q)
  }, 300)
}

// 中止/关闭 AI 生成遮罩（右上角 ×，遮罩内已确认中止）：清掉本页"生成中"态并回到来处页面。
// 关键修复：本页是"生成过程页"（gen=1 从会后整理跳来），此前遮罩无 @close，关掉后 aiGenerating
// 仍为 true，页面会一直显示无效的"AI 正在生成…"横幅，把用户困在纪要页、回不到会后整理。
// 后端生成是长任务、无法真正取消：保留全局后台任务(aiTask)让它跑完后由悬浮条通知，这里只收前台等待态。
function onGenAbort() {
  aiGenerating.value = false
  aiTask.overlayShown = false
  if (_aiTimer) { clearTimeout(_aiTimer); _aiTimer = null }
  // 从会后整理页（生成会议纪要 / 完成会后整理）跳来的：回到会后整理页，别停在纪要页
  if (fromPage === 'meeting-live-quick' && meetingId) {
    const q = 'meetingId=' + meetingId
    redirectTo('/pages/meeting-live-quick/meeting-live-quick?' + q)
    setTimeout(() => {
      if (document.querySelector('.minutes-page')) window.location.replace('/meeting-live-quick?' + q)
    }, 300)
    return
  }
  navigateBack()
}

// 会议进行中：在纪要页确认纪要无误并结束会议
async function endMeetingFromMinutes() {
  if (!isChair.value) { toast({ title: '仅主任/副主任可结束会议', icon: 'none' }); return }
  // 真正执行结束：确认框正常弹出后走这里
  // 结束后跳到会议详情页（公示页面）：主任可在此「发起公示」
  const goPublish = function () {
    redirectTo('/pages/committee-detail/committee-detail?id=' + meetingId + '&from=minutes')
    // 软路由偶发不切换（URL 变了却停在纪要页）→ 500ms 后仍在本页则硬导航兜底
    setTimeout(() => {
      if (document.querySelector('.minutes-page')) window.location.replace('/committee-detail?id=' + meetingId + '&from=minutes')
    }, 500)
  }
  // ⚠ 测试期开关（0722 用户定，与 MeetingLiveQuick 的 TEST_KEEP_MEETING_OPEN 同语义）：
  // 确认无误不真正结束归档会议，直接去详情页；上线前置回 false。见 docs/上线前TODO.md
  const TEST_KEEP_MEETING_OPEN = true
  if (TEST_KEEP_MEETING_OPEN) {
    hideToast() // 清掉可能残留的 toast，避免吞掉紧跟的确认框
    const res0 = await showModal({
      title: '确认纪要',
      content: '确认会议纪要无误吗？（测试期不会归档会议，仍可随时回来修改）',
      confirmText: '确认',
      cancelText: '再想想'
    })
    if (res0.confirm) {
      toast({ title: '纪要已确认', icon: 'success' })
      setTimeout(goPublish, 600)
    }
    return
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
  const seed = hasServerMinutes.value ? stripDraftHeading(plainText.value) : ''
  editMode.value = true
  reviseMode.value = false
  editText.value = seed
}

function stripDraftHeading(value) {
  return normalizeLegacyBasicSection(String(value || '').replace(/^[^\r\n]*[（(]草稿[）)][ \t]*\r?\n+/, ''))
}

function normalizeLegacyBasicSection(value) {
  return String(value || '')
    .replace(/(^|\n)一、会议基本情况[ \t]*\r?\n/, '$1')
    .replace(/(^|\n)二、议题审议情况/g, '$1一、议题审议情况')
    .replace(/(^|\n)三、会议结论与后续安排/g, '$1二、会议结论与后续安排')
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

async function exitEditor() {
  if (reviseMode.value) {
    cancelEdit()
    return
  }
  const changed = editText.value.trim() !== stripDraftHeading(plainText.value).trim()
  if (!changed) {
    editMode.value = false
    navigateBack()
    return
  }
  const res = await showModal({
    title: '返回上一页',
    content: '纪要内容有修改，是否保存后返回？',
    confirmText: '保存并返回',
    cancelText: '继续编辑'
  })
  if (!res.confirm) return
  try {
    await api.committeeUpdateMinutes(meetingId, editText.value)
    plainText.value = editText.value
    hasServerMinutes.value = true
    editMode.value = false
    toast({ title: '纪要已保存', icon: 'success' })
    setTimeout(() => navigateBack(), 300)
  } catch (e) {
    toast({ title: e.message || '保存失败', icon: 'none' })
  }
}

async function confirmMinutes() {
  if (!editText.value.trim()) {
    toast({ title: '纪要内容不能为空', icon: 'none' })
    return
  }
  try {
    if (editText.value.trim() !== stripDraftHeading(plainText.value).trim()) {
      await api.committeeUpdateMinutes(meetingId, editText.value)
      plainText.value = editText.value
      hasServerMinutes.value = true
    }
    editMode.value = false
    toast({ title: '纪要已确认', icon: 'success' })
    setTimeout(() => redirectTo('/pages/committee-detail/committee-detail?id=' + meetingId + '&from=minutes'), 300)
  } catch (e) {
    toast({ title: e.message || '保存失败', icon: 'none' })
  }
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

// 右上角「首页」：直接回业委会主页（redirectTo 硬替换，避免返回栈残留在纪要页）
function goHome() {
  goModuleHome('meeting')
}

function viewTodoList() {
  // 改为独立页结构化卡片展示，避免 showModal 截断/挤成一坨
  // 软路由偶发不切换（URL 变了却停在本页）→ 加硬导航兜底，确保一定跳过去
  const target = '/minutes-todos?meetingId=' + meetingId
  navigateTo('/pages/minutes-todos/minutes-todos?meetingId=' + meetingId)
  setTimeout(() => {
    if (document.querySelector('.minutes-page')) window.location.href = target
  }, 300)
}
</script>

<style scoped>
.minutes-nav-back { width:96rpx; height:124rpx; display:flex; align-items:center; justify-content:center; color:#fff; font-size:66rpx; font-weight:700; }
/* 顶栏统一为纯深橙（与其他页一致，覆盖 PageNav 默认黄橙渐变） */
:deep(.page-nav) { background: #2b5589; }  /* 会议模块页头（规范三色制） */
.page { min-height:100vh; background:#f4f5f7; padding:24rpx 24rpx 100rpx; box-sizing:border-box; }
/* 顶栏右上角「首页」：白描边药丸，适配深橙 PageNav 头 */
.nav-home { display:inline-flex; align-items:center; height:64rpx; margin-right:20rpx; padding:0 24rpx; border:2rpx solid rgba(255,255,255,0.6); border-radius:34rpx; background:rgba(255,255,255,0.12); color:#fff; font-size:30rpx; font-weight:600; line-height:1; }
.nav-home:active { background:rgba(255,255,255,0.28); }
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
/* 正文第一行作标题：字号加大三号、黑体加粗、居中 */
.minutes-letterhead { display:flex; flex-direction:column; align-items:center; text-align:center; padding:18rpx 10rpx 34rpx; }
.minutes-meeting-name { font-size:42rpx; font-weight:700; color:#161616; line-height:1.45; white-space:pre-wrap; }
.doc-body { display:block; font-size:34rpx; color:#33373d; line-height:1.9; white-space:pre-wrap; padding:24rpx 0; }
/* 尚无纪要正文的引导（替代已删除的结构化模板页） */
.no-minutes-tip { padding:56rpx 20rpx; text-align:center; color:#8A9099; font-size:27rpx; line-height:1.8; }

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
/* 编辑纪要(左·ghost 浅色·次) / 确认无误(右·深橙填充·主)：约 4:6 主次，确认无误更宽更醒目 */
.action-row { display:flex; gap:20rpx; align-items:center; }
.action-row .end-meeting-btn, .action-row .edit-minutes-btn { width:auto; padding:20rpx 0; font-size:32rpx; }
.action-row .edit-minutes-btn { flex:0 0 40%; }
.action-row .end-meeting-btn { flex:1; }
.end-meeting-btn { width:100%; padding:24rpx 0; background:var(--c-primary-dark); color:#fff; border:none; border-radius:44rpx; font-size:34rpx; font-weight:700; text-align:center; }
.end-meeting-btn:active { background:var(--c-primary-strong); }
.edit-minutes-btn { width:100%; padding:24rpx 0; background:var(--c-primary-dark); color:#fff; border:none; border-radius:44rpx; font-size:34rpx; font-weight:700; text-align:center; }
.edit-minutes-btn:active { background:var(--c-primary-strong); }
.edit-minutes-btn.ghost { background:#fff; color:var(--c-primary-dark); border:2rpx solid var(--c-primary-dark); padding:22rpx 0; font-size:32rpx; font-weight:600; }
.copy-btn { width:100%; padding:22rpx 0; text-align:center; background:#fff; color:#C77800; border:2rpx solid #FFA800; border-radius:44rpx; font-size:30rpx; font-weight:600; }
.more-links { display:flex; flex-wrap:wrap; justify-content:center; gap:10rpx 24rpx; margin-top:8rpx; }
.more-link { font-size:25rpx; color:#92979e; padding:8rpx 6rpx; font-weight:400; }
.more-link.primary-link { color:#7f858c; font-weight:400; }

/* 编辑纪要弹窗 —— 全屏模式，方便老年人操作 */
.edit-modal-mask { position:fixed; inset:0; z-index:500; display:flex; flex-direction:column; background:#fff; }
.edit-modal { flex:1; display:flex; flex-direction:column; overflow:hidden; }
/* 编辑态顶栏用深蓝色（区别于查看页的深橙 PageNav）：布局与查看页几乎一致，靠顶栏换色让用户明确感知"已进入编辑模式" */
.edit-modal-head { display:flex; align-items:center; justify-content:space-between; padding:calc(24rpx + env(safe-area-inset-top)) 32rpx 24rpx; background:#1A6296; flex-shrink:0; }
.edit-modal-back { width:150rpx; padding:8rpx 0; border:none; background:transparent; color:#fff; text-align:left; font-size:34rpx; font-weight:600; }
.edit-modal-head-space { width:150rpx; }
.edit-modal-title { font-size:44rpx; font-weight:700; color:#fff; }
.edit-modal-close { font-size:68rpx; color:rgba(255,255,255,0.88); padding:0 8rpx; line-height:1; }
.edit-modal-textarea { flex:1; width:100%; background:#fff; border:none; resize:none; font-family:inherit; box-sizing:border-box; overflow-y:auto; padding:36rpx 32rpx; font-size:38rpx; color:#1a1a1a; line-height:2; }
.edit-modal-actions { display:flex; gap:24rpx; flex-shrink:0; padding:24rpx 32rpx calc(24rpx + env(safe-area-inset-bottom)); background:#f6f6f8; border-top:2rpx solid #ebebeb; }
.edit-modal-actions .btn { flex:1; line-height:3; font-size:38rpx; border-radius:44rpx; border:none; font-weight:700; }
.edit-modal-actions .btn-ghost { background:#fff; color:#666; border:2rpx solid #ddd; }
.edit-modal-actions .btn-save { background:#eef1f4; color:#4f5964; }
.edit-modal-actions .btn-primary { background:var(--c-primary-dark); color:#fff; }
.edit-modal-actions .confirm-minutes-btn { flex:1.35; }
</style>
