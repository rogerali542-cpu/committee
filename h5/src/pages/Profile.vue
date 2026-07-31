<template>
  <div class="profile-scroll" style="overflow-y:auto;">
    <!-- 页头（0731 用户×设计师骨架）：中性深灰（与待办聚合/历史记录同族），‹ 返回首页 + 标题，
         下排 头像 + 姓名 + 机构·角色。原大头像居中 hero、角色说明句、参会统计卡整体退役——
         统计入口由下方「我的参会与接待记录」行承接 -->
    <div class="pf-hd">
      <div class="pf-hd-bar" @click="goCockpitFromHd">
        <i class="pf-back"></i>
        <span class="pf-hd-title">个人中心</span>
      </div>
      <div class="pf-id">
        <div class="pf-avatar">{{ activeRole.realName && activeRole.realName[0] }}</div>
        <div class="pf-id-main">
          <div class="pf-name">{{ activeRole.realName }}</div>
          <div class="pf-org">{{ orgLine }}</div>
        </div>
      </div>
    </div>

    <div class="pf-wrap">
      <!-- 切换身份（演示能力，标「测试用」防误会是正式功能） -->
      <div class="pf-sec-label">切换身份<span class="pf-sec-note"> · 测试用</span></div>
      <div class="pf-card">
        <div
          v-for="item in internalRoles"
          :key="item.id"
          class="pf-row"
          :class="{ active: item.id === activeRole.id }"
          @click="switchRole(item)"
        >
          <div class="pf-row-avatar">{{ item.realName[0] }}</div>
          <span class="pf-row-name">{{ item.realName }}</span>
          <span class="pf-row-role">{{ item.role }}</span>
          <span v-if="item.id === activeRole.id" class="pf-check">✓</span>
          <i v-else class="pf-arr"></i>
        </div>
      </div>

      <!-- 轻列表（骨架：透明底+分隔线，62px 行高标准）；原分组卡里的功能入口全数保留 -->
      <div class="pf-links">
        <div class="pf-link" @click="goMyRecords">
          <span class="pf-link-t">我的参会与接待记录</span>
          <i class="pf-arr"></i>
        </div>
        <div class="pf-link" @click="goSeal">
          <span class="pf-link-t">印章管理</span>
          <i class="pf-arr"></i>
        </div>
        <div v-if="isChair" class="pf-link" @click="goSecretaryManagement">
          <span class="pf-link-t">秘书授权管理</span>
          <i class="pf-arr"></i>
        </div>
        <div class="pf-link" @click="openNotifications">
          <span class="pf-link-t">系统通知</span>
          <div v-if="unreadCount > 0" class="badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</div>
          <i class="pf-arr"></i>
        </div>
        <!-- 「业委会信息」行已删（0731 用户定：页面长、该行又只是 WIP 占位没有落地页，等有页面再加回） -->
        <div class="pf-link" @click="doLogout">
          <span class="pf-link-t">退出登录</span>
          <i class="pf-arr"></i>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import api from '@/api'
import { toast } from '@/utils/ui'
import { navigateTo, redirectTo } from '@/utils/navigate'
import { getStorage, setStorage } from '@/utils/storage'
import { useAuthStore } from '@/stores/auth'
import { meetingRecordingSession, discardMeetingRecording } from '@/composables/meetingRecordingSession'

const auth = useAuthStore()

const activeRole = ref({})
// 顶栏 ‹ 返回首页（骨架：返回胶囊撤销，整行可点）
function goCockpitFromHd() {
  setStorage('home_layout', 'portal')
  window.location.replace('/main?home=portal')
}
const internalRoles = ref([])
const unreadCount = ref(0)
const isChair = computed(() => activeRole.value.role === '主任')
// 「阳光花园业委会 · 主任」——机构名+角色一行（骨架样式）
const orgLine = computed(() =>
  ((activeRole.value.communityName || '阳光花园') + '业委会 · ' + (activeRole.value.role || '')))

