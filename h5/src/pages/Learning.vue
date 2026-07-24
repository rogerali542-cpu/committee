<template>
  <div class="page" style="overflow-y:auto">

    <!-- 学习培训现为底部一级 Tab（0721 用户定：加回第4个 tab）：作为根页面不挂返回箭头，
         用空的 #left 占位保持标题居中，视觉与其它 tab 页的自定义头部一致。 -->
    <PageNav title="学习培训" style="margin: 0 -3.2vw 0">
      <template #left><div class="nav-left-spacer"></div></template>
    </PageNav>

    <!-- 类型切换: 内部学习 / 外部培训 -->
    <div class="type-tabs">
      <div class="type-tab" :class="{ active: learnType == 'internal' }" @click="switchType('internal')">内部学习</div>
      <div class="type-tab" :class="{ active: learnType == 'training' }" @click="switchType('training')">外部培训</div>
    </div>

    <!-- 年度履职概览：对应评分要求，但不把培训本身做成线上课程 -->
    <div class="learn-target">
      <div class="lt-head">
        <span class="lt-title">年度学习培训情况</span>
        <span class="lt-year">{{ currentYear }}年</span>
      </div>
      <div class="annual-list">
        <div class="annual-row">
          <div class="annual-main">
            <span class="annual-name">年度学习</span>
            <span class="annual-desc">内部学习及可计入的外部培训</span>
          </div>
          <span class="annual-status" :class="annualStudyCount >= target ? 'done' : 'warn'">
            {{ annualStudyCount }}/{{ target }}次
          </span>
        </div>
        <div class="annual-row">
          <div class="annual-main">
            <span class="annual-name">街镇业务培训</span>
            <span class="annual-desc">本年度至少参加1次</span>
          </div>
          <span class="annual-status" :class="annualStreetDone ? 'done' : 'warn'">
            {{ annualStreetDone ? '已参加' : '待参加' }}
          </span>
        </div>
        <div class="annual-row">
          <div class="annual-main">
            <span class="annual-name">规定人员专项培训</span>
            <span class="annual-desc">记录相关职责人员参加情况</span>
          </div>
          <span class="annual-status" :class="annualSpecialDone ? 'done' : 'neutral'">
            {{ annualSpecialDone ? '已有记录' : '暂无记录' }}
          </span>
        </div>
      </div>
    </div>

    <!-- 培训子分类（仅外部培训） -->
    <div class="train-sub-tabs" v-if="learnType == 'training'">
      <div class="tsc-card" :class="{ active: trainSub == 'street' }" @click="switchTrainSub('street')">
        <div class="tsc-icon" style="background:#EBF5FB;">🏛️</div>
        <span class="tsc-title">街镇组织培训</span>
        <span class="tsc-desc">街镇统一组织 · 按通知参加</span>
        <span class="tsc-badge">{{ streetCount }}</span>
      </div>
      <div class="tsc-card" :class="{ active: trainSub == 'special' }" @click="switchTrainSub('special')">
        <div class="tsc-icon" style="background:#FDF2E9;">🛡️</div>
        <span class="tsc-title">专项业务培训</span>
        <span class="tsc-desc">市区街道组织 · 相关人员参加</span>
        <span class="tsc-badge">{{ specialCount }}</span>
      </div>
    </div>

    <!-- 管理状态：培训在软件外进行，系统负责通知、结果登记和留档 -->
    <div class="filter-tabs">
      <div class="f-tab" :class="{ active: learnStage == 'preparing' }" @click="switchStage('preparing')">待参加 <span class="f-count">{{ counts.pending }}</span></div>
      <div class="f-tab" :class="{ active: learnStage == 'ongoing' }" @click="switchStage('ongoing')">待整理 <span class="f-count">{{ counts.ongoing }}</span></div>
      <div class="f-tab" :class="{ active: learnStage == 'ended' }" @click="switchStage('ended')">已完成 <span class="f-count">{{ counts.ended }}</span></div>
    </div>

    <!-- 学习卡片列表 -->
    <div class="learn-list">
      <template v-if="items.length">
        <div class="learn-card" v-for="item in items" :key="item.id" @click="openDetail(item)">
          <div class="lc-header">
            <span class="lc-title">{{ item.title }}</span>
            <span class="lc-pill" :class="item.stage">{{ item.stage === 'preparing' ? (item.notified ? '已通知' : '待通知') : item.stage === 'ongoing' ? '待整理' : '已完成' }}</span>
          </div>
          <div class="lc-meta">
            <span class="lc-meta-line">{{ item.date }} · {{ formatTime(item.time) }}</span>
            <span class="lc-meta-line">{{ item.location }}</span>
            <span class="lc-meta-line">组织：{{ item.trainer || '未填写' }}</span>
            <span class="lc-meta-line" v-if="item.attendees">
              {{ item.stage === 'ended' ? '实际参加' : '计划参加' }}：{{ item.stage === 'ended' ? actualAttendanceCount(item) : attendeeCount(item.attendees) }}人
            </span>
            <span class="lc-meta-line" v-if="item.evidences && item.evidences.length">已上传{{ item.evidences.length }}份材料</span>
          </div>
          <div class="lc-arrow">›</div>
        </div>
      </template>
      <div v-else class="empty-state"><span>暂无学习记录</span></div>
    </div>

    <!-- FAB 新建（主任/副主任/记录员可见） -->
    <div class="fab" v-if="canCreate" @click="openCreate">
      <span class="fab-icon">+</span>
    </div>

    <!-- 创建学习记录弹窗 -->
    <div v-if="createVisible" class="modal-mask" @click="closeCreate">
      <div class="form-sheet" @click.stop>
        <div class="sheet-head">
          <span class="sheet-title">新增学习记录</span>
          <span class="sheet-close" @click="closeCreate">×</span>
        </div>

        <div class="form-group">
          <span class="form-label">学习标题 *</span>
          <input class="form-input large" v-model="createForm.title" placeholder="如 2026年度第1次业委会学习" />
        </div>

        <div class="form-row">
          <div class="form-group half">
            <span class="form-label">日期 *</span>
            <input type="date" class="picker-field" :value="createForm.date" @change="onDateChange" />
          </div>
          <div class="form-group half">
            <span class="form-label">时间</span>
            <input type="time" class="picker-field" :value="createForm.time" @change="onTimeChange" />
          </div>
        </div>

        <div class="form-group">
          <span class="form-label">地点</span>
          <input class="form-input" v-model="createForm.location" placeholder="如 社区活动室" />
        </div>

        <div class="form-group">
          <span class="form-label">组织单位/讲师</span>
          <input class="form-input" v-model="createForm.trainer" placeholder="如 临汾路街道" />
        </div>

        <div class="form-group">
          <span class="form-label">计划参加人员（用于发送通知）</span>
          <input class="form-input" v-model="createForm.attendees" placeholder="如 张建国,李秀英,王志强" />
        </div>

        <div class="form-group">
          <span class="form-label">学习类型</span>
          <div class="type-row">
            <span class="type-chip" :class="{ on: createForm.type === 'internal' }" @click="pickCreateType('internal')">内部学习</span>
            <span class="type-chip" :class="{ on: createForm.type === 'street' }" @click="pickCreateType('street')">街镇培训</span>
            <span class="type-chip" :class="{ on: createForm.type === 'special' }" @click="pickCreateType('special')">专项培训</span>
          </div>
        </div>

        <div class="form-group">
          <span class="form-label">学习内容说明</span>
          <textarea class="form-textarea" v-model="createForm.description" placeholder="简述学习内容和目的" style="min-height:80px;height:80px;"></textarea>
        </div>

        <div class="sheet-actions weighted-actions">
          <button class="btn btn-ghost" @click="closeCreate">取消</button>
          <button class="btn btn-primary" @click="submitCreate">确认创建</button>
        </div>
      </div>
    </div>

    <div v-if="undoVisible" class="undo-toast">
      <span>{{ undoText }}</span>
      <span class="undo-btn" @click="undoLearning">撤销</span>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onActivated, onUnmounted } from 'vue';
