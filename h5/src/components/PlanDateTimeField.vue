<template>
  <div class="pdt">
    <!-- 触发行：日期 | 时间（与发起会议同款分栏行） -->
    <div class="pdt-field">
      <div class="pdt-part" @click="openDate">
        <span class="pdt-label">日期</span>
        <span class="pdt-value" :class="{ ph: !date }">{{ date ? fmtPlanDate(date) : '选择日期' }}</span>
        <span class="pdt-arrow">›</span>
      </div>
      <div class="pdt-part pdt-part-time" @click="openTime">
        <span class="pdt-label">时间</span>
        <span class="pdt-value" :class="{ ph: !time }">{{ time || '选择时间' }}</span>
        <span class="pdt-arrow">›</span>
      </div>
    </div>

    <!-- 日期弹窗：小日历（月历网格），点日期即选 -->
    <div v-if="dateOpen" class="picker-pop-mask" @click="dateOpen = false">
      <div class="cal-pop" @click.stop>
        <div class="pop-close"><span class="close-btn" @click="dateOpen = false">×</span></div>
        <div class="cal-head">
          <span class="cal-nav" @click="prevMonth">‹</span>
          <span class="cal-title">{{ dpYear }}年{{ dpMonth }}月</span>
          <span class="cal-nav" @click="nextMonth">›</span>
        </div>
        <div class="cal-week">
          <span v-for="w in ['日','一','二','三','四','五','六']" :key="w" class="cal-wd">{{ w }}</span>
        </div>
        <div class="cal-grid">
          <span v-for="(cell, i) in calCells" :key="i" class="cal-cell"
                :class="{ empty: !cell, disabled: cell && isPastDay(cell), on: cell && isSelectedDay(cell), today: cell && !isSelectedDay(cell) && isToday(cell) }"
                @click="cell && pickDay(cell)">{{ cell || '' }}</span>
        </div>
        <div class="pp-actions">
          <button class="btn btn-ghost" @click="dateOpen = false">取消</button>
        </div>
      </div>
    </div>

    <!-- 时间弹窗：大按钮点选（时 + 分），点选即生效 -->
    <div v-if="timeOpen" class="picker-pop-mask" @click="timeOpen = false">
      <div class="picker-pop" @click.stop>
        <div class="pop-close"><span class="close-btn" @click="timeOpen = false">×</span></div>
        <div class="tg-cur">{{ pad(tpHour) }}:{{ pad(tpMinute) }}</div>
        <div class="tg-label">时</div>
        <div class="tg-grid">
          <span v-for="h in hourOptions" :key="h" class="tg-cell"
                :class="{ on: h === tpHour, disabled: isPastTime(h, 45) }"
                @click="setHour(h)">{{ pad(h) }}</span>
        </div>
        <div class="tg-label">分</div>
        <div class="tg-grid tg-grid-m">
          <span v-for="m in minuteOptions" :key="m" class="tg-cell"
                :class="{ on: m === tpMinute, disabled: isPastTime(tpHour, m) }"
                @click="setMinute(m)">{{ pad(m) }}</span>
        </div>
        <div class="pp-actions">
          <button class="btn btn-primary" @click="confirmTime">确认</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { toast } from '@/utils/ui'

const props = defineProps({
  date: { type: String, default: '' },
  time: { type: String, default: '' },
  // 是否禁止选到今天之前（发起类默认 true）
  minToday: { type: Boolean, default: true }
})
const emit = defineEmits(['update:date', 'update:time'])

function pad(n) { return String(n).padStart(2, '0') }
function todayStr() {
  const d = new Date()
  return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
}
function fmtPlanDate(s) {
  const p = String(s || '').split('-')
  return p.length === 3 ? (Number(p[1]) + '月' + Number(p[2]) + '日') : String(s || '')
}

