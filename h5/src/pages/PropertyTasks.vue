<template>
  <div class="page">
    <span class="intro">业委会转来的物业类诉求，请处理后上传佐证并回填结果。</span>

    <!-- 筛选 -->
    <div class="filter-tabs">
      <div class="f-tab" :class="{ active: filter === 'pending' }" @click="switchFilter('pending')">待处理 <span class="f-count">{{ counts.pending }}</span></div>
      <div class="f-tab" :class="{ active: filter === 'replied' }" @click="switchFilter('replied')">已完成 <span class="f-count">{{ counts.replied }}</span></div>
      <div class="f-tab" :class="{ active: filter === 'all' }" @click="switchFilter('all')">全部 <span class="f-count">{{ counts.all }}</span></div>
    </div>

    <div class="task-list">
      <template v-if="items.length">
        <div class="task-card" v-for="item in items" :key="item.id">
          <div class="tc-header">
            <span class="tc-visitor">{{ item.visitorName }}{{ item.room ? ' · ' + item.room : '' }}</span>
            <span class="status-pill" :class="item.propertyStatus">{{ item.propertyStatusLabel }}</span>
          </div>
          <div class="tc-meta"><span>📅 {{ item.date }} {{ item.time }}</span></div>
          <div class="tc-content">{{ item.content }}</div>

          <!-- 佐证（处理中/已完成可见） -->
          <div class="tc-ev" v-if="item.evidences.length">
            <span class="tc-ev-title">📎 处理佐证（{{ item.evidences.length }}）</span>
            <div class="tc-ev-list">
              <template v-for="ev in item.evidences" :key="ev.id">
                <img v-if="isImage(ev)" class="tc-ev-thumb" :src="ev.fileUrl" @click="openFile(ev.fileUrl)" />
                <span v-else class="tc-ev-name" @click="openFile(ev.fileUrl)">📄 {{ ev.fileName }}</span>
              </template>
            </div>
          </div>

          <div class="tc-reply" v-if="item.propertyReply">
            <span class="tc-reply-label">处理结果：</span>
            <span>{{ item.propertyReply }}</span>
            <span v-if="item.propertyRepliedAt" class="tc-reply-time">{{ item.propertyRepliedBy }} · {{ item.propertyRepliedAt }}</span>
          </div>

          <!-- 操作 -->
          <div class="tc-next" v-if="item.propertyStatus === 'dispatched'">
            <span class="tc-hint">👉 待你受理</span>
            <button class="btn btn-primary mini" @click="startTask(item.id)">开始处理</button>
          </div>
          <div class="tc-next" v-else-if="item.propertyStatus === 'processing'">
            <span class="tc-hint">👉 待你回填：上传佐证后提交</span>
            <button class="btn btn-primary mini" @click="openReply(item.id)">填写结果并完成</button>
          </div>
        </div>
      </template>
      <div v-else-if="!loading" class="empty-state">
        <span class="empty-emoji">🛠️</span>
        <span>{{ filter === 'pending' ? '暂无待处理工单' : '暂无工单' }}</span>
      </div>
    </div>

    <!-- 完成面板：上传佐证 + 填写结果 -->
    <div v-if="replyVisible" class="modal-mask" @click="closeReply">
      <div class="form-sheet" @click.stop="noop">
        <div class="sheet-head">
          <span class="sheet-title">完成处理</span>
          <span class="sheet-close" @click="closeReply">×</span>
        </div>

        <div class="form-group">
          <span class="form-label">处理佐证（必传）</span>
          <div class="ev-edit-list">
            <div v-for="ev in replyEvidences" :key="ev.id" class="ev-edit-item">
              <img v-if="isImage(ev)" class="ev-edit-thumb" :src="ev.fileUrl" @click="openFile(ev.fileUrl)" />
              <span v-else class="ev-edit-name" @click="openFile(ev.fileUrl)">📄 {{ ev.fileName }}</span>
              <span class="ev-del" @click="removeEvidence(replyId, ev.id)">×</span>
            </div>
            <span class="ev-empty" v-if="!replyEvidences.length">尚未上传，请上传现场照片/处理凭证</span>
          </div>
          <button class="btn btn-outline mini" @click="addEvidence(replyId)">+ 上传佐证</button>
        </div>

        <div class="form-group">
          <span class="form-label">处理/解决情况</span>
          <textarea class="form-textarea" v-model="replyText" placeholder="例如：已安排维修人员于X月X日完成处理，并恢复正常。" />
        </div>

        <div class="sheet-actions weighted-actions">
          <button class="btn btn-ghost" @click="closeReply">取消</button>
          <button class="btn btn-primary" @click="submitReply" :disabled="submitting">提交完成<span class="btn-arrow">›</span></button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onActivated } from 'vue'
