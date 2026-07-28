import { createRouter, createWebHistory } from 'vue-router'
import { getStorage } from '@/utils/storage'

// 已迁移为实页；孤儿页（meeting-hub/info-public/property-board/Placeholder）已随死代码清理移除。
const routes = [
  { path: '/', redirect: '/main' },
  { path: '/login', component: () => import('@/pages/Login.vue'), meta: { title: '登录', noAuth: true } },

  // 底部三个一级栏目：会议与接待复用同一业务页面，按 section 只展示各自内容。
  { path: '/main', component: () => import('@/pages/Committee.vue'), props: { section: 'meeting' }, meta: { title: '业委会会议', tab: true } },
  { path: '/reception-center', component: () => import('@/pages/Committee.vue'), props: { section: 'reception' }, meta: { title: '接待中心', tab: true } },
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
  // 会议记录/纪要 PDF 预览页（kind=record|minutes）：页内预览+导出
  { path: '/doc-preview', component: () => import('@/pages/DocPreview.vue'), meta: { title: '文档预览' } },

  // 其余页
  { path: '/my-meeting', component: () => import('@/pages/MyMeeting.vue'), meta: { title: '我的会议' } },
  { path: '/meeting-live-quick', component: () => import('@/pages/MeetingLiveQuick.vue'), meta: { title: '会议进行' } },
  // 接待 0716 重做（方案 A）：原 /reception 列表页已删——首页接待 tab 本来就有清单，
  // 点进去又是一个清单纯属重复。现在首页点某件 → 直接进这条的处理页。原页面见 commit 6745a12。
  { path: '/reception-detail', component: () => import('@/pages/ReceptionDetail.vue'), meta: { title: '接待处理' } },
  { path: '/reception-records', component: () => import('@/pages/ReceptionRecords.vue'), meta: { title: '接待记录' } },
  { path: '/reception-create', component: () => import('@/pages/ReceptionCreate.vue'), meta: { title: '登记接待' } },
  // 接待日安排：编辑接待时间/地点/接待人 + 导出公告 PDF 去打印（0717）
  { path: '/reception-notice', component: () => import('@/pages/ReceptionNotice.vue'), meta: { title: '接待日安排' } },
  { path: '/reception', redirect: '/reception-center' },
  { path: '/learning', component: () => import('@/pages/Learning.vue'), meta: { title: '学习培训', tab: true } },
  { path: '/learning-create', component: () => import('@/pages/LearningCreate.vue'), meta: { title: '新增学习记录' } },
  { path: '/learning-detail', component: () => import('@/pages/LearningDetail.vue'), meta: { title: '学习培训详情' } },
  { path: '/notifications', component: () => import('@/pages/Notifications.vue'), meta: { title: '通知' } },
  { path: '/todo', component: () => import('@/pages/Todo.vue'), meta: { title: '待办' } },
  { path: '/library', component: () => import('@/pages/Library.vue'), meta: { title: '资料库' } },
  { path: '/seal', component: () => import('@/pages/SealManagement.vue'), meta: { title: '印章管理', tab: true } },
  // 物业侧工作台 0716 随内部派单流下线：物业以后只在外部工单系统里干活。见 commit 6745a12。
  { path: '/archive-detail', component: () => import('@/pages/ArchiveDetail.vue'), meta: { title: '归档详情' } },
  { path: '/admin', component: () => import('@/pages/Admin.vue'), meta: { title: '管理后台' } },
  { path: '/secretary-management', component: () => import('@/pages/SecretaryManagement.vue'), meta: { title: '秘书授权管理' } },
  { path: '/management', component: () => import('@/pages/ManagementOverview.vue'), meta: { title: '履职管理' } },
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
  if (activeRole && activeRole.enabled === false) return { path: '/login' }
  const governmentManager = activeRole && (activeRole.role === '街道管理员' || activeRole.role === '区级管理员')
  if (governmentManager && to.path !== '/management' && to.path !== '/login') return { path: '/management' }
  if (!governmentManager && to.path === '/management') return { path: '/main' }
  return true
})

export default router
