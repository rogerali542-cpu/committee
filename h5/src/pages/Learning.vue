<template>
  <div class="page" :class="{ 'has-bar': canCreate }" style="overflow-y:auto">

    <!-- 顶栏统一为业委会首页同款（0729 用户定：深青底 + 左对齐标题 + 身份副标）。
         .page 有 24rpx 横向内边距，.hd 用负边距抵消让顶栏满宽 -->
    <div class="hd">
      <div class="hd-left">
        <span class="hd-title">学习培训</span>
        <!-- 身份行＝个人中心入口（0731 定稿骨架：与业委会页头一致） -->
        <span class="hd-sub hd-sub-link" @click="goProfilePage">{{ activeRole.realName }} · {{ activeRole.role }} ›</span>
      </div>
      <!-- 页头「返回首页」已删（0731 设计师定）：标签页回家走底栏「首页」格，按钮重复 -->
    </div>

    <!-- 年度学习情况（0731 定稿骨架）：图标+标题头，两行「内部学习/外部培训」；
         右侧状态按三级色——未达标=暖胶囊（唯一跳出来的），达标=无底灰字 -->
    <div class="learn-target">
      <div class="lt-head">
        <span class="lt-ico">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 6.5C10.5 5 8.2 4.4 5.5 4.4c-.8 0-1.5.6-1.5 1.4v11c0 .8.7 1.4 1.5 1.4 2.7 0 5 .6 6.5 2.1 1.5-1.5 3.8-2.1 6.5-2.1.8 0 1.5-.6 1.5-1.4v-11c0-.8-.7-1.4-1.5-1.4-2.7 0-5 .6-6.5 2.1z"/><line x1="12" y1="6.5" x2="12" y2="20.3"/>
          </svg>
        </span>
        <span class="lt-title">{{ currentYear }}年度学习情况</span>
      </div>
      <div class="annual-row">
        <span class="annual-name">内部学习</span>
        <span v-if="isLoading" class="annual-status ok">读取中…</span>
        <span v-else class="annual-status" :class="annualStudyCount >= target ? 'ok' : 'warn'">已完成 {{ annualStudyCount }} / {{ target }} 次</span>
      </div>
      <div class="annual-row">
        <span class="annual-name">外部培训</span>
        <span v-if="isLoading" class="annual-status ok">读取中…</span>
        <span v-else class="annual-status" :class="annualExternalCount >= 1 ? 'ok' : 'warn'">{{ annualExternalCount >= 1 ? ('已参加 ' + annualExternalCount + ' / 1 次') : '待参加 0 / 1 次' }}</span>
      </div>
    </div>

    <!-- 最近一项：只表达近期安排；没有安排是正常业务空态，不与数据加载失败混淆 -->
    <section class="learn-section">
      <h2>当前安排</h2>
      <div v-if="isLoading" class="learning-empty">正在读取近期安排…</div>
      <div v-else-if="nextItem" class="next-learning" @click="tapItem(nextItem)">
        <span class="record-tag" :class="nextItem.category === 'external' ? 'external' : 'internal'">{{ recordType(nextItem) }}</span>
        <strong>{{ nextItem.title }}</strong>
        <p>{{ fmtD(nextItem.date) }}<span v-if="nextItem.location"> · {{ nextItem.location }}</span> · {{ stageText(nextItem) }}</p>
        <i class="ll-arr"></i>
      </div>
      <div v-else class="learning-empty">暂无近期学习培训安排</div>
    </section>

    <!-- 内部学习与外部培训共用一套记录，不再拆成两个入口 -->
    <section class="learn-section records-section">
      <div class="section-head">
        <h2>学习培训记录</h2>
        <span>{{ isLoading ? '读取中…' : (recordItems.length + '条') }}</span>
      </div>
      <div v-for="it in recordItems.slice(0, 4)" :key="it.id" class="record-row" @click="openDetail(it)">
        <span class="record-tag" :class="it.category === 'external' ? 'external' : 'internal'">{{ recordType(it) }}</span>
        <span class="record-main">
          <b>{{ it.title }}</b>
          <em>{{ fmtD(it.date) }} · {{ it.category === 'external' ? '已登记' : '已完成' }}</em>
        </span>
        <i class="ll-arr"></i>
      </div>
      <div v-if="isLoading" class="learning-empty compact">正在读取学习培训记录…</div>
      <div v-else-if="!recordItems.length" class="learning-empty compact">暂无学习培训记录</div>
      <button v-if="recordItems.length" type="button" class="view-records" @click="goArchive('learning')">查看全部记录</button>
    </section>

    <!-- 现有两个操作入口保持分工：内部学习走发起，外部培训走补录 -->
    <div class="learn-links">
      <div v-if="canCreate" class="learn-link" @click="openCreate">
        <span class="ll-text">＋ 登记外部培训</span>
        <i class="ll-arr"></i>
      </div>
    </div>

    <!-- 底部主按钮（0731 定稿骨架）：发起内部学习＝本页唯一实心色块，按 CLAUDE.md 钉底栏上沿 -->
    <div v-if="canCreate" class="learn-bar">
      <button type="button" class="learn-primary" @click="openInitiate">发起内部学习</button>
    </div>

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
import { navigateTo } from '@/utils/navigate';
import { getStorage } from '@/utils/storage';

