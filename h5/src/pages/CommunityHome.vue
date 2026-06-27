<template>
  <div class="page">
    <!-- Header -->
    <div class="header">
      <span class="header-title">阳光家园</span>
      <span class="header-sub">幸福路88号</span>
    </div>

    <!-- 版块标签（轻微提示可左右滑动） -->
    <div class="section-tabs">
      <div class="s-tab" :class="{ on: currentCard === 0 }" @click="switchCard(0)">
        <span>📢 小区公告</span>
      </div>
      <div class="s-tab" :class="{ on: currentCard === 1 }" @click="switchCard(1)">
        <span>🏗️ 物业事项</span>
      </div>
      <div class="s-tab" :class="{ on: currentCard === 2 }" @click="switchCard(2)">
        <span>📋 会议公示</span>
      </div>
    </div>

    <!-- Swiper 横滑三卡片 -->
    <div class="card-swiper" ref="swiperRef" @scroll="onSwiperScroll">

      <!-- 卡片1: 小区公告 -->
      <div class="swiper-item">
        <div class="card-scroll">
          <div class="card">
            <div class="card-head">
              <span class="card-head-icon">📢</span>
              <span class="card-head-title">小区公告</span>
            </div>
            <div class="notice-list">
              <div class="notice-item" v-for="item in notices" :key="item.id">
                <div class="notice-date-badge">{{ item.date }}</div>
                <span class="notice-title">{{ item.title }}</span>
                <span class="notice-detail">{{ item.detail }}</span>
              </div>
            </div>
            <div v-if="notices.length === 0" class="empty-state">
              <span class="empty-emoji">📭</span>
              <span class="empty-text">暂无公告</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 卡片2: 物业事项公示 -->
      <div class="swiper-item">
        <div class="card-scroll">
          <div class="card">
            <div class="card-head">
              <span class="card-head-icon">🏗️</span>
              <span class="card-head-title">物业事项公示</span>
            </div>
            <!-- 状态筛选 -->
            <div class="filter-row">
              <div class="f-pill" :class="{ active: propFilter === 'all' }"
                   @click="filterProperty('all')">全部</div>
              <div class="f-pill" :class="{ active: propFilter === 'processing' }"
                   @click="filterProperty('processing')">处理中</div>
              <div class="f-pill" :class="{ active: propFilter === 'done' }"
                   @click="filterProperty('done')">已处理</div>
            </div>
            <div v-if="filteredProperties.length > 0" class="prop-list">
              <div class="prop-card" v-for="item in filteredProperties" :key="item.id">
                <div class="prop-head">
                  <span class="prop-tag">物业事项</span>
                  <span class="prop-status" :class="item.statusKey">{{ item.statusLabel }}</span>
                </div>
                <span class="prop-content">{{ item.content }}</span>
                <span class="prop-date">📅 反映日期 {{ item.date }}</span>
                <div v-if="item.propertyReply" class="prop-reply">
                  <span class="prop-reply-label">物业处理结果：</span>
                  <span>{{ item.propertyReply }}</span>
                </div>
              </div>
            </div>
            <div v-else-if="!propLoading" class="empty-state">
              <span class="empty-emoji">🏗️</span>
              <span class="empty-text">暂无物业事项</span>
            </div>
            <div v-else class="empty-state">
              <span class="empty-text">加载中...</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 卡片3: 会议纪要公示 -->
      <div class="swiper-item">
        <div class="card-scroll">
          <div class="card">
            <div class="card-head">
              <span class="card-head-icon">📋</span>
              <span class="card-head-title">会议纪要公示</span>
            </div>
            <div v-if="publicMinutes.length > 0" class="minutes-list">
              <div class="minutes-item" v-for="item in publicMinutes" :key="item.id"
                   @click="openMinutes(item.id)">
                <span class="minutes-title">{{ item.title }}</span>
                <span class="minutes-desc" v-if="item.description">{{ item.description }}</span>
                <div class="minutes-meta">
                  <span class="minutes-date">📅 {{ item.date }}</span>
                  <span class="minutes-arrow">查看 ›</span>
                </div>
              </div>
            </div>
            <div v-else-if="!minutesLoading" class="empty-state">
              <span class="empty-emoji">📋</span>
              <span class="empty-text">暂无公示的会议纪要</span>
            </div>
            <div v-else class="empty-state">
              <span class="empty-text">加载中...</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onActivated } from 'vue'
