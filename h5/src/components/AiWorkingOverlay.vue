<template>
  <div class="aio-mask" :class="{ 'theme-party': theme === 'party', 'is-news': phase === 'news' && !done }" v-if="shown && !minimized">
    <div class="aio-card" :class="{ 'theme-party': theme === 'party', 'is-done': done, 'is-news': phase === 'news' && !done }">
      <button class="aio-min" @click="minimize" aria-label="最小化">–</button>
      <button class="aio-close" @click="onClose" aria-label="关闭">×</button>
      <span class="aio-pt" style="top:8%;left:10%;width:8rpx;height:8rpx"></span>
      <span class="aio-pt" style="top:14%;left:86%;width:10rpx;height:10rpx;animation-delay:.6s"></span>
      <span class="aio-pt" style="top:6%;left:60%;width:6rpx;height:6rpx;animation-delay:1.1s"></span>
      <span class="aio-pt" style="top:20%;left:24%;width:6rpx;height:6rpx;animation-delay:.9s"></span>

      <!-- 新闻生成态：红色科技装饰（HUD 四角 + 扫描线 + 反应堆核心），仅生成中渲染 -->
      <div v-if="phase === 'news' && !done" class="aio-scifi" aria-hidden="true">
        <span class="sf-corner tl"></span><span class="sf-corner tr"></span>
        <span class="sf-corner bl"></span><span class="sf-corner br"></span>
        <span class="sf-scan"></span>
      </div>
      <div v-if="phase === 'news' && !done" class="aio-reactor" aria-hidden="true">
        <span class="rc-ring"></span>
        <span class="rc-ring rc-ring2"></span>
        <span class="rc-core"></span>
      </div>

      <div class="aio-hdr">
        <span class="aio-title">{{ title }}</span>
        <span class="aio-badge"><i class="aio-bdot"></i>{{ done ? '已完成' : badge }}</span>
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
          <div v-if="theme === 'party'" class="aio-doc-icon" aria-hidden="true">
            <span></span><span></span><span></span>
          </div>
          <div v-else class="aio-check">✓</div>
        </div>
        <span class="aio-say done">{{ doneSay }}</span>
        <div class="aio-done-sub">用时 {{ elapsed }} · 共消耗 {{ tokens }} token</div>
      </template>

      <div class="aio-steps">
        <div v-for="(label, i) in steps" :key="i" class="aio-step" :class="stepClass(i + 1)"><span class="aio-sdot">{{ stepClass(i + 1) === 'done' && theme !== 'party' ? '✓' : (i + 1) }}</span><span class="aio-slabel">{{ label }}</span></div>
      </div>

      <div class="aio-prog">
        <div class="aio-track"><div class="aio-fill" :style="{ width: pct + '%' }"></div></div>
        <span class="aio-pct">{{ pct }}%</span>
      </div>

      <button v-if="done" class="aio-btn" @click="onConfirm">{{ doneBtn }} →</button>
      <div v-else class="aio-by">由 <b>豆包大模型</b> 提供 · 完成后自动通知您 🔔</div>
    </div>
  </div>

  <!-- 最小化后的悬浮球：后台继续处理，点击展开回大窗；完成时自动弹回 -->
  <div v-if="shown && minimized" class="aio-fab" :class="{ 'theme-party': theme === 'party' }" @click="restore">
    <span class="aio-fab-ico"></span>
    <span class="aio-fab-txt">{{ done ? '已完成 · 点此查看' : badge }}</span>
  </div>
</template>

<script setup>
// "AI 工作中"悬浮等待卡片：录音转写(asr) / 生成纪要(gen) 两个时刻复用。
// 浮动卡片 + 半透明遮罩罩在原页上方；AI 完成后切"完成"态、出确认按钮，点击才关闭并 emit('confirm') 进入下一步。
// 深蓝科技风 + 发光能量核心 + 实时运行数据(耗时真、token/预计完成估算剧场化)；进度只升不退；prefers-reduced-motion 降级。
// 入参 active = AI 任务进行中；active 由 true→false 视为"已完成"(假定成功)。另存档"全屏版"见记忆 ai-working-screen-design。
import { ref, computed, watch, onUnmounted } from 'vue'
import { showModal } from '@/utils/ui'

