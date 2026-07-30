<template>
  <div class="page" style="overflow-y:auto">

    <!-- 顶栏统一为业委会首页同款（0729 用户定：深青灰底 + 左对齐标题 + 身份副标）。
         .page 有 24rpx 横向内边距，.hd 用负边距抵消让顶栏满宽 -->
    <div class="hd">
      <div class="hd-left">
        <span class="hd-title">学习培训</span>
        <span class="hd-sub">{{ activeRole.realName }} · {{ activeRole.role }}</span>
      </div>
      <!-- 返回驾驶舱移入顶栏右上角（0730 用户定） -->
      <button type="button" class="hd-cockpit" @click="goCockpitFromHd">返回驾驶舱</button>
    </div>

    <!-- 年度履职摘要：只保留成员需要确认的两项 -->
    <div class="learn-target">
      <div class="lt-head" @click="toggleSummary">
        <span class="lt-title">{{ currentYear }}年度学习情况</span>
        <span class="lt-toggle" :class="{ open: summaryOpen }"></span>
      </div>
      <div class="annual-list" v-if="summaryOpen">
        <div class="annual-row">
          <div class="annual-main">
            <span class="annual-name">内部学习</span>
            <span class="annual-desc">年度要求至少完成{{ target }}次</span>
          </div>
          <span class="annual-status" :class="annualStudyCount >= target ? 'done' : 'warn'">
            已完成 {{ annualStudyCount }}/{{ target }}次
          </span>
        </div>
        <div class="annual-row">
          <div class="annual-main">
            <span class="annual-name">外部培训</span>
            <span class="annual-desc">年度要求至少参加1次</span>
          </div>
          <span class="annual-status" :class="annualExternalDone ? 'done' : 'warn'">
            {{ annualExternalDone ? '已参加' : '待参加' }}
          </span>
        </div>
      </div>
    </div>

    <!-- 进行中（0729 用户定）：已通知但还未完成登记的内部学习，置顶提醒，点卡片直接进登记页 -->
    <div v-if="canCreate && ongoingItems.length" class="inprogress-card">
      <div class="ip-head">
        <span class="ip-title">进行中</span>
        <span class="ip-count">{{ ongoingItems.length }} 项待登记</span>
      </div>
      <div v-for="it in ongoingItems" :key="it.id" class="ip-row" @click="goRegister(it)">
        <div class="ip-main">
          <span class="ip-name">{{ it.title }}</span>
          <span class="ip-sub">{{ it.date }} · {{ formatTime(it.time) }} · {{ it.stage === 'ongoing' ? '登记中' : '已通知，待登记结果' }}</span>
        </div>
        <span class="ip-go">去登记 ›</span>
      </div>
    </div>

    <!-- 两个入口（0728 用户定）：发起内部学习＝事前计划+通知；登记学习记录＝事后补录（含外部培训） -->
    <div v-if="canCreate" class="learn-actions">
      <button class="la-card la-internal" type="button" @click="openInitiate">
        <span class="la-copy"><strong>发起内部学习</strong><em>组织学习、通知参加</em></span>
        <span class="la-arrow">›</span>
      </button>
      <button class="la-card la-external" type="button" @click="openCreate">
        <span class="la-copy"><strong>登记外部培训</strong><em>记录已参加的外部培训</em></span>
        <span class="la-arrow">›</span>
      </button>
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
          <!-- 收起行=标题+浅灰摘要两行(0725 用户定,参照主流列表行:主行中等字重+副行弱化),类型在展开细节 -->
          <div class="lc-header">
            <div class="lc-title-wrap">
              <!-- 状态签移到标题旁(0728 用户定),右侧空间留给「详情」入口 -->
              <div class="lc-title-line">
                <span class="lc-title">{{ item.title }}</span>
                <span class="lc-pill" :class="item.stage">{{ item.stage === 'preparing' ? (item.notified ? '已通知' : '待通知') : item.stage === 'ongoing' ? '待整理' : '已完成' }}</span>
              </div>
              <!-- 副行只留日期时间(0725 用户定):地点太长必截断,反成噪音,展开细节里看全 -->
              <span class="lc-sub">{{ item.date }} · {{ formatTime(item.time) }}</span>
            </div>
            <span class="lc-more" :class="{ open: expandedId === item.id }">{{ expandedId === item.id ? '收起' : '详情' }}<span class="lc-more-arr"></span></span>
          </div>
          <template v-if="expandedId === item.id">
            <div class="lc-meta">
              <span class="lc-meta-line">分类：{{ item.categoryLabel || (item.category === 'external' ? '外部培训' : '内部学习') }}</span>
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

    <!-- 底部虚线入口已删(0725 用户定):新增是本页主动作,升级为列表上方大按钮 .learn-register-card -->

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
import perm from '@/utils/perm';
import { toast } from '@/utils/ui';
import { navigateTo } from '@/utils/navigate';
import { getStorage, setStorage } from '@/utils/storage';

