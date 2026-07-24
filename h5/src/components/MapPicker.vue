<template>
  <div v-if="open" class="mp-mask" @click.self="close">
    <div class="mp-sheet">
      <div class="mp-head">
        <span class="mp-title">从地图选择地点</span>
        <span class="mp-close" @click="close">×</span>
      </div>
      <!-- 腾讯官方 H5 选点组件：搜索/定位/拖图选点后点组件内「确认」，postMessage 回传地址与经纬度 -->
      <iframe v-if="configured" :src="pickerUrl" class="mp-frame" frameborder="0" allow="geolocation"></iframe>
      <div v-else class="mp-nokey">
        <div class="mp-nokey-title">地图选点还未配置 Key</div>
        <div class="mp-nokey-sub">
          按 docs/地图接入教程.md 申请腾讯地图 Key，填入 h5/.env.local 的
          VITE_TXMAP_KEY / VITE_TXMAP_REFERER 并重启前端即可启用真实地图。
        </div>
        <button type="button" class="mp-demo-btn" @click="pickDemo">先用演示地点</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'picked'])

const KEY = import.meta.env.VITE_TXMAP_KEY || ''
const REFERER = import.meta.env.VITE_TXMAP_REFERER || '业委会智能履职'
const configured = computed(() => !!KEY)

const pickerUrl = computed(() =>
  'https://apis.map.qq.com/tools/locpicker?search=1&type=1'
  + '&key=' + encodeURIComponent(KEY)
  + '&referer=' + encodeURIComponent(REFERER))

// 选点组件回传：{ module:'locationPicker', latlng:{lat,lng}, poiaddress, poiname, cityname }
function onMessage(ev) {
  if (!props.open) return
  const d = ev && ev.data
  if (!d || d.module !== 'locationPicker') return
  emit('picked', {
    name: d.poiname || '',
    address: d.poiaddress || '',
    lat: d.latlng && d.latlng.lat != null ? Number(d.latlng.lat) : null,
    lng: d.latlng && d.latlng.lng != null ? Number(d.latlng.lng) : null
  })
  emit('close')
}
onMounted(() => window.addEventListener('message', onMessage, false))
onBeforeUnmount(() => window.removeEventListener('message', onMessage, false))

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
.mp-nokey { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 18rpx; padding: 0 48rpx; text-align: center; }
.mp-nokey-title { font-size: 32rpx; font-weight: 700; color: #1f2329; }
.mp-nokey-sub { font-size: 26rpx; color: #6B7280; line-height: 1.6; }
.mp-demo-btn { margin-top: 16rpx; min-height: 76rpx; padding: 0 48rpx; border: 2rpx solid #C9D8E5; border-radius: 14rpx; background: #EAF3FB; color: #2F5678; font-size: 28rpx; font-weight: 600; }
</style>
