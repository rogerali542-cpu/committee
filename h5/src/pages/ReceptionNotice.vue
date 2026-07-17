<template>
  <!-- 根类 .recep-notice 是软路由硬跳兜底的落地哨兵（同 ReceptionDetail 的 .recep-detail）：
       挂在根上，进页即有、不等接口 -->
  <div class="page recep-notice" style="overflow-y:auto;">
    <PageNav title="接待安排" back-to="/main?tab=reception" />

    <div v-if="loadErr" class="page-empty">{{ loadErr }}</div>
    <template v-else>
      <!-- 0717 用户重定位：接待时间/地点在制度里有基本值（首页卡片展示的就是它），
           本页只干一件事——临时调整时改时间/地点，预览生成的公告并导出去张贴。
           0717 追加：时间拆成「周几多选 + 起止时间」两个结构化的框（自由文本老人写不齐格式）；
           公告正文改说话口吻（类会议通知），所以多了个「调整原因（选填）」。 -->
      <div class="sec-card">
        <div class="field">
          <label class="f-label">接待时间（可选多天）</label>
          <div class="day-chips">
            <span v-for="d in DAYS" :key="d" class="day-chip"
                  :class="{ on: form.days.includes(d), dim: !canManage }"
                  @click="toggleDay(d)">{{ d }}</span>
          </div>
          <div class="time-range">
            <input v-model="form.start" type="time" class="f-input t-input" :disabled="!canManage" />
            <span class="tr-sep">至</span>
            <input v-model="form.end" type="time" class="f-input t-input" :disabled="!canManage" />
          </div>
        </div>

        <div class="field">
          <label class="f-label">接待地点</label>
          <input v-model="form.place" class="f-input" maxlength="60" :disabled="!canManage" />
        </div>

        <div class="field">
          <label class="f-label">调整原因（选填，写了公告会带上）</label>
          <input v-model="form.reason" class="f-input" maxlength="60" :disabled="!canManage" />
        </div>
        <div v-if="!canManage" class="sec-hint">你没有接待管理权限，只能查看。如需修改请联系主任。</div>
      </div>

      <!-- 公告预览：本页的主体。跟着上面的框实时变，所见即所出。
           ⚠ 句子必须跟后端 ReceptionNoticePdfService 逐字一致——预览就是那张纸 -->
      <div class="sec-card">
        <div class="sec-title">公告预览</div>
        <div class="preview">
          <div class="pv-title">业主接待日公告</div>
          <div class="pv-org">{{ orgName }}</div>
          <div class="pv-line"></div>
          <div class="pv-greet">敬告各位业主：</div>
          <div class="pv-para">{{ noticeBody }}</div>
          <div class="pv-para">欢迎广大业主届时前来反映问题、提出建议。</div>
          <div class="pv-sign">
            <div>{{ orgName }}</div>
            <div>{{ todayText }}</div>
          </div>
        </div>
        <!-- 导出前自动保存改动：填完直接导出是老人最自然的路径，不该被「先保存」拦一道 -->
        <button v-if="canManage" class="big-action" :disabled="exporting || !timeText" @click="exportPdf">
          {{ exporting ? '正在生成…' : '导出公告 PDF，去打印' }}
        </button>
      </div>

      <div class="bottom-space"></div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import perm from '@/utils/perm'
import { toast } from '@/utils/ui'

const DAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const canManage = ref(false)
const loadErr = ref('')
const exporting = ref(false)
const orgName = ref('业主委员会')

const form = reactive({ days: [], start: '', end: '', place: '', reason: '' })
// saved 镜像服务端当前值（组合后的 timeDesc + place + reason），导出前判断要不要先落库
const saved = reactive({ timeDesc: '', place: '', reason: '' })

function toggleDay(d) {
  if (!canManage.value) return
  const i = form.days.indexOf(d)
  if (i >= 0) form.days.splice(i, 1)
  else form.days.push(d)
}

/** 组合后的时间文案，如「每周二、周四 14:00—15:00」；没填齐返回空 */
const timeText = computed(() => {
  if (!form.days.length || !form.start || !form.end) return ''
  const days = DAYS.filter(d => form.days.includes(d)) // 按周一→周日排序，跟点选顺序无关
  return '每' + days.join('、') + ' ' + form.start + '—' + form.end
})

