<template>
  <div class="page reception-records">
    <PageNav title="接待记录">
      <template #left>
        <button class="nav-back" type="button" aria-label="返回接待中心" @click="backToCenter">‹</button>
      </template>
    </PageNav>

    <main class="records-body">
      <section class="summary-grid">
        <div class="summary-card">
          <strong>{{ annualVisitorCount }}</strong><span>全年接待人次</span>
        </div>
        <div class="summary-card handled">
          <strong>{{ annualHandledCount }}</strong><span>已处理</span>
        </div>
        <div class="summary-card pending">
          <strong>{{ annualPendingCount }}</strong><span>待处理</span>
        </div>
      </section>

      <section class="filter-card">
        <div class="filter-title">查看月份</div>
        <div class="select-row">
          <select v-model="yearFilter">
            <option v-for="year in years" :key="year" :value="String(year)">{{ year }}年</option>
          </select>
          <select v-model="monthFilter">
            <option v-for="m in 12" :key="m" :value="String(m)">{{ m }}月</option>
          </select>
        </div>
      </section>

      <div v-if="loading" class="empty">正在加载接待记录…</div>
      <div v-else-if="loadErr" class="empty error">{{ loadErr }}</div>
      <div v-else-if="!monthGroups.length" class="empty">没有符合条件的接待记录</div>

      <section v-for="month in monthGroups" :key="month.key" class="month-section">
        <div class="month-head">
          <strong>{{ month.label }}</strong>
          <span>{{ month.days.length }}次接待 · {{ month.itemCount }}项记录</span>
        </div>

        <article v-for="day in month.days" :key="day.date" class="day-card">
          <button class="day-head" type="button" @click="toggleDay(day.date)">
            <span class="date-block">
              <strong>{{ formatDay(day.date) }}</strong>
              <em>{{ weekday(day.date) }}<template v-if="day.time"> · {{ String(day.time).slice(0, 5) }}</template></em>
            </span>
            <span class="day-summary">
              <span class="day-summary-top">
                <strong>{{ day.noVisit ? '无人来访' : day.visitorCount + '人来访' }}</strong>
                <em :class="day.status">{{ statusLabel(day.status) }}</em>
              </span>
              <!-- 收起态摘要（0723 用户定）：不展开也能看出是谁、什么事 -->
              <span v-if="day.summaryText" class="day-summary-sub">{{ day.summaryText }}</span>
            </span>
            <span class="fold-text">{{ openDays.has(day.date) ? '收起' : '展开' }}</span>
          </button>

          <div v-if="openDays.has(day.date)" class="day-items">
            <div v-for="record in day.records" :key="record.id" class="record-row" @click="goDetail(record)">
              <div class="record-main">
                <div class="record-title">
                  <strong>{{ record.visitorName === '无人来访' ? '无人来访' : (record.visitorName || '来访业主') }}</strong>
                  <span v-if="record.room">{{ record.room }}</span>
                  <i :class="recordStatus(record)">{{ statusLabel(recordStatus(record)) }}</i>
                </div>
                <p>{{ record.visitorName === '无人来访' ? '本次接待无业主来访，已完成留档' : (record.content || '无诉求内容') }}</p>
                <small><template v-if="record.visitorName !== '无人来访'">{{ record.categoryLabel || categoryLabel(record.category) }}</template><template v-if="record.receiver"><template v-if="record.visitorName !== '无人来访'"> · </template>接待人：{{ record.receiver }}</template></small>
              </div>
              <span class="record-go">{{ record.visitorName === '无人来访' ? '查看记录' : '查看' }}</span>
            </div>
          </div>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import { navigateTo } from '@/utils/navigate'

const records = ref([])
const loading = ref(true)
const loadErr = ref('')
const yearFilter = ref(String(new Date().getFullYear()))
const monthFilter = ref(String(new Date().getMonth() + 1))
const openDays = reactive(new Set())

function recordStatus(record) {
  if (record.done) return 'done'
  if (record.propertyTransferred || record.ticketPushed) return 'doing'
  return 'pending'
}
// 状态词全模块统一（0723 用户定）：待处理 / 处理中 / 已办结
function statusLabel(status) {
  return status === 'done' ? '已办结' : (status === 'doing' ? '处理中' : '待处理')
}
function categoryLabel(category) {
  return { property: '物业类', public_affairs: '公共事务', neighbor: '邻里纠纷', other: '其他' }[category] || '其他'
}

