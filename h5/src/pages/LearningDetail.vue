<template>
  <div class="page" style="overflow-y:auto;">
    <!-- 导航新规(0725 用户定):返回=历史上一页(学习列表 push 进入,新建成功是 replace 落进来,回退都到列表) -->
    <PageNav :title="navTitle" style="margin: -24rpx -24rpx 20rpx">
      <template #right>
        <button class="nav-home" @click="goHome">首页</button>
      </template>
    </PageNav>
    <div v-if="item">
      <!-- 头部：标题 + 阶段 -->
      <div class="detail-head">
        <span class="dh-title">{{ item.title }}</span>
        <span class="stage-pill" :class="item.stage">{{ item.stage === 'preparing' ? (item.notified ? '已通知' : '待通知') : item.stage === 'ongoing' ? '待整理' : '已完成' }}</span>
      </div>

      <!-- 基本信息 -->
      <div class="info-card">
        <div class="field-row">
          <span class="field-label">分类</span>
          <!-- 分类只读（0729 用户定：发起/登记时已定，详情不再可改） -->
          <span class="field-val tag" :class="'cat-' + currentCategory">{{ currentCategory === 'external' ? '外部培训' : '内部学习' }}</span>
        </div>
        <div class="field-row">
          <span class="field-label">时间</span>
          <span class="field-val">{{ item.date }} {{ formatTime(item.time) }}</span>
        </div>
        <div class="field-row">
          <span class="field-label">地点</span>
          <span class="field-val">{{ item.location || '未填写' }}</span>
        </div>
        <div class="field-row">
          <span class="field-label">组织单位</span>
          <span class="field-val">{{ organizerDisplay }}</span>
        </div>
        <div class="field-row">
          <span class="field-label">{{ item.stage === 'preparing' ? '计划参加' : '通知人员' }}</span>
          <span class="field-val">{{ attendeesDisplay }}</span>
        </div>
      </div>

      <!-- 内容说明 -->
      <div class="content-card" v-if="item.description">
        <span class="cc-label">学习内容</span>
        <span class="cc-text">{{ item.description }}</span>
      </div>

      <!-- 培训前：通知人员（默认收起，全选+单独勾选，与会议通知页同款；未通知可改，发送即按所选落库） -->
      <div class="sign-card" v-if="item.stage === 'preparing' && (members.length || item._signList.length)">
        <div class="nc2-head" @click="noticeListOpen = !noticeListOpen">
          <span class="sign-title">通知人员</span>
          <div class="nc2-right">
            <div v-if="!item.notified" class="mc-all" @click.stop="toggleAll">
              <div class="mc-check" :class="{ on: allChecked }">{{ allChecked ? '✓' : '' }}</div>
              <span class="mc-all-label">全选</span>
            </div>
            <span class="sign-stat" :class="item.notified ? 'ok' : ''">{{ item.notified ? '已通知全员' : ('已选 ' + selectedCount + '/' + members.length + ' 人') }}</span>
            <span class="nc2-arrow" :class="{ open: noticeListOpen }"></span>
          </div>
        </div>
        <div v-if="noticeListOpen" class="sign-list nc2-list">
          <template v-if="!item.notified">
            <div v-for="m in members" :key="m.userRoleId" class="mc-item" @click="toggleMember(m.userRoleId)">
              <div class="mc-check" :class="{ on: m.checked }">{{ m.checked ? '✓' : '' }}</div>
              <div class="mc-person">
                <span class="mc-name">{{ m.name }}</span>
                <span v-if="m.role" class="mc-role">{{ m.role }}</span>
              </div>
            </div>
          </template>
          <template v-else>
            <div v-for="s in item._signList" :key="s.name" class="sign-row"><span class="sign-name">{{ s.name }}</span></div>
          </template>
        </div>
        <div v-if="!item.notified && canManage" class="sign-my">
          <button class="btn-signin btn-notify" @click="notifyAll">发送通知</button>
        </div>
      </div>

      <!-- 进行中/已完成：实际参加人员（只读；登记在独立页「登记结果」进行） -->
      <div class="sign-card" v-if="(item.stage === 'ongoing' || item.stage === 'ended') && item._signList.length">
        <div class="sign-head">
          <span class="sign-title">实际参加人员</span>
          <span class="sign-stat ok">{{ item._signedCount }}/{{ item._totalCount }} 人参加</span>
        </div>
        <div class="sign-list">
          <div v-for="s in item._signList" :key="s.name" class="sign-row" :class="s.signed ? 'done' : ''">
            <span class="sign-name">{{ s.name }}</span>
            <span class="sign-status" :class="s.signed ? 'ok' : 'wait'">{{ s.signed ? '已参加' : '未参加' }}</span>
          </div>
        </div>
      </div>

      <!-- 通知与留档材料 -->
      <div class="ev-card">
        <div class="ev-head">
          <span class="ev-title">{{ catNoun }}材料（{{ item.evidences ? item.evidences.length : 0 }}）</span>
        </div>
        <div v-if="item.evidences && item.evidences.length" class="ev-list">
          <div v-for="ev in item.evidences" :key="ev.id" class="ev-item">
            <img v-if="isImage(ev)" class="ev-thumb" :src="ev.fileUrl" @click="openFile(ev.fileUrl)" />
            <span v-else class="ev-name ev-link" @click="openFile(ev.fileUrl)">📄 {{ ev.fileName }}</span>
            <span v-if="isImage(ev)" class="ev-name">{{ ev.fileName }}</span>
            <span v-if="item.stage !== 'ended' && canManage" class="ev-del" @click="removeEvidence(ev.id)">×</span>
          </div>
        </div>
        <span v-else class="ev-empty">
          <template v-if="item.stage === 'preparing'">可上传{{ catNoun }}通知、课件或{{ catNoun }}材料</template>
          <template v-else-if="item.stage === 'ongoing'">可上传{{ catNoun }}记录、课件、照片等</template>
          <template v-else>暂无归档资料</template>
        </span>
        <!-- 上传：下方居中大按钮（0729 用户定） -->
        <button v-if="item.stage !== 'ended' && canManage" type="button" class="ev-add-btn" @click="addEvidence">＋ 上传材料</button>
      </div>

      <!-- 管理操作 -->
      <div class="action-card" v-if="item.stage !== 'ended' && canManage">
        <span v-if="item.stage === 'preparing' && !item.notified" class="ac-hint">请先通知参加人员，通知后才能登记结果</span>
        <button v-if="item.stage === 'preparing'" class="btn-primary" :disabled="!item.notified" @click="goRegister">登记{{ catNoun }}结果</button>
        <button v-if="item.stage === 'ongoing'" class="btn-primary" @click="goRegister">继续登记{{ catNoun }}结果</button>
      </div>

      <div class="perm-note" v-if="item.stage !== 'ended' && !canManage">
        <span>仅负责人可登记培训结果和完成留档</span>
      </div>

      <div class="done-card" v-if="item.stage === 'ended'">
        <span class="done-text">本次学习培训已完成留档</span>
      </div>

      <div class="delete-area" v-if="canManage">
        <span class="delete-link" @click="removeLearning">删除此学习记录</span>
      </div>
    </div>

    <div v-else class="empty-state"><span>加载中...</span></div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import perm from '@/utils/perm'
