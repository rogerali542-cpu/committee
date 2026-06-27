<template>
  <div class="pub-page info-public-page">
    <PublishNav title="公示信息" />

    <div class="pub-wrap">
      <div class="pub-note warn info-intro">
        业委会会议纪要及决定，依规在作出后三日内向全体业主公示。
      </div>

      <template v-if="items.length">
        <div
          class="pub-card"
          v-for="item in items"
          :key="item.id"
          @click="viewDetail(item)"
        >
          <span class="pub-tag">{{ item.type === 'committee' ? '会议公示' : '大会决议' }}</span>
          <!-- 状态徽标位：现示公示状态；未来上社区链后此处换「已上链/存证中」 -->
          <span class="pub-badge is-pub">已公示</span>

          <span class="pub-title">{{ item.title }}</span>

          <div class="pub-meta">
            <span class="mi" v-if="item.date">会议日期：<b>{{ item.date }}</b></span>
            <span class="mi" v-if="item.publishDate">公示时间：<b>{{ item.publishDate }}</b></span>
          </div>

          <template v-if="item.description">
            <div class="pub-divider"></div>
            <span class="pub-body">{{ item.description }}</span>
          </template>
        </div>
      </template>

      <div v-else class="pub-card pub-empty">
        <div class="empty-icon">!</div>
        <span class="empty-title">暂无已公示的会议公告</span>
        <span class="empty-text">公示内容将在会议作出决定后三日内发布于此。</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onActivated } from 'vue'
import api from '@/api'
import { navigateTo } from '@/utils/navigate'
import PublishNav from '@/components/PublishNav.vue'

const items = ref([])

async function loadData() {
  try {
    const res = await api.publicInfo()
    items.value = res
  } catch (e) { /* offline ok */ }
}

function viewDetail(item) {
  const { id, type } = item
  if (type === 'committee') {
    navigateTo('/pages/minutes/minutes?meetingId=' + id + '&from=info-public')
  }
}

onMounted(() => { loadData() })
onActivated(() => { loadData() })
</script>

<style scoped>
.info-public-page { padding-bottom: 40rpx; }

/* 顶部说明条：复用琥珀提醒条样式，高对比、居中可读 */
.info-intro {
  text-align: center;
  line-height: 1.7;
  margin-bottom: 24rpx;
}

/* 卡片可点击反馈 */
.pub-card { cursor: pointer; }
.pub-card:active { background: var(--pub-blue-soft); }

.pub-empty { text-align: center; padding: 72rpx 36rpx; cursor: default; }
.pub-empty:active { background: #fff; }
.empty-icon { width: 96rpx; height: 96rpx; border-radius: 50%; background: var(--pub-blue-soft); color: var(--pub-blue); display: flex; align-items: center; justify-content: center; margin: 0 auto 24rpx; font-size: 52rpx; font-weight: 800; }
.empty-title { display: block; font-size: 38rpx; color: var(--pub-ink); font-weight: 700; margin-bottom: 14rpx; }
.empty-text { display: block; font-size: 30rpx; color: var(--pub-sub); line-height: 1.7; }
</style>