const dirty = computed(() =>
  timeText.value !== saved.timeDesc || form.place !== saved.place || form.reason !== saved.reason)

/** 公告正文。句式跟后端 PDF 完全一致：有原因走「调整」口吻，没原因走平铺通告 */
const noticeBody = computed(() => {
  const time = timeText.value || '未填写'
  const place = form.place || '未填写'
  const reason = form.reason.trim()
  return reason
    ? orgName.value + '因' + reason + '，需要调整近期的业主接待时间。调整后的接待时间为：'
      + time + '；接待地点仍为：' + place + '。给您带来不便，敬请谅解。'
    : orgName.value + '现将业主接待安排公告如下：接待时间为：' + time + '；接待地点为：' + place + '。'
})

const todayText = computed(() => {
  const d = new Date()
  return d.getFullYear() + ' 年 ' + (d.getMonth() + 1) + ' 月 ' + d.getDate() + ' 日'
})

/** 把存量 timeDesc（自由文本）拆回结构化字段。拆不动就空着让用户重选，不硬猜 */
function parseTimeDesc(s) {
  const str = String(s || '')
  const dayPart = str.match(/每?\s*((?:周[一二三四五六日天][、，,\s]*)+)/)
  if (dayPart) {
    for (const m of dayPart[1].match(/周[一二三四五六日天]/g) || []) {
      const d = m === '周天' ? '周日' : m
      if (DAYS.includes(d) && !form.days.includes(d)) form.days.push(d)
    }
  }
  const t = str.match(/(\d{1,2}:\d{2})\s*[—\-~至]+\s*(\d{1,2}:\d{2})/)
  if (t) { form.start = pad(t[1]); form.end = pad(t[2]) }
}
// input[type=time] 只认 HH:MM，「9:00」得补成「09:00」
function pad(v) { return v.length === 4 ? '0' + v : v }

async function load() {
  canManage.value = perm.can('reception.manage')
  try {
    const sys = await api.receptionSystem()
    saved.timeDesc = (sys && sys.timeDesc) || ''
    form.place = saved.place = (sys && sys.place) || ''
    form.reason = saved.reason = (sys && sys.adjustReason) || ''
    parseTimeDesc(saved.timeDesc)
    // 抬头直接用后端算好的整串，不在这儿拼。后端 ReceptionService.noticeOrgName() 是唯一实现，
    // PDF 也调它 —— 预览和印出来的纸因此不可能不一致。
    // （别改回「取 communityName 自己拼」：库里那个名字现在是坏的，存着 4 个 '?'，
    //   前端拼就会显示「????业主委员会」，而 PDF 那边被兜底成了「业主委员会」）
    if (sys && sys.orgName) orgName.value = sys.orgName
  } catch (e) {
    loadErr.value = (e && e.message) || '接待安排加载失败'
  }
}
onMounted(load)

