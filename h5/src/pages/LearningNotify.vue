<template>
  <div class="page learning-notify">
    <PageNav title="学习通知" />

    <main class="notify-body" v-if="item">
      <!-- 通知内容预览：与会议通知同款（首行缩进、落款靠右） -->
      <section class="notice-card">
        <div class="nc-title">{{ item.title }}</div>
        <div class="nc-para">各位委员：现定于 {{ fmtCnDate(item.date) }} {{ fmtHm(item.time) }} 在{{ item.location || '业委会办公室' }}组织内部学习，主要内容：{{ item.description || item.title }}，请准时参加。</div>
        <div class="nc-sign">业主委员会</div>
      </section>

      <!-- 计划参加人员 -->
      <section class="who-card" v-if="planNames.length">
        <div class="who-head">
          <span class="who-title">计划参加人员</span>
          <span class="who-stat" :class="{ ok: item.notified }">{{ item.notified ? '已通知全员' : ('共 ' + planNames.length + ' 人') }}</span>
        </div>
        <div class="who-list">
          <span v-for="n in planNames" :key="n" class="who-chip">{{ n }}</span>
        </div>
      </section>

      <!-- 已通知：提示 + 进入详情（结束后在详情登记学习情况） -->
      <div class="done-tip" v-if="item.notified">
        <span class="dt-ic">✓</span>
        <span class="dt-text">{{ notifiedText }}</span>
      </div>
    </main>

    <!-- 底部主操作：未通知=二选一发通知；已通知=去详情 -->
    <div class="notify-footer" v-if="item">
      <template v-if="!item.notified">
        <button class="nf-btn nf-app" :disabled="busy" @click="notifyApp">App内通知</button>
        <button class="nf-btn nf-wechat" :disabled="busy" @click="notifyWechat">去微信通知</button>
      </template>
      <template v-else>
        <button class="nf-btn nf-detail" @click="goDetail">进入详情（结束后登记学习情况）</button>
      </template>
    </div>

    <div v-if="!item" class="empty-state"><span>加载中…</span></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import { toast } from '@/utils/ui'

const route = useRoute()
const id = Number(route.query.id)
const item = ref(null)
const busy = ref(false)

const planNames = computed(() => String((item.value && item.value.attendees) || '').split(/[,，、\s]+/).filter(Boolean))
const notifiedText = computed(() => planNames.value.length
  ? ('已通知 ' + planNames.value.length + ' 位委员，学习结束后可在详情登记参加情况')
  : '通知已发出，学习结束后可在详情登记参加情况')

function load() {
  api.learningList('internal', null).then((list) => {
    const found = (list || []).find((i) => i.id === id)
    if (found) item.value = found
    else toast({ title: '学习记录不存在', icon: 'none' })
  }).catch(() => toast({ title: '加载失败', icon: 'none' }))
}

async function notifyApp() {
  if (busy.value) return
  busy.value = true
  try {
    await api.learningNotifyAll(id)
    toast({ title: '已在 App 内通知全员', icon: 'success' })
    load()
  } catch (e) {
    toast({ title: (e && e.message) || '通知失败', icon: 'none' })
  } finally { busy.value = false }
}

async function notifyWechat() {
  if (busy.value) return
  busy.value = true
  try {
    await copyText(noticePlainText())
    await api.learningNotifyAll(id)
    toast({ title: '通知已复制，去微信群粘贴发送', icon: 'none' })
    load()
  } catch (e) {
    toast({ title: (e && e.message) || '操作失败', icon: 'none' })
  } finally { busy.value = false }
}

function goDetail() {
  window.location.replace('/learning-detail?id=' + id)
}

// 复制到剪贴板（带非 HTTPS/旧 WebView 降级），与会议通知一致
function copyText(text) {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    return navigator.clipboard.writeText(text)
  }
  return new Promise((resolve) => {
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta); ta.select()
    try { document.execCommand('copy') } catch (e) { /* 忽略 */ }
    document.body.removeChild(ta); resolve()
  })
}

function noticePlainText() {
  const it = item.value || {}
  return '各位委员：现定于 ' + fmtCnDate(it.date) + ' ' + fmtHm(it.time) + ' 在' + (it.location || '业委会办公室')
    + '组织内部学习，主要内容：' + (it.description || it.title) + '，请准时参加。\n——业主委员会'
}

// 2026-08-06 → 2026年8月6日
function fmtCnDate(s) {
  if (!s) return ''
  const m = String(s).match(/(\d{4})-(\d{2})-(\d{2})/)
  return m ? (Number(m[1]) + '年' + Number(m[2]) + '月' + Number(m[3]) + '日') : String(s)
}
function fmtHm(s) { return String(s || '').slice(0, 5) }

onMounted(load)
</script>

<style scoped>
.learning-notify { min-height: 100vh; background: #f5f5f7; }
.notify-body { padding: 24rpx 28rpx calc(200rpx + env(safe-area-inset-bottom)); }

.notice-card { background: #fff; border-radius: 22rpx; padding: 30rpx 28rpx; box-shadow: 0 6rpx 18rpx rgba(31, 45, 61, .06); }
.nc-title { font-size: 34rpx; font-weight: 700; color: #1f2329; margin-bottom: 16rpx; }
.nc-para { font-size: 30rpx; line-height: 1.75; color: #2b323b; text-indent: 2em; }
.nc-sign { margin-top: 16rpx; text-align: right; font-size: 29rpx; color: #33373d; }

.who-card { margin-top: 20rpx; background: #fff; border-radius: 22rpx; padding: 24rpx 26rpx; box-shadow: 0 6rpx 18rpx rgba(31, 45, 61, .06); }
.who-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14rpx; }
.who-title { font-size: 30rpx; font-weight: 700; color: #1f2329; }
.who-stat { font-size: 26rpx; color: #C77800; font-weight: 600; }
.who-stat.ok { color: #2E9E5B; }
.who-list { display: flex; flex-wrap: wrap; gap: 12rpx; }
.who-chip { padding: 8rpx 20rpx; border-radius: 999rpx; background: #F1F3F5; color: #4E6076; font-size: 27rpx; }

.done-tip { margin-top: 20rpx; display: flex; align-items: center; gap: 12rpx; padding: 20rpx 24rpx; background: #E8F5EE; border-radius: 18rpx; }
.dt-ic { color: #2E9E5B; font-weight: 700; font-size: 30rpx; }
.dt-text { font-size: 28rpx; color: #2E7D46; line-height: 1.5; font-weight: 600; }

.notify-footer { position: fixed; left: 0; right: 0; bottom: 0; z-index: 30; display: flex; gap: 24rpx; padding: 18rpx 28rpx calc(20rpx + env(safe-area-inset-bottom)); background: #fff; box-shadow: 0 -6rpx 20rpx rgba(0,0,0,.06); }
.nf-btn { flex: 1; height: 92rpx; border: 0; border-radius: 20rpx; font-size: 32rpx; font-weight: 700; }
.nf-btn:disabled { opacity: .6; }
.nf-app { background: #fff; border: 2rpx solid var(--c-primary); color: var(--c-primary-dark); }
.nf-wechat { background: var(--c-primary); color: #fff; }
.nf-detail { background: var(--c-primary); color: #fff; }

.empty-state { padding: 100rpx 0; text-align: center; color: #8A94A6; font-size: 30rpx; }
</style>
