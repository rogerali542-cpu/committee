<template>
  <PageNav title="物业看板" />
  <div class="page">
    <span class="intro">小区物业相关问题的处理进展，向全体业主公开。</span>

    <div class="filter-tabs">
      <div class="f-tab" :class="{ active: filter === 'all' }" @click="switchFilter('all')">全部 <span class="f-count">{{ counts.all }}</span></div>
      <div class="f-tab" :class="{ active: filter === 'processing' }" @click="switchFilter('processing')">处理中 <span class="f-count">{{ counts.processing }}</span></div>
      <div class="f-tab" :class="{ active: filter === 'done' }" @click="switchFilter('done')">已处理 <span class="f-count">{{ counts.done }}</span></div>
    </div>

    <div class="board-list">
      <template v-if="items.length">
        <div class="board-card" v-for="item in items" :key="item.id">
          <div class="bc-head">
            <span class="bc-tag">物业事项</span>
            <span class="status-pill" :class="item.statusKey">{{ item.statusLabel }}</span>
          </div>
          <div class="bc-content">{{ item.content }}</div>
          <div class="bc-meta"><span>反映日期 {{ item.date }}</span></div>
          <div class="bc-reply" v-if="item.propertyReply">
            <span class="bc-reply-label">物业处理结果：</span>
            <span>{{ item.propertyReply }}</span>
            <span v-if="item.propertyRepliedAt" class="bc-reply-time">{{ item.propertyRepliedAt }}</span>
          </div>
        </div>
      </template>
      <div v-else-if="!loading" class="empty-state">
        <span class="empty-emoji">📢</span>
        <span>暂无物业事项</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { onMounted, onActivated } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'

const filter = ref('all') // all / processing / done
const counts = ref({ all: 0, pending: 0, processing: 0, done: 0 })
const items = ref([])
const loading = ref(true)

async function loadBoard() {
  loading.value = true
  try {
    const all = await api.receptionPropertyPublic()
    counts.value = {
      all: all.length,
      pending: all.filter(r => r.statusKey === 'pending').length,
      processing: all.filter(r => r.statusKey === 'processing').length,
      done: all.filter(r => r.statusKey === 'done').length
    }
    const f = filter.value
    items.value = f === 'all' ? all : all.filter(r => r.statusKey === f)
    loading.value = false
  } catch (e) {
    loading.value = false
  }
}

function switchFilter(f) {
  filter.value = f
  loadBoard()
}

let mounted = false
onMounted(() => {
  mounted = true
  loadBoard()
})
onActivated(() => {
  if (mounted) loadBoard()
})
</script>

<style scoped>
.page { min-height: 100vh; background: #f4f5f7; padding: 24rpx 24rpx 40rpx; box-sizing: border-box; }
.intro { font-size: 28rpx; color: #666; display: block; margin-bottom: 24rpx; text-align: center; line-height: 1.6; padding: 0 20rpx; }

.filter-tabs { display: flex; gap: 14rpx; margin-bottom: 24rpx; }
.f-tab { flex: 1; text-align: center; font-size: 30rpx; color: #6b7785; background: #fff; border-radius: 18rpx; padding: 18rpx 0; font-weight: 500; }
.f-tab.active { background: #FFF3DC; color: #C77800; font-weight: 700; }
.f-count { font-size: 28rpx; color: #777; }
.f-tab.active .f-count { color: #C77800; }

.board-card { background: #fff; border-radius: 24rpx; padding: 28rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.bc-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14rpx; }
.bc-tag { font-size: 28rpx; font-weight: 600; background: #FFF3DC; color: #C77800; padding: 4rpx 16rpx; border-radius: 10rpx; }
.status-pill { font-size: 28rpx; font-weight: 600; padding: 4rpx 16rpx; border-radius: 12rpx; }
.status-pill.pending { background: #FDECEA; color: #E74C3C; }
.status-pill.processing { background: #FFF3DC; color: #E67E22; }
.status-pill.done { background: #E8F7EE; color: #27AE60; }
.bc-content { font-size: 32rpx; color: #1f2329; line-height: 1.6; margin: 8rpx 0 12rpx; }
.bc-meta { font-size: 28rpx; color: #666; }
.bc-reply { font-size: 30rpx; color: #333; line-height: 1.7; background: #E8F7EE; border-radius: 14rpx; padding: 18rpx 20rpx; margin-top: 16rpx; border-left: 6rpx solid #2ECC71; }
.bc-reply-label { font-weight: 700; color: #27AE60; }
.bc-reply-time { display: block; font-size: 28rpx; color: #666; margin-top: 6rpx; }

.empty-state { display: flex; flex-direction: column; align-items: center; color: #777; font-size: 32rpx; padding-top: 120rpx; }
.empty-emoji { font-size: 80rpx; margin-bottom: 20rpx; }
</style>
