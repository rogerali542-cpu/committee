<template>
  <div class="pub-page arch-page">
    <PublishNav title="历史记录" />

    <div class="pub-wrap">
      <!-- 三页签：会议 / 接待 / 学习（0730 设计师定：统一档案馆，中性深灰、不用三色） -->
      <div class="arch-tabs">
        <div v-for="t in TABS" :key="t.key" class="arch-tab" :class="{ active: tab === t.key }" @click="switchTab(t.key)">
          {{ t.label }}<span class="arch-tab-count">{{ counts[t.key] || 0 }}</span>
        </div>
      </div>

      <!-- 年份筛选（客户端；三类数据均无服务端年份参数）。只一个年份时不显示，免冗余 -->
      <div v-if="years.length > 1" class="arch-years">
        <div class="arch-year" :class="{ active: yearFilter === '' }" @click="setYear('')">全部 {{ curList.length }}</div>
        <div v-for="y in years" :key="y" class="arch-year" :class="{ active: yearFilter === y }" @click="setYear(y)">{{ y }}年 {{ yearCounts[y] || 0 }}</div>
      </div>

      <!-- 纯轻列表（spec §5：只需知晓的历史信息＝透明底+分隔线，非白卡） -->
      <div v-if="pageItems.length" class="arch-list">
        <div v-for="item in pageItems" :key="item.kind + '-' + item.id" class="arch-row" @click="openDetail(item)">
          <div class="arch-info">
            <div class="arch-title">{{ item.title }}</div>
            <div class="arch-meta">
              <span v-if="item.date">{{ item.date }}</span>
              <template v-if="item.metaText"><span class="arch-dot">·</span><span>{{ item.metaText }}</span></template>
            </div>
          </div>
          <span v-if="item.statusText" class="arch-status">{{ item.statusText }}</span>
          <span class="arch-arrow">›</span>
        </div>
      </div>

      <!-- 翻页条（0803 用户定：记录会累到几百条，分段加载会退化成点二十次）：
           上一页 / 第 N 页 共 M 页 / 下一页，两侧按钮 ≥46px；中间那块可点，
           页数多时弹出跳页，免得从第 1 页一路点到第 12 页 -->
      <div v-if="totalPages > 1" class="arch-pager">
        <button type="button" class="ap-btn" :disabled="page <= 1" @click="goPage(page - 1)">
          <i class="ap-arr pre"></i>上一页
        </button>
        <button type="button" class="ap-now" :disabled="totalPages <= 2" @click="openPageJump">
          第 {{ page }} 页 · 共 {{ totalPages }} 页
        </button>
        <button type="button" class="ap-btn" :disabled="page >= totalPages" @click="goPage(page + 1)">
          下一页<i class="ap-arr next"></i>
        </button>
      </div>

      <!-- ⚠ 空态用独立 v-if（不能接 v-else-if）：中间隔了翻页条，链会断 -->
      <div v-if="!items.length && !loading" class="arch-empty">
        <div class="empty-icon">📚</div>
        <span class="empty-title">{{ curTab.label }}暂无归档</span>
        <span class="empty-text">{{ curTab.emptyHint }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onActivated } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import PublishNav from '@/components/PublishNav.vue'
import { navigateTo, redirectTo } from '@/utils/navigate'
import { getStorage } from '@/utils/storage'
import { showActionSheet } from '@/utils/ui'

const route = useRoute()

const TABS = [
  { key: 'committee', label: '会议', emptyHint: '已归档的会议纪要会显示在这里。' },
  { key: 'reception', label: '接待', emptyHint: '往期接待记录与已办事项会显示在这里。' },
  { key: 'learning', label: '学习', emptyHint: '已完成的培训记录会显示在这里。' },
  // 事项页签（0731 设计师稿；原名「已办」，0731 用户定改「事项」）：
  // 业委会待办页只留未办，已办结的待办（会议+接待）统一收进这里
  { key: 'done', label: '事项', emptyHint: '已办结的待办事项会显示在这里。' }
]

const tab = ref('committee')
const yearFilter = ref('')
const lists = ref({ committee: [], reception: [], learning: [], done: [] })
const counts = ref({ committee: 0, reception: 0, learning: 0, done: 0 })
const loading = ref(true)

function byDateDesc(a, b) { return String(b.date || '').localeCompare(String(a.date || '')) }

