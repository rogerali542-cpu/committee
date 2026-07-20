<template>
  <div class="news-page">
    <PageNav :title="pageTitle" style="margin:0 0 0;" />

    <div class="news-scroll" style="overflow-y:auto;">
      <div v-if="news" class="news-workbench">
        <div class="news-review-bar">
          <div class="review-copy">
            <span class="review-label">AI 草稿</span>
            <span class="review-title">发布前审核</span>
          </div>
          <p>请重点核对时间、地点、人员、数字和表述口径</p>
        </div>

        <article class="news-paper">
          <header class="article-head">
            <div class="news-kicker">{{ isPartyNews ? '党建引领 · 社区治理' : '业委会 · 会议动态' }}</div>
            <h1 class="news-title">{{ news.title }}</h1>
            <div class="news-byline">
              <span>{{ isPartyNews ? '社区党建工作动态' : '业委会会议动态' }}</span>
              <span class="byline-dot"></span>
              <time>{{ today }}</time>
            </div>
          </header>
          <div class="article-rule"><span></span></div>
          <div class="article-body">
            <p class="news-para" :class="{ lead: i === 0 }" v-for="(p, i) in paras" :key="i">{{ p }}</p>
          </div>
          <footer class="news-sign">
            <span class="ns-org">{{ isPartyNews ? '中共社区党支部委员会' : '社区业主委员会' }}</span>
            <span class="ns-date">{{ today }}</span>
          </footer>
        </article>

        <div class="news-actions">
          <button class="news-btn ghost" @click="regen" :disabled="loading">{{ loading ? '生成中…' : '重新生成' }}</button>
          <button class="news-btn primary" @click="copyAll">复制全文</button>
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
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import { toast } from '@/utils/ui'
import { redirectTo } from '@/utils/navigate'
import PageNav from '@/components/PageNav.vue'
import AiWorkingOverlay from '@/components/AiWorkingOverlay.vue'
import { aiTask, startAiTask, finishAiTask, failAiTask, clearAiTask } from '@/composables/aiTask'

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
  const resume = route.query.resume === '1'
  // ① 先读本地缓存（详情页生成后存的），秒开
  try {
    if (resume) sessionStorage.removeItem(NEWS_KEY(meetingId.value))
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
    if (st && st.status === 'running') {
      if (!aiTask.active) startAiTask({ label: '党建新闻生成中…', originPath: '/committee-detail?id=' + meetingId.value, targetPath: '/news?meetingId=' + meetingId.value + '&resume=1' })
      loadingText.value = '豆包正在撰写党建新闻…'; pollNews(); return
    }
  } catch (e) {}
  finally { checking.value = false }
  // ③ 从没生成过 / 失败 → 现场发起生成
  regen()
})

onUnmounted(() => { stopPoll(); aiTask.overlayShown = false })

// 新闻页显示全屏进度或本页小悬浮窗时，隐藏根节点的重复胶囊；离开本页后自动交还。
watch(loading, (value) => { if (value) aiTask.overlayShown = true }, { immediate: true })

function applyNews(st) {
  stopPoll()
  news.value = { title: st.title, content: st.content }
  try { sessionStorage.setItem(NEWS_KEY(meetingId.value), JSON.stringify(news.value)) } catch (e) {}
  if (aiTask.active) finishAiTask({ doneLabel: '党建新闻已生成', targetPath: '/news?meetingId=' + meetingId.value })
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
      if (st && st.status === 'failed') { failAiTask({ failLabel: '党建新闻生成失败' }); loading.value = false; loadingText.value = '生成失败，点下方按钮重试'; toast({ title: '生成失败，请重试', icon: 'none' }); return }
    } catch (e) {}
    pollNews()
  }, 3000)
}

async function regen() {
  if (loading.value || !meetingId.value) return
  loading.value = true
  loadingText.value = '豆包正在撰写党建新闻…'
  startAiTask({ label: '党建新闻生成中…', originPath: '/news?meetingId=' + meetingId.value, targetPath: '/news?meetingId=' + meetingId.value + '&resume=1' })
  try {
    const st = await api.committeeGenerateNews(meetingId.value) // 发起（去重），后台 @Async 生成
    if (st && st.status === 'success' && st.content) { applyNews(st); return }
    if (st && st.status === 'failed') { loading.value = false; loadingText.value = '生成失败，点下方按钮重试'; toast({ title: '生成失败，请重试', icon: 'none' }); return }
    pollNews() // running → 轮询到完成
  } catch (e) {
    failAiTask({ failLabel: '党建新闻生成失败' })
    loading.value = false
    toast({ title: (e && e.message) || '生成失败，请重试', icon: 'none' })
    loadingText.value = '生成失败，点下方按钮重试'
  }
}

