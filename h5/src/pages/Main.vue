<template>
  <div class="home">
    <!-- 顶栏：标题 + 通知铃 -->
    <div class="hd">
      <div class="hd-left">
        <span class="hd-title">业委会</span>
        <span class="hd-sub">{{ activeRole.realName }} · {{ activeRole.role }}</span>
      </div>
      <div class="hd-bell" @click="goNotifications">
        <span class="hd-bell-ico">🔔</span>
        <div v-if="unread > 0" class="hd-badge">{{ unread > 99 ? '99+' : unread }}</div>
      </div>
    </div>

    <!-- ① 有进行中的会议：每张卡片独立展示 -->
    <template v-if="currents.length > 0">
      <div v-for="cur in currents" :key="cur.id" class="meet-card">
        <span class="meet-tag">{{ cur.tag }}</span>
        <span class="meet-title">{{ cur.title }}</span>
        <span class="meet-meta">{{ cur.meetingDate }} {{ cur.meetingTime }} · {{ cur.location }}</span>

        <div class="steps">
          <template v-for="(item, index) in cur.steps" :key="item.no">
            <div v-if="index > 0" class="step-line" :class="item.state === 'todo' ? 'todo' : 'done'"></div>
            <div class="step" :class="item.state">
              <div class="step-dot">
                <span v-if="item.state === 'done'">✓</span>
                <span v-else>{{ item.no }}</span>
              </div>
              <span class="step-label">{{ item.label }}</span>
            </div>
          </template>
        </div>

        <div class="big-btn" @click="goCurrent(cur)">
          <span class="big-btn-ico">{{ cur.ctaIcon }}</span>
          <span class="big-btn-text">{{ cur.ctaLabel }}</span>
        </div>

        <div v-if="canCreate" class="meet-del" @click="removeCurrent(cur)">删除会议</div>
      </div>

      <!-- 新增会议（仅主任，卡片列表底部） -->
      <div v-if="canCreate" class="add-meet-row" @click="goCreate">
        <span class="add-meet-ico">➕</span>
        <span class="add-meet-text">新增会议</span>
      </div>
    </template>

    <template v-else>
      <div v-if="canCreate" class="idle">
        <span class="idle-hint">当前没有进行中的会议</span>
        <div class="big-btn primary" @click="goCreate">
          <span class="big-btn-ico">➕</span>
          <span class="big-btn-text">发起会议</span>
        </div>
      </div>
      <div v-else class="idle">
        <span class="idle-emoji">☕</span>
        <span class="idle-hint">暂时没有需要您处理的会议</span>
        <span class="idle-sub">有新会议时，会在这里提醒您</span>
      </div>

      <!-- 最近一次会议 -->
      <div v-if="recent" class="recent" @click="goRecent">
        <div class="recent-head">最近一次会议</div>
        <div class="recent-row">
          <div class="recent-body">
            <span class="recent-title">{{ recent.title }}</span>
            <span class="recent-meta">{{ recent.meetingDate }} · {{ recent.status }}</span>
          </div>
          <span class="recent-arrow">›</span>
        </div>
      </div>
    </template>

    <!-- 更多功能（配角） -->
    <div class="more">
      <span class="more-title">更多功能</span>
      <div class="more-grid">
        <div class="more-item" @click="goReception">
          <span class="more-ico">🤝</span>
          <span class="more-label">接待记录</span>
        </div>
        <div v-if="canCreate" class="more-item" @click="goNoticeAdmin">
          <span class="more-ico">📢</span>
          <span class="more-label">公告管理</span>
        </div>
        <div class="more-item" @click="goLearning">
          <span class="more-ico">📖</span>
          <span class="more-label">学习培训</span>
        </div>
        <div v-if="canViewInternal" class="more-item" @click="goLibrary">
          <span class="more-ico">📚</span>
          <span class="more-label">历史会议</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onActivated } from 'vue'
