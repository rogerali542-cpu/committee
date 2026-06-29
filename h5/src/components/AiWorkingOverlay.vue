<template>
  <div class="aio-mask" v-if="shown">
    <div class="aio-card">
      <span class="aio-pt" style="top:8%;left:10%;width:8rpx;height:8rpx"></span>
      <span class="aio-pt" style="top:14%;left:86%;width:10rpx;height:10rpx;animation-delay:.6s"></span>
      <span class="aio-pt" style="top:6%;left:60%;width:6rpx;height:6rpx;animation-delay:1.1s"></span>
      <span class="aio-pt" style="top:20%;left:24%;width:6rpx;height:6rpx;animation-delay:.9s"></span>

      <div class="aio-hdr">
        <span class="aio-title">豆包正在为您整理纪要</span>
        <span class="aio-badge"><i class="aio-bdot"></i>{{ done ? '已完成' : '草稿生成中' }}</span>
      </div>

      <!-- 进行中 -->
      <template v-if="!done">
        <svg class="aio-core" viewBox="0 0 152 118" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
          <defs>
            <radialGradient id="aioOrb" cx="48%" cy="38%" r="62%"><stop offset="0" stop-color="#EAF4FF"/><stop offset="40%" stop-color="#62A6FF"/><stop offset="100%" stop-color="#1B47AE"/></radialGradient>
            <linearGradient id="aioRing" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#7FE6FF"/><stop offset="100%" stop-color="#3B82F6" stop-opacity="0"/></linearGradient>
          </defs>
          <circle class="aio-halo" cx="76" cy="59" r="50" fill="#3E78E6" opacity=".3"/>
          <g class="aio-ring"><circle cx="76" cy="59" r="52" fill="none" stroke="url(#aioRing)" stroke-width="3" stroke-dasharray="72 200" stroke-linecap="round"/><circle cx="76" cy="7" r="3.2" fill="#9FE6FF"/><circle cx="128" cy="59" r="2.4" fill="#7FD3FF"/></g>
          <g class="aio-ring2"><circle cx="76" cy="59" r="42" fill="none" stroke="#6FD3FF" stroke-width="1.5" stroke-dasharray="12 260" opacity=".7"/><circle cx="34" cy="59" r="2.2" fill="#A9E6FF"/></g>
          <circle class="aio-orb" cx="76" cy="59" r="29" fill="url(#aioOrb)"/>
          <ellipse cx="68" cy="48" rx="11" ry="6.5" fill="#fff" opacity=".5"/>
        </svg>

        <span class="aio-say">{{ say }} <i>·</i><i>·</i><i>·</i></span>

        <div class="aio-metrics">
          <div class="aio-m"><span class="aio-ml">已耗时</span><span class="aio-mv">{{ elapsed }}</span></div>
          <div class="aio-m"><span class="aio-ml">预计完成</span><span class="aio-mv eta">{{ eta }}</span></div>
          <div class="aio-m"><span class="aio-ml">已消耗 token</span><span class="aio-mv">{{ tokens }}</span></div>
          <div class="aio-m"><span class="aio-ml">{{ metricLabel }}</span><span class="aio-mv">{{ metricVal }}</span></div>
        </div>
      </template>

      <!-- 完成 -->
      <template v-else>
        <div class="aio-done-core">
          <div class="aio-check">✓</div>
        </div>
        <span class="aio-say done">{{ doneSay }}</span>
        <div class="aio-done-sub">用时 {{ elapsed }} · 共消耗 {{ tokens }} token</div>
      </template>

      <div class="aio-steps">
        <div class="aio-step" :class="stepClass(1)"><span class="aio-sdot">{{ stepClass(1) === 'done' ? '✓' : '1' }}</span><span class="aio-slabel">上传录音</span></div>
        <div class="aio-step" :class="stepClass(2)"><span class="aio-sdot">{{ stepClass(2) === 'done' ? '✓' : '2' }}</span><span class="aio-slabel">识别转写</span></div>
        <div class="aio-step" :class="stepClass(3)"><span class="aio-sdot">{{ stepClass(3) === 'done' ? '✓' : '3' }}</span><span class="aio-slabel">提炼议题</span></div>
        <div class="aio-step" :class="stepClass(4)"><span class="aio-sdot">{{ stepClass(4) === 'done' ? '✓' : '4' }}</span><span class="aio-slabel">生成纪要</span></div>
      </div>

      <div class="aio-prog">
        <div class="aio-track"><div class="aio-fill" :style="{ width: pct + '%' }"></div></div>
        <span class="aio-pct">{{ pct }}%</span>
      </div>

      <button v-if="done" class="aio-btn" @click="onConfirm">{{ doneBtn }} →</button>
      <div v-else class="aio-by">由 <b>豆包大模型</b> 提供 · 完成后自动通知您 🔔</div>
    </div>
  </div>
