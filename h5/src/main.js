import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from '@/router'
import App from '@/App.vue'
import { useAuthStore } from '@/stores/auth'
import navstats from '@/utils/navstats'
import '@/styles/global.css'
import '@/styles/publish-theme.css'

// 软路由失败率埋点：本次整页加载若紧跟在一次软跳之后(<5s)，判定为硬跳兜底救援并计数
navstats.checkRescueOnBoot()
window.__navStats = navstats.summary

const app = createApp(App)
app.use(createPinia())
app.use(router)

// 启动时从 localStorage 恢复登录态（对齐小程序 app.js onLaunch）
useAuthStore().restore()

app.mount('#app')
