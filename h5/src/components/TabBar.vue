<template>
  <button v-if="isTab" class="cockpit-return" type="button" @click="backToCockpit">
    返回驾驶舱
  </button>
  <nav v-if="isTab" class="tabbar">
    <div
      v-for="t in tabs"
      :key="t.path"
      class="tab"
      :class="{ active: active === t.path }"
      @click="go(t.path)"
    >
      <div class="tab-icon">
        {{ t.icon }}
      </div>
      <div class="tab-label">{{ t.label }}</div>
    </div>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { switchTab } from '@/utils/navigate'
import { homeShell } from '@/composables/homeShell'

const route = useRoute()
const tabs = [
  { path: '/main', icon: '📋', label: '业委会会议' },
  { path: '/reception-center', icon: '🤝', label: '接待中心' },
  { path: '/learning', icon: '📚', label: '学习培训' },
  { path: '/profile', icon: '👤', label: '个人中心' }
]
const active = computed(() => route.path)
// 欢迎引导页激活时隐藏底栏（选定业务后由 Committee.vue 复位再显示）
// URL 是驾驶舱时直接判定隐藏，避免开发期热更新中新旧页面卸载/挂载顺序造成底栏短暂误显。
const routeIsCockpit = computed(() => route.path === '/main' && route.query.home === 'portal')
const isTab = computed(() => tabs.some((t) => t.path === route.path) && !routeIsCockpit.value && !homeShell.welcomeVisible)
function go(path) {
  if (path === '/main') {
    // “业委会会议”是工作页入口；驾驶舱只能由单独的“返回驾驶舱”按钮进入。
    // Committee.vue 在 /main 与 /reception-center 间会复用实例，因此这里显式带 tabs 并刷新，
    // 避免沿用 localStorage 中的 portal 布局而误入驾驶舱。
    localStorage.setItem('home_layout', JSON.stringify('tabs'))
    window.location.assign('/main?home=tabs')
    return
  }
  if (path !== route.path) switchTab(path)
}
function backToCockpit() {
  localStorage.setItem('home_layout', JSON.stringify('portal'))
  window.location.assign('/main?home=portal')
}
</script>

<style scoped>
.cockpit-return {
  position: fixed;
  right: 22rpx;
  bottom: calc(112rpx + env(safe-area-inset-bottom));
  z-index: 101;
  min-height: 48rpx;
  padding: 0 20rpx;
  border: 2rpx solid rgba(47, 61, 86, .18);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, .94);
  box-shadow: 0 5rpx 16rpx rgba(30, 42, 62, .10);
  color: #53627A;
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1;
}
.cockpit-return:active { transform: translateY(1rpx); background: #F3F6F9; }
.tabbar {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 100;
  display: flex; background: #fff; border-top: 1rpx solid #ececec;
  padding-bottom: env(safe-area-inset-bottom);
  height: calc(100rpx + env(safe-area-inset-bottom));
}
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4rpx; color: #666; }
.tab.active { color: #FFA800; }
.tab-icon { position: relative; font-size: 44rpx; line-height: 1; }
.tab-label { font-size: 26rpx; }
</style>
