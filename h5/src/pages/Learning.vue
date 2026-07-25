<template>
  <div class="page" style="overflow-y:auto">

    <!-- 学习培训现为底部一级 Tab（0721 用户定：加回第4个 tab）：作为根页面不挂返回箭头，
         用空的 #left 占位保持标题居中，视觉与其它 tab 页的自定义头部一致。 -->
    <PageNav title="学习培训" style="margin: 0 -3.2vw 0">
      <template #left><div class="nav-left-spacer"></div></template>
    </PageNav>

    <!-- 年度履职摘要：只保留成员需要确认的两项 -->
    <div class="learn-target">
      <div class="lt-head">
        <span class="lt-title">{{ currentYear }}年学习情况</span>
      </div>
      <div class="annual-list">
        <div class="annual-row">
          <div class="annual-main">
            <span class="annual-name">年度学习</span>
            <span class="annual-desc">年度要求至少完成{{ target }}次</span>
          </div>
          <span class="annual-status" :class="annualStudyCount >= target ? 'done' : 'warn'">
            已完成 {{ annualStudyCount }}/{{ target }}次
          </span>
        </div>
        <div class="annual-row">
          <div class="annual-main">
            <span class="annual-name">街镇培训</span>
            <span class="annual-desc">年度要求至少参加1次</span>
          </div>
          <span class="annual-status" :class="annualStreetDone ? 'done' : 'warn'">
            {{ annualStreetDone ? '已参加' : '待参加' }}
          </span>
        </div>
      </div>
    </div>

    <div class="records-head">
      <span class="records-title">学习记录</span>
      <span class="records-count">共{{ allItems.length }}条</span>
    </div>

    <!-- 单层筛选，不再要求用户理解内外部与培训子分类 -->
    <div class="filter-tabs">
      <div class="f-tab" :class="{ active: recordFilter == 'all' }" @click="switchFilter('all')">全部 {{ recordCounts.all }}</div>
      <div class="f-tab" :class="{ active: recordFilter == 'pending' }" @click="switchFilter('pending')">待处理 {{ recordCounts.pending }}</div>
      <div class="f-tab" :class="{ active: recordFilter == 'ended' }" @click="switchFilter('ended')">已完成 {{ recordCounts.ended }}</div>
    </div>

    <!-- 学习卡片列表 -->
    <div class="learn-list">
      <template v-if="items.length">
        <!-- 手风琴(0725 用户定):默认全部收起只留标题行,点卡片摊开细节;进详情走展开区内的按钮 -->
        <div class="learn-card" v-for="item in items" :key="item.id" :class="{ open: expandedId === item.id }" @click="toggleExpand(item)">
          <div class="lc-header">
            <div class="lc-title-wrap">
              <span class="lc-type">{{ learningTypeName(item) }}</span>
              <span class="lc-title">{{ item.title }}</span>
            </div>
            <span class="lc-pill" :class="item.stage">{{ item.stage === 'preparing' ? (item.notified ? '已通知' : '待通知') : item.stage === 'ongoing' ? '待整理' : '已完成' }}</span>
            <span class="lc-arrow" :class="{ open: expandedId === item.id }">⌄</span>
          </div>
          <template v-if="expandedId === item.id">
            <div class="lc-meta">
              <span class="lc-meta-line">{{ item.date }} · {{ formatTime(item.time) }}</span>
              <span class="lc-meta-line">{{ item.location }}</span>
              <span class="lc-meta-line">组织：{{ item.trainer || '未填写' }}</span>
              <span class="lc-meta-line" v-if="item.attendees">
                {{ item.stage === 'ended' ? '实际参加' : '计划参加' }}：{{ item.stage === 'ended' ? actualAttendanceCount(item) : attendeeCount(item.attendees) }}人
              </span>
              <span class="lc-meta-line" v-if="item.evidences && item.evidences.length">已上传{{ item.evidences.length }}份材料</span>
            </div>
            <button type="button" class="lc-detail-btn" @click.stop="openDetail(item)">查看详情 ›</button>
          </template>
        </div>
      </template>
      <div v-else class="empty-state"><span>{{ recordFilter === 'all' ? '暂无学习记录' : '当前没有需要显示的记录' }}</span></div>
    </div>

    <!-- 新建入口(0725 用户定):原右下 FAB 压卡片、又和「返回驾驶舱」浮球叠在一起;
         改为列表尾部虚线入口,与业委会页「发起其他会议」同款 -->
    <div v-if="canCreate" class="create-learning-entry" @click="openCreate">＋ 新增学习记录</div>

    <!-- 创建表单已迁独立页 /learning-create(0725 用户定:弹层改整页) -->

    <div v-if="undoVisible" class="undo-toast">
      <span>{{ undoText }}</span>
      <span class="undo-btn" @click="undoLearning">撤销</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, onUnmounted } from 'vue';
