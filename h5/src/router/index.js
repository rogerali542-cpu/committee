import { createRouter, createWebHistory } from 'vue-router'
import { getStorage } from '@/utils/storage'

// 已迁移为实页；孤儿页（meeting-hub/info-public/property-board/Placeholder）已随死代码清理移除。
const routes = [
  { path: '/', redirect: '/main' },
  { path: '/login', component: () => import('@/pages/Login.vue'), meta: { title: '登录', noAuth: true } },

  // tabBar 两页（小区首页已移除）
  { path: '/main', component: () => import('@/pages/Committee.vue'), meta: { title: '业委会', tab: true } },
  { path: '/profile', component: () => import('@/pages/Profile.vue'), meta: { title: '个人中心', tab: true } },

  // 核心主线（原"业委会/会议管理"页已并入主页 /main）
  { path: '/committee', redirect: '/main' },
  { path: '/committee-detail', component: () => import('@/pages/CommitteeDetail.vue'), meta: { title: '会议详情' } },
  { path: '/minutes', component: () => import('@/pages/Minutes.vue'), meta: { title: '会议纪要' } },
  { path: '/minutes-view', component: () => import('@/pages/MinutesView.vue'), meta: { title: '会议纪要' } },
  { path: '/news', component: () => import('@/pages/News.vue'), meta: { title: '党建新闻' } },
  { path: '/minutes-public', component: () => import('@/pages/MinutesPublic.vue'), meta: { title: '公开纪要' } },
  { path: '/minutes-internal', component: () => import('@/pages/MinutesInternal.vue'), meta: { title: '内部总结' } },
  { path: '/minutes-todos', component: () => import('@/pages/MinutesTodos.vue'), meta: { title: '待办事项' } },

  // 其余页
  { path: '/my-meeting', component: () => import('@/pages/MyMeeting.vue'), meta: { title: '我的会议' } },
  { path: '/meeting-live-quick', component: () => import('@/pages/MeetingLiveQuick.vue'), meta: { title: '会议进行' } },
  { path: '/reception', component: () => import('@/pages/Reception.vue'), meta: { title: '接待' } },
  { path: '/learning', component: () => import('@/pages/Learning.vue'), meta: { title: '学习' } },
  { path: '/learning-detail', component: () => import('@/pages/LearningDetail.vue'), meta: { title: '学习详情' } },
  { path: '/notifications', component: () => import('@/pages/Notifications.vue'), meta: { title: '通知' } },
  { path: '/todo', component: () => import('@/pages/Todo.vue'), meta: { title: '待办' } },
  { path: '/library', component: () => import('@/pages/Library.vue'), meta: { title: '资料库' } },
  { path: '/property-tasks', component: () => import('@/pages/PropertyTasks.vue'), meta: { title: '物业任务' } },
  { path: '/archive-detail', component: () => import('@/pages/ArchiveDetail.vue'), meta: { title: '归档详情' } },
  { path: '/admin', component: () => import('@/pages/Admin.vue'), meta: { title: '管理后台' } },
  { path: '/nav-stats', component: () => import('@/pages/NavStats.vue'), meta: { title: '软路由诊断' } },

  { path: '/:pathMatch(.*)*', redirect: '/main' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() { return { top: 0 } }
})

router.beforeEach((to) => {
  if (to.meta.noAuth) return true
  const token = getStorage('token', '')
  const activeRole = getStorage('activeRole', null)
  const logged = !!(token || (activeRole && activeRole.id))
  if (!logged) return { path: '/login' }
  return true
})

export default router