const curTab = computed(() => TABS.find(t => t.key === tab.value) || TABS[0])
const curList = computed(() => lists.value[tab.value] || [])
const years = computed(() => {
  const ys = new Set()
  curList.value.forEach(it => { const y = String(it.date || '').slice(0, 4); if (/^\d{4}$/.test(y)) ys.add(y) })
  return [...ys].sort((a, b) => b.localeCompare(a))
})
// 每个年份的条数（0803 用户定）：胶囊上直接写清楚，选之前就知道那年有多少
const yearCounts = computed(() => {
  const m = {}
  curList.value.forEach(it => { const y = String(it.date || '').slice(0, 4); if (/^\d{4}$/.test(y)) m[y] = (m[y] || 0) + 1 })
  return m
})
// 跨年份的页签默认只看最近一年（0803 用户定：几百条堆一起翻页也累）；
// 只有一个年份时筛选条不显示，默认自然是全部
function defaultYearFor(key) {
  const ys = new Set()
    ; (lists.value[key] || []).forEach(it => { const y = String(it.date || '').slice(0, 4); if (/^\d{4}$/.test(y)) ys.add(y) })
  const arr = [...ys].sort((a, b) => b.localeCompare(a))
  return arr.length > 1 ? arr[0] : ''
}
const items = computed(() => {
  const list = curList.value
  return yearFilter.value ? list.filter(it => String(it.date || '').startsWith(yearFilter.value)) : list
})

// ── 翻页（0803 用户定：会议/接待/事项会累到几百条）──
// 纯前端分页：四路数据本就一次性拉全量，这里只切显示；等哪天单页签上千条、
// 首屏拉取本身变慢，再让后端出 page/size 接口，这层照样能接
// 一页 6 条（0803 用户定：不用往下翻才看得到翻页条）。为什么不是 7：
// 标题两行的行（如「整理上半年会议、公示、接待和培训档案目录」）比一行的高约 16px，
// 7 条全是两行时页码条会掉到 887px、跌出 844 视口；6 条最差也只到 794，稳在首屏内
const PAGE_SIZE = 6
const page = ref(1)
const totalPages = computed(() => Math.max(1, Math.ceil(items.value.length / PAGE_SIZE)))
const pageItems = computed(() => items.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))
// 数据变少（切页签/筛年份/刷新后条目减少）时把越界页码拉回最后一页，避免翻到空白页
watch(totalPages, (n) => { if (page.value > n) page.value = n })

function goPage(p) {
  const next = Math.min(Math.max(1, p), totalPages.value)
  if (next === page.value) return
  page.value = next
  // 翻页后回到列表顶部：本项目滚动只在 #app-scroll，window 上的滚动 API 全失效
  const el = document.getElementById('app-scroll')
  if (el) el.scrollTo({ top: 0, behavior: 'smooth' })
}

// 跳页：页数多时不必从第 1 页一路点过去（两页时没必要弹，按钮已 disabled）
async function openPageJump() {
  if (totalPages.value <= 2) return
  // picker 变体：当前页打勾、其余不带 ›（› 表示「去别处」，这里是就地选择）
  const itemList = Array.from({ length: totalPages.value }, (_, i) => ({
    label: '第 ' + (i + 1) + ' 页', selected: i + 1 === page.value
  }))
  const r = await showActionSheet({ title: '跳到第几页', variant: 'picker', itemList })
  if (r && r.tapIndex >= 0) goPage(r.tapIndex + 1)
}

