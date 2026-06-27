<template>
  <div class="page" v-if="view">
    <div class="hd-card">
      <span class="hd-title">{{ view.title }}</span>
      <span class="hd-sub">{{ view.sub }}</span>
      <div class="hd-tags">
        <span class="hd-tag" v-for="(item, idx) in view.tags" :key="idx">{{ item }}</span>
      </div>
    </div>

    <div class="sec" v-if="view.desc">
      <span class="sec-title">说明</span>
      <span class="sec-text">{{ view.desc }}</span>
    </div>

    <div class="sec" v-if="view.signInText">
      <span class="sec-title">签到情况</span>
      <span class="sec-text">{{ view.signInText }}</span>
    </div>

    <div class="sec" v-if="view.hasMinutes">
      <span class="sec-title">会议纪要</span>
      <div class="doc-row" @click="openMinutes">
        <span class="doc-icon">DOC</span>
        <span class="doc-name">查看会议纪要</span>
        <span class="doc-arrow">›</span>
      </div>
    </div>

    <div class="sec" v-if="view.materials.length">
      <span class="sec-title">会议材料（{{ view.materials.length }}）</span>
      <div class="doc-row" v-for="(item, index) in view.materials" :key="item.name"
           @click="previewMaterial(index)">
        <img v-if="isImage(item.fileType, item.url)" class="doc-thumb" :src="item.url" />
        <span v-else class="doc-icon">FILE</span>
        <span class="doc-name">{{ isImage(item.fileType, item.url) ? item.name : ('📄 ' + item.name) }}</span>
        <span class="doc-size">{{ item.sizeText || '' }}</span>
      </div>
    </div>

    <div class="sec">
      <div class="sec-head">
        <span class="sec-title">归档后补充（{{ view.archiveExtras ? view.archiveExtras.length : 0 }}）</span>
        <span class="sec-action" v-if="view.canSupplement" @click="addArchiveExtra">+ 补充</span>
      </div>
      <div v-if="view.archiveExtras && view.archiveExtras.length">
        <div class="doc-row supplement" v-for="(item, index) in view.archiveExtras" :key="item.id">
          <img v-if="isImage(item.fileType, item.url)" class="doc-thumb" :src="item.url"
               @click="openFile(item.url)" />
          <span v-else class="doc-icon" @click="openFile(item.url)">SUP</span>
          <div class="doc-main" @click="openFile(item.url)">
            <span class="doc-name">{{ isImage(item.fileType, item.url) ? item.fileName : ('📄 ' + item.fileName) }}</span>
            <span class="doc-meta">{{ item.reason || '补充归档资料' }}{{ item.addedBy ? ' · ' + item.addedBy : '' }}</span>
            <span class="doc-size" v-if="item.sizeText">{{ item.sizeText }}</span>
          </div>
          <span class="doc-detail" @click.stop="previewArchiveExtra(index)">详情</span>
        </div>
      </div>
      <span v-else class="empty-line">暂无归档后补充材料</span>
    </div>

    <div class="sec" v-if="view.evidences.length">
      <span class="sec-title">佐证材料（{{ view.evidences.length }}）</span>
      <div class="doc-row" v-for="item in view.evidences" :key="item.id"
           @click="openFile(item.fileUrl)">
        <img v-if="isImage(item.fileType, item.fileUrl)" class="doc-thumb" :src="item.fileUrl" />
        <span v-else class="doc-icon">FILE</span>
        <span class="doc-name">{{ isImage(item.fileType, item.fileUrl) ? item.fileName : ('📄 ' + item.fileName) }}</span>
      </div>
    </div>

    <!-- 归档管理（仅主任/副主任） -->
    <div class="sec" v-if="view.isCommittee && view.isChair">
      <span class="sec-title">归档管理</span>
      <span class="life-state">当前状态：{{ view.published ? '已公示归档' : view.withdrawn ? '公示已撤回（仍在档）' : '已归档（未公示）' }}</span>
      <span class="life-reason" v-if="view.withdrawn && view.withdrawReason">撤回原因：{{ view.withdrawReason }}</span>
      <span class="life-hint" v-if="view.published">已公示会议如需撤出，请先撤回公示，再撤销归档。</span>

      <div class="life-actions">
        <button v-if="view.published" class="btn btn-outline" @click="withdrawPublish">撤回公示</button>
        <button v-if="view.withdrawn" class="btn btn-outline" @click="rePublish">修订后重新公示</button>
        <button class="btn btn-outline" @click="viewMinutesRevisions">修订历史{{ view.minutesRevisionCount ? '（' + view.minutesRevisionCount + '）' : '' }}</button>
        <button v-if="!view.published" class="btn btn-danger" @click="revokeArchive">撤销归档</button>
      </div>

      <div class="life-log" v-if="view.archiveLog.length">
        <span class="life-log-title">操作记录</span>
        <div class="life-log-row" v-for="item in view.archiveLog" :key="item.id">
          <span class="llr-action">{{ item.action }}</span>
          <span class="llr-meta">{{ item.operator }} · {{ item.at }}</span>
          <span class="llr-reason" v-if="item.reason">{{ item.reason }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import perm from '@/utils/perm'
import { toast, showModal } from '@/utils/ui'
import { navigateTo, navigateBack } from '@/utils/navigate'
import { pickAndUpload, humanSize } from '@/utils/upload'

// 判断是否图片：按 fileType 后缀或 url 结尾
function isImage(fileType, url) {
  const re = /\.(jpg|jpeg|png|gif|webp)$/i
  const t = (fileType || '').toLowerCase()
  if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(t)) return true
  if (t && re.test('.' + t)) return true
  return !!(url && re.test(url))
}