function refresh() {
  const role = getStorage('activeRole', null)
  if (!role) { redirectTo('/pages/login/login'); return }

  // 切换身份名单：优先用后端 user_roles（onMounted 拉取，根治写死名单与库脱节），兜底旧写死名单
  const allIdentities = serverIdentities.value || [
    { id: 1, realName: '张建国', role: '主任' },
    { id: 2, realName: '李秀英', role: '副主任' },
    { id: 3, realName: '王志强', role: '委员' },
    { id: 4, realName: '赵丽娟', role: '委员' },
    { id: 5, realName: '刘海涛', role: '委员' },
    { id: 6, realName: '陈晓梅', role: '委员' },
    { id: 7, realName: '杨国华', role: '委员' },
    { id: 8, realName: '秘书小李', role: '业委会秘书', enabled: true }
  ]

  activeRole.value = role
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

// 我的参会与接待记录 → 历史记录页（会议/接待归档）；软路由坑同款硬跳兜底
function goMyRecords() {
  navigateTo('/library')
  setTimeout(() => {
    if (!document.querySelector('.arch-page')) window.location.href = '/library'
  }, 300)
}

// 印章管理（0731 用户定：低频入口从驾驶舱撤下归此处）；硬跳保证必达（软路由偶发不切视图）
function goSeal() {
  window.location.assign('/seal')
}

async function switchRole(item) {
  // 点当前已选身份＝以该身份直接回首页（0731 用户定：确认选中即进入，不是无动作）
  if (item.id === activeRole.value.id) { goCockpitFromHd(); return }
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
  // 切完整页跳驾驶舱首页（0731 用户定：换身份后回首页开始新身份的工作，不停留在个人中心）。
  // 整页跳转与原先 reload 一样把 KeepAlive 缓存的会议页（上一身份的 MediaRecorder、
  // beforeunload 守卫、内存状态）全部销毁，新身份从干净状态启动——否则旧残留会拦跳转/串状态（真机踩过）
  setStorage('home_layout', 'portal')
  setTimeout(() => window.location.replace('/main?home=portal'), 600)
}

function goSecretaryManagement() { navigateTo('/secretary-management') }
async function doLogout() {
  if (meetingRecordingSession.meetingId) {
    await discardMeetingRecording(meetingRecordingSession.meetingId)
  }
  auth.logout()
  // 清本次会话登录标记（0731 登录页改造）：退出后守卫立即拦回登录页
  try { sessionStorage.removeItem('demo_authed') } catch (e) {}
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
.profile-scroll { min-height: 100vh; background: #f2f3f5; }

/* 页头：中性深灰渐变（个人中心不属于任何模块，与待办聚合页/历史记录同族色） */
.pf-hd { background: linear-gradient(160deg, #55606e 0%, #434d5a 100%); padding: calc(env(safe-area-inset-top) + 18rpx) 32rpx 40rpx; color: #fff; }
.pf-hd-bar { display: flex; align-items: center; gap: 18rpx; min-height: 72rpx; cursor: pointer; }
.pf-hd-bar:active { opacity: .75; }
/* CSS 边框画返回箭头（项目规约：不用字符箭头） */
.pf-back { display: inline-block; width: 18rpx; height: 18rpx; border-left: 4rpx solid #fff; border-bottom: 4rpx solid #fff; transform: rotate(45deg); }
.pf-hd-title { font-size: 34rpx; font-weight: 700; }
.pf-id { display: flex; align-items: center; gap: 24rpx; margin-top: 20rpx; }
.pf-avatar { flex-shrink: 0; width: 112rpx; height: 112rpx; border-radius: 50%; background: rgba(255,255,255,.28); display: flex; align-items: center; justify-content: center; font-size: 48rpx; font-weight: 700; }
.pf-id-main { min-width: 0; }
.pf-name { font-size: 40rpx; font-weight: 750; line-height: 1.2; }
.pf-org { margin-top: 8rpx; font-size: 27rpx; color: rgba(255,255,255,.75); }

.pf-wrap { padding: 0 24rpx 60rpx; }

/* 分区标签：「切换身份 · 测试用」 */
.pf-sec-label { padding: 32rpx 8rpx 16rpx; font-size: 30rpx; font-weight: 700; color: #1F2937; }
.pf-sec-note { font-weight: 500; font-size: 27rpx; color: #6B7280; }

/* 身份白卡：中性头像 + 姓名 + 右侧灰角色；当前项浅蓝底+蓝勾（个人中心蓝系） */
.pf-card { background: #fff; border-radius: 24rpx; box-shadow: 0 2rpx 6rpx rgba(31,41,55,.05), 0 10rpx 26rpx rgba(31,41,55,.07); overflow: hidden; }
/* 身份行压矮（0731 用户定：8 人列表占了整屏半，62→48px）——切身份是低频测试功能，不吃拇指区标准 */
.pf-row { display: flex; align-items: center; gap: 18rpx; min-height: 96rpx; padding: 0 28rpx; border-top: 2rpx solid #F0F2F5; cursor: pointer; }
.pf-row:first-child { border-top: 0; }
.pf-row:active { background: #FAFBFC; }
.pf-row.active { background: #EDF3FB; }
.pf-row-avatar { flex-shrink: 0; width: 60rpx; height: 60rpx; border-radius: 50%; background: #EDEFF3; color: #4A5560; font-size: 27rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; }
.pf-row-name { flex: 1; min-width: 0; font-size: 31rpx; font-weight: 700; color: #1F2937; }
.pf-row-role { flex-shrink: 0; font-size: 27rpx; color: #6B7280; }
.pf-check { flex-shrink: 0; font-size: 36rpx; font-weight: 800; color: #2b5589; }

/* 轻列表：透明底+分隔线（62px 行高标准，与驾驶舱一致） */
.pf-links { margin-top: 36rpx; padding: 0 8rpx; }
.pf-link { display: flex; align-items: center; gap: 12rpx; min-height: 124rpx; border-top: 2rpx solid #E2E5EA; cursor: pointer; }
.pf-link:last-child { border-bottom: 2rpx solid #E2E5EA; }
.pf-link:active { opacity: .65; }
.pf-link-t { flex: 1; min-width: 0; font-size: 32rpx; color: #1F2937; }
.pf-arr { flex-shrink: 0; display: inline-block; width: 14rpx; height: 14rpx; border-right: 3rpx solid #B4BCC7; border-bottom: 3rpx solid #B4BCC7; transform: rotate(-45deg); }

/* 通知未读红点 */
.badge {
  min-width: 36rpx; height: 36rpx; line-height: 36rpx; text-align: center;
  background: #E74C3C; color: #fff; font-size: 26rpx; font-weight: 700;
  border-radius: 18rpx; padding: 0 10rpx;
}
</style>