// 顶栏身份副标（业委会首页同款）：realName · role，点击进个人中心
const activeRole = ref(getStorage('activeRole', {}) || {});
function goProfilePage() {
  navigateTo('/profile')
  setTimeout(() => { if (!document.querySelector('.profile-scroll')) window.location.href = '/profile' }, 300)
}

const allItems = ref([]);
const items = ref([]);
const target = ref(2);
const annualStudyCount = ref(0);
const annualExternalCount = ref(0);
const currentYear = new Date().getFullYear();
const canCreate = ref(false);
const undoVisible = ref(false);
const undoText = ref('');
const isLoading = ref(true);

let undoTimer = null;
let undoData = null;
function openDetail(item) {
  navigateTo('/learning-detail?id=' + item.id);
}
// 培训记录入口（0731 定稿骨架）：全部记录在历史记录学习页签
function goArchive(tab) {
  const url = '/library' + (tab ? '?tab=' + tab : '');
  navigateTo(url);
  // 软路由偶发不切视图（本仓已知坑）：0.3s 后没见到档案馆根节点就硬跳
  setTimeout(() => {
    if (!document.querySelector('.arch-page')) window.location.href = url;
  }, 300);
}

async function loadAll() {
  isLoading.value = true;
  try {
    const [allInternal, allTraining] = await Promise.all([
      api.learningList('internal', null).catch(() => []),
      api.learningList('training', null).catch(() => [])
    ]);
    const merged = [...allInternal, ...allTraining];
    // 年度计数按显式分类 category（不再靠 type/接口拆分推断）
    annualStudyCount.value = merged.filter(i => i.category === 'internal' && i.stage === 'ended').length;
    annualExternalCount.value = merged.filter(i => i.category === 'external' && i.stage === 'ended').length;
    allItems.value = merged.sort((a, b) => {
      const stageOrder = { preparing: 0, ongoing: 1, ended: 2 };
      const stageDiff = (stageOrder[a.stage] ?? 9) - (stageOrder[b.stage] ?? 9);
      if (stageDiff) return stageDiff;
      return String(b.date || '').localeCompare(String(a.date || ''));
    });
    // 在办轻行：非 ended 全列（0731 定稿骨架：不再分"进行中卡/记录列表"两摊）
    items.value = allItems.value.filter(i => i.stage !== 'ended');
  } catch (e) {
    allItems.value = [];
    items.value = [];
  } finally {
    isLoading.value = false;
  }
}

const nextItem = computed(() => {
  return allItems.value
    .filter(i => i.stage !== 'ended')
    .slice()
    .sort((a, b) => String(a.date || '9999-12-31').localeCompare(String(b.date || '9999-12-31')))[0] || null
})
const recordItems = computed(() => allItems.value.filter(i => i.stage === 'ended'))
function recordType(it) {
  if (it.type === 'special') return '专项培训'
  return it.category === 'external' ? '外部培训' : '内部学习'
}