// H5 新标签打开文件
function openFile(url) {
  if (!url) { toast({ title: '该文件暂无链接', icon: 'none' }); return }
  window.open(url, '_blank')
}

const LEARNING_TYPE_LABEL = { internal: '内部学习', street: '街道培训', special: '专项学习' }
const COMPLIANCE_LABEL = { valid: '会议有效', flawed: '有效（带说明）', invalid: '会议无效' }

const route = useRoute()
const view = ref(null)
const loading = ref(true)

let kind = 'committee'
let id = null

onMounted(() => {
  kind = route.query.kind || 'committee'
  id = parseInt(route.query.id)
  load()
})

async function load() {
  loading.value = true
  try {
    let v
    if (kind === 'owner') {
      toast({ title: '业主大会模块已停用', icon: 'none' })
      navigateBack()
      return
    } else if (kind === 'learning') v = await buildLearning()
    else v = await buildCommittee()
    view.value = v
    loading.value = false
  } catch (e) {
    loading.value = false
    toast({ title: e.message || '加载失败', icon: 'none' })
  }
}

async function buildCommittee() {
  const d = await api.committeeDetail(id)
  const published = d.publish && d.publish.published
  const withdrawn = d.publish && d.publish.withdrawn
  const isChair = perm.isChair()
  return {
    title: d.title,
    sub: (d.meetingDate || '') + ' ' + (d.meetingTime || '') + (d.location ? ' · ' + d.location : ''),
    tags: [COMPLIANCE_LABEL[d.compliance] || '', published ? '已公示' : withdrawn ? '公示已撤回' : '已归档'].filter(Boolean),
    desc: '',
    hasMinutes: true, minutesFrom: 'library',
    materials: d.materials || [],
    archiveExtras: d.archiveExtras || [],
    evidences: (d.record && d.record.evidences) || [],
    signInText: '',
    canSupplement: isChair,
    // 归档管理（仅主任/副主任，委员会会议）
    isCommittee: true,
    isChair: isChair,
    published: !!published,
    withdrawn: !!withdrawn,
    withdrawReason: (d.publish && d.publish.withdrawReason) || '',
    archiveLog: d.archiveLog || [],
    minutesRevisionCount: d.minutesRevisionCount || 0
  }
}

