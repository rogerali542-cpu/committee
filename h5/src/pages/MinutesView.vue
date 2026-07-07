<template>
  <div class="mv-page detail-minutes-view">
    <PageNav title="会议纪要" style="margin:-24rpx -24rpx 0;" />

    <div v-if="loading" class="doc mv-loading">正在加载会议纪要…</div>

    <!-- 正文格式与老纪要页一致：只显示已生成的纪要，不再现拼结构化兜底 -->
    <div v-else-if="text" class="doc">
      <span class="doc-body-title">{{ title }}</span>
      <span class="doc-body">{{ body }}</span>
      <!-- 底部只保留 复制全文 / 待办事项，去掉编辑纪要、结束会议等操作；两项稍微加大 -->
      <div class="mv-links">
        <span class="mv-link" @click="copyAll">复制全文</span>
        <span class="mv-link" @click="viewTodos">待办事项</span>
      </div>
    </div>

    <div v-else class="mv-empty">
      <div class="mv-empty-ico">📄</div>
      <div class="mv-empty-title">暂无会议纪要</div>
      <div class="mv-empty-sub">本次会议还没有生成会议纪要，可在会议录音页生成后再来查看。</div>
    </div>
  </div>
</template>

<script setup>
// 会议详情专用的「只读」会议纪要阅读页：只显示后端已生成的纪要（格式同老页），
// 去掉编辑/结束会议等操作，底部只留复制/待办；返回固定回会议详情。
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast } from '@/utils/ui'
import { navigateTo } from '@/utils/navigate'
import PageNav from '@/components/PageNav.vue'

const route = useRoute()
const loading = ref(true)
const text = ref('')
let meetingId = null

// 去掉 AI 纪要里的 Markdown 标题标记（行首 #），纯文本更干净；首行作标题，其余作正文
const pretty = computed(() => String(text.value || '').replace(/^[ \t]*#{1,6}[ \t]*/gm, ''))
const title = computed(() => (pretty.value.split('\n')[0] || '').trim())
const body = computed(() => pretty.value.split('\n').slice(1).join('\n').replace(/^\s*\n/, ''))

async function load() {
  loading.value = true
  try {
    const txt = await api.committeeMinutes(meetingId)
    text.value = txt && String(txt).trim() ? txt : ''
  } catch (e) {
    text.value = ''
  } finally {
    loading.value = false
  }
}

async function copyAll() {
  try {
    await navigator.clipboard.writeText(text.value)
    toast({ title: '已复制' })
  } catch (e) {
    toast({ title: '复制失败', icon: 'none' })
  }
}

// 待办事项独立页；软路由偶发不切换 → 硬导航兜底
function viewTodos() {
  const q = 'meetingId=' + meetingId
  navigateTo('/pages/minutes-todos/minutes-todos?' + q)
  setTimeout(() => {
    if (document.querySelector('.detail-minutes-view')) window.location.href = '/minutes-todos?' + q
  }, 300)
}

onMounted(() => {
  meetingId = parseInt(route.query.meetingId)
  if (meetingId) load()
  else loading.value = false
})
</script>

<style scoped>
/* 顶栏统一为纯深橙（覆盖 PageNav 默认渐变） */
:deep(.page-nav) { background: var(--c-primary-dark); }
.mv-page { min-height: 100vh; background: #f4f5f7; padding: 24rpx 24rpx calc(40rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
/* 正文格式与老纪要页保持一致 */
.doc { background: #fff; border-radius: 24rpx; padding: 36rpx 32rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.mv-loading { color: #888; text-align: center; font-size: 30rpx; }
.doc-body-title { display: block; font-size: 46rpx; font-weight: 700; color: #1a1a1a; text-align: center; line-height: 1.4; padding: 8rpx 0 4rpx; }
.doc-body { display: block; font-size: 34rpx; color: #33373d; line-height: 1.9; white-space: pre-wrap; padding: 24rpx 0; }
/* 复制/待办：在老页小链接基础上加大，方便点（老页 28rpx → 34rpx，加内边距 + 分隔线） */
.mv-links { display: flex; flex-wrap: wrap; justify-content: center; gap: 20rpx 48rpx; margin-top: 16rpx; padding-top: 24rpx; border-top: 2rpx solid #f0f0f0; }
.mv-link { font-size: 34rpx; font-weight: 700; color: #C77800; padding: 16rpx 26rpx; }
.mv-link:active { opacity: 0.6; }
/* 无纪要时的空状态 */
.mv-empty { background: #fff; border-radius: 24rpx; padding: 80rpx 40rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); text-align: center; }
.mv-empty-ico { font-size: 72rpx; margin-bottom: 16rpx; }
.mv-empty-title { font-size: 34rpx; font-weight: 700; color: #1a1a1a; margin-bottom: 12rpx; }
.mv-empty-sub { font-size: 28rpx; color: #888; line-height: 1.7; }
</style>
