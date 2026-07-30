import { reactive } from 'vue'

// 首页外壳共享态：欢迎引导页（路线乙）激活时隐藏底部标签栏——
// 用户「选定业务前不显示底栏，选了之后再出现」。Committee.vue 置位，TabBar.vue 读取。
export const homeShell = reactive({
  welcomeVisible: false,
  // 0730 用户定：向下滚动时隐藏底部一级导航栏，让出被操作条+导航栏叠占的高度；上滑复现。
  navHidden: false
})
