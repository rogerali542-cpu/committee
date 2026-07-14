<template>
  <div class="mv-page detail-minutes-view">
    <PageNav title="会议纪要" style="margin:-24rpx -24rpx 0;" />

    <div v-if="loading" class="doc mv-loading">正在加载会议纪要…</div>

    <!-- 正文格式与老纪要页一致；未公示、未归档时可在当前页直接编辑 -->
    <div v-else-if="text" class="doc">
      <div class="minutes-letterhead">
        <span class="minutes-meeting-name" :class="{ editable: editing }" :contenteditable="editing" @input="editableMeetingName = $event.currentTarget.innerText">{{ editing ? editableMeetingName : documentParts.meetingName }}</span>
        <span class="minutes-main-title">会议纪要</span>
        <span v-if="editing || documentParts.issue" class="minutes-issue" :class="{ editable: editing }" :contenteditable="editing" data-placeholder="可填写期数" @input="editableIssue = $event.currentTarget.innerText">{{ editing ? editableIssue : documentParts.issue }}</span>
      </div>
      <div class="minutes-rule"></div>
      <div class="doc-body" :class="{ editable: editing }" :contenteditable="editing" @input="editableBody = $event.currentTarget.innerText">{{ editing ? editableBody : documentParts.body }}</div>
      <div v-if="editing || showSignature" class="mv-signature">阳光花园业主委员会</div>
      <div v-if="editing" class="mv-editor-actions">
        <button class="mv-cancel" :disabled="saving" @click="cancelEdit">取消</button>
        <button class="mv-save" :disabled="saving" @click="saveEdit">{{ saving ? '保存中…' : '确定' }}</button>
      </div>
      <div v-else-if="canEdit" class="mv-editor-actions single">
        <button class="mv-cancel" @click="backToDetail">返回</button>
      </div>
      <div v-else class="mv-links">
        <span class="mv-link" @click="copyAll">复制全文</span>
        <span class="mv-link" @click="viewTodos">待办事项</span>
      </div>
    </div>

    <div v-else class="mv-empty">
      <div class="mv-empty-ico">📄</div>
      <div class="mv-empty-title">暂无会议纪要</div>
      <div class="mv-empty-sub">本次会议还没有生成会议纪要，可在会议录音页生成后再来查看。</div>
    </div>
  </div>
</template>

<script setup>
// 会议详情专用的会议纪要页：查看与编辑合并，避免再跳回旧纪要编辑页。
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import perm from '@/utils/perm'
import { toast } from '@/utils/ui'
import { navigateTo, redirectTo } from '@/utils/navigate'
import PageNav from '@/components/PageNav.vue'

const route = useRoute()
const loading = ref(true)
const text = ref('')
const canEdit = ref(false)
const editing = ref(false)
const editableMeetingName = ref('')
const editableIssue = ref('')
const editableBody = ref('')
const saving = ref(false)
let meetingId = null

