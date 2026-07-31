<template>
  <!-- 「返回驾驶舱」已移入各页顶栏右上角（0730 用户定，图一骨架），不再浮在底栏上方 -->
  <!-- merged 只给上方钉着固定操作条的工作页（驾驶舱无固定条，active 为空自然排除） -->
  <nav v-if="isTab" class="tabbar" :class="{ hidden: homeShell.navHidden, merged: activeKey === 'committee' || activeKey === 'reception' }">
    <div
      v-for="t in tabs"
      :key="t.key"
      class="tab"
      :class="[t.tone, { active: activeKey === t.key }]"
      @click="go(t)"
    >
      <!-- 图标底栏（0730 用户定：与驾驶舱底栏同款画风）——内联 SVG 线性图标 + 色块 -->
      <span class="tab-ico" :class="t.tone">
        <svg v-if="t.key === 'committee'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="9" cy="8" r="3.2"/><path d="M3.5 19c.6-3.2 2.8-5 5.5-5s4.9 1.8 5.5 5"/><circle cx="16.8" cy="9" r="2.4"/><path d="M15.6 13.6c2.3.2 4.1 1.7 4.7 4.4"/>
        </svg>
        <svg v-else-if="t.key === 'reception'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M4 6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v7a2.5 2.5 0 0 1-2.5 2.5H9l-4.2 3.4c-.4.3-.8 0-.8-.4V6.5z"/><line x1="8" y1="9" x2="16" y2="9"/><line x1="8" y1="12.5" x2="13" y2="12.5"/>
        </svg>
        <svg v-else-if="t.key === 'learning'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 6.5C10.5 5 8.2 4.4 5.5 4.4c-.8 0-1.5.6-1.5 1.4v11c0 .8.7 1.4 1.5 1.4 2.7 0 5 .6 6.5 2.1 1.5-1.5 3.8-2.1 6.5-2.1.8 0 1.5-.6 1.5-1.4v-11c0-.8-.7-1.4-1.5-1.4-2.7 0-5 .6-6.5 2.1z"/><line x1="12" y1="6.5" x2="12" y2="20.3"/>
        </svg>
        <svg v-else-if="t.key === 'seal'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M9.5 10.5c-.8-.9-1.3-2-1.3-3.2C8.2 5 9.9 3.4 12 3.4s3.8 1.6 3.8 3.9c0 1.2-.5 2.3-1.3 3.2l-.6.7c-.3.4-.3.9 0 1.3h2.9c1.4 0 2.6 1.1 2.6 2.6v1.5H4.6v-1.5c0-1.4 1.2-2.6 2.6-2.6h2.9c.3-.4.3-.9 0-1.3l-.6-.7z"/><line x1="5.5" y1="20" x2="18.5" y2="20"/>
        </svg>
        <svg v-else-if="t.key === 'home'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M4 11.2 12 4.4l8 6.8"/><path d="M6.2 9.6V19a1 1 0 0 0 1 1h3.1v-4.8h3.4V20h3.1a1 1 0 0 0 1-1V9.6"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="8" r="3.6"/><path d="M5 20c.8-3.8 3.5-5.8 7-5.8s6.2 2 7 5.8"/>
        </svg>
      </span>
      <span class="tab-label">{{ t.label }}</span>
    </div>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { redirectTo } from '@/utils/navigate'
import { homeShell } from '@/composables/homeShell'

