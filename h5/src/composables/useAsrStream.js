// 真·实时流式语音识别（边说边出字）。
// 浏览器用 AudioWorklet（不支持则降级 ScriptProcessor）采集麦克风 → 重采样到 16kHz/16bit/mono raw PCM
// → 经同源 WebSocket 代理 /asr-ws 推给 ocr-asr-service 的 /v1/asr/stream（代理层注入内网令牌）。
// 服务端回 JSON 事件：{type:'partial',text} 实时累积、{type:'final',text} 收尾、{type:'error'} 出错（含静音）。
//
// 用法：
//   const asr = useAsrStream()
//   await asr.start()                 // 开麦 + 连 WS + 推流；liveText 实时刷新
//   const r = await asr.stop()        // 发 end、等 final；返回 { text, noSound, serviceError }
//   asr.cancel()                      // 中途放弃，释放麦克风与连接
import { ref, computed } from 'vue'

export function useAsrStream() {
  const AC = typeof window !== 'undefined' ? (window.AudioContext || window.webkitAudioContext) : null
  const supported = ref(
    typeof navigator !== 'undefined' &&
    !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia) &&
    typeof WebSocket !== 'undefined' && !!AC
  )

  const listening = ref(false)   // 正在采集并推流
  const finishing = ref(false)   // 已发 end、等最终结果
  const liveText = ref('')       // 实时累积识别文字（partial）
  const seconds = ref(0)

  let ws = null
  let audioCtx = null
  let stream = null
  let srcNode = null
  let procNode = null
  let muteNode = null
  let workletUrl = null
  let secTimer = null

  let gotPartial = false
  let maxLevel = 0            // 本地测到的最大音量（静音兜底判断）
  let finalText = ''
  let errText = ''
  let settled = false        // final/error 是否已到
  let finalResolve = null
  let finishTimer = null

  const timeText = computed(() => {
    const s = seconds.value
    const mm = String(Math.floor(s / 60)).padStart(2, '0')
    const ss = String(s % 60).padStart(2, '0')
    return mm + ':' + ss
  })

  function wsUrl() {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    return proto + '://' + location.host + '/asr-ws'
  }

  function startSec() { stopSec(); seconds.value = 0; secTimer = setInterval(() => { seconds.value += 1 }, 1000) }
  function stopSec() { if (secTimer) { clearInterval(secTimer); secTimer = null } }

  // Float32（任意采样率）→ 16kHz Int16 PCM
  function toPcm16k(float32, inRate) {
    let src = float32
    if (inRate !== 16000) {
      const ratio = inRate / 16000
      const outLen = Math.max(1, Math.floor(float32.length / ratio))
      const out = new Float32Array(outLen)
      for (let i = 0; i < outLen; i++) out[i] = float32[Math.floor(i * ratio)] || 0
      src = out
    }
    const i16 = new Int16Array(src.length)
    for (let i = 0; i < src.length; i++) {
      let s = src[i]
      if (s > 1) s = 1; else if (s < -1) s = -1
      i16[i] = s < 0 ? s * 0x8000 : s * 0x7fff
    }
    return i16
  }

  function onPcm(float32) {
    for (let i = 0; i < float32.length; i++) {
      const a = float32[i] < 0 ? -float32[i] : float32[i]
      if (a > maxLevel) maxLevel = a
    }
    if (!ws || ws.readyState !== 1) return
    try { ws.send(toPcm16k(float32, audioCtx.sampleRate).buffer) } catch (e) { /* 忽略单帧发送失败 */ }
  }

  function handleMsg(data) {
    let ev
    try { ev = JSON.parse(data) } catch (e) { return }
    if (ev.type === 'partial') {
      gotPartial = true
      if (typeof ev.text === 'string' && ev.text.length >= liveText.value.length) liveText.value = ev.text
    } else if (ev.type === 'final') {
      finalText = ev.text || liveText.value || ''
      resolveFinal()
    } else if (ev.type === 'error') {
      errText = ev.error || '识别失败'
      resolveFinal()
    }
  }

  function resolveFinal() {
    if (settled) return
    settled = true
    if (finishTimer) { clearTimeout(finishTimer); finishTimer = null }
    if (finalResolve) { const r = finalResolve; finalResolve = null; r() }
  }

  async function setupNode() {
    srcNode = audioCtx.createMediaStreamSource(stream)
    muteNode = audioCtx.createGain()
    muteNode.gain.value = 0 // 静音：让音频图被拉动而不外放（否则 worklet/processor 不跑）
    // 首选 AudioWorklet（低延迟、不废弃）
    if (audioCtx.audioWorklet && typeof AudioWorkletNode !== 'undefined') {
      try {
        const code = 'class P extends AudioWorkletProcessor{process(i){const c=i[0]&&i[0][0];if(c)this.port.postMessage(c.slice(0));return true}}registerProcessor("pcm-worklet",P)'
        workletUrl = URL.createObjectURL(new Blob([code], { type: 'application/javascript' }))
        await audioCtx.audioWorklet.addModule(workletUrl)
        procNode = new AudioWorkletNode(audioCtx, 'pcm-worklet')
        procNode.port.onmessage = (e) => onPcm(e.data)
        srcNode.connect(procNode); procNode.connect(muteNode); muteNode.connect(audioCtx.destination)
        return
      } catch (e) { /* 降级到 ScriptProcessor */ }
    }
    // 降级：ScriptProcessorNode（老 iOS/微信 WebView 兼容）
    const bufSize = 4096
    procNode = audioCtx.createScriptProcessor(bufSize, 1, 1)
    procNode.onaudioprocess = (e) => onPcm(e.inputBuffer.getChannelData(0))
    srcNode.connect(procNode); procNode.connect(muteNode); muteNode.connect(audioCtx.destination)
  }

  // 开麦 + 连 WS + 推流。失败抛异常（麦克风被拒 / WS 连不上）。
  async function start() {
    if (!supported.value) throw new Error('unsupported')
    reset()
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true }
    })
    audioCtx = new AC()
    if (audioCtx.state === 'suspended') { try { await audioCtx.resume() } catch (e) {} }

    ws = new WebSocket(wsUrl())
    ws.binaryType = 'arraybuffer'
    await new Promise((resolve, reject) => {
      const to = setTimeout(() => reject(new Error('ws-timeout')), 8000)
      ws.onopen = () => { clearTimeout(to); resolve() }
      ws.onerror = () => { clearTimeout(to); reject(new Error('ws-error')) }
    })
    ws.onmessage = (ev) => handleMsg(ev.data)
    ws.onclose = () => { if (!settled && finishing.value) resolveFinal() } // final 前断线 → 收尾兜底

    await setupNode()
    listening.value = true
    startSec()
  }

  // 说完了：停止采集、发 end、等 final/error。返回 { text, noSound, serviceError }。
  async function stop() {
    if (finishing.value) return { text: '', noSound: false, serviceError: '' }
    finishing.value = true
    listening.value = false
    stopSec()
    teardownAudio() // 停止推流（保留 ws 收最终结果）
    if (ws && ws.readyState === 1) { try { ws.send(JSON.stringify({ type: 'end' })) } catch (e) {} }

    await new Promise((resolve) => {
      finalResolve = resolve
      finishTimer = setTimeout(() => resolveFinal(), 10000) // 兜底：10s 没结果就收尾
    })

    const text = (finalText || '').trim()
    const silenceErr = /静音|空音频|未识别到任何文本/.test(errText)
    const noSound = !text && (silenceErr || (!gotPartial && maxLevel < 0.02) || errText === '')
    const serviceError = !text && !noSound ? errText : ''
    cleanup()
    finishing.value = false
    return { text, noSound, serviceError }
  }

  function cancel() { cleanup(); listening.value = false; finishing.value = false; stopSec() }

  function teardownAudio() {
    try { if (procNode) { procNode.onaudioprocess = null; if (procNode.port) procNode.port.onmessage = null; procNode.disconnect() } } catch (e) {}
    try { if (srcNode) srcNode.disconnect() } catch (e) {}
    try { if (muteNode) muteNode.disconnect() } catch (e) {}
    try { if (stream) stream.getTracks().forEach((t) => t.stop()) } catch (e) {}
    procNode = null; srcNode = null; muteNode = null; stream = null
    if (workletUrl) { try { URL.revokeObjectURL(workletUrl) } catch (e) {} workletUrl = null }
  }

  function cleanup() {
    teardownAudio()
    if (finishTimer) { clearTimeout(finishTimer); finishTimer = null }
    try { if (audioCtx) audioCtx.close() } catch (e) {}
    audioCtx = null
    try { if (ws) { ws.onmessage = null; ws.onclose = null; ws.onerror = null; if (ws.readyState <= 1) ws.close() } } catch (e) {}
    ws = null
  }

  function reset() {
    cleanup()
    stopSec()
    liveText.value = ''
    seconds.value = 0
    gotPartial = false
    maxLevel = 0
    finalText = ''
    errText = ''
    settled = false
    finalResolve = null
    listening.value = false
    finishing.value = false
  }

  return { supported, listening, finishing, liveText, seconds, timeText, start, stop, cancel }
}
