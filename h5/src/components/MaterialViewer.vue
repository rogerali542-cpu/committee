<template>
  <div v-if="s.visible" class="mv-mask">
    <!-- 顶部橙色导航栏，大返回按钮（老年人友好） -->
    <header class="mv-bar">
      <div class="mv-back" @click="close">‹ 返回</div>
      <div class="mv-title">{{ s.name }}</div>
      <div class="mv-tools">
        <span v-if="kind === 'image' && !error" class="mv-tool" @click="toggleZoom">{{ zoom > 1 ? '缩小' : '放大' }}</span>
      </div>
    </header>

    <div ref="contentEl" class="mv-content" :class="kind === 'image' ? 'mv-dark' : 'mv-light'">
      <!-- 图片 -->
      <div v-if="kind === 'image'" class="mv-img-wrap">
        <img :src="s.url" class="mv-img" :style="{ width: (zoom * 100) + '%' }" @error="onMediaError" />
      </div>

      <!-- PDF -->
      <div v-else-if="kind === 'pdf'" class="mv-pdf">
        <div v-if="loading" class="mv-loading">
          <span class="mv-spin"></span>
          正在加载 PDF<span v-if="pdfTotal">（{{ pdfRendered }}/{{ pdfTotal }} 页）</span>…
        </div>
        <div ref="pdfPagesEl" class="mv-pdf-pages"></div>
      </div>

      <!-- 加载失败 / 不支持格式 兜底 -->
      <div v-if="error || kind === 'other'" class="mv-fallback">
        <span class="mv-fb-ic">📄</span>
        <p class="mv-fb-name">{{ s.name }}</p>
        <p class="mv-fb-tip">{{ error ? '无法在页面内预览此文件' : '该格式暂不支持页面内预览' }}</p>
        <button class="mv-fb-btn" @click="openExternal">用浏览器打开</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { useMaterialViewer, closeMaterialViewer } from '@/composables/materialViewer'
// 用 Vite 原生 ?worker 导入：让 Vite 自己把 pdf worker 当独立 worker 资源打包/服务，
// dev / prod / 微信 WebView 都走同一条正确路径，绕开 dev 下"裸 node_modules 文件 transform 卡住"的坑。
import PdfWorker from 'pdfjs-dist/legacy/build/pdf.worker.min.js?worker'

// 全局只建一个 worker 实例并复用（预览每次只渲染一个 PDF，串行使用安全）
let pdfWorker = null
function getPdfWorker() {
  if (!pdfWorker) pdfWorker = new PdfWorker()
  return pdfWorker
}

const s = useMaterialViewer()
const contentEl = ref(null)
const pdfPagesEl = ref(null)
const loading = ref(false)
const error = ref(false)
const zoom = ref(1)
const pdfTotal = ref(0)
const pdfRendered = ref(0)
let renderToken = 0

const kind = computed(() => {
  const ft = (s.fileType || '').toLowerCase()
  const u = (s.url || '').toLowerCase()
  if (/\.(jpg|jpeg|png|gif|webp|bmp)(\?|$)/.test(u) || /(jpg|jpeg|png|gif|webp|bmp|图片|照片|image)/.test(ft)) return 'image'
  if (/\.pdf(\?|$)/.test(u) || ft.includes('pdf')) return 'pdf'
  return 'other'
})

function close() { closeMaterialViewer() }
function openExternal() { if (s.url) window.open(s.url, '_blank') }
function toggleZoom() { zoom.value = zoom.value > 1 ? 1 : 2 }
function onMediaError() { error.value = true }

watch(() => s.visible, async (vis) => {
  if (vis) {
    zoom.value = 1
    error.value = false
    document.body.style.overflow = 'hidden'
    if (kind.value === 'pdf') {
      await nextTick()
      renderPdf(s.url)
    }
  } else {
    document.body.style.overflow = ''
    if (pdfPagesEl.value) pdfPagesEl.value.innerHTML = ''
    pdfTotal.value = 0
    pdfRendered.value = 0
    renderToken++
  }
})