import api from '@/api';
import PageNav from '@/components/PageNav.vue';
import perm from '@/utils/perm';
import { toast } from '@/utils/ui';
import { navigateTo } from '@/utils/navigate';

const recordFilter = ref('all');
const allItems = ref([]);
const items = ref([]);
const recordCounts = computed(() => ({
  all: allItems.value.length,
  pending: allItems.value.filter(item => item.stage !== 'ended').length,
  ended: allItems.value.filter(item => item.stage === 'ended').length
}));
const target = ref(2);
const annualStudyCount = ref(0);
const annualStreetDone = ref(false);
const currentYear = new Date().getFullYear();
const canCreate = ref(false);
const undoVisible = ref(false);
const undoText = ref('');
// createVisible/createForm 已删(0725):创建表单迁独立页 /learning-create

let undoTimer = null;
let undoData = null;
function openDetail(item) {
  navigateTo('/pages/learning-detail/learning-detail?id=' + item.id);
}

function learningTypeName(item) {
  if (item.type === 'street') return '街镇培训';
  if (item.type === 'special') return '专项培训';
  return '内部学习';
}

function applyRecordFilter() {
  if (recordFilter.value === 'ended') {
    items.value = allItems.value.filter(i => i.stage === 'ended');
  } else if (recordFilter.value === 'pending') {
    items.value = allItems.value.filter(i => i.stage !== 'ended');
  } else {
    items.value = [...allItems.value];
  }
}

function switchFilter(filter) {
  recordFilter.value = filter;
  applyRecordFilter();
}

async function loadAll() {
  try {
    const [allInternal, allTraining] = await Promise.all([
      api.learningList('internal', null).catch(() => []),
      api.learningList('training', null).catch(() => [])
    ]);
    const completedInternal = allInternal.filter(i => i.stage === 'ended').length;
    const completedExternal = allTraining.filter(i => i.stage === 'ended').length;
    annualStudyCount.value = completedInternal + completedExternal;
    annualStreetDone.value = allTraining.some(i => i.type === 'street' && i.stage === 'ended');
    allItems.value = [...allInternal, ...allTraining].sort((a, b) => {
      const stageOrder = { preparing: 0, ongoing: 1, ended: 2 };
      const stageDiff = (stageOrder[a.stage] ?? 9) - (stageOrder[b.stage] ?? 9);
      if (stageDiff) return stageDiff;
      return String(b.date || '').localeCompare(String(a.date || ''));
    });
    applyRecordFilter();
  } catch (e) {
    allItems.value = [];
    items.value = [];
  }
}

// 手风琴展开态(0725):默认全收起;记录当前展开的一条,再点或点别的卡收回
const expandedId = ref(null)
function toggleExpand(item) {
  expandedId.value = expandedId.value === item.id ? null : item.id
}

function attendeeCount(value) {
  return String(value || '').split(/[,，、\s]+/).filter(Boolean).length;
}

function actualAttendanceCount(item) {
  const signs = item && item.signIns ? Object.values(item.signIns) : [];
  return signs.filter(Boolean).length;
}

function formatTime(value) {
  return String(value || '').slice(0, 5);
}

function showUndo(text, prev) {
  if (!prev) return;
  clearTimeout(undoTimer);
  undoData = prev;
  undoVisible.value = true;
  undoText.value = text;
  undoTimer = setTimeout(() => {
    undoData = null;
    undoVisible.value = false;
  }, 5000);
}

function undoLearning() {
  if (!undoData) return;
  clearTimeout(undoTimer);
  undoData = null;
  undoVisible.value = false;
  loadAll();
}

// ── 创建学习记录:表单已迁独立页 /learning-create(0725) ──
function openCreate() {
  window.location.assign('/learning-create')   // 硬跳,与驾驶舱各入口一致(软路由偶发不切视图)
}

let mounted = false;
onMounted(() => {
  mounted = true;
  canCreate.value = perm.can('learning.create');
  loadAll();
});
onActivated(() => {
  if (!mounted) return;
  canCreate.value = perm.can('learning.create');
  loadAll();
});
onUnmounted(() => {
  clearTimeout(undoTimer);
});
</script>