</template>

<script setup>
// "AI 工作中"悬浮等待卡片：录音转写(asr) / 生成纪要(gen) 两个时刻复用。
// 浮动卡片 + 半透明遮罩罩在原页上方；AI 完成后切"完成"态、出确认按钮，点击才关闭并 emit('confirm') 进入下一步。
// 深蓝科技风 + 发光能量核心 + 实时运行数据(耗时真、token/预计完成估算剧场化)；进度只升不退；prefers-reduced-motion 降级。
// 入参 active = AI 任务进行中；active 由 true→false 视为"已完成"(假定成功)。另存档"全屏版"见记忆 ai-working-screen-design。
import { ref, computed, watch, onUnmounted } from 'vue'

const props = defineProps({
  active: { type: Boolean, default: false },
  phase: { type: String, default: 'asr' },
  audioDurSec: { type: Number, default: 0 },        // 录音时长(秒)
  audioFileSizeByte: { type: Number, default: 0 }   // 录音文件大小(bytes)
})
const emit = defineEmits(['confirm'])

const CFG = {
  asr: { target: 25, tok: 205, say: '正在为您识别录音、转写文字', lab: '已解析音频', max: 25, unit: ' 分钟', dec: 0, active: 2, doneSay: '录音已转写完成', doneBtn: '查看转写结果' },
  gen: { target: 112, tok: 268, say: '正在为您提炼议题、生成纪要草稿', lab: '上下文理解', pct: true, active: 4, doneSay: '会议纪要草稿已生成', doneBtn: '查看纪要' }
}

const shown = ref(false)
const done = ref(false)
const sec = ref(0)
let timer = null

const cfg = computed(() => {
  const base = CFG[props.phase] || CFG.asr
  if (props.phase === 'asr') {
    const sz = props.audioFileSizeByte || 0
    const dur = props.audioDurSec || 0
    // 上传时间：按 500KB/s 估算（4G/WiFi 保守值）
    const uploadSec = sz > 0 ? sz / 500000 : 0
    // 有实际时长则直接用；否则按 48kbps(≈6000 bytes/s) 从文件大小估算
    const estDur = dur > 0 ? dur : (sz > 0 ? sz / 6000 : 0)
    // Doubao ASR 离线批处理约 20x 实时速
    const procSec = estDur > 0 ? estDur / 20 : 0
    const total = uploadSec + procSec
    if (total > 0) {
      const overrides = { target: Math.max(10, Math.ceil(total + 8)) }
      if (estDur > 0) {
        // 「已解析音频」从 0 匀速涨到实际录音时长，视觉上贴合真实进度
        overrides.max = parseFloat((estDur / 60).toFixed(1))
        overrides.dec = 1
      }
      return { ...base, ...overrides }
    }
  }
  return CFG[props.phase] || CFG.asr
})
const frac = computed(() => (done.value ? 1 : Math.min(sec.value / cfg.value.target, 1)))

function mmss(s) { const m = Math.floor(s / 60), x = s % 60; return (m < 10 ? '0' : '') + m + ':' + (x < 10 ? '0' : '') + x }
function comma(n) { return Math.round(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',') }

const say = computed(() => cfg.value.say)
const doneSay = computed(() => cfg.value.doneSay)
const doneBtn = computed(() => cfg.value.doneBtn)
const elapsed = computed(() => mmss(sec.value))
const eta = computed(() => { const r = cfg.value.target - sec.value; return r > 3 ? '还需 ' + mmss(r) : '即将完成' })
const tokens = computed(() => comma(sec.value * cfg.value.tok))
const pct = computed(() => (done.value ? 100 : Math.min(96, Math.round((1 - Math.pow(1 - frac.value, 2.2)) * 96))))
const metricLabel = computed(() => cfg.value.lab)
const metricVal = computed(() => {
  if (cfg.value.pct) return Math.min(99, Math.round((1 - Math.pow(1 - frac.value, 1.7)) * 99)) + '%'
  return (cfg.value.dec ? (frac.value * cfg.value.max).toFixed(1) : Math.round(frac.value * cfg.value.max)) + cfg.value.unit
})

