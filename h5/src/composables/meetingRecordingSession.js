import { reactive } from 'vue'
import { navigateTo } from '@/utils/navigate'

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
