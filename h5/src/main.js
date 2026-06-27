import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from '@/router'
import App from '@/App.vue'
import { useAuthStore } from '@/stores/auth'
import '@/styles/global.css'
import '@/styles/publish-theme.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)

// 启动时从 localStorage 恢复登录态（对齐小程序 app.js onLaunch）
useAuthStore().restore()

app.mount('#app')