import api from '@/api'
import { toast } from '@/utils/ui'
import { redirectTo } from '@/utils/navigate'
import { getStorage } from '@/utils/storage'
import { pickAndUpload } from '@/utils/upload'

// 判断是否图片（按 fileType 或链接后缀）
function isImage(ev) {
  const t = (ev.fileType || '').toLowerCase()
  const u = (ev.fileUrl || '').toLowerCase()
  return /(jpg|jpeg|png|gif|webp)/.test(t) || /\.(jpg|jpeg|png|gif|webp)(\?|$)/.test(u)
}

// H5 新标签打开文件
function openFile(url) {
  if (url) window.open(url, '_blank')
}

const filter = ref('pending')
const counts = ref({ pending: 0, replied: 0, all: 0 })
const items = ref([])
const loading = ref(true)
// 回填面板
const replyVisible = ref(false)
const replyId = ref(null)
const replyEvidences = ref([])
const replyText = ref('')
const submitting = ref(false)

function noop() {}

async function loadTasks() {
  loading.value = true
  try {
    const all = await api.receptionPropertyTasks('all')
    const isPending = r => r.propertyStatus === 'dispatched' || r.propertyStatus === 'processing'
    const c = {
      all: all.length,
      pending: all.filter(isPending).length,
      replied: all.filter(r => r.propertyStatus === 'replied').length
    }
    const f = filter.value
    const list = f === 'all' ? all
      : f === 'pending' ? all.filter(isPending)
      : all.filter(r => r.propertyStatus === 'replied')
    items.value = list
    counts.value = c
    loading.value = false
    // 回填面板开着时同步当前工单的佐证
    if (replyVisible.value && replyId.value) {
      const cur = all.find(r => r.id === replyId.value)
      if (cur) replyEvidences.value = cur.evidences || []
    }
  } catch (e) {
    loading.value = false
  }
}

function switchFilter(f) {
  filter.value = f
  loadTasks()
}

// 物业未处理 → 开始处理
async function startTask(id) {
  try {
    await api.receptionPropertyStart(id)
    loadTasks()
  } catch (err) {
    toast({ title: err.message || '操作失败', icon: 'none' })
  }
}

// 上传佐证（处理中工单）—— 选图上传后调 receptionAddEvidence
async function addEvidence(id) {
  try {
    const r = await pickAndUpload('image/*')
    if (!r) return // 用户取消
    await api.receptionAddEvidence(id, r.fileName, r.fileType, r.url)
    toast({ title: '已上传', icon: 'success' })
    await loadTasks() // 刷新（含面板内 replyEvidences 同步）
  } catch (err) {
    toast({ title: err.message || '上传失败', icon: 'none' })
  }
}

async function removeEvidence(rid, evId) {
  try {
    await api.receptionRemoveEvidence(rid, evId)
    loadTasks()
  } catch (err) {
    toast({ title: err.message || '删除失败', icon: 'none' })
  }
}

// 填写结果并完成
function openReply(id) {
  const rec = items.value.find(r => r.id === id)
  replyVisible.value = true
  replyId.value = id
  replyText.value = ''
  replyEvidences.value = rec ? (rec.evidences || []) : []
}

function closeReply() {
  replyVisible.value = false
  replyId.value = null
  replyText.value = ''
  replyEvidences.value = []
}

async function submitReply() {
  if (submitting.value) return
  const reply = (replyText.value || '').trim()
  if (!reply) { toast({ title: '请填写处理结果', icon: 'none' }); return }
  if (!replyEvidences.value.length) { toast({ title: '请先上传处理佐证', icon: 'none' }); return }
  submitting.value = true
  try {
    await api.receptionPropertyReply(replyId.value, reply)
    toast({ title: '已完成', icon: 'success' })
    replyVisible.value = false
    replyId.value = null
    replyText.value = ''
    replyEvidences.value = []
    submitting.value = false
    loadTasks()
  } catch (err) {
    submitting.value = false
    toast({ title: err.message || '提交失败', icon: 'none' })
  }
}

function onShow() {
  const role = getStorage('activeRole')
  if (!role) { redirectTo('/pages/login/login'); return }
  loadTasks()
}

onMounted(onShow)
onActivated(onShow)
</script>

