<template>
  <header class="page-nav">
    <div class="nav-back" @click="back">‹</div>
    <div class="nav-title">{{ title }}</div>
    <div class="nav-placeholder"></div>
  </header>
</template>

<script setup>
import { navigateBack } from '@/utils/navigate'
defineProps({ title: { type: String, default: '' } })
function back() { navigateBack() }
</script>

<style scoped>
.page-nav {
  position: sticky; top: 0; z-index: 50;
  background: linear-gradient(160deg, #FFCC44 0%, #FFA800 100%);
  padding-top: env(safe-area-inset-top);
  display: flex; align-items: center;
  height: calc(124rpx + env(safe-area-inset-top));
  box-sizing: content-box;
}
.nav-back { width: 96rpx; height: 124rpx; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 66rpx; font-weight: 700; }
.nav-title { flex: 1; text-align: center; color: #fff; font-size: 38rpx; font-weight: 700; }
.nav-placeholder { width: 96rpx; }
</style>
