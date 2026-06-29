<template>
  <div class="pub-page lib-page">
    <PublishNav title="历史会议" />

    <div class="pub-wrap">
      <!-- 顶部分类切换（蓝色） -->
      <div class="lib-tabs">
        <div class="lib-tab" :class="{ active: tab === 'committee' }" @click="switchTab('committee')">
          业委会会议 <span class="lib-tab-count">{{ counts.committee }}</span>
        </div>
        <div class="lib-tab" :class="{ active: tab === 'learning' }" @click="switchTab('learning')">
          学习培训 <span class="lib-tab-count">{{ counts.learning }}</span>
        </div>
      </div>

      <template v-if="items.length">
        <div class="pub-card lib-card" v-for="item in items" :key="item.id" @click="openDetail(item)">
          <span class="pub-tag">{{ item.kind === 'committee' ? '业委会会议' : '学习培训' }}</span>
          <!-- 状态徽标位：现示归档/公示状态；未来上社区链后此处换「已上链/存证中」 -->
          <span class="pub-badge" :class="badgeClass(item.statusText)">{{ item.statusText }}</span>

          <span class="pub-title">{{ item.title }}</span>

          <div class="pub-meta">
            <span class="mi" v-if="item.date">日期：<b>{{ item.date }}</b></span>
            <span class="mi" v-if="item.metaText">{{ item.metaText }}</span>
          </div>
        </div>
      </template>

      <div v-else-if="!loading" class="pub-card lib-empty">
        <div class="empty-icon">📚</div>
        <span class="empty-title">该分类暂无归档</span>
        <span class="empty-text">已结束并归档的{{ tab === 'committee' ? '会议' : '学习活动' }}会显示在这里。</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onActivated } from 'vue'
import api from '@/api'
import PublishNav from '@/components/PublishNav.vue'
import { redirectTo } from '@/utils/navigate'
import { navigateTo } from '@/utils/navigate'
import { getStorage } from '@/utils/storage'

function byDateDesc(a, b) { return (b.date || '').localeCompare(a.date || '') }

const tab = ref('committee')
const counts = ref({ committee: 0, learning: 0 })
const lists = ref({ committee: [], learning: [] })
const items = ref([])
const loading = ref(true)

// 状态 → 徽标配色：已公示=蓝、已完成=绿、已归档=中性
function badgeClass(status) {
  if (status === '已公示') return 'is-pub'
  if (status === '已完成') return 'is-done'
  return 'is-wait'
}

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
.lib-page { padding-bottom: 40rpx; }

/* 蓝色分类切换 */
.lib-tabs { display: flex; gap: 14rpx; margin-bottom: 24rpx; }
.lib-tab {
  flex: 1; text-align: center; font-size: 30rpx; color: var(--pub-sub);
  background: #fff; border-radius: 18rpx; padding: 20rpx 0; font-weight: 600;
  box-shadow: 0 6rpx 20rpx rgba(41, 63, 102, 0.06);
}
.lib-tab.active { background: var(--pub-blue); color: #fff; }
.lib-tab-count { font-size: 28rpx; opacity: 0.85; }

/* 卡片可点击反馈 */
.lib-card { cursor: pointer; }
.lib-card:active { background: var(--pub-blue-soft); }

/* 学习「已完成」绿色徽标（在蓝色体系里作正向区分） */
.pub-badge.is-done { background: var(--pub-green-soft); color: var(--pub-green); }

/* 空态蓝卡 */
.lib-empty { text-align: center; padding: 72rpx 36rpx; cursor: default; }
.lib-empty:active { background: #fff; }
.empty-icon {
  width: 100rpx; height: 100rpx; border-radius: 50%; background: var(--pub-blue-soft);
  display: flex; align-items: center; justify-content: center; margin: 0 auto 24rpx; font-size: 52rpx;
}
.empty-title { display: block; font-size: 38rpx; color: var(--pub-ink); font-weight: 700; margin-bottom: 14rpx; }
.empty-text { display: block; font-size: 30rpx; color: var(--pub-sub); line-height: 1.7; }
</style>
