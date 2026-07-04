<template>
  <div class="page minutes-internal-page" style="overflow-y:auto;">
    <PageNav title="内部总结" style="display:block;margin:-24rpx -24rpx 0;" />
    <div v-if="loading" class="empty-state"><span>加载中...</span></div>

    <div v-else-if="!reportText" class="access-card">
      <div class="access-icon">!</div>
      <span class="access-title">暂无内部议题报告</span>
      <span class="access-text">{{ emptyText }}</span>
    </div>

    <div v-else class="doc">
      <div class="info-banner">内部资料 · 详细版议题报告，仅供业委会内部查看</div>
      <span class="doc-title">{{ title }}</span>

      <div class="doc-sec">
        <span class="sec-title">AI 议题报告</span>
        <span class="doc-body">{{ reportText }}</span>
      </div>

      <div class="doc-sec" v-if="todoText">
        <span class="sec-title">待办事项</span>
        <span class="doc-body">{{ todoText }}</span>
      </div>

      <div class="minutes-actions">
        <button class="btn-primary copy-btn" @click="copyReport">复制报告全文</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast } from '@/utils/ui'
import PageNav from '@/components/PageNav.vue'

const route = useRoute()

const loading = ref(true)
const title = ref('')
const reportText = ref('')
const todoText = ref('')
const emptyText = ref('')

let meetingId = null

async function load() {
  let t = '会议议题报告'
  try {
    const detail = await api.committeeDetail(meetingId)
    if (detail && detail.title) t = detail.title
  } catch (e) {}
  let report = ''
  let todo = ''
  try { report = await api.committeeQuickTopicReport(meetingId) } catch (e) {}
  try { todo = await api.committeeQuickTodos(meetingId) } catch (e) {}
  report = report && String(report).trim()
  todo = todo && String(todo).trim()
  loading.value = false
  title.value = t
  reportText.value = report || ''
  todoText.value = todo || ''
  emptyText.value = report ? '' : '内部AI议题报告尚未生成，请先在会议进行页生成纪要草稿。'
}

async function copyReport() {
  const text = [reportText.value, todoText.value].filter(Boolean).join('\n\n')
  if (!text) return
  await navigator.clipboard.writeText(text)
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
.page { min-height: 100vh; background: #f4f5f7; padding: 24rpx 24rpx 100rpx; box-sizing: border-box; }
.doc { background: #fff; border-radius: 24rpx; padding: 36rpx 32rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }

.access-card { background: #fff; border-radius: 24rpx; padding: 64rpx 36rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); text-align: center; }
.access-icon { width: 96rpx; height: 96rpx; border-radius: 50%; background: #FFF3E0; color: #E67E22; display: flex; align-items: center; justify-content: center; margin: 0 auto 24rpx; font-size: 52rpx; font-weight: 700; }
.access-title { display: block; font-size: 38rpx; color: #1f2329; font-weight: 700; margin-bottom: 14rpx; }
.access-text { display: block; font-size: 30rpx; color: #666; line-height: 1.7; }

.info-banner { background: #F1EEFB; color: #6B4FBB; font-size: 28rpx; font-weight: 600; text-align: center; padding: 18rpx 20rpx; border-radius: 14rpx; margin-bottom: 28rpx; }

.doc-title { font-size: 42rpx; font-weight: 700; text-align: center; display: block; margin-bottom: 20rpx; color: #1f2329; line-height: 1.4; }
.doc-sec { margin-bottom: 24rpx; padding-top: 24rpx; border-top: 2rpx solid #f0f0f0; }
.sec-title { font-size: 32rpx; font-weight: 700; color: #1f2329; display: block; margin-bottom: 16rpx; }
.doc-body { display: block; font-size: 32rpx; color: #33373d; line-height: 1.9; white-space: pre-wrap; word-break: break-word; }

.minutes-actions { margin-top: 16rpx; }
.copy-btn { width: 100%; }
.btn-primary { background: var(--c-primary-dark); color: #fff; border-radius: 18rpx; font-size: 34rpx; font-weight: 600; height: 96rpx; line-height: 96rpx; }

.empty-state { text-align: center; color: #666; font-size: 32rpx; padding: 80rpx 0; }
</style>
