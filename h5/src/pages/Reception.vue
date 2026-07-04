<template>
  <div class="page" style="overflow-y:auto">
    <PageNav title="接待记录" />
    <div class="page-head">
      <div></div>
      <button v-if="canManage" class="head-btn" @click="openCreate">登记</button>
    </div>

    <!-- 接待制度公示：折叠成小文字串，点击展开查看 -->
    <div class="recep-system">
      <div class="rs-strip" @click="sysOpen = !sysOpen">
        <span class="rs-title">📍 接待制度公示</span>
        <span class="rs-strip-hint">{{ sysOpen ? '收起 ▴' : '点击查看 ›' }}</span>
      </div>
      <div v-if="sysOpen" class="rs-detail">
        <div class="rs-rows">
          <div class="rs-row"><span class="rs-key">定时</span><span class="rs-val">{{ sys.timeDesc }}</span></div>
          <div class="rs-row"><span class="rs-key">定点</span><span class="rs-val">{{ sys.place }}</span></div>
          <div class="rs-row"><span class="rs-key">定人</span><span class="rs-val">{{ sys.person }}</span></div>
        </div>
        <span v-if="canManage" class="rs-edit" @click.stop="openSysEditor">编辑</span>
      </div>
    </div>

    <!-- 月度统计 -->
    <div class="stats-row">
      <div class="stat-card">
        <span class="stat-num">{{ stats.monthCount }}<span class="stat-unit"> 次</span></span>
        <span class="stat-label">{{ stats.monthLabel }}</span>
      </div>
      <div class="stat-card">
        <span class="stat-num">{{ stats.pending }}<span class="stat-unit"> 件</span></span>
        <span class="stat-label">待办</span>
      </div>
      <div class="stat-card">
        <span class="stat-num">{{ stats.yearCount }}<span class="stat-unit"> 次</span></span>
        <span class="stat-label">年度已接待</span>
      </div>
    </div>

    <!-- 筛选标签 -->
    <div class="filter-tabs">
      <div class="f-tab" :class="filter === 'all' ? 'active' : ''" @click="switchFilter('all')">全部 <span class="f-count">{{ stats.total }}</span></div>
      <div class="f-tab" :class="filter === 'pending' ? 'active' : ''" @click="switchFilter('pending')">待跟进 <span class="f-count">{{ stats.pending }}</span></div>
      <div class="f-tab" :class="filter === 'done' ? 'active' : ''" @click="switchFilter('done')">已办结 <span class="f-count">{{ stats.done }}</span></div>
    </div>

    <!-- 接待记录列表 -->
    <div class="record-list">
      <template v-if="records.length">
        <div class="recep-card" v-for="item in records" :key="item.id">
          <div class="rc-header">
            <span class="rc-visitor">{{ item.visitorName }}{{ item.room ? ' · ' + item.room : '' }}</span>
            <span v-if="item.done" class="stage-pill ended">已办结</span>
          </div>
          <!-- 下一步指引：补齐闭环断点 -->
          <div v-if="item.nextAction" class="rc-next" :class="item.nextAction.action ? '' : 'info'">
            <span class="rc-next-text">👉 {{ item.nextAction.text }}</span>
            <span v-if="item.nextAction.action" class="rc-next-btn" @click="doNextAction(item.nextAction.action, item.id)">{{ item.nextAction.cta }}</span>
          </div>
          <div class="rc-meta">
            <span>📅 {{ item.date }} {{ item.time }}</span>
            <span v-if="item.receiver" class="rc-receiver">🧑 接待人：{{ item.receiver }}</span>
            <span class="rc-cat" :class="item.category">{{ item.categoryLabel }}</span>
          </div>
          <div class="rc-content">{{ item.content }}</div>
          <div class="rc-resolution" v-if="item.resolution">
            <span class="rc-res-label">处理/解决：</span>
            <span>{{ item.resolution }}</span>
          </div>
          <div class="rc-prop-reply" v-if="item.propertyReply">
            <span class="rc-res-label">物业反馈：</span>
            <span>{{ item.propertyReply }}</span>
            <span v-if="item.propertyRepliedBy" class="rc-prop-by">— {{ item.propertyRepliedBy }} {{ item.propertyRepliedAt }}</span>
          </div>
          <div class="rc-followup">
            <span class="rc-fu-title">后续工作</span>
            <div class="rc-fu-item">
              <span class="rc-fu-label">物业处理</span>
              <template v-if="!item.needsPropertyFeedback">
                <span class="rc-fu-na">非物业类·不适用</span>
              </template>
              <template v-else-if="item.propertyStatus === 'pending_dispatch'">
                <span v-if="canManage" class="fu-chip" @click="dispatchProperty(item.id)">转物业处理</span>
                <span v-else class="rc-fu-na">待派单</span>
              </template>
              <template v-else>
                <span class="rc-fu-na" :class="item.propertyStatus === 'replied' ? 'ok' : ''">{{ item.propertyStatusLabel }}</span>
              </template>
            </div>
            <div class="rc-fu-item">
              <span class="rc-fu-label">向业主反馈诉求解决情况</span>
              <template v-if="canManage">
                <span class="fu-chip" :class="item.fedOwner ? 'on' : ''" @click="toggleFollow(item.id, 'fedOwner')">{{ item.fedOwner ? '已反馈' : '反馈' }}</span>
              </template>
              <template v-else>
                <span class="rc-fu-na">{{ item.fedOwner ? '已反馈' : '待反馈' }}</span>
              </template>
            </div>
          </div>
          <!-- 佐证材料 -->
          <div class="rc-evidence">
            <div class="rc-ev-head">
              <span class="rc-ev-title">📎 佐证材料（{{ item.evidences ? item.evidences.length : 0 }}）</span>
              <span v-if="canManage" class="add-link" @click="addEvidence(item.id)">+ 上传</span>
            </div>
            <div v-if="item.evidences && item.evidences.length" class="rc-ev-list">
              <div v-for="ev in item.evidences" :key="ev.id" class="rc-ev-item">
                <img v-if="isImage(ev)" :src="ev.fileUrl" @click="openFile(ev.fileUrl)" class="rc-ev-thumb" />
                <span class="rc-ev-name" @click="openFile(ev.fileUrl)">{{ isImage(ev) ? ev.fileName : '📄 ' + ev.fileName }}</span>
                <span v-if="canManage" class="tp-del" @click="removeEvidence(item.id, ev.id)">×</span>
              </div>
            </div>
            <span v-else class="rc-ev-empty">可上传现场照片、处理凭证、反馈截图等</span>
          </div>

          <div v-if="canManage" class="rc-actions">
            <button class="btn btn-outline" @click="openResolutionEditor(item.id)">{{ item.resolution ? '修改处理结果' : '填写处理结果' }}</button>
            <button class="btn btn-ghost" @click="removeRecord(item.id)">删除</button>
          </div>
        </div>
      </template>
      <div v-else class="empty-state"><span>暂无接待记录</span></div>
    </div>

    <div v-if="createVisible" class="modal-mask" @click="closeCreate">
      <div class="form-sheet" @click.stop>
        <div class="sheet-head">
          <span class="sheet-title">新增接待记录</span>
          <span class="sheet-close" @click="closeCreate">×</span>
        </div>

        <!-- 分类 -->
        <span class="form-label">诉求分类</span>
        <div class="type-row">
          <span class="type-chip" :class="createForm.category === 'property' ? 'on' : ''" @click="pickCategory('property')">物业类</span>
          <span class="type-chip" :class="createForm.category === 'public_affairs' ? 'on' : ''" @click="pickCategory('public_affairs')">公共事务</span>
          <span class="type-chip" :class="createForm.category === 'neighbor' ? 'on' : ''" @click="pickCategory('neighbor')">邻里纠纷</span>
        </div>

        <!-- 日期时间 -->
        <div class="form-row">
          <div class="form-group half">
            <span class="form-label">接待日期 *</span>
            <input type="date" class="picker-field" :value="createForm.date" @change="onDateChange" />
          </div>
          <div class="form-group half">
            <span class="form-label">时间</span>
            <input type="time" class="picker-field" :value="createForm.time" @change="onTimeChange" />
          </div>
        </div>

        <!-- 来访业主 + 房号 -->
        <div class="form-row">
          <div class="form-group half">
            <span class="form-label">来访业主 *</span>
            <input class="form-input" v-model="createForm.visitorName" placeholder="姓名" />
          </div>
          <div class="form-group half">
            <span class="form-label">房号</span>
            <input class="form-input" v-model="createForm.room" placeholder="如 5号楼302" />
          </div>
        </div>

        <!-- 接待人 -->
        <div class="form-group">
          <span class="form-label">接待人</span>
          <input class="form-input" v-model="createForm.receiver" placeholder="如 张建国（主任）" />
        </div>

        <!-- 诉求内容 -->
        <div class="form-group">
          <span class="form-label">诉求内容 *</span>
          <textarea class="form-textarea" v-model="createForm.content" placeholder="简述业主反映的问题或建议"></textarea>
        </div>

        <div class="sheet-actions">
          <button class="btn btn-ghost" @click="closeCreate">取消</button>
          <button class="btn btn-primary" @click="submitCreate">确认登记</button>
        </div>
      </div>
    </div>

    <!-- 填写处理结果面板 -->
    <div v-if="resolutionVisible" class="modal-mask" @click="closeResolutionEditor">
      <div class="form-sheet" @click.stop>
        <div class="sheet-head">
          <span class="sheet-title">处理结果</span>
          <span class="sheet-close" @click="closeResolutionEditor">×</span>
        </div>
        <div class="form-group">
          <span class="form-label">记录处理/解决情况</span>
          <textarea class="form-textarea" style="min-height:140px;height:140px;" v-model="editingResolution" placeholder="例如：&#10;1. 已联系物业公司，约定7月20日前完成清理&#10;2. 物业已出具整改通知书&#10;3. 业主表示满意处理结果"></textarea>
        </div>
        <div class="sheet-actions">
          <button class="btn btn-ghost" @click="closeResolutionEditor">取消</button>
          <button class="btn btn-primary" @click="submitResolution">保存结果</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onMounted, onActivated } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import perm from '@/utils/perm'