async function renderPdf(url) {
  const token = ++renderToken
  loading.value = true
  error.value = false
  pdfTotal.value = 0
  pdfRendered.value = 0
  if (pdfPagesEl.value) pdfPagesEl.value.innerHTML = ''
  try {
    const mod = await import('pdfjs-dist/legacy/build/pdf')
    const pdfjsLib = (mod && mod.getDocument) ? mod : (mod.default || mod)
    // 用 workerPort 复用同一个 worker 实例，避免 workerSrc 走裸文件路径
    pdfjsLib.GlobalWorkerOptions.workerPort = getPdfWorker()
    const task = pdfjsLib.getDocument({ url, withCredentials: false })
    const pdf = await task.promise
    if (token !== renderToken) return
    pdfTotal.value = pdf.numPages
    const dpr = Math.min(window.devicePixelRatio || 1, 2)
    const cw = (contentEl.value ? contentEl.value.clientWidth : window.innerWidth) - 24
    for (let i = 1; i <= pdf.numPages; i++) {
      if (token !== renderToken) return
      const page = await pdf.getPage(i)
      const base = page.getViewport({ scale: 1 })
      const scale = cw > 0 ? cw / base.width : 1
      const vp = page.getViewport({ scale: scale * dpr })
      const canvas = document.createElement('canvas')
      canvas.className = 'mv-pdf-canvas'
      canvas.width = Math.floor(vp.width)
      canvas.height = Math.floor(vp.height)
      canvas.style.width = '100%'
      const ctx = canvas.getContext('2d')
      if (token !== renderToken) return
      if (pdfPagesEl.value) pdfPagesEl.value.appendChild(canvas)
      await page.render({ canvasContext: ctx, viewport: vp }).promise
      if (token !== renderToken) return
      pdfRendered.value = i
      loading.value = false
    }
  } catch (e) {
    if (token === renderToken) error.value = true
  } finally {
    if (token === renderToken) loading.value = false
  }
}
</script>

<style scoped>
.mv-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  background: #1c1c1e;
}
.mv-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: calc(20rpx + env(safe-area-inset-top)) 24rpx 20rpx;
  background: var(--c-primary-dark);
  flex-shrink: 0;
}
.mv-back {
  flex-shrink: 0;
  font-size: 34rpx;
  font-weight: 600;
  color: #fff;
  padding: 6rpx 10rpx 6rpx 0;
}
.mv-back:active { opacity: 0.7; }
.mv-title {
  flex: 1;
  min-width: 0;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.mv-tools {
  flex-shrink: 0;
  min-width: 96rpx;
  display: flex;
  justify-content: flex-end;
}
.mv-tool {
  font-size: 30rpx;
  color: #fff;
  padding: 6rpx 12rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.7);
  border-radius: 16rpx;
}
.mv-tool:active { background: rgba(255, 255, 255, 0.2); }

.mv-content {
  flex: 1;
  overflow: auto;
  -webkit-overflow-scrolling: touch;
}
.mv-dark { background: #1c1c1e; }
.mv-light { background: #e9e9ec; }

.mv-img-wrap {
  min-height: 100%;
  display: flex;
  align-items: flex-start;
  justify-content: center;
}
.mv-img {
  display: block;
  max-width: 100%;
  height: auto;
}

.mv-pdf { padding: 12rpx; }
.mv-pdf-pages { display: flex; flex-direction: column; gap: 16rpx; }
.mv-pdf-canvas {
  display: block;
  background: #fff;
  box-shadow: 0 2rpx 16rpx rgba(0, 0, 0, 0.18);
}
.mv-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
  padding: 80rpx 24rpx;
  font-size: 30rpx;
  color: #555;
}
.mv-spin {
  width: 36rpx;
  height: 36rpx;
  border: 5rpx solid #ddd;
  border-top-color: #FFA800;
  border-radius: 50%;
  animation: mv-rotate 0.8s linear infinite;
}
@keyframes mv-rotate { to { transform: rotate(360deg); } }

.mv-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  padding: 120rpx 48rpx;
  text-align: center;
}
.mv-fb-ic { font-size: 100rpx; }
.mv-fb-name { font-size: 32rpx; color: #1f2329; word-break: break-all; }
.mv-fb-tip { font-size: 28rpx; color: #888; }
.mv-fb-btn {
  margin-top: 16rpx;
  font-size: 32rpx;
  color: #fff;
  background: linear-gradient(160deg, #FFC23D, #FFA800);
  border: none;
  padding: 18rpx 56rpx;
  border-radius: 44rpx;
}
.mv-fb-btn:active { opacity: 0.85; }
</style>