// ── 日期 ──
const dateOpen = ref(false)
const dpYear = ref(2026)
const dpMonth = ref(1)
function openDate() {
  const parts = (props.date || todayStr()).split('-')
  dpYear.value = Number(parts[0]) || new Date().getFullYear()
  dpMonth.value = Number(parts[1]) || 1
  dateOpen.value = true
}
const calCells = computed(() => {
  const first = new Date(dpYear.value, dpMonth.value - 1, 1).getDay()
  const days = new Date(dpYear.value, dpMonth.value, 0).getDate()
  const cells = []
  for (let i = 0; i < first; i++) cells.push(0)
  for (let d = 1; d <= days; d++) cells.push(d)
  return cells
})
function prevMonth() { if (dpMonth.value <= 1) { dpMonth.value = 12; dpYear.value -= 1 } else { dpMonth.value -= 1 } }
function nextMonth() { if (dpMonth.value >= 12) { dpMonth.value = 1; dpYear.value += 1 } else { dpMonth.value += 1 } }
function dateStr(day) { return dpYear.value + '-' + pad(dpMonth.value) + '-' + pad(day) }
function isSelectedDay(day) { return props.date === dateStr(day) }
function isToday(day) { return todayStr() === dateStr(day) }
function isPastDay(day) { return props.minToday && dateStr(day) < todayStr() }
function pickDay(day) {
  if (isPastDay(day)) { toast({ title: '日期不能早于今天', icon: 'none' }); return }
  emit('update:date', dateStr(day))
  dateOpen.value = false
}

// ── 时间 ──
const timeOpen = ref(false)
const tpHour = ref(14)
const tpMinute = ref(0)
const hourOptions = Array.from({ length: 12 }, (_, i) => i + 9)   // 9—20 点
const minuteOptions = Array.from({ length: 4 }, (_, i) => i * 15) // 0/15/30/45
function openTime() {
  const parts = (props.time || '14:00').split(':')
  tpHour.value = Math.min(20, Math.max(9, Number(parts[0]) || 14))
  tpMinute.value = (Math.round((Number(parts[1]) || 0) / 15) * 15) % 60
  if (isPastTime(tpHour.value, tpMinute.value)) {
    const next = firstAvailable()
    if (!next) { toast({ title: '今天已无可选时间，请改日期', icon: 'none' }); return }
    tpHour.value = next.hour; tpMinute.value = next.minute; applyTime()
  }
  timeOpen.value = true
}
function isPastTime(hour, minute) {
  if (!props.minToday || props.date !== todayStr()) return false
  const now = new Date()
  return Number(hour) * 60 + Number(minute) <= now.getHours() * 60 + now.getMinutes()
}
function firstAvailable() {
  for (const hour of hourOptions) for (const minute of minuteOptions) {
    if (!isPastTime(hour, minute)) return { hour, minute }
  }
  return null
}
function applyTime() { emit('update:time', pad(tpHour.value) + ':' + pad(tpMinute.value)) }
function setHour(h) {
  if (isPastTime(h, 45)) return
  tpHour.value = h
  if (isPastTime(h, tpMinute.value)) {
    const m = minuteOptions.find(mm => !isPastTime(h, mm))
    if (m === undefined) return
    tpMinute.value = m
  }
  applyTime()
}
function setMinute(m) {
  if (isPastTime(tpHour.value, m)) return
  tpMinute.value = m
  applyTime()
}
function confirmTime() {
  if (isPastTime(tpHour.value, tpMinute.value)) { toast({ title: '时间不能早于当前时间', icon: 'none' }); return }
  applyTime()
  timeOpen.value = false
}
</script>

