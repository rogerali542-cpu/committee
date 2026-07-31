<template>
  <!-- 调整接待安排（0731 设计师稿重做）：设置行版式——每行「灰标签 + 黑值 +（原值提示）+ ›」，
       点行改值（周几/人员=选择单，时间=时刻选择单，地点/原因=编辑弹窗）。
       根类 .recep-notice 是软路由硬跳兜底的落地哨兵 -->
  <div class="page recep-notice" style="overflow-y:auto;">
    <div class="rn-hd">
      <div class="rn-hd-bar" @click="backToReception">
        <i class="rn-back"></i>
        <span class="rn-hd-title">调整接待安排</span>
      </div>
    </div>

    <div v-if="loadErr" class="page-empty">{{ loadErr }}</div>
    <template v-else>
      <div class="rn-card">
        <div class="rn-row" :class="{ ro: !canManage }" @click="pickDay">
          <span class="rn-k">每周</span>
          <span class="rn-v">{{ form.day || '未选择' }}</span>
          <span v-if="orig.day && form.day !== orig.day" class="rn-orig">原 {{ orig.day }}</span>
          <i v-if="canManage" class="rn-arr"></i>
        </div>
        <div class="rn-row" :class="{ ro: !canManage }" @click="pickTime('start')">
          <span class="rn-k">开始时间</span>
          <span class="rn-v">{{ form.start || '未选择' }}</span>
          <span v-if="orig.start && form.start !== orig.start" class="rn-orig">原 {{ orig.start }}</span>
          <i v-if="canManage" class="rn-arr"></i>
        </div>
        <div class="rn-row" :class="{ ro: !canManage }" @click="pickTime('end')">
          <span class="rn-k">结束时间</span>
          <span class="rn-v">{{ form.end || '未选择' }}</span>
          <span v-if="orig.end && form.end !== orig.end" class="rn-orig">原 {{ orig.end }}</span>
          <i v-if="canManage" class="rn-arr"></i>
        </div>
        <div class="rn-row" :class="{ ro: !canManage }" @click="pickPlace">
          <span class="rn-k">接待地点</span>
          <span class="rn-v" :class="{ empty: !form.place }">{{ form.place || '未填写' }}</span>
          <i v-if="canManage" class="rn-arr"></i>
        </div>
        <div class="rn-row" :class="{ ro: !canManage }" @click="pickPerson">
          <span class="rn-k">接待人员</span>
          <span class="rn-v" :class="{ empty: !form.person }">{{ form.person || '未指定' }}</span>
          <i v-if="canManage" class="rn-arr"></i>
        </div>
        <div class="rn-row" :class="{ ro: !canManage }" @click="editReason">
          <span class="rn-k">调整原因</span>
          <span class="rn-v" :class="{ empty: !form.reason }">{{ form.reason || '未填写' }}</span>
          <i v-if="canManage" class="rn-arr"></i>
        </div>
      </div>

      <!-- 提前 1 天惯例（0731 用户定；制度汇编未规定时限，此为产品自定规则） -->
      <div v-if="canManage" class="rn-hint">按惯例，接待安排调整请至少提前 1 天公示业主。</div>
      <div v-if="!canManage" class="rn-hint">你没有接待管理权限，只能查看。如需修改请联系主任。</div>

      <!-- 公告预览：轻列表行，点开展开（0731 设计师稿）。⚠ 句子与后端 PDF 逐字一致 -->
      <div class="rn-links">
        <div class="rn-link" @click="previewOpen = !previewOpen">
          <span class="rn-link-t">公告预览</span>
          <i class="rn-chev" :class="{ open: previewOpen }"></i>
        </div>
      </div>
      <div v-if="previewOpen" class="rn-card rn-preview">
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
        <div v-if="exportSuccess" class="export-success">已导出PDF文件，可打印通知</div>
        <button v-if="canManage" class="pv-export" :disabled="exporting || !saved.timeDesc" @click="exportPdf">
          {{ exporting ? '正在生成…' : '导出为 PDF' }}
        </button>
      </div>

      <!-- 底部主按钮（0731 设计师稿）：保存并生成公告——保存成功自动展开公告预览 -->
      <div v-if="canManage" class="rn-bar">
        <button type="button" class="rn-primary" :disabled="saving || !dirty || !timeText" @click="confirmAdjustment">
          {{ saving ? '正在保存…' : (!dirty ? '未做修改' : '保存并生成公告') }}
        </button>
      </div>
    </template>

    <!-- 填写其他地点（0731 设计师定）：输入框不进弹层——键盘一弹会盖掉列表。
         独立全屏层一次只做一件事：绿头 ‹ + 输入卡 + 底部「用这个地点」 -->
    <div v-if="placeInputOpen" class="rn-place-page">
      <div class="rn-hd">
        <div class="rn-hd-bar" @click="backFromPlaceInput">
          <i class="rn-back"></i>
          <span class="rn-hd-title">填写接待地点</span>
        </div>
      </div>
      <div class="pi-body">
        <input ref="placeInputEl" v-model="placeDraft" class="pi-input" type="text" maxlength="30"
          placeholder="例如：3号楼架空层活动室" @keyup.enter="usePlaceDraft" />
      </div>
      <div class="pi-bar">
        <button type="button" class="rn-primary" :disabled="!placeDraft.trim()" @click="usePlaceDraft">用这个地点</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import api from '@/api'
