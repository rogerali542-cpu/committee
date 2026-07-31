<template>
  <div class="login">
    <div class="login-head">
      <div class="login-logo">业</div>
      <div class="login-title">业委专区</div>
      <div class="login-sub">{{ (chair.communityName || '阳光花园') + '业主委员会' }}</div>
    </div>

    <!-- 账号密码表单（0731 用户定）：模拟手机号+密码(123456)登录，成功即以主任身份进入。
         原"选身份列表"退役——切换身份能力收在个人中心「切换身份 · 测试用」 -->
    <div class="login-card">
      <div class="lf-field">
        <span class="lf-label">手机号</span>
        <input class="lf-input" type="tel" v-model.trim="phone" maxlength="11" placeholder="请输入手机号" />
      </div>
      <div class="lf-field">
        <span class="lf-label">密码</span>
        <input class="lf-input" :type="showPwd ? 'text' : 'password'" v-model.trim="password" placeholder="请输入密码" />
        <span class="lf-eye" @click="showPwd = !showPwd">{{ showPwd ? '隐藏' : '显示' }}</span>
      </div>
      <button class="lf-btn" @click="doLogin">登 录</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api'
import { useAuthStore } from '@/stores/auth'
import { redirectTo } from '@/utils/navigate'
import { toast } from '@/utils/ui'
import { setStorage } from '@/utils/storage'

const auth = useAuthStore()

// 兜底主任（后端连不上时可用）——正常情况下 onMounted 用数据库 user_roles 的主任覆盖，
// 保证登录后的人名/小区与库一致
const FALLBACK_CHAIR = { id: 1, realName: '张建国', role: '主任', communityId: 1, communityName: '阳光花园' }
const chair = ref(FALLBACK_CHAIR)
const phone = ref('13800138000')   // 模拟账号预填，检查演示一键可进；仍可编辑并校验
const password = ref('123456')
const showPwd = ref(false)

onMounted(async () => {
  try {
    const list = await api.devRoles()
    const c = Array.isArray(list) ? list.find(r => r.role === '主任') : null
    if (c) {
      chair.value = {
        id: c.id, realName: c.realName, role: c.role,
        communityId: c.communityId || 1, communityName: c.communityName || '阳光花园',
        scopeLevel: c.scopeLevel, scopeRegionCode: c.scopeRegionCode, scopeRegionName: c.scopeRegionName
      }
    }
  } catch (e) { /* 后端未启动：用兜底主任，能进但人名可能与库不一致 */ }
})

function doLogin() {
  if (!/^1\d{10}$/.test(phone.value)) { toast({ title: '请输入 11 位手机号', icon: 'none' }); return }
  if (password.value !== '123456') { toast({ title: '密码不正确', icon: 'none' }); return }
  const r = chair.value
  auth.login({
    id: r.id, role: r.role, realName: r.realName,
    communityId: r.communityId || 1, communityName: r.communityName || '阳光花园',
    enabled: true, scopeLevel: r.scopeLevel,
    scopeRegionCode: r.scopeRegionCode, scopeRegionName: r.scopeRegionName
  })
  // 本次浏览器会话的登录标记（router 守卫检查）：关掉软件重开即失效→再进先到登录页；
  // 应用内的整页跳转（location.replace 换 tab 等）同会话不受影响
  try { sessionStorage.setItem('demo_authed', '1') } catch (e) { /* 隐私模式等取不到时不拦 */ }
  // 登录统一进驾驶舱首页（0731 用户定）
  setStorage('home_layout', 'portal')
  redirectTo('/main?home=portal')
  // 部分手机 WebView / Cloudflare 公网预览中偶发软路由不切页；登录态已写入后用硬跳兜底。
  setTimeout(() => {
    if (window.location.pathname === '/login') window.location.replace('/main?home=portal')
  }, 300)
}
</script>

<style scoped>
.login { min-height: 100vh; background: #f5f5f7; }
/* head 自带橙底：无论标题/副标题多高，白字始终落在橙色上，不会漏到灰底 */
.login-head { text-align: center; padding: 110rpx 32rpx 60rpx; background: var(--c-primary-dark); }
.login-logo { width: 120rpx; height: 120rpx; margin: 0 auto 24rpx; background: #fff; color: #FFA800; border-radius: 28rpx; font-size: 64rpx; font-weight: 800; display: flex; align-items: center; justify-content: center; box-shadow: 0 8rpx 24rpx rgba(0,0,0,0.12); }
.login-title { font-size: 44rpx; font-weight: 800; color: #fff; }
.login-sub { font-size: 28rpx; color: rgba(255,255,255,0.9); margin-top: 10rpx; }

/* 表单卡：62px 行高标准，输入字号放大（老年友好） */
.login-card { margin: 36rpx 32rpx; background: #fff; border-radius: 24rpx; padding: 16rpx 32rpx 40rpx; box-shadow: 0 4rpx 20rpx rgba(0,0,0,0.05); }
.lf-field { display: flex; align-items: center; gap: 20rpx; min-height: 124rpx; border-bottom: 2rpx solid #F0F2F5; }
.lf-label { flex-shrink: 0; width: 122rpx; font-size: 31rpx; font-weight: 600; color: #1F2937; }
.lf-input { flex: 1; min-width: 0; border: 0; outline: none; background: transparent; font-size: 33rpx; color: #1F2937; }
.lf-input::placeholder { color: #9AA4B0; }
.lf-eye { flex-shrink: 0; padding: 10rpx 4rpx; font-size: 27rpx; color: #6B7280; cursor: pointer; }
.lf-eye:active { opacity: .6; }
.lf-btn { display: block; width: 100%; margin-top: 48rpx; min-height: 100rpx; border: 0; border-radius: 999rpx; background: var(--c-primary-dark); color: #fff; font-size: 34rpx; font-weight: 700; letter-spacing: 8rpx; }
.lf-btn:active { opacity: .85; }
</style>
