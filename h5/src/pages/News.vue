<template>
  <div class="news-page">
    <PageNav :title="pageTitle" style="margin:0 0 0;" />

    <div class="news-scroll" style="overflow-y:auto;">
      <div v-if="news" class="news-workbench">
        <div class="news-meta">
          <div>
            <div class="news-kicker">{{ isPartyNews ? '党建新闻稿' : '会议新闻稿' }}</div>
            <div class="news-source">依据本次会议纪要生成</div>
          </div>
          <span class="news-status">AI生成 · 待审核</span>
        </div>

        <div class="news-review-tip">发布前请核对事实、时间、人员和表述口径。</div>

        <div class="news-paper">
          <div class="news-flag">
            <span>{{ today }}</span>
          </div>
          <h1 class="news-title">{{ news.title }}</h1>
          <p class="news-para" v-for="(p, i) in paras" :key="i">{{ p }}</p>
          <div class="news-sign">
            <span class="ns-org">{{ isPartyNews ? '中共社区党支部委员会' : '社区业主委员会' }}</span>
            <span class="ns-date">{{ today }}</span>
          </div>
        </div>

        <div class="news-actions">
          <button class="news-btn primary" @click="copyAll">复制全文</button>
          <button class="news-btn ghost" @click="regen" :disabled="loading">{{ loading ? '生成中…' : '重新生成' }}</button>
        </div>
        <button class="news-home" @click="goHome">回到首页</button>
      </div>

      <div v-else class="news-empty">
        <div class="ne-card">
          <div class="ne-flag">党建新闻稿</div>
          <div class="ne-text">{{ loadingText }}</div>
          <button v-if="!loading && !checking" class="news-btn primary" @click="regen">生成党建新闻</button>
        </div>
      </div>
    </div>

    <AiWorkingOverlay :active="loading" phase="news" theme="party" @confirm="onDone" @close="onClose" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast } from '@/utils/ui'
import { redirectTo } from '@/utils/navigate'
import PageNav from '@/components/PageNav.vue'
import AiWorkingOverlay from '@/components/AiWorkingOverlay.vue'

const route = useRoute()
const meetingId = ref(null)
const news = ref(null)
const loading = ref(false)          // 真在生成/轮询中 → 开全屏遮罩
const checking = ref(false)         // 仅进页查服务端状态 → 只显示文字，不弹遮罩（服务端已有成稿时直接展示）
const loadingText = ref('正在准备党建新闻…')

const NEWS_KEY = (id) => 'committee_news_' + id

const paras = computed(() => String(news.value && news.value.content || '')
  .split(/\n+/).map(s => s.trim()).filter(Boolean))

// 大多数会议纪要生成的是党建新闻；但内容明显不是党建主题时，不再冠以「党建新闻」，改用中性「会议新闻」。
const PARTY_KW = ['党建', '党支部', '党员', '党组织', '党委', '党的', '党风', '党性', '主题党日', '三会一课', '两学一做', '初心使命', '红色教育', '党史']
const isPartyNews = computed(() => {
  const txt = (news.value ? ((news.value.title || '') + (news.value.content || '')) : '')
  return PARTY_KW.some(k => txt.includes(k))
})
const pageTitle = computed(() => (news.value && !isPartyNews.value) ? '会议新闻' : '党建新闻')

const today = (() => {
  const d = new Date()
  return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日'
})()

let _pollTimer = null
function stopPoll() { if (_pollTimer) { clearTimeout(_pollTimer); _pollTimer = null } }

onMounted(async () => {
  meetingId.value = route.query.meetingId
  // ① 先读本地缓存（详情页生成后存的），秒开
  try {
    const cached = sessionStorage.getItem(NEWS_KEY(meetingId.value))
    if (cached) news.value = JSON.parse(cached)
  } catch (e) {}
  if (news.value) return
  // ② 无缓存（如退微信后 webview 重载、或换设备）→ 问服务端权威状态。
  //    只是读取，不开遮罩（checking）；已有成稿直接展示，不弹「生成完成」。
  checking.value = true
  loadingText.value = '正在获取党建新闻…'
  try {
    const st = await api.committeeNewsStatus(meetingId.value)
    if (st && st.status === 'success' && st.content) { applyNews(st); return }
    if (st && st.status === 'running') { loadingText.value = '豆包正在撰写党建新闻…'; pollNews(); return } // 后台真在生成 → 开遮罩看进度
  } catch (e) {}
  finally { checking.value = false }
  // ③ 从没生成过 / 失败 → 现场发起生成
  regen()
})

onUnmounted(stopPoll)

function applyNews(st) {
  stopPoll()
  news.value = { title: st.title, content: st.content }
  try { sessionStorage.setItem(NEWS_KEY(meetingId.value), JSON.stringify(news.value)) } catch (e) {}
  loading.value = false
}

