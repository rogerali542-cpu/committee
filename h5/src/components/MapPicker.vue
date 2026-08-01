<template>
  <div v-if="open" class="mp-mask" @click.self="close">
    <div class="mp-sheet">
      <div class="mp-head">
        <span class="mp-title">从地图选择地点</span>
        <span class="mp-close" @click="close">×</span>
      </div>

      <!-- 腾讯官方 H5 选点组件：搜索/定位/拖图选点后点组件内「确认」，postMessage 回传地址与经纬度。
           txReady 先解析默认落点再渲染（0801 设计师点10），URL 带 coord 直接落到表单坐标/当前位置 -->
      <iframe v-if="activeProvider === 'tencent' && txReady" :src="tencentUrl" class="mp-frame" frameborder="0"
              allow="geolocation" @load="onTencentIframeLoad"></iframe>
      <div v-else-if="activeProvider === 'tencent'" class="mp-locating">正在定位…</div>

      <!-- 高德 JS API 选点：中心固定针，拖图取点，逆地理出地址；顶部关键字搜索 -->
      <template v-else-if="activeProvider === 'amap'">
        <div class="mp-search-row">
          <input class="mp-search-input" v-model="amapKeyword" placeholder="搜索小区/大厦/路名" @keyup.enter="amapSearch" />
          <button type="button" class="mp-search-btn" @click="amapSearch">搜索</button>
        </div>
        <div v-if="amapResults.length" class="mp-results">
          <div v-for="(r, i) in amapResults" :key="i" class="mp-result-row" @click="amapPickResult(r)">
            <div class="mp-result-name">{{ r.name }}</div>
            <div class="mp-result-addr">{{ r.address }}</div>
          </div>
        </div>
        <div class="mp-map-wrap">
          <div ref="amapEl" class="mp-map"></div>
          <span class="mp-pin">📍</span>
          <div v-if="amapError" class="mp-map-err">{{ amapError }}</div>
        </div>
        <div class="mp-confirm-bar">
          <div class="mp-cur">
            <div class="mp-cur-name">{{ amapCurName || '拖动地图选择位置' }}</div>
            <div class="mp-cur-addr">{{ amapCurAddress }}</div>
          </div>
          <button type="button" class="mp-confirm-btn" :disabled="!amapCurAddress" @click="amapConfirm">确认</button>
        </div>
      </template>

      <div v-else class="mp-nokey">
        <div class="mp-nokey-title">地图选点还未配置 Key</div>
        <div class="mp-nokey-sub">
          按 docs/地图接入教程.md 申请腾讯或高德地图 Key，填入 h5/.env.local
          （VITE_TXMAP_KEY 或 VITE_AMAP_KEY+VITE_AMAP_JSCODE）并重启前端即可启用真实地图。
        </div>
        <button type="button" class="mp-demo-btn" @click="pickDemo">先用演示地点</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { getStorage, setStorage } from '@/utils/storage'
import { toast } from '@/utils/ui'

const props = defineProps({
  open: { type: Boolean, default: false },
  // 0801 设计师点10：初始落点（表单里已存的会议地点坐标）。没有时组件自己尝试浏览器定位，
  // 都拿不到才落地图默认视野——不再一打开就是天安门。
  initLat: { type: Number, default: null },
  initLng: { type: Number, default: null }
})
const emit = defineEmits(['close', 'picked'])

// ── 默认落点解析（0801 设计师点10）：优先表单已有坐标 → 其次浏览器定位（限时 2.5s，拿不到不挡加载）──
function resolveStartCoord() {
  if (props.initLat != null && props.initLng != null) return Promise.resolve(props.initLat + ',' + props.initLng)
  if (!navigator.geolocation) return Promise.resolve('')
  return new Promise((resolve) => {
    let done = false
    const finish = (v) => { if (!done) { done = true; resolve(v) } }
    setTimeout(() => finish(''), 2500)
    navigator.geolocation.getCurrentPosition(
      (pos) => finish(pos.coords.latitude + ',' + pos.coords.longitude),
      () => finish(''),
      { enableHighAccuracy: true, timeout: 2400, maximumAge: 300000 }
    )
  })
}

// ── 供应商选择：配了哪家用哪家；两家都配了用 VITE_MAP_PROVIDER 指定（默认腾讯，组件更省心）──
const TX_KEY = import.meta.env.VITE_TXMAP_KEY || ''
const TX_REFERER = import.meta.env.VITE_TXMAP_REFERER || '业委会智能履职'
const AMAP_KEY = import.meta.env.VITE_AMAP_KEY || ''
const AMAP_JSCODE = import.meta.env.VITE_AMAP_JSCODE || ''
const PREFER = (import.meta.env.VITE_MAP_PROVIDER || '').toLowerCase()
// 配置指定的首选供应商（管理员定）
const configuredProvider = computed(() => {
  if (PREFER === 'amap' && AMAP_KEY) return 'amap'
  if (PREFER === 'tencent' && TX_KEY) return 'tencent'
  if (TX_KEY) return 'tencent'
  if (AMAP_KEY) return 'amap'
  return ''
})