function stepClass(i) { if (done.value) return 'done'; const a = cfg.value.active; return i < a ? 'done' : (i === a ? 'active' : '') }

function start() { stop(); sec.value = 0; done.value = false; timer = setInterval(() => { sec.value += 1 }, 1000) }
function stop() { if (timer) { clearInterval(timer); timer = null } }
function finish() { stop(); done.value = true } // 冻结耗时，切完成态等用户确认

watch(() => props.active, (a) => {
  if (a) { shown.value = true; start() }
  else if (shown.value) { finish() } // 进行中 → 完成
}, { immediate: true })
watch(() => props.phase, () => { if (props.active) start() })

function onConfirm() { shown.value = false; done.value = false; emit('confirm') }

onUnmounted(stop)
</script>

<style scoped>
.aio-mask {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(6, 14, 36, 0.66);
  display: flex; align-items: center; justify-content: center;
  padding: 40rpx; box-sizing: border-box;
}
.aio-card {
  position: relative; width: 640rpx; max-width: 92%; max-height: 88vh; overflow: hidden;
  background: radial-gradient(135% 80% at 50% 8%, #244089 0%, #122456 52%, #0A1430 100%);
  border: 2rpx solid rgba(120, 170, 255, 0.28);
  border-radius: 36rpx;
  box-shadow: 0 24rpx 80rpx rgba(8, 20, 60, 0.6), 0 0 60rpx rgba(60, 120, 230, 0.25);
  padding: 36rpx 34rpx 30rpx;
  box-sizing: border-box;
  display: flex; flex-direction: column; align-items: center;
}
.aio-pt { position: absolute; border-radius: 50%; background: #8FC6FF; box-shadow: 0 0 12rpx 2rpx rgba(120,180,255,.8); animation: aioTwk 3.2s ease-in-out infinite; }
.aio-hdr { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.aio-title { font-size: 36rpx; font-weight: 700; color: #EAF2FF; text-shadow: 0 0 24rpx rgba(90,150,255,.5); }
.aio-badge { display: inline-flex; align-items: center; gap: 8rpx; background: rgba(120,170,255,.16); color: #AED2FF; font-size: 24rpx; font-weight: 600; padding: 7rpx 18rpx; border-radius: 999rpx; border: 2rpx solid rgba(140,185,255,.28); white-space: nowrap; }
.aio-bdot { width: 12rpx; height: 12rpx; border-radius: 50%; background: #5FD2FF; box-shadow: 0 0 10rpx #5FD2FF; animation: aioBlink 1.4s ease-in-out infinite; }
.aio-core { display: block; width: 280rpx; height: 218rpx; margin: 6rpx 0 0; }
.aio-halo, .aio-ring, .aio-ring2, .aio-orb { transform-box: fill-box; transform-origin: center; }
.aio-halo { animation: aioHalo 3s ease-in-out infinite; }
.aio-ring { animation: aioSpin 8s linear infinite; }
.aio-ring2 { animation: aioSpinR 12s linear infinite; }
.aio-orb { animation: aioBreathe 3s ease-in-out infinite; }
.aio-done-core { width: 100%; display: flex; justify-content: center; padding: 22rpx 0 10rpx; }
.aio-check { width: 150rpx; height: 150rpx; border-radius: 50%; background: radial-gradient(circle at 50% 38%, #6FE6C8 0%, #22D3A6 60%, #109A78 100%); color: #053024; font-size: 84rpx; font-weight: 800; display: flex; align-items: center; justify-content: center; box-shadow: 0 0 40rpx rgba(34,211,166,.55); }
.aio-say { display: block; text-align: center; font-size: 34rpx; color: #EDF3FF; font-weight: 600; margin: 12rpx 0 22rpx; }
.aio-say.done { color: #BFF3E2; margin-top: 8rpx; }
.aio-say i { font-style: normal; color: #7FB6FF; opacity: .4; animation: aioDot 1.4s infinite; }
.aio-say i:nth-child(2) { animation-delay: .2s; } .aio-say i:nth-child(3) { animation-delay: .4s; }
.aio-done-sub { font-size: 26rpx; color: #9FB7DC; margin-bottom: 24rpx; }
.aio-metrics { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; width: 100%; margin-bottom: 28rpx; }
.aio-m { background: rgba(255,255,255,.055); border: 2rpx solid rgba(150,190,255,.18); border-radius: 16rpx; padding: 16rpx 20rpx; }
.aio-ml { display: block; font-size: 23rpx; color: #7E9BC8; }
.aio-mv { display: block; font-size: 36rpx; font-weight: 700; color: #CFE9FF; margin-top: 6rpx; text-shadow: 0 0 16rpx rgba(95,170,255,.45); font-variant-numeric: tabular-nums; }
.aio-mv.eta { color: #7FE6C8; text-shadow: 0 0 16rpx rgba(60,210,170,.45); }
.aio-steps { display: flex; align-items: flex-start; justify-content: space-between; position: relative; width: 100%; margin-bottom: 26rpx; }
.aio-steps::before { content: ""; position: absolute; top: 28rpx; left: 12%; right: 12%; height: 4rpx; background: rgba(255,255,255,.14); }
.aio-step { position: relative; display: flex; flex-direction: column; align-items: center; gap: 10rpx; width: 25%; z-index: 1; }
.aio-sdot { width: 56rpx; height: 56rpx; border-radius: 50%; background: rgba(255,255,255,.06); border: 3rpx solid rgba(255,255,255,.2); color: #90A6CE; display: flex; align-items: center; justify-content: center; font-size: 26rpx; font-weight: 700; }
.aio-step.done .aio-sdot { background: #22D3A6; border-color: #22D3A6; color: #062b20; box-shadow: 0 0 18rpx rgba(34,211,166,.5); }
.aio-step.active .aio-sdot { background: #3B82F6; border-color: #7FB4FF; color: #fff; animation: aioPulse 1.8s ease-out infinite; }
.aio-slabel { font-size: 24rpx; color: #8095BB; text-align: center; }
.aio-step.active .aio-slabel { color: #BFE0FF; font-weight: 600; }
.aio-step.done .aio-slabel { color: #6FE6C6; }
.aio-prog { display: flex; align-items: center; gap: 16rpx; width: 100%; margin-bottom: 22rpx; }
.aio-track { flex: 1; height: 16rpx; background: rgba(255,255,255,.12); border-radius: 10rpx; overflow: hidden; }
.aio-fill { height: 100%; background: linear-gradient(90deg, #5FD2FF, #3B82F6); border-radius: 10rpx; box-shadow: 0 0 20rpx rgba(95,170,255,.7); transition: width .9s ease; }
.aio-pct { font-size: 34rpx; font-weight: 700; color: #CFE6FF; min-width: 78rpx; text-align: right; font-variant-numeric: tabular-nums; }
.aio-by { text-align: center; font-size: 23rpx; color: #7494C8; }
.aio-by b { color: #9FD0FF; font-weight: 600; }
.aio-btn { width: 100%; height: 92rpx; border: none; border-radius: 18rpx; background: linear-gradient(90deg, #4FC0FF, #2E73E6); color: #fff; font-size: 32rpx; font-weight: 700; box-shadow: 0 8rpx 28rpx rgba(46,115,230,.45); }
.aio-btn:active { background: linear-gradient(90deg, #3FA8EC, #245FC4); }
@keyframes aioBreathe { 0%,100% { transform: scale(1); } 50% { transform: scale(1.06); } }
@keyframes aioHalo { 0%,100% { opacity: .32; transform: scale(.92); } 50% { opacity: .68; transform: scale(1.05); } }
@keyframes aioSpin { to { transform: rotate(360deg); } }
@keyframes aioSpinR { to { transform: rotate(-360deg); } }
@keyframes aioPulse { 0% { box-shadow: 0 0 0 0 rgba(95,170,255,.6); } 70% { box-shadow: 0 0 0 20rpx rgba(95,170,255,0); } 100% { box-shadow: 0 0 0 0 rgba(95,170,255,0); } }
@keyframes aioTwk { 0%,100% { opacity: .2; transform: scale(.7); } 50% { opacity: 1; transform: scale(1); } }
@keyframes aioDot { 0%,100% { opacity: .35; } 50% { opacity: 1; } }
@keyframes aioBlink { 0%,100% { opacity: 1; } 50% { opacity: .3; } }
@media (prefers-reduced-motion: reduce) {
  .aio-halo, .aio-ring, .aio-ring2, .aio-orb, .aio-pt, .aio-step.active .aio-sdot, .aio-bdot, .aio-say i { animation: none !important; }
  .aio-mask { backdrop-filter: none; }
}
</style>
