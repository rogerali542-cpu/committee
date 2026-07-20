<template>
  <div class="pub-page lib-page">
    <PublishNav title="历史记录" />

    <div class="pub-wrap">
      <!-- 顶部分类切换（蓝色） -->
      <div class="lib-tabs">
        <div class="lib-tab" :class="{ active: tab === 'committee' }" @click="switchTab('committee')">
          业委会会议 <span class="lib-tab-count">{{ counts.committee }}</span>
        </div>
      </div>

      <template v-if="items.length">
        <div class="pub-card lib-card" v-for="item in items" :key="item.id" @click="openDetail(item)">
          <span class="pub-tag">业委会会议</span>
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
        <span class="empty-text">已结束并归档的会议会显示在这里。</span>
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
const counts = ref({ committee: 0 })
const lists = ref({ committee: [] })
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
  const committeeItems = (committee || [])
    .filter(m => m.compliance !== 'invalid')
    .map(m => ({
      id: m.id, kind: 'committee', title: m.title, date: m.meetingDate || '',
      statusText: (m.publish && m.publish.published) ? '已公示' : '已归档',
      metaText: (m.materials ? m.materials.length : 0) + ' 份材料'
    }))
    .sort(byDateDesc)

  const ls = { committee: committeeItems }
  lists.value = ls
  counts.value = { committee: committeeItems.length }
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
/* 本页统一为深橙风格：仅在 .lib-page 作用域内把共享的蓝色 --pub-* 覆盖成橙，
   并给 PublishNav 传橙色渐变(--pub-nav-grad)。不影响其它 publish 页（它们不设这些变量→维持蓝）。 */
.lib-page {
  padding-bottom: 40rpx;
  --pub-blue: #A85800;
  --pub-blue-2: #C76A00;
  --pub-blue-deep: #8F4A06;
  --pub-blue-soft: #FFF3E0;
  --pub-blue-line: #F0E1CE;
  --pub-nav-grad: linear-gradient(160deg, #C76A00 0%, #A85800 100%);
}

/* 分类切换（深橙） */
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
