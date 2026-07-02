<template>
  <div class="news-page">
    <PageNav title="党建新闻" style="margin:0 0 0;" />

    <div class="news-scroll" style="overflow-y:auto;">
      <div class="news-paper" v-if="news">
        <div class="news-flag">
          <span class="nf-badge">党建融媒 · AI 生成</span>
          <span class="nf-date">{{ today }}</span>
        </div>
        <h1 class="news-title">{{ news.title }}</h1>
        <div class="news-rule"><span class="nr-star">★</span></div>
        <p class="news-para" v-for="(p, i) in paras" :key="i">{{ p }}</p>
        <div class="news-sign">
          <span class="ns-org">中共社区党支部委员会</span>
          <span class="ns-date">{{ today }}</span>
        </div>
        <div class="news-note">本篇由豆包大模型依据会议纪要自动生成，仅供参考，发布前请人工审核。</div>
        <div class="news-actions">
          <button class="news-btn" @click="copyAll">复制全文</button>
          <button class="news-btn ghost" @click="regen" :disabled="loading">{{ loading ? '生成中…' : '重新生成' }}</button>
        </div>
      </div>

      <div v-else class="news-empty">
        <div class="ne-flag">党建融媒</div>
        <div class="ne-text">{{ loadingText }}</div>
        <button v-if="!loading" class="news-btn" @click="regen">生成党建新闻</button>
      </div>
    </div>

    <AiWorkingOverlay :active="loading" phase="news" theme="party" @confirm="onDone" @close="onClose" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast } from '@/utils/ui'
import PageNav from '@/components/PageNav.vue'
import AiWorkingOverlay from '@/components/AiWorkingOverlay.vue'

const route = useRoute()
const meetingId = ref(null)
const news = ref(null)
const loading = ref(false)
const loadingText = ref('正在准备党建新闻…')

const NEWS_KEY = (id) => 'committee_news_' + id

const paras = computed(() => String(news.value && news.value.content || '')
  .split(/\n+/).map(s => s.trim()).filter(Boolean))

const today = (() => {
  const d = new Date()
  return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日'
})()

onMounted(() => {
  meetingId.value = route.query.meetingId
  // 优先读会议详情页生成后缓存的新闻；没有再现场生成
  try {
    const cached = sessionStorage.getItem(NEWS_KEY(meetingId.value))
    if (cached) news.value = JSON.parse(cached)
  } catch (e) {}
  if (!news.value) regen()
})

async function regen() {
  if (loading.value || !meetingId.value) return
  loading.value = true
  loadingText.value = '豆包正在撰写党建新闻…'
  try {
    const res = await api.committeeGenerateNews(meetingId.value)
    if (res && res.content) {
      news.value = { title: res.title, content: res.content }
      try { sessionStorage.setItem(NEWS_KEY(meetingId.value), JSON.stringify(news.value)) } catch (e) {}
    } else {
      toast({ title: '生成失败，请重试', icon: 'none' })
    }
  } catch (e) {
    toast({ title: (e && e.message) || '生成失败，请重试', icon: 'none' })
    loadingText.value = '生成失败，点下方按钮重试'
  } finally {
    loading.value = false
  }
}

// 遮罩「查看新闻稿」/关闭：新闻已在本页，直接收起遮罩即可
function onDone() {}
function onClose() {}

function copyAll() {
  const text = (news.value ? (news.value.title + '\n\n' + news.value.content) : '')
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(() => toast({ title: '已复制全文', icon: 'success' }))
    } else {
      const ta = document.createElement('textarea')
      ta.value = text; document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta)
      toast({ title: '已复制全文', icon: 'success' })
    }
  } catch (e) { toast({ title: '复制失败', icon: 'none' }) }
}
</script>

<style scoped>
.news-page { min-height: 100vh; background: #F4ECE3; display: flex; flex-direction: column; }
/* 顶栏红色党建风 */
:deep(.page-nav) { background: linear-gradient(90deg, #B3121A, #8E0E14); }
.news-scroll { flex: 1; padding: 28rpx 28rpx 60rpx; box-sizing: border-box; }

.news-paper {
  background: #FFFDFB;
  border-radius: 20rpx;
  border-top: 10rpx solid #C0141B;
  box-shadow: 0 6rpx 24rpx rgba(140, 20, 20, 0.12);
  padding: 34rpx 34rpx 40rpx;
}
.news-flag { display: flex; align-items: center; justify-content: space-between; margin-bottom: 26rpx; }
.nf-badge { font-size: 24rpx; font-weight: 700; color: #fff; background: linear-gradient(90deg, #C0141B, #E23A2E); padding: 8rpx 18rpx; border-radius: 999rpx; letter-spacing: 1rpx; }
.nf-date { font-size: 24rpx; color: #B08968; }

.news-title { font-size: 46rpx; line-height: 1.4; font-weight: 800; color: #A80F16; text-align: center; margin: 8rpx 0 0; letter-spacing: 1rpx; }
.news-rule { display: flex; align-items: center; justify-content: center; margin: 20rpx 0 26rpx; position: relative; }
.news-rule::before, .news-rule::after { content: ""; height: 3rpx; width: 34%; background: linear-gradient(90deg, transparent, #C0141B); }
.news-rule::after { background: linear-gradient(90deg, #C0141B, transparent); }
.nr-star { color: #C0141B; font-size: 30rpx; margin: 0 16rpx; }

.news-para { font-size: 34rpx; line-height: 1.9; color: #2B2B2B; text-indent: 2em; margin: 0 0 22rpx; text-align: justify; }
.news-sign { display: flex; flex-direction: column; align-items: flex-end; gap: 6rpx; margin-top: 34rpx; }
.ns-org { font-size: 32rpx; font-weight: 700; color: #333; }
.ns-date { font-size: 30rpx; color: #555; }
.news-note { margin-top: 30rpx; padding-top: 20rpx; border-top: 1rpx dashed #E4CFC0; font-size: 24rpx; color: #B08968; line-height: 1.6; }

.news-actions { display: flex; gap: 20rpx; margin-top: 30rpx; }
.news-btn { flex: 1; height: 88rpx; border: none; border-radius: 16rpx; background: linear-gradient(90deg, #C0141B, #E23A2E); color: #fff; font-size: 32rpx; font-weight: 700; box-shadow: 0 6rpx 18rpx rgba(200, 30, 30, 0.28); }
.news-btn:active { opacity: .9; }
.news-btn.ghost { background: #fff; color: #C0141B; border: 2rpx solid #C0141B; box-shadow: none; }
.news-btn:disabled { opacity: .6; }

.news-empty { display: flex; flex-direction: column; align-items: center; gap: 24rpx; padding: 120rpx 40rpx; }
.ne-flag { font-size: 40rpx; font-weight: 800; color: #C0141B; letter-spacing: 4rpx; }
.ne-text { font-size: 30rpx; color: #8a6b52; }
</style>
