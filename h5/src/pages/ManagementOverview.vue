<template>
  <div class="management-page">
    <header class="management-head">
      <div>
        <div class="management-title">业委会履职管理</div>
        <div class="management-scope">{{ scopeText }}</div>
      </div>
      <div class="identity">{{ activeRole.realName }} · {{ activeRole.role }}</div>
    </header>

    <section class="summary">
      <div class="summary-item"><strong>0</strong><span>小区业委会</span></div>
      <div class="summary-item"><strong>0</strong><span>履职提醒</span></div>
      <div class="summary-item"><strong>—</strong><span>平均评分</span></div>
    </section>

    <section class="map-card">
      <div class="map-toolbar">
        <div>
          <div class="section-title">辖区履职地图</div>
          <div class="section-sub">后续接入小区坐标、评分及履职数据</div>
        </div>
        <span class="data-state">接口已预留</span>
      </div>
      <div class="map-placeholder">
        <div class="map-grid"></div>
        <div class="map-empty">
          <div class="pin">⌖</div>
          <div class="map-empty-title">暂无小区数据</div>
          <div class="map-empty-text">数据接入后，可在地图查看会议、接待、学习及综合评分情况</div>
        </div>
      </div>
      <div class="legend">
        <span><i class="good"></i>正常</span>
        <span><i class="warning"></i>临近不达标</span>
        <span><i class="danger"></i>需要指导</span>
      </div>
    </section>

    <section class="empty-reminder">
      <div class="section-title">履职提醒</div>
      <div class="empty-line">暂无提醒数据</div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import api from '@/api'
import { getStorage } from '@/utils/storage'

const activeRole = ref(getStorage('activeRole', {}) || {})
const overview = ref(null)
const scopeText = computed(() => {
  if (overview.value && overview.value.scopeRegionName) return overview.value.scopeRegionName
  if (activeRole.value.scopeRegionName) return activeRole.value.scopeRegionName
  return activeRole.value.role === '区级管理员' ? '静安区' : '所属街道'
})

onMounted(async () => {
  try { overview.value = await api.managementOverview() } catch (e) { /* 尚未接入数据时保留空态 */ }
})
</script>

<style scoped>
.management-page { min-height: 100vh; background: #eef2f6; padding-bottom: 50rpx; color: #223047; }
.management-head { background: #526882; color: #fff; padding: 36rpx 34rpx 30rpx; display: flex; align-items: flex-end; justify-content: space-between; gap: 20rpx; }
.management-title { font-size: 38rpx; font-weight: 750; }
.management-scope, .identity { margin-top: 8rpx; color: rgba(255,255,255,.78); font-size: 24rpx; }
.identity { white-space: nowrap; }
.summary { margin: 24rpx; display: grid; grid-template-columns: repeat(3,1fr); gap: 16rpx; }
.summary-item { background: #fff; border-radius: 20rpx; padding: 24rpx 12rpx; text-align: center; box-shadow: 0 5rpx 18rpx rgba(42,62,84,.06); }
.summary-item strong { display: block; font-size: 38rpx; color: #3f6187; }
.summary-item span { display: block; margin-top: 6rpx; font-size: 23rpx; color: #7b8798; }
.map-card, .empty-reminder { margin: 0 24rpx 24rpx; background: #fff; border-radius: 24rpx; padding: 26rpx; box-shadow: 0 5rpx 18rpx rgba(42,62,84,.06); }
.map-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 20rpx; }
.section-title { font-size: 31rpx; font-weight: 700; }
.section-sub { margin-top: 6rpx; font-size: 23rpx; color: #8b96a5; }
.data-state { color: #65778c; background: #eef2f6; padding: 7rpx 14rpx; border-radius: 999rpx; font-size: 22rpx; white-space: nowrap; }
.map-placeholder { margin-top: 22rpx; height: 520rpx; border-radius: 20rpx; overflow: hidden; position: relative; background: #e9eef3; }
.map-grid { position: absolute; inset: 0; opacity: .38; background-image: linear-gradient(#c3ced9 2rpx,transparent 2rpx),linear-gradient(90deg,#c3ced9 2rpx,transparent 2rpx); background-size: 72rpx 72rpx; transform: rotate(-8deg) scale(1.15); }
.map-empty { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 40rpx; text-align: center; }
.pin { width: 82rpx; height: 82rpx; border-radius: 50%; background: #d9e3ed; color: #5f7894; display: flex; align-items: center; justify-content: center; font-size: 46rpx; }
.map-empty-title { margin-top: 20rpx; font-size: 30rpx; font-weight: 700; }
.map-empty-text { max-width: 500rpx; margin-top: 10rpx; color: #748296; font-size: 24rpx; line-height: 1.55; }
.legend { display: flex; gap: 28rpx; margin-top: 18rpx; color: #667386; font-size: 23rpx; }
.legend span { display: flex; align-items: center; gap: 8rpx; }
.legend i { width: 16rpx; height: 16rpx; border-radius: 50%; }
.legend .good { background: #4c9a70; }.legend .warning { background: #d39a43; }.legend .danger { background: #c96767; }
.empty-line { margin-top: 22rpx; padding: 28rpx; background: #f5f7f9; border-radius: 18rpx; text-align: center; color: #8a95a5; font-size: 25rpx; }
</style>