<style scoped>
/* 触发行：与发起会议 field-line-split 同款——白底圆角，日期|时间分栏、中缝分隔 */
.pdt-field { display: flex; border: 2rpx solid #DFE5E9; border-radius: 16rpx; background: #FCFDFD; overflow: hidden; }
.pdt-part { flex: 1; min-width: 0; display: flex; align-items: center; gap: 12rpx; padding: 0 18rpx; height: 84rpx; box-sizing: border-box; }
.pdt-part-time { border-left: 2rpx solid #EEF1F4; }
.pdt-label { flex-shrink: 0; font-size: 26rpx; color: #61656c; font-weight: 700; }
.pdt-value { flex: 1; text-align: left; font-size: 30rpx; color: #24364B; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pdt-value.ph { color: #9AA2AB; font-weight: 500; }
.pdt-arrow { flex-shrink: 0; font-size: 28rpx; color: #c4c8cd; line-height: 1; }

/* 弹窗遮罩 + 关闭 */
.picker-pop-mask { position: fixed; inset: 0; z-index: 210; background: rgba(0,0,0,0.42); display: flex; align-items: center; justify-content: center; padding: 48rpx; box-sizing: border-box; }
.pop-close { display: flex; justify-content: flex-end; margin-bottom: 4rpx; }
.close-btn { width: 60rpx; height: 60rpx; line-height: 56rpx; text-align: center; border-radius: 30rpx; color: #666; background: #f5f5f5; font-size: 40rpx; flex-shrink: 0; }

/* 日历 */
.cal-pop { width: 86%; max-width: 620rpx; background: #fff; border-radius: 24rpx; padding: 28rpx 24rpx calc(20rpx + env(safe-area-inset-bottom)); box-sizing: border-box; }
.cal-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18rpx; }
.cal-title { font-size: 36rpx; font-weight: 700; color: #1f2329; }
.cal-nav { width: 76rpx; height: 76rpx; display: flex; align-items: center; justify-content: center; font-size: 52rpx; color: var(--c-primary-dark); font-weight: 700; }
.cal-nav:active { opacity: 0.5; }
.cal-week { display: grid; grid-template-columns: repeat(7, 1fr); margin-bottom: 8rpx; }
.cal-wd { text-align: center; font-size: 26rpx; color: #999; padding: 8rpx 0; }
.cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 6rpx; }
.cal-cell { height: 78rpx; display: flex; align-items: center; justify-content: center; font-size: 32rpx; color: #1f2329; border-radius: 12rpx; }
.cal-cell.empty { visibility: hidden; }
.cal-cell.disabled { color: #C7CDD5; background: transparent; }
.cal-cell.today { color: var(--c-primary-dark); font-weight: 700; }
.cal-cell.on { background: #3E6BA8; color: #fff; font-weight: 700; }
.cal-cell:not(.empty):not(.on):not(.disabled):active { background: #E6EDF8; }

/* 时间网格 */
.picker-pop { position: relative; width: 100%; max-width: 660rpx; background: #fff; border-radius: 26rpx; padding: 28rpx 26rpx 38rpx; box-sizing: border-box; }
.tg-cur { text-align: center; font-size: 64rpx; font-weight: 700; color: #8B5E34; letter-spacing: 2rpx; margin: 4rpx 0 12rpx; }
.tg-label { font-size: 28rpx; color: #999; margin: 2rpx 2rpx 8rpx; }
.tg-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14rpx; }
.tg-grid-m { margin-bottom: 4rpx; }
.tg-cell { height: 88rpx; display: flex; align-items: center; justify-content: center; font-size: 36rpx; color: #1f2329; background: #f5f6f8; border-radius: 14rpx; }
.tg-cell.on { background: #E6EDF8; color: #2F5E96; font-weight: 700; box-shadow: inset 0 0 0 3rpx #3E6BA8; }
.tg-cell:not(.on):active { background: #E6EDF8; }
.tg-cell.disabled { color: #C4CAD2; background: #F7F8FA; box-shadow: none; }

/* 操作按钮 */
.pp-actions { display: flex; gap: 18rpx; margin-top: 24rpx; justify-content: center; }
.pp-actions .btn { flex: 0 0 60%; width: 60%; height: 84rpx; border: 0; border-radius: 18rpx; font-size: 30rpx; font-weight: 700; }
.btn-ghost { background: #F1F3F5; color: #5B6570; }
.btn-primary { background: #3E6BA8; color: #fff; }
</style>