import api from '@/api'
import perm from '@/utils/perm'
import { getStorage } from '@/utils/storage'
import { showModal, toast } from '@/utils/ui'
import { navigateTo, redirectTo } from '@/utils/navigate'

// 会议阶段 → 开会主线三步
const STEP_BY_STAGE = { preparing: 1, ongoing: 2, ended: 3 }
const STEP_LABELS = ['', '准备开会', '正式开会', '会后总结']

const activeRole = ref({})
const canCreate = ref(false)        // 主任/副主任：可发起会议
const canViewInternal = ref(false)  // 可见资料库
const unread = ref(0)
const loading = ref(true)
const currents = ref([])            // 所有进行中/准备中的会议卡片
const recent = ref(null)            // 最近一次已结束会议

function onShow() {
  const role = getStorage('activeRole', null)
  if (!role) {
    redirectTo('/pages/login/login')
    return
  }
  activeRole.value = role
  canCreate.value = perm.can('committee.create')
  canViewInternal.value = perm.can('view.internal')
  loadUnread()
  loadMeetings()
}

async function loadUnread() {
  try {
    const res = await api.notificationList()
    unread.value = (res && res.unread) || 0
  } catch (e) { /* 离线可忽略 */ }
}

async function loadMeetings() {
  try {
    const list = await api.committeeList()
    const meetings = Array.isArray(list) ? list : []
    const isChair = perm.isChair() || perm.isRecorder()

    // 所有进行中/准备中的会议；主任额外包含「已结束未公示」（待整理纪要）
    let actives = meetings.filter((m) => m.stage === 'preparing' || m.stage === 'ongoing')
    if (isChair) {
      const pending = meetings.filter((m) => m.stage === 'ended'
        && m.compliance !== 'invalid'
        && (!m.publish || !m.publish.published))
      actives = [...actives, ...pending]
    }
    // 最近一次已结束会议（空闲态展示，排除已在卡片列表里的）
    const activeIds = new Set(actives.map((m) => m.id))
    const rec = meetings.find((m) => m.stage === 'ended' && !activeIds.has(m.id))

    currents.value = actives.map((m) => decorateCurrent(m, isChair))
    recent.value = rec ? decorateRecent(rec) : null
    loading.value = false
  } catch (e) {
    loading.value = false
  }
}

// 给当前会议附加：三步进度、主操作按钮文案/图标
function decorateCurrent(m, isChair) {
  const step = STEP_BY_STAGE[m.stage] || 1
  const steps = [1, 2, 3].map((no) => ({
    no: no,
    label: STEP_LABELS[no],
    state: no < step ? 'done' : (no === step ? 'active' : 'todo')
  }))

  let ctaLabel, ctaIcon, tag
  if (m.stage === 'preparing') {
    ctaLabel = isChair ? '继续准备会议' : '查看会议通知'
    ctaIcon = '📋'
    tag = '会议通知'
  } else if (m.stage === 'ongoing') {
    ctaLabel = isChair ? '进入会议 · 开始录音' : '查看会议'
    ctaIcon = isChair ? '🎙️' : '👀'
    tag = '正在开的会'
  } else {
    ctaLabel = '整理会议记录'
    ctaIcon = '📝'
    tag = '会后总结'
  }

  return {
    id: m.id,
    title: m.title,
    meetingDate: m.meetingDate,
    meetingTime: m.meetingTime,
    location: m.location,
    step: step,
    stepText: STEP_LABELS[step],
    steps: steps,
    ctaLabel: ctaLabel,
    ctaIcon: ctaIcon,
    tag: tag
  }
}

function decorateRecent(m) {
  let status = '已结束'
  if (m.publish && m.publish.published) status = '已张榜公开'
  else if (m.compliance === 'invalid') status = '会议无效'
  return { id: m.id, title: m.title, meetingDate: m.meetingDate, status: status }
}