import perm from '@/utils/perm'
import { toast, showModal, showActionSheet } from '@/utils/ui'
import { goModuleHome } from '@/utils/navigate'
import { getStorage, setStorage } from '@/utils/storage'

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

const HOUR_OPTS = Array.from({ length: 14 }, (_, i) => String(i + 8).padStart(2, '0'))   /* 0731 mock：时段从 08:00 起 */
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
// 载入时的原值快照（0731 设计师稿：改动行旁标「原 19:00」，老人看得见改了什么）
const orig = reactive({ day: '', start: '', end: '' })


// 导航新规(0725 用户定):返回=历史上一页(驾驶舱「修改安排」/接待首页 push 进入,回退天然回来处)
function backToReception() {
  // 0731 修 BUG：进本页时软路由偶发「push 了 URL 但视图没切」+300ms 硬跳补层，历史栈被弄脏，
  // history.back() 有时落到夹在中间的 /main（业委会首页）。返回改确定性直达接待首页，
  // 不依赖历史栈（replace 不叠层，落地后再按返回是更早的上一页）
  goModuleHome('reception')
}

/** 组合后的时间文案，如「每周二 15:00—17:00」；没填齐返回空 */
const timeText = computed(() => {
  const validTime = value => /^(0[89]|1\d|2[01]):(00|30)$/.test(value)
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
    // 原值快照：改动后行旁显示「原 X」
    orig.day = form.day; orig.start = form.start; orig.end = form.end
    // 数据回填完成后再启用"改起始自动调结束",避免加载已存时长时被 1 小时覆盖
    nextTick(() => { formLoaded = true })
  }
}
onMounted(load)