async function loadArchive() {
  loading.value = true
  // 四路数据并行拉取，任一失败降级为空数组，互不影响
  const [committee, reception, learning, todos] = await Promise.all([
    api.committeeArchiveList().catch(() => []),
    api.receptionRecords('all').catch(() => []),
    api.learningList('', 'ended').catch(() => []),
    api.committeeTodosOverview().catch(() => [])
  ])
  // 会议：已归档会议纪要（保留公示/归档态；三级色规则下这两态都是灰字）
  const committeeItems = (committee || [])
    .filter(m => m.compliance !== 'invalid')
    .map(m => ({
      id: m.id, kind: 'committee', title: m.title, date: m.meetingDate || '',
      statusText: (m.publish && m.publish.published) ? '已公示' : '已归档',
      metaText: (m.materials ? m.materials.length : 0) + ' 份材料'
    }))
    .sort(byDateDesc)
  // 接待：往期接待记录与已办事项（记录只有一种状态，不写状态标签——spec §11）
  const receptionItems = (reception || [])
    .map(r => ({
      id: r.id, kind: 'reception',
      title: r.visitorName ? (r.visitorName + ' 来访') : (String(r.content || '').split(/[，。；、,.;]/)[0] || '接待记录'),
      date: r.date || '',
      statusText: '',
      metaText: [r.receiver && ('接待人 ' + r.receiver), r.room].filter(Boolean).join(' · ')
    }))
    .sort(byDateDesc)
  // 学习：已完成培训记录（已完成＝灰字）
  const learningItems = (learning || [])
    .map(l => ({
      id: l.id, kind: 'learning', title: l.title, date: l.date || '',
      statusText: '已完成',
      metaText: [l.trainer && ('讲师 ' + l.trainer), l.category === 'internal' ? '内部学习' : '外部培训'].filter(Boolean).join(' · ')
    }))
    .sort(byDateDesc)

  // 已办：会议待办 done + 接待事项 done（0731 设计师稿：待办页只留未办，已办统一在此）
  const doneTodoItems = (todos || [])
    .filter(t => t.status === 'done')
    .map(t => ({
      id: t.id, kind: 'todo-meeting', meetingId: t.meetingId,
      title: t.title, date: t.meetingDate || '',
      statusText: '已完成',
      metaText: t.meetingTitle || ''
    }))
  const doneRecItems = (reception || [])
    .filter(r => r.done && r.visitorName !== '无人来访')
    .map(r => ({
      id: r.id, kind: 'reception',
      title: String(r.content || '').split(/[，。；、,.;]/)[0] || (r.visitorName ? (r.visitorName + ' 来访') : '接待事项'),
      date: r.date || '',
      statusText: '已办结',
      metaText: r.visitorName ? (r.visitorName + ' 反映') : ''
    }))
  const doneItems = [...doneTodoItems, ...doneRecItems].sort(byDateDesc)

  lists.value = { committee: committeeItems, reception: receptionItems, learning: learningItems, done: doneItems }
  counts.value = { committee: committeeItems.length, reception: receptionItems.length, learning: learningItems.length, done: doneItems.length }
  // 数据到位后才知道有几个年份 → 这时才能定默认年份（enter() 里 list 还是空的）
  yearFilter.value = defaultYearFor(tab.value)
  page.value = 1
  loading.value = false
}

function switchTab(t) { tab.value = t; yearFilter.value = defaultYearFor(t); page.value = 1 }
function setYear(y) { yearFilter.value = y; page.value = 1 }

function openDetail(item) {
  // 会议/学习归档详情走 ArchiveDetail；接待有自己的详情页（ArchiveDetail 不含 reception 分支）；
  // 已办的会议待办进待办详情页（0731 用户定：单场待办管理页不再作为跳转目标）
  if (item.kind === 'todo-meeting') {
    window.location.href = '/todo-detail?id=' + item.id + '&meetingId=' + item.meetingId
  } else if (item.kind === 'reception') {
    navigateTo('/pages/reception-detail/reception-detail?id=' + item.id + '&from=archive')
  } else {
    navigateTo('/pages/archive-detail/archive-detail?kind=' + item.kind + '&id=' + item.id)
  }
}

function enter() {
  const activeRole = getStorage('activeRole')
  if (!activeRole) { redirectTo('/pages/login/login'); return }
  const qtab = route.query && route.query.tab
  if (qtab && TABS.some(t => t.key === qtab)) tab.value = qtab
  page.value = 1 // 从详情页返回(onActivated)也回第 1 页，与页签/年份切换一致
  loadArchive()
}

onMounted(enter)
onActivated(enter)
</script>

<style scoped>
/* 档案馆＝中性深灰（0730 设计师定：不属于任何模块，所以不用三色）。
   在本页作用域内把 publish 主题的蓝/资料库旧深青覆盖成中性灰，并给 PublishNav 传中性灰顶栏。 */
