<template>
  <div class="page docp-page">
    <PageNav :title="titleText" style="margin:-24rpx -24rpx 0;">
      <template #left><div class="docp-back" @click="goBack">‹</div></template>
      <template #right>
        <button class="nav-home" @click="goHome">首页</button>
      </template>
    </PageNav>

    <div v-if="loading" class="docp-state">正在生成{{ titleText }}，请稍候…</div>
    <div v-else-if="error" class="docp-state">
      <span class="docp-err">{{ error }}</span>
      <button class="docp-retry" @click="load">重试</button>
    </div>
    <template v-else>
      <!-- 预览即最终 PDF：与导出文件完全一致 -->
      <iframe class="docp-frame" :src="url"></iframe>
      <div class="docp-foot">
        <span class="docp-tip">如无法直接预览，可导出后查看</span>
        <button class="docp-export" @click="download">导出 PDF</button>
      </div>
    </template>
  </div>
</template>

<script setup>
// 会议记录 / 会议纪要 独立预览页（0722 用户定：查看=进新页，页内预览+导出PDF）。
// 内容即后端生成的最终 PDF，预览与导出同一份文件。kind=record|minutes。
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast } from '@/utils/ui'
import { redirectTo, navigateBack } from '@/utils/navigate'
import PageNav from '@/components/PageNav.vue'

const route = useRoute()
const meetingId = parseInt(route.query.meetingId)
const kind = String(route.query.kind || 'record')

const titleText = computed(() => (kind === 'minutes' ? '会议纪要' : '会议记录'))
const loading = ref(true)
const error = ref('')
const url = ref('')
let blob = null

async function load() {
  loading.value = true
  error.value = ''
  try {
    blob = kind === 'minutes'
      ? await api.committeeExportMinutesPdf(meetingId)
      : await api.committeeExportMeetingRecord(meetingId)
    if (url.value) { try { URL.revokeObjectURL(url.value) } catch (e) {} }
    url.value = URL.createObjectURL(blob)
  } catch (e) {
    error.value = (e && e.message) || titleText.value + '生成失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function download() {
  if (!blob) return
  const link = document.createElement('a')
  link.href = url.value
  link.download = titleText.value + '.pdf'
  document.body.appendChild(link)
  link.click()
  link.remove()
  toast({ title: titleText.value + '已导出', icon: 'success' })
}

function goBack() {
  if (!meetingId) { navigateBack(); return }
  redirectTo('/pages/committee-detail/committee-detail?id=' + meetingId + '&from=doc-preview')
  setTimeout(() => {
    if (document.querySelector('.docp-page')) window.location.replace('/committee-detail?id=' + meetingId + '&from=doc-preview')
  }, 400)
}
function goHome() {
  redirectTo('/main')
  setTimeout(() => { if (document.querySelector('.docp-page')) window.location.replace('/main') }, 500)
}

onMounted(() => {
  if (!meetingId) { loading.value = false; error.value = '缺少会议参数'; return }
  load()
})
onUnmounted(() => { if (url.value) { try { URL.revokeObjectURL(url.value) } catch (e) {} } })
</script>

<style scoped>
.docp-page { display:flex; flex-direction:column; height:100vh; box-sizing:border-box; overflow:hidden; }
.docp-back { width:96rpx; height:124rpx; display:flex; align-items:center; justify-content:center; color:#fff; font-size:66rpx; font-weight:700; }
.docp-state { flex:1; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:26rpx; color:#7B818B; font-size:30rpx; padding:0 40rpx; text-align:center; line-height:1.7; }
.docp-err { color:#8A5A2B; }
.docp-retry { border:2rpx solid #D8DBE0; background:#fff; color:#55585E; font-size:27rpx; font-weight:600; border-radius:999rpx; padding:12rpx 44rpx; }
.docp-frame { flex:1; width:100%; border:0; background:#F5F6F8; margin-top:12rpx; }
.docp-foot { display:flex; align-items:center; gap:16rpx; padding:14rpx 20rpx calc(14rpx + env(safe-area-inset-bottom)); background:#fff; border-top:2rpx solid #EEF0F3; }
.docp-tip { flex:1; font-size:23rpx; color:#A0A5AD; }
.docp-export { flex-shrink:0; border:0; border-radius:999rpx; background:#2F6FB2; color:#fff; font-size:29rpx; font-weight:600; padding:16rpx 44rpx; }
.docp-export:active { background:#265C96; }
</style>
