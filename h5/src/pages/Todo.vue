<template>
  <div class="page">
    <span class="intro">只显示此刻需要你操作的事项，按会议开始时间排序。</span>

    <template v-if="pending.length || active.length">
      <!-- 待开始：我还没动手的 -->
      <div v-if="pending.length" class="todo-section">
        <div class="section-head"><span class="section-title">待开始</span><span class="section-count">{{ pending.length }}</span></div>
        <div class="todo-card" v-for="item in pending" :key="item.key" @click="openItem(item)">
          <div class="tc-head">
            <span class="tc-tag">{{ item.tag }}</span>
            <span class="tc-status">{{ item.statusText }}</span>
          </div>
          <span class="tc-title">{{ item.title }}</span>
          <div class="tc-meta"><span v-if="item.subText">{{ item.subText }}</span></div>
        </div>
      </div>

      <!-- 进行中：已经在处理但没完成的 -->
      <div v-if="active.length" class="todo-section">
        <div class="section-head"><span class="section-title">进行中</span><span class="section-count">{{ active.length }}</span></div>
        <div class="todo-card" v-for="item in active" :key="item.key" @click="openItem(item)">
          <div class="tc-head">
            <span class="tc-tag">{{ item.tag }}</span>
            <span class="tc-status active">{{ item.statusText }}</span>
          </div>
          <span class="tc-title">{{ item.title }}</span>
          <div class="tc-meta"><span v-if="item.subText">{{ item.subText }}</span></div>
        </div>
      </div>
    </template>

    <div v-else-if="!loading" class="empty-state">
      <span class="empty-emoji">🎉</span>
      <span>暂无待办，所有事项都已处理</span>
    </div>
  </div>
</template>

<script setup>
// 我要办理 —— 只聚合"此刻需要我操作"的事项，分两类：待开始 / 进行中，按开始时间排序
// 待开始 = 我还没动手的（如未确认参会、待派单）；进行中 = 我已经在处理但没完成的
import { ref, onMounted, onActivated } from 'vue'
import api from '@/api'
import perm from '@/utils/perm'
import { getStorage } from '@/utils/storage'
import { redirectTo, navigateTo } from '@/utils/navigate'

const pending = ref([])   // 待开始
const active = ref([])    // 进行中
const total = ref(0)
const loading = ref(true)

function sortByStart(a, b) {
  return (a.sortKey || '').localeCompare(b.sortKey || '')
}