import api from '@/api';
import PageNav from '@/components/PageNav.vue';
import perm from '@/utils/perm';
import { toast } from '@/utils/ui';
import { navigateTo } from '@/utils/navigate';

const learnType = ref('internal');
const learnStage = ref('preparing');
const trainSub = ref('street');
const items = ref([]);
const counts = ref({ pending: 0, ongoing: 0, ended: 0 });
const target = ref(2);
const streetCount = ref(0);
const specialCount = ref(0);
const annualStudyCount = ref(0);
const annualStreetDone = ref(false);
const annualSpecialDone = ref(false);
const currentYear = new Date().getFullYear();
const canCreate = ref(false);
const undoVisible = ref(false);
const undoText = ref('');
const createVisible = ref(false);
const createForm = reactive({
  title: '', date: '', time: '14:00', location: '',
  trainer: '', attendees: '', type: 'internal', description: ''
});

let undoTimer = null;
let undoData = null;
let initialViewResolved = false;

function openDetail(item) {
  navigateTo('/pages/learning-detail/learning-detail?id=' + item.id);
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
    annualSpecialDone.value = allTraining.some(i => i.type === 'special' && i.stage === 'ended');

    // 首次进入不固定停在一个空的“内部学习·待参加”筛选上。
    // 优先展示有待办的数据；没有待办时展示实际存在的完成记录，避免上方显示已完成、下方却说暂无记录。
    if (!initialViewResolved) {
      initialViewResolved = true;
      if (!allInternal.length && allTraining.length) {
        learnType.value = 'training';
        const streetRecords = allTraining.filter(i => i.type === 'street');
        trainSub.value = streetRecords.length ? 'street' : 'special';
      }
      const initialRecords = learnType.value === 'training'
        ? allTraining.filter(i => i.type === trainSub.value)
        : allInternal;
      if (!initialRecords.some(i => i.stage === 'preparing')) {
        learnStage.value = initialRecords.some(i => i.stage === 'ongoing') ? 'ongoing' : 'ended';
      }
    }

    if (learnType.value === 'training') {
      // counts 接口只认 internal/street/special；"training" 是聚合别名会 400。
      // 所以外部培训一次性拉全量(不带 stage)，本地按「子类 + 阶段」切分：
      // 列表只留当前阶段，而阶段角标要跨全部阶段现算——否则非选中阶段永远算成 0。
      const all = allTraining;
      const subAll = all.filter(i => i.type === trainSub.value);
      items.value = subAll.filter(i => i.stage === learnStage.value);
      counts.value = {
        pending: subAll.filter(i => i.stage === 'preparing').length,
        ongoing: subAll.filter(i => i.stage === 'ongoing').length,
        ended: subAll.filter(i => i.stage === 'ended').length
      };
      streetCount.value = all.filter(i => i.type === 'street').length;
      specialCount.value = all.filter(i => i.type === 'special').length;
    } else {
      items.value = allInternal.filter(i => i.stage === learnStage.value);
      counts.value = {
        pending: allInternal.filter(i => i.stage === 'preparing').length,
        ongoing: allInternal.filter(i => i.stage === 'ongoing').length,
        ended: allInternal.filter(i => i.stage === 'ended').length
      };
    }
  } catch (e) {
    items.value = [];
    counts.value = { pending: 0, ongoing: 0, ended: 0 };
  }
}