.arch-page {
  padding-bottom: 40rpx;
  background: #f2f3f5;
  --pub-blue: #4a5560;
  --pub-blue-2: #5a6472;
  --pub-blue-deep: #3a424e;
  --pub-blue-soft: #eceef1;
  --pub-blue-line: #e2e5ea;
  --pub-nav-grad: linear-gradient(160deg, #55606e 0%, #434d5a 100%);
}

/* 三页签 */
.arch-tabs { display: flex; gap: 14rpx; margin-bottom: 20rpx; }
.arch-tab { flex: 1; text-align: center; font-size: 30rpx; color: #6b7280; background: #fff; border-radius: 18rpx; padding: 20rpx 0; font-weight: 600; box-shadow: 0 6rpx 20rpx rgba(31,41,55,0.05); }
.arch-tab.active { background: #4a5560; color: #fff; }
.arch-tab-count { font-size: 26rpx; opacity: 0.85; margin-left: 6rpx; }

/* 年份筛选胶囊 */
.arch-years { display: flex; flex-wrap: wrap; gap: 12rpx; margin-bottom: 22rpx; }
.arch-year { padding: 8rpx 24rpx; border-radius: 999rpx; background: #fff; color: #6b7280; font-size: 26rpx; border: 2rpx solid #e2e5ea; }
.arch-year.active { background: #4a5560; color: #fff; border-color: #4a5560; }

/* 轻列表：透明底 + 分隔线（spec §5：只需知晓的历史信息，弱于要办的白卡） */
.arch-list { border-top: 2rpx solid #e2e5ea; }
.arch-row { display: flex; align-items: center; gap: 16rpx; min-height: 108rpx; padding: 22rpx 8rpx; border-bottom: 2rpx solid #e2e5ea; cursor: pointer; }
.arch-row:active { background: #ecedf0; }
.arch-info { flex: 1; min-width: 0; }
.arch-title { font-size: 31rpx; font-weight: 650; color: #1f2937; line-height: 1.35; }
.arch-meta { margin-top: 8rpx; font-size: 26rpx; color: #6b7280; display: flex; align-items: center; gap: 8rpx; flex-wrap: wrap; }
.arch-dot { opacity: .5; }
.arch-status { flex-shrink: 0; align-self: center; font-size: 25rpx; font-weight: 500; color: #6b7280; }
.arch-arrow { flex-shrink: 0; font-size: 32rpx; color: #b4bcc7; }

/* 翻页条：两侧按钮 + 中间页码。老人手指粗，按钮给到 92rpx(≈48px) 高、整块可点；
   中间页码在只有两页时不可跳（disabled 只去掉可点感，不变灰——它同时是状态显示） */
.arch-pager { display: flex; align-items: center; gap: 14rpx; margin-top: 26rpx; }
.arch-pager button { font-family: inherit; touch-action: manipulation; user-select: none; -webkit-tap-highlight-color: transparent; }
.ap-btn { flex: 0 0 auto; display: flex; align-items: center; justify-content: center; gap: 10rpx;
  min-width: 176rpx; height: 92rpx; padding: 0 24rpx; box-sizing: border-box;
  border: 2rpx solid #d7dbe2; border-radius: 14rpx; background: #fff; color: #3a424e; font-size: 28rpx; font-weight: 600; }
.ap-btn:active:not(:disabled) { background: #eceef1; }
.ap-btn:disabled { color: #b4bcc7; border-color: #e6e9ed; background: #f7f8f9; }
/* 箭头用 CSS 边框画（项目规范：⌄/⌃ 字符基线偏低、压不准中线） */
.ap-arr { display: inline-block; width: 14rpx; height: 14rpx; border-right: 3rpx solid currentColor; border-bottom: 3rpx solid currentColor; }
.ap-arr.pre { transform: rotate(135deg); position: relative; left: 3rpx; }
.ap-arr.next { transform: rotate(-45deg); position: relative; right: 3rpx; }
.ap-now { flex: 1; min-width: 0; height: 92rpx; padding: 0 8rpx; border: 0; border-radius: 14rpx;
  background: transparent; color: #6b7280; font-size: 27rpx; font-weight: 600; }
.ap-now:active:not(:disabled) { background: #eceef1; }
.ap-now:disabled { color: #6b7280; }

/* 空态 */
.arch-empty { text-align: center; padding: 80rpx 36rpx; background: #fff; border-radius: 20rpx; box-shadow: 0 6rpx 20rpx rgba(31,41,55,0.05); }
.empty-icon { width: 100rpx; height: 100rpx; border-radius: 50%; background: #eceef1; display: flex; align-items: center; justify-content: center; margin: 0 auto 24rpx; font-size: 52rpx; }
.empty-title { display: block; font-size: 34rpx; color: #1f2937; font-weight: 700; margin-bottom: 12rpx; }
.empty-text { display: block; font-size: 28rpx; color: #6b7280; line-height: 1.6; }
</style>
