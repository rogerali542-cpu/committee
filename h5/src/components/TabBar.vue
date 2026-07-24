<template>
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
const isTab = computed(() => tabs.some((t) => t.path === route.path) && !homeShell.welcomeVisible)
function go(path) { if (path !== route.path) switchTab(path) }
</script>

<style scoped>
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
