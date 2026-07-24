<template>
  <div class="page reception-create">
    <PageNav title="登记接待">
      <template #left>
        <button class="back-btn" type="button" aria-label="返回接待中心" @click="back">‹</button>
      </template>
    </PageNav>

    <main class="create-body">
      <section class="form-card">
        <div class="form-row">
          <label class="field half">
            <span>接待日期 *</span>
            <button class="control date-control" type="button" @click="openCalendar">
              <span>{{ displayDate }}</span>
            </button>
          </label>
          <label class="field half">
            <span>接待时间 *</span>
            <button class="control time-control" type="button" @click="openTimePicker">
              <span>{{ form.time || '请选择时间' }}</span>
              <span class="clock-icon"></span>
            </button>
          </label>
        </div>

        <label class="field">
          <span>接待人 *</span>
          <select v-model="form.receiver" class="control">
            <option value="" disabled>请选择接待人</option>
            <option v-if="form.receiver && !members.some(member => member.name === form.receiver)" :value="form.receiver">
              {{ form.receiver }}
            </option>
            <option v-for="member in members" :key="member.id || member.name" :value="member.name">
              {{ member.name }}<template v-if="member.role">（{{ member.role }}）</template>
            </option>
          </select>
        </label>

        <button class="no-visit-btn" type="button" :disabled="saving" @click="submitNoVisit">
          {{ saving ? '正在登记…' : '本次无人来访' }}
        </button>
      </section>

      <section v-for="(visitor, index) in visitors" :key="visitor.key" class="visitor-card">
        <div class="visitor-head">
          <strong>来访业主 {{ index + 1 }}</strong>
          <button v-if="visitors.length > 1" type="button" @click="removeVisitor(index)">删除</button>
        </div>
        <div class="form-row">
          <label class="field half">
            <span>姓名 *</span>
            <input v-model="visitor.visitorName" class="control" />
          </label>
          <label class="field half">
            <span>房号</span>
            <input v-model="visitor.room" class="control" />
          </label>
        </div>
        <div class="field">
          <span>诉求分类</span>
          <div class="type-row">
            <button v-for="item in CATEGORIES" :key="item.value" type="button"
                    :class="{ active: visitor.category === item.value }"
                    @click="visitor.category = item.value">
              {{ item.label }}
            </button>
          </div>
        </div>
        <label class="field">
          <span>诉求内容 *</span>
          <textarea v-model="visitor.content" class="control textarea"></textarea>
        </label>
      </section>

      <button class="add-visitor" type="button" @click="addVisitor">＋ 继续添加业主</button>
      <button class="submit-btn" type="button" :disabled="saving" @click="submit">
        {{ saving ? '正在保存…' : '确认登记' }}
      </button>
    </main>

    <div v-if="calendarOpen" class="calendar-mask" @click="calendarOpen = false">
      <section class="calendar-panel" role="dialog" aria-modal="true" aria-label="选择接待日期" @click.stop>
        <header class="calendar-head">
          <button type="button" aria-label="上一个月" @click="changeMonth(-1)">‹</button>
          <strong>{{ calendarYear }}年{{ calendarMonth }}月</strong>
          <button type="button" aria-label="下一个月" @click="changeMonth(1)">›</button>
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
            :class="{ selected: isSelectedDate(day), today: isTodayDate(day) }"
            type="button"
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import { getStorage } from '@/utils/storage'
import { toast } from '@/utils/ui'

