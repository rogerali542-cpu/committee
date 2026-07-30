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
        <div class="arch-year" :class="{ active: yearFilter === '' }" @click="yearFilter = ''">全部</div>
        <div v-for="y in years" :key="y" class="arch-year" :class="{ active: yearFilter === y }" @click="yearFilter = y">{{ y }}年</div>
      </div>

      <!-- 纯轻列表（spec §5：只需知晓的历史信息＝透明底+分隔线，非白卡） -->
      <div v-if="items.length" class="arch-list">
        <div v-for="item in items" :key="item.kind + '-' + item.id" class="arch-row" @click="openDetail(item)">
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

      <div v-else-if="!loading" class="arch-empty">
        <div class="empty-icon">📚</div>
        <span class="empty-title">{{ curTab.label }}暂无归档</span>
        <span class="empty-text">{{ curTab.emptyHint }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import PublishNav from '@/components/PublishNav.vue'
import { navigateTo, redirectTo } from '@/utils/navigate'
import { getStorage } from '@/utils/storage'

const route = useRoute()

const TABS = [
  { key: 'committee', label: '会议', emptyHint: '已归档的会议纪要会显示在这里。' },
  { key: 'reception', label: '接待', emptyHint: '往期接待记录与已办事项会显示在这里。' },
  { key: 'learning', label: '学习', emptyHint: '已完成的培训记录会显示在这里。' },
  // 已办页签（0731 设计师稿）：业委会待办页只留未办，已办结的待办（会议+接待）统一收进这里
  { key: 'done', label: '已办', emptyHint: '已办结的待办事项会显示在这里。' }
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
const items = computed(() => {
  const list = curList.value
  return yearFilter.value ? list.filter(it => String(it.date || '').startsWith(yearFilter.value)) : list
})

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
  loading.value = false
}

function switchTab(t) { tab.value = t; yearFilter.value = '' }

function openDetail(item) {
  // 会议/学习归档详情走 ArchiveDetail；接待有自己的详情页（ArchiveDetail 不含 reception 分支）；
  // 已办的会议待办回到该场会议的待办页（整页跳转最稳）
  if (item.kind === 'todo-meeting') {
    window.location.href = '/minutes-todos?meetingId=' + item.meetingId
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

/* 空态 */
.arch-empty { text-align: center; padding: 80rpx 36rpx; background: #fff; border-radius: 20rpx; box-shadow: 0 6rpx 20rpx rgba(31,41,55,0.05); }
.empty-icon { width: 100rpx; height: 100rpx; border-radius: 50%; background: #eceef1; display: flex; align-items: center; justify-content: center; margin: 0 auto 24rpx; font-size: 52rpx; }
.empty-title { display: block; font-size: 34rpx; color: #1f2937; font-weight: 700; margin-bottom: 12rpx; }
.empty-text { display: block; font-size: 28rpx; color: #6b7280; line-height: 1.6; }
</style>
