<template>
  <div class="profile-scroll" style="overflow-y:auto;">
    <!-- Header -->
    <div class="profile-header" :style="{ background: headerGrad, paddingTop: (statusBarHeight + 24) + 'px' }">
      <!-- 返回驾驶舱移入顶栏右上角（0730 用户定） -->
      <button type="button" class="ph-cockpit" :style="{ color: textColor, borderColor: textColor }" @click="goCockpitFromHd">返回首页</button>
      <div class="ph-avatar" :style="{ color: textColor }">{{ activeRole.realName && activeRole.realName[0] }}</div>
      <span class="ph-name" :style="{ color: textColor }">{{ activeRole.realName }}</span>
      <span class="ph-role-chip" :style="{ background: 'rgba(255,255,255,0.3)', color: textColor }">{{ activeRole.role }}</span>
      <span class="ph-desc" :style="{ color: textColor, opacity: 0.82 }">{{ roleDesc }}</span>
    </div>

    <!-- Stats Row (委员会成员) -->
    <div class="stats-row" v-if="showStats">
      <div class="stat-item">
        <span class="stat-num">{{ stats.total }}</span>
        <span class="stat-label">参会记录</span>
      </div>
      <div class="stat-item">
        <span class="stat-num">{{ stats.voted }}</span>
        <span class="stat-label">参与表决</span>
      </div>
      <div class="stat-item">
        <span class="stat-num">{{ stats.attended }}</span>
        <span class="stat-label">已参会</span>
      </div>
    </div>

    <!-- 身份切换 -->
    <div class="section-header">切换身份</div>
    <div class="section">
      <div
        class="menu-row"
        :class="{ active: item.id === activeRole.id }"
        v-for="item in internalRoles"
        :key="item.id"
        @click="switchRole(item)"
      >
        <div class="menu-icon" :class="'id-avatar-' + item.roleClass">{{ item.realName[0] }}</div>
        <span class="menu-label">{{ item.realName }}</span>
        <span class="menu-role-tag" :class="item.roleClass">{{ item.role }}</span>
        <span v-if="item.id === activeRole.id" class="menu-check">✓</span>
        <span v-else class="menu-arrow">›</span>
      </div>
    </div>

    <!-- 系统管理（仅管理员） -->
    <template v-if="recordsList.length">
      <div class="section-header">{{ recordsTitle }}</div>
      <div class="section">
        <div
          class="menu-row"
          v-for="item in recordsList"
          :key="item.label"
          @click="item.action === 'secretary' ? goSecretaryManagement() : showWip()"
        >
          <div class="menu-icon" :style="{ background: item.bg }">{{ item.icon }}</div>
          <span class="menu-label">{{ item.label }}</span>
          <span class="menu-arrow">›</span>
        </div>
      </div>
    </template>

    <!-- 印章管理（0731 用户定：低频入口从驾驶舱撤下，归个人中心） -->
    <div class="section-header">业委会事务</div>
    <div class="section">
      <div class="menu-row" @click="goSeal">
        <div class="menu-icon" style="background:#E6EDF8;">章</div>
        <span class="menu-label">印章管理</span>
        <span class="menu-arrow">›</span>
      </div>
    </div>

    <!-- 消息通知 -->
    <div class="section-header">消息通知</div>
    <div class="section">
      <div class="menu-row" @click="openNotifications">
        <div class="menu-icon" style="background:#E6EDF8;">🔔</div>
        <span class="menu-label">系统通知</span>
        <div v-if="unreadCount > 0" class="badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</div>
        <span class="menu-arrow">›</span>
      </div>
    </div>

    <button class="logout-btn" @click="doLogout">退出登录</button>
  </div>
</template>

<script setup>
import { ref, onMounted, onActivated } from 'vue'
import api from '@/api'
import { toast } from '@/utils/ui'
import { navigateTo, redirectTo } from '@/utils/navigate'
import { getStorage, setStorage } from '@/utils/storage'
import { useAuthStore } from '@/stores/auth'
import { meetingRecordingSession, discardMeetingRecording } from '@/composables/meetingRecordingSession'

const auth = useAuthStore()

const statusBarHeight = ref(0)
const activeRole = ref({})
// 顶栏「返回驾驶舱」（0730：由 TabBar 浮球移入顶栏）
function goCockpitFromHd() {
  setStorage('home_layout', 'portal')
  window.location.replace('/main?home=portal')
}
const headerGrad = ref('')
const textColor = ref('#5C3D00')
const roleDesc = ref('')
const roleClass = ref('')
const showStats = ref(false)
const stats = ref({ total: 0, voted: 0, attended: 0 })
const recordsTitle = ref('')
const recordsList = ref([])
const internalRoles = ref([])
const unreadCount = ref(0)