const years = computed(() => Array.from(new Set(records.value.map(r => String(r.date || '').slice(0, 4)).filter(Boolean)))
  .sort((a, b) => Number(b) - Number(a)))

const filteredRecords = computed(() => {
  return records.value.filter(record => {
    const date = String(record.date || '')
    return date.slice(0, 4) === yearFilter.value &&
      String(Number(date.slice(5, 7))) === monthFilter.value
  })
})
// 顶部三项按所选年份统计；无人来访只留档，不计入接待人次。
const annualVisitorRecords = computed(() => records.value.filter(r =>
  String(r.date || '').slice(0, 4) === yearFilter.value && r.visitorName !== '无人来访'))
const annualVisitorCount = computed(() => annualVisitorRecords.value.length)
const annualHandledCount = computed(() => annualVisitorRecords.value.filter(r => recordStatus(r) !== 'pending').length)
const annualPendingCount = computed(() => annualVisitorRecords.value.filter(r => recordStatus(r) === 'pending').length)

const monthGroups = computed(() => {
  const months = new Map()
  filteredRecords.value.forEach(record => {
    const date = String(record.date || '')
    const monthKey = date.slice(0, 7) || 'unknown'
    if (!months.has(monthKey)) months.set(monthKey, new Map())
    const days = months.get(monthKey)
    if (!days.has(date)) days.set(date, [])
    days.get(date).push(record)
  })
  return Array.from(months.entries()).sort(([a], [b]) => b.localeCompare(a)).map(([key, days]) => {
    const dayRows = Array.from(days.entries()).sort(([a], [b]) => b.localeCompare(a)).map(([date, dayRecords]) => {
      const visitorRecords = dayRecords.filter(r => r.visitorName !== '无人来访')
      const statuses = visitorRecords.length ? visitorRecords.map(recordStatus) : ['done']
      const status = statuses.every(s => s === 'done') ? 'done'
        : (statuses.every(s => s !== 'pending') ? 'doing' : 'pending')
      // 收起态摘要：单条=「姓名：事由」，多条=姓名列表——不展开也知道是谁、什么事
      let summaryText = ''
      if (visitorRecords.length === 1) {
        const r0 = visitorRecords[0]
        const brief = String(r0.content || '').slice(0, 14)
        summaryText = (r0.visitorName || '来访业主') + (brief ? '：' + brief + (String(r0.content || '').length > 14 ? '…' : '') : '')
      } else if (visitorRecords.length > 1) {
        const names = visitorRecords.map(r => r.visitorName).filter(Boolean)
        summaryText = names.slice(0, 3).join('、') + (names.length > 3 ? ' 等' : '')
      }
      return {
        date,
        time: dayRecords.map(r => r.time).filter(Boolean).sort().reverse()[0] || '',
        // 无人来访同样是正式留档记录，展开后按普通事项卡片展示；只是不计入 visitorCount。
        records: dayRecords,
        noVisit: visitorRecords.length === 0,
        visitorCount: visitorRecords.length,
        summaryText,
        status
      }
    })
    const [year, month] = key.split('-')
    return {
      key,
      label: key === 'unknown' ? '日期未填写' : year + '年' + Number(month) + '月',
      days: dayRows,
      itemCount: dayRows.reduce((sum, day) => sum + Math.max(day.records.length, 1), 0)
    }
  })
})

function toggleDay(date) {
  if (openDays.has(date)) openDays.delete(date)
  else openDays.add(date)
}
function formatDay(date) {
  const parts = String(date || '').split('-')
  return parts.length === 3 ? Number(parts[1]) + '月' + Number(parts[2]) + '日' : date
}
function weekday(date) {
  const d = new Date(date + 'T00:00:00')
  return Number.isNaN(d.getTime()) ? '' : ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
}
function goDetail(record) {
  navigateTo('/pages/reception-detail/reception-detail?id=' + record.id + '&from=records')
  setTimeout(() => {
    if (!document.querySelector('.recep-detail')) window.location.href = '/reception-detail?id=' + record.id + '&from=records'
  }, 300)
}
function backToCenter() {
  window.location.replace('/reception-center')
}

