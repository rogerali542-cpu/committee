<template>
  <div class="login">
    <div class="login-head">
      <div class="login-logo">业</div>
      <div class="login-title">业委专区</div>
      <div class="login-sub">请选择测试身份登录</div>
    </div>
    <div class="role-list">
      <div v-for="r in internalRoles" :key="r.id" class="role-item" @click="doLogin(r)">
        <div class="role-avatar">{{ r.realName.slice(0, 1) }}</div>
        <div class="role-info">
          <div class="role-name">{{ r.realName }} <span class="role-tag">{{ r.role }}</span></div>
          <div class="role-desc">{{ r.desc }}</div>
        </div>
        <div class="role-arrow">›</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useAuthStore } from '@/stores/auth'
import { redirectTo } from '@/utils/navigate'

const auth = useAuthStore()

// 1:1 自 miniapp/pages/login/login.js
const internalRoles = [
  { id: 99, realName: '系统管理员', role: '管理员', desc: '管理权限分配' },
  { id: 1, realName: '张建国', role: '主任', desc: '负责召集主持会议' },
  { id: 2, realName: '李秀英', role: '副主任', desc: '协助主任开展工作' },
  { id: 3, realName: '王志强', role: '委员', desc: '确认参会和参与表决' },
  { id: 4, realName: '赵丽娟', role: '委员', desc: '确认参会和参与表决' },
  { id: 5, realName: '刘海涛', role: '委员', desc: '确认参会和参与表决' },
  { id: 6, realName: '陈晓梅', role: '委员', desc: '确认参会和参与表决' },
  { id: 7, realName: '杨国华', role: '委员', desc: '确认参会和参与表决' }
]

function doLogin(r) {
  auth.login({ id: r.id, role: r.role, realName: r.realName, communityId: 1, communityName: '阳光家园' })
  redirectTo('/pages/main/main')
}
</script>

<style scoped>
.login { min-height: 100vh; background: linear-gradient(180deg, var(--c-primary-dark) 0%, var(--c-primary-dark) 220rpx, #f5f5f7 220rpx); padding: 0 32rpx; }
.login-head { text-align: center; padding: 90rpx 0 50rpx; }
.login-logo { width: 120rpx; height: 120rpx; margin: 0 auto 24rpx; background: #fff; color: #FFA800; border-radius: 28rpx; font-size: 64rpx; font-weight: 800; display: flex; align-items: center; justify-content: center; box-shadow: 0 8rpx 24rpx rgba(0,0,0,0.12); }
.login-title { font-size: 44rpx; font-weight: 800; color: #fff; }
.login-sub { font-size: 28rpx; color: rgba(255,255,255,0.9); margin-top: 10rpx; }
.role-list { background: #fff; border-radius: 24rpx; overflow: hidden; box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.05); }
.role-item { display: flex; align-items: center; padding: 28rpx 28rpx; border-bottom: 1rpx solid #f2f2f2; }
.role-item:last-child { border-bottom: none; }
.role-avatar { width: 84rpx; height: 84rpx; border-radius: 50%; background: #FFF3E0; color: #E67E22; font-size: 36rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.role-info { flex: 1; margin-left: 22rpx; }
.role-name { font-size: 32rpx; font-weight: 600; color: #1a1a1a; }
.role-tag { font-size: 22rpx; color: #FFA800; background: #FFF6E5; padding: 2rpx 12rpx; border-radius: 16rpx; margin-left: 8rpx; }
.role-desc { font-size: 24rpx; color: #666; margin-top: 6rpx; }
.role-arrow { color: #888; font-size: 40rpx; }
</style>