import { toast, showModal } from '@/utils/ui'
import { navigateBack, goModuleHome } from '@/utils/navigate'
import { pickAndUpload } from '@/utils/upload'
import { getStorage } from '@/utils/storage'

const route = useRoute()
const item = ref(null)
const canManage = ref(false)   // 可以操作（创建/推进）= 主任/副主任/委员
const selectedAttendance = ref([])
const noticeListOpen = ref(false)   // 通知人员列表默认收起
// 通知人员选择（业委会成员，默认全选、可展开单独勾选）——发送通知时按所选落库
const members = ref([])             // [{ userRoleId, name, role, checked }]
const memberTotal = computed(() => members.value.length)
const memberNames = computed(() => members.value.map(m => m.name).filter(Boolean))
const selectedCount = computed(() => members.value.filter(m => m.checked).length)
const allChecked = computed(() => members.value.length > 0 && members.value.every(m => m.checked))
function toggleAll() { const t = !allChecked.value; members.value.forEach(m => { m.checked = t }) }
function toggleMember(mid) { const m = members.value.find(x => x.userRoleId === mid); if (m) m.checked = !m.checked }
function selectedNames() { return members.value.filter(m => m.checked).map(m => m.name) }
// 显式分类（内部学习/外部培训），空值按内部兜底展示
const currentCategory = computed(() => (item.value && item.value.category === 'external') ? 'external' : 'internal')
// 名词随分类：内部学习→"学习"，外部培训→"培训"（标题/材料等文案统一取用）
const catNoun = computed(() => currentCategory.value === 'external' ? '培训' : '学习')
// 顶栏标题：仅「准备中且未通知」显示"通知"，已通知/进行中/已完成都显示"详情"
const navTitle = computed(() => {
  const isNotice = item.value && item.value.stage === 'preparing' && !item.value.notified
  return catNoun.value + (isNotice ? '通知' : '详情')
})
// 组织单位：内部学习由业委会自行组织，显示本业委会；外部培训显示填写的组织单位/讲师
const committeeName = computed(() => {
  const r = getStorage('activeRole', null) || {}
  return r.communityName ? (r.communityName + '业委会') : '业主委员会'
})
const organizerDisplay = computed(() =>
  currentCategory.value === 'internal' ? committeeName.value : ((item.value && item.value.trainer) || '未填写'))