// ── 行点击（0731 设计师稿：行版式，点行改值） ──
const TIME_OPTS = HOUR_OPTS.flatMap(h => MINUTE_OPTS.map(m => h + ':' + m))
// 底部选择单（0731 用户定：每周/开始/结束都用弹层）——picker 变体：当前值浅绿高亮+绿勾
async function pickDay() {
  if (!canManage.value) return
  const res = await showActionSheet({ title: '每周几接待', variant: 'picker',
    itemList: DAYS.map(d => ({ label: d, selected: d === form.day })) })
  if (res && res.tapIndex >= 0) form.day = DAYS[res.tapIndex]
}
async function pickTime(field) {
  if (!canManage.value) return
  const res = await showActionSheet({ title: field === 'start' ? '开始时间' : '结束时间', variant: 'picker',
    itemList: TIME_OPTS.map(t => ({ label: t, selected: t === form[field] })) })
  if (res && res.tapIndex >= 0) form[field] = TIME_OPTS[res.tapIndex]
}
// 接待人员=多选（0731 设计师定：可能两人值班；点名字切换选中，底部 取消/确定 落定）。
// 只列名字不带职务——七个人互相都认识，职务在公告署名时才有意义
async function pickPerson() {
  if (!canManage.value) return
  if (!receptionMembers.value.length) { toast({ title: '暂无可选成员', icon: 'none' }); return }
  const chosen = String(form.person || '').split(/[、，,\s]+/).filter(Boolean)
  const res = await showActionSheet({ title: '接待人员', variant: 'picker', multi: true, confirmText: '确定',
    itemList: receptionMembers.value.map(m => ({ label: m.name, selected: chosen.includes(m.name) })) })
  if (res && res.confirm && Array.isArray(res.tapIndexes)) {
    form.person = res.tapIndexes.map(i => receptionMembers.value[i].name).join('、')
  }
}
// ── 接待地点（0731 设计师定）：弹层放已有地点，最后一行「＋ 填写其他地点」进独立输入层；
//    手填过的存 localStorage 下次出现在列表；排序按使用频率（保存公告时 +1），不按拼音 ──
const PLACE_STORE_KEY = 'rn_places'
const DEFAULT_PLACES = ['小区物业办公室', '2号楼架空层活动室']
const placeInputOpen = ref(false)
const placeDraft = ref('')
const placeInputEl = ref(null)

function loadPlaceStore() {
  const s = getStorage(PLACE_STORE_KEY)
  return (s && typeof s === 'object')
    ? { custom: Array.isArray(s.custom) ? s.custom : [], freq: (s.freq && typeof s.freq === 'object') ? s.freq : {} }
    : { custom: [], freq: {} }
}
function placeOptions() {
  const store = loadPlaceStore()
  // 当前值排最前（勾一眼可见），其余按使用频率降序；同频保持原顺序（手填的在默认前）
  const all = [form.place, saved.place, ...store.custom, ...DEFAULT_PLACES]
    .map(v => String(v || '').trim()).filter(Boolean)
  const uniq = [...new Set(all)]
  return uniq.map((name, i) => ({ name, i }))
    .sort((a, b) => ((store.freq[b.name] || 0) - (store.freq[a.name] || 0)) || (a.i - b.i))
    .map(o => o.name)
}
function rememberPlace(name, bump) {
  const store = loadPlaceStore()
  if (!DEFAULT_PLACES.includes(name) && !store.custom.includes(name)) {
    store.custom.push(name)
    if (store.custom.length > 8) store.custom.shift()   // 手填最多留 8 个，太长反而难选
  }
  if (bump) store.freq[name] = (store.freq[name] || 0) + 1
  setStorage(PLACE_STORE_KEY, store)
}
async function pickPlace() {
  if (!canManage.value) return
  const opts = placeOptions()
  const res = await showActionSheet({ title: '接待地点', variant: 'picker',
    itemList: [...opts.map(p => ({ label: p, selected: p === form.place })), { label: '＋ 填写其他地点', arrow: true }] })
  if (!res || res.tapIndex < 0) return
  if (res.tapIndex < opts.length) { form.place = opts[res.tapIndex]; return }
  // 最后一行：进全屏输入层（输入框不进弹层，键盘不盖列表）
  placeDraft.value = ''
  placeInputOpen.value = true
  nextTick(() => { try { placeInputEl.value && placeInputEl.value.focus() } catch (e) { /* 自动聚焦失败无妨 */ } })
}
function usePlaceDraft() {
  const v = placeDraft.value.trim().slice(0, 30)
  if (!v) return
  form.place = v
  rememberPlace(v, false)   // 手填过的立即存下来——这类用户最怕重复输入
  placeInputOpen.value = false
}
// 左上角返回＝回到进入前的状态：地点弹层保持打开（0731 用户定）；「用这个地点」才算选完收层
function backFromPlaceInput() {
  placeInputOpen.value = false
  pickPlace()
}
async function editReason() {
  if (!canManage.value) return
  const res = await showModal({ title: '调整原因（选填）', content: form.reason, editable: true, placeholderText: '如：本周主任外出，接待顺延', confirmText: '确定' })
  if (res && res.confirm) form.reason = String(res.content || '').trim().slice(0, 60)
}