function refresh() {
  statusBarHeight.value = 20

  const role = getStorage('activeRole', null)
  if (!role) { redirectTo('/pages/login/login'); return }

  // 切换身份名单：优先用后端 user_roles（onMounted 拉取，根治写死名单与库脱节），兜底旧写死名单
  const allIdentities = serverIdentities.value || [
    { id: 1, realName: '张建国', role: '主任', roleClass: 'chair' },
    { id: 2, realName: '李秀英', role: '副主任', roleClass: 'chair' },
    { id: 3, realName: '王志强', role: '委员', roleClass: 'member' },
    { id: 4, realName: '赵丽娟', role: '委员', roleClass: 'member' },
    { id: 5, realName: '刘海涛', role: '委员', roleClass: 'member' },
    { id: 6, realName: '陈晓梅', role: '委员', roleClass: 'member' },
    { id: 7, realName: '杨国华', role: '委员', roleClass: 'member' },
    { id: 8, realName: '秘书小李', role: '业委会秘书', roleClass: 'secretary', enabled: true }
  ]

  const gradMap = {
    '主任': 'linear-gradient(160deg,#4470a5,#2f5f9e)',
    '副主任': 'linear-gradient(160deg,#4470a5,#2f5f9e)',
    '委员': 'linear-gradient(160deg,#4470a5,#2f5f9e)',
    '业委会秘书': 'linear-gradient(160deg,#4470a5,#2f5f9e)'
  }
  const descMap = {
    '主任': '负责召集主持会议，具有最高操作权限',
    '副主任': '协助主任开展工作，享有同等会议权限',
    '委员': '确认参会，参与表决，查看会议资料',
    '业委会秘书': '经主任授权，协助处理通知、材料与日常工作'
  }
  const roleClassMap = {
    '主任': '', '副主任': '', '委员': ''
  }

  const isCommittee = ['主任', '副主任', '委员'].includes(role.role)

  // Records menu by role
  let rTitle = ''
  let rList = []
  if (role.role === '主任') {
    rTitle = '人员与授权'
    rList = [
      { icon: '秘', bg: '#E8EEF8', label: '秘书授权管理', action: 'secretary' }
    ]
  }

  const s = { total: isCommittee ? 3 : 0, voted: isCommittee ? 2 : 0, attended: isCommittee ? 3 : 0 }

  activeRole.value = role
  headerGrad.value = gradMap[role.role] || gradMap['主任']
  textColor.value = '#fff'
  roleDesc.value = descMap[role.role] || ''
  roleClass.value = roleClassMap[role.role] || ''
  showStats.value = isCommittee
  stats.value = s
  recordsTitle.value = rTitle
  recordsList.value = rList
  internalRoles.value = allIdentities.filter(i => ['主任', '副主任', '委员', '业委会秘书'].includes(i.role))

  loadUnread()
}

async function loadUnread() {
  try {
    const res = await api.notificationList()
    unreadCount.value = res.unread || 0
  } catch (e) {}
}

function openNotifications() {
  navigateTo('/pages/notifications/notifications')
}

// 印章管理（0731 用户定：低频入口从驾驶舱撤下归此处）；硬跳保证必达（软路由偶发不切视图）
function goSeal() {
  window.location.assign('/seal')
}

async function switchRole(item) {
  if (item.id === activeRole.value.id) return
  if (item.enabled === false) {
    toast({ title: '该秘书授权已被主任收回', icon: 'none' })
    return
  }
  const newRole = {
    id: parseInt(item.id),
    role: item.role,
    realName: item.realName,
    communityId: item.communityId || 1,
    communityName: item.communityName || '阳光家园',
    enabled: item.enabled !== false,
    scopeLevel: item.scopeLevel,
    scopeRegionCode: item.scopeRegionCode,
    scopeRegionName: item.scopeRegionName
  }
  if (meetingRecordingSession.meetingId) {
    await discardMeetingRecording(meetingRecordingSession.meetingId)
  }
  auth.switchRole(newRole)
  toast({ title: '已切换为 ' + item.realName, icon: 'success' })
  // 切完整页重载：KeepAlive 缓存的会议页（连同上一身份的 MediaRecorder、beforeunload 守卫、
  // 内存状态）全部销毁，新身份从干净状态启动——否则旧身份的残留会拦跳转/串状态（真机踩过）
  setTimeout(() => window.location.reload(), 600)
}

function goSecretaryManagement() { navigateTo('/secretary-management') }
function showWip() { toast({ title: '功能开发中', icon: 'none' }) }
async function doLogout() {
  if (meetingRecordingSession.meetingId) {
    await discardMeetingRecording(meetingRecordingSession.meetingId)
  }
  auth.logout()
  redirectTo('/pages/login/login')
}