// 轮询服务端新闻状态直到 success/failed（后端 @Async 后台生成，退微信也照跑）
function pollNews() {
  stopPoll()
  loading.value = true
  _pollTimer = setTimeout(async () => {
    _pollTimer = null
    try {
      const st = await api.committeeNewsStatus(meetingId.value)
      if (st && st.status === 'success' && st.content) return applyNews(st)
      if (st && st.status === 'failed') { loading.value = false; loadingText.value = '生成失败，点下方按钮重试'; toast({ title: '生成失败，请重试', icon: 'none' }); return }
    } catch (e) {}
    pollNews()
  }, 3000)
}

async function regen() {
  if (loading.value || !meetingId.value) return
  loading.value = true
  loadingText.value = '豆包正在撰写党建新闻…'
  try {
    const st = await api.committeeGenerateNews(meetingId.value) // 发起（去重），后台 @Async 生成
    if (st && st.status === 'success' && st.content) { applyNews(st); return }
    if (st && st.status === 'failed') { loading.value = false; loadingText.value = '生成失败，点下方按钮重试'; toast({ title: '生成失败，请重试', icon: 'none' }); return }
    pollNews() // running → 轮询到完成
  } catch (e) {
    loading.value = false
    toast({ title: (e && e.message) || '生成失败，请重试', icon: 'none' })
    loadingText.value = '生成失败，点下方按钮重试'
  }
}

// 遮罩「查看新闻稿」/关闭：新闻已在本页，直接收起遮罩即可
function onDone() {}
function onClose() {}

// 回到首页（业委会主页 /main）：硬导航兜底，避免软路由偶发不切换
function goHome() {
  try { redirectTo('/main') } catch (e) {}
  setTimeout(() => { if (!location.pathname.startsWith('/main')) location.href = '/main' }, 300)
}

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
.news-scroll { flex: 1; padding: 24rpx 28rpx 54rpx; box-sizing: border-box; }

.news-workbench { display: flex; flex-direction: column; gap: 18rpx; }
.news-meta { display: flex; align-items: flex-start; justify-content: space-between; gap: 18rpx; }
.news-kicker { font-size: 38rpx; line-height: 1.25; font-weight: 800; color: #8E0E14; }
.news-source { margin-top: 8rpx; font-size: 24rpx; color: #9A7458; }
.news-status { flex-shrink: 0; margin-top: 4rpx; padding: 8rpx 16rpx; border-radius: 999rpx; background: #FFF7EF; border: 1rpx solid #E7B9A2; color: #A8432E; font-size: 23rpx; font-weight: 700; }
.news-review-tip { border-radius: 14rpx; background: #FFF8EE; border: 1rpx solid #EAD0B8; color: #8D6240; font-size: 25rpx; line-height: 1.45; padding: 16rpx 20rpx; }

.news-paper {
  background: #FFFDFB;
  border-radius: 18rpx;
  border: 1rpx solid #E9D9C9;
  box-shadow: 0 6rpx 22rpx rgba(105, 57, 28, 0.08);
  padding: 34rpx 34rpx 38rpx;
}
.news-flag { display: flex; justify-content: flex-end; margin-bottom: 18rpx; font-size: 24rpx; color: #A98A70; }

.news-title { font-size: 40rpx; line-height: 1.45; font-weight: 800; color: #8E0E14; text-align: left; margin: 0 0 28rpx; letter-spacing: 0; }
.news-para { font-size: 31rpx; line-height: 1.86; color: #292724; text-indent: 2em; margin: 0 0 24rpx; text-align: justify; }
.news-sign { display: flex; flex-direction: column; align-items: flex-end; gap: 6rpx; margin-top: 34rpx; }
.ns-org { font-size: 29rpx; font-weight: 700; color: #333; }
.ns-date { font-size: 27rpx; color: #666; }

.news-actions { display: grid; grid-template-columns: 1.3fr 1fr; gap: 18rpx; margin-top: 4rpx; }
.news-btn { width: 100%; height: 88rpx; border: none; border-radius: 14rpx; color: #fff; font-size: 31rpx; font-weight: 800; }
.news-btn.primary { background: #B3121A; box-shadow: 0 6rpx 16rpx rgba(179, 18, 26, 0.22); }
.news-btn:active { opacity: .9; }
.news-btn.ghost { background: #FFFDFB; color: #B3121A; border: 2rpx solid #D9AAA0; box-shadow: none; }
.news-btn:disabled { opacity: .6; }
.news-home { height: 64rpx; border: none; background: transparent; color: #9A7458; font-size: 27rpx; font-weight: 600; }

.news-empty { display: flex; flex-direction: column; align-items: stretch; padding: 96rpx 18rpx; }
.ne-card { background: #FFFDFB; border: 1rpx solid #E9D9C9; border-radius: 18rpx; padding: 54rpx 34rpx; box-shadow: 0 6rpx 22rpx rgba(105, 57, 28, 0.08); text-align: center; }
.ne-flag { font-size: 38rpx; font-weight: 800; color: #8E0E14; }
.ne-text { margin: 18rpx 0 34rpx; font-size: 29rpx; color: #8a6b52; }
</style>