// ── 自动降级（0723 用户定）：默认腾讯，腾讯加载失败当场切高德（不让用户干等）；
//    连续失败达阈值后，以后直接默认高德；冷却期过再自动给腾讯一次机会（临时抽风能自愈）。──
const FAIL_KEY = 'map_tx_fail_count'   // 腾讯连续加载失败次数
const FAIL_TS_KEY = 'map_tx_fail_ts'   // 最近一次失败时刻
const FAIL_THRESHOLD = 2               // 连续失败 2 次 → 默认改高德
const WATCHDOG_MS = 7000               // 腾讯 iframe 未在此时限内 load 视为失败（page load 很快，够宽松）
const RETRY_COOLDOWN_MS = 30 * 60 * 1000 // 降级后 30 分钟再自动试一次腾讯

// 本次实际展示的供应商（可在腾讯失败时被切成 amap）
const activeProvider = ref('')
let _watchdog = null

// 决定本次打开先用哪家：只有两家都配置时才有降级逻辑
function pickStartProvider() {
  if (!TX_KEY || !AMAP_KEY) return configuredProvider.value        // 只配一家 → 无从降级
  if (configuredProvider.value === 'amap') return 'amap'           // 管理员显式选高德 → 就用高德
  let fails = Number(getStorage(FAIL_KEY, 0)) || 0
  const lastTs = Number(getStorage(FAIL_TS_KEY, 0)) || 0
  // 已降级但冷却期已过 → 重置为 1，本次再给腾讯一次机会（失败即重新锁定高德）
  if (fails >= FAIL_THRESHOLD && Date.now() - lastTs > RETRY_COOLDOWN_MS) {
    fails = 1; setStorage(FAIL_KEY, 1)
  }
  return fails >= FAIL_THRESHOLD ? 'amap' : 'tencent'
}

function armWatchdog() { clearWatchdog(); _watchdog = setTimeout(onTencentTimeout, WATCHDOG_MS) }
function clearWatchdog() { if (_watchdog) { clearTimeout(_watchdog); _watchdog = null } }
// 腾讯选点页加载成功（含慢网）→ 清连续失败计数
function onTencentIframeLoad() { clearWatchdog(); setStorage(FAIL_KEY, 0) }
// 腾讯超时未加载 → 记一次失败；当场切高德保证用户有可用地图
function onTencentTimeout() {
  _watchdog = null
  const fails = (Number(getStorage(FAIL_KEY, 0)) || 0) + 1
  setStorage(FAIL_KEY, fails); setStorage(FAIL_TS_KEY, Date.now())
  if (AMAP_KEY) {
    activeProvider.value = 'amap'
    initAmap()
    toast({ title: fails >= FAIL_THRESHOLD ? '腾讯地图暂不可用，已切换高德地图' : '地图加载较慢，已切换备用地图' })
  }
}

// ── 腾讯：locpicker iframe ──
// txReady：先解析默认落点（≤2.5s）再渲染 iframe——URL 带 coord 一步到位，不然先落天安门再跳很晃眼
const txReady = ref(false)
const txCoord = ref('')   // 'lat,lng'，空 = 用组件默认视野
const tencentUrl = computed(() =>
  'https://apis.map.qq.com/tools/locpicker?search=1&type=1'
  + '&key=' + encodeURIComponent(TX_KEY)
  + '&referer=' + encodeURIComponent(TX_REFERER)
  + (txCoord.value ? '&coord=' + encodeURIComponent(txCoord.value) : ''))

// 选点组件回传：{ module:'locationPicker', latlng:{lat,lng}, poiaddress, poiname, cityname }
function onMessage(ev) {
  if (!props.open || activeProvider.value !== 'tencent') return
  const d = ev && ev.data
  if (!d || d.module !== 'locationPicker') return
  clearWatchdog() // 拿到回传说明腾讯选点正常工作
  emit('picked', {
    name: d.poiname || '',
    address: d.poiaddress || '',
    lat: d.latlng && d.latlng.lat != null ? Number(d.latlng.lat) : null,
    lng: d.latlng && d.latlng.lng != null ? Number(d.latlng.lng) : null
  })
  emit('close')
}
onMounted(() => window.addEventListener('message', onMessage, false))
onBeforeUnmount(() => { window.removeEventListener('message', onMessage, false); clearWatchdog(); destroyAmap() })