// 遮罩「查看新闻稿」/关闭：新闻已在本页，直接收起遮罩即可
function onDone() { clearAiTask() }
function onClose() { aiTask.overlayShown = false }

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
.news-page { min-height:100vh; background:#F3F4F6; display:flex; flex-direction:column; }
:deep(.page-nav) { background:#A31219; }
.news-scroll { flex:1; overflow-y:auto; padding:22rpx 24rpx calc(52rpx + env(safe-area-inset-bottom)); box-sizing:border-box; }
.news-workbench { width:100%; max-width:760px; margin:0 auto; display:flex; flex-direction:column; gap:18rpx; }

.news-review-bar { display:flex; align-items:flex-start; flex-direction:column; gap:10rpx; padding:18rpx 22rpx; border-radius:16rpx; background:#FFF8E8; border:1rpx solid #EAD8AE; }
.review-copy { display:flex; align-items:center; gap:14rpx; flex-shrink:0; }
.review-label { padding:5rpx 12rpx; border-radius:8rpx; background:#A31219; color:#fff; font-size:26rpx; font-weight:700; }
.review-title { color:#4A3B23; font-size:28rpx; font-weight:700; }
.news-review-bar p { margin:0; color:#806B47; font-size:26rpx; line-height:1.55; text-align:left; }

.news-paper { background:#fff; border-radius:20rpx; border:1rpx solid #E5E7EB; box-shadow:0 10rpx 34rpx rgba(35,39,47,.07); padding:48rpx 38rpx 44rpx; }
.article-head { text-align:left; }
.news-kicker { color:#A31219; font-size:26rpx; font-weight:700; letter-spacing:2rpx; margin-bottom:18rpx; }
.news-title { margin:0; color:#191B20; font-family:"Noto Serif SC","Songti SC",SimSun,serif; font-size:45rpx; line-height:1.42; font-weight:800; letter-spacing:.5rpx; }
.news-byline { display:flex; align-items:center; flex-wrap:wrap; gap:12rpx; margin-top:22rpx; color:#7A7F87; font-size:26rpx; }
.byline-dot { width:5rpx; height:5rpx; border-radius:50%; background:#B8BBC0; }
.article-rule { height:2rpx; margin:30rpx 0 34rpx; background:#ECEDEF; position:relative; }
.article-rule span { position:absolute; left:0; top:0; width:76rpx; height:4rpx; background:#A31219; transform:translateY(-1rpx); }
.article-body { max-width:68ch; margin:0 auto; }
.news-para { margin:0 0 28rpx; color:#2F3338; font-family:"Noto Serif SC","Songti SC",SimSun,serif; font-size:32rpx; line-height:1.9; text-align:justify; }
.news-para.lead { color:#202327; font-size:32rpx; font-weight:600; }
.news-sign { display:flex; flex-direction:column; align-items:flex-end; gap:7rpx; margin-top:44rpx; padding-top:28rpx; border-top:1rpx solid #F0F1F2; }
.ns-org { color:#383C42; font-size:28rpx; font-weight:700; }
.ns-date { color:#858A92; font-size:26rpx; }

.news-actions { display:grid; grid-template-columns:1fr 1.25fr; gap:16rpx; margin-top:2rpx; }
.news-btn { width:100%; height:88rpx; border-radius:14rpx; font-size:32rpx; font-weight:700; }
.news-btn.primary { border:0; background:#A31219; color:#fff; box-shadow:0 7rpx 18rpx rgba(163,18,25,.2); }
.news-btn.ghost { background:#fff; color:#A31219; border:2rpx solid #D5A5A8; }
.news-btn:active { transform:translateY(1rpx); opacity:.9; }
.news-btn:disabled { opacity:.55; }
.news-home { align-self:center; min-width:220rpx; height:68rpx; border:0; background:transparent; color:#6F747C; font-size:28rpx; font-weight:600; }

.news-empty { width:100%; max-width:760px; margin:0 auto; padding:96rpx 18rpx; box-sizing:border-box; }
.ne-card { background:#fff; border:1rpx solid #E5E7EB; border-radius:20rpx; padding:58rpx 36rpx; box-shadow:0 10rpx 34rpx rgba(35,39,47,.07); text-align:center; }
.ne-flag { font-size:38rpx; font-weight:800; color:#A31219; }
.ne-text { margin:18rpx 0 34rpx; font-size:32rpx; color:#70757D; }

@media (max-width:480px) {
  .news-paper { padding:40rpx 30rpx 38rpx; }
  .news-title { font-size:42rpx; }
}
</style>
