<template>
  <div class="page reception-create">
    <PageNav title="登记接待">
      <template #left>
        <button class="back-btn" type="button" aria-label="返回上一页" @click="back">‹</button>
      </template>
    </PageNav>

    <main class="create-body">
      <p v-if="draftRestored" class="draft-note">已恢复上次未完成的接待登记</p>

      <section class="form-card summary-card">
        <button type="button" class="summary-row summary-button" @click="dateQuickOpen = true">
          <span>接待日期</span>
          <strong>{{ displayDate }}</strong>
          <i class="row-arr"></i>
        </button>
        <button type="button" class="summary-row summary-button" @click="openTimePicker">
          <span>接待时间</span>
          <strong>{{ form.time }}</strong>
          <i class="row-arr"></i>
        </button>
        <button type="button" class="summary-row summary-button" @click="openReceiverPicker">
          <span>接待人</span>
          <strong>{{ form.receiver || '请选择接待人' }}</strong>
          <i class="row-arr"></i>
        </button>
      </section>

      <label class="no-visit-row">
        <input v-model="noVisit" type="checkbox" />
        <i></i>
        <span>本次无人来访</span>
      </label>

      <section v-for="(visitor, index) in visitors" v-show="!noVisit" :key="visitor.key" class="visitor-card">
        <div class="visitor-head">
          <strong>来访业主 {{ index + 1 }}</strong>
          <button v-if="visitors.length > 1" type="button" @click="removeVisitor(index)">删除</button>
        </div>
        <div class="form-row">
          <label class="field half">
            <input v-model="visitor.visitorName" class="control" aria-label="业主姓名" placeholder="姓名" />
          </label>
          <label class="field half">
            <input v-model="visitor.room" class="control" aria-label="房号" placeholder="房号" />
          </label>
        </div>
        <div class="field">
          <button class="category-row" type="button" @click="categoryPickerIndex = index">
            <span>诉求分类</span>
            <strong :class="{ 'is-empty': !visitor.category }">{{ categoryLabel(visitor.category) }}</strong>
            <i class="row-arr"></i>
          </button>
        </div>
        <label class="field">
          <textarea v-model="visitor.content" class="control textarea" placeholder="业主反映的问题"></textarea>
        </label>
      </section>

      <button v-if="!noVisit" class="add-visitor" type="button" @click="addVisitor">
        <span>＋ 再登记一位业主</span>
        <i class="row-arr"></i>
      </button>
      <button v-if="hasDraft" class="clear-draft" type="button" @click="clearDraftAndReset">清空重填</button>
      <button class="submit-btn" type="button" :disabled="saving || !canSubmit" @click="submit">
        {{ saving ? '正在保存…' : (noVisit ? '登记为无人来访' : '保存接待记录') }}
      </button>
    </main>

    <div v-if="categoryPickerIndex >= 0" class="sheet-mask" @click="categoryPickerIndex = -1">
      <section class="category-sheet" @click.stop>
        <header>选择诉求分类</header>
        <button v-for="item in CATEGORIES" :key="item.value" type="button"
                :class="{ active: visitors[categoryPickerIndex] && visitors[categoryPickerIndex].category === item.value }"
                @click="chooseCategory(item.value)">
          {{ item.label }}
        </button>
        <button class="sheet-cancel" type="button" @click="categoryPickerIndex = -1">取消</button>
      </section>
    </div>

    <div v-if="dateQuickOpen" class="calendar-mask" @click="dateQuickOpen = false">
      <section class="quick-date-panel" role="dialog" aria-modal="true" aria-label="选择接待日期" @click.stop>
        <header>接待日期</header>
        <button v-for="option in quickDateOptions" :key="option.value" type="button"
                :class="{ selected: form.date === option.value }" @click="chooseQuickDate(option.value)">
          <span><b>{{ option.label }}</b>{{ option.text }}</span>
          <i v-if="form.date === option.value"></i>
        </button>
        <button class="other-date" type="button" @click="openOtherDate">
          <span>选其他日期</span><i class="row-arr"></i>
        </button>
        <button class="picker-cancel" type="button" @click="dateQuickOpen = false">取消</button>
      </section>
    </div>

    <div v-if="calendarOpen" class="calendar-mask" @click="calendarOpen = false">
      <section class="calendar-panel" role="dialog" aria-modal="true" aria-label="选择接待日期" @click.stop>
        <header class="calendar-head">
          <button type="button" aria-label="上一个月" @click="changeMonth(-1)">‹</button>
          <strong>{{ calendarYear }}年{{ calendarMonth }}月</strong>
          <button type="button" aria-label="下一个月" :disabled="isCurrentCalendarMonth" @click="changeMonth(1)">›</button>
        </header>
        <div class="calendar-week">
          <span v-for="weekday in WEEKDAYS" :key="weekday">{{ weekday }}</span>
        </div>
        <div class="calendar-grid">
          <span v-for="blank in leadingBlankCount" :key="'blank-' + blank" class="calendar-day blank"></span>
          <button
            v-for="day in calendarDayCount"
            :key="day"
            class="calendar-day"
            :class="{ selected: isSelectedDate(day), today: isTodayDate(day), disabled: isFutureDate(day) }"
            type="button"
            :disabled="isFutureDate(day)"
            @click="selectDate(day)"
          >
            {{ day }}
          </button>
        </div>
        <footer class="calendar-actions">
          <button type="button" @click="goToday">今天</button>
          <button type="button" @click="calendarOpen = false">取消</button>
        </footer>
      </section>
    </div>

    <div v-if="timePickerOpen" class="calendar-mask" @click="timePickerOpen = false">
      <section class="time-panel" role="dialog" aria-modal="true" aria-label="选择接待时间" @click.stop>
        <header class="time-heading">
          <strong>{{ paddedHour }}:{{ paddedMinute }}</strong>
        </header>
        <div class="time-label">小时</div>
        <div class="hour-grid">
          <button
            v-for="hour in HOUR_OPTIONS"
            :key="hour"
            type="button"
            :class="{ selected: hour === pickedHour }"
            :disabled="isFutureHour(hour)"
            @click="pickedHour = hour"
          >
            {{ String(hour).padStart(2, '0') }}
          </button>
        </div>
        <div class="time-label minute-label">分钟</div>
        <div class="minute-grid">
          <button
            v-for="minute in MINUTE_OPTIONS"
            :key="minute"
            type="button"
            :class="{ selected: minute === pickedMinute }"
            :disabled="isFutureMinute(minute)"
            @click="pickedMinute = minute"
          >
            {{ String(minute).padStart(2, '0') }}
          </button>
        </div>
        <footer class="time-actions">
          <button type="button" class="cancel" @click="timePickerOpen = false">取消</button>
          <button type="button" class="confirm" @click="confirmTime">确认</button>
        </footer>
      </section>
    </div>

    <div v-if="receiverPickerOpen" class="calendar-mask" @click="receiverPickerOpen = false">
      <section class="receiver-panel" role="dialog" aria-modal="true" aria-label="选择接待人" @click.stop>
        <header>接待人</header>
        <button v-for="member in receiverOptions" :key="member.id || member.name" type="button"
                :class="{ selected: pickedReceiver === member.name }" @click="pickedReceiver = member.name">
          <i></i><span>{{ member.name }}</span>
        </button>
        <footer>
          <button type="button" class="cancel" @click="receiverPickerOpen = false">取消</button>
          <button type="button" class="confirm" @click="confirmReceiver">确定</button>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import { getStorage } from '@/utils/storage'
