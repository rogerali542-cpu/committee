<template>
  <header class="page-nav">
    <!-- 左侧默认单返回；父组件可用 #left / #right 具名插槽自定义（如通知页左箭头、右首页） -->
    <slot name="left"><div class="nav-back" @click="back">‹</div></slot>
    <div class="nav-title">{{ title }}</div>
    <slot name="right"><div class="nav-placeholder"></div></slot>
  </header>
</template>

<script setup>
import { navigateBack, redirectTo } from '@/utils/navigate'
// backTo：指定后退目标页(如 '/main')。给了就直接回该页(replace，不留在历史里)，
// 避免 router.back() 弹到中间旧页面导致状态看起来“丢了”；不给则沿用弹历史栈。
const props = defineProps({
  title: { type: String, default: '' },
  backTo: { type: String, default: '' }
})
function back() {
  if (props.backTo) redirectTo(props.backTo)
  else navigateBack()
}
</script>

<style scoped>
.page-nav {
  position: sticky; top: 0; z-index: 50;
  background: var(--c-primary-dark);
  padding-top: env(safe-area-inset-top);
  display: flex; align-items: center;
  height: calc(124rpx + env(safe-area-inset-top));
  box-sizing: content-box;
}
.nav-back { width: 96rpx; height: 124rpx; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 66rpx; font-weight: 700; }
.nav-title { flex: 1; text-align: center; color: #fff; font-size: 38rpx; font-weight: 700; }
.nav-placeholder { width: 96rpx; }
</style>