import api from '@/api'
import { navigateTo } from '@/utils/navigate'

// 兜底示例公告（接口为空/异常时显示）
const DEFAULT_NOTICES = [
  {
    id: 1,
    date: '2026-06-20',
    title: '关于小区电梯年度维保的通知',
    detail: '定于6月28日对1-8栋电梯进行年度安全维保，届时每栋将轮流停梯约2小时，请各位业主提前做好准备。'
  },
  {
    id: 2,
    date: '2026-06-15',
    title: '地下车库照明改造施工公告',
    detail: '6月18日-7月5日对地下车库B1/B2层进行LED照明节能改造，施工期间部分车位将临时封闭，请按现场指引停放车辆。'
  },
  {
    id: 3,
    date: '2026-06-10',
    title: '小区绿化养护工作安排',
    detail: '6月12日起对小区公共绿地进行夏季修剪打药，请各位业主在此期间关好门窗，看护好儿童和宠物。'
  },
  {
    id: 4,
    date: '2026-06-05',
    title: '物业费缴纳提醒',
    detail: '2026年第三季度物业费已开始收缴，请于7月15日前通过微信小程序或前往物业服务中心缴纳，逾期将产生滞纳金。'
  },
  {
    id: 5,
    date: '2026-05-28',
    title: '关于小区门禁系统升级的通知',
    detail: '6月1日起小区东门、南门门禁将升级为人脸识别+刷卡双模式，请未录入人脸信息的业主携带身份证到物业服务中心办理。'
  }
]

const currentCard = ref(0)

// 卡片1: 小区公告
const notices = ref([])
const noticeLoading = ref(true)

// 卡片2: 物业事项公示
const properties = ref([])
const propFilter = ref('all')
const propLoading = ref(true)

const filteredProperties = computed(() => {
  if (propFilter.value === 'all') {
    return properties.value
  }
  return properties.value.filter((r) => r.statusKey === propFilter.value)
})

// 卡片3: 会议纪要公示
const publicMinutes = ref([])
const minutesLoading = ref(true)

const swiperRef = ref(null)

// ── 小区公告 ──
async function loadNotices() {
  noticeLoading.value = true
  try {
    const list = await api.noticeList()
    notices.value = Array.isArray(list) && list.length > 0 ? list : DEFAULT_NOTICES
    noticeLoading.value = false
  } catch (e) {
    // 网络异常时用示例兜底
    notices.value = DEFAULT_NOTICES
    noticeLoading.value = false
  }
}

// ── 物业事项 ──
async function loadProperties() {
  propLoading.value = true
  try {
    const list = await api.receptionPropertyPublic()
    const list2 = Array.isArray(list) && list.length > 0 ? list : []
    if (list2.length === 0) {
      // API 无数据时用示例兜底
      list2.push({
        id: 101,
        date: '2026-06-18',
        content: '3栋2单元电梯运行时异响，业主反映存在安全隐患，请安排检修。',
        statusKey: 'done',
        statusLabel: '已处理',
        propertyReply: '已联系电梯维保单位检查，更换了曳引轮轴承，异响已消除，电梯恢复正常运行。',
        propertyRepliedAt: '2026-06-21'
      })
    }
    properties.value = list2
    propLoading.value = false
  } catch (e) {
    // 网络异常时用示例兜底
    const fallback = [{
      id: 101,
      date: '2026-06-18',
      content: '3栋2单元电梯运行时异响，业主反映存在安全隐患，请安排检修。',
      statusKey: 'done',
      statusLabel: '已处理',
      propertyReply: '已联系电梯维保单位检查，更换了曳引轮轴承，异响已消除，电梯恢复正常运行。',
      propertyRepliedAt: '2026-06-21'
    }]
    properties.value = fallback
    propLoading.value = false
  }
}

function filterProperty(filter) {
  propFilter.value = filter
}

