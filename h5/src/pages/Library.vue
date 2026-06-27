<template>
  <div class="page">
    <PageNav title="历史会议" style="margin: 0 -3.2vw 0" />
    <!-- 顶部分类切换 -->
    <div class="tabs">
      <div class="tab" :class="{ active: tab === 'committee' }" @click="switchTab('committee')">业委会会议 <span class="tab-count">{{ counts.committee }}</span></div>
      <div class="tab" :class="{ active: tab === 'learning' }" @click="switchTab('learning')">学习培训 <span class="tab-count">{{ counts.learning }}</span></div>
    </div>

    <div class="list">
      <template v-if="items.length">
        <div class="lib-card" v-for="item in items" :key="item.id" @click="openDetail(item)">
          <div class="lc-main">
            <span class="lc-title">{{ item.title }}</span>
            <div class="lc-meta">
              <span v-if="item.date">{{ item.date }}</span>
              <span class="lc-dot" v-if="item.date && item.metaText">·</span>
              <span v-if="item.metaText">{{ item.metaText }}</span>
            </div>
          </div>
          <div class="lc-right">
            <span class="lc-status">{{ item.statusText }}</span>
            <span class="lc-arrow">›</span>
          </div>
        </div>
      </template>
      <div v-else-if="!loading" class="empty-state">
        <span class="empty-emoji">📚</span>
        <span>该分类暂无归档</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onActivated } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import { redirectTo } from '@/utils/navigate'
import { navigateTo } from '@/utils/navigate'
import { getStorage } from '@/utils/storage'

function byDateDesc(a, b) { return (b.date || '').localeCompare(a.date || '') }

const tab = ref('committee')
const counts = ref({ committee: 0, learning: 0 })
const lists = ref({ committee: [], learning: [] })
const items = ref([])
const loading = ref(true)

async function loadArchive() {
  loading.value = true

  const committee = await api.committeeArchiveList().catch(() => [])
  const learning = await api.learningList('', 'ended').catch(() => [])

  const committeeItems = (committee || [])
    .filter(m => m.compliance !== 'invalid')
    .map(m => ({
      id: m.id, kind: 'committee', title: m.title, date: m.meetingDate || '',
      statusText: (m.publish && m.publish.published) ? '已公示' : '已归档',
      metaText: (m.materials ? m.materials.length : 0) + ' 份材料'
    }))
    .sort(byDateDesc)

  const learningItems = (learning || []).map(m => ({
    id: m.id, kind: 'learning', title: m.title, date: m.date || '',
    statusText: '已完成',
    metaText: (m.trainer ? '讲师 ' + m.trainer : '学习活动')
  })).sort(byDateDesc)

  const ls = { committee: committeeItems, learning: learningItems }
  lists.value = ls
  counts.value = { committee: committeeItems.length, learning: learningItems.length }
  items.value = ls[tab.value]
  loading.value = false
}

function switchTab(t) {
  tab.value = t
  items.value = lists.value[t]
}

function openDetail(item) {
  navigateTo('/pages/archive-detail/archive-detail?kind=' + item.kind + '&id=' + item.id)
}

function enter() {
  const activeRole = getStorage('activeRole')
  if (!activeRole) {
    redirectTo('/pages/login/login')
    return
  }
  loadArchive()
}

onMounted(enter)
onActivated(enter)
</script>

<style scoped>
.page { min-height: 100vh; background: #f4f5f7; padding: 0 24rpx 40rpx; box-sizing: border-box; }

.tabs { display: flex; gap: 14rpx; margin: 20rpx 0 24rpx; }
.tab { flex: 1; text-align: center; font-size: 30rpx; color: #6b7785; background: #fff; border-radius: 18rpx; padding: 18rpx 0; font-weight: 500; }
.tab.active { background: #FFF3DC; color: #C77800; font-weight: 700; }
.tab-count { font-size: 28rpx; color: #777; }
.tab.active .tab-count { color: #C77800; }

.lib-card {
  background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx;
  margin-bottom: 16rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06);
  display: flex; align-items: center; justify-content: space-between;
}
.lib-card:active { background: #f7f9fb; }
.lc-main { flex: 1; min-width: 0; }
.lc-title { font-size: 36rpx; font-weight: 700; color: #1f2329; display: block; }
.lc-meta { font-size: 28rpx; color: #666; margin-top: 8rpx; display: flex; gap: 10rpx; }
.lc-dot { color: #666; }
.lc-right { display: flex; align-items: center; gap: 12rpx; margin-left: 16rpx; }
.lc-status { font-size: 28rpx; font-weight: 600; color: #27AE60; background: #E8F7EE; border-radius: 10rpx; padding: 4rpx 16rpx; white-space: nowrap; }
.lc-arrow { font-size: 40rpx; color: #666; }

.empty-state {
  display: flex; flex-direction: column; align-items: center;
  color: #777; font-size: 32rpx; padding-top: 120rpx;
}
.empty-emoji { font-size: 80rpx; margin-bottom: 20rpx; }
</style>
