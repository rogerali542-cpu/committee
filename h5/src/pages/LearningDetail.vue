<template>
  <div class="page" style="overflow-y:auto;">
    <!-- 回退（0717 用户指出：这页原来没有返回首页的出口）。.page 有 24rpx 横向内边距，
         负 margin 让导航条满宽贴顶，与其他页观感一致 -->
    <!-- backTo 显式回首页培训 tab（0717 用户定）：history.back 在硬跳兜底后不可靠，
         且要落回对应 tab 而非默认开会（首页 show() 读 ?tab=） -->
    <PageNav title="培训详情" back-to="/main?tab=learning" style="margin: -24rpx -24rpx 20rpx" />
    <div v-if="item">
      <!-- 头部：标题 + 阶段 -->
      <div class="detail-head">
        <span class="dh-title">{{ item.title }}</span>
        <span class="stage-pill" :class="item.stage">{{ item.stage === 'preparing' ? '待开始' : item.stage === 'ongoing' ? '进行中' : '已结束' }}</span>
      </div>

      <!-- 基本信息 -->
      <div class="info-card">
        <div class="field-row">
          <span class="field-label">类型</span>
          <span class="field-val tag" :class="item.type">{{ item.type === 'internal' ? '内部学习' : item.type === 'street' ? '街镇培训' : '专项培训' }}</span>
        </div>
        <div class="field-row">
          <span class="field-label">时间</span>
          <span class="field-val">{{ item.date }} {{ item.time }}</span>
        </div>
        <div class="field-row">
          <span class="field-label">地点</span>
          <span class="field-val">{{ item.location || '未填写' }}</span>
        </div>
        <div class="field-row">
          <span class="field-label">讲师</span>
          <span class="field-val">{{ item.trainer || '未填写' }}</span>
        </div>
        <div class="field-row">
          <span class="field-label">参加人员</span>
          <span class="field-val">{{ item.attendees || '未填写' }}</span>
        </div>
      </div>

      <!-- 进度条（非待开阶段） -->
      <div class="progress-card" v-if="item.stage !== 'preparing'">
        <div class="pr-head">
          <span class="pr-label">学习进度</span>
          <span class="pr-pct">{{ item.progress }}%</span>
        </div>
        <div class="pr-bar"><div class="pr-fill" :style="{ width: item.progress + '%' }"></div></div>
      </div>

      <!-- 内容说明 -->
      <div class="content-card" v-if="item.description">
        <span class="cc-label">学习内容</span>
        <span class="cc-text">{{ item.description }}</span>
      </div>

      <!-- 待开阶段：通知 -->
      <div class="sign-card" v-if="item.stage === 'preparing' && item._signList.length">
        <div class="sign-head">
          <span class="sign-title">参训人员</span>
          <span class="sign-stat" :class="item.notified ? 'ok' : ''">{{ item.notified ? '已通知全员' : '待通知' }}</span>
        </div>
        <div class="sign-list">
          <div v-for="s in item._signList" :key="s.name" class="sign-row">
            <span class="sign-name">{{ s.name }}</span>
          </div>
        </div>
        <div v-if="!item.notified && canManage" class="sign-my">
          <button class="btn-signin" @click="notifyAll">通知全员</button>
        </div>
      </div>

      <!-- 进行中阶段：签到 -->
      <div class="sign-card" v-if="item.stage === 'ongoing' && item._signList.length">
        <div class="sign-head">
          <span class="sign-title">参训签到</span>
          <span class="sign-stat">{{ item._signedCount }}/{{ item._totalCount }} 已签到</span>
        </div>
        <div class="sign-list">
          <div v-for="s in item._signList" :key="s.name" class="sign-row" :class="s.signed ? 'done' : ''">
            <span class="sign-name">{{ s.name }}</span>
            <span class="sign-status" :class="s.signed ? 'ok' : 'wait'">{{ s.signed ? '已签到' : '未签到' }}</span>
          </div>
        </div>
        <div v-if="myName && !item._isMySignIn" class="sign-my">
          <button class="btn-signin" @click="signIn">签到确认</button>
        </div>
      </div>

      <!-- 已结束：签到汇总 -->
      <div class="sign-card" v-if="item.stage === 'ended' && item._signList.length">
        <div class="sign-head">
          <span class="sign-title">参训签到汇总</span>
          <span class="sign-stat ok">{{ item._signedCount }}/{{ item._totalCount }} 已签到</span>
        </div>
        <div class="sign-list">
          <div v-for="s in item._signList" :key="s.name" class="sign-row" :class="s.signed ? 'done' : ''">
            <span class="sign-name">{{ s.name }}</span>
            <span class="sign-status" :class="s.signed ? 'ok' : 'wait'">{{ s.signed ? '已签到' : '缺席' }}</span>
          </div>
        </div>
      </div>

      <!-- 佐证材料（所有阶段可上传） -->
      <div class="ev-card">
        <div class="ev-head">
          <span class="ev-title">资料附件（{{ item.evidences ? item.evidences.length : 0 }}）</span>
          <span v-if="item.stage !== 'ended' && canManage" class="ev-add" @click="addEvidence">上传</span>
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
          <template v-if="item.stage === 'preparing'">上传培训通知、政府文件、学习材料等</template>
          <template v-else-if="item.stage === 'ongoing'">上传签到表、现场照片、培训证书等</template>
          <template v-else>暂无归档资料</template>
        </span>
      </div>

      <!-- 阶段操作 -->
      <div class="action-card" v-if="item.stage !== 'ended' && canManage">
        <span class="ac-hint">{{ item.stage === 'preparing' ? '准备开始本次学习' : '学习进行中，完成后确认' }}</span>
        <button v-if="item.stage === 'preparing'" class="btn-primary" @click="startLearn">开始学习</button>
        <button v-if="item.stage === 'ongoing'" class="btn-primary finish" @click="finishLearn">完成学习</button>
      </div>

      <div class="perm-note" v-if="item.stage !== 'ended' && !canManage">
        <span>仅主任/副主任/记录员可推进学习阶段</span>
      </div>

      <div class="done-card" v-if="item.stage === 'ended'">
        <span class="done-text">本次学习已完成归档</span>
      </div>

      <div class="delete-area" v-if="canManage">
        <span class="delete-link" @click="removeLearning">删除此学习记录</span>
      </div>
    </div>

    <div v-else class="empty-state"><span>加载中...</span></div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import perm from '@/utils/perm'
