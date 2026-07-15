// H5 长录音模块：替代小程序 wx.getRecorderManager。
// 用 getUserMedia + MediaRecorder，支持长时间录音（timeslice 持续累积 chunks）、
// 暂停/继续（iOS 兼容降级）、自管计时（暂停时不计、累计）。产出 Blob（webm/mp4），
// 由后端统一转码成 16k 单声道 mp3 供豆包 ASR——前端不强求采样率/声道。
import { ref, computed } from 'vue'
import recStore from '@/utils/recStore'

export function useRecorder() {
  const recording = ref(false)   // 正在录（含暂停时仍为 true，表示一次录音会话进行中）
  const paused = ref(false)
  const seconds = ref(0)
  const hasRecording = ref(false) // 已产出可上传的录音
  // 录音异常中断（来电抢占麦克风/切后台被挂起）：轨死亡事件秒级感知 + 数据流看门狗兜底。
  // 页面层 watch 它弹提示，避免"界面还在计时、实际早没在录"的假录音。
  const interrupted = ref(false)
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
  // 录音切片落地：传了 persistKey 的录音（会议主录音）每片顺手写 IndexedDB，
  // 页面被杀后可恢复；上传成功(reset)或主动重录(start)时清盘。存储失败静默——不影响录音本身。
  let persistSessionKey = ''
  // 屏幕常亮 + 中断检测的内部状态
  let wakeLock = null
  let healthTimer = null
  let lastChunkAt = 0
  let gotFirstChunk = false // 只有确认本机会按秒吐片(安卓)才启用数据流看门狗，iOS不吐片时靠轨事件

  // 录音中保持屏幕常亮：息屏是后台挂起的主要诱因之一。不支持的环境静默跳过。
  // 注意锁在页面切后台时会被系统自动释放，回前台需重新申请（visibilitychange 里补）。
  async function acquireWakeLock() {
    try {
      if (navigator.wakeLock && !wakeLock) {
        wakeLock = await navigator.wakeLock.request('screen')
        wakeLock.addEventListener('release', () => { wakeLock = null })
      }
    } catch (e) { /* 微信内可能不支持，忽略 */ }
  }
  function releaseWakeLock() {
    try { if (wakeLock) { wakeLock.release(); wakeLock = null } } catch (e) {}
  }
  function onVisibilityChange() {
    if (document.visibilityState !== 'visible') return
    if (recording.value) {
      acquireWakeLock()
      if (!paused.value) tickSeconds() // 回前台先把冻结的显示时长补到真实值
      healthCheck() // 回前台立即体检一次，别等下个周期
    }
  }
  function markInterrupted() {
    if (interrupted.value || !recording.value) return
    interrupted.value = true
    stopTimer() // 停计时，避免"没在录还在走秒"的假象
    // 中断时把时长校正为「实际采集到的」：墙钟会把后台没录上的死区也算进去，
    // 以最后一片数据的到达时刻为准（按秒吐片时误差≤1秒）。iOS 不吐片则保持原值。
    if (gotFirstChunk && startedWallMs && lastChunkAt > startedWallMs) {
      seconds.value = Math.max(0, Math.floor((lastChunkAt - startedWallMs - pausedAccumMs) / 1000))
    }
  }
  // 周期体检（3秒一次）：①麦克风轨全死 → 中断；②曾按秒吐片但已 >6 秒没新片 → 数据流停了，中断
  function healthCheck() {
    if (!recording.value || paused.value || interrupted.value) return
    const tracks = (stream && stream.getAudioTracks()) || []
    if (tracks.length && tracks.every((t) => t.readyState === 'ended')) { markInterrupted(); return }
    if (gotFirstChunk && Date.now() - lastChunkAt > 6000) markInterrupted()
  }
  function startHealthWatch() {
    stopHealthWatch()
    healthTimer = setInterval(healthCheck, 3000)
    document.addEventListener('visibilitychange', onVisibilityChange)
  }
  function stopHealthWatch() {
    if (healthTimer) { clearInterval(healthTimer); healthTimer = null }
    document.removeEventListener('visibilitychange', onVisibilityChange)
  }

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

  // 计时改「看墙钟」而非「每秒+1」：切后台 JS 被冻结时累加器会停走（真机实测卡在切出时刻），
  // 墙钟算法回前台一刷新就是真实经过时长。暂停时长单独累计扣除。
  let startedWallMs = 0
  let pausedAccumMs = 0
  let pauseStartedMs = 0
  function tickSeconds() {
    if (!startedWallMs) return
    seconds.value = Math.max(0, Math.floor((Date.now() - startedWallMs - pausedAccumMs) / 1000))
  }
  function startTimer() { stopTimer(); timer = setInterval(tickSeconds, 1000) }
  function stopTimer() { if (timer) { clearInterval(timer); timer = null } }

  function releaseStream() {
    if (stream) { stream.getTracks().forEach((t) => t.stop()); stream = null }
  }

  // getUserMedia 在个别 webview（尤其微信/企业微信）里可能既不 resolve 也不 reject → 表现为"点了没反应"。
  // 加超时兜底：ms 内没结果就以 TimeoutError 抛出，让上层给"用浏览器打开/查权限"的明确提示；
  // 若流在超时后才姗姗来迟，及时 stop 掉，避免白占用麦克风。
  function gumWithTimeout(constraints, ms) {
    return new Promise((resolve, reject) => {
      let settled = false
      const timer = setTimeout(() => {
        if (settled) return
        settled = true
        const e = new Error('调起麦克风超时未响应'); e.name = 'TimeoutError'; reject(e)
      }, ms)
      navigator.mediaDevices.getUserMedia(constraints).then(
        (s) => {
          if (settled) { try { s.getTracks().forEach((t) => t.stop()) } catch (_) {} return }
          settled = true; clearTimeout(timer); resolve(s)
        },
        (err) => { if (settled) return; settled = true; clearTimeout(timer); reject(err) }
      )
    })
  }

  // 开始一次新录音（会清掉上一次结果）。opts.persistKey：切片落地的会议标识（如 'committee-282'）
  async function start(opts) {
    if (recording.value) return
    if (!supported.value) throw new Error('当前浏览器不支持录音（需 HTTPS 且允许麦克风）')
    // 主动开新录 = 放弃上一段未清盘的落地数据（与内存 chunks 清空的语义一致）
    if (persistSessionKey) { recStore.clearSession(persistSessionKey); persistSessionKey = '' }
    try {
      stream = await gumWithTimeout({ audio: { channelCount: 1, sampleRate: 16000, echoCancellation: true, noiseSuppression: true } }, 12000)
    } catch (e) {
      // 约束过严（个别机型不认 sampleRate/channelCount）→ 放宽为最简约束再试一次；权限/超时类不重试，直接抛给上层
      if (e && (e.name === 'OverconstrainedError' || e.name === 'ConstraintNotSatisfiedError')) {
        stream = await gumWithTimeout({ audio: true }, 12000)
      } else {
        throw e
      }
    }
    chosenMime = pickMime()
    chunks = []
    resultBlob = null
    const persistKey = opts && opts.persistKey
    if (persistKey) {
      persistSessionKey = persistKey + ':' + Date.now()
      recStore.beginSession(persistSessionKey, { meetingKey: persistKey, mimeType: chosenMime || 'audio/webm' })
    }
    mediaRecorder = chosenMime ? new MediaRecorder(stream, { mimeType: chosenMime }) : new MediaRecorder(stream)
    mediaRecorder.ondataavailable = (e) => {
      if (e.data && e.data.size > 0) {
        chunks.push(e.data)
        lastChunkAt = Date.now()
        gotFirstChunk = true
        if (persistSessionKey) recStore.appendChunk(persistSessionKey, e.data)
      }
    }
    mediaRecorder.start(1000) // 每秒切片，长录音持续累积，避免一次性占内存
    recording.value = true
    paused.value = false
    hasRecording.value = false
    interrupted.value = false
    seconds.value = 0
    gotFirstChunk = false
    lastChunkAt = Date.now()
    startedWallMs = Date.now()
    pausedAccumMs = 0
    pauseStartedMs = 0
    // 麦克风轨死亡（来电抢占等）系统会发 ended 事件——比看门狗更快感知
    stream.getAudioTracks().forEach((t) => { t.addEventListener('ended', markInterrupted) })
    acquireWakeLock()
    startHealthWatch()
    startTimer()
  }

  function pause() {
    if (!recording.value || paused.value || !mediaRecorder) return
    try { mediaRecorder.pause(); paused.value = true; pauseStartedMs = Date.now(); stopTimer() } catch (e) { /* iOS 可能不支持，忽略 */ }
  }

  function resume() {
    if (!recording.value || !paused.value || !mediaRecorder) return
    try {
      mediaRecorder.resume()
      paused.value = false
      if (pauseStartedMs) { pausedAccumMs += Date.now() - pauseStartedMs; pauseStartedMs = 0 } // 暂停时长计入扣除项
      lastChunkAt = Date.now() // 暂停期间不吐片，重置基准防看门狗误报
      startTimer()
    } catch (e) { /* 忽略 */ }
  }

  // 结束录音，resolve { blob, ext, durationSec, mimeType }；无内容 resolve null。
  // ⚠ 麦克风轨死亡（来电抢占）后 Chrome 可能永不回调 onstop → 必须带超时兜底，
  //   否则中断处理里的 await stop() 会永远挂起、恢复弹窗弹不出来。切片全在内存里，自行定稿即可。
  function stop() {
    return new Promise((resolve) => {
      if (!mediaRecorder) { resolve(null); return }
      let settled = false
      const finalize = () => {
        if (settled) return
        settled = true
        stopTimer()
        stopHealthWatch()
        releaseWakeLock()
        const type = (mediaRecorder && (mediaRecorder.mimeType || chosenMime)) || 'audio/webm'
        resultBlob = new Blob(chunks, { type })
        recording.value = false
        paused.value = false
        hasRecording.value = resultBlob.size > 0
        releaseStream()
        resolve(hasRecording.value
          ? { blob: resultBlob, ext: extFromMime(type), durationSec: seconds.value, mimeType: type }
          : null)
      }
      mediaRecorder.onstop = finalize
      try { if (mediaRecorder.requestData) mediaRecorder.requestData() } catch (e) { /* 冲刷不足1秒的尾巴，失败无妨 */ }
      try { mediaRecorder.stop() } catch (e) { finalize(); return }
      setTimeout(finalize, 2000)
    })
  }

  function reset() {
    stopTimer()
    stopHealthWatch()
    releaseWakeLock()
    interrupted.value = false
    startedWallMs = 0
    pausedAccumMs = 0
    pauseStartedMs = 0
    chunks = []
    resultBlob = null
    seconds.value = 0
    recording.value = false
    paused.value = false
    hasRecording.value = false
    releaseStream()
    mediaRecorder = null
    // reset 的两个调用场景（上传成功 / 主动放弃）都意味着落地数据不再需要
    if (persistSessionKey) { recStore.clearSession(persistSessionKey); persistSessionKey = '' }
  }

  function getBlob() { return resultBlob }

  return {
    recording, paused, seconds, timeText, hasRecording, supported, interrupted,
    start, pause, resume, stop, reset, getBlob
  }
}