// 距「当前已公示安排」的下一场接待不足 1 天？（0731 提前 1 天惯例的守门）
// 从 saved.timeDesc 解析周几与结束时刻；解析不出（如未设置过）不拦
function nextSessionWithin1Day() {
  const m = String(saved.timeDesc || '').match(/周([一二三四五六日天])/)
  if (!m) return false
  const dowMap = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 0, '天': 0 }
  const recDow = dowMap[m[1]]
  const now = new Date()
  let days = (recDow - now.getDay() + 7) % 7
  if (days === 0) {
    const t = String(saved.timeDesc || '').match(/[—–-]\s*(\d{1,2}):(\d{2})/)
    const endAt = new Date(now.getFullYear(), now.getMonth(), now.getDate(), t ? Number(t[1]) : 20, t ? Number(t[2]) : 0)
    if (now > endAt) days = 7
  }
  return days <= 1
}

/** 点击确定后才保存，并以已保存的数据生成下方的新公告。 */
async function confirmAdjustment() {
  if (saving.value) return
  if (!timeText.value) { toast({ title: '请先选择星期和起止时间', icon: 'none' }); return }
  // 提前 1 天惯例（0731 用户定）：下一场就在 24 小时内还要改，先确认一道
  if (nextSessionWithin1Day()) {
    const res = await showModal({
      size: 'action',
      title: '距下次接待不足 1 天，仍要调整吗？',
      content: '按惯例应提前 1 天公示业主。',
      confirmText: '仍要保存', cancelText: '取消', showCancel: true
    })
    if (!res || !res.confirm) return
  }
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
    if (place) rememberPlace(place, true)   // 使用频率 +1：常用地点下次排最前
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
.page { background: var(--c-bg-page); min-height: 100%; padding-bottom: 184rpx; }   /* 底部给 fixed 主按钮条让位 */
.page-empty { padding: 120rpx 40rpx; text-align: center; color: var(--c-text-weak); font-size: 30rpx; }

/* 页头：接待模块绿，‹ + 标题（0731 设计师稿） */
.rn-hd { background: #2f6b45; padding: calc(env(safe-area-inset-top) + 18rpx) 32rpx 26rpx; color: #fff; }
.rn-hd-bar { display: flex; align-items: center; gap: 18rpx; min-height: 72rpx; cursor: pointer; }
.rn-hd-bar:active { opacity: .75; }
.rn-back { display: inline-block; width: 18rpx; height: 18rpx; border-left: 4rpx solid #fff; border-bottom: 4rpx solid #fff; transform: rotate(45deg); }
.rn-hd-title { font-size: 34rpx; font-weight: 700; }

/* 设置行白卡：每行 灰标签+黑值+（原值）+›，62px 行高标准 */
.rn-card { margin: 24rpx; background: #fff; border-radius: 24rpx; padding: 4rpx 30rpx; box-shadow: 0 2rpx 6rpx rgba(31,41,55,.05), 0 10rpx 26rpx rgba(31,41,55,.07); }
.rn-row { display: flex; align-items: center; gap: 20rpx; min-height: 124rpx; border-top: 2rpx solid #F0F2F5; cursor: pointer; }
.rn-row:first-child { border-top: 0; }
.rn-row:active { background: #FAFBFC; }
.rn-row.ro { cursor: default; }
.rn-row.ro:active { background: transparent; }
.rn-k { flex-shrink: 0; width: 150rpx; font-size: 29rpx; color: #6B7280; }
.rn-v { flex: 1; min-width: 0; font-size: 33rpx; font-weight: 700; color: #1F2937; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rn-v.empty { color: #9AA4B0; font-weight: 500; }
/* 原值提示（0731 设计师稿）：改了才出现，灰字 */
.rn-orig { flex-shrink: 0; font-size: 27rpx; color: #6B7280; }
.rn-arr { flex-shrink: 0; display: inline-block; width: 14rpx; height: 14rpx; border-right: 3rpx solid #B4BCC7; border-bottom: 3rpx solid #B4BCC7; transform: rotate(-45deg); }

.rn-hint { margin: 0 32rpx; font-size: 27rpx; line-height: 1.6; color: #8A94A6; }

/* 公告预览轻行 + 展开卡 */
.rn-links { margin: 28rpx 24rpx 0; padding: 0 8rpx; }
.rn-link { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; min-height: 124rpx; border-top: 2rpx solid #E2E5EA; border-bottom: 2rpx solid #E2E5EA; cursor: pointer; }
.rn-link:active { opacity: .65; }
.rn-link-t { font-size: 32rpx; color: #1F2937; }
.rn-chev { flex-shrink: 0; display: inline-block; width: 14rpx; height: 14rpx; border-right: 3rpx solid #B4BCC7; border-bottom: 3rpx solid #B4BCC7; transform: rotate(45deg); position: relative; top: -2rpx; transition: transform .2s ease, top .2s ease; }
.rn-chev.open { transform: rotate(-135deg); top: 2rpx; }
.rn-preview { padding: 34rpx 30rpx; }

/* 公告纸样式（沿用：句子与后端 PDF 逐字一致） */
.pv-title { text-align: center; font-size: 38rpx; font-weight: 800; color: #1F2937; }
.pv-org { text-align: center; margin-top: 8rpx; font-size: 28rpx; color: #6B7280; }
.pv-line { height: 2rpx; background: #E2E5EA; margin: 22rpx 0; }
.pv-greet { font-size: 30rpx; color: #1F2937; }
.pv-para { margin-top: 14rpx; font-size: 30rpx; line-height: 1.8; color: #1F2937; text-indent: 2em; }
.pv-para.no-indent { text-indent: 0; }
.pv-sign { margin-top: 30rpx; text-align: right; font-size: 29rpx; color: #1F2937; line-height: 1.9; }
.export-success { margin-top: 20rpx; font-size: 28rpx; color: #2E7D50; text-align: center; }
.pv-export { display: block; width: 100%; margin-top: 26rpx; min-height: 96rpx; border: 0; border-radius: 18rpx; background: #E4F0E8; color: #2f6b45; font-size: 32rpx; font-weight: 700; }
.pv-export:active { background: #D6E9DD; }
.pv-export:disabled { opacity: .55; }

/* 填写其他地点全屏层：绿头 + 输入卡 + 底部主按钮，与本页同构（一次只做一件事） */
.rn-place-page { position: fixed; inset: 0; z-index: 500; background: var(--c-bg-page); display: flex; flex-direction: column; }
.pi-body { padding: 24rpx; }
.pi-input { display: block; width: 100%; box-sizing: border-box; min-height: 112rpx; padding: 0 30rpx; background: #fff; border: 0; border-radius: 20rpx; font-size: 33rpx; font-weight: 600; color: #1F2937; box-shadow: 0 2rpx 6rpx rgba(31,41,55,.05), 0 10rpx 26rpx rgba(31,41,55,.07); }
.pi-input::placeholder { color: #9AA4B0; font-weight: 400; }
.pi-bar { margin-top: auto; padding: 12rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); background: #fff; box-shadow: 0 -10rpx 24rpx rgba(20,42,58,.06); }

/* 底部主按钮条（本页无底栏，钉视口底） */
.rn-bar { position: fixed; left: 0; right: 0; bottom: 0; z-index: 90; padding: 12rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); background: #fff; box-shadow: 0 -10rpx 24rpx rgba(20,42,58,.06); }
.rn-primary { width: 100%; min-height: 120rpx; border: 0; border-radius: 20rpx; background: #2f6b45; color: #fff; font-size: 38rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; }
.rn-primary:active { background: #285f3d; }
.rn-primary:disabled { background: #A8BEB0; }
</style>