async function loadTodos() {
  loading.value = true
  const all = []
  const activeRole = getStorage('activeRole')
  const role = activeRole ? activeRole.role : ''

  // 1) 业委会会议：仅参会成员（委员/主任/副主任）才有待办
  //    - 准备阶段：通知已送达我但我尚未查看 → 「查看会议通知」（查看即完成）
  //    - 进行中：我还没办完（确认参会/表决）
  if (perm.can('committee.sign_in')) {
    try {
      const list = await api.committeeList(null)
      ;(list || []).forEach(m => {
        if (m.stage === 'preparing' && m.myNoticeUnread) {
          all.push({
            key: 'cn-' + m.id, id: m.id, kind: 'committee', tag: '业委会会议',
            title: m.title,
            statusText: '会议通知待查看',
            subText: m.meetingDate ? (m.meetingDate + (m.meetingTime ? ' ' + m.meetingTime : '')) : '',
            group: 'pending',
            sortKey: (m.meetingDate || '') + ' ' + (m.meetingTime || '')
          })
        } else if (m.stage === 'ongoing' && m.progress >= 0 && m.progress < 100) {
          all.push({
            key: 'c-' + m.id, id: m.id, kind: 'committee', tag: '业委会会议',
            title: m.title,
            statusText: m.progressLabel || '待处理',
            subText: m.meetingDate ? (m.meetingDate + (m.meetingTime ? ' ' + m.meetingTime : '')) : '',
            group: m.progress === 0 ? 'pending' : 'active',
            sortKey: (m.meetingDate || '') + ' ' + (m.meetingTime || '')
          })
        }
      })
    } catch (e) { /* offline ok */ }
  }

  // 2) 接待事项（业委会，有 reception.manage）—— 都是待我处理且未开始，归「待开始」
  if (perm.can('reception.manage')) {
    try {
      // 0716 接待重做：propertyStatus 状态机与 fedOwner 已随内部派单流下线。
      // 现在 'pending' 就是「没填处理结果」，后端已按新口径过滤，这里不用再分状态。
      // 派没派工单不影响待办：派单 ≠ 办结，工单派出去了这件事仍挂在委员名下。
      const recs = await api.receptionRecords('pending')
      ;(recs || []).forEach(r => {
        all.push({
          key: 'r-' + r.id, id: r.id, kind: 'reception', tag: '接待事项',
          title: r.content || (r.visitorName ? r.visitorName + ' 的诉求' : '接待诉求'),
          statusText: r.ticketPushed ? '已派工单·待填处理结果' : '待处理',
          subText: r.date || '',
          group: 'pending',
          sortKey: (r.date || '') + ' ' + (r.time || '')
        })
      })
    } catch (e) { /* offline ok */ }
  }

  // 3) 物业工单段已删（0716 方案 A）：内部派单流下线，物业不再登录本 App 干活，
  //    改为在外部工单系统里处理业委会派过去的单。原实现见 commit 6745a12。

  pending.value = all.filter(i => i.group === 'pending').sort(sortByStart)
  active.value = all.filter(i => i.group === 'active').sort(sortByStart)
  total.value = all.length
  loading.value = false
}

function openItem(item) {
  const { id, kind } = item
  if (kind === 'reception') {
    // 0716：接待列表页已删，直接进这一条的处理页；哨兵 .recep-detail 在目标页根上
    navigateTo('/pages/reception-detail/reception-detail?id=' + id)
    setTimeout(() => { if (!document.querySelector('.recep-detail')) window.location.href = '/reception-detail?id=' + id }, 300)
  } else {
    navigateTo('/pages/committee-detail/committee-detail?id=' + id + '&from=todo')
  }
}

function init() {
  const activeRole = getStorage('activeRole')
  if (!activeRole) {
    redirectTo('/pages/login/login')
    return
  }
  loadTodos()
}

onMounted(init)
onActivated(init)
</script>

<style scoped>
.page { min-height: 100vh; background: #f4f5f7; padding: 24rpx; box-sizing: border-box; }
.intro { font-size: 32rpx; color: #666; display: block; margin-bottom: 24rpx; text-align: center; }

.todo-section { margin-bottom: 32rpx; }
.section-head { display: flex; align-items: center; gap: 14rpx; margin: 0 4rpx 16rpx; }
.section-title { font-size: 32rpx; font-weight: 700; color: #1f2329; }
.section-count { font-size: 32rpx; color: #C77800; background: #FFF3DC; border-radius: 14rpx; padding: 4rpx 16rpx; font-weight: 600; }

.todo-card { background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.todo-card:active { background: #fafbfc; }
.tc-head { display: flex; justify-content: space-between; align-items: center; }
.tc-tag { font-size: 32rpx; background: #FFF3DC; color: #C77800; padding: 4rpx 16rpx; border-radius: 10rpx; font-weight: 600; }
.tc-status { font-size: 32rpx; color: #E67E22; font-weight: 600; }
.tc-status.active { color: #2E86DE; }
.tc-title { font-size: 36rpx; font-weight: 700; display: block; margin: 14rpx 0 10rpx; color: #1f2329; line-height: 1.5; }
.tc-meta { font-size: 32rpx; color: #666; display: flex; flex-direction: column; gap: 4rpx; }
.tc-sub { color: #777; }

.empty-state { display: flex; flex-direction: column; align-items: center; color: #777; font-size: 32rpx; padding-top: 120rpx; }
.empty-emoji { font-size: 80rpx; margin-bottom: 20rpx; }
</style>