// 计划参加：正好是全体业委会成员时显示"全体业委会成员"，否则列出姓名
const attendeesDisplay = computed(() => {
  const names = String((item.value && item.value.attendees) || '').split(/[,，、\s]+/).filter(Boolean)
  if (!names.length) return '未填写'
  const all = memberNames.value
  if (all.length && names.length >= all.length && all.every(n => names.includes(n))) return '全体业委会成员'
  return names.join('、')
})
// 载入业委会成员并按记录已有计划名单预勾选（无则默认全选）
async function loadMembers() {
  try {
    const list = await api.committeeMembers()
    const planned = new Set(String((item.value && item.value.attendees) || '').split(/[,，、\s]+/).filter(Boolean))
    members.value = (list || [])
      .map(m => ({ userRoleId: Number(m.userRoleId), name: m.name || '委员', role: m.role || '', checked: planned.size ? planned.has(m.name || '委员') : true }))
      .filter(m => m.userRoleId)
  } catch (e) { members.value = [] }
}
let itemId = null

function loadItem() {
  var id = itemId
  // 通过 API 列表查找（内部学习 -> 培训）
  api.learningList('internal', null).then(function (internal) {
    var found = internal.find(function (i) { return i.id === id })
    if (found) { applyItem(found); return }
    api.learningList('training', null).then(function (training) {
      var f2 = training.find(function (i) { return i.id === id })
      if (f2) applyItem(f2)
    })
  })
}

function applyItem(found) {
  // 将 signIns 对象转为可渲染的数组
  found._signList = []
  if (found.signIns) {
    Object.keys(found.signIns).forEach(function (name) {
      found._signList.push({ name: name, signed: found.signIns[name] })
    })
  }
  found._signedCount = found._signList.filter(function (s) { return s.signed }).length
  found._totalCount = found._signList.length
  selectedAttendance.value = found._signList.filter(function (s) { return s.signed }).map(function (s) { return s.name })
  item.value = found
  if (found.stage === 'preparing') loadMembers()   // 准备阶段载入成员并预勾选
}

