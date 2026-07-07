import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { readFileSync } from 'node:fs'

// 实时语音识别 WS 代理需要 ocr-asr-service 的内网令牌。令牌只存在 gitignore 的
// ocr-asr-service/.env（不入库），这里在开发期读出来，注入到 /asr-ws → :8003 的转发里，
// 浏览器只连同源 /asr-ws，拿不到令牌。读不到则代理不带令牌（服务端会 403，开发期自查即可）。
function loadAsrToken() {
  try {
    const env = readFileSync(fileURLToPath(new URL('../ocr-asr-service/.env', import.meta.url)), 'utf-8')
    const m = env.match(/^\s*INTERNAL_TOKEN\s*=\s*(.+)\s*$/m)
    return m ? m[1].trim() : ''
  } catch (e) { return '' }
}
const ASR_TOKEN = process.env.ASR_INTERNAL_TOKEN || loadAsrToken()
const ASR_TARGET = process.env.OCR_SERVICE_WS_TARGET || 'ws://localhost:8003'

// 开发期：前端 5173，/api 反代到后端 8080，避免跨域 + 混合内容（手机微信里也走同源）。
// 基础配置抽出复用：vite.config.https.js 会在此之上叠加自签 HTTPS（手机真机测相机用）。
export const baseConfig = {
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
    allowedHosts: ['.trycloudflare.com'], // 放行 cloudflared 隧道域名，否则 Vite6 会拦「Blocked request」
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 实时流式 ASR：浏览器 WS /asr-ws → ocr-asr-service /v1/asr/stream（注入内网令牌）
      '/asr-ws': {
        target: ASR_TARGET,
        ws: true,
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReqWs', (proxyReq) => {
            proxyReq.path = '/v1/asr/stream' + (ASR_TOKEN ? ('?token=' + encodeURIComponent(ASR_TOKEN)) : '')
          })
        }
      }
    }
  }
}

export default defineConfig(baseConfig)
