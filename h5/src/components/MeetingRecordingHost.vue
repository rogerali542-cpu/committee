<template>
  <button ref="entryEl" v-if="show" class="global-rec-entry" :class="{ paused: session.paused, dragging }" :style="positionStyle"
    @click="handleClick" @pointerdown="startDrag">
    <span class="global-rec-dot"></span>
    <span class="global-rec-name">{{ session.paused ? '已暂停：' : '录音中：' }}{{ session.recorderName || '本人' }}</span>
  </button>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { meetingRecordingSession as session, openMeetingRecording, discardMeetingRecording } from '@/composables/meetingRecordingSession'
import { getStorage } from '@/utils/storage'
const route = useRoute()
// 录音悬浮入口只属于会议现场流程。签到、会议进行和议题表决共用
// /meeting-live-quick；离开该流程后录音会话可按原逻辑保留，但不在其他业务页露出浮窗。
const isMeetingFlowRoute = computed(() => route.path === '/meeting-live-quick')
const show = computed(() => session.active && isMeetingFlowRoute.value && !session.pageVisible)
const entryEl = ref(null)
const dragging = ref(false)
const position = ref(null)
const positionStyle = computed(() => position.value
  ? { left: position.value.left + 'px', top: position.value.top + 'px', right: 'auto', bottom: 'auto' }
  : null)
let drag = null
let moved = false

// 测试时频繁切换身份：旧录音不得跨身份残留。immediate 也能清理由热更新前遗留的会话。
watch(() => [session.meetingId, session.recorderName], async ([meetingId, recorderName]) => {
  const role = getStorage('activeRole', null) || {}
  if (meetingId && recorderName && role.realName && recorderName !== role.realName) {
    await discardMeetingRecording(meetingId)
  }
}, { immediate: true })

function startDrag(e) {
  if (!entryEl.value) return
  const rect = entryEl.value.getBoundingClientRect()
  drag = { pointerId: e.pointerId, startX: e.clientX, startY: e.clientY, dx: e.clientX - rect.left, dy: e.clientY - rect.top, width: rect.width, height: rect.height }
  moved = false
  dragging.value = true
  entryEl.value.setPointerCapture?.(e.pointerId)
  window.addEventListener('pointermove', moveDrag)
  window.addEventListener('pointerup', endDrag, { once: true })
}

function moveDrag(e) {
  if (!drag || e.pointerId !== drag.pointerId) return
  const margin = 6
  const left = Math.min(Math.max(margin, e.clientX - drag.dx), window.innerWidth - drag.width - margin)
  const top = Math.min(Math.max(margin, e.clientY - drag.dy), window.innerHeight - drag.height - margin)
  if (Math.abs(e.clientX - drag.startX) > 4 || Math.abs(e.clientY - drag.startY) > 4) moved = true
  position.value = { left, top }
}

function endDrag() {
  dragging.value = false
  drag = null
  window.removeEventListener('pointermove', moveDrag)
}

function handleClick() {
  if (moved) { moved = false; return }
  openMeetingRecording()
}
</script>

<style scoped>
.global-rec-entry { position:fixed; right:20rpx; bottom:calc(142rpx + env(safe-area-inset-bottom)); z-index:2500; max-width:62vw; display:flex; align-items:center; gap:9rpx; border:0; border-radius:999rpx; padding:10rpx 14rpx; background:rgba(128,43,38,0.94); color:#fff; box-shadow:0 6rpx 18rpx rgba(64,20,18,0.22); font-family:inherit; cursor:grab; touch-action:none; user-select:none; }
.global-rec-entry.dragging { cursor:grabbing; transform:scale(1.03); box-shadow:0 9rpx 24rpx rgba(64,20,18,0.3); }
.global-rec-entry.paused { background:rgba(72,78,88,0.96); }
.global-rec-dot { width:12rpx; height:12rpx; flex-shrink:0; border-radius:50%; background:#FFB4AE; box-shadow:0 0 0 5rpx rgba(255,180,174,0.14); animation:globalRecPulse 1.25s ease-in-out infinite; }
.paused .global-rec-dot { background:#D5DAE1; box-shadow:none; animation:none; }
.global-rec-name { min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:24rpx; font-weight:600; line-height:1.35; }
@keyframes globalRecPulse { 50% { opacity:.55; transform:scale(.86); } }
</style>