const props = defineProps({
  active: { type: Boolean, default: false },
  phase: { type: String, default: 'asr' },
  theme: { type: String, default: 'tech' },         // tech=深蓝科技风 / party=红色党建风
  audioDurSec: { type: Number, default: 0 },        // 录音时长(秒)
  audioFileSizeByte: { type: Number, default: 0 }   // 录音文件大小(bytes)
})
const emit = defineEmits(['confirm', 'close'])

const STEPS_MINUTES = ['上传录音', '识别转写', '提炼议题', '生成纪要']
const CFG = {
  asr: { target: 25, tok: 205, say: '正在为您识别录音、转写文字', lab: '已解析音频', max: 25, unit: ' 分钟', dec: 0, active: 2, doneSay: '录音已转写完成', doneBtn: '查看会议纪要', title: '豆包正在为您整理纪要', badge: '草稿生成中', steps: STEPS_MINUTES },
  // 仅识别（不接生成纪要）：上传录音→转写→提炼，完成后回到录音页核对表决结果
  recognize: { target: 25, tok: 205, say: '正在为您识别录音、转写文字', lab: '已解析音频', max: 25, unit: ' 分钟', dec: 0, active: 2, doneSay: '录音识别完成', doneBtn: '下一步', title: '豆包正在为您识别录音', badge: '录音识别中', steps: ['上传录音', '识别转写', '提炼议题', '核对表决'] },
  gen: { target: 112, tok: 268, say: '正在为您提炼议题、生成纪要草稿', lab: '上下文理解', pct: true, active: 4, doneSay: '已生成会议纪要', doneBtn: '查看会议纪要', title: '豆包正在为您整理纪要', badge: '草稿生成中', steps: STEPS_MINUTES },
  // 党建新闻生成（红色党建风）：研读纪要 → 提炼党建主线 → 撰写初稿 → 润色成稿
  news: { target: 55, tok: 240, say: '正在根据会议纪要整理内容', lab: '内容整理', pct: true, active: 3, doneSay: '党建新闻已生成', doneBtn: '查看新闻稿', title: '正在生成新闻稿', badge: '生成中', steps: ['读取纪要', '整理内容', '生成新闻', '完成'] }
}

const shown = ref(false)
const done = ref(false)
const minimized = ref(false)   // 最小化：收起大窗、悬浮球代替，后台任务不受影响
const sec = ref(0)
let timer = null

// X 关闭的确认文案（按阶段：中止对应的 AI 任务）
const closeMsg = computed(() => {
  if (props.phase === 'gen') return '将中止 AI 生成会议纪要，确认要关闭吗？'
  if (props.phase === 'news') return '将中止 AI 生成党建新闻，确认要关闭吗？'
  if (props.phase === 'asr' || props.phase === 'recognize') return '将中止录音识别，确认要关闭吗？'
  return '将中止本次 AI 处理，确认要关闭吗？'
})

