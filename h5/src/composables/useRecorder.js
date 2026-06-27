// H5 长录音模块：替代小程序 wx.getRecorderManager。
// 用 getUserMedia + MediaRecorder，支持长时间录音（timeslice 持续累积 chunks）、
// 暂停/继续（iOS 兼容降级）、自管计时（暂停时不计、累计）。产出 Blob（webm/mp4），
// 由后端统一转码成 16k 单声道 mp3 供豆包 ASR——前端不强求采样率/声道。
import { ref, computed } from 'vue'

export function useRecorder() {
  const recording = ref(false)   // 正在录（含暂停时仍为 true，表示一次录音会话进行中）
  const paused = ref(false)
  const seconds = ref(0)
  const hasRecording = ref(false) // 已产出可上传的录音
  const supported = ref(
    typeof navigator !== 'undefined' &&
    !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia) &&
    typeof window !== 'undefined' && !!window.MediaRecorder
  )

  let mediaRecorder = null
  let stream = null
  let chunks = []
  let timer = null
  let chosenMime = ''
  let resultBlob = null

  const timeText = computed(() => {
    const s = seconds.value
    const mm = String(Math.floor(s / 60)).padStart(2, '0')
    const ss = String(s % 60).padStart(2, '0')
    return mm + ':' + ss
  })

  function pickMime() {
    const candidates = [
      'audio/webm;codecs=opus',
      'audio/webm',
      'audio/mp4',            // iOS Safari / 微信 iOS
      'audio/ogg;codecs=opus' // Firefox
    ]
    if (window.MediaRecorder && MediaRecorder.isTypeSupported) {
      for (const t of candidates) {
        if (MediaRecorder.isTypeSupported(t)) return t
      }
    }
    return '' // 交给浏览器默认
  }

  function extFromMime(t) {
    if (!t) return 'webm'
    if (t.includes('mp4')) return 'm4a'
    if (t.includes('ogg')) return 'ogg'
    if (t.includes('mpeg')) return 'mp3'
    return 'webm'
  }

  function startTimer() { stopTimer(); timer = setInterval(() => { seconds.value += 1 }, 1000) }
  function stopTimer() { if (timer) { clearInterval(timer); timer = null } }

  function releaseStream() {
    if (stream) { stream.getTracks().forEach((t) => t.stop()); stream = null }
  }

  // 开始一次新录音（会清掉上一次结果）
  async function start() {
    if (recording.value) return
    if (!supported.value) throw new Error('当前浏览器不支持录音（需 HTTPS 且允许麦克风）')
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, sampleRate: 16000, echoCancellation: true, noiseSuppression: true }
    })
    chosenMime = pickMime()
    chunks = []
    resultBlob = null
    mediaRecorder = chosenMime ? new MediaRecorder(stream, { mimeType: chosenMime }) : new MediaRecorder(stream)
    mediaRecorder.ondataavailable = (e) => { if (e.data && e.data.size > 0) chunks.push(e.data) }
    mediaRecorder.start(1000) // 每秒切片，长录音持续累积，避免一次性占内存
    recording.value = true
    paused.value = false
    hasRecording.value = false
    seconds.value = 0
    startTimer()
  }

  function pause() {
    if (!recording.value || paused.value || !mediaRecorder) return
    try { mediaRecorder.pause(); paused.value = true; stopTimer() } catch (e) { /* iOS 可能不支持，忽略 */ }
  }

  function resume() {
    if (!recording.value || !paused.value || !mediaRecorder) return
    try { mediaRecorder.resume(); paused.value = false; startTimer() } catch (e) { /* 忽略 */ }
  }

  // 结束录音，resolve { blob, ext, durationSec, mimeType }；无内容 resolve null
  function stop() {
    return new Promise((resolve) => {
      if (!mediaRecorder) { resolve(null); return }
      mediaRecorder.onstop = () => {
        stopTimer()
        const type = mediaRecorder.mimeType || chosenMime || 'audio/webm'
        resultBlob = new Blob(chunks, { type })
        recording.value = false
        paused.value = false
        hasRecording.value = resultBlob.size > 0
        releaseStream()
        resolve(hasRecording.value
          ? { blob: resultBlob, ext: extFromMime(type), durationSec: seconds.value, mimeType: type }
          : null)
      }
      try { mediaRecorder.stop() } catch (e) { stopTimer(); resolve(null) }
    })
  }

  function reset() {
    stopTimer()
    chunks = []
    resultBlob = null
    seconds.value = 0
    recording.value = false
    paused.value = false
    hasRecording.value = false
    releaseStream()
    mediaRecorder = null
  }

  function getBlob() { return resultBlob }

  return {
    recording, paused, seconds, timeText, hasRecording, supported,
    start, pause, resume, stop, reset, getBlob
  }
}