// 后端身份名单（唯一事实源=user_roles）：拉到后重刷切换列表；失败留 null 走兜底写死名单
const serverIdentities = ref(null)
async function loadServerIdentities() {
  try {
    const list = await api.devRoles()
    if (Array.isArray(list) && list.length) {
      serverIdentities.value = list.map(r => ({
          id: r.id, realName: r.realName, role: r.role,
          roleClass: r.role === '委员' ? 'member' : r.role === '业委会秘书' ? 'secretary' : 'chair',
          communityId: r.communityId, communityName: r.communityName,
          enabled: r.enabled !== false,
          scopeLevel: r.scopeLevel, scopeRegionCode: r.scopeRegionCode, scopeRegionName: r.scopeRegionName
        }))
      refresh()
    }
  } catch (e) { /* 后端未启动：走兜底名单 */ }
}

let mounted = false
onMounted(() => { mounted = true; refresh(); loadServerIdentities() })
onActivated(() => { if (mounted) refresh() })
</script>

<style scoped>
.profile-scroll { min-height: 100vh; background: #f4f5f7; }

/* Header */
.profile-header {
  position: relative;
  padding: 0 40rpx 56rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.ph-cockpit { position: absolute; top: calc(env(safe-area-inset-top) + 18rpx); right: 24rpx; min-height: 56rpx; padding: 0 26rpx; border: 2rpx solid currentColor; border-radius: 999rpx; background: rgba(255,255,255,.14); font-size: 26rpx; font-weight: 500; }
.ph-cockpit:active { opacity: .7; }
.ph-avatar {
  width: 160rpx; height: 160rpx;
  border-radius: 50%;
  background: rgba(255,255,255,0.35);
  font-size: 64rpx; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 20rpx;
  border: 4rpx solid rgba(255,255,255,0.6);
}
.ph-name { font-size: 44rpx; font-weight: 700; margin-bottom: 12rpx; }
.ph-role-chip {
  font-size: 28rpx; font-weight: 600;
  padding: 6rpx 24rpx; border-radius: 30rpx;
}
.ph-desc { font-size: 28rpx; margin-top: 14rpx; text-align: center; padding: 0 40rpx; line-height: 1.5; }

/* Stats */
.stats-row {
  display: flex;
  margin: -28rpx 24rpx 0;
  background: #fff; border-radius: 24rpx;
  box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06);
  overflow: hidden;
  position: relative; z-index: 2;
}
.stat-item {
  flex: 1;
  display: flex; flex-direction: column; align-items: center;
  padding: 28rpx 0; gap: 8rpx;
}
.stat-item:not(:last-child) { border-right: 2rpx solid #f0f0f0; }
.stat-num { font-size: 44rpx; font-weight: 700; color: #1f2329; }
.stat-label { font-size: 28rpx; color: #666; }

/* Section */
.section-header {
  font-size: 28rpx; color: #666; font-weight: 500;
  padding: 32rpx 32rpx 12rpx;
}
.section {
  margin: 0 24rpx;
  background: #fff; border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06);
}

/* Menu row */
.menu-row {
  display: flex; align-items: center; gap: 20rpx;
  padding: 28rpx 28rpx;
  border-top: 2rpx solid #f5f5f5;
}
.menu-row:first-child { border-top: none; }
.menu-row.active { background: #F0F5FB; }   /* 个人中心蓝系（0731 用户定：与会议同款蓝） */
.menu-row:active { background: #fafbfc; }
.menu-icon {
  width: 72rpx; height: 72rpx; border-radius: 18rpx;
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 36rpx;
}
.menu-label { flex: 1; font-size: 34rpx; color: #1f2329; }
.menu-arrow { font-size: 40rpx; color: #666; }
.menu-check { font-size: 38rpx; color: #FFA800; font-weight: 700; }

/* Identity group */
.menu-role-tag { font-size: 28rpx; padding: 4rpx 16rpx; border-radius: 10rpx; background: #f0f0f0; color: #666; }
.menu-role-tag.chair { background: #FFF3DC; color: #C77800; }
.menu-role-tag.recorder { background: #F5EEF8; color: #9B59B6; }

/* ID avatar colors */
.id-avatar-chair { background: linear-gradient(135deg,#FFCC44,#FFA800); color: #fff; }
.id-avatar-member { background: linear-gradient(135deg,#FFCC44,#FFA800); color: #fff; }
.id-avatar-recorder { background: linear-gradient(135deg,#BB8FCE,#9B59B6); color: #fff; }

/* Logout */
.logout-btn {
  margin: 32rpx 24rpx 48rpx;
  width: calc(100% - 48rpx);
  padding: 28rpx;
  background: #fff; border-radius: 24rpx;
  color: #E74C3C; font-size: 34rpx; font-weight: 600;
  text-align: center;
  border: none;
  box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06);
}

/* 通知未读红点 */
.badge {
  min-width: 36rpx; height: 36rpx; line-height: 36rpx; text-align: center;
  background: #E74C3C; color: #fff; font-size: 28rpx; font-weight: 700;
  border-radius: 18rpx; padding: 0 10rpx; margin-right: 8rpx;
}
</style>