// ── 会议纪要公示 ──
async function loadPublicMinutes() {
  minutesLoading.value = true
  try {
    const list = await api.publicInfo()
    const list2 = Array.isArray(list) && list.length > 0 ? list : []
    if (list2.length === 0) {
      // API 无数据时用示例兜底
      list2.push({
        id: 1,
        type: 'committee',
        title: '2026年6月业委会例会',
        description: '审议小区电梯维保方案、地下车库照明改造预算及第三季度物业费调整事项',
        date: '2026-06-15',
        publishDate: '2026-06-18'
      })
    }
    publicMinutes.value = list2
    minutesLoading.value = false
  } catch (e) {
    // 网络异常时用示例兜底
    const fallback = [{
      id: 1,
      type: 'committee',
      title: '2026年6月业委会例会',
      description: '审议小区电梯维保方案、地下车库照明改造预算及第三季度物业费调整事项',
      date: '2026-06-15',
      publishDate: '2026-06-18'
    }]
    publicMinutes.value = fallback
    minutesLoading.value = false
  }
}

function openMinutes(id) {
  navigateTo('/pages/minutes-public/minutes-public?meetingId=' + id)
}

// ── Swiper 变化 ──
let scrollTimer = null
function onSwiperScroll() {
  const el = swiperRef.value
  if (!el) return
  if (scrollTimer) clearTimeout(scrollTimer)
  scrollTimer = setTimeout(() => {
    const idx = Math.round(el.scrollLeft / el.clientWidth)
    if (idx !== currentCard.value) currentCard.value = idx
  }, 60)
}

// 点击版块标签切换
function switchCard(index) {
  currentCard.value = index
  nextTick(() => {
    const el = swiperRef.value
    if (el) {
      el.scrollTo({ left: index * el.clientWidth, behavior: 'smooth' })
    }
  })
}

let firstRun = true
function refresh() {
  loadNotices()
  loadProperties()
  loadPublicMinutes()
}

onMounted(() => {
  firstRun = false
  refresh()
})

onActivated(() => {
  if (firstRun) return
  refresh()
})
</script>

<style scoped>
.page {
  background: #f4f5f7;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

/* ── Header ── */
.header {
  padding: 8rpx 36rpx 14rpx;
  background: linear-gradient(160deg, #FFC23D, #FFA800);
}
.header-title {
  font-size: 48rpx;
  font-weight: 700;
  color: #fff;
  display: block;
}
.header-sub {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.78);
  margin-top: 2rpx;
  display: block;
}

/* ── 版块标签（轻微暗示可切换） ── */
.section-tabs {
  display: flex;
  background: #fff;
  margin: 0 24rpx;
  border-radius: 20rpx 20rpx 0 0;
  padding: 18rpx 16rpx 0;
  gap: 8rpx;
  box-shadow: 0 -2rpx 8rpx rgba(0,0,0,0.03);
}
.s-tab {
  flex: 1;
  text-align: center;
  font-size: 30rpx;
  color: #5A6473;
  font-weight: 500;
  padding: 14rpx 0 18rpx;
  border-bottom: 4rpx solid transparent;
  transition: all 0.2s;
}
.s-tab.on {
  color: #C77800;
  font-weight: 700;
  border-bottom-color: #FFA800;
}

/* ── Swiper ── */
.card-swiper {
  flex: 1;
  height: 0;  /* flex:1 配合 height:0 让 swiper 撑满剩余高度 */
  display: flex;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-snap-type: x mandatory;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.card-swiper::-webkit-scrollbar {
  display: none;
}

.swiper-item {
  flex: 0 0 100%;
  width: 100%;
  height: 100%;
  scroll-snap-align: center;
}

.card-scroll {
  height: 100%;
  padding: 0 24rpx 0;
  box-sizing: border-box;
  overflow-y: auto;
}

/* ── 卡片容器 ── */
.card {
  background: #fff;
  border-radius: 0 0 24rpx 24rpx;
  padding: 20rpx 32rpx 40rpx;
  box-shadow: 0 8rpx 28rpx rgba(41, 63, 102, 0.08);
  min-height: 100%;
  box-sizing: border-box;
}

.card-head {
  display: flex;
  align-items: center;
  margin-bottom: 32rpx;
}
.card-head-icon {
  font-size: 48rpx;
  margin-right: 14rpx;
}
.card-head-title {
  font-size: 44rpx;
  font-weight: 700;
  color: var(--pub-ink);
}

/* ── 筛选标签（公示卡 → 蓝） ── */
.filter-row {
  display: flex;
  gap: 14rpx;
  margin-bottom: 28rpx;
}
.f-pill {
  flex: 1;
  text-align: center;
  font-size: 30rpx;
  color: var(--pub-sub);
  background: #f1f4fa;
  border-radius: 18rpx;
  padding: 16rpx 0;
  font-weight: 500;
}
.f-pill.active {
  background: var(--pub-blue-soft);
  color: var(--pub-blue);
  font-weight: 700;
}

/* ── 小区公告列表（公示卡 → 蓝） ── */
.notice-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.notice-item {
  background: #fbfcff;
  border-radius: 18rpx;
  padding: 28rpx 26rpx;
  border-left: 8rpx solid var(--pub-blue);
}

.notice-date-badge {
  display: inline-block;
  background: var(--pub-blue-soft);
  color: var(--pub-blue);
  font-size: 28rpx;
  font-weight: 600;
  padding: 4rpx 16rpx;
  border-radius: 10rpx;
  margin-bottom: 14rpx;
}

.notice-title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--pub-ink);
  display: block;
  line-height: 1.5;
  margin-bottom: 10rpx;
}