// 去掉 AI 纪要里的 Markdown 标题标记（行首 #），纯文本更干净；首行作标题，其余作正文
const pretty = computed(() => String(text.value || '')
  .replace(/^[ \t]*#{1,6}[ \t]*/gm, '')
  .replace(/^[^\r\n]*[（(]草稿[）)][ \t]*\r?\n+/, '')
  // AI 偶尔把“某某业主委员会”单独作为首行署名；展示时去掉，下一行纪要标题自动顶上。
  .replace(/^[^\r\n]*业主委员会[ \t]*\r?\n+/, '')
  .replace(/(^|\n)一、会议基本情况[ \t]*\r?\n/, '$1')
  .replace(/(^|\n)二、议题审议情况/g, '$1一、议题审议情况')
  .replace(/(^|\n)三、会议结论与后续安排/g, '$1二、会议结论与后续安排'))
const documentParts = computed(() => {
  const lines = pretty.value.split('\n')
  const marker = lines.findIndex(line => line.trim() === '会议纪要')
  if (marker >= 0) {
    const meetingName = lines.slice(0, marker).filter(line => line.trim()).join('\n').trim()
    let bodyStart = marker + 1
    let issue = ''
    if (/^第.+期$/.test((lines[bodyStart] || '').trim())) issue = lines[bodyStart++].trim()
    return { meetingName: meetingName || '会议', issue, body: lines.slice(bodyStart).join('\n').replace(/^\s*\n/, '') }
  }
  return { meetingName: (lines[0] || '').trim(), issue: '', body: lines.slice(1).join('\n').replace(/^\s*\n/, '') }
})
const SIGNATURE = '阳光花园业主委员会'
const showSignature = computed(() => !String(text.value || '').trimEnd().endsWith(SIGNATURE))

function withSignature(value) {
  const content = String(value || '').trimEnd()
  return content.endsWith(SIGNATURE) ? content : content + '\n\n' + SIGNATURE
}

function beginInlineEdit() {
  editableMeetingName.value = documentParts.value.meetingName
  editableIssue.value = documentParts.value.issue
  editableBody.value = String(documentParts.value.body || '').replace(/\s*阳光花园业主委员会\s*$/, '').trimEnd()
  editing.value = true
}

async function load() {
  loading.value = true
  try {
    const [txt, detail] = await Promise.all([
      api.committeeMinutes(meetingId),
      api.committeeDetail(meetingId)
    ])
    text.value = txt && String(txt).trim() ? txt : ''
    const published = !!(detail && detail.publish && detail.publish.published)
    const archived = !!(detail && detail._archived)
    canEdit.value = (perm.isChair() || perm.can('committee.publish')) && !published && !archived
    if (canEdit.value && text.value) beginInlineEdit()
  } catch (e) {
    text.value = ''
    canEdit.value = false
  } finally {
    loading.value = false
  }
}

function cancelEdit() {
  // 编辑值尚未写入 text，退出编辑态即可恢复到上一次“确定”保存的内容。
  editableMeetingName.value = documentParts.value.meetingName
  editableIssue.value = documentParts.value.issue
  editableBody.value = String(documentParts.value.body || '').replace(/\s*阳光花园业主委员会\s*$/, '').trimEnd()
  editing.value = false
}

function backToDetail() {
  const q = 'id=' + meetingId + '&from=minutes'
  redirectTo('/pages/committee-detail/committee-detail?' + q)
  setTimeout(() => {
    if (document.querySelector('.detail-minutes-view')) window.location.replace('/committee-detail?' + q)
  }, 300)
}

async function saveEdit() {
  if (!editableBody.value.trim()) { toast({ title: '纪要内容不能为空', icon: 'none' }); return }
  const header = (editableMeetingName.value.trim() || '会议') + '\n会议纪要'
  const issue = editableIssue.value.trim() ? '\n' + editableIssue.value.trim() : ''
  const value = withSignature(header + issue + '\n\n' + editableBody.value.trim())
  saving.value = true
  try {
    await api.committeeUpdateMinutes(meetingId, value)
    text.value = value
    toast({ title: '纪要已保存', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

async function copyAll() {
  try {
    await navigator.clipboard.writeText(withSignature(text.value))
    toast({ title: '已复制' })
  } catch (e) {
    toast({ title: '复制失败', icon: 'none' })
  }
}

// 待办事项独立页；软路由偶发不切换 → 硬导航兜底
function viewTodos() {
  const q = 'meetingId=' + meetingId
  navigateTo('/pages/minutes-todos/minutes-todos?' + q)
  setTimeout(() => {
    if (document.querySelector('.detail-minutes-view')) window.location.href = '/minutes-todos?' + q
  }, 300)
}

onMounted(() => {
  meetingId = parseInt(route.query.meetingId)
  if (meetingId) load()
  else loading.value = false
})
</script>

<style scoped>
/* 顶栏统一为纯深橙（覆盖 PageNav 默认渐变） */
:deep(.page-nav) { background: var(--c-primary-dark); }
.mv-page { min-height: 100vh; background: #f4f5f7; padding: 24rpx 24rpx calc(40rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
/* 正文格式与老纪要页保持一致 */
.doc { background: #fff; border-radius: 24rpx; padding: 36rpx 32rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.mv-loading { color: #888; text-align: center; font-size: 30rpx; }
.minutes-letterhead { display:flex; flex-direction:column; align-items:center; text-align:center; padding:12rpx 10rpx 22rpx; }
.minutes-meeting-name { font-size:34rpx; color:#202124; line-height:1.45; white-space:pre-wrap; }
.minutes-main-title { margin-top:12rpx; font-size:58rpx; font-weight:700; letter-spacing:14rpx; color:#d71920; line-height:1.25; }
.minutes-issue { margin-top:6rpx; font-size:32rpx; color:#202124; }
.minutes-rule { height:2rpx; background:#b65d5d; margin:4rpx 0 18rpx; }
.doc-body { display: block; font-size: 34rpx; color: #33373d; line-height: 1.9; white-space: pre-wrap; padding: 24rpx 0; }
.editable { outline: none; border-radius: 8rpx; transition: background .15s; }
.editable:focus { background: #fffaf2; box-shadow: 0 0 0 2rpx rgba(198,106,0,.18); }
.minutes-meeting-name.editable { min-width: 60%; }
.minutes-issue.editable { min-width: 32%; min-height: 1.45em; }
.minutes-issue.editable:empty::before { content: attr(data-placeholder); color: #b4b7bc; font-weight: 400; }
.doc-body.editable { min-height: 52vh; }
/* 复制/待办：在老页小链接基础上加大，方便点（老页 28rpx → 34rpx，加内边距 + 分隔线） */
.mv-links { display: flex; flex-wrap: wrap; justify-content: center; gap: 12rpx 34rpx; margin-top: 12rpx; padding-top: 18rpx; border-top: 2rpx solid #f0f0f0; }
.mv-link { font-size: 26rpx; font-weight: 400; color: #858b92; padding: 10rpx 16rpx; }
.mv-link:active { opacity: 0.6; }
.mv-signature { margin-top: 36rpx; text-align: right; font-size: 32rpx; line-height: 1.8; color: #33373d; }
.mv-editor-actions { display: flex; gap: 20rpx; margin-top: 34rpx; padding-top: 22rpx; border-top: 2rpx solid #f0f0f0; }
.mv-editor-actions.single { justify-content: center; }
.mv-editor-actions.single button { flex: 0 0 60%; }
.mv-editor-actions button { flex: 1; height: 84rpx; border-radius: 42rpx; font-size: 30rpx; border: 0; }
.mv-cancel { background: #f1f2f4; color: #555; }
.mv-save { background: var(--c-primary-dark); color: #fff; }
.mv-editor-actions button:disabled { opacity: .55; }
/* 无纪要时的空状态 */
.mv-empty { background: #fff; border-radius: 24rpx; padding: 80rpx 40rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); text-align: center; }
.mv-empty-ico { font-size: 72rpx; margin-bottom: 16rpx; }
.mv-empty-title { font-size: 34rpx; font-weight: 700; color: #1a1a1a; margin-bottom: 12rpx; }
.mv-empty-sub { font-size: 28rpx; color: #888; line-height: 1.7; }
</style>