async function notifyAll() {
  if (!canManage.value) return
  const names = selectedNames()
  if (!names.length) { toast({ title: '请至少选择一位参加人员', icon: 'none' }); return }
  try {
    await api.learningNotifyAll(itemId, names, 'app')
    toast({ title: '已发送通知', icon: 'success' })
    loadItem()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

// 登记结果改为独立页（0729 用户定：不在当前页展开）；准备阶段进入即推进到进行中
function goRegister() {
  if (!canManage.value) { toast({ title: '仅主任/副主任/委员可操作', icon: 'none' }); return }
  if (item.value && item.value.stage === 'preparing' && !item.value.notified) {
    toast({ title: '请先通知参加人员，通知后才能登记结果', icon: 'none' }); return
  }
  window.location.assign('/learning-register?id=' + itemId)
}

function formatTime(value) {
  return String(value || '').slice(0, 5)
}

function goHome() {
  goModuleHome('meeting')
}

async function addEvidence() {
  if (!canManage.value) return
  try {
    const r = await pickAndUpload('image/*,.pdf,.doc,.docx')
    if (!r) return  // 用户取消
    await api.learningAddEvidence(itemId, r.fileName, r.fileType, r.url)
    toast({ title: '已上传', icon: 'success' })
    loadItem()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

function isImage(ev) {
  const t = (ev.fileType || '').toLowerCase()
  const u = (ev.fileUrl || '').toLowerCase()
  return /(jpg|jpeg|png|gif|webp)/.test(t) || /\.(jpg|jpeg|png|gif|webp)(\?|$)/.test(u)
}

function openFile(url) {
  if (url) window.open(url, '_blank')
}

function removeEvidence(evId) {
  try {
    api.learningRemoveEvidence(itemId, evId).then(function () { loadItem() })
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function removeLearning() {
  const res = await showModal({
    title: '确认删除',
    content: '确定删除该学习记录？'
  })
  if (res.confirm) {
    try {
      await api.learningRemove(itemId)
      toast({ title: '已删除', icon: 'success' })
      navigateBack()
    } catch (e) { toast({ title: e.message, icon: 'none' }) }
  }
}

onMounted(() => {
  itemId = parseInt(route.query.id)
  canManage.value = perm.can('learning.create')
  loadItem()
})
</script>

<style scoped>
.page { min-height: 100vh; background: #f4f5f7; padding: 24rpx 24rpx 160rpx; box-sizing: border-box; }
.nav-home { display:inline-flex; align-items:center; height:60rpx; margin-right:18rpx; padding:0 22rpx; border:2rpx solid rgba(255,255,255,.58); border-radius:32rpx; background:rgba(255,255,255,.12); color:#fff; font-size:28rpx; font-weight:600; line-height:1; }
.nav-home:active { background:rgba(255,255,255,.28); }

/* 头部 */
.detail-head { background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; }
.dh-title { font-size: 38rpx; font-weight: 700; color: #1f2329; line-height: 1.4; flex: 1; }

/* 信息卡片 */
.info-card { background: #fff; border-radius: 24rpx; padding: 14rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
/* 居中对齐：分类标签(带内边距)与左侧标签同高不再下坠；行内距收紧 */
.field-row { display: flex; align-items: center; gap: 20rpx; padding: 13rpx 0; border-bottom: 2rpx solid #f5f5f5; }
.field-row:last-child { border-bottom: none; }
.field-label { font-size: 28rpx; color: #666; width: 140rpx; flex-shrink: 0; line-height: 1.5; }
.field-val { font-size: 30rpx; color: #1f2329; flex: 1; line-height: 1.5; }
.field-val.tag { font-size: 28rpx; font-weight: 600; padding: 4rpx 18rpx; border-radius: 10rpx; display: inline-block; width: auto; flex: none; }
.field-val.tag.cat-internal { background: #FFF3DC; color: #C77800; }
.field-val.tag.cat-external { background: #EBF5FB; color: #2980B9; }
/* 分类可编辑段控（主任/副主任在详情里改内部/外部） */

/* 进度卡片 */
.progress-card { background: #fff; border-radius: 24rpx; padding: 24rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.pr-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14rpx; }
.pr-label { font-size: 28rpx; color: #666; }
.pr-pct { font-size: 32rpx; font-weight: 700; color: #C77800; }
.pr-bar { background: #f0f0f0; border-radius: 8rpx; height: 14rpx; overflow: hidden; }
.pr-fill { height: 100%; border-radius: 8rpx; background: #FFA800; }

/* 内容说明 */
.content-card { background: #fff; border-radius: 24rpx; padding: 24rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.cc-label { font-size: 28rpx; color: #666; display: block; margin-bottom: 12rpx; }
.cc-text { font-size: 32rpx; color: #33373d; line-height: 1.8; }

/* 签到 */
.sign-card { background: #fff; border-radius: 24rpx; padding: 24rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.sign-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.sign-title { font-size: 30rpx; font-weight: 700; color: #1f2329; }
.sign-stat { font-size: 28rpx; color: #C77800; font-weight: 600; }
.sign-stat.ok { color: #27AE60; }
/* 通知人员：可点击收起/展开的头部 + 计数 + 箭头（默认收起） */
.nc2-head { display: flex; align-items: center; justify-content: space-between; }
.nc2-head:active { opacity: .7; }
.nc2-right { display: flex; align-items: center; gap: 14rpx; }
.nc2-arrow { display: inline-block; width: 14rpx; height: 14rpx; border-right: 3rpx solid #A4A9B0; border-bottom: 3rpx solid #A4A9B0; transform: rotate(45deg); position: relative; top: -2rpx; transition: transform .18s ease, top .18s ease; }
.nc2-arrow.open { transform: rotate(-135deg); top: 2rpx; }
.nc2-list { margin-top: 16rpx; }
/* 通知人员：全选 + 可勾选成员行（与会议通知页同款） */
.mc-all { display: flex; align-items: center; gap: 8rpx; }
.mc-all-label { font-size: 27rpx; color: #A85800; font-weight: 700; white-space: nowrap; }
.mc-item { display: flex; align-items: center; gap: 14rpx; padding: 14rpx 8rpx; border-bottom: 1px solid #f0f0f2; }
.mc-item:last-child { border-bottom: none; }
.mc-item:active { background: #fafafa; }
.mc-check { flex-shrink: 0; width: 38rpx; height: 38rpx; border-radius: 50%; border: 3rpx solid #cfd4da; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 24rpx; font-weight: 700; box-sizing: border-box; }
.mc-check.on { background: var(--c-primary); border-color: var(--c-primary); }
.mc-person { display: flex; flex-direction: column; gap: 2rpx; }
.mc-name { font-size: 28rpx; color: #1f2329; font-weight: 600; }
.mc-role { font-size: 20rpx; color: #9aa0a6; }
.attendance-hint { display: block; margin: -4rpx 0 14rpx; font-size: 25rpx; color: #8A94A6; }
.sign-list { display: flex; flex-direction: column; gap: 4rpx; margin-bottom: 10rpx; }
.sign-row { display: flex; align-items: center; justify-content: space-between; padding: 14rpx; border-radius: 12rpx; }
.attendance-row { cursor: pointer; background: #F6F7F9; margin-bottom: 6rpx; }
.attendance-row.done { background: #EAF5EE; }
.attendance-check { font-size: 26rpx; color: #778292; }
.attendance-row.done .attendance-check { color: #2E7D50; font-weight: 600; }
.sign-row.done { background: #f9f9f9; }
.sign-name { font-size: 30rpx; color: #1f2329; }
.sign-status { font-size: 28rpx; font-weight: 600; }
.sign-status.ok { color: #27AE60; }
.sign-status.wait { color: #666; }
.sign-my { padding-top: 16rpx; border-top: 2rpx solid #f0f0f0; text-align: center; }
.btn-signin { width: 100%; height: 84rpx; line-height: 84rpx; border-radius: 42rpx; background: #fff; color: #C77800; border: 2rpx solid #FFA800; font-size: 30rpx; font-weight: 600; margin: 0; }
.btn-notify { width: 50%; }   /* 发送通知：半宽居中（父级 text-align:center） */

/* 佐证 */
.ev-card { background: #fff; border-radius: 24rpx; padding: 24rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.ev-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12rpx; }
.ev-title { font-size: 30rpx; font-weight: 700; color: #1f2329; }
/* 上传：下方居中大按钮（0729 用户定） */
.ev-add-btn { display: block; width: 66%; margin: 22rpx auto 4rpx; height: 88rpx; border: 2rpx solid var(--c-primary); border-radius: 20rpx; background: var(--c-primary-soft); color: var(--c-primary-dark); font-size: 30rpx; font-weight: 700; }
.ev-add-btn:active { background: #F7E6C8; }
.ev-list { display: flex; flex-direction: column; gap: 8rpx; }
.ev-item { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 16rpx 18rpx; background: #fafbfc; border-radius: 12rpx; font-size: 28rpx; color: #444; }
.ev-name { flex: 1; min-width: 0; word-break: break-all; }
.ev-link { color: #2980B9; cursor: pointer; }
.ev-thumb { width: 120rpx; height: 120rpx; object-fit: cover; border-radius: 8rpx; border: 1rpx solid #eee; flex-shrink: 0; cursor: pointer; }
.ev-del { font-size: 40rpx; color: #666; margin-left: 16rpx; flex-shrink: 0; padding: 0 8rpx; cursor: pointer; }
.ev-empty { font-size: 28rpx; color: #777; text-align: center; padding: 16rpx 0; }

/* 操作区 */
.action-card { background: #fff; border-radius: 24rpx; padding: 28rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); text-align: center; }
.ac-hint { font-size: 28rpx; color: #666; display: block; margin-bottom: 20rpx; }
.btn-primary { width: 100%; height: 96rpx; line-height: 96rpx; border-radius: 48rpx; background: var(--c-primary-dark); color: #fff; font-size: 34rpx; font-weight: 600; border: none; margin: 0; }
.btn-primary.finish { background: #5DADE2; }
.btn-primary:disabled { background: #D6C3A6; color: #fff; }

/* 无权限 / 已完成 */
.perm-note { background: #FFF8E8; border: 2rpx solid #FFE0A3; border-radius: 18rpx; padding: 20rpx 24rpx; margin-bottom: 20rpx; font-size: 28rpx; color: #9A7600; text-align: center; }
.done-card { background: #E8F7EE; border-radius: 24rpx; padding: 36rpx; text-align: center; margin-bottom: 20rpx; }
.done-text { font-size: 32rpx; color: #27AE60; font-weight: 600; }

/* 删除 */
.delete-area { text-align: center; padding: 24rpx 0; }
.delete-link { font-size: 28rpx; color: #E74C3C; }

/* 复用 */
.empty-state { padding: 80rpx; text-align: center; color: #777; font-size: 32rpx; }
.stage-pill { font-size: 28rpx; font-weight: 600; padding: 6rpx 18rpx; border-radius: 12rpx; white-space: nowrap; }
/* 三级状态色（0730 设计师定，全 app 通用）：已通知/待通知=常态灰、待整理=进行中蓝、已完成=常态灰 */
.stage-pill.preparing { background: transparent; color: #6B7280; }
.stage-pill.ongoing { background: transparent; color: #2F5F9E; }
.stage-pill.ended { background: transparent; color: #6B7280; }
</style>