<style scoped>
.page { min-height: 100vh; background: #f4f5f7; padding: 24rpx 24rpx 40rpx; box-sizing: border-box; }
.intro { font-size: 28rpx; color: #666; display: block; margin-bottom: 24rpx; text-align: center; line-height: 1.6; }

.filter-tabs { display: flex; gap: 14rpx; margin-bottom: 24rpx; }
.f-tab { flex: 1; text-align: center; font-size: 30rpx; color: #6b7785; background: #fff; border-radius: 18rpx; padding: 18rpx 0; font-weight: 500; }
.f-tab.active { background: #FFF3DC; color: #C77800; font-weight: 700; }
.f-count { font-size: 28rpx; color: #777; }
.f-tab.active .f-count { color: #C77800; }

.task-card { background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.tc-header { display: flex; justify-content: space-between; align-items: center; }
.tc-visitor { font-size: 34rpx; font-weight: 700; color: #1f2329; }
.status-pill { font-size: 28rpx; font-weight: 600; padding: 4rpx 16rpx; border-radius: 12rpx; }
.status-pill.dispatched { background: #FDECEA; color: #E74C3C; }
.status-pill.processing { background: #FFF3DC; color: #E67E22; }
.status-pill.replied { background: #E8F7EE; color: #27AE60; }
.tc-meta { font-size: 28rpx; color: #666; margin: 12rpx 0; }
.tc-content { font-size: 32rpx; color: #33373d; line-height: 1.6; }

.tc-ev { margin-top: 16rpx; background: #fafbfc; border: 2rpx solid #f0f0f0; border-radius: 14rpx; padding: 16rpx 18rpx; }
.tc-ev-title { font-size: 28rpx; color: #666; }
.tc-ev-list { display: flex; flex-wrap: wrap; align-items: center; gap: 12rpx; margin-top: 12rpx; }
.tc-ev-name { font-size: 30rpx; color: #C77800; padding: 8rpx 0; }
.tc-ev-thumb { width: 120rpx; height: 120rpx; object-fit: cover; border-radius: 8rpx; border: 1rpx solid #eee; }

.ev-edit-list { margin-bottom: 14rpx; }
.ev-edit-item { display: flex; justify-content: space-between; align-items: center; padding: 10rpx 0; gap: 16rpx; }
.ev-edit-name { font-size: 30rpx; color: #C77800; flex: 1; }
.ev-edit-thumb { width: 120rpx; height: 120rpx; object-fit: cover; border-radius: 8rpx; border: 1rpx solid #eee; flex: none; }
.ev-del { font-size: 38rpx; color: #666; padding: 0 12rpx; }
.ev-empty { font-size: 28rpx; color: #777; }
.btn-outline { background: #fff; color: #C77800; border: 2rpx solid #FFCC44; }

.tc-reply { font-size: 30rpx; color: #33373d; line-height: 1.7; background: #E8F7EE; border-radius: 14rpx; padding: 18rpx 20rpx; margin-top: 16rpx; border-left: 6rpx solid #2ECC71; }
.tc-reply-label { font-weight: 700; color: #27AE60; }
.tc-reply-time { display: block; font-size: 28rpx; color: #666; margin-top: 6rpx; }

.tc-next { margin-top: 18rpx; display: flex; align-items: center; justify-content: space-between; gap: 14rpx; }
.tc-hint { font-size: 28rpx; color: #B8500B; flex: 1; }
.btn { font-size: 30rpx; border-radius: 22rpx; }
.btn.mini { padding: 8rpx 24rpx; }
.btn-primary { background: var(--c-primary-dark); color: #fff; }
.btn-ghost { background: #f2f2f2; color: #666; }

.empty-state { display: flex; flex-direction: column; align-items: center; color: #777; font-size: 32rpx; padding-top: 120rpx; }
.empty-emoji { font-size: 80rpx; margin-bottom: 20rpx; }

/* 回填面板 */
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.45); display: flex; align-items: flex-end; z-index: 100; }
.form-sheet { width: 100%; background: #fff; border-radius: 24rpx 24rpx 0 0; padding: 32rpx 28rpx calc(32rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.sheet-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24rpx; }
.sheet-title { font-size: 36rpx; font-weight: 700; color: #1f2329; }
.sheet-close { font-size: 48rpx; color: #666; line-height: 1; }
.form-group { margin-bottom: 24rpx; }
.form-label { font-size: 28rpx; color: #666; display: block; margin-bottom: 12rpx; }
.form-textarea { width: 100%; box-sizing: border-box; min-height: 200rpx; background: #f6f6f8; border-radius: 14rpx; padding: 20rpx; font-size: 32rpx; color: #1f2329; border: none; }
.form-textarea::placeholder { color: #666; }
.sheet-actions { display: flex; gap: 16rpx; }
.sheet-actions .btn { flex: 1; line-height: 2.6; font-size: 32rpx; }
</style>
