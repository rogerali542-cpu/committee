<template>
  <!-- 根类 .recep-notice 是软路由硬跳兜底的落地哨兵（同 ReceptionDetail 的 .recep-detail）：
       挂在根上，进页即有、不等接口 -->
  <div class="page recep-notice" style="overflow-y:auto;">
    <PageNav title="接待安排">
      <template #left><button class="notice-back" type="button" aria-label="返回上一页" @click="backToReception">‹</button></template>
      <template #right><button class="nav-home-btn" @click="goModuleHome('reception')">首页</button></template>
    </PageNav>

    <div v-if="loadErr" class="page-empty">{{ loadErr }}</div>
    <template v-else>
      <!-- 0717 用户重定位：接待时间/地点在制度里有基本值（首页卡片展示的就是它），
           本页只干一件事——临时调整时改时间/地点，预览生成的公告并导出去张贴。
           0717 追加：时间拆成「周几单选 + 起止时间」两个结构化的框（自由文本老人写不齐格式）；
           公告正文改说话口吻（类会议通知），所以多了个「调整原因（选填）」。 -->
      <div class="sec-card">
        <div class="field">
          <label class="f-label">接待时间</label>
          <div class="reception-time-line">
            <select v-model="form.day" class="f-select day-select" :disabled="!canManage">
              <option value="">周几</option>
              <option v-for="d in DAYS" :key="d" :value="d">{{ d }}</option>
            </select>
            <!-- 小时与分钟分开选择，避免手机端出现过长的时间列表。 -->
            <div class="time-range">
              <div class="time-pair">
                <select v-model="startHour" class="f-select t-part" aria-label="开始小时" :disabled="!canManage">
                  <option value="">时</option>
                  <option v-for="h in HOUR_OPTS" :key="'sh-' + h" :value="h">{{ h }}</option>
                </select>
                <span class="time-colon">:</span>
                <select v-model="startMinute" class="f-select t-part minute-part" aria-label="开始分钟" :disabled="!canManage">
                  <option value="">分</option>
                  <option v-for="m in MINUTE_OPTS" :key="'sm-' + m" :value="m">{{ m }}</option>
                </select>
              </div>
              <span class="tr-sep">至</span>
              <div class="time-pair">
                <select v-model="endHour" class="f-select t-part" aria-label="结束小时" :disabled="!canManage">
                  <option value="">时</option>
                  <option v-for="h in HOUR_OPTS" :key="'eh-' + h" :value="h">{{ h }}</option>
                </select>
                <span class="time-colon">:</span>
                <select v-model="endMinute" class="f-select t-part minute-part" aria-label="结束分钟" :disabled="!canManage">
                  <option value="">分</option>
                  <option v-for="m in MINUTE_OPTS" :key="'em-' + m" :value="m">{{ m }}</option>
                </select>
              </div>
            </div>
          </div>
        </div>

        <div class="field">
          <label class="f-label">接待地点</label>
          <input v-model="form.place" class="f-input" maxlength="60" :disabled="!canManage" />
        </div>

        <div class="field">
          <label class="f-label">接待人员</label>
          <select v-model="form.person" class="f-select" :class="{ placeholder: !form.person }" :disabled="!canManage">
            <!-- hidden:仅作占位提示,不出现在下拉列表里(0725 用户定) -->
            <option value="" disabled hidden>请选择接待人员</option>
            <option v-for="m in receptionMembers" :key="m.id || m.name" :value="m.name">
              {{ m.name }}<template v-if="m.role"> · {{ m.role }}</template>
            </option>
          </select>
        </div>

        <div class="field">
          <label class="f-label">调整原因（选填）</label>
          <input v-model="form.reason" class="f-input" maxlength="60" :disabled="!canManage" />
        </div>
        <div v-if="!canManage" class="sec-hint">你没有接待管理权限，只能查看。如需修改请联系主任。</div>
        <!-- 灰态给出原因文案（0723）：老人首次进页看到灰按钮不知为何点不动 -->
        <button v-if="canManage" class="confirm-adjust" type="button"
                :disabled="saving || !dirty || !timeText" @click="confirmAdjustment">
          {{ saving ? '正在保存…' : (!dirty ? '未做修改' : '保存') }}
        </button>
      </div>

      <!-- 公告预览：本页的主体。跟着上面的框实时变，所见即所出。
           ⚠ 句子必须跟后端 ReceptionNoticePdfService 逐字一致——预览就是那张纸 -->
      <div class="sec-card">
        <div class="preview-card-head">
          <div class="sec-title">公告预览</div>
          <button class="preview-toggle" type="button" @click="previewOpen = !previewOpen">
            {{ previewOpen ? '收起' : '查看' }}
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
            <div>{{ orgFullName }}</div>
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
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import perm from '@/utils/perm'
import { toast } from '@/utils/ui'
import { navigateBack, goModuleHome } from '@/utils/navigate'

const DAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const canManage = ref(false)
const loadErr = ref('')
const exporting = ref(false)
const exportSuccess = ref(false)
const saving = ref(false)
const previewOpen = ref(false)
const orgName = ref('业主委员会')
// 落款全称（含区划+届别，0723 与会议文书统一）：后端 orgFullName，取不到退 orgName
const orgFullName = ref('业主委员会')
const committeeRoster = ref([])
// 接待人员=业委会成员(0725 用户定:主任/副主任/委员都算,不只委员),排除秘书等非成员岗
const RECEPTION_ROLES = ['主任', '副主任', '委员']
const receptionMembers = computed(() =>
  committeeRoster.value.filter(member => RECEPTION_ROLES.includes(String(member.role || '').trim()))
)

const HOUR_OPTS = Array.from({ length: 12 }, (_, i) => String(i + 10).padStart(2, '0'))
const MINUTE_OPTS = ['00', '30']
const form = reactive({ day: '', start: '', end: '', place: '', person: '', reason: '' })
function timePart(field, part) {
  return computed({
    get: () => {
      const pieces = String(form[field] || '').split(':')
      return pieces.length === 2 ? pieces[part] : ''
    },
    set: (value) => {
      const pieces = String(form[field] || '').split(':')
      const hour = part === 0 ? value : (pieces[0] || '')
      const minute = part === 1 ? value : (pieces[1] || '')
      form[field] = hour || minute ? hour + ':' + minute : ''
    }
  })
}
const startHour = timePart('start', 0)
const startMinute = timePart('start', 1)
const endHour = timePart('end', 0)
const endMinute = timePart('end', 1)

// 接待默认时长 1 小时(0725 用户定):改起始时间自动把结束设为起始+1小时,
// 特殊时长用户之后手动改结束即可。初始加载已存数据时不覆盖(formLoaded 守卫)。
let formLoaded = false
watch(() => form.start, (val) => {
  if (!formLoaded) return
  const m = /^(\d{1,2}):(\d{2})$/.exec(String(val || ''))
  if (!m) return
  let hh = Number(m[1]) + 1
  const maxHour = Number(HOUR_OPTS[HOUR_OPTS.length - 1]) // 21
  if (hh > maxHour) hh = maxHour                          // 越界则夹到最晚,避免出现无效结束时间
  form.end = String(hh).padStart(2, '0') + ':' + m[2]
})
// saved 是已点击「确定」的公告快照；编辑 form 不会直接改变下方公告。
const saved = reactive({ timeDesc: '', place: '', person: '', reason: '' })


// 导航新规(0725 用户定):返回=历史上一页(驾驶舱「修改安排」/接待首页 push 进入,回退天然回来处)
function backToReception() {
  navigateBack()
}