import { toast, showModal } from '@/utils/ui'
import { pickAndUpload } from '@/utils/upload'

const sys = ref({ published: true, timeDesc: '', place: '', person: '' })
const sysOpen = ref(false)   // 接待制度公示：默认折叠，点击小文字串展开
const stats = ref({ monthCount: 0, monthLabel: '', pending: 0, yearCount: 0, done: 0, total: 0 })
const records = ref([])
const filter = ref('all')
const canManage = ref(false)
const canFeedback = ref(false)
const createVisible = ref(false)
const resolutionVisible = ref(false)
const editingId = ref(null)
const editingResolution = ref('')
const createForm = reactive({
  date: '',
  time: '14:00',
  visitorName: '',
  room: '',
  receiver: '',
  category: 'property',
  content: ''
})

async function loadAll() {
  try {
    const [sysData, recs, statsData] = await Promise.all([
      api.receptionSystem(),
      api.receptionRecords(filter.value),
      api.receptionStats()
    ])
    const now = new Date()
    sys.value = sysData || {}
    records.value = enrichRecords(recs)
    stats.value = {
      ...statsData,
      monthLabel: (now.getMonth() + 1) + '月已接待'
    }
  } catch (e) {
    toast({ title: e.message, icon: 'none' })
  }
}

// 给每条记录算出「下一步该做什么」——业委会视角，补上闭环断点
function enrichRecords(list) {
  const cm = canManage.value
  return (list || []).map((r) => {
    let nextAction = null
    if (cm && !r.done) {
      if (r.category === 'property') {
        if (r.propertyStatus === 'replied' && !r.fedOwner) {
          nextAction = { text: '物业已回复，下一步：把结果反馈给业主', action: 'fedOwner', cta: '向业主反馈' }
        } else if (!r.propertyStatus || r.propertyStatus === 'pending_dispatch') {
          nextAction = { text: '下一步：转物业处理', action: 'dispatch', cta: '转物业处理' }
        } else if (r.propertyStatus === 'dispatched') {
          nextAction = { text: '已派物业，等待物业受理', action: '', cta: '' }
        } else if (r.propertyStatus === 'processing') {
          nextAction = { text: '物业处理中，请等待回填结果', action: '', cta: '' }
        }
      } else if (!r.fedOwner) {
        nextAction = { text: '下一步：向业主反馈处理结果', action: 'fedOwner', cta: '向业主反馈' }
      }
    }
    return Object.assign({}, r, { nextAction })
  })
}

