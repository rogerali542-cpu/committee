<template>
  <header class="pub-nav">
    <div class="nav-back" @click="back">‹</div>
    <div class="nav-title">{{ title }}</div>
    <div class="nav-placeholder"></div>
  </header>
</template>

<script setup>
// 公示/通知页专用蓝色顶栏（区别于内部业委工作页的橙色 PageNav）。
import { navigateBack } from '@/utils/navigate'
defineProps({ title: { type: String, default: '' } })
function back() { navigateBack() }
</script>

<style scoped>
.pub-nav {
  position: sticky; top: 0; z-index: 50;
  background: var(--pub-nav-grad, linear-gradient(160deg, #5D8EB8 0%, #3D70A0 100%));
  padding-top: env(safe-area-inset-top);
  display: flex; align-items: center;
  height: calc(116rpx + env(safe-area-inset-top));
  box-sizing: content-box;
}
.nav-back { width: 88rpx; height: 88rpx; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 52rpx; font-weight: 300; }
.nav-title { flex: 1; text-align: center; color: #fff; font-size: 38rpx; font-weight: 700; }
.nav-placeholder { width: 88rpx; }
</style>
