<template>
  <header class="pub-nav">
    <div class="nav-back" @click="back">‹</div>
    <div class="nav-title">{{ title }}</div>
    <div class="nav-placeholder"></div>
  </header>
</template>

<script setup>
// 公示/通知页专用蓝色顶栏（区别于内部业委工作页的橙色 PageNav）。
// onBack：页面可指定明确的返回目标（历史回退在硬跳兜底后不可靠——栈里是什么全凭运气）
import { navigateBack } from '@/utils/navigate'
const props = defineProps({ title: { type: String, default: '' }, onBack: { type: Function, default: null } })
function back() {
  if (props.onBack) { props.onBack(); return }
  navigateBack()
}
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