async function buildLearning() {
  const list = await api.learningList('', 'ended')
  const d = (list || []).find(x => x.id === id) || {}
  const signIns = d.signIns || {}
  const total = Object.keys(signIns).length
  const signed = Object.keys(signIns).filter(k => signIns[k]).length
  return {
    title: d.title || '',
    sub: (d.date || '') + ' ' + (d.time || '') + (d.location ? ' · ' + d.location : '') + (d.trainer ? ' · 讲师 ' + d.trainer : ''),
    tags: [LEARNING_TYPE_LABEL[d.type] || '学习', '已完成'],
    desc: d.description || '',
    hasMinutes: false, minutesFrom: '',
    materials: [],
    archiveExtras: [],
    evidences: d.evidences || [],
    signInText: total ? ('签到 ' + signed + '/' + total + ' 人') : '',
    canSupplement: false
  }
}

function openMinutes() {
  const v = view.value
  if (!v || !v.hasMinutes) return
  navigateTo('/pages/minutes/minutes?meetingId=' + id + '&from=' + v.minutesFrom)
}

function previewMaterial(idx) {
  const mt = view.value.materials[idx]
  if (!mt) return
  if (mt.url) { openFile(mt.url); return } // 有真实文件链接则新标签打开
  showModal({ title: mt.name, content: mt.content || '（暂无预览内容）', showCancel: false, confirmText: '关闭' })
}

function previewArchiveExtra(idx) {
  const item = view.value.archiveExtras[idx]
  if (!item) return
  showModal({
    title: item.fileName,
    content: '补充原因：' + (item.reason || '补充归档资料') + '\n补充人：' + (item.addedBy || '—') + '\n补充时间：' + (item.addedAt || '—'),
    showCancel: false,
    confirmText: '关闭'
  })
}

// 选文件上传补充归档材料（先填补充原因，再选文件上传）
async function addArchiveExtra() {
  const res = await showModal({
    title: '补充归档材料',
    content: '请先填写补充原因，再选择文件上传。',
    editable: true,
    placeholderText: '请填写补充原因（必填）'
  })
  if (!res.confirm) return
  const reason = (res.content || '').trim()
  if (!reason) { toast({ title: '请填写补充原因', icon: 'none' }); return }
  try {
    const r = await pickAndUpload('*/*')
    if (!r) return // 用户取消
    await api.committeeAddArchiveExtra(id, r.fileName, humanSize(r.fileSize), r.fileType, reason, r.url)
    toast({ title: '已上传', icon: 'success' })
    load()
  } catch (e) {
    toast({ title: e.message || '上传失败', icon: 'none' })
  }
}

// 撤回公示（必填原因，仍留在资料库，可重新公示或撤销归档）
async function withdrawPublish() {
  const res = await showModal({
    title: '撤回公示',
    content: '撤回后业主将看到"已撤回"状态，会议仍在档。如需彻底撤出，请再走"撤销归档"。',
    editable: true,
    placeholderText: '请填写撤回原因（必填）'
  })
  if (!res.confirm) return
  const reason = (res.content || '').trim()
  if (!reason) { toast({ title: '撤回必须填写原因', icon: 'none' }); return }
  try {
    await api.committeeWithdrawPublish(id, reason)
    toast({ title: '已撤回公示', icon: 'success' })
    load()
  } catch (e) { toast({ title: e.message || '撤回失败', icon: 'none' }) }
}

// 修订后重新公示
async function rePublish() {
  try {
    await api.committeePublish(id)
    toast({ title: '已重新公示', icon: 'success' })
    load()
  } catch (e) { toast({ title: e.message || '公示失败', icon: 'none' }) }
}

// 撤销归档（仅误归档用，必填原因；已公示需先撤回公示）
async function revokeArchive() {
  const res = await showModal({
    title: '撤销归档',
    content: '仅用于误归档（归错会议/误点归档/未结束就归档）。撤销后会议撤出资料库，回到归档前阶段。',
    editable: true,
    placeholderText: '请填写撤销原因（必填）'
  })
  if (!res.confirm) return
  const reason = (res.content || '').trim()
  if (!reason) { toast({ title: '撤销必须填写原因', icon: 'none' }); return }
  try {
    await api.committeeRevokeArchive(id, reason)
    toast({ title: '已撤销归档', icon: 'success' })
    setTimeout(() => { navigateBack() }, 600)
  } catch (e) { toast({ title: e.message || '撤销失败', icon: 'none' }) }
}