/** 导出 = （有改动先自动保存）+ 生成下载。保存失败就不导出，防止印出旧内容 */
async function exportPdf() {
  if (exporting.value) return
  if (!timeText.value) { toast({ title: '请先选周几、填起止时间', icon: 'none' }); return }
  exporting.value = true
  try {
    if (dirty.value) {
      // 不传 person：后端 updateSystem 按 containsKey 更新，历史存的接待人保持原样
      await api.receptionUpdateSystem({
        timeDesc: timeText.value, place: form.place.trim(),
        adjustReason: form.reason.trim(), published: true
      })
      saved.timeDesc = timeText.value
      saved.place = form.place = form.place.trim()
      saved.reason = form.reason = form.reason.trim()
    }
    const blob = await api.receptionExportNotice()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = orgName.value + '-业主接待日公告.pdf'
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    toast({ title: '已导出，去打印吧', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '导出失败', icon: 'none' })
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.page { background: var(--c-bg-page); min-height: 100vh; }
.page-empty { padding: 120rpx 40rpx; text-align: center; color: var(--c-text-weak); font-size: 30rpx; }
/* 本页字号一律 ≥28rpx(14px)，跟接待处理页同口径 */
.sec-card { margin: 20rpx 24rpx; padding: 26rpx 28rpx; background: var(--c-bg-card);
  border: 2rpx solid #EEF2F4; border-radius: 22rpx; box-shadow: 0 10rpx 28rpx rgba(20,42,58,0.07); }
.sec-title { font-size: 32rpx; font-weight: 700; color: var(--c-text-strong); margin-bottom: 16rpx; }
.sec-hint { margin-top: 4rpx; font-size: 28rpx; line-height: 1.5; color: var(--c-text-weak); }

.field { margin-bottom: 24rpx; }
.field:last-of-type { margin-bottom: 6rpx; }
.f-label { display: block; margin-bottom: 10rpx; font-size: 28rpx; font-weight: 700; color: var(--c-text-mid); }
.f-input { width: 100%; box-sizing: border-box; height: 88rpx; padding: 0 20rpx;
  border: 2rpx solid #E3E8EB; border-radius: 16rpx; background: #FCFDFD;
  font-size: 30rpx; color: var(--c-text-strong); outline: none; }
.f-input:focus { border-color: var(--c-border-focus); }
.f-input:disabled { background: #F4F5F7; color: var(--c-text-weak); }

/* 周几多选：胶囊 chips，点了变主色。选中态要够醒目，老人得一眼看出哪几天亮着 */
.day-chips { display: flex; flex-wrap: wrap; gap: 12rpx; margin-bottom: 14rpx; }
.day-chip { padding: 12rpx 22rpx; border-radius: 999rpx; border: 2rpx solid #E3E8EB;
  background: #FCFDFD; font-size: 28rpx; font-weight: 600; color: var(--c-text-mid);
  cursor: pointer; user-select: none; }
.day-chip.on { background: var(--c-primary-dark); border-color: var(--c-primary-dark); color: #fff; }
.day-chip.dim { cursor: default; opacity: 0.7; }
.day-chip:active { opacity: 0.75; }

/* 起止时间：两个 time 输入并排，中间「至」 */
.time-range { display: flex; align-items: center; gap: 14rpx; }
.t-input { flex: 1; min-width: 0; }
.tr-sep { flex-shrink: 0; font-size: 28rpx; color: var(--c-text-mid); }

/* 0717 用户定：导出按钮缩小 20%（高 96→76）、宽度 60% 居中。
   字号 32→28 没砍满 20%——28rpx 是本页字号下限，破线老人看不清 */
.big-action { display: block; width: 60%; height: 76rpx; margin: 18rpx auto 0; border: none; border-radius: 18rpx;
  font-size: 28rpx; font-weight: 700; color: #fff; background: var(--c-primary-dark); }
.big-action:disabled { opacity: 0.5; }

/* 纸样预览：让委员在按下导出前就知道印出来长什么样。
   白底居中排版，刻意跟 App 的卡片风格不一样——它代表"那张纸"。
   0717 用户定：做高一点（min-height + flex 让落款沉底），更像一页纸 */
.preview { display: flex; flex-direction: column; min-height: 640rpx; padding: 44rpx 32rpx; background: #fff;
  border: 2rpx solid #E3E8EB; border-radius: 12rpx; }
.pv-title { text-align: center; font-size: 34rpx; font-weight: 800; color: #1F2329; letter-spacing: 2rpx; }
.pv-org { margin-top: 8rpx; text-align: center; font-size: 28rpx; color: var(--c-text-mid); }
.pv-line { margin: 14rpx 0 22rpx; height: 2rpx; background: #1F2329; }
.pv-greet { font-size: 29rpx; line-height: 1.7; color: var(--c-text-strong); }
/* 正文首行缩进两字 + 1.7 行距，念出来像一封告示 */
.pv-para { margin-top: 10rpx; font-size: 29rpx; line-height: 1.7; color: var(--c-text-strong); text-indent: 2em; }
.pv-sign { margin-top: auto; padding-top: 40rpx; text-align: right; font-size: 28rpx; line-height: 1.8; color: var(--c-text-mid); }

.bottom-space { height: 60rpx; }
</style>
