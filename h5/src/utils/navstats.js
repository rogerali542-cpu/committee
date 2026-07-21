// 软路由失败率埋点（临时诊断用，跑一两周出数据后可整体删除）。
// 原理：所有硬跳兜底的共同特征 = 「软跳发起后紧跟一次整页重载」。
// ① navigateTo/redirectTo 每次发起软跳时记录 {目标, 时间戳} 到 localStorage；
// ② App 启动时若发现上一笔软跳发生在 5 秒内 → 判定本次启动是硬跳救援，记一次 rescue。
// 这样 20 多处散落的兜底点无须逐个埋点，全部自动被统计。
// 已知噪声：软跳后 5 秒内用户手动刷新会被误计一次（极少）；开发环境 Vite 整页重载同理（看 env 字段区分）。

const STATS_KEY = 'nav_stats_v1'
const LAST_KEY = 'nav_last_attempt_v1'
const RESCUE_WINDOW_MS = 5000
const MAX_RESCUES = 200

function load() {
  try {
    const raw = localStorage.getItem(STATS_KEY)
    if (raw) return JSON.parse(raw)
  } catch (e) { /* 坏数据当作重新开始 */ }
  return { firstAt: Date.now(), attempts: 0, byRoute: {}, rescues: [] }
}

function save(stats) {
  try { localStorage.setItem(STATS_KEY, JSON.stringify(stats)) } catch (e) { /* 存储满则放弃 */ }
}

// 本次整页加载是怎么来的：navigate=地址跳转(硬跳兜底都是这种) / reload=手动刷新 / back_forward=前进后退。
// 手动刷新和外链回退不是救援，靠它排除；API 不可用的老 WebView 退回旧接口，再不行按 navigate 保守计入。
function navType() {
  try {
    const e = performance.getEntriesByType('navigation')
    if (e && e[0] && e[0].type) return e[0].type
  } catch (err) {}
  try {
    const t = performance.navigation && performance.navigation.type
    if (t === 1) return 'reload'
    if (t === 2) return 'back_forward'
    if (t === 0) return 'navigate'
  } catch (err) {}
  return 'navigate'
}

// 将来若新增「主动整页跳转」（非兜底救援），跳转前调它抹掉凭证，避免被误计一次救援。
// 目前全代码的 App 内硬跳均为兜底救援（都在"目标页未挂上"的条件分支里），暂无调用点。
export function markIntentionalHardNav() {
  try { localStorage.removeItem(LAST_KEY) } catch (e) {}
}

// 软跳发起：计数 + 留下"最近一次尝试"凭证供启动侧核对
export function recordAttempt(to) {
  const route = String(to || '').split('?')[0]
  const stats = load()
  stats.attempts++
  stats.byRoute[route] = (stats.byRoute[route] || 0) + 1
  save(stats)
  try { localStorage.setItem(LAST_KEY, JSON.stringify({ to: String(to || ''), t: Date.now() })) } catch (e) {}
}

// App 启动时调用：若刚刚（5秒内）发起过软跳，本次整页加载即视为硬跳救援
export function checkRescueOnBoot() {
  let last = null
  try { last = JSON.parse(localStorage.getItem(LAST_KEY) || 'null') } catch (e) {}
  try { localStorage.removeItem(LAST_KEY) } catch (e) {}
  if (!last || !last.t) return
  const gap = Date.now() - last.t
  if (gap < 0 || gap > RESCUE_WINDOW_MS) return
  if (navType() !== 'navigate') return // 手动刷新(reload)/前进后退(back_forward)不是救援
  const stats = load()
  stats.rescues.push({ to: last.to, t: last.t, gapMs: gap, env: import.meta.env.PROD ? 'prod' : 'dev' })
  if (stats.rescues.length > MAX_RESCUES) stats.rescues = stats.rescues.slice(-MAX_RESCUES)
  save(stats)
}

export function summary() {
  const stats = load()
  const rescues = stats.rescues || []
  const prod = rescues.filter(r => r.env === 'prod').length
  return {
    自: new Date(stats.firstAt).toLocaleString(),
    软跳总次数: stats.attempts,
    硬跳救援次数: rescues.length,
    其中正式环境: prod,
    救援率: stats.attempts ? (rescues.length / stats.attempts * 100).toFixed(2) + '%' : '0%',
    各路由软跳: stats.byRoute,
    救援明细: rescues.map(r => ({ 目标: r.to, 时间: new Date(r.t).toLocaleString(), 延迟ms: r.gapMs, 环境: r.env }))
  }
}

export function resetStats() {
  try { localStorage.removeItem(STATS_KEY); localStorage.removeItem(LAST_KEY) } catch (e) {}
}

export default { recordAttempt, checkRescueOnBoot, summary, resetStats, markIntentionalHardNav }
