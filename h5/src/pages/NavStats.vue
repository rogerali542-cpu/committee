<template>
  <div class="ns-page">
    <PageNav title="软路由诊断" style="margin:-24rpx -24rpx 0;" />
    <div class="ns-card">
      <div class="ns-row" v-for="(v, k) in flat" :key="k"><b>{{ k }}</b><span>{{ v }}</span></div>
    </div>
    <div class="ns-card">
      <div class="ns-title">各路由软跳次数</div>
      <div class="ns-row" v-for="(n, route) in data.各路由软跳" :key="route"><b>{{ route }}</b><span>{{ n }}</span></div>
    </div>
    <div class="ns-card">
      <div class="ns-title">救援明细（{{ (data.救援明细 || []).length }}）</div>
      <div class="ns-rescue" v-for="(r, i) in data.救援明细" :key="i">{{ r.时间 }} → {{ r.目标 }}（{{ r.延迟ms }}ms · {{ r.环境 }}）</div>
      <div v-if="!(data.救援明细 || []).length" class="ns-empty">暂无救援记录</div>
    </div>
    <button class="ns-reset" @click="doReset">清零重新统计</button>
  </div>
</template>

<script setup>
// 软路由失败率埋点的查看页（临时诊断用）：真机上直接访问 /nav-stats 即可看数据
import { ref, computed } from 'vue'
import PageNav from '@/components/PageNav.vue'
import navstats from '@/utils/navstats'

const data = ref(navstats.summary())
const flat = computed(() => {
  const d = data.value
  return { 统计开始: d.自, 软跳总次数: d.软跳总次数, 硬跳救援次数: d.硬跳救援次数, 其中正式环境: d.其中正式环境, 救援率: d.救援率 }
})
function doReset() {
  navstats.resetStats()
  data.value = navstats.summary()
}
</script>

<style scoped>
.ns-page { min-height: 100vh; background: #f4f5f7; padding: 24rpx 24rpx 60rpx; box-sizing: border-box; }
.ns-card { background: #fff; border-radius: 20rpx; padding: 24rpx 28rpx; margin-top: 24rpx; }
.ns-title { font-size: 30rpx; font-weight: 700; color: #333; margin-bottom: 12rpx; }
.ns-row { display: flex; justify-content: space-between; gap: 20rpx; font-size: 28rpx; color: #444; padding: 8rpx 0; }
.ns-row b { font-weight: 600; color: #666; }
.ns-rescue { font-size: 26rpx; color: #B23; line-height: 1.7; word-break: break-all; }
.ns-empty { font-size: 28rpx; color: #999; }
.ns-reset { display: block; margin: 32rpx auto 0; padding: 14rpx 48rpx; border: 1.5px solid #ccc; border-radius: 12rpx; background: #fff; color: #666; font-size: 28rpx; }
</style>