// 顶栏身份副标（业委会首页同款）：realName · role
const activeRole = ref(getStorage('activeRole', {}) || {});
// 顶栏「返回驾驶舱」（0730：由 TabBar 浮球移入顶栏）
function goCockpitFromHd() {
  setStorage('home_layout', 'portal')
  window.location.replace('/main?home=portal')
}

const recordFilter = ref('all');
const allItems = ref([]);
const items = ref([]);
const recordCounts = computed(() => ({
  all: listItems.value.length,
  pending: listItems.value.filter(item => item.stage !== 'ended').length,
  ended: listItems.value.filter(item => item.stage === 'ended').length
}));
const target = ref(2);
const annualStudyCount = ref(0);
const annualExternalDone = ref(false);
const currentYear = new Date().getFullYear();
const canCreate = ref(false);
// 年度学习情况卡可折叠：默认展开；有"进行中"事项(管理者视角)时默认折叠以突出进行中；手动点过后尊重手动选择
const summaryOpen = ref(true);
let summaryTouched = false;
function toggleSummary() { summaryOpen.value = !summaryOpen.value; summaryTouched = true; }
const undoVisible = ref(false);
const undoText = ref('');
// createVisible/createForm 已删(0725):创建表单迁独立页 /learning-create

let undoTimer = null;
let undoData = null;
function openDetail(item) {
  navigateTo('/learning-detail?id=' + item.id);
}