const route = useRoute()
// 四项底栏（0731 设计师二改：第四格「个人中心」→「首页」。个人中心是低频入口不占底栏 1/4，
// 挪到页头身份行（张建国 · 主任，点击即进）；首页(驾驶舱)才是每天要回的地方，给常驻格）
const tabs = [
  { path: '/main', label: '首页', key: 'home', tone: 'slate' },   /* 0731 用户定：首页放第一格；驾驶舱＝中性岩灰，不属于任何模块色 */
  { path: '/main', label: '业委会', key: 'committee', tone: 'blue' },
  { path: '/reception-center', label: '业主接待', key: 'reception', tone: 'green' },
  { path: '/learning', label: '学习培训', key: 'learning', tone: 'amber' }
]
// 高亮按 key（两格共用 /main）：驾驶舱亮「首页」，工作态 /main 亮「业委会」。
// welcomeVisible（=驾驶舱态）与 URL 双判定，防开发期热更新时挂载顺序造成误亮。
const routeIsCockpit = computed(() => route.path === '/main' && route.query.home === 'portal')
const activeKey = computed(() => {
  if (homeShell.welcomeVisible || routeIsCockpit.value) return 'home'
  const hit = tabs.find((t) => t.path === route.path && t.key !== 'home')
  return hit ? hit.key : ''
})
const isTab = computed(() => tabs.some((t) => t.path === route.path))
// 标签切换用 replace(0725 用户报的 bug):底部四个 tab 是平级页,不应堆进历史。
// 原来用 push/location.assign,历史会累积「接待中心→业委会→详情页」,从详情页逐级返回时
// 会穿过 /main 再退回接待中心(用户遇到的"返回错误进入接待页面")。改 replace 后 tab 不占历史栈。
// /main 两格靠 home_layout + query 显式区分（Committee.vue 复用实例，必须带参刷新防误入另一态）。
function go(t) {
  if (t.key === activeKey.value) return
  if (t.key === 'home') {
    localStorage.setItem('home_layout', JSON.stringify('portal'))
    window.location.replace('/main?home=portal')
    return
  }
  if (t.key === 'committee') {
    localStorage.setItem('home_layout', JSON.stringify('tabs'))
    window.location.replace('/main?home=tabs')
    return
  }
  if (t.path !== route.path) redirectTo(t.path)
}
</script>

<style scoped>
/* 图标底栏（0730 用户定：驾驶舱同款画风）——高度约 110rpx，各页 132rpx 留白仍够 */
.tabbar {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 100;
  display: flex; background: #fff; border-top: 1rpx solid #ececec;
  padding: 8rpx 0 calc(6rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -6rpx 18rpx rgba(24, 51, 76, .05);
  transition: transform .22s ease;
}
/* 向下滚动时收起（0730 用户定，点8）：让出被操作条+导航栏叠占的高度 */
.tabbar.hidden { transform: translateY(120%); }
/* 上方钉着固定操作条的页（/main 会议操作条、/reception-center 接待动作区）：
   底栏去掉顶部描边+阴影，两片白连成一整片、共用一个背景（0730 用户定） */
.tabbar.merged { border-top: 0; box-shadow: none; }
/* 5 格（0730 二改：印章独立成 tab） */
.tab { flex: 1; min-width: 0; display: flex; flex-direction: column; align-items: center; gap: 5rpx; padding: 4rpx 0; }
.tab-ico { width: 52rpx; height: 52rpx; border-radius: 14rpx; display: flex; align-items: center; justify-content: center; transition: box-shadow .15s; }
.tab-ico svg { width: 32rpx; height: 32rpx; }
/* 配色（0730 用户定点6 + 规范三色制）：未选中统一灰；选中态点亮——三个工作模块用规范模块色
   （会议蓝#2b5589／接待绿#2f6b45／学习深青#2a6b73），首页(驾驶舱)非模块＝中性岩灰(slate)。
   注：learning 的 tone 类名仍叫 amber（沿用），色值已改为模块深青，橙不再作模块色 */
.tab-ico { color: #7E8794; background: #F1F3F6; }
.tab.active .tab-ico.blue { color: #2b5589; background: #E6EDF8; }
.tab.active .tab-ico.green { color: #2f6b45; background: #E4F0E8; }
.tab.active .tab-ico.amber { color: #2a6b73; background: #DDEBEC; }
.tab.active .tab-ico.seal { color: #A8484A; background: #F4E6E6; }   /* 印章＝印泥红，非模块 */
.tab.active .tab-ico.slate { color: #4A5560; background: #EDEFF3; }
.tab-label { font-size: 23rpx; font-weight: 500; color: #6A7482; line-height: 1; white-space: nowrap; transition: color .15s; }
/* 选中态：图标块描一圈本色 + 标签跟本色加粗 */
.tab.active .tab-ico { box-shadow: inset 0 0 0 3rpx currentColor; }
.tab.active .tab-label { color: #2F3D56; font-weight: 700; }
.tab.active.blue .tab-label { color: #2b5589; }
.tab.active.green .tab-label { color: #2f6b45; }
.tab.active.amber .tab-label { color: #2a6b73; }
.tab.active.seal .tab-label { color: #A8484A; }
.tab:active { opacity: .75; }
</style>
