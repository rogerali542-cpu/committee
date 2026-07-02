import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 开发期：前端 5173，/api 反代到后端 8080，避免跨域 + 混合内容（手机微信里也走同源）
export default defineConfig({
  plugins: [vue()],
  // 预构建 pdfjs，避免首次动态加载 PDF 预览时触发整页 reload
  optimizeDeps: {
    include: ['pdfjs-dist/legacy/build/pdf']
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: true, // 允许局域网 / cloudflared 隧道访问
    port: Number(process.env.PORT) || 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
