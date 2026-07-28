<template>
  <!-- 「业委会」一级 tab 下的二级切换：会议 | 印章（0728 用户定：印章并入业委会，不单独占底栏格） -->
  <div class="gov-subtabs" :class="active">
    <button type="button" class="gst" :class="{ on: active === 'meeting' }" @click="go('meeting')">会议</button>
    <button type="button" class="gst" :class="{ on: active === 'seal' }" @click="go('seal')">印章</button>
  </div>
</template>

<script setup>
import { redirectTo } from '@/utils/navigate'

defineProps({ active: { type: String, default: 'meeting' } })

// 与底栏切换一致：回会议走 /main 的 tabs 布局硬跳；去印章用软路由（不同组件，能正常切视图）
function go(which) {
  if (which === 'seal') {
    if (window.location.pathname !== '/seal') redirectTo('/seal')
  } else {
    localStorage.setItem('home_layout', JSON.stringify('tabs'))
    window.location.replace('/main?home=tabs')
  }
}
</script>

<style scoped>
.gov-subtabs { display: flex; gap: 14rpx; padding: 16rpx 24rpx 4rpx; }
.gst {
  flex: 0 0 auto; min-width: 128rpx; padding: 12rpx 34rpx; border: 0; border-radius: 999rpx;
  background: #EEF0F3; color: #5B6675; font-size: 28rpx; font-weight: 600; line-height: 1.4;
}
/* 选中色随所在页主题走，避免深棕压在蓝色会议页上突兀 */
/* 选中＝品牌色胶囊 + 一点边缘发光（与底栏同款设计语言） */
.gov-subtabs.meeting .gst.on { background: #3E6BA8; color: #fff; box-shadow: 0 0 14rpx rgba(62, 107, 168, 0.5), 0 4rpx 12rpx rgba(62, 107, 168, 0.28); }  /* 会议＝业委会蓝 */
.gov-subtabs.seal .gst.on { background: #B0772E; color: #fff; box-shadow: 0 0 14rpx rgba(176, 119, 46, 0.5), 0 4rpx 12rpx rgba(176, 119, 46, 0.28); }     /* 印章＝暖琥珀 */
.gst:active { opacity: 0.75; }
</style>
