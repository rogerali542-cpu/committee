<template>
  <div class="pub-page minutes-public-page">
    <PublishNav title="公开纪要" />

    <div class="pub-wrap">
      <div v-if="loading" class="pub-loading">加载中…</div>

      <div v-else-if="!plainText" class="pub-card pub-empty">
        <div class="empty-icon">!</div>
        <span class="empty-title">暂无公开纪要</span>
        <span class="empty-text">{{ emptyText }}</span>
      </div>

      <template v-else>
        <div class="pub-card">
          <span class="pub-tag">会议纪要</span>
          <!-- 状态徽标位：现示公示状态；未来上社区链后此处换「已上链/存证中」 -->
          <span class="pub-badge" :class="badgeClass">{{ badgeText }}</span>

          <span class="pub-title">{{ title }}</span>

          <div class="pub-meta" v-if="meetingDate || location">
            <span class="mi" v-if="meetingDate">会议时间：<b>{{ meetingDate }} {{ meetingTime }}</b></span>
            <span class="mi" v-if="location">会议地点：<b>{{ location }}</b></span>
          </div>

          <div v-if="isDraft" class="pub-note warn">草稿 · 会议尚未结束，内容可能还会调整</div>

          <div class="pub-divider"></div>

          <span class="pub-body">{{ plainText }}</span>
        </div>

        <button class="pub-btn" @click="copyText">复制纪要全文</button>
        <span class="pub-foot">本页为面向居民的公开纪要展示版</span>
      </template>
    </div>
  </div>
</template>

<script setup>
// 面向民众的「公开纪要」独立页：只读展示正式会议纪要正文（minutesMarkdown）。
// 与 pages/minutes 的内部工作视图分开——这里不含内部AI议题报告/待办/编辑/修订入口。
// 视觉=蓝色公示体系（publish-theme.css + PublishNav）。
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast } from '@/utils/ui'
import PublishNav from '@/components/PublishNav.vue'

const route = useRoute()

const loading = ref(true)
const title = ref('')
const meetingDate = ref('')
const meetingTime = ref('')
const location = ref('')
const plainText = ref('')
const isDraft = ref(false)
const isPublished = ref(false)
const emptyText = ref('')

const badgeText = computed(() => (isDraft.value ? '草稿' : isPublished.value ? '已公示' : '待公示'))
const badgeClass = computed(() => (isDraft.value ? 'is-draft' : isPublished.value ? 'is-pub' : 'is-wait'))

let meetingId = null

async function load() {
  try {
    const detail = await api.committeeDetail(meetingId)
    const published = !!(detail && detail.publish && detail.publish.published)
    const draft = !detail || detail.stage !== 'ended'
    let text = ''
    try { text = await api.committeeMinutes(meetingId) } catch (e) {}
    loading.value = false
    title.value = (detail && detail.title) || '业主委员会会议纪要'
    meetingDate.value = (detail && detail.meetingDate) || ''
    meetingTime.value = (detail && detail.meetingTime) || ''
    location.value = (detail && detail.location) || ''
    plainText.value = (text && String(text).trim()) || ''
    isDraft.value = draft
    isPublished.value = published
    emptyText.value = (text && String(text).trim()) ? '' : '正式纪要尚未生成，请先在会议进行页生成纪要草稿。'
  } catch (e) {
    loading.value = false
    emptyText.value = e.message || '纪要暂不可查看'
  }
}

async function copyText() {
  if (!plainText.value) return
  await navigator.clipboard.writeText(plainText.value)
  toast({ title: '已复制' })
}

onMounted(() => {
  meetingId = parseInt(route.query.meetingId)
  if (!meetingId) {
    loading.value = false
    emptyText.value = '缺少会议参数'
    return
  }
  load()
})
</script>

<style scoped>
.pub-loading { text-align: center; color: var(--pub-sub); font-size: 32rpx; padding: 120rpx 0; }

.pub-empty { text-align: center; padding: 72rpx 36rpx; }
.empty-icon { width: 96rpx; height: 96rpx; border-radius: 50%; background: var(--pub-blue-soft); color: var(--pub-blue); display: flex; align-items: center; justify-content: center; margin: 0 auto 24rpx; font-size: 52rpx; font-weight: 800; }
.empty-title { display: block; font-size: 38rpx; color: var(--pub-ink); font-weight: 700; margin-bottom: 14rpx; }
.empty-text { display: block; font-size: 30rpx; color: var(--pub-sub); line-height: 1.7; }
</style>