function localDateString(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const today = localDateString()
const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']
const HOUR_OPTIONS = Array.from({ length: 12 }, (_, index) => index + 10)
const MINUTE_OPTIONS = [0, 30]
const CATEGORIES = [
  { value: 'property', label: '物业类' },
  { value: 'public_affairs', label: '公共事务' },
  { value: 'neighbor', label: '邻里纠纷' },
  { value: 'other', label: '其他' }
]

const activeRole = getStorage('activeRole', null) || {}
const form = reactive({
  date: today,
  time: '19:00',
  receiver: activeRole.realName || activeRole.name || ''
})
const members = ref([])
const visitors = ref([])
const saving = ref(false)
const calendarOpen = ref(false)
const calendarYear = ref(new Date().getFullYear())
const calendarMonth = ref(new Date().getMonth() + 1)
const timePickerOpen = ref(false)
const pickedHour = ref(19)
const pickedMinute = ref(0)
let visitorKey = 0

const displayDate = computed(() => {
  const [year, month, day] = String(form.date || '').split('-')
  return year && month && day ? `${year}年${Number(month)}月${Number(day)}日` : '请选择日期'
})
const leadingBlankCount = computed(() => new Date(calendarYear.value, calendarMonth.value - 1, 1).getDay())
const calendarDayCount = computed(() => new Date(calendarYear.value, calendarMonth.value, 0).getDate())
const paddedHour = computed(() => String(pickedHour.value).padStart(2, '0'))
const paddedMinute = computed(() => String(pickedMinute.value).padStart(2, '0'))

function dateAt(day) {
  return `${calendarYear.value}-${String(calendarMonth.value).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}
function openCalendar() {
  const [year, month] = String(form.date || today).split('-').map(Number)
  calendarYear.value = year || new Date().getFullYear()
  calendarMonth.value = month || new Date().getMonth() + 1
  calendarOpen.value = true
}
function changeMonth(step) {
  const date = new Date(calendarYear.value, calendarMonth.value - 1 + step, 1)
  calendarYear.value = date.getFullYear()
  calendarMonth.value = date.getMonth() + 1
}
function isSelectedDate(day) { return form.date === dateAt(day) }
function isTodayDate(day) { return today === dateAt(day) }
function selectDate(day) {
  form.date = dateAt(day)
  calendarOpen.value = false
}
function goToday() {
  form.date = today
  const now = new Date()
  calendarYear.value = now.getFullYear()
  calendarMonth.value = now.getMonth() + 1
  calendarOpen.value = false
}
function openTimePicker() {
  const [hour, minute] = String(form.time || '19:00').split(':').map(Number)
  pickedHour.value = HOUR_OPTIONS.includes(hour) ? hour : 19
  pickedMinute.value = MINUTE_OPTIONS.includes(minute) ? minute : 0
  timePickerOpen.value = true
}
function confirmTime() {
  form.time = `${paddedHour.value}:${paddedMinute.value}`
  timePickerOpen.value = false
}

function newVisitor() {
  return { key: ++visitorKey, visitorName: '', room: '', category: 'property', content: '' }
}
function addVisitor() { visitors.value.push(newVisitor()) }
function removeVisitor(index) { visitors.value.splice(index, 1) }
function back() { window.location.assign('/reception-center') }

function validateBase() {
  if (!form.date || !form.time) {
    toast({ title: '请选择接待日期和时间', icon: 'none' })
    return false
  }
  if (!String(form.receiver || '').trim()) {
    toast({ title: '请选择接待人', icon: 'none' })
    return false
  }
  return true
}

async function submitNoVisit() {
  if (saving.value || !validateBase()) return
  saving.value = true
  try {
    await api.receptionCreateSession({ ...form, noVisit: true, visitors: [] })
    toast({ title: '已登记无人来访', icon: 'success' })
    back()
  } catch (error) {
    toast({ title: (error && error.message) || '登记失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

async function submit() {
  if (saving.value || !validateBase()) return
  const incomplete = visitors.value.some(visitor =>
    !String(visitor.visitorName || '').trim() || !String(visitor.content || '').trim())
  if (!visitors.value.length || incomplete) {
    toast({ title: '请补全每位业主的姓名和诉求内容', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await api.receptionCreateSession({ ...form, visitors: visitors.value })
    toast({ title: '本次接待已登记', icon: 'success' })
    back()
  } catch (error) {
    toast({ title: (error && error.message) || '登记失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  addVisitor()
  try {
    const [system, roster] = await Promise.all([
      api.receptionSystem().catch(() => null),
      api.committeeMembers().catch(() => [])
    ])
    members.value = roster || []
    const match = String((system && system.timeDesc) || '').match(/(\d{1,2})[:：](00|30)/)
    if (match) form.time = match[1].padStart(2, '0') + ':' + match[2]
  } catch (error) { /* 默认值可继续登记 */ }
})
</script>

<style scoped>
.reception-create { min-height: 100vh; background: #F3F5F7; overflow-y: auto; }
.back-btn { width: 64rpx; height: 64rpx; border: 0; background: transparent; color: #fff; font-size: 54rpx; }
.create-body { padding: 24rpx 22rpx 80rpx; }
.form-card, .visitor-card { margin-bottom: 22rpx; padding: 26rpx; border-radius: 24rpx; background: #fff; box-shadow: 0 6rpx 18rpx rgba(31,45,61,.06); }
.form-row { display: flex; gap: 18rpx; }
.field { display: block; margin-bottom: 22rpx; color: #46515D; font-size: 28rpx; font-weight: 700; }
.field.half { flex: 1; min-width: 0; }
.field > span { display: block; margin-bottom: 10rpx; }
.control { width: 100%; height: 84rpx; box-sizing: border-box; padding: 0 18rpx; border: 2rpx solid #DFE5E9; border-radius: 16rpx; background: #FCFDFD; color: #202833; font-size: 30rpx; outline: none; }
.date-control { display: flex; align-items: center; justify-content: space-between; text-align: left; }
.time-control { display: flex; align-items: center; justify-content: space-between; text-align: left; }
.clock-icon { position: relative; width: 40rpx; height: 40rpx; box-sizing: border-box;
  border: 3rpx solid #4775AF; border-radius: 50%; }
.clock-icon::before { content: ""; position: absolute; left: 17rpx; top: 7rpx; width: 3rpx; height: 12rpx; border-radius: 2rpx; background: #4775AF; }
.clock-icon::after { content: ""; position: absolute; left: 17rpx; top: 17rpx; width: 10rpx; height: 3rpx; border-radius: 2rpx; background: #4775AF; transform: rotate(25deg); transform-origin: left center; }
.textarea { height: 170rpx; padding-top: 16rpx; resize: none; }
.no-visit-btn { width: 100%; height: 80rpx; border: 2rpx solid #B9C4CC; border-radius: 18rpx; background: #fff; color: #4A5560; font-size: 29rpx; font-weight: 700; }
.visitor-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 22rpx; color: #202833; font-size: 32rpx; }
.visitor-head button { border: 0; background: transparent; color: #A86B60; font-size: 27rpx; }
.type-row { display: flex; flex-wrap: wrap; gap: 12rpx; }
.type-row button { min-height: 58rpx; padding: 8rpx 20rpx; border: 0; border-radius: 30rpx; background: #F1F3F5; color: #5B6570; font-size: 27rpx; }
.type-row button.active { background: #B96300; color: #fff; font-weight: 700; }
.add-visitor { width: 100%; height: 78rpx; margin-bottom: 28rpx; border: 2rpx dashed #AAB7C0; border-radius: 18rpx; background: #fff; color: #46515D; font-size: 29rpx; }
.submit-btn { display: block; width: 68%; height: 88rpx; margin: 0 auto; border: 0; border-radius: 18rpx; background: #B96300; color: #fff; font-size: 32rpx; font-weight: 700; }
.calendar-mask { position: fixed; inset: 0; z-index: 300; display: flex; align-items: center; justify-content: center;
  padding: 34rpx; box-sizing: border-box; background: rgba(23, 32, 42, .48); }
.calendar-panel { width: 100%; max-width: 650rpx; padding: 30rpx 26rpx 24rpx; box-sizing: border-box;
  border-radius: 28rpx; background: #fff; box-shadow: 0 24rpx 70rpx rgba(10, 20, 30, .24); }
.calendar-head { display: grid; grid-template-columns: 86rpx 1fr 86rpx; align-items: center; margin-bottom: 22rpx; }
.calendar-head strong { text-align: center; color: #243247; font-size: 38rpx; }
.calendar-head button { width: 76rpx; height: 76rpx; border: 0; border-radius: 50%; background: #F0F4F8;
  color: #376AAB; font-size: 54rpx; line-height: 1; }
.calendar-week, .calendar-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 7rpx; }
.calendar-week { margin-bottom: 10rpx; }
.calendar-week span { padding: 8rpx 0; text-align: center; color: #7C8794; font-size: 27rpx; font-weight: 700; }
.calendar-day { height: 72rpx; border: 0; border-radius: 14rpx; background: transparent; color: #26313E;
  font-size: 32rpx; font-weight: 500; }
.calendar-day.blank { visibility: hidden; }
.calendar-day.today { color: #376AAB; box-shadow: inset 0 0 0 3rpx #AFC5DF; font-weight: 700; }
.calendar-day.selected { background: #4775AF; color: #fff; box-shadow: none; font-weight: 800; }
.calendar-actions { display: flex; justify-content: flex-end; gap: 18rpx; margin-top: 22rpx; padding-top: 20rpx;
  border-top: 2rpx solid #EDF0F3; }
.calendar-actions button { min-width: 120rpx; height: 64rpx; padding: 0 24rpx; border: 0; border-radius: 14rpx;
  background: #EDF3FA; color: #376AAB; font-size: 29rpx; font-weight: 700; }
.calendar-actions button:last-child { background: #F2F3F5; color: #65707C; }
.time-panel { width: 100%; max-width: 650rpx; padding: 30rpx 28rpx 26rpx; box-sizing: border-box;
  border-radius: 28rpx; background: #fff; box-shadow: 0 24rpx 70rpx rgba(10, 20, 30, .24); }
.time-heading { display: flex; align-items: center; justify-content: center; margin-bottom: 24rpx; }
.time-heading strong { color: #8B5E34; font-size: 54rpx; letter-spacing: 2rpx; }
.time-label { margin: 0 0 12rpx; color: #727D88; font-size: 27rpx; font-weight: 700; }
.minute-label { margin-top: 24rpx; }
.hour-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 13rpx; }
.minute-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16rpx; }
.hour-grid button, .minute-grid button { height: 76rpx; border: 0; border-radius: 14rpx;
  background: #F2F4F6; color: #28333F; font-size: 32rpx; }
.hour-grid button.selected, .minute-grid button.selected { background: #E4ECF6; color: #2F609D;
  box-shadow: inset 0 0 0 3rpx #4775AF; font-weight: 800; }
.time-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 18rpx; margin-top: 48rpx; }
.time-actions button { height: 74rpx; border-radius: 16rpx; font-size: 30rpx; font-weight: 700; }
.time-actions .cancel { border: 2rpx solid #D8DEE4; background: #fff; color: #66717C; }
.time-actions .confirm { border: 0; background: #4775AF; color: #fff; }
button:disabled { opacity: .55; }
</style>