import { toast, showModal } from '@/utils/ui'
import { navigateBack } from '@/utils/navigate'
import { getStorage } from '@/utils/storage'
import { pickAndUpload } from '@/utils/upload'

const route = useRoute()
const item = ref(null)
const canManage = ref(false)   // 可以操作（创建/推进）= 主任/副主任/委员
const myName = ref('')
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
  // 当前用户是否在参训名单中
  var activeRole = getStorage('activeRole')
  var mn = (activeRole && activeRole.realName) ? activeRole.realName : ''
  found._isMySignIn = found._signList.some(function (s) { return s.name === mn && s.signed })
  myName.value = mn
  item.value = found
}

async function notifyAll() {
  if (!canManage.value) return
  try {
    await api.learningNotifyAll(itemId)
    toast({ title: '已通知全员', icon: 'success' })
    loadItem()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function signIn() {
  try {
    await api.learningSignIn(itemId)
    loadItem()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function startLearn() {
  if (!canManage.value) {
    toast({ title: '仅主任/副主任/委员可操作', icon: 'none' })
    return
  }
  try {
    await api.learningStart(itemId)
    toast({ title: '已开始', icon: 'success' })
    loadItem()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function finishLearn() {
  if (!canManage.value) {
    toast({ title: '仅主任/副主任/委员可操作', icon: 'none' })
    return
  }
  try {
    await api.learningFinish(itemId)
    toast({ title: '已完成', icon: 'success' })
    loadItem()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}

async function addEvidence() {
  if (!canManage.value) return
  try {
    const r = await pickAndUpload('image/*')
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

/* 头部 */
.detail-head { background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; }
.dh-title { font-size: 38rpx; font-weight: 700; color: #1f2329; line-height: 1.4; flex: 1; }

/* 信息卡片 */
.info-card { background: #fff; border-radius: 24rpx; padding: 24rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.field-row { display: flex; align-items: flex-start; gap: 20rpx; padding: 18rpx 0; border-bottom: 2rpx solid #f5f5f5; }
.field-row:last-child { border-bottom: none; }
.field-label { font-size: 28rpx; color: #666; width: 140rpx; flex-shrink: 0; line-height: 1.5; }
.field-val { font-size: 30rpx; color: #1f2329; flex: 1; line-height: 1.5; }
.field-val.tag { font-size: 28rpx; font-weight: 600; padding: 4rpx 18rpx; border-radius: 10rpx; display: inline-block; width: auto; flex: none; }
.field-val.tag.internal { background: #FFF3DC; color: #C77800; }
.field-val.tag.street { background: #EBF5FB; color: #2980B9; }
.field-val.tag.special { background: #FDF2E9; color: #E67E22; }

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
.sign-list { display: flex; flex-direction: column; gap: 4rpx; margin-bottom: 10rpx; }
.sign-row { display: flex; align-items: center; justify-content: space-between; padding: 14rpx; border-radius: 12rpx; }
.sign-row.done { background: #f9f9f9; }
.sign-name { font-size: 30rpx; color: #1f2329; }
.sign-status { font-size: 28rpx; font-weight: 600; }
.sign-status.ok { color: #27AE60; }
.sign-status.wait { color: #666; }
.sign-my { padding-top: 16rpx; border-top: 2rpx solid #f0f0f0; text-align: center; }
.btn-signin { width: 100%; height: 84rpx; line-height: 84rpx; border-radius: 42rpx; background: #fff; color: #C77800; border: 2rpx solid #FFA800; font-size: 30rpx; font-weight: 600; margin: 0; }

/* 佐证 */
.ev-card { background: #fff; border-radius: 24rpx; padding: 24rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.ev-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12rpx; }
.ev-title { font-size: 30rpx; font-weight: 700; color: #1f2329; }
.ev-add { font-size: 28rpx; color: #C77800; font-weight: 600; }
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
.stage-pill.preparing { background: #FFF3E0; color: #E67E22; }
.stage-pill.ongoing { background: #EBF5FB; color: #2980B9; }
.stage-pill.ended { background: #F0F0F0; color: #666; }
</style>