// ── 高德：JS API 2.0 中心取点 + 逆地理 + 关键字搜索 ──
const amapEl = ref(null)
const amapKeyword = ref('')
const amapResults = ref([])
const amapCurName = ref('')
const amapCurAddress = ref('')
const amapError = ref('')
let _amap = null            // AMap 地图实例
let _geocoder = null
let _placeSearch = null
let _amapLoadPromise = null

function loadAmapScript() {
  if (window.AMap) return Promise.resolve()
  if (_amapLoadPromise) return _amapLoadPromise
  // 安全密钥必须在 JS API 加载前挂到全局（高德官方要求；生产建议换 nginx 代理方案，见教程）
  window._AMapSecurityConfig = { securityJsCode: AMAP_JSCODE }
  _amapLoadPromise = new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = 'https://webapi.amap.com/maps?v=2.0&key=' + encodeURIComponent(AMAP_KEY)
      + '&plugin=AMap.Geocoder,AMap.PlaceSearch,AMap.Geolocation'
    s.onload = () => resolve()
    s.onerror = () => { _amapLoadPromise = null; reject(new Error('高德地图脚本加载失败，请检查网络与 Key')) }
    document.head.appendChild(s)
  })
  return _amapLoadPromise
}

async function initAmap() {
  amapError.value = ''
  try {
    await loadAmapScript()
    await nextTick()
    if (!amapEl.value || _amap) return
    // 0801 设计师点10：表单已有坐标（改地点/编辑会议）直接落到该点；否则再走浏览器定位，不再默认北京
    const hasInit = props.initLat != null && props.initLng != null
    _amap = new window.AMap.Map(amapEl.value, { zoom: 16, resizeEnable: true, ...(hasInit ? { center: [props.initLng, props.initLat] } : {}) })
    _geocoder = new window.AMap.Geocoder()
    _placeSearch = new window.AMap.PlaceSearch({ pageSize: 5 })
    _amap.on('moveend', regeoCenter)
    // 尝试定位到当前位置（手机允许定位即生效；电脑/拒绝授权则失败，留默认视野，用户可搜索）
    if (!hasInit) locateToCurrent()
    regeoCenter()
  } catch (e) {
    amapError.value = (e && e.message) || '地图初始化失败'
  }
}

// 定位到当前位置并居中（0723）：AMap.Geolocation 拿到坐标就把地图挪过去，
// moveend 会自动触发逆地理刷新地址。失败静默（无权限/超时）——保持默认视野，用户搜索选点。
function locateToCurrent() {
  if (!_amap || !window.AMap || !window.AMap.Geolocation) return
  try {
    const geo = new window.AMap.Geolocation({ enableHighAccuracy: true, timeout: 8000, showButton: false, showMarker: false, showCircle: false, panToLocation: false })
    _amap.addControl(geo)
    geo.getCurrentPosition((status, result) => {
      if (status === 'complete' && result && result.position) {
        _amap.setCenter([result.position.lng, result.position.lat])
      }
    })
  } catch (e) { /* 定位失败留默认视野 */ }
}

function regeoCenter() {
  if (!_amap || !_geocoder) return
  const c = _amap.getCenter()
  _geocoder.getAddress([c.lng, c.lat], (status, result) => {
    if (status === 'complete' && result && result.regeocode) {
      amapCurAddress.value = result.regeocode.formattedAddress || ''
      const pois = (result.regeocode.pois || [])
      amapCurName.value = pois.length ? pois[0].name : ''
    }
  })
}

function amapSearch() {
  const kw = (amapKeyword.value || '').trim()
  if (!kw || !_placeSearch) return
  _placeSearch.search(kw, (status, result) => {
    if (status === 'complete' && result && result.poiList && result.poiList.pois) {
      amapResults.value = result.poiList.pois.map(p => ({
        name: p.name, address: p.address || (p.district || ''),
        lat: p.location ? p.location.lat : null, lng: p.location ? p.location.lng : null
      })).filter(p => p.lat != null)
    } else {
      amapResults.value = []
    }
  })
}

function amapPickResult(r) {
  amapResults.value = []
  amapKeyword.value = r.name
  amapCurName.value = r.name
  amapCurAddress.value = r.address
  if (_amap && r.lat != null) _amap.setCenter([r.lng, r.lat])
}

function amapConfirm() {
  if (!_amap) return
  const c = _amap.getCenter()
  emit('picked', { name: amapCurName.value, address: amapCurAddress.value, lat: c.lat, lng: c.lng })
  emit('close')
}

function destroyAmap() {
  if (_amap) { try { _amap.destroy() } catch (e) {} _amap = null }
  _geocoder = null; _placeSearch = null
  amapResults.value = []; amapCurName.value = ''; amapCurAddress.value = ''
}