// "2026-08-12" → "8月12日"
function fmtD(s) {
  const p = String(s || '').split('-')
  return p.length === 3 ? (Number(p[1]) + '月' + Number(p[2]) + '日') : String(s || '')
}
function stageText(it) {
  if (it.stage === 'ongoing') return '待整理'
  return it.notified ? '已通知' : '待通知'
}
// 在办行点击：管理者对已通知项直达登记页（原「进行中卡」快捷路径保留），其余进详情
function tapItem(it) {
  if (canCreate.value && it.notified && it.stage !== 'ended') {
    window.location.assign('/learning-register?id=' + it.id)
    return
  }
  openDetail(it)
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
.page { min-height: 100%; background: #f4f5f7; padding: 0 24rpx 40rpx; box-sizing: border-box; }   /* 0731 app 壳：底栏在滚动区外 */
.page.has-bar { padding-bottom: 184rpx; }   /* fixed 主按钮条≈148rpx + 缝隙（CLAUDE.md 配方） */

/* 顶栏：业委会首页同款（深青底 + 白色标题 + 身份副标）。.page 有 24rpx 横向内边距，负边距抵消让顶栏满宽 */
.hd { display: flex; align-items: flex-end; justify-content: space-between; margin: 0 -24rpx; padding: calc(env(safe-area-inset-top) + 14rpx) 32rpx 18rpx; background: #2a6b73; }   /* 学习页顶栏＝模块深青 */
.hd-left { display: flex; flex-direction: column; padding-top: 4rpx; }
.hd-title { font-size: 42rpx; font-weight: 700; color: #fff; line-height: 1.25; }
.hd-sub { font-size: 28rpx; color: #bdd8db; margin-top: 4rpx; line-height: 1.3; }
.hd-sub-link { cursor: pointer; }
.hd-sub-link:active { opacity: .65; }

/* 年度学习情况卡（0731 定稿骨架）：图标+标题头 + 两行大间距；行高对齐 62px 拇指区标准 */
.learn-target { margin: 20rpx 0 10rpx; overflow: hidden; background: #fff; border-radius: 24rpx; padding: 10rpx 30rpx; box-shadow: 0 2rpx 6rpx rgba(31,41,55,.05), 0 10rpx 26rpx rgba(31,41,55,.07); }
.lt-head { display: flex; align-items: center; gap: 14rpx; min-height: 96rpx; }
.lt-ico { flex-shrink: 0; width: 34rpx; height: 34rpx; color: #2a6b73; display: inline-flex; }
.lt-ico svg { width: 34rpx; height: 34rpx; }
.lt-title { font-size: 31rpx; font-weight: 700; color: #1f2329; }
.annual-row { display: flex; min-width: 0; align-items: center; justify-content: space-between; gap: 20rpx; min-height: 124rpx; border-top: 2rpx solid #F0F2F5; }
.annual-name { font-size: 33rpx; font-weight: 700; color: #1F2937; }
/* 三级色（规范§四）：未达标=暖胶囊（唯一跳出来的），达标=无底灰字 */
.annual-status { flex-shrink: 0; max-width: 43%; font-size: 27rpx; font-weight: 600; text-align: right; }
.annual-status.ok { color: #6B7280; font-weight: 500; }
.annual-status.warn { color: #9A5B12; background: #F7E4C6; padding: 8rpx 18rpx; border-radius: 10rpx; }

/* 轻列表（62px 行高标准，与首页/会议页一致）：透明底+分隔线 */
.learn-links { margin-top: 24rpx; padding: 0 8rpx; }
.learn-link { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; min-height: 124rpx; border-top: 2rpx solid #E2E5EA; cursor: pointer; }
.learn-link:last-child { border-bottom: 2rpx solid #E2E5EA; }
.learn-link:active { opacity: .65; }
.ll-text { flex: 1; min-width: 0; font-size: 32rpx; color: #1F2937; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ll-text b { font-weight: 600; }
.ll-text em { font-style: normal; font-weight: 500; color: #6B7280; font-size: 28rpx; }
.ll-arr { flex-shrink: 0; display: inline-block; width: 14rpx; height: 14rpx; border-right: 3rpx solid #B4BCC7; border-bottom: 3rpx solid #B4BCC7; transform: rotate(-45deg); }

.learn-section { margin-top: 24rpx; padding: 26rpx 28rpx; background: #fff; border-radius: 24rpx; box-shadow: 0 2rpx 6rpx rgba(31,41,55,.04), 0 10rpx 24rpx rgba(31,41,55,.06); }
.learn-section h2 { margin: 0; font-size: 32rpx; line-height: 1.4; color: #1F2937; }
.section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8rpx; }
.section-head > span { font-size: 25rpx; color: #8792A2; }
.next-learning { position: relative; margin-top: 20rpx; padding: 20rpx 40rpx 4rpx 0; cursor: pointer; }
.next-learning:active, .record-row:active { opacity: .68; }
.next-learning strong { display: block; margin-top: 13rpx; font-size: 33rpx; line-height: 1.4; color: #1F2937; }
.next-learning p { margin: 10rpx 0 0; font-size: 27rpx; line-height: 1.5; color: #7A8594; }
.next-learning .ll-arr { position: absolute; top: 50%; right: 4rpx; margin-top: -7rpx; }
.record-tag { display: inline-flex; align-items: center; min-height: 42rpx; padding: 0 14rpx; border-radius: 9rpx; background: #E6F2F3; color: #28636A; font-size: 23rpx; font-weight: 650; }
.record-tag.external { background: #F3EADB; color: #94601F; }
.record-row { display: flex; align-items: center; gap: 16rpx; min-height: 116rpx; border-top: 2rpx solid #F0F2F5; cursor: pointer; }
.record-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8rpx; }
.record-main b { overflow: hidden; color: #1F2937; font-size: 29rpx; font-weight: 650; white-space: nowrap; text-overflow: ellipsis; }
.record-main em { color: #7A8594; font-size: 25rpx; font-style: normal; }
.learning-empty { margin-top: 18rpx; padding: 27rpx 0 12rpx; color: #8792A2; font-size: 28rpx; text-align: center; }
.learning-empty.compact { margin-top: 4rpx; padding: 38rpx 0 24rpx; }
.view-records { display: block; width: 100%; min-height: 76rpx; border: 0; border-top: 2rpx solid #F0F2F5; background: transparent; color: #2A6B73; font-size: 27rpx; font-weight: 600; }

/* 底部主按钮条（CLAUDE.md 配方：fixed 钉底栏上沿；规格对齐会议/接待页主按钮） */
.learn-bar { position: fixed; left: 0; right: 0; bottom: calc(98rpx + env(safe-area-inset-bottom)); z-index: 90; padding: 12rpx 24rpx 16rpx; background: #fff; box-shadow: 0 -10rpx 24rpx rgba(20,42,58,.06); }
.learn-primary { width: 100%; min-height: 120rpx; border: 0; border-radius: 20rpx; background: #2a6b73; color: #fff; font-size: 38rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; }
.learn-primary:active { background: #235a61; }

/* bottom 抬到底部 TabBar(100rpx) 之上，否则撤销条会被一级 tab 栏挡住 */
.undo-toast { position: fixed; left: 24rpx; right: 24rpx; bottom: calc(120rpx + env(safe-area-inset-bottom)); z-index: 40; background: rgba(45,45,45,0.94); color: #fff; border-radius: 18rpx; padding: 22rpx 26rpx; display: flex; align-items: center; justify-content: space-between; font-size: 28rpx; box-shadow: 0 8rpx 24rpx rgba(0,0,0,0.2); }
.undo-btn { color: #F4D03F; font-weight: 700; padding-left: 24rpx; }
</style>