async function viewMinutesRevisions() {
  try {
    const list = await api.committeeMinutesRevisions(id)
    if (!list || !list.length) { toast({ title: '暂无修订记录', icon: 'none' }); return }
    const lines = list.map((r) => {
      return 'v' + r.versionNo + ' · ' + (r.editorName || '—') + ' · ' + (r.createdAt || '') +
        (r.reason ? '\n  原因：' + r.reason : '')
    })
    showModal({ title: '纪要修订历史', content: lines.join('\n'), showCancel: false, confirmText: '关闭' })
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}
</script>

<style scoped>
.page { min-height: 100vh; background: #f4f5f7; padding: 24rpx 24rpx 40rpx; box-sizing: border-box; }

.hd-card { background: #fff; border-radius: 24rpx; padding: 32rpx 28rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.hd-title { font-size: 40rpx; font-weight: 700; color: #1f2329; display: block; line-height: 1.4; }
.hd-sub { font-size: 28rpx; color: #666; display: block; margin-top: 10rpx; }
.hd-tags { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 18rpx; }
.hd-tag { font-size: 28rpx; font-weight: 600; color: #C77800; background: #FFF3DC; border-radius: 10rpx; padding: 4rpx 16rpx; }

.sec { background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.sec-head { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; margin-bottom: 16rpx; }
.sec-title { font-size: 28rpx; font-weight: 600; color: #666; display: block; margin-bottom: 16rpx; }
.sec-head .sec-title { margin-bottom: 0; }
.sec-action { font-size: 28rpx; color: #FFA800; font-weight: 700; background: #FFF6E5; border-radius: 14rpx; padding: 8rpx 20rpx; flex-shrink: 0; }
.sec-text { font-size: 32rpx; color: #33373d; line-height: 1.7; }

.doc-row { display: flex; align-items: center; gap: 16rpx; padding: 20rpx; background: #fafbfc; border: 2rpx solid #f0f0f0; border-radius: 14rpx; margin-top: 12rpx; }
.doc-icon { width: 64rpx; height: 48rpx; border-radius: 10rpx; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: #f0f0f0; color: #777; font-size: 28rpx; font-weight: 700; cursor: pointer; }
.doc-thumb { width: 120rpx; height: 120rpx; object-fit: cover; border-radius: 8rpx; border: 1rpx solid #eee; flex-shrink: 0; cursor: pointer; }
.doc-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.doc-name { flex: 1; font-size: 30rpx; color: #33373d; word-break: break-all; }
.doc-meta { font-size: 28rpx; color: #666; line-height: 1.5; }
.doc-size { font-size: 28rpx; color: #777; }
.doc-detail { font-size: 28rpx; color: #FFA800; font-weight: 700; background: #FFF6E5; border-radius: 12rpx; padding: 12rpx 22rpx; flex-shrink: 0; cursor: pointer; }
.doc-arrow { font-size: 36rpx; color: #FFA800; }
.doc-row.supplement { background: #FFFDF7; border-color: #FFE8B8; }
.empty-line { display: block; font-size: 28rpx; color: #777; padding: 14rpx 0 4rpx; }

/* 归档管理 */
.life-state { display: block; font-size: 30rpx; color: #33373d; font-weight: 600; }
.life-reason { display: block; font-size: 28rpx; color: #E67E22; margin-top: 8rpx; }
.life-hint { display: block; font-size: 28rpx; color: #666; margin-top: 8rpx; line-height: 1.6; }
.life-actions { display: flex; flex-wrap: wrap; gap: 14rpx; margin-top: 20rpx; }
.life-actions .btn { margin: 0; }
.life-log { margin-top: 24rpx; border-top: 2rpx solid #f0f0f0; padding-top: 18rpx; }
.life-log-title { display: block; font-size: 28rpx; color: #666; margin-bottom: 12rpx; }
.life-log-row { display: flex; flex-wrap: wrap; align-items: baseline; gap: 12rpx; padding: 8rpx 0; }
.llr-action { font-size: 28rpx; color: #E74C3C; font-weight: 600; }
.llr-meta { font-size: 28rpx; color: #777; }
.llr-reason { font-size: 28rpx; color: #666; width: 100%; }
</style>
