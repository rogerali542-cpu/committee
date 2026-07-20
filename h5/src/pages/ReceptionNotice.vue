<template>
  <!-- 根类 .recep-notice 是软路由硬跳兜底的落地哨兵（同 ReceptionDetail 的 .recep-detail）：
       挂在根上，进页即有、不等接口 -->
  <div class="page recep-notice" style="overflow-y:auto;">
    <PageNav title="接待安排">
      <!-- replace 不把接待安排页留在历史栈里，之后从处理页返回不会误入这里。 -->
      <template #left><button class="notice-back" type="button" aria-label="返回接待中心" @click="backToReception">‹</button></template>
    </PageNav>

    <div v-if="loadErr" class="page-empty">{{ loadErr }}</div>
    <template v-else>
      <!-- 0717 用户重定位：接待时间/地点在制度里有基本值（首页卡片展示的就是它），
           本页只干一件事——临时调整时改时间/地点，预览生成的公告并导出去张贴。
           0717 追加：时间拆成「周几多选 + 起止时间」两个结构化的框（自由文本老人写不齐格式）；
           公告正文改说话口吻（类会议通知），所以多了个「调整原因（选填）」。 -->
      <div class="sec-card">
        <div class="field">
          <label class="f-label">接待时间</label>
          <div class="reception-time-line">
            <select v-model="form.day" class="f-select day-select" :disabled="!canManage">
              <option value="">周几</option>
              <option v-for="d in DAYS" :key="d" :value="d">{{ d }}</option>
            </select>
            <!-- 起止时间不用系统时间控件（会冒 AM/PM），使用半小时一档的下拉。 -->
            <div class="time-range">
              <select v-model="form.start" class="f-select t-input" :disabled="!canManage">
                <option value="">开始</option>
                <option v-if="form.start && !TIME_OPTS.includes(form.start)" :value="form.start">{{ form.start }}</option>
                <option v-for="t in TIME_OPTS" :key="t" :value="t">{{ t }}</option>
              </select>
              <span class="tr-sep">至</span>
              <select v-model="form.end" class="f-select t-input" :disabled="!canManage">
                <option value="">结束</option>
                <option v-if="form.end && !TIME_OPTS.includes(form.end)" :value="form.end">{{ form.end }}</option>
                <option v-for="t in TIME_OPTS" :key="t" :value="t">{{ t }}</option>
              </select>
            </div>
          </div>
        </div>

        <div class="field">
          <label class="f-label">接待地点</label>
          <input v-model="form.place" class="f-input" maxlength="60" :disabled="!canManage" />
        </div>

        <div class="field">
          <label class="f-label">接待人员</label>
          <select v-model="form.person" class="f-select" :disabled="!canManage">
            <option value="">请选择委员</option>
            <option v-if="form.person && !committeeRoster.some(m => m.name === form.person)" :value="form.person">{{ form.person }}</option>
            <option v-for="m in committeeRoster" :key="m.id || m.name" :value="m.name">
              {{ m.name }}<template v-if="m.role">（{{ m.role }}）</template>
            </option>
          </select>
        </div>

        <div class="field">
          <label class="f-label">调整原因（选填，写了公告会带上）</label>
          <input v-model="form.reason" class="f-input" maxlength="60" :disabled="!canManage" />
        </div>
        <div v-if="!canManage" class="sec-hint">你没有接待管理权限，只能查看。如需修改请联系主任。</div>
        <button v-if="canManage" class="confirm-adjust" type="button"
                :disabled="saving || !dirty || !timeText" @click="confirmAdjustment">
          {{ saving ? '正在保存…' : '确定' }}
        </button>
      </div>

      <!-- 公告预览：本页的主体。跟着上面的框实时变，所见即所出。
           ⚠ 句子必须跟后端 ReceptionNoticePdfService 逐字一致——预览就是那张纸 -->
      <div class="sec-card">
        <div class="preview-card-head">
          <div class="sec-title">公告预览</div>
          <button class="preview-toggle" type="button" @click="previewOpen = !previewOpen">
            {{ previewOpen ? '收起' : '展开' }}
          </button>
        </div>
        <div v-if="previewOpen" class="preview">
          <div class="pv-title">业主接待日公告</div>
          <div class="pv-org">{{ orgName }}</div>
          <div class="pv-line"></div>
          <div class="pv-greet">敬告各位业主：</div>
          <div v-for="(p, i) in noticeParas" :key="i" class="pv-para" :class="{ 'no-indent': p.noIndent }">{{ p.text }}</div>
          <div class="pv-para">欢迎广大业主届时前来反映问题、提出建议。</div>
          <div class="pv-sign">
            <div>{{ orgName }}</div>
            <div>{{ todayText }}</div>
          </div>
        </div>
        <!-- 导出前自动保存改动：填完直接导出是老人最自然的路径，不该被「先保存」拦一道 -->
        <div v-if="exportSuccess" class="export-success">已导出PDF文件，可打印通知</div>
        <button v-if="canManage" class="big-action" :disabled="exporting || !saved.timeDesc" @click="exportPdf">
          {{ exporting ? '正在生成…' : '导出为 PDF' }}
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
const exportSuccess = ref(false)
const saving = ref(false)
const previewOpen = ref(true)
const orgName = ref('业主委员会')
const committeeRoster = ref([])