import { toast } from '@/utils/ui'
import { navigateBack } from '@/utils/navigate'
import { dutyPersonForDate, isRotation } from '@/utils/rotation'

function localDateString(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const today = localDateString()
const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']
const HOUR_OPTIONS = Array.from({ length: 14 }, (_, index) => index + 8)
const MINUTE_OPTIONS = [0, 30]
const CATEGORIES = [
  { value: 'property', label: '物业类' },
  { value: 'public_affairs', label: '公共事务' },
  { value: 'neighbor', label: '邻里纠纷' },
  { value: 'other', label: '其他' }
]

const activeRole = getStorage('activeRole', null) || {}
const DRAFT_KEY = 'reception_create_draft'
const form = reactive({
  date: today,
  time: '19:00',
  receiver: activeRole.realName || activeRole.name || ''
})
const members = ref([])
const receptionSystem = ref(null)
const visitors = ref([])
const noVisit = ref(false)
const saving = ref(false)
const dateQuickOpen = ref(false)
const calendarOpen = ref(false)
const calendarYear = ref(new Date().getFullYear())
const calendarMonth = ref(new Date().getMonth() + 1)
const timePickerOpen = ref(false)
const pickedHour = ref(19)
const pickedMinute = ref(0)
const receiverPickerOpen = ref(false)
const pickedReceiver = ref(form.receiver)
const categoryPickerIndex = ref(-1)
const draftRestored = ref(false)
const hasDraft = ref(false)
const draftReady = ref(false)
let visitorKey = 0
let draftTimer = null

const displayDate = computed(() => {
  const [year, month, day] = String(form.date || '').split('-')
  if (!year || !month || !day) return '请选择日期'
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  const weekday = weekdays[new Date(Number(year), Number(month) - 1, Number(day)).getDay()]
  return `${Number(month)}月${Number(day)}日 ${weekday}`
})
const leadingBlankCount = computed(() => new Date(calendarYear.value, calendarMonth.value - 1, 1).getDay())
const calendarDayCount = computed(() => new Date(calendarYear.value, calendarMonth.value, 0).getDate())
const isCurrentCalendarMonth = computed(() => {
  const now = new Date()
  return calendarYear.value === now.getFullYear() && calendarMonth.value === now.getMonth() + 1
})
const paddedHour = computed(() => String(pickedHour.value).padStart(2, '0'))
const paddedMinute = computed(() => String(pickedMinute.value).padStart(2, '0'))
const quickDateOptions = computed(() => {
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return [
    { offset: 0, label: '今天' },
    { offset: -1, label: '昨天' },
    { offset: -2, label: '前天' }
  ].map(option => {
    const date = new Date()
    date.setDate(date.getDate() + option.offset)
    return {
      value: localDateString(date),
      label: option.label,
      text: `${date.getMonth() + 1}月${date.getDate()}日 ${weekdays[date.getDay()]}`
    }
  })
})
const receiverOptions = computed(() => {
  const options = [...members.value]
  if (form.receiver && !options.some(member => member.name === form.receiver)) {
    options.unshift({ id: 'current-receiver', name: form.receiver })
  }
  return options
})
const canSubmit = computed(() => {
  if (!form.date || !form.time || !String(form.receiver || '').trim() || isFutureSelection()) return false
  if (noVisit.value) return true
  return visitors.value.length > 0 && visitors.value.every(visitor =>
    String(visitor.visitorName || '').trim() &&
    String(visitor.category || '').trim() &&
    String(visitor.content || '').trim()
  )
})

function dateAt(day) {
  return `${calendarYear.value}-${String(calendarMonth.value).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}
function openCalendar() {
  const [year, month] = String(form.date || today).split('-').map(Number)
  calendarYear.value = year || new Date().getFullYear()
  calendarMonth.value = month || new Date().getMonth() + 1
  calendarOpen.value = true
}
function chooseQuickDate(value) {
  form.date = value
  normalizeSelectedTime()
  applySuggestedReceiver()
  dateQuickOpen.value = false
}
function openOtherDate() {
  dateQuickOpen.value = false
  openCalendar()
}
function changeMonth(step) {
  const date = new Date(calendarYear.value, calendarMonth.value - 1 + step, 1)
  const now = new Date()
  const currentMonth = new Date(now.getFullYear(), now.getMonth(), 1)
  if (date > currentMonth) return
  calendarYear.value = date.getFullYear()
  calendarMonth.value = date.getMonth() + 1
}
function isSelectedDate(day) { return form.date === dateAt(day) }
function isTodayDate(day) { return today === dateAt(day) }
function isFutureDate(day) {
  const candidate = new Date(calendarYear.value, calendarMonth.value - 1, Number(day), 0, 0, 0, 0)
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0, 0)
  return candidate.getTime() > todayStart.getTime()
}
function selectDate(day) {
  if (isFutureDate(day)) return
  form.date = dateAt(day)
  normalizeSelectedTime()
  applySuggestedReceiver()
  calendarOpen.value = false
}
function goToday() {
  form.date = today
  normalizeSelectedTime()
  applySuggestedReceiver()
  const now = new Date()
  calendarYear.value = now.getFullYear()
  calendarMonth.value = now.getMonth() + 1
  calendarOpen.value = false
}
function openTimePicker() {
  normalizeSelectedTime()
  const [hour, minute] = String(form.time || '19:00').split(':').map(Number)
  pickedHour.value = HOUR_OPTIONS.includes(hour) ? hour : 19
  pickedMinute.value = MINUTE_OPTIONS.includes(minute) ? minute : 0
  timePickerOpen.value = true
}
function isTodaySelected() { return form.date === localDateString() }
function isFutureHour(hour) {
  return isTodaySelected() && hour > new Date().getHours()
}
function isFutureMinute(minute) {
  if (!isTodaySelected()) return false
  const now = new Date()
  return pickedHour.value > now.getHours() ||
    (pickedHour.value === now.getHours() && minute > now.getMinutes())
}
function latestAllowedTime() {
  const now = new Date()
  const hour = Math.max(8, Math.min(21, now.getHours()))
  const minute = now.getHours() < 8 ? 0 : (now.getMinutes() >= 30 ? 30 : 0)
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
}
function isFutureSelection() {
  if (!form.date || !form.time) return false
  const [year, month, day] = String(form.date).split('-').map(Number)
  const [hour, minute] = String(form.time).split(':').map(Number)
  return new Date(year, month - 1, day, hour, minute, 0).getTime() > Date.now()
}
function normalizeSelectedTime() {
  if (isTodaySelected() && isFutureSelection()) form.time = latestAllowedTime()
}
function confirmTime() {
  if (isFutureHour(pickedHour.value) || isFutureMinute(pickedMinute.value)) {
    toast({ title: '不能登记晚于当前时间的接待记录', icon: 'none' })
    return
  }
  form.time = `${paddedHour.value}:${paddedMinute.value}`
  timePickerOpen.value = false
}
function confirmReceiver() {
  if (pickedReceiver.value) form.receiver = pickedReceiver.value
  receiverPickerOpen.value = false
}
function openReceiverPicker() {
  pickedReceiver.value = form.receiver
  receiverPickerOpen.value = true
}

function draftIsMeaningful() {
  return noVisit.value || visitors.value.some(visitor =>
    String(visitor.visitorName || '').trim() ||
    String(visitor.room || '').trim() ||
    String(visitor.category || '').trim() ||
    String(visitor.content || '').trim()
  )
}
function discardDraft() {
  clearTimeout(draftTimer)
  localStorage.removeItem(DRAFT_KEY)
  hasDraft.value = false
  draftRestored.value = false
}
function saveDraft() {
  if (!draftReady.value) return
  if (!draftIsMeaningful()) {
    discardDraft()
    return
  }
  localStorage.setItem(DRAFT_KEY, JSON.stringify({
    form: { date: form.date, time: form.time, receiver: form.receiver },
    noVisit: noVisit.value,
    visitors: visitors.value.map(({ visitorName, room, category, content }) => ({
      visitorName, room, category, content
    })),
    savedAt: Date.now()
  }))
  hasDraft.value = true
}
function scheduleDraftSave() {
  clearTimeout(draftTimer)
  draftTimer = setTimeout(saveDraft, 350)
}
function restoreDraft() {
  try {
    const draft = JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null')
    if (!draft || !draft.form || !Array.isArray(draft.visitors)) return false
    form.date = draft.form.date || today
    form.time = draft.form.time || '19:00'
    form.receiver = draft.form.receiver || form.receiver
    pickedReceiver.value = form.receiver
    noVisit.value = !!draft.noVisit
    visitors.value = draft.visitors.length
      ? draft.visitors.map(visitor => ({ key: ++visitorKey, ...visitor }))
      : [newVisitor()]
    draftRestored.value = true
    hasDraft.value = true
    return true
  } catch (error) {
    localStorage.removeItem(DRAFT_KEY)
    return false
  }
}
function clearDraftAndReset() {
  draftReady.value = false
  discardDraft()
  form.date = today
  form.time = '19:00'
  form.receiver = activeRole.realName || activeRole.name || ''
  pickedReceiver.value = form.receiver
  noVisit.value = false
  visitors.value = [newVisitor()]
  normalizeSelectedTime()
  applySuggestedReceiver()
  draftReady.value = true
  toast({ title: '已清空，可以重新登记', icon: 'none' })
}

// 接待人默认值与首页“下一次接待”共用同一套轮值算法：
// 轮值模式按所选日期所在周和委员名册顺序计算；明确指定人员时优先使用指定人。
function applySuggestedReceiver() {
  const system = receptionSystem.value
  if (!system || !members.value.length || !form.date) return
  const personText = String(system.person || '').trim()
  let suggested = ''
  if (isRotation(personText)) {
    const [year, month, day] = String(form.date).split('-').map(Number)
    const selectedDate = new Date(year, month - 1, day, 12, 0, 0)
    suggested = dutyPersonForDate(selectedDate, members.value)
  } else {
    suggested = personText.split(/[、,，]/).map(name => name.trim()).filter(Boolean)[0] || ''
  }
  if (suggested) {
    form.receiver = suggested
    pickedReceiver.value = suggested
  }
}

function newVisitor() {
  return { key: ++visitorKey, visitorName: '', room: '', category: '', content: '' }
}
function addVisitor() { visitors.value.push(newVisitor()) }
function removeVisitor(index) { visitors.value.splice(index, 1) }
function categoryLabel(value) {
  const item = CATEGORIES.find(category => category.value === value)
  return item ? item.label : '未选择'
}
function chooseCategory(value) {
  const visitor = visitors.value[categoryPickerIndex.value]
  if (visitor) visitor.category = value
  categoryPickerIndex.value = -1
}

// 导航新规(0725 用户定):返回=历史上一页(驾驶舱/接待首页 push 进入,回退天然回来处);
// 登记成功后也走这里=回到来处继续
function back() {
  navigateBack()
}

function validateBase() {
  if (!form.date || !form.time) {
    toast({ title: '请选择接待日期和时间', icon: 'none' })
    return false
  }
  if (isFutureSelection()) {
    toast({ title: '接待日期和时间不能晚于当前时间', icon: 'none' })
    return false
  }
  if (!String(form.receiver || '').trim()) {
    toast({ title: '请选择接待人', icon: 'none' })
    return false
  }
  return true
}

async function submit() {
  if (saving.value || !validateBase()) return
  if (noVisit.value) {
    saving.value = true
    try {
      await api.receptionCreateSession({ ...form, noVisit: true, visitors: [] })
      draftReady.value = false
      discardDraft()
      toast({ title: '已登记无人来访', icon: 'success' })
      back()
    } catch (error) {
      toast({ title: (error && error.message) || '登记失败', icon: 'none' })
    } finally {
      saving.value = false
    }
    return
  }
  const incomplete = visitors.value.some(visitor =>
    !String(visitor.visitorName || '').trim() ||
    !String(visitor.category || '').trim() ||
    !String(visitor.content || '').trim())
  if (!visitors.value.length || incomplete) {
    toast({ title: '请补全姓名、诉求分类和反映问题', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await api.receptionCreateSession({ ...form, visitors: visitors.value })
    draftReady.value = false
    discardDraft()
    toast({ title: '本次接待已登记', icon: 'success' })
    back()
  } catch (error) {
    toast({ title: (error && error.message) || '登记失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  const restored = restoreDraft()
  if (!restored) addVisitor()
  try {
    const [system, roster] = await Promise.all([
      api.receptionSystem().catch(() => null),
      api.committeeMembers().catch(() => [])
    ])
    receptionSystem.value = system
    members.value = roster || []
    const match = String((system && system.timeDesc) || '').match(/(\d{1,2})[:：](00|30)/)
    if (!restored) {
      if (match) form.time = match[1].padStart(2, '0') + ':' + match[2]
      normalizeSelectedTime()
      applySuggestedReceiver()
    }
  } catch (error) { /* 默认值可继续登记 */ }
  draftReady.value = true
})

watch([form, visitors, noVisit], scheduleDraftSave, { deep: true })
onBeforeUnmount(() => {
  clearTimeout(draftTimer)
  saveDraft()
})
</script>

<style scoped>
:deep(.page-nav) { background: #2f6b45; }  /* 接待模块页头（规范三色制） */
.reception-create { min-height: 100vh; background: #F3F5F7; overflow-y: auto; }
.back-btn { width: 64rpx; height: 64rpx; border: 0; background: transparent; color: #fff; font-size: 54rpx; }
.create-body { padding: 24rpx 22rpx 80rpx; }
.draft-note { margin: -4rpx 4rpx 18rpx; color: #7A8594; font-size: 25rpx; line-height: 1.5; }
.form-card, .visitor-card { margin-bottom: 22rpx; padding: 26rpx; border-radius: 24rpx; background: #fff; box-shadow: 0 6rpx 18rpx rgba(31,45,61,.06); }
.summary-card { padding: 0 26rpx; }
.summary-row { position: relative; display: grid; grid-template-columns: 130rpx 1fr 22rpx; align-items: center; gap: 18rpx; min-height: 100rpx; border-bottom: 2rpx solid #E3E7EB; color: #687383; font-size: 28rpx; }
.summary-button { width: 100%; padding: 0; border-top: 0; border-right: 0; border-left: 0; background: transparent; text-align: left; }
.summary-row:last-child { border-bottom: 0; }
.summary-value { display: flex; align-items: center; gap: 16rpx; min-width: 0; }
.summary-value button { padding: 0; border: 0; background: transparent; color: #1F2937; font-size: 31rpx; font-weight: 700; white-space: nowrap; }
.summary-row strong { min-width: 0; color: #1F2937; font-size: 31rpx; font-weight: 700; }
.row-arr { justify-self: end; width: 13rpx; height: 13rpx; border-right: 3rpx solid #AAB3BE; border-bottom: 3rpx solid #AAB3BE; transform: rotate(-45deg); }
.form-row { display: flex; gap: 18rpx; }
.field { display: block; margin-bottom: 22rpx; color: #46515D; font-size: 28rpx; font-weight: 700; }
.field.half { flex: 1; min-width: 0; }
.field > span { display: block; margin-bottom: 10rpx; }
.control { width: 100%; height: 116rpx; box-sizing: border-box; padding: 0 22rpx; border: 2rpx solid #D7DEE5; border-radius: 18rpx; background: #fff; color: #202833; font-size: 34rpx; outline: none; }
.control::placeholder { color: #8993A1; }
.date-control { display: flex; align-items: center; justify-content: space-between; text-align: left; }
.time-control { display: flex; align-items: center; justify-content: space-between; text-align: left; }
.clock-icon { position: relative; width: 40rpx; height: 40rpx; box-sizing: border-box;
  border: 3rpx solid #4775AF; border-radius: 50%; }
.clock-icon::before { content: ""; position: absolute; left: 17rpx; top: 7rpx; width: 3rpx; height: 12rpx; border-radius: 2rpx; background: #4775AF; }
.clock-icon::after { content: ""; position: absolute; left: 17rpx; top: 17rpx; width: 10rpx; height: 3rpx; border-radius: 2rpx; background: #4775AF; transform: rotate(25deg); transform-origin: left center; }
.textarea { height: 180rpx; padding-top: 22rpx; resize: none; }
.visitor-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 22rpx; color: #202833; font-size: 32rpx; }
.visitor-head button { border: 0; background: transparent; color: #A86B60; font-size: 27rpx; }
.category-row { display: grid; grid-template-columns: 150rpx 1fr 22rpx; align-items: center; gap: 16rpx; width: 100%; min-height: 92rpx; padding: 0; border: 0; border-top: 2rpx solid #E3E7EB; border-bottom: 2rpx solid #E3E7EB; background: transparent; color: #687383; font-size: 29rpx; text-align: left; }
.category-row strong { color: #495463; font-size: 29rpx; }
.category-row strong.is-empty { color: #8A94A3; font-weight: 400; }
.add-visitor { display: flex; width: 100%; height: 92rpx; margin-bottom: 12rpx; padding: 0 12rpx; align-items: center; justify-content: space-between; border: 0; border-bottom: 2rpx solid #DDE3E8; background: transparent; color: #253142; font-size: 30rpx; text-align: left; }
.clear-draft { display: block; width: 100%; min-height: 82rpx; margin: 2rpx 0 10rpx; padding: 0 12rpx; border: 0; border-bottom: 2rpx solid #DDE3E8; background: transparent; color: #8A5B54; font-size: 28rpx; text-align: left; }
.no-visit-row { position: relative; display: flex; align-items: center; justify-content: flex-start; gap: 18rpx; min-height: 92rpx; margin-bottom: 28rpx; padding: 0 12rpx; border-bottom: 2rpx solid #DDE3E8; color: #46515D; font-size: 30rpx; }
.no-visit-row input { position: absolute; opacity: 0; }
.no-visit-row i { width: 42rpx; height: 42rpx; box-sizing: border-box; border: 3rpx solid #AEB8C2; border-radius: 8rpx; background: #fff; }
.no-visit-row input:checked + i { border-color: #2F6B45; background: #2F6B45; }
.no-visit-row input:checked + i::after { content: ""; display: block; width: 19rpx; height: 10rpx; margin: 10rpx 0 0 8rpx; border-left: 4rpx solid #fff; border-bottom: 4rpx solid #fff; transform: rotate(-45deg); }
.submit-btn { position: sticky; bottom: 0; z-index: 20; display: block; width: 100%; height: 96rpx; margin: 28rpx auto 0; border: 0; border-radius: 18rpx; background: #2F6B45; color: #fff; font-size: 32rpx; font-weight: 700; box-shadow: 0 -12rpx 24rpx rgba(243,245,247,.96); }
.submit-btn:disabled { opacity: 1; background: #A9B8AE; color: rgba(255,255,255,.92); }
.sheet-mask { position: fixed; inset: 0; z-index: 320; display: flex; align-items: flex-end; background: rgba(23,32,42,.46); }
.category-sheet { width: 100%; padding: 20rpx 24rpx calc(24rpx + env(safe-area-inset-bottom)); border-radius: 28rpx 28rpx 0 0; background: #fff; }
.category-sheet header { padding: 18rpx 8rpx 22rpx; color: #1F2937; font-size: 32rpx; font-weight: 700; }
.category-sheet button { display: flex; align-items: center; width: 100%; min-height: 88rpx; padding: 0 12rpx; border: 0; border-top: 2rpx solid #E5E8EC; background: transparent; color: #374151; font-size: 30rpx; text-align: left; }
.category-sheet button.active { color: #2F6B45; font-weight: 700; }
.category-sheet .sheet-cancel { justify-content: center; margin-top: 14rpx; border: 0; border-radius: 16rpx; background: #F2F4F6; color: #65707C; }
.calendar-mask { position: fixed; inset: 0; z-index: 300; display: flex; align-items: flex-end; justify-content: center;
  padding: 0; box-sizing: border-box; background: rgba(23, 32, 42, .48); }
.calendar-panel { width: 100%; max-width: 750rpx; padding: 30rpx 26rpx calc(24rpx + env(safe-area-inset-bottom)); box-sizing: border-box;
  border-radius: 28rpx 28rpx 0 0; background: #fff; box-shadow: 0 -12rpx 40rpx rgba(10, 20, 30, .18); }
.calendar-head { display: grid; grid-template-columns: 86rpx 1fr 86rpx; align-items: center; margin-bottom: 22rpx; }
.calendar-head strong { text-align: center; color: #243247; font-size: 38rpx; }
.calendar-head button { width: 76rpx; height: 76rpx; border: 0; border-radius: 50%; background: #F0F4F8;
  color: #2F6B45; font-size: 54rpx; line-height: 1; }
.calendar-week, .calendar-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 7rpx; }
.calendar-week { margin-bottom: 10rpx; }
.calendar-week span { padding: 8rpx 0; text-align: center; color: #7C8794; font-size: 27rpx; font-weight: 700; }
.calendar-day { height: 72rpx; border: 0; border-radius: 14rpx; background: transparent; color: #26313E;
  font-size: 32rpx; font-weight: 500; }
.calendar-day.blank { visibility: hidden; }
.calendar-day.disabled { color: #C5CBD2; background: transparent; box-shadow: none; cursor: not-allowed; }
.calendar-day.today { color: #2F6B45; box-shadow: inset 0 0 0 3rpx #B8D0C0; font-weight: 700; }
.calendar-day.selected { background: #E9F1EC; color: #1F2937; box-shadow: none; font-weight: 800; }
.calendar-actions { display: flex; justify-content: flex-end; gap: 18rpx; margin-top: 22rpx; padding-top: 20rpx;
  border-top: 2rpx solid #EDF0F3; }
.calendar-actions button { min-width: 120rpx; height: 64rpx; padding: 0 24rpx; border: 0; border-radius: 14rpx;
  background: #EEF5F0; color: #2F6B45; font-size: 29rpx; font-weight: 700; }
.calendar-actions button:last-child { background: #F2F3F5; color: #65707C; }
.time-panel { width: 100%; max-width: 750rpx; padding: 30rpx 28rpx calc(26rpx + env(safe-area-inset-bottom)); box-sizing: border-box;
  border-radius: 28rpx 28rpx 0 0; background: #fff; box-shadow: 0 -12rpx 40rpx rgba(10, 20, 30, .18); }
.time-heading { display: flex; align-items: center; justify-content: center; margin-bottom: 24rpx; }
.time-heading strong { color: #2F754C; font-size: 54rpx; letter-spacing: 2rpx; }
.time-label { margin: 0 0 12rpx; color: #727D88; font-size: 27rpx; font-weight: 700; }
.minute-label { margin-top: 24rpx; }
.hour-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 13rpx; }
.minute-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16rpx; }
.hour-grid button, .minute-grid button { height: 76rpx; border: 0; border-radius: 14rpx;
  background: #F2F4F6; color: #28333F; font-size: 32rpx; }
.hour-grid button:disabled, .minute-grid button:disabled { background: #F6F7F8; color: #C2C8CF; box-shadow: none; }
.hour-grid button.selected, .minute-grid button.selected { background: #EEF5F0; color: #1F2937;
  box-shadow: inset 0 0 0 3rpx #2F754C; font-weight: 800; }
.time-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 18rpx; margin-top: 48rpx; }
.time-actions button { height: 74rpx; border-radius: 16rpx; font-size: 30rpx; font-weight: 700; }
.time-actions .cancel { border: 2rpx solid #D8DEE4; background: #fff; color: #66717C; }
.time-actions .confirm { border: 0; background: #2F754C; color: #fff; }

.quick-date-panel, .receiver-panel { width: 100%; max-width: 750rpx; padding: 0 0 calc(18rpx + env(safe-area-inset-bottom)); border-radius: 28rpx 28rpx 0 0; background: #fff; box-shadow: 0 -12rpx 40rpx rgba(10,20,30,.18); overflow: hidden; }
.quick-date-panel > header, .receiver-panel > header { min-height: 86rpx; padding: 0 32rpx; display: flex; align-items: center; border-bottom: 2rpx solid #E5E8EC; color: #1F2937; font-size: 32rpx; font-weight: 700; }
.quick-date-panel > button:not(.picker-cancel) { display: flex; width: 100%; min-height: 100rpx; padding: 0 34rpx; align-items: center; justify-content: space-between; border: 0; border-bottom: 2rpx solid #E5E8EC; background: #fff; color: #657080; font-size: 30rpx; text-align: left; }
.quick-date-panel > button.selected { background: #EEF5F0; }
.quick-date-panel > button span { display: flex; align-items: center; gap: 16rpx; }
.quick-date-panel > button b { color: #1F2937; font-size: 32rpx; }
.quick-date-panel > button > i:not(.row-arr) { width: 28rpx; height: 15rpx; border-left: 5rpx solid #2F754C; border-bottom: 5rpx solid #2F754C; transform: rotate(-45deg); }
.quick-date-panel .other-date { color: #1F2937 !important; }
.picker-cancel { display: block; width: calc(100% - 48rpx); min-height: 82rpx; margin: 18rpx auto 0; border: 0; border-radius: 16rpx; background: #F2F4F6; color: #4B5563; font-size: 30rpx; font-weight: 700; }

.receiver-panel { max-height: 86vh; overflow-y: auto; }
.receiver-panel > button { display: flex; width: 100%; min-height: 96rpx; padding: 0 34rpx; align-items: center; gap: 20rpx; border: 0; border-bottom: 2rpx solid #E5E8EC; background: #fff; color: #1F2937; font-size: 31rpx; text-align: left; }
.receiver-panel > button.selected { background: #EEF5F0; font-weight: 700; }
.receiver-panel > button > i { position: relative; width: 44rpx; height: 44rpx; flex: 0 0 auto; box-sizing: border-box; border: 3rpx solid #C7CED7; border-radius: 8rpx; background: #fff; }
.receiver-panel > button.selected > i { border-color: #2F754C; background: #2F754C; }
.receiver-panel > button.selected > i::after { content: ""; position: absolute; left: 10rpx; top: 8rpx; width: 19rpx; height: 10rpx; border-left: 4rpx solid #fff; border-bottom: 4rpx solid #fff; transform: rotate(-45deg); }
.receiver-panel footer { position: sticky; bottom: 0; display: grid; grid-template-columns: 1fr 1.8fr; gap: 16rpx; padding: 18rpx 24rpx; background: #fff; }
.receiver-panel footer button { min-height: 80rpx; border-radius: 16rpx; font-size: 30rpx; font-weight: 700; }
.receiver-panel footer .cancel { border: 0; background: #F2F4F6; color: #4B5563; }
.receiver-panel footer .confirm { border: 0; background: #2F754C; color: #fff; }
button:disabled { opacity: .55; }
</style>
