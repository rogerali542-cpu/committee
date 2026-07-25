<template>
  <div class="login">
    <div class="login-head">
      <div class="login-logo">业</div>
      <div class="login-title">业委专区</div>
      <div class="login-sub">请选择测试身份登录</div>
    </div>
    <div class="role-list">
      <div v-for="r in internalRoles" :key="r.id" class="role-item" :class="{ disabled: r.enabled === false }" @click="doLogin(r)">
        <div class="role-avatar">{{ r.realName.slice(0, 1) }}</div>
        <div class="role-info">
          <div class="role-name">{{ r.realName }} <span class="role-tag">{{ r.role }}</span></div>
          <div class="role-desc">{{ r.enabled === false ? '授权已被主任收回' : r.desc }}</div>
        </div>
        <div class="role-arrow">›</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'
import { useAuthStore } from '@/stores/auth'
import { redirectTo } from '@/utils/navigate'
import { toast } from '@/utils/ui'

const auth = useAuthStore()

const ROLE_DESC = {
  '主任': '负责召集主持会议',
  '副主任': '协助主任开展工作',
  '委员': '确认参会和参与表决',
  '业委会秘书': '经主任授权协助处理通知、材料和日常工作',
  '街道管理员': '查看本街道业委会履职情况',
  '区级管理员': '查看全区业委会履职情况'
}
// 兜底名单（后端连不上时可用）——正常情况下 onMounted 会用数据库 user_roles 覆盖，
// 根治"前端写死名单与库脱节→材料里人名对不上"（0723 测试反馈#1）
const FALLBACK_ROLES = [
  { id: 1, realName: '张建国', role: '主任', desc: ROLE_DESC['主任'] },
  { id: 2, realName: '李秀英', role: '副主任', desc: ROLE_DESC['副主任'] },
  { id: 3, realName: '王志强', role: '委员', desc: ROLE_DESC['委员'] },
  { id: 4, realName: '赵丽娟', role: '委员', desc: ROLE_DESC['委员'] },
  { id: 5, realName: '刘海涛', role: '委员', desc: ROLE_DESC['委员'] },
  { id: 6, realName: '陈晓梅', role: '委员', desc: ROLE_DESC['委员'] },
  { id: 7, realName: '杨国华', role: '委员', desc: ROLE_DESC['委员'] },
  { id: 8, realName: '秘书小李', role: '业委会秘书', desc: ROLE_DESC['业委会秘书'], enabled: true }
]
const internalRoles = ref(FALLBACK_ROLES)

onMounted(async () => {
  try {
    const list = await api.devRoles()
    if (Array.isArray(list) && list.length) {
      internalRoles.value = list.map(r => ({
        id: r.id, realName: r.realName, role: r.role,
        desc: ROLE_DESC[r.role] || '参与业委会工作',
        communityId: r.communityId || 1, communityName: r.communityName || '阳光家园',
        enabled: r.enabled !== false,
        scopeLevel: r.scopeLevel,
        scopeRegionCode: r.scopeRegionCode,
        scopeRegionName: r.scopeRegionName
      }))
    }
  } catch (e) { /* 后端未启动：用兜底名单，能进但材料人名可能对不上 */ }
})

function doLogin(r) {
  if (r.enabled === false) {
    toast({ title: '该秘书授权已被主任收回', icon: 'none' })
    return
  }
  auth.login({
    id: r.id, role: r.role, realName: r.realName,
    communityId: r.communityId || 1, communityName: r.communityName || '阳光家园',
    enabled: r.enabled !== false, scopeLevel: r.scopeLevel,
    scopeRegionCode: r.scopeRegionCode, scopeRegionName: r.scopeRegionName
  })
  const isGovernmentManager = r.role === '街道管理员' || r.role === '区级管理员'
  redirectTo(isGovernmentManager ? '/management' : '/pages/main/main')
  // 部分手机 WebView / Cloudflare 公网预览中偶发软路由不切页；登录态已写入后用硬跳兜底。
  setTimeout(() => {
    if (window.location.pathname === '/login') window.location.replace(isGovernmentManager ? '/management' : '/main')
  }, 300)
}
</script>

<style scoped>
.login { min-height: 100vh; background: #f5f5f7; }
/* head 自带橙底：无论标题/副标题多高，白字始终落在橙色上，不会漏到灰底 */
.login-head { text-align: center; padding: 90rpx 32rpx 44rpx; background: var(--c-primary-dark); }
.login-logo { width: 120rpx; height: 120rpx; margin: 0 auto 24rpx; background: #fff; color: #FFA800; border-radius: 28rpx; font-size: 64rpx; font-weight: 800; display: flex; align-items: center; justify-content: center; box-shadow: 0 8rpx 24rpx rgba(0,0,0,0.12); }
.login-title { font-size: 44rpx; font-weight: 800; color: #fff; }
.login-sub { font-size: 28rpx; color: rgba(255,255,255,0.9); margin-top: 10rpx; }
.role-list { margin: 28rpx 32rpx 40rpx; background: #fff; border-radius: 24rpx; overflow: hidden; box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.05); }
.role-item { display: flex; align-items: center; padding: 28rpx 28rpx; border-bottom: 1rpx solid #f2f2f2; }
.role-item:last-child { border-bottom: none; }
.role-item.disabled { opacity: 0.55; background: #f4f5f7; }
.role-avatar { width: 84rpx; height: 84rpx; border-radius: 50%; background: #FFF3E0; color: #E67E22; font-size: 36rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.role-info { flex: 1; margin-left: 22rpx; }
.role-name { font-size: 32rpx; font-weight: 600; color: #1a1a1a; }
.role-tag { font-size: 22rpx; color: #FFA800; background: #FFF6E5; padding: 2rpx 12rpx; border-radius: 16rpx; margin-left: 8rpx; }
.role-desc { font-size: 24rpx; color: #666; margin-top: 6rpx; }
.role-arrow { color: #888; font-size: 40rpx; }
</style>
