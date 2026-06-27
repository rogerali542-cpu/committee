<template>
  <div class="ph">
    <PageNav :title="title" />
    <div class="empty-state">
      <div class="ph-emoji">🚧</div>
      <div class="ph-text">「{{ title }}」迁移中</div>
      <div class="ph-sub">该页面将在第二期补全</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PageNav from '@/components/PageNav.vue'

const route = useRoute()
const title = computed(() => route.meta.title || '页面')
</script>

<style scoped>
.ph { min-height: 100vh; background: #f5f5f7; }
.ph-emoji { font-size: 90rpx; margin-bottom: 20rpx; }
.ph-text { font-size: 32rpx; color: #555; }
.ph-sub { font-size: 24rpx; color: #666; margin-top: 12rpx; }
</style>