.notice-detail {
  font-size: 30rpx;
  color: var(--pub-sub);
  display: block;
  line-height: 1.7;
}

/* ── 物业事项列表 ── */
.prop-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.prop-card {
  background: #fbfcff;
  border-radius: 18rpx;
  padding: 26rpx 26rpx;
  border-left: 8rpx solid var(--pub-blue);
}

.prop-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14rpx;
}

.prop-tag {
  font-size: 28rpx;
  background: var(--pub-blue-soft);
  color: var(--pub-blue);
  padding: 4rpx 16rpx;
  border-radius: 10rpx;
  font-weight: 600;
}

/* 状态徽标：红=待处理 / 琥珀=处理中 / 绿=已处理（语义色，非品牌色） */
.prop-status {
  font-size: 28rpx;
  font-weight: 700;
  padding: 4rpx 16rpx;
  border-radius: 12rpx;
}
.prop-status.pending {
  background: #FDECEA;
  color: #E74C3C;
}
.prop-status.processing {
  background: var(--pub-amber-soft);
  color: var(--pub-amber);
}
.prop-status.done {
  background: var(--pub-green-soft);
  color: var(--pub-green);
}

.prop-content {
  font-size: 32rpx;
  color: var(--pub-ink);
  line-height: 1.6;
  display: block;
  margin-bottom: 12rpx;
}

.prop-date {
  font-size: 28rpx;
  color: var(--pub-sub);
  display: block;
}

.prop-reply {
  font-size: 30rpx;
  color: var(--pub-text);
  line-height: 1.7;
  background: var(--pub-green-soft);
  border-radius: 14rpx;
  padding: 18rpx 20rpx;
  margin-top: 16rpx;
  border-left: 6rpx solid var(--pub-green);
}

.prop-reply-label {
  font-weight: 700;
  color: var(--pub-green);
}

/* ── 会议纪要列表 ── */
.minutes-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.minutes-item {
  background: #fbfcff;
  border-radius: 18rpx;
  padding: 28rpx 26rpx;
  border-left: 8rpx solid var(--pub-blue);
}
.minutes-item:active {
  background: #eef2fb;
}

.minutes-title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--pub-ink);
  display: block;
  line-height: 1.5;
  margin-bottom: 8rpx;
}

.minutes-desc {
  font-size: 30rpx;
  color: var(--pub-sub);
  line-height: 1.6;
  margin-bottom: 14rpx;
  /* 最多两行 */
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.minutes-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.minutes-date {
  font-size: 28rpx;
  color: var(--pub-sub);
}

.minutes-arrow {
  font-size: 30rpx;
  color: var(--pub-blue);
  font-weight: 700;
}

/* ── 空状态 ── */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 100rpx;
}

.empty-emoji {
  font-size: 80rpx;
  margin-bottom: 20rpx;
}

.empty-text {
  font-size: 32rpx;
  color: var(--pub-sub);
}
</style>