function doNextAction(action, id) {
  if (action === 'dispatch') {
    dispatchProperty(id)
  } else if (action === 'fedOwner') {
    toggleFollow(id, 'fedOwner')
  }
}

function switchFilter(f) {
  filter.value = f
  loadAll()
}

function openCreate() {
  if (!canManage.value) return
  createVisible.value = true
  Object.assign(createForm, {
    date: todayStr(),
    time: '14:00',
    visitorName: '',
    room: '',
    receiver: '',
    category: 'property',
    content: ''
  })
}

function closeCreate() {
  createVisible.value = false
}

function pickCategory(category) {
  createForm.category = category
}

function onDateChange(e) {
  createForm.date = e.target.value
}

function onTimeChange(e) {
  createForm.time = e.target.value
}

async function submitCreate() {
  if (!createForm.date || !createForm.visitorName || !createForm.content) {
    toast({ title: '请补全日期、来访业主和诉求内容', icon: 'none' })
    return
  }
  try {
    await api.receptionCreate({ ...createForm })
    toast({ title: '已登记', icon: 'success' })
    createVisible.value = false
    filter.value = 'all'
    loadAll()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function openSysEditor() {
  const res = await showModal({
    title: '编辑接待制度',
    editable: true,
    placeholderText: '定时：每周六上午9:00-11:00\n定点：物业办公室\n定人：张建国（主任）',
    content: sys.value.timeDesc + '\n' + sys.value.place + '\n' + sys.value.person
  })
  if (res.confirm && res.content) {
    const lines = res.content.split('\n').filter(l => l.trim())
    const updates = { timeDesc: lines[0] || '', place: lines[1] || '', person: lines[2] || '' }
    try {
      await api.receptionUpdateSystem(updates)
      loadAll()
    } catch (e) { toast({ title: e.message, icon: 'none' }) }
  }
}

function openResolutionEditor(id) {
  const record = records.value.find(function (r) { return r.id === id })
  resolutionVisible.value = true
  editingId.value = id
  editingResolution.value = record ? (record.resolution || '') : ''
}

function closeResolutionEditor() {
  resolutionVisible.value = false
  editingId.value = null
  editingResolution.value = ''
}

async function submitResolution() {
  const id = editingId.value
  const resolution = editingResolution.value
  if (!resolution.trim()) {
    toast({ title: '请输入处理结果', icon: 'none' })
    return
  }
  try {
    await api.receptionUpdateResolution(id, resolution.trim())
    toast({ title: '已保存', icon: 'success' })
    resolutionVisible.value = false
    editingId.value = null
    editingResolution.value = ''
    loadAll()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function dispatchProperty(id) {
  if (!canManage.value) return
  const res = await showModal({
    title: '转物业处理',
    content: '将该物业类诉求派给物业处理。物业会收到通知并回填处理结果。',
    confirmText: '确认派单'
  })
  if (!res.confirm) return
  try {
    await api.receptionDispatch(id)
    toast({ title: '已转物业', icon: 'success' })
    loadAll()
  } catch (err) { toast({ title: err.message || '派单失败', icon: 'none' }) }
}

async function toggleFollow(id, field) {
  if (field === 'fedProperty' && !canManage.value && !canFeedback.value) return
  if (field === 'fedOwner' && !canManage.value) return
  try {
    await api.receptionToggleFollow(id, field)
    loadAll()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function addEvidence(id) {
  try {
    const r = await pickAndUpload('image/*')
    if (!r) return
    await api.receptionAddEvidence(id, r.fileName, r.fileType, r.url)
    toast({ title: '已上传', icon: 'success' })
    loadAll()
  } catch (e) {
    toast({ title: e.message || '上传失败', icon: 'none' })
  }
}

function isImage(ev) {
  const t = (ev.fileType || '').toLowerCase()
  const u = (ev.fileUrl || '').toLowerCase()
  return /^(jpg|jpeg|png|gif|webp)$/.test(t) || /\.(jpg|jpeg|png|gif|webp)(\?|$)/.test(u)
}

function openFile(url) {
  if (url) window.open(url, '_blank')
}

async function removeEvidence(rid, evId) {
  try {
    await api.receptionRemoveEvidence(rid, evId)
    loadAll()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function removeRecord(id) {
  const res = await showModal({
    title: '确认删除',
    content: '确定删除该接待记录？'
  })
  if (res.confirm) {
    try {
      await api.receptionRemove(id)
      loadAll()
    } catch (e) { toast({ title: e.message, icon: 'none' }) }
  }
}

function todayStr() {
  const d = new Date()
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}

function show() {
  canManage.value = perm.can('reception.manage')
  canFeedback.value = perm.can('reception.property_feedback')
  loadAll()
}

onMounted(show)
onActivated(show)
</script>

<style scoped>
.page { min-height: 100vh; background: #f4f5f7; padding-bottom: 120rpx; }
.page-head { display: flex; align-items: center; justify-content: space-between; padding: 24rpx 24rpx 0; }
.page-title { display: block; font-size: 40rpx; font-weight: 700; color: #1f2329; }
.page-sub { display: block; font-size: 28rpx; color: #666; margin-top: 6rpx; }
.head-btn { background: var(--c-primary-dark); color: #fff; border-radius: 30rpx; font-size: 28rpx; font-weight: 600; padding: 0 28rpx; line-height: 2.6; border: none; }

/* 接待制度 */
.recep-system { margin: 24rpx 24rpx 0; background: #fff; border-radius: 24rpx; padding: 22rpx 26rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); border-left: 8rpx solid var(--c-primary-dark); }
/* 折叠态：一行可点击的小文字串 */
.rs-strip { display: flex; align-items: center; justify-content: space-between; cursor: pointer; }
.rs-strip-hint { font-size: 28rpx; color: var(--c-primary-dark); font-weight: 600; }
.rs-title { font-size: 30rpx; font-weight: 700; color: #1f2329; }
.rs-edit { display: inline-block; font-size: 28rpx; color: var(--c-primary-dark); font-weight: 600; }
/* 展开态：点击后显示的定时/定点/定人 */
.rs-detail { margin-top: 18rpx; padding-top: 16rpx; border-top: 2rpx dashed #e8e8e8; }
.rs-rows { display: flex; flex-direction: column; gap: 12rpx; }
.rs-row { display: flex; align-items: center; gap: 14rpx; }
.rs-key { font-size: 28rpx; font-weight: 600; color: #666; width: 64rpx; flex-shrink: 0; }
.rs-val { font-size: 30rpx; color: #33373d; }
.rs-detail .rs-edit { margin-top: 16rpx; }

/* 统计 */
.stats-row { display: flex; gap: 16rpx; padding: 24rpx; }
.stat-card { flex: 1; background: #fff; border-radius: 24rpx; padding: 26rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); text-align: center; }
.stat-num { font-size: 48rpx; font-weight: 700; color: #1f2329; display: block; }
.stat-unit { font-size: 28rpx; font-weight: 400; color: #666; }
.stat-label { font-size: 28rpx; color: #666; margin-top: 8rpx; display: block; }

/* 筛选 */
.filter-tabs { display: flex; gap: 0; margin: 0 24rpx 20rpx; background: #fff; border-radius: 18rpx; padding: 8rpx; }
.f-tab { flex: 1; padding: 16rpx 8rpx; text-align: center; font-size: 28rpx; color: #666; border-radius: 12rpx; display: flex; align-items: center; justify-content: center; gap: 8rpx; }
.f-tab.active { background: var(--c-primary-dark); color: #fff; font-weight: 700; }
.f-count { font-size: 28rpx; opacity: 0.85; }

/* 记录卡片 */
.record-list { padding: 0 24rpx; display: flex; flex-direction: column; gap: 20rpx; }
.recep-card { background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.rc-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 12rpx; }
.rc-visitor { font-size: 34rpx; font-weight: 700; color: #1f2329; }
.rc-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 14rpx; font-size: 28rpx; color: #666; margin-bottom: 14rpx; }
.rc-receiver { font-size: 28rpx; color: #666; }
.rc-cat { font-size: 28rpx; font-weight: 600; padding: 4rpx 16rpx; border-radius: 10rpx; }
.rc-cat.property { background: #FFF0E0; color: #E67E22; }
.rc-cat.public_affairs { background: #EBF5FB; color: #2980B9; }
.rc-cat.neighbor { background: #F5EEF8; color: #9B59B6; }
.rc-cat.other { background: #f0f0f0; color: #666; }
.rc-content { font-size: 32rpx; color: #33373d; line-height: 1.6; background: #fafbfc; border-radius: 14rpx; padding: 18rpx 20rpx; margin: 14rpx 0; }
.rc-resolution { font-size: 30rpx; color: #33373d; line-height: 1.6; background: #F0F9F4; border-radius: 14rpx; padding: 18rpx 20rpx; margin: 14rpx 0; border-left: 6rpx solid #2ECC71; }
.rc-res-label { font-weight: 700; color: #27AE60; }
.rc-next { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; background: #FFF6E9; border: 2rpx solid #FFE0A6; border-radius: 16rpx; padding: 16rpx 20rpx; margin: 14rpx 0; }
.rc-next.info { background: #F5F7FA; border-color: #E6E9ee; }
.rc-next-text { font-size: 28rpx; color: #B8500B; flex: 1; }
.rc-next.info .rc-next-text { color: #8a93a0; }
.rc-next-btn { font-size: 28rpx; font-weight: 600; color: #fff; background: var(--c-primary-dark); padding: 10rpx 24rpx; border-radius: 24rpx; white-space: nowrap; }
.rc-prop-reply { font-size: 30rpx; color: #33373d; line-height: 1.6; background: #FFF6E9; border-radius: 14rpx; padding: 18rpx 20rpx; margin: 14rpx 0; border-left: 6rpx solid #FFA800; }
.rc-prop-reply .rc-res-label { color: #E67E22; }
.rc-prop-by { display: block; font-size: 28rpx; color: #777; margin-top: 8rpx; }
.rc-followup { display: flex; flex-direction: column; gap: 14rpx; background: #FBFBFB; border: 2rpx solid #f0f0f0; border-radius: 16rpx; padding: 18rpx 20rpx; margin-bottom: 14rpx; }
.rc-fu-title { font-size: 28rpx; font-weight: 600; color: #666; }
.rc-fu-item { display: flex; align-items: center; justify-content: space-between; }
.rc-fu-label { font-size: 28rpx; color: #666; }
.rc-fu-na { font-size: 28rpx; color: #666; }
.rc-fu-na.ok { color: #27AE60; font-weight: 600; }
.fu-chip { font-size: 28rpx; padding: 8rpx 22rpx; border-radius: 24rpx; background: #FFF3DC; border: 2rpx solid #FFCC44; color: #C77800; font-weight: 600; }
.fu-chip.on { background: #E8F7EE; border-color: #27AE60; color: #27AE60; font-weight: 600; }
.rc-evidence { background: #fafbfc; border: 2rpx solid #f0f0f0; border-radius: 16rpx; padding: 18rpx 20rpx; margin-bottom: 14rpx; }
.rc-ev-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10rpx; }
.rc-ev-title { font-size: 28rpx; font-weight: 600; color: #666; }
.add-link { font-size: 28rpx; color: #FFA800; font-weight: 600; }
.rc-ev-list { display: flex; flex-direction: column; gap: 8rpx; }
.rc-ev-item { display: flex; align-items: center; gap: 16rpx; padding: 12rpx 18rpx; background: #fff; border-radius: 12rpx; font-size: 28rpx; color: #444; }
.rc-ev-thumb { width: 120rpx; height: 120rpx; object-fit: cover; border-radius: 8rpx; border: 1rpx solid #eee; flex-shrink: 0; cursor: pointer; }
.rc-ev-name { flex: 1; min-width: 0; word-break: break-all; color: #2980B9; cursor: pointer; }
.rc-ev-empty { font-size: 28rpx; color: #666; text-align: center; padding: 16rpx 0; }
.tp-del { font-size: 34rpx; color: #666; margin-left: 16rpx; flex-shrink: 0; }

.rc-actions { display: flex; gap: 14rpx; justify-content: flex-end; padding-top: 16rpx; border-top: 2rpx solid #f5f5f5; }

/* 创建弹窗 */
.modal-mask { position: fixed; inset: 0; z-index: 50; background: rgba(0,0,0,0.36); display: flex; align-items: flex-end; }
.form-sheet { width: 100%; max-height: 88vh; overflow: auto; background: #fff; border-radius: 24rpx 24rpx 0 0; padding: 32rpx 28rpx calc(32rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.sheet-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }
.sheet-title { font-size: 36rpx; font-weight: 700; color: #1f2329; }
.sheet-close { width: 56rpx; height: 56rpx; line-height: 52rpx; text-align: center; border-radius: 28rpx; font-size: 40rpx; color: #666; background: #f5f5f5; flex-shrink: 0; }

.type-row { display: flex; flex-wrap: wrap; gap: 14rpx; margin-bottom: 12rpx; }
.type-chip { min-height: 60rpx; box-sizing: border-box; display: flex; align-items: center; justify-content: center; font-size: 28rpx; color: #666; background: #f5f5f5; padding: 10rpx 24rpx; border-radius: 30rpx; }
.type-chip.on { color: #fff; background: #FFA800; font-weight: 600; }

.form-row { display: flex; gap: 16rpx; align-items: flex-start; }
.form-group { margin-bottom: 20rpx; min-width: 0; }
.form-group.half { flex: 1; min-width: 0; }
.form-label { display: block; font-size: 28rpx; color: #777; margin-bottom: 12rpx; line-height: 1.45; }

.form-input, .form-textarea { width: 100%; box-sizing: border-box; background: #f6f6f8; border-radius: 14rpx; font-size: 32rpx; color: #1f2329; border: none; }
.form-input { height: 88rpx; min-height: 88rpx; line-height: normal; padding: 0 20rpx; }
.form-input::placeholder, .form-textarea::placeholder { color: #666; font-size: 30rpx; }
.picker-field { width: 100%; min-height: 88rpx; box-sizing: border-box; background: #f6f6f8; border: none; border-radius: 14rpx; padding: 0 20rpx; font-size: 32rpx; color: #1f2329; line-height: normal; display: flex; align-items: center; }
.form-textarea { min-height: 200rpx; height: 200rpx; line-height: 1.5; padding: 20rpx; }

.sheet-actions { display: flex; gap: 16rpx; padding-top: 12rpx; }
.sheet-actions .btn { flex: 1; min-width: 0; height: 88rpx; line-height: 88rpx; border-radius: 44rpx; font-size: 32rpx; font-weight: 600; padding: 0 24rpx; margin: 0; border: 0; display: flex; align-items: center; justify-content: center; box-sizing: border-box; }
.btn-ghost { color: #777; background: #f5f5f5; }
.btn-primary { color: #fff; background: var(--c-primary-dark); }
</style>