const cfg = computed(() => {
  const base = CFG[props.phase] || CFG.asr
  if (props.phase === 'asr' || props.phase === 'recognize') {
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
const title = computed(() => cfg.value.title || '豆包正在为您整理纪要')
const badge = computed(() => cfg.value.badge || '草稿生成中')
const steps = computed(() => cfg.value.steps || STEPS_MINUTES)
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
  if (a) { shown.value = true; minimized.value = false; start() } // 每次新开都从大窗开始
  else if (shown.value) { finish() } // 进行中 → 完成
}, { immediate: true })
watch(() => props.phase, () => { if (props.active) start() })
// 处理完成时若正最小化 → 自动弹回大窗，让用户看到「已完成 → 查看」
watch(done, (d) => { if (d && minimized.value) minimized.value = false })

function minimize() { minimized.value = true }
function restore() { minimized.value = false }

function onConfirm() { shown.value = false; done.value = false; minimized.value = false; emit('confirm') }
async function onClose() {
  // 已完成态无需确认，直接关；进行中关闭 = 中止任务，先确认
  if (!done.value) {
    const res = await showModal({
      title: '',
      content: closeMsg.value,
      confirmText: '确认关闭',
      cancelText: '继续等待',
      contentBold: true,
      emphasizeCancel: true // 「继续等待」为主(突出)、「确认关闭」为次
    })
    if (!res.confirm) return
  }
  stop(); shown.value = false; done.value = false; minimized.value = false; emit('close')
}

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
.aio-close { position: absolute; top: 18rpx; right: 20rpx; z-index: 3; width: 60rpx; height: 60rpx; display: flex; align-items: center; justify-content: center; color: #AECBF0; font-size: 48rpx; line-height: 1; background: rgba(255,255,255,.1); border: 2rpx solid rgba(150,190,255,.22); border-radius: 50%; padding: 0; }
.aio-close:active { background: rgba(255,255,255,.2); }
/* 最小化按钮：X 左侧 */
.aio-min { position: absolute; top: 18rpx; right: 92rpx; z-index: 3; width: 60rpx; height: 60rpx; display: flex; align-items: center; justify-content: center; color: #AECBF0; font-size: 44rpx; line-height: 1; background: rgba(255,255,255,.1); border: 2rpx solid rgba(150,190,255,.22); border-radius: 50%; padding: 0; }
.aio-min:active { background: rgba(255,255,255,.2); }
.theme-party .aio-min { color: #FFE1C4; background: rgba(255,255,255,.12); border-color: rgba(255,210,150,.35); }
/* 最小化悬浮球：固定右下，后台处理中/已完成 */
.aio-fab { position: fixed; right: 28rpx; bottom: 44rpx; z-index: 1000; display: inline-flex; align-items: center; gap: 12rpx; padding: 16rpx 28rpx; border-radius: 999rpx; background: linear-gradient(90deg,#2E73E6,#1B47AE); color: #EAF2FF; font-size: 28rpx; font-weight: 600; box-shadow: 0 10rpx 30rpx rgba(20,50,120,.5); border: 2rpx solid rgba(140,185,255,.4); }
.aio-fab:active { filter: brightness(1.08); }
.aio-fab-ico { width: 26rpx; height: 26rpx; border: 4rpx solid rgba(180,210,255,.4); border-top-color: #fff; border-radius: 50%; animation: aioSpin 0.8s linear infinite; }
.aio-fab.theme-party { background: linear-gradient(90deg,#D5262B,#8E0F14); border-color: rgba(255,210,150,.45); color: #FFE6CE; }
.aio-fab.theme-party .aio-fab-ico { border-color: rgba(255,210,150,.4); border-top-color: #fff; }
.aio-pt { position: absolute; border-radius: 50%; background: #8FC6FF; box-shadow: 0 0 12rpx 2rpx rgba(120,180,255,.8); animation: aioTwk 3.2s ease-in-out infinite; }
/* 状态标签(草稿生成中)下移到右上角两个按钮(最小化/关闭)下方，避免重叠；标题仍留在顶部左侧 */
.aio-hdr { display: flex; align-items: flex-start; justify-content: space-between; width: 100%; box-sizing: border-box; padding-right: 20rpx; }
.aio-title { font-size: 36rpx; font-weight: 700; color: #EAF2FF; text-shadow: 0 0 24rpx rgba(90,150,255,.5); }
.aio-badge { display: inline-flex; align-items: center; gap: 8rpx; margin-top: 52rpx; background: rgba(120,170,255,.16); color: #AED2FF; font-size: 26rpx; font-weight: 600; padding: 7rpx 18rpx; border-radius: 999rpx; border: 2rpx solid rgba(140,185,255,.28); white-space: nowrap; }
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
.aio-done-sub { font-size: 28rpx; color: #9FB7DC; margin-bottom: 24rpx; }
.aio-metrics { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; width: 100%; margin-bottom: 28rpx; }
.aio-m { background: rgba(255,255,255,.055); border: 2rpx solid rgba(150,190,255,.18); border-radius: 16rpx; padding: 16rpx 20rpx; }
.aio-ml { display: block; font-size: 26rpx; color: #7E9BC8; }
.aio-mv { display: block; font-size: 36rpx; font-weight: 700; color: #CFE9FF; margin-top: 6rpx; text-shadow: 0 0 16rpx rgba(95,170,255,.45); font-variant-numeric: tabular-nums; }
.aio-mv.eta { color: #7FE6C8; text-shadow: 0 0 16rpx rgba(60,210,170,.45); }
.aio-steps { display: flex; align-items: flex-start; justify-content: space-between; position: relative; width: 100%; margin-bottom: 26rpx; }
.aio-steps::before { content: ""; position: absolute; top: 28rpx; left: 12%; right: 12%; height: 4rpx; background: rgba(255,255,255,.14); }
.aio-step { position: relative; display: flex; flex-direction: column; align-items: center; gap: 10rpx; width: 25%; z-index: 1; }
.aio-sdot { width: 56rpx; height: 56rpx; border-radius: 50%; background: rgba(255,255,255,.06); border: 3rpx solid rgba(255,255,255,.2); color: #90A6CE; display: flex; align-items: center; justify-content: center; font-size: 28rpx; font-weight: 700; }
.aio-step.done .aio-sdot { background: #22D3A6; border-color: #22D3A6; color: #062b20; box-shadow: 0 0 18rpx rgba(34,211,166,.5); }
.aio-step.active .aio-sdot { background: #3B82F6; border-color: #7FB4FF; color: #fff; animation: aioPulse 1.8s ease-out infinite; }
.aio-slabel { font-size: 26rpx; color: #8095BB; text-align: center; }
.aio-step.active .aio-slabel { color: #BFE0FF; font-weight: 600; }
.aio-step.done .aio-slabel { color: #6FE6C6; }
.aio-prog { display: flex; align-items: center; gap: 16rpx; width: 100%; margin-bottom: 22rpx; }
.aio-track { flex: 1; height: 16rpx; background: rgba(255,255,255,.12); border-radius: 10rpx; overflow: hidden; }
.aio-fill { height: 100%; background: linear-gradient(90deg, #5FD2FF, #3B82F6); border-radius: 10rpx; box-shadow: 0 0 20rpx rgba(95,170,255,.7); transition: width .9s ease; }
.aio-pct { font-size: 34rpx; font-weight: 700; color: #CFE6FF; min-width: 78rpx; text-align: right; font-variant-numeric: tabular-nums; }
.aio-by { text-align: center; font-size: 26rpx; color: #7494C8; }
.aio-by b { color: #9FD0FF; font-weight: 600; }
.aio-btn { width: 100%; height: 92rpx; border: none; border-radius: 18rpx; background: linear-gradient(90deg, #4FC0FF, #2E73E6); color: #fff; font-size: 32rpx; font-weight: 700; box-shadow: 0 8rpx 28rpx rgba(46,115,230,.45); }
.aio-btn:active { background: linear-gradient(90deg, #3FA8EC, #245FC4); }

/* ══ 新闻生成态：红色科技风（HUD 四角 + 扫描线 + 反应堆核心 + 红色霓虹进度）══ */
.aio-mask.theme-party.is-news { background:rgba(14,2,6,.72); backdrop-filter:blur(5px); }
.aio-card.theme-party.is-news {
  width:600rpx; max-width:90%; min-height:470rpx; justify-content:center;
  padding:64rpx 46rpx 46rpx; overflow:hidden;
  /* 网格线用 1px：rpx 用在渐变色标位置会被转换插件搞坏，导致整条 background 被丢弃 */
  background:
    linear-gradient(rgba(255,90,80,.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,90,80,.05) 1px, transparent 1px),
    radial-gradient(120% 92% at 50% -6%, #4A0E17 0%, #2A0810 46%, #120409 100%);
  background-size:34rpx 34rpx, 34rpx 34rpx, auto;
  border:1px solid rgba(255,96,84,.32);
  box-shadow:0 28rpx 80rpx rgba(6,1,4,.6), 0 0 60rpx rgba(200,32,40,.22), inset 0 0 60rpx rgba(180,20,30,.14);
}
/* 顶部发光红条 */
.aio-card.theme-party.is-news::before {
  content:""; position:absolute; top:0; left:0; right:0; height:5rpx;
  background:linear-gradient(90deg, transparent, #FF4D45 20%, #FFB36B 50%, #FF4D45 80%, transparent);
  box-shadow:0 0 18rpx rgba(255,80,60,.7);
}
.aio-card.theme-party.is-news .aio-core,
.aio-card.theme-party.is-news .aio-badge,
.aio-card.theme-party.is-news .aio-steps { display:none; }
/* 红色能量粒子（重新启用并染红） */
.aio-card.theme-party.is-news .aio-pt { display:block; background:#FF6A5A; box-shadow:0 0 12rpx 2rpx rgba(255,96,80,.85); }

/* HUD 四角 + 扫描线 装饰层 */
.aio-scifi { position:absolute; inset:0; pointer-events:none; z-index:1; }
.sf-corner { position:absolute; width:32rpx; height:32rpx; border:3rpx solid #FF5A52; box-shadow:0 0 12rpx rgba(255,80,64,.55); opacity:.9; }
.sf-corner.tl { top:18rpx; left:18rpx; border-right:0; border-bottom:0; border-top-left-radius:6rpx; }
.sf-corner.tr { top:18rpx; right:18rpx; border-left:0; border-bottom:0; border-top-right-radius:6rpx; }
.sf-corner.bl { bottom:18rpx; left:18rpx; border-right:0; border-top:0; border-bottom-left-radius:6rpx; }
.sf-corner.br { bottom:18rpx; right:18rpx; border-left:0; border-top:0; border-bottom-right-radius:6rpx; }
.sf-scan { position:absolute; left:5%; right:5%; top:8%; height:2rpx; background:linear-gradient(90deg, transparent, #FF6A5A, transparent); box-shadow:0 0 18rpx 2rpx rgba(255,90,70,.55); animation:sfScan 3.4s ease-in-out infinite; }

/* 反应堆核心：双环反向旋转 + 脉动红核 + 环上卫星点 */
.aio-reactor { position:relative; width:158rpx; height:158rpx; margin:0 auto 26rpx; display:flex; align-items:center; justify-content:center; z-index:2; }
.aio-reactor .rc-ring { position:absolute; inset:0; border-radius:50%; border:3rpx solid transparent; border-top-color:#FF5A52; border-right-color:#FFB36B; box-shadow:0 0 22rpx rgba(255,84,68,.4); animation:aioSpin 1.5s linear infinite; }
.aio-reactor .rc-ring::after { content:""; position:absolute; top:-7rpx; left:50%; width:13rpx; height:13rpx; margin-left:-6.5rpx; border-radius:50%; background:#FFDDB0; box-shadow:0 0 14rpx #FF8A5A; }
.aio-reactor .rc-ring2 { inset:22rpx; border-top-color:#FFCF6B; border-left-color:#FF5A52; border-right-color:transparent; animation:aioSpinR 2.4s linear infinite; }
.aio-reactor .rc-ring2::after { top:auto; bottom:-6rpx; width:10rpx; height:10rpx; margin-left:-5rpx; background:#FFCF8A; box-shadow:0 0 12rpx #FFB36B; }
.aio-reactor .rc-core { width:72rpx; height:72rpx; border-radius:50%; background:radial-gradient(circle at 42% 34%, #FFE6C6 0%, #FF5A46 46%, #A8121C 100%); box-shadow:0 0 36rpx rgba(255,74,58,.75), inset 0 0 16rpx rgba(255,220,180,.5); animation:aioBreathe 2.4s ease-in-out infinite; }

.aio-card.theme-party.is-news .aio-hdr { justify-content:center; padding:0; position:relative; z-index:2; }
.aio-card.theme-party.is-news .aio-title { color:#FFF4EE; font-size:38rpx; font-weight:700; letter-spacing:3rpx; text-shadow:0 0 22rpx rgba(255,90,64,.6); text-align:center; }
.aio-card.theme-party.is-news .aio-title::before { display:none; }
.aio-card.theme-party.is-news .aio-say { margin:20rpx 0 26rpx; color:#E9C3B8; font-size:32rpx; font-weight:400; position:relative; z-index:2; }
.aio-card.theme-party.is-news .aio-say i { color:#FF6A5A; }
.aio-card.theme-party.is-news .aio-metrics { display:grid; grid-template-columns:1fr 1fr; gap:12rpx; margin-bottom:26rpx; position:relative; z-index:2; }
.aio-card.theme-party.is-news .aio-m { position:relative; padding:16rpx 18rpx 16rpx 24rpx; background:rgba(42,7,11,.5); border:1rpx solid rgba(255,96,82,.22); border-radius:12rpx; overflow:hidden; }
.aio-card.theme-party.is-news .aio-m::after { content:""; position:absolute; left:0; top:14rpx; bottom:14rpx; width:4rpx; background:#FF5A48; box-shadow:0 0 10rpx rgba(255,88,64,.7); border-radius:2rpx; }
.aio-card.theme-party.is-news .aio-m:nth-child(3) { grid-column:1 / -1; }
.aio-card.theme-party.is-news .aio-m:nth-child(4) { display:none; }
.aio-card.theme-party.is-news .aio-ml { color:#D79C90; font-size:26rpx; }
.aio-card.theme-party.is-news .aio-mv { margin-top:5rpx; color:#FFEDE2; font-size:32rpx; text-shadow:0 0 14rpx rgba(255,96,64,.5); font-variant-numeric:tabular-nums; }
.aio-card.theme-party.is-news .aio-mv.eta { color:#FFCA8C; text-shadow:0 0 14rpx rgba(255,170,90,.5); }
.aio-card.theme-party.is-news .aio-prog { display:flex; margin-bottom:24rpx; position:relative; z-index:2; }
.aio-card.theme-party.is-news .aio-track { height:12rpx; background:rgba(255,96,82,.14); border:1rpx solid rgba(255,110,96,.2); }
.aio-card.theme-party.is-news .aio-fill { position:relative; overflow:hidden; background:linear-gradient(90deg,#A8121C,#FF5A46 62%,#FFB36B); box-shadow:0 0 18rpx rgba(255,96,64,.6); }
.aio-card.theme-party.is-news .aio-fill::after { content:""; position:absolute; inset:0; background:linear-gradient(90deg, transparent, rgba(255,255,255,.5), transparent); animation:sfShimmer 1.6s linear infinite; }
.aio-card.theme-party.is-news .aio-pct { min-width:72rpx; color:#FFE7DA; font-size:32rpx; text-shadow:0 0 12rpx rgba(255,96,64,.5); }
.aio-card.theme-party.is-news .aio-by { color:transparent; font-size:0; position:relative; z-index:2; }
.aio-card.theme-party.is-news .aio-by::after { content:"可以先收起，生成完成后会提醒您"; color:#C79185; font-size:26rpx; font-weight:400; }
.aio-card.theme-party.is-news .aio-close,
.aio-card.theme-party.is-news .aio-min { color:#F0C6BC; background:rgba(40,8,12,.55); border-color:rgba(255,120,100,.28); z-index:3; }
.aio-fab.aio-fab.theme-party { background:linear-gradient(90deg,#2A0810,#A8121C); border-color:rgba(255,150,120,.4); color:#FFEDE4; box-shadow:0 8rpx 28rpx rgba(30,4,8,.5); }

@keyframes sfScan { 0%{top:10%;opacity:0} 12%{opacity:1} 88%{opacity:1} 100%{top:90%;opacity:0} }
@keyframes sfShimmer { 0%{transform:translateX(-100%)} 100%{transform:translateX(100%)} }

/* ════════ 红色党建风格（theme=party）：覆盖深蓝科技风配色 ════════ */
.aio-mask.theme-party { background: rgba(40, 4, 6, 0.68); }
.aio-card.theme-party {
  background: radial-gradient(135% 80% at 50% 8%, #C0141B 0%, #8E0F14 52%, #5C0A0E 100%);
  border-color: rgba(255, 210, 150, 0.4);
  box-shadow: 0 24rpx 80rpx rgba(80, 8, 10, 0.6), 0 0 60rpx rgba(220, 60, 50, 0.3);
}
.theme-party .aio-close { color: #FFE1C4; background: rgba(255,255,255,.12); border-color: rgba(255,210,150,.35); }
.theme-party .aio-pt { background: #FFD98A; box-shadow: 0 0 12rpx 2rpx rgba(255,200,120,.8); }
.theme-party .aio-title { color: #FFF3E6; text-shadow: 0 0 24rpx rgba(255,180,120,.55); }
.theme-party .aio-badge { background: rgba(255,220,170,.18); color: #FFE6C8; border-color: rgba(255,210,150,.4); }
.theme-party .aio-bdot { background: #FFD070; box-shadow: 0 0 10rpx #FFD070; }
.theme-party .aio-halo { fill: #E0322D; }
.theme-party .aio-orb { fill: #D5262B; }
.theme-party .aio-ring circle { stroke: #FFCF6B; }
.theme-party .aio-ring2 circle { stroke: #FFB86B; }
.theme-party .aio-say { color: #FFF1E4; }
.theme-party .aio-say i { color: #FFC98A; }
.theme-party .aio-say.done { color: #FFE0C0; }
.theme-party .aio-done-sub { color: #E6B48C; }
.theme-party .aio-m { background: rgba(255,255,255,.06); border-color: rgba(255,210,150,.2); }
.theme-party .aio-ml { color: #E8B58A; }
.theme-party .aio-mv { color: #FFE6CE; text-shadow: 0 0 16rpx rgba(255,180,120,.45); }
.theme-party .aio-mv.eta { color: #FFD07A; text-shadow: 0 0 16rpx rgba(255,190,110,.45); }
.theme-party .aio-steps::before { background: rgba(255,220,180,.18); }
.theme-party .aio-sdot { background: rgba(255,255,255,.07); border-color: rgba(255,220,180,.28); color: #F0C29A; }
.theme-party .aio-step.done .aio-sdot { background: #FFC24D; border-color: #FFC24D; color: #5C1B00; box-shadow: 0 0 18rpx rgba(255,194,77,.5); }
.theme-party .aio-step.active .aio-sdot { background: #E0322D; border-color: #FFB27A; color: #fff; }
.theme-party .aio-slabel { color: #E7B78F; }
.theme-party .aio-step.active .aio-slabel { color: #FFE2C6; font-weight: 600; }
.theme-party .aio-step.done .aio-slabel { color: #FFCF8A; }
.theme-party .aio-track { background: rgba(255,255,255,.14); }
.theme-party .aio-fill { background: linear-gradient(90deg, #FFC24D, #E0322D); box-shadow: 0 0 20rpx rgba(255,150,90,.7); }
.theme-party .aio-pct { color: #FFE6CE; }
.theme-party .aio-by { color: #E3AE86; }
.theme-party .aio-by b { color: #FFD9A8; }
.theme-party .aio-check { background: radial-gradient(circle at 50% 38%, #FFD98A 0%, #F5B301 55%, #C67A00 100%); color: #5C1B00; box-shadow: 0 0 40rpx rgba(245,179,1,.55); }
.theme-party .aio-btn { background: linear-gradient(90deg, #FF6B4D, #D5262B); box-shadow: 0 8rpx 28rpx rgba(200,40,30,.5); }
.theme-party .aio-btn:active { background: linear-gradient(90deg, #E85A3C, #B81E23); }

/* 党建新闻适老版：暖白公文卡片、低刺激动效、单一完成标识 */
.aio-mask.theme-party { background: rgba(37, 24, 24, .48); }
.aio-card.theme-party {
  background: #FFFDF9;
  border: 2rpx solid #E2C9BD;
  border-top: 12rpx solid #A81E24;
  box-shadow: 0 22rpx 64rpx rgba(55, 25, 25, .28);
  color: #332727;
}
.theme-party .aio-pt { display: none; }
.theme-party .aio-title { color: #72171C; text-shadow: none; font-size: 38rpx; line-height: 1.4; }
.theme-party .aio-close,
.theme-party .aio-min { color: #7C5555; background: #F8EEEA; border-color: #E2C9BD; }
.theme-party .aio-badge { background: #F8ECE7; color: #8B292D; border-color: #E7C7BA; font-size: 28rpx; }
.theme-party .aio-bdot { background: #A81E24; box-shadow: none; animation: none; }
.theme-party .aio-halo,
.theme-party .aio-ring,
.theme-party .aio-ring2,
.theme-party .aio-orb { animation: none; }
.theme-party .aio-halo { opacity: .14; }
.theme-party .aio-say { color: #3C3030; font-size: 36rpx; line-height: 1.5; }
.theme-party .aio-say i { color: #A81E24; animation: none; opacity: .7; }
.theme-party .aio-say.done { color: #72171C; font-size: 42rpx; font-weight: 800; margin: 12rpx 0 8rpx; }
.theme-party .aio-done-sub { color: #765F5F; font-size: 32rpx; margin-bottom: 34rpx; }
.theme-party .aio-m { background: #FAF5F1; border-color: #E8D9D1; }
.theme-party .aio-ml { color: #765F5F; font-size: 28rpx; }
.theme-party .aio-mv,
.theme-party .aio-mv.eta { color: #72171C; text-shadow: none; }
.theme-party .aio-steps::before { background: #E5D5CD; }
.theme-party .aio-sdot { background: #FFF; border-color: #D8C4BB; color: #765F5F; }
.theme-party .aio-step.done .aio-sdot {
  background: #A81E24; border-color: #A81E24; color: #FFF;
  box-shadow: none;
}
.theme-party .aio-step.active .aio-sdot { background: #A81E24; border-color: #A81E24; animation: none; }
.theme-party .aio-slabel { color: #765F5F; font-size: 28rpx; line-height: 1.35; }
.theme-party .aio-step.done .aio-slabel,
.theme-party .aio-step.active .aio-slabel { color: #72171C; font-weight: 700; }
.theme-party .aio-track { background: #E9DDD7; height: 18rpx; }
.theme-party .aio-fill { background: #A81E24; box-shadow: none; transition: width 1.2s linear; }
.theme-party .aio-pct { color: #72171C; }
.theme-party .aio-by { color: #806A6A; font-size: 28rpx; }
.theme-party .aio-by b { color: #72171C; }
.theme-party .aio-doc-icon {
  position: relative; width: 104rpx; height: 126rpx; box-sizing: border-box;
  margin: 10rpx 0 8rpx; border: 4rpx solid #A81E24; border-radius: 10rpx;
  background: #FFF; box-shadow: 8rpx 8rpx 0 #F1E2DC;
  display: flex; flex-direction: column; justify-content: center; gap: 14rpx; padding: 0 20rpx;
}
.theme-party .aio-doc-icon::after {
  content: ""; position: absolute; right: -4rpx; top: -4rpx;
  width: 28rpx; height: 28rpx; background: #FFFDF9;
  border-left: 4rpx solid #A81E24; border-bottom: 4rpx solid #A81E24;
}
.theme-party .aio-doc-icon span { display: block; height: 4rpx; border-radius: 2rpx; background: #A81E24; opacity: .72; }
.theme-party .aio-doc-icon span:last-child { width: 68%; }
.theme-party.is-done .aio-hdr { padding-bottom: 12rpx; border-bottom: 2rpx solid #EEE0D9; }
.theme-party.is-done .aio-badge { background: #F3E4DE; }
.theme-party.is-done .aio-steps,
.theme-party.is-done .aio-prog { display: none; }
.theme-party.is-done .aio-done-core { padding: 34rpx 0 8rpx; }
.theme-party.is-done .aio-done-sub { margin-bottom: 38rpx; }
.theme-party .aio-btn {
  height: 100rpx; border-radius: 18rpx;
  background: #A81E24; box-shadow: none;
  font-size: 36rpx; font-weight: 800;
}
.theme-party .aio-btn:active { background: #85171C; }
.aio-fab.theme-party { background: #A81E24; border-color: #DDB7A6; color: #FFF; box-shadow: 0 8rpx 24rpx rgba(82,22,25,.28); }
.aio-fab.theme-party .aio-fab-ico { animation-duration: 1.6s; }
@keyframes aioBreathe { 0%,100% { transform: scale(1); } 50% { transform: scale(1.06); } }
@keyframes aioHalo { 0%,100% { opacity: .32; transform: scale(.92); } 50% { opacity: .68; transform: scale(1.05); } }
@keyframes aioSpin { to { transform: rotate(360deg); } }
@keyframes aioSpinR { to { transform: rotate(-360deg); } }
@keyframes aioPulse { 0% { box-shadow: 0 0 0 0 rgba(95,170,255,.6); } 70% { box-shadow: 0 0 0 20rpx rgba(95,170,255,0); } 100% { box-shadow: 0 0 0 0 rgba(95,170,255,0); } }
@keyframes aioTwk { 0%,100% { opacity: .2; transform: scale(.7); } 50% { opacity: 1; transform: scale(1); } }
@keyframes aioDot { 0%,100% { opacity: .35; } 50% { opacity: 1; } }
@keyframes aioBlink { 0%,100% { opacity: 1; } 50% { opacity: .3; } }
@media (prefers-reduced-motion: reduce) {
  .aio-halo, .aio-ring, .aio-ring2, .aio-orb, .aio-pt, .aio-step.active .aio-sdot, .aio-bdot, .aio-say i,
  .sf-scan, .aio-reactor .rc-ring, .aio-reactor .rc-ring2, .aio-reactor .rc-core, .aio-card.theme-party.is-news .aio-fill::after { animation: none !important; }
  .aio-mask { backdrop-filter: none; }
}
</style>