const TIME_OPTS = Array.from({ length: 25 }, (_, i) => {
  const minutes = (8 * 60) + i * 30
  return String(Math.floor(minutes / 60)).padStart(2, '0') + ':' + String(minutes % 60).padStart(2, '0')
})
const form = reactive({ day: '', start: '', end: '', place: '', person: '', reason: '' })
// saved 是已点击「确定」的公告快照；编辑 form 不会直接改变下方公告。
const saved = reactive({ timeDesc: '', place: '', person: '', reason: '' })

function backToReception() {
  window.location.replace('/reception-center')
}

/** 组合后的时间文案，如「每周二 15:00—17:00」；没填齐返回空 */
const timeText = computed(() => {
  if (!form.day || !form.start || !form.end) return ''
  return '每' + form.day + ' ' + form.start + '—' + form.end
})

const dirty = computed(() =>
  timeText.value !== saved.timeDesc || form.place !== saved.place ||
  form.person !== saved.person || form.reason !== saved.reason)

/** 公告段落。时间、地点、人员分别顶格成行；说明及后续正文保持正文缩进。 */
const noticeParas = computed(() => {
  const time = saved.timeDesc || '未填写'
  const place = saved.place || '未填写'
  const person = saved.person || '未填写'
  const reason = saved.reason.trim()
  if (reason) {
    return [
      { text: orgName.value + '因' + reason + '，需要调整近期的业主接待安排。', noIndent: false },
      { text: '接待时间调整为：' + time, noIndent: false },
      { text: '接待地点为：' + place, noIndent: false },
      { text: '接待人员为：' + person, noIndent: false },
      { text: '给您带来不便，敬请谅解。', noIndent: false }
    ]
  }
  return [
    { text: orgName.value + '现将业主接待安排公告如下：', noIndent: false },
    { text: '接待时间为：' + time, noIndent: false },
    { text: '接待地点为：' + place, noIndent: false },
    { text: '接待人员为：' + person, noIndent: false }
  ]
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
      if (DAYS.includes(d) && !form.day) form.day = d
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
    const [sys, members] = await Promise.all([
      api.receptionSystem(),
      api.committeeMembers().catch(() => [])
    ])
    committeeRoster.value = members || []
    saved.timeDesc = (sys && sys.timeDesc) || ''
    form.place = saved.place = (sys && sys.place) || ''
    form.person = saved.person = (sys && sys.person) || ''
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

/** 点击确定后才保存，并以已保存的数据生成下方的新公告。 */
async function confirmAdjustment() {
  if (saving.value) return
  if (!timeText.value) { toast({ title: '请先选择星期和起止时间', icon: 'none' }); return }
  saving.value = true
  try {
    const place = form.place.trim()
    const person = form.person.trim()
    const reason = form.reason.trim()
    await api.receptionUpdateSystem({
      timeDesc: timeText.value,
      place,
      person,
      adjustReason: reason,
      published: true
    })
    saved.timeDesc = timeText.value
    saved.place = form.place = place
    saved.person = form.person = person
    saved.reason = form.reason = reason
    previewOpen.value = true
    toast({ title: '接待安排已保存', icon: 'success' })
  } catch (e) {
    toast({ title: (e && e.message) || '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

/** PDF 只导出已经点击确定保存的公告，未确认的编辑不会混入。 */
async function exportPdf() {
  if (exporting.value) return
  if (!saved.timeDesc) { toast({ title: '请先确认接待安排', icon: 'none' }); return }
  exportSuccess.value = false
  exporting.value = true
  try {
    const blob = await api.receptionExportNotice()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = orgName.value + '-业主接待日公告.pdf'
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    exportSuccess.value = true
  } catch (e) {
    toast({ title: (e && e.message) || '导出失败', icon: 'none' })
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.page { background: var(--c-bg-page); min-height: 100vh; }
.notice-back { width: 96rpx; height: 124rpx; display: flex; align-items: center; justify-content: center;
  padding: 0; border: 0; background: transparent; color: #fff; font-size: 66rpx; font-weight: 700; }
.page-empty { padding: 120rpx 40rpx; text-align: center; color: var(--c-text-weak); font-size: 32rpx; }
/* 本页字号一律 ≥28rpx(14px)，跟接待处理页同口径 */
.sec-card { margin: 20rpx 24rpx; padding: 26rpx 28rpx; background: var(--c-bg-card);
  border: 2rpx solid #EEF2F4; border-radius: 22rpx; box-shadow: 0 10rpx 28rpx rgba(20,42,58,0.07); }
.sec-title { font-size: 32rpx; font-weight: 700; color: var(--c-text-strong); margin-bottom: 16rpx; }
.preview-card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.preview-card-head .sec-title { margin-bottom: 0; }
.preview-toggle { display: inline-flex; align-items: center; gap: 6rpx; padding: 8rpx 4rpx 8rpx 18rpx;
  border: 0; background: transparent; color: var(--c-primary-dark); font-size: 32rpx; font-weight: 600; }
.sec-hint { margin-top: 4rpx; font-size: 32rpx; line-height: 1.5; color: var(--c-text-weak); }

.field { margin-bottom: 24rpx; }
.field:last-of-type { margin-bottom: 6rpx; }
.f-label { display: block; margin-bottom: 10rpx; font-size: 32rpx; font-weight: 700; color: var(--c-text-mid); }
.f-input { width: 100%; box-sizing: border-box; height: 88rpx; padding: 0 20rpx;
  border: 2rpx solid #E3E8EB; border-radius: 16rpx; background: #FCFDFD;
  font-size: 32rpx; color: var(--c-text-strong); outline: none; }
.f-input:focus { border-color: var(--c-border-focus); }
.f-input:disabled { background: #F4F5F7; color: var(--c-text-weak); }
.f-select { width:100%; box-sizing:border-box; height:88rpx; padding:0 20rpx; border:2rpx solid #E3E8EB;
  border-radius:16rpx; background:#FCFDFD; font-size:32rpx; color:var(--c-text-strong); outline:none; }
.f-select:disabled { background:#F4F5F7; color:var(--c-text-weak); }
.reception-time-line { display: flex; align-items: center; gap: 12rpx; }
.reception-time-line .day-select { flex: 0 0 29%; min-width: 0; }
.reception-time-line .time-range { flex: 1; min-width: 0; }
.confirm-adjust { display: block; width: 42%; height: 76rpx; margin: 24rpx 0 2rpx auto; border: 0;
  border-radius: 16rpx; background: var(--c-primary-dark); color: #fff; font-size: 32rpx; font-weight: 700; }
.confirm-adjust:disabled { opacity: 0.42; }
.confirm-adjust:active:not(:disabled) { background: var(--c-primary-strong); }


/* 周几多选：胶囊 chips，点了变主色。选中态要够醒目，老人得一眼看出哪几天亮着 */
.day-chips { display: flex; flex-wrap: wrap; gap: 12rpx; margin-bottom: 14rpx; }
.day-chip { padding: 12rpx 22rpx; border-radius: 999rpx; border: 2rpx solid #E3E8EB;
  background: #FCFDFD; font-size: 32rpx; font-weight: 600; color: var(--c-text-mid);
  cursor: pointer; user-select: none; }
.day-chip.on { background: var(--c-primary-dark); border-color: var(--c-primary-dark); color: #fff; }
.day-chip.dim { cursor: default; opacity: 0.7; }
.day-chip:active { opacity: 0.75; }

/* 起止时间：两个 time 输入并排，中间「至」 */
.time-range { display: flex; align-items: center; gap: 14rpx; }
.t-input { flex: 1; min-width: 0; }
.tr-sep { flex-shrink: 0; font-size: 32rpx; color: var(--c-text-mid); }

/* 0717 用户定：导出按钮缩小 20%（高 96→76）、宽度 60% 居中。
   字号 32→28 没砍满 20%——28rpx 是本页字号下限，破线老人看不清 */
.big-action { display: block; width: 60%; height: 76rpx; margin: 18rpx auto 0; border: none; border-radius: 18rpx;
  font-size: 32rpx; font-weight: 700; color: #fff; background: #A94832;
  box-shadow: 0 6rpx 16rpx rgba(114,48,34,0.18); }
.big-action:active { background: #8F3B2A; }
.big-action:disabled { opacity: 0.5; }
.export-success { margin: 18rpx 0 -6rpx; text-align: center; color: #278653; font-size: 28rpx; line-height: 1.5; }

/* 纸样预览：让委员在按下导出前就知道印出来长什么样。
   白底居中排版，刻意跟 App 的卡片风格不一样——它代表"那张纸"。
   0717 用户定：做高一点（min-height + flex 让落款沉底），更像一页纸 */
.preview { display: flex; flex-direction: column; min-height: 640rpx; padding: 44rpx 32rpx; background: #fff;
  border: 2rpx solid #E3E8EB; border-radius: 12rpx; }
.pv-title { text-align: center; font-size: 34rpx; font-weight: 800; color: #1F2329; letter-spacing: 2rpx; }
.pv-org { margin-top: 8rpx; text-align: center; font-size: 32rpx; color: var(--c-text-mid); }
.pv-line { margin: 14rpx 0 22rpx; height: 2rpx; background: #1F2329; }
.pv-greet { font-size: 32rpx; line-height: 1.7; color: var(--c-text-strong); }
/* 正文首行缩进两字 + 1.7 行距，念出来像一封告示 */
.pv-para { margin-top: 10rpx; font-size: 32rpx; line-height: 1.7; color: var(--c-text-strong); text-indent: 2em; }
.pv-para.no-indent { text-indent: 0; }
.pv-sign { margin-top: auto; padding-top: 40rpx; text-align: right; font-size: 32rpx; line-height: 1.8; color: var(--c-text-mid); }

.bottom-space { height: 60rpx; }
</style>