function applyRecordFilter() {
  const src = listItems.value;   // 已排除"进行中"卡里的项（管理者视角）
  if (recordFilter.value === 'ended') {
    items.value = src.filter(i => i.stage === 'ended');
  } else if (recordFilter.value === 'pending') {
    items.value = src.filter(i => i.stage !== 'ended');
  } else {
    items.value = [...src];
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
    const merged = [...allInternal, ...allTraining];
    // 年度计数按显式分类 category（不再靠 type/接口拆分推断）
    annualStudyCount.value = merged.filter(i => i.category === 'internal' && i.stage === 'ended').length;
    annualExternalDone.value = merged.some(i => i.category === 'external' && i.stage === 'ended');
    allItems.value = merged.sort((a, b) => {
      const stageOrder = { preparing: 0, ongoing: 1, ended: 2 };
      const stageDiff = (stageOrder[a.stage] ?? 9) - (stageOrder[b.stage] ?? 9);
      if (stageDiff) return stageDiff;
      return String(b.date || '').localeCompare(String(a.date || ''));
    });
    applyRecordFilter();
    // 有进行中事项(且管理者能看到进行中卡)时默认折叠年度卡，突出进行中；用户手动点过则不再自动改
    if (!summaryTouched) summaryOpen.value = !(canCreate.value && ongoingItems.value.length);
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

// ── 登记外部培训:补录已参加的外部培训(0729 用户定:内部走发起、外部走登记) ──
function openCreate() {
  window.location.assign('/learning-create?type=external')   // 硬跳,与驾驶舱各入口一致(软路由偶发不切视图)
}
// 发起内部学习：事前计划+通知流程（0728 用户定）
function openInitiate() {
  window.location.assign('/learning-initiate')
}
// 进行中：已通知但未完成登记的内部学习（notified 且未结束）；点卡片进登记页
const ongoingItems = computed(() => allItems.value.filter(i => i.notified && i.stage !== 'ended'))
// 记录列表数据源：管理者视角下，"进行中"卡里的项(已通知未结束)不再重复列进下方记录；
// 非管理者无"进行中"卡，记录照常全列，避免看不到。
const listItems = computed(() => canCreate.value
  ? allItems.value.filter(i => !(i.notified && i.stage !== 'ended'))
  : allItems.value)
function goRegister(it) {
  window.location.assign('/learning-register?id=' + it.id)
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

/* 顶栏：业委会首页同款（深青灰底 + 白色标题 + 身份副标）。.page 有 24rpx 横向内边距，负边距抵消让顶栏满宽 */
.hd { display: flex; align-items: flex-end; justify-content: space-between; margin: 0 -24rpx; padding: calc(env(safe-area-inset-top) + 14rpx) 32rpx 18rpx; background: #A85800; }   /* 学习页顶栏转深橙（0730 用户定：含顶栏） */
.hd-left { display: flex; flex-direction: column; padding-top: 4rpx; }
.hd-cockpit { flex-shrink: 0; align-self: center; display: inline-flex; align-items: center; justify-content: center; min-height: 56rpx; padding: 0 26rpx; border: 2rpx solid rgba(255,255,255,.55); border-radius: 999rpx; background: rgba(255,255,255,.08); color: #fff; font-size: 26rpx; font-weight: 500; }
.hd-cockpit:active { background: rgba(255,255,255,.2); }
.hd-title { font-size: 42rpx; font-weight: 700; color: #fff; line-height: 1.25; }
.hd-sub { font-size: 28rpx; color: #fff; margin-top: 4rpx; line-height: 1.3; }

/* 年度目标 */
.learn-target { margin: 20rpx 0 24rpx; background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.lt-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0; }
.lt-head:active { opacity: 0.7; }
.lt-title { font-size: 32rpx; font-weight: 700; color: #1f2329; }
/* 折叠三角：展开时朝下，收起时朝右（0729 用户定） */
.lt-toggle { flex-shrink: 0; width: 0; height: 0; border-left: 9rpx solid transparent; border-right: 9rpx solid transparent; border-top: 11rpx solid #AAB2BC; transition: transform .2s ease; }
.lt-toggle:not(.open) { transform: rotate(-90deg); }
.annual-list { margin-top: 18rpx; }
.annual-list { display: flex; flex-direction: column; }
.annual-row { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 20rpx 0; border-top: 2rpx solid #F0F2F4; }
.annual-main { min-width: 0; display: flex; flex-direction: column; gap: 7rpx; }
.annual-name { font-size: 29rpx; font-weight: 700; color: #303747; }
.annual-desc { font-size: 24rpx; color: #8993A3; line-height: 1.35; }
.annual-status { flex-shrink: 0; min-width: 106rpx; text-align: center; padding: 7rpx 14rpx; border-radius: 999rpx; font-size: 24rpx; font-weight: 650; }
.annual-status.done { color: #2E7D50; background: #E8F4EC; }
.annual-status.warn { color: #A96518; background: #FAEEDC; }
.annual-status.neutral { color: #657183; background: #EEF1F4; }

/* 进行中：待登记提醒卡（0729 用户定），暖橙描边强调、点行进登记页 */
.inprogress-card { margin: 0 0 24rpx; background: linear-gradient(135deg, #FFFDFA 0%, #FBF3E7 100%); border: 2rpx solid #EAD9BF; border-radius: 24rpx; padding: 22rpx 24rpx; box-shadow: 0 8rpx 28rpx rgba(96, 72, 40, 0.08); }
.ip-head { display: flex; align-items: baseline; gap: 14rpx; margin-bottom: 12rpx; }
.ip-title { font-size: 30rpx; font-weight: 750; color: #7E571C; }
.ip-count { font-size: 24rpx; color: #A8895C; }
.ip-row { display: flex; align-items: center; gap: 16rpx; padding: 16rpx 6rpx; border-top: 2rpx solid #F0E3CE; }
.ip-row:first-of-type { border-top: none; }
.ip-row:active { opacity: 0.7; }
.ip-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6rpx; }
.ip-name { font-size: 30rpx; font-weight: 600; color: #3A2E1C; line-height: 1.35; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ip-sub { font-size: 24rpx; color: #A08A6A; }
.ip-go { flex-shrink: 0; font-size: 27rpx; font-weight: 700; color: #B0772E; white-space: nowrap; }

.records-head { display: flex; align-items: center; justify-content: space-between; margin: 0 4rpx 14rpx; }
.records-title { font-size: 32rpx; font-weight: 700; color: #303747; }
.records-count { font-size: 24rpx; color: #8A94A6; }

/* 筛选标签 */
.filter-tabs { display: flex; gap: 0; margin-bottom: 20rpx; background: #fff; border-radius: 18rpx; padding: 8rpx; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.04); }
.f-tab { flex: 1; padding: 16rpx 8rpx; text-align: center; font-size: 28rpx; color: #666; border-radius: 12rpx; display: flex; align-items: center; justify-content: center; gap: 8rpx; }
.f-tab.active { background: var(--c-primary-dark); color: #fff; font-weight: 700; }

/* 学习卡片 */
.learn-list { display: flex; flex-direction: column; gap: 16rpx; }
/* 收起卡压缩到与业委会记录行同级(0725 用户定):内距/字号/标签整体降一档 */
.learn-card { background: #fff; border-radius: 20rpx; padding: 22rpx 24rpx; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.05); position: relative; }
.learn-card:active { background: #fafbfc; }
.lc-header { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; }
.lc-title-wrap { min-width: 0; flex: 1; }
/* 标题行=标题+状态签并排(0728 用户定:状态签移到标题旁,腾出右侧给「详情」) */
.lc-title-line { display: flex; align-items: flex-start; gap: 12rpx; }
.lc-title { min-width: 0; font-size: 30rpx; font-weight: 560; color: #1f2329; line-height: 1.4; }  /* 中等字重,别用大黑体压场 */
.lc-sub { display: block; margin-top: 8rpx; font-size: 24rpx; color: #808C99; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }  /* 副行一行截断,超长省略 */
.lc-pill { flex-shrink: 0; margin-top: 2rpx; font-size: 24rpx; font-weight: 600; padding: 4rpx 14rpx; border-radius: 10rpx; white-space: nowrap; }
/* 三级状态色（0730 设计师定，全 app 通用）：已通知/待通知=常态灰、待整理=进行中蓝(当前该办的阶段)、
   已完成=常态灰。（年度达标进度 .annual-status 属合规指标、非任务状态，保留绿/琥珀语义不动） */
.lc-pill.preparing { background: transparent; color: #6B7280; }
.lc-pill.ongoing { background: transparent; color: #2F5F9E; }
.lc-pill.ended { background: transparent; color: #6B7280; }
.lc-meta { display: flex; flex-direction: column; gap: 8rpx; margin: 16rpx 0 12rpx; padding-top: 18rpx; border-top: 2rpx solid #F1F3F5; }
.lc-detail-btn { display: block; width: 56%; height: 72rpx; margin: 8rpx auto 2rpx; border: 2rpx solid #D8C9A8; border-radius: 16rpx; background: #FFFBF2; color: #8B5A1E; font-size: 28rpx; font-weight: 600; }
.lc-detail-btn:active { background: #F7EFDD; }
.lc-meta-line { font-size: 30rpx; color: #6b7785; display: flex; align-items: center; gap: 10rpx; }
/* 展开入口：文字「详情/收起」+ 旋转箭头(0728 用户定:替代原裸箭头,更明确好点) */
.lc-more { flex-shrink: 0; display: inline-flex; align-items: center; gap: 8rpx; color: #8A94A0; font-size: 26rpx; white-space: nowrap; }
/* 箭头改 CSS 绘制并垂直居中（字符 ⌄ 基线偏低会下坠） */
.lc-more-arr { display: inline-block; width: 12rpx; height: 12rpx; border-right: 3rpx solid currentColor; border-bottom: 3rpx solid currentColor; transform: rotate(45deg); position: relative; top: -2rpx; transition: transform .2s ease, top .2s ease; }
.lc-more.open .lc-more-arr { transform: rotate(-135deg); top: 2rpx; }

/* bottom 抬到底部 TabBar(100rpx) 之上，否则撤销条会被一级 tab 栏挡住 */
.undo-toast { position: fixed; left: 24rpx; right: 24rpx; bottom: calc(120rpx + env(safe-area-inset-bottom)); z-index: 40; background: rgba(45,45,45,0.94); color: #fff; border-radius: 18rpx; padding: 22rpx 26rpx; display: flex; align-items: center; justify-content: space-between; font-size: 28rpx; box-shadow: 0 8rpx 24rpx rgba(0,0,0,0.2); }
.undo-btn { color: #F4D03F; font-weight: 700; padding-left: 24rpx; }

/* FAB */
/* bottom 抬到底部 TabBar(100rpx) 之上，否则「+」新建按钮会压在一级 tab 栏上 */
/* FAB 已删(0725):压卡片、与「返回驾驶舱」浮球冲突;新建入口改列表尾部虚线条 */
/* 两个入口卡（0729 用户定：与进行中卡拉开层次——白底 + 左侧不同色竖条，内部暖橙/外部蓝） */
.learn-actions { display: flex; flex-direction: column; gap: 16rpx; margin: 30rpx 0 34rpx; }
.la-card { position: relative; overflow: hidden; display: flex; align-items: center; justify-content: space-between; gap: 18rpx; width: 100%; box-sizing: border-box;
  padding: 24rpx 28rpx; text-align: left; border: 2rpx solid #ECEEF1; border-radius: 20rpx; color: inherit;
  background: #fff; box-shadow: 0 4rpx 14rpx rgba(20, 42, 58, 0.05); }
.la-card::before { content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 8rpx; }
.la-card:active { opacity: 0.7; }
/* 0730 用户定：学习页统一深橙色系——内部=深橙、外部=浅一档的琥珀，不再用蓝 */
.la-internal::before { background: #A85800; }   /* 内部学习＝深橙 */
.la-external::before { background: #C98F3F; }   /* 外部培训＝琥珀 */
.la-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.la-copy strong { font-size: 32rpx; line-height: 1.3; font-weight: 700; }
.la-internal .la-copy strong { color: #8F4A06; }
.la-external .la-copy strong { color: #96702B; }
.la-copy em { font-size: 24rpx; line-height: 1.35; color: #99A0A8; font-style: normal; }
.la-arrow { flex-shrink: 0; font-size: 38rpx; font-weight: 700; }
.la-internal .la-arrow { color: #C88A4A; }
.la-external .la-arrow { color: #D3B074; }

/* 创建弹窗样式已删(0725):表单迁独立页 /learning-create */
</style>