onMounted(async () => {
  try {
    records.value = (await api.receptionRecords('all')) || []
    const firstDate = records.value.map(r => r.date).filter(Boolean).sort().reverse()[0]
    if (firstDate) {
      yearFilter.value = firstDate.slice(0, 4)
      monthFilter.value = String(Number(firstDate.slice(5, 7)))
      openDays.add(firstDate)
    }
  } catch (e) {
    loadErr.value = (e && e.message) || '接待记录加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page { min-height: 100vh; background: var(--c-bg-page); }
.nav-back { width: 96rpx; height: 124rpx; display: flex; align-items: center; justify-content: center;
  padding: 0; border: 0; background: transparent; color: #fff; font-size: 66rpx; font-weight: 700; }
.records-body { padding: 22rpx 24rpx 70rpx; }
.summary-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16rpx; }
.summary-card { min-width: 0; padding: 20rpx 8rpx; border: 2rpx solid #E6EAEC; border-radius: 18rpx;
  background: #fff; color: var(--c-text-mid); }
.summary-card strong { display: block; font-size: 40rpx; line-height: 1.2; color: var(--c-text-strong); }
.summary-card span { display: block; margin-top: 7rpx; font-size: 27rpx; }
.summary-card.pending strong { color: #B26A00; }
.summary-card.handled strong { color: #278653; }
.filter-card { margin-top: 20rpx; padding: 22rpx; border: 2rpx solid #E7EBED; border-radius: 20rpx; background: #fff; }
.filter-title { margin-bottom: 14rpx; font-size: 28rpx; font-weight: 600; color: var(--c-text-mid); }
.select-row { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14rpx; }
.select-row select { min-width: 0; height: 68rpx; padding: 0 8rpx; border: 2rpx solid #E2E7E9;
  border-radius: 12rpx; background: #F8FAFA; color: var(--c-text-strong); font-size: 28rpx; }
.month-section { margin-top: 28rpx; }
.month-head { display: flex; align-items: baseline; justify-content: space-between; padding: 0 4rpx 12rpx; }
.month-head strong { font-size: 34rpx; color: var(--c-text-strong); }
.month-head span { font-size: 26rpx; color: var(--c-text-weak); }
.day-card { margin-bottom: 16rpx; border: 2rpx solid #E5EAEC; border-radius: 20rpx; background: #fff; overflow: hidden; }
.day-head { display: flex; align-items: center; gap: 16rpx; width: 100%; padding: 22rpx;
  border: 0; background: #fff; text-align: left; color: inherit; }
.date-block { flex-shrink: 0; display: flex; flex-direction: column; gap: 3rpx; }
.date-block strong { font-size: 30rpx; color: var(--c-text-strong); }
.date-block em { font-size: 26rpx; color: var(--c-text-weak); font-style: normal; }
.day-summary { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6rpx; }
.day-summary-top { display: flex; align-items: center; gap: 10rpx; }
.day-summary-top > strong { font-size: 28rpx; color: var(--c-text-mid); font-weight: 500; }
.day-summary-sub { font-size: 26rpx; color: var(--c-text-mid); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.day-summary em, .record-title i { padding: 5rpx 14rpx; border-radius: 999rpx; font-size: 25rpx; font-style: normal; font-weight: 600; flex-shrink: 0; }
.day-summary .pending, .record-title .pending { color: #9A5A13; background: #FFF1D8; }
.day-summary .doing, .record-title .doing { color: #0F766E; background: #E7F6F3; }
.day-summary .done, .record-title .done { color: #287653; background: #E8F5EE; }
.fold-text { flex-shrink: 0; color: var(--c-primary-dark); font-size: 28rpx; }
.day-items { border-top: 2rpx solid #EDF0F2; padding: 0 22rpx; }
.record-row { display: flex; align-items: center; gap: 16rpx; padding: 20rpx 0; border-top: 2rpx solid #F0F2F3; }
.record-row:first-child { border-top: 0; }
.record-main { flex: 1; min-width: 0; }
.record-title { display: flex; align-items: center; gap: 9rpx; }
.record-title strong { font-size: 30rpx; color: var(--c-text-strong); }
.record-title span { font-size: 26rpx; color: var(--c-text-weak); }
/* 诉求正文两行截断（原单行截断老人看不出是什么事） */
.record-main p { margin: 8rpx 0 5rpx; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  font-size: 29rpx; line-height: 1.5; color: var(--c-text-mid); }
.record-main small { font-size: 26rpx; color: var(--c-text-weak); }
.record-go { flex-shrink: 0; color: var(--c-primary-dark); font-size: 28rpx; }
.empty { margin-top: 32rpx; padding: 80rpx 20rpx; border-radius: 20rpx; background: #fff;
  text-align: center; color: var(--c-text-weak); font-size: 28rpx; }
.empty.error { color: #A33B31; }
</style>