<style scoped>
.page { min-height: 100vh; background: #f4f5f7; padding: 0 24rpx 160rpx; box-sizing: border-box; }

/* tab 根页面：左侧占位与 PageNav 右侧 96rpx 占位对齐，标题保持居中 */
.nav-left-spacer { width: 96rpx; }

/* 年度目标 */
.learn-target { margin: 20rpx 0 24rpx; background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.lt-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20rpx; }
.lt-title { font-size: 32rpx; font-weight: 700; color: #1f2329; }
.lt-year { font-size: 25rpx; color: #8A94A6; }
.annual-list { display: flex; flex-direction: column; }
.annual-row { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 20rpx 0; border-top: 2rpx solid #F0F2F4; }
.annual-main { min-width: 0; display: flex; flex-direction: column; gap: 7rpx; }
.annual-name { font-size: 29rpx; font-weight: 700; color: #303747; }
.annual-desc { font-size: 24rpx; color: #8993A3; line-height: 1.35; }
.annual-status { flex-shrink: 0; min-width: 106rpx; text-align: center; padding: 7rpx 14rpx; border-radius: 999rpx; font-size: 24rpx; font-weight: 650; }
.annual-status.done { color: #2E7D50; background: #E8F4EC; }
.annual-status.warn { color: #A96518; background: #FAEEDC; }
.annual-status.neutral { color: #657183; background: #EEF1F4; }
.lt-badge { font-size: 28rpx; font-weight: 600; padding: 4rpx 16rpx; border-radius: 10rpx; }
.lt-badge.done { background: #E8F7EE; color: #27AE60; }
.lt-badge.warn { background: #FFF3E0; color: #E67E22; }
.lt-body { display: flex; align-items: center; gap: 28rpx; }
.lt-ring-wrap { position: relative; width: 184rpx; height: 184rpx; flex-shrink: 0; border-radius: 50%; background: #FFF3DC; display: flex; align-items: center; justify-content: center; }
.lt-ring { width: 184rpx; height: 184rpx; position: absolute; inset: 0; border-radius: 50%; -webkit-mask: radial-gradient(circle, transparent 66%, #000 67%); mask: radial-gradient(circle, transparent 66%, #000 67%); }
.lt-ring-center { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.lt-ring-num { font-size: 50rpx; font-weight: 700; color: #C77800; line-height: 1.1; }
.lt-ring-unit2 { font-size: 26rpx; font-weight: 400; color: #d6a64a; }
.lt-ring-label { font-size: 26rpx; color: #C8A05A; }
.lt-info { flex: 1; }
.lt-info-row { display: flex; align-items: center; gap: 12rpx; font-size: 28rpx; color: #6b7785; margin-bottom: 12rpx; }
.lt-dot { width: 16rpx; height: 16rpx; border-radius: 50%; flex-shrink: 0; }
.lt-bar-wrap { flex: 1; background: #f0f0f0; border-radius: 6rpx; height: 14rpx; overflow: hidden; }
.lt-bar-fill { height: 100%; border-radius: 6rpx; }
.lt-num { font-weight: 700; color: #1f2329; font-size: 28rpx; }
.lt-rule { font-size: 28rpx; color: #777; margin-top: 8rpx; display: block; }

.records-head { display: flex; align-items: center; justify-content: space-between; margin: 0 4rpx 14rpx; }
.records-title { font-size: 32rpx; font-weight: 700; color: #303747; }
.records-count { font-size: 24rpx; color: #8A94A6; }

/* 筛选标签 */
.filter-tabs { display: flex; gap: 0; margin-bottom: 20rpx; background: #fff; border-radius: 18rpx; padding: 8rpx; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.04); }
.f-tab { flex: 1; padding: 16rpx 8rpx; text-align: center; font-size: 28rpx; color: #666; border-radius: 12rpx; display: flex; align-items: center; justify-content: center; gap: 8rpx; }
.f-tab.active { background: var(--c-primary-dark); color: #fff; font-weight: 700; }
.f-count { font-size: 28rpx; opacity: 0.85; }

/* 学习卡片 */
.learn-list { display: flex; flex-direction: column; gap: 16rpx; }
/* 收起卡压缩到与业委会记录行同级(0725 用户定):内距/字号/标签整体降一档 */
.learn-card { background: #fff; border-radius: 20rpx; padding: 22rpx 24rpx; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.05); position: relative; }
.learn-card:active { background: #fafbfc; }
.lc-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 14rpx; }
.lc-title-wrap { min-width: 0; flex: 1; padding-right: 12rpx; display: block; text-wrap: balance; }  /* 标签+标题行内流排,折行均衡 */
.lc-type { display: inline-block; vertical-align: 3rpx; margin-right: 10rpx; font-size: 21rpx; color: #5F6E82; background: #EEF2F7; border-radius: 999rpx; padding: 3rpx 12rpx; line-height: 1.3; }
.lc-title { font-size: 30rpx; font-weight: 650; color: #1f2329; line-height: 1.5; }  /* 与业委会记录行同级字号 */
.lc-pill { font-size: 24rpx; font-weight: 600; padding: 4rpx 14rpx; border-radius: 10rpx; flex-shrink: 0; white-space: nowrap; align-self: flex-start; }
.lc-pill.preparing { background: #FFF3E0; color: #E67E22; }
.lc-pill.ongoing { background: #EBF5FB; color: #2980B9; }
.lc-pill.ended { background: #F0F0F0; color: #666; }
.lc-meta { display: flex; flex-direction: column; gap: 8rpx; margin: 16rpx 0 12rpx; padding-top: 18rpx; border-top: 2rpx solid #F1F3F5; }
.lc-detail-btn { display: block; width: 56%; height: 72rpx; margin: 8rpx auto 2rpx; border: 2rpx solid #D8C9A8; border-radius: 16rpx; background: #FFFBF2; color: #8B5A1E; font-size: 28rpx; font-weight: 600; }
.lc-detail-btn:active { background: #F7EFDD; }
.lc-meta-line { font-size: 30rpx; color: #6b7785; display: flex; align-items: center; gap: 10rpx; }
.lc-progress { display: flex; align-items: center; gap: 14rpx; margin: 8rpx 0 12rpx; }
.lc-progress-bar { flex: 1; background: #f0f0f0; border-radius: 8rpx; height: 12rpx; overflow: hidden; }
.lc-progress-fill { height: 100%; border-radius: 8rpx; background: #FFA800; }
.lc-progress-text { font-size: 28rpx; color: #C77800; font-weight: 700; flex-shrink: 0; }
.lc-footer { display: flex; justify-content: flex-end; gap: 14rpx; margin-top: 16rpx; border-top: 2rpx solid #f5f5f5; padding-top: 16rpx; }
/* 收起态指示:下箭头,与状态标签同行右侧对齐,展开后旋转 */
.lc-arrow { flex-shrink: 0; margin-left: 10rpx; margin-top: 4rpx; font-size: 36rpx; line-height: 44rpx; color: #8A94A0; transition: transform .2s; }  /* 与右侧状态标签同行等高对齐 */
.lc-arrow.open { transform: rotate(180deg); }
.lc-btn { min-height: 64rpx; line-height: 64rpx; padding: 0 30rpx; border-radius: 20rpx; border: none; font-size: 28rpx; font-weight: 600; margin: 0; display: flex; align-items: center; justify-content: center; }
.lc-btn.start { background: var(--c-primary-dark); color: #fff; }
.lc-btn.finish { background: #5DADE2; color: #fff; }

/* bottom 抬到底部 TabBar(100rpx) 之上，否则撤销条会被一级 tab 栏挡住 */
.undo-toast { position: fixed; left: 24rpx; right: 24rpx; bottom: calc(120rpx + env(safe-area-inset-bottom)); z-index: 40; background: rgba(45,45,45,0.94); color: #fff; border-radius: 18rpx; padding: 22rpx 26rpx; display: flex; align-items: center; justify-content: space-between; font-size: 28rpx; box-shadow: 0 8rpx 24rpx rgba(0,0,0,0.2); }
.undo-btn { color: #F4D03F; font-weight: 700; padding-left: 24rpx; }

/* FAB */
/* bottom 抬到底部 TabBar(100rpx) 之上，否则「+」新建按钮会压在一级 tab 栏上 */
/* FAB 已删(0725):压卡片、与「返回驾驶舱」浮球冲突;新建入口改列表尾部虚线条 */
.create-learning-entry { width: 64%; margin: 30rpx auto 16rpx; height: 84rpx; display: flex; align-items: center; justify-content: center; text-align: center; color: var(--c-text-mid); font-size: 30rpx; font-weight: 600; border: 2rpx dashed #C9D0D6; border-radius: 22rpx; background: #F5F6F8; cursor: pointer; }
.create-learning-entry:active { background: #EAEDF0; }

/* 创建弹窗样式已删(0725):表单迁独立页 /learning-create */
</style>