// 删除会议（仅主任；清理测试数据用，任意阶段均可）
async function removeCurrent(cur) {
  const res = await showModal({
    title: '删除会议',
    content: '确定删除「' + cur.title + '」？删除后无法恢复。',
    confirmText: '删除',
    confirmColor: '#E74C3C'
  })
  if (!res.confirm) return
  try {
    await api.committeeRemove(cur.id)
    toast({ title: '已删除', icon: 'success' })
    loadMeetings()
  } catch (e) {
    toast({ title: (e && e.message) || '删除失败', icon: 'none' })
  }
}

// ── 导航 ──
function goCurrent(cur) {
  const isChair = perm.isChair() || perm.isRecorder()
  const url = isChair
    ? '/pages/committee-detail/committee-detail?id=' + cur.id
    : '/pages/my-meeting/my-meeting?id=' + cur.id
  navigateTo(url)
}
function goCreate() {
  // 发起会议 → 会议管理页（含新建入口）。阶段 3 会改成一步直达的建会向导。
  navigateTo('/pages/committee/committee')
}
function goRecent() {
  if (!recent.value) return
  navigateTo('/pages/committee-detail/committee-detail?id=' + recent.value.id)
}
function goReception() { navigateTo('/pages/reception/reception') }
function goNoticeAdmin() { navigateTo('/pages/notice-admin/notice-admin') }
function goLearning() { navigateTo('/pages/learning/learning') }
function goLibrary() { navigateTo('/pages/library/library') }
function goNotifications() { navigateTo('/pages/notifications/notifications') }

onMounted(onShow)
onActivated(onShow)
</script>

<style scoped>
.home {
  min-height: 100vh;
  background: #f4f5f7;
  padding-bottom: calc(132rpx + env(safe-area-inset-bottom));
  display: flex; flex-direction: column; box-sizing: border-box;
}