watch(() => props.open, async (v) => {
  if (v) {
    activeProvider.value = pickStartProvider()
    if (activeProvider.value === 'amap') initAmap()
    else if (activeProvider.value === 'tencent') {
      txReady.value = false
      txCoord.value = await resolveStartCoord()  // 先定落点（表单坐标/浏览器定位，≤2.5s）
      txReady.value = true
      armWatchdog() // 起看门狗：超时未加载→切高德
    }
  } else {
    clearWatchdog(); destroyAmap(); txReady.value = false
  }
})

function close() { emit('close') }
// 未配置 Key 的演示兜底：回一个虚构地点（无经纬度），保证演示流程不断
function pickDemo() {
  emit('picked', { name: '阳光花园·党群服务中心', address: '（地图演示地点，配置 Key 后为真实选点）', lat: null, lng: null })
  emit('close')
}
</script>

<style scoped>
.mp-mask { position: fixed; inset: 0; z-index: 1200; background: rgba(15, 23, 32, 0.55); display: flex; align-items: flex-end; }
.mp-sheet { width: 100%; height: 82vh; background: #fff; border-radius: 24rpx 24rpx 0 0; display: flex; flex-direction: column; overflow: hidden; }
.mp-head { flex-shrink: 0; display: flex; align-items: center; justify-content: space-between; padding: 24rpx 28rpx; border-bottom: 2rpx solid #EEF0F3; }
.mp-title { font-size: 32rpx; font-weight: 700; color: #1f2329; }
.mp-close { width: 56rpx; height: 56rpx; display: flex; align-items: center; justify-content: center; font-size: 44rpx; color: #7A818B; cursor: pointer; }
.mp-frame { flex: 1; width: 100%; border: 0; }
/* 高德模式 */
.mp-search-row { flex-shrink: 0; display: flex; gap: 14rpx; padding: 16rpx 24rpx; }
.mp-search-input { flex: 1; min-height: 72rpx; border: 2rpx solid #E2E5E9; border-radius: 14rpx; padding: 0 20rpx; font-size: 28rpx; }
.mp-search-btn { flex-shrink: 0; min-height: 72rpx; padding: 0 30rpx; border: 0; border-radius: 14rpx; background: #3F6078; color: #fff; font-size: 28rpx; font-weight: 600; }
.mp-results { flex-shrink: 0; max-height: 300rpx; overflow-y: auto; border-bottom: 2rpx solid #EEF0F3; }
.mp-result-row { padding: 16rpx 28rpx; border-bottom: 2rpx solid #F4F6F8; cursor: pointer; }
.mp-result-row:active { background: #F4F8FB; }
.mp-result-name { font-size: 28rpx; font-weight: 600; color: #1f2329; }
.mp-result-addr { font-size: 24rpx; color: #6B7280; margin-top: 4rpx; }
.mp-map-wrap { flex: 1; position: relative; min-height: 0; }
.mp-map { width: 100%; height: 100%; }
.mp-pin { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -100%); font-size: 52rpx; pointer-events: none; }
.mp-map-err { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; padding: 0 48rpx; text-align: center; background: #F7F8FA; color: #B02A1E; font-size: 28rpx; }
/* 0801 设计师点9：确认栏改纵向——地址正是要确认的东西，不许「…中华人民共…」截断：
   占满一行、最多两行折行；「确认」移到下面独立一整行（主操作贴底） */
.mp-confirm-bar { flex-shrink: 0; display: flex; flex-direction: column; align-items: stretch; gap: 14rpx; padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom)); border-top: 2rpx solid #EEF0F3; }
.mp-cur { min-width: 0; }
.mp-cur-name { font-size: 30rpx; font-weight: 700; color: #1f2329; line-height: 1.4; }
.mp-cur-addr { font-size: 24rpx; color: #6B7280; margin-top: 4rpx; line-height: 1.5; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
.mp-confirm-btn { width: 100%; min-height: 88rpx; padding: 0; border: 0; border-radius: 16rpx; background: #3567A4; color: #fff; font-size: 32rpx; font-weight: 700; }  /* 杂蓝 #3F6078 并入会议蓝 */
.mp-confirm-btn:disabled { opacity: 0.5; }
/* 0801 点10：腾讯 iframe 等默认落点解析时的占位（≤2.5s） */
.mp-locating { flex: 1; display: flex; align-items: center; justify-content: center; color: #8A97A6; font-size: 28rpx; }
/* 未配置 Key */
.mp-nokey { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 18rpx; padding: 0 48rpx; text-align: center; }
.mp-nokey-title { font-size: 32rpx; font-weight: 700; color: #1f2329; }
.mp-nokey-sub { font-size: 26rpx; color: #6B7280; line-height: 1.6; }
.mp-demo-btn { margin-top: 16rpx; min-height: 76rpx; padding: 0 48rpx; border: 2rpx solid #C9D8E5; border-radius: 14rpx; background: #EAF3FB; color: #2F5678; font-size: 28rpx; font-weight: 600; }
</style>
