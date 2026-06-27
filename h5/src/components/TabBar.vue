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
        <span v-if="t.badge && unread > 0" class="tab-badge">{{ unread > 99 ? '99+' : unread }}</span>
      </div>
      <div class="tab-label">{{ t.label }}</div>
    </div>
  </nav>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { switchTab } from '@/utils/navigate'

const route = useRoute()
const tabs = [
  { path: '/community-home', icon: '🏠', label: '小区首页' },
  { path: '/main', icon: '📋', label: '业委会', badge: true },
  { path: '/profile', icon: '👤', label: '个人中心' }
]
const unread = ref(0) // TODO 第二期：轮询未读通知数
const active = computed(() => route.path)
const isTab = computed(() => tabs.some((t) => t.path === route.path))
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
.tab-label { font-size: 28rpx; }
.tab-badge { position: absolute; top: -10rpx; right: -18rpx; min-width: 32rpx; height: 32rpx; padding: 0 6rpx; background: #E74C3C; color: #fff; font-size: 22rpx; border-radius: 16rpx; display: flex; align-items: center; justify-content: center; box-sizing: border-box; }
</style>