/** 组合后的时间文案，如「每周二 15:00—17:00」；没填齐返回空 */
const timeText = computed(() => {
  const validTime = value => /^(1\d|20|21):(00|30)$/.test(value)
  if (!form.day || !validTime(form.start) || !validTime(form.end)) return ''
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
function conciseTimeDesc(value) {
  return String(value || '').replace(/[，,、]?\s*法定节假日暂停.*$/, '').trim()
}

async function load() {
  canManage.value = perm.can('reception.manage')
  try {
    const [sys, members] = await Promise.all([
      api.receptionSystem(),
      api.committeeMembers().catch(() => [])
    ])
    committeeRoster.value = members || []
    saved.timeDesc = conciseTimeDesc(sys && sys.timeDesc)
    form.place = saved.place = (sys && sys.place) || ''
    // “业委会委员轮值”只作为占位提示，进入页面后由用户选择具体轮值委员。
    form.person = saved.person = ''
    // 调整原因只针对本次公告，不沿用上一次保存的临时原因。
    form.reason = saved.reason = ''
    parseTimeDesc(saved.timeDesc)
    // 抬头直接用后端算好的整串，不在这儿拼。后端 ReceptionService.noticeOrgName() 是唯一实现，
    // PDF 也调它 —— 预览和印出来的纸因此不可能不一致。
    // （别改回「取 communityName 自己拼」：库里那个名字现在是坏的，存着 4 个 '?'，
    //   前端拼就会显示「????业主委员会」，而 PDF 那边被兜底成了「业主委员会」）
    if (sys && sys.orgName) orgName.value = sys.orgName
    orgFullName.value = (sys && sys.orgFullName) || orgName.value
  } catch (e) {
    loadErr.value = (e && e.message) || '接待安排加载失败'
  } finally {
    // 数据回填完成后再启用"改起始自动调结束",避免加载已存时长时被 1 小时覆盖
    nextTick(() => { formLoaded = true })
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
:deep(.page-nav) { background: #2f6b45; }  /* 接待模块页头（规范三色制） */
.page { background: var(--c-bg-page); min-height: 100vh; }
.notice-back { width: 96rpx; height: 124rpx; display: flex; align-items: center; justify-content: center;
  padding: 0; border: 0; background: transparent; color: #fff; font-size: 66rpx; font-weight: 700; }
.nav-home-btn { display: inline-flex; align-items: center; height: 64rpx; margin-right: 20rpx; padding: 0 24rpx; border: 2rpx solid rgba(255,255,255,0.6); border-radius: 34rpx; background: rgba(255,255,255,0.12); color: #fff; font-size: 30rpx; font-weight: 600; line-height: 1; }
.nav-home-btn:active { background: rgba(255,255,255,0.28); }
.page-empty { padding: 120rpx 40rpx; text-align: center; color: var(--c-text-weak); font-size: 30rpx; }
/* 本页字号一律 ≥28rpx(14px)，跟接待处理页同口径 */
.sec-card { margin: 20rpx 24rpx; padding: 26rpx 28rpx; background: var(--c-bg-card);
  border: 2rpx solid #EEF2F4; border-radius: 22rpx; box-shadow: 0 10rpx 28rpx rgba(20,42,58,0.07); }
.sec-title { font-size: 32rpx; font-weight: 700; color: var(--c-text-strong); margin-bottom: 16rpx; }
.preview-card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.preview-card-head .sec-title { margin-bottom: 0; }
.preview-toggle { display: inline-flex; align-items: center; gap: 6rpx; padding: 8rpx 4rpx 8rpx 18rpx;
  border: 0; background: transparent; color: var(--c-primary-dark); font-size: 28rpx; font-weight: 600; }
.sec-hint { margin-top: 4rpx; font-size: 28rpx; line-height: 1.5; color: var(--c-text-weak); }

.field { margin-bottom: 24rpx; }
.field:last-of-type { margin-bottom: 6rpx; }
.f-label { display: block; margin-bottom: 10rpx; font-size: 28rpx; font-weight: 700; color: var(--c-text-mid); }
.f-input { width: 100%; box-sizing: border-box; height: 88rpx; padding: 0 20rpx;
  border: 2rpx solid #E3E8EB; border-radius: 16rpx; background: #FCFDFD;
  font-size: 30rpx; color: var(--c-text-strong); outline: none; }
.f-input:focus { border-color: var(--c-border-focus); }
.f-input:disabled { background: #F4F5F7; color: var(--c-text-weak); }
.f-select { width:100%; box-sizing:border-box; height:88rpx; padding:0 20rpx; border:2rpx solid #E3E8EB;
  border-radius:16rpx; background:#FCFDFD; font-size:30rpx; color:var(--c-text-strong); outline:none; }
.f-select.placeholder { color: var(--c-text-weak); }
.f-select:disabled { background:#F4F5F7; color:var(--c-text-weak); }
.reception-time-line { display: flex; align-items: center; gap: 12rpx; }
.reception-time-line .day-select { flex: 0 0 29%; min-width: 0; }
.reception-time-line .time-range { flex: 1; min-width: 0; }
.confirm-adjust { display: block; width: 42%; height: 76rpx; margin: 24rpx 0 2rpx auto; border: 0;
  border-radius: 16rpx; background: var(--c-primary-dark); color: #fff; font-size: 30rpx; font-weight: 700; }
.confirm-adjust:disabled { opacity: 0.42; }
.confirm-adjust:active:not(:disabled) { background: var(--c-primary-strong); }


/* .day-chips/.day-chip* 死样式已删（0723）：周几为单选下拉，多选胶囊从未上线 */

/* 起止时间：两个 time 输入并排，中间「至」 */
.time-range { display: flex; align-items: center; gap: 14rpx; }
.time-pair { display: flex; flex: 1; min-width: 0; align-items: center; gap: 5rpx; }
.t-part { flex: 1; min-width: 0; padding: 0 8rpx; text-align: center; }
.minute-part { flex: 0 0 43%; }
.time-colon { flex-shrink: 0; color: var(--c-text-mid); font-size: 30rpx; }
.tr-sep { flex-shrink: 0; font-size: 28rpx; color: var(--c-text-mid); }

/* 0717 用户定：导出按钮缩小 20%（高 96→76）、宽度 60% 居中。
   字号 32→28 没砍满 20%——28rpx 是本页字号下限，破线老人看不清 */
.big-action { display: block; width: 60%; height: 76rpx; margin: 48rpx auto 0; border: none; border-radius: 18rpx;
  font-size: 30rpx; font-weight: 700; color: #fff; background: #A94832;
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
.pv-org { margin-top: 8rpx; text-align: center; font-size: 28rpx; color: var(--c-text-mid); }
.pv-line { margin: 14rpx 0 22rpx; height: 2rpx; background: #1F2329; }
.pv-greet { font-size: 29rpx; line-height: 1.7; color: var(--c-text-strong); }
/* 正文首行缩进两字 + 1.7 行距，念出来像一封告示 */
.pv-para { margin-top: 10rpx; font-size: 29rpx; line-height: 1.7; color: var(--c-text-strong); text-indent: 2em; }
.pv-para.no-indent { text-indent: 0; }
.pv-sign { margin-top: auto; padding-top: 40rpx; text-align: right; font-size: 28rpx; line-height: 1.8; color: var(--c-text-mid); }

.bottom-space { height: 60rpx; }
</style>