/* ── 顶栏 ── */
.hd {
  display: flex; align-items: flex-end; justify-content: space-between;
  padding: calc(20rpx + env(safe-area-inset-top)) 32rpx 30rpx;
  background: linear-gradient(160deg, #FFC23D, #FFA800);
}
.hd-left { display: flex; flex-direction: column; padding-top: 20rpx; }
.hd-title { font-size: 52rpx; font-weight: 700; color: #fff; }
.hd-sub { font-size: 34rpx; color: #fff; opacity: 0.92; margin-top: 8rpx; }
.hd-bell { position: relative; padding: 8rpx; }
.hd-bell-ico { font-size: 52rpx; }
.hd-badge {
  position: absolute; top: -2rpx; right: -6rpx;
  min-width: 34rpx; height: 34rpx; padding: 0 8rpx;
  background: #E74C3C; color: #fff; font-size: 28rpx;
  border-radius: 17rpx; line-height: 34rpx; text-align: center;
}

/* ── 当前会议主卡片 ── */
.meet-card {
  margin: 28rpx 24rpx 0;
  background: #fff; border-radius: 24rpx;
  padding: 36rpx 32rpx 40rpx;
  box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06);
}
.meet-tag { font-size: 32rpx; color: #FF8C00; font-weight: 600; }
.meet-title {
  display: block; font-size: 52rpx; font-weight: 700; color: #1f2329;
  margin-top: 14rpx; line-height: 1.35;
}
.meet-meta { display: block; font-size: 34rpx; color: #6b7785; margin-top: 16rpx; }

/* ── 三步进度 ── */
.steps {
  display: flex; align-items: flex-start; justify-content: space-between;
  margin: 44rpx 8rpx 40rpx;
}
.step { display: flex; flex-direction: column; align-items: center; width: 150rpx; }
.step-dot {
  width: 76rpx; height: 76rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 40rpx; font-weight: 700;
  background: #e9edf2; color: #666;
}
.step-label { font-size: 32rpx; color: #666; margin-top: 14rpx; }
.step.done .step-dot { background: #FFE7B3; color: #C77800; }
.step.done .step-label { color: #6b7785; }
.step.active .step-dot { background: #FFA800; color: #fff; box-shadow: 0 4rpx 14rpx rgba(255,168,0,0.45); }
.step.active .step-label { color: #1f2329; font-weight: 700; }
.step-line { flex: 1; height: 6rpx; border-radius: 3rpx; margin-top: 35rpx; }
.step-line.done { background: #FFCF66; }
.step-line.todo { background: #e9edf2; }

/* ── 大按钮 ── */
.big-btn {
  display: flex; align-items: center; justify-content: center;
  height: 140rpx; border-radius: 22rpx;
  background: #FFA800; margin-top: 8rpx;
  box-shadow: 0 8rpx 22rpx rgba(255,168,0,0.34);
}
.big-btn:active { opacity: 0.88; }
.big-btn-ico { font-size: 50rpx; margin-right: 16rpx; }
.big-btn-text { font-size: 48rpx; font-weight: 700; color: #fff; }

/* 新增会议按钮（比卡片内大按钮小 20%） */
.add-meet-row {
  margin: 48rpx 24rpx 0;
  display: flex; align-items: center; justify-content: center;
  height: 112rpx; border-radius: 22rpx;
  background: #FFA800;
  box-shadow: 0 8rpx 22rpx rgba(255,168,0,0.34);
}
.add-meet-row:active { opacity: 0.88; }
.add-meet-ico { font-size: 40rpx; margin-right: 12rpx; }
.add-meet-text { font-size: 38rpx; font-weight: 700; color: #fff; }

/* 删除会议（测试用，弱化） */
.meet-del {
  text-align: center; color: #E74C3C; font-size: 30rpx;
  margin-top: 28rpx; padding: 8rpx;
}
.meet-del:active { opacity: 0.6; }

/* ── 空闲态 ── */
.idle {
  margin: 64rpx 24rpx 0;
  display: flex; flex-direction: column; align-items: center;
}
.idle-emoji { font-size: 104rpx; margin-bottom: 24rpx; }
.idle-hint { font-size: 38rpx; color: #4a5560; margin-bottom: 32rpx; }
.idle-sub { font-size: 30rpx; color: #666; margin-top: 6rpx; }
.idle .big-btn.primary { width: 88%; height: 150rpx; }
.idle .big-btn.primary .big-btn-text { font-size: 52rpx; }
.idle .big-btn.primary .big-btn-ico { font-size: 56rpx; }

/* ── 最近一次会议 ── */
.recent { margin: 48rpx 24rpx 0; }
.recent-head { font-size: 30rpx; color: #666; margin-bottom: 16rpx; padding-left: 8rpx; }
.recent-row {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-radius: 20rpx; padding: 30rpx 28rpx;
  box-shadow: 0 6rpx 18rpx rgba(0,0,0,0.04);
}
.recent-body { display: flex; flex-direction: column; }
.recent-title { font-size: 38rpx; color: #1f2329; font-weight: 600; }
.recent-meta { font-size: 32rpx; color: #666; margin-top: 10rpx; }
.recent-arrow { font-size: 48rpx; color: #666; }

/* ── 更多功能（沉底 + 缩小） ── */
.more { margin: 0 28rpx; margin-top: auto; padding-top: 56rpx; }
.more-title { font-size: 28rpx; color: #777; padding-left: 6rpx; }
.more-grid { display: flex; margin-top: 14rpx; gap: 16rpx; }
.more-item {
  flex: 1; background: #fff; border-radius: 18rpx;
  padding: 36rpx 0 32rpx; display: flex; flex-direction: column; align-items: center;
  box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.03);
}
.more-item:active { background: #fafafa; }
.more-ico { font-size: 44rpx; line-height: 1.1; }
.more-label { font-size: 28rpx; color: #666; margin-top: 14rpx; }
</style>
