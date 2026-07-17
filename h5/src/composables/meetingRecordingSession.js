import { reactive } from 'vue'
import { navigateTo } from '@/utils/navigate'
import recStore from '@/utils/recStore'

let discardHandler = null

export const meetingRecordingSession = reactive({
  meetingId: '',
  meetingTitle: '',
  recorderName: '',
  active: false,
  paused: false,
  timeText: '00:00',
  pageVisible: false,
  openRequest: 0
})

export function openMeetingRecording() {
  if (!meetingRecordingSession.meetingId) return
  // 即使当前仍在同一个会议路由（例如议题页），也通知会议组件切回录音页面。
  meetingRecordingSession.openRequest += 1
  navigateTo('/pages/meeting-live-quick/meeting-live-quick?type=committee&meetingId=' + meetingRecordingSession.meetingId)
}

// 会议进行页可能被 KeepAlive 缓存：离开录音页后 MediaRecorder 仍在该页面实例中运行。
// 删除会议时通过这里回调原页面实例，真正释放麦克风和计时器，而不只是隐藏悬浮窗。
export function registerMeetingRecordingDiscard(handler) {
  discardHandler = typeof handler === 'function' ? handler : null
  return () => { if (discardHandler === handler) discardHandler = null }
}

export async function discardMeetingRecording(meetingId) {
  const id = String(meetingId == null ? '' : meetingId)
  if (!id || String(meetingRecordingSession.meetingId || '') !== id) return false
  try { if (discardHandler) await discardHandler(id) } catch (e) { /* 状态清理仍须继续 */ }
  const sessions = await recStore.listSessions('committee-' + id)
  await Promise.all(sessions.map((s) => recStore.clearSession(s.key)))
  meetingRecordingSession.meetingId = ''
  meetingRecordingSession.meetingTitle = ''
  meetingRecordingSession.recorderName = ''
  meetingRecordingSession.active = false
  meetingRecordingSession.paused = false
  meetingRecordingSession.timeText = '00:00'
  meetingRecordingSession.pageVisible = false
  return true
}