function switchType(type) {
  learnType.value = type;
  learnStage.value = 'preparing';
  loadAll();
}

function switchTrainSub(sub) {
  trainSub.value = sub;
  learnStage.value = 'preparing';
  loadAll();
}

function switchStage(stage) {
  learnStage.value = stage;
  loadAll();
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

// ── 创建学习记录 ──

function openCreate() {
  createVisible.value = true;
  createForm.title = '';
  createForm.date = todayStr();
  createForm.time = '14:00';
  createForm.location = '社区活动室';
  createForm.trainer = '';
  createForm.attendees = '';
  // 'training' 是街镇+专项的聚合别名、不是真实类型：在外部培训 tab 新建时默认取当前子分类，
  // 否则会带着 'training' 提交→后端 valueOf 失败静默落成 internal，记录从培训列表消失。
  createForm.type = learnType.value === 'training' ? trainSub.value : 'internal';
  createForm.description = '';
}

function closeCreate() {
  createVisible.value = false;
}

function onDateChange(e) {
  createForm.date = e.target.value;
}

function onTimeChange(e) {
  createForm.time = e.target.value;
}

function pickCreateType(type) {
  createForm.type = type;
}

async function submitCreate() {
  const form = createForm;
  if (!form.title || !form.date) {
    toast({ title: '请补全标题和日期', icon: 'none' });
    return;
  }
  try {
    const result = await api.learningCreate({ ...form });
    toast({ title: '已创建', icon: 'success' });
    createVisible.value = false;
    loadAll();
    // 自动跳转到详情页
    navigateTo('/pages/learning-detail/learning-detail?id=' + result.id);
  } catch (e) {
    toast({ title: e.message, icon: 'none' });
  }
}

function todayStr() {
  const d = new Date();
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
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

/* 类型标签 */
.type-tabs { display: flex; gap: 16rpx; margin: 20rpx 0; }
.type-tab { flex: 1; padding: 20rpx; text-align: center; background: #fff; border-radius: 18rpx; font-size: 30rpx; color: #666; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.04); font-weight: 500; }
.type-tab.active { background: var(--c-primary-dark); color: #fff; font-weight: 700; }

/* 年度目标 */
.learn-target { margin: 0 0 20rpx; background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.lt-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20rpx; }
.lt-title { font-size: 32rpx; font-weight: 700; color: #1f2329; }
.lt-year { font-size: 25rpx; color: #8A94A6; }
.annual-list { display: flex; flex-direction: column; }
.annual-row { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 20rpx 0; border-top: 2rpx solid #F0F2F4; }
.annual-main { min-width: 0; display: flex; flex-direction: column; gap: 7rpx; }
.annual-name { font-size: 29rpx; font-weight: 700; color: #303747; }
.annual-desc { font-size: 24rpx; color: #8993A3; line-height: 1.35; }
.annual-status { flex-shrink: 0; min-width: 106rpx; text-align: center; padding: 7rpx 14rpx; border-radius: 999rpx; font-size: 24rpx; font-weight: 700; }
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

/* 培训子分类卡片 */
.train-sub-tabs { display: flex; gap: 16rpx; margin-bottom: 20rpx; }
.tsc-card { flex: 1; background: #fff; border-radius: 24rpx; padding: 26rpx 22rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); border: 3rpx solid transparent; position: relative; }
.tsc-card.active { border-color: #FFA800; background: #FFFBF2; }
.tsc-icon { width: 72rpx; height: 72rpx; border-radius: 18rpx; display: flex; align-items: center; justify-content: center; margin-bottom: 14rpx; font-size: 38rpx; }
.tsc-title { font-size: 30rpx; font-weight: 700; color: #1f2329; display: block; margin-bottom: 6rpx; }
.tsc-desc { font-size: 28rpx; color: #666; line-height: 1.4; display: block; }
.tsc-badge { position: absolute; top: 16rpx; right: 16rpx; font-size: 28rpx; font-weight: 700; background: #FFF3DC; color: #C77800; min-width: 36rpx; height: 36rpx; padding: 0 10rpx; border-radius: 18rpx; display: flex; align-items: center; justify-content: center; }

/* 筛选标签 */
.filter-tabs { display: flex; gap: 0; margin-bottom: 20rpx; background: #fff; border-radius: 18rpx; padding: 8rpx; box-shadow: 0 4rpx 14rpx rgba(0,0,0,0.04); }
.f-tab { flex: 1; padding: 16rpx 8rpx; text-align: center; font-size: 28rpx; color: #666; border-radius: 12rpx; display: flex; align-items: center; justify-content: center; gap: 8rpx; }
.f-tab.active { background: var(--c-primary-dark); color: #fff; font-weight: 700; }
.f-count { font-size: 28rpx; opacity: 0.85; }

/* 学习卡片 */
.learn-list { display: flex; flex-direction: column; gap: 20rpx; }
.learn-card { background: #fff; border-radius: 24rpx; padding: 34rpx; padding-right: 60rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); position: relative; }
.learn-card:active { background: #fafbfc; }
.lc-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 14rpx; }
.lc-title { font-size: 40rpx; font-weight: 700; color: #1f2329; flex: 1; padding-right: 12rpx; line-height: 1.4; }
.lc-pill { font-size: 28rpx; font-weight: 600; padding: 4rpx 16rpx; border-radius: 12rpx; flex-shrink: 0; white-space: nowrap; }
.lc-pill.preparing { background: #FFF3E0; color: #E67E22; }
.lc-pill.ongoing { background: #EBF5FB; color: #2980B9; }
.lc-pill.ended { background: #F0F0F0; color: #666; }
.lc-meta { display: flex; flex-direction: column; gap: 8rpx; margin-bottom: 12rpx; }
.lc-meta-line { font-size: 30rpx; color: #6b7785; display: flex; align-items: center; gap: 10rpx; }
.lc-progress { display: flex; align-items: center; gap: 14rpx; margin: 8rpx 0 12rpx; }
.lc-progress-bar { flex: 1; background: #f0f0f0; border-radius: 8rpx; height: 12rpx; overflow: hidden; }
.lc-progress-fill { height: 100%; border-radius: 8rpx; background: #FFA800; }
.lc-progress-text { font-size: 28rpx; color: #C77800; font-weight: 700; flex-shrink: 0; }
.lc-footer { display: flex; justify-content: flex-end; gap: 14rpx; margin-top: 16rpx; border-top: 2rpx solid #f5f5f5; padding-top: 16rpx; }
.lc-arrow { position: absolute; right: 24rpx; top: 50%; transform: translateY(-50%); font-size: 44rpx; color: #666; }
.lc-btn { min-height: 64rpx; line-height: 64rpx; padding: 0 30rpx; border-radius: 20rpx; border: none; font-size: 28rpx; font-weight: 600; margin: 0; display: flex; align-items: center; justify-content: center; }
.lc-btn.start { background: var(--c-primary-dark); color: #fff; }
.lc-btn.finish { background: #5DADE2; color: #fff; }

/* bottom 抬到底部 TabBar(100rpx) 之上，否则撤销条会被一级 tab 栏挡住 */
.undo-toast { position: fixed; left: 24rpx; right: 24rpx; bottom: calc(120rpx + env(safe-area-inset-bottom)); z-index: 40; background: rgba(45,45,45,0.94); color: #fff; border-radius: 18rpx; padding: 22rpx 26rpx; display: flex; align-items: center; justify-content: space-between; font-size: 28rpx; box-shadow: 0 8rpx 24rpx rgba(0,0,0,0.2); }
.undo-btn { color: #F4D03F; font-weight: 700; padding-left: 24rpx; }

/* FAB */
/* bottom 抬到底部 TabBar(100rpx) 之上，否则「+」新建按钮会压在一级 tab 栏上 */
.fab { position: fixed; bottom: calc(140rpx + env(safe-area-inset-bottom)); right: 36rpx; width: 104rpx; height: 104rpx; background: var(--c-primary-dark); border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 8rpx 22rpx rgba(255,168,0,0.45); z-index: 30; }
.fab-icon { font-size: 56rpx; color: #fff; font-weight: 300; }

/* 创建弹窗 */
/* z-index 150 压过底部 TabBar(100)，否则新建弹窗底部会被一级 tab 栏骑住、按钮点不到 */
.modal-mask { position: fixed; inset: 0; z-index: 150; background: rgba(0,0,0,0.36); display: flex; align-items: flex-end; }
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
.form-input.large { height: 96rpx; min-height: 96rpx; font-size: 34rpx; font-weight: 600; }
.form-input::placeholder, .form-textarea::placeholder { color: #666; font-size: 30rpx; }
.picker-field { width: 100%; min-height: 88rpx; box-sizing: border-box; background: #f6f6f8; border: none; border-radius: 14rpx; padding: 0 20rpx; font-size: 32rpx; color: #1f2329; line-height: normal; display: flex; align-items: center; }
.form-textarea { min-height: 160rpx; height: 160rpx; line-height: 1.5; padding: 20rpx; }
.sheet-actions { display: flex; gap: 16rpx; padding-top: 12rpx; }
.sheet-actions .btn { flex: 1; min-width: 0; height: 88rpx; line-height: 88rpx; border-radius: 44rpx; font-size: 32rpx; font-weight: 600; padding: 0 24rpx; margin: 0; border: 0; display: flex; align-items: center; justify-content: center; box-sizing: border-box; }
.btn-ghost { color: #777; background: #f5f5f5; }
.btn-primary { color: #fff; background: var(--c-primary-dark); }
</style>
