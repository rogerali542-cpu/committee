// 导航工具：替代 wx.navigateTo/redirectTo/switchTab/navigateBack。
// 迁移时小程序里的路径字符串 '/pages/xxx/xxx?a=1' 可原样传入，这里解析成路由。
import router from '@/router'

export function toRoute(url) {
  const [pathPart, queryPart] = String(url).split('?')
  const m = pathPart.match(/\/pages\/([^/]+)\//)
  const path = m ? '/' + m[1] : pathPart
  const query = {}
  if (queryPart) {
    queryPart.split('&').forEach((kv) => {
      const idx = kv.indexOf('=')
      const k = idx >= 0 ? kv.slice(0, idx) : kv
      const v = idx >= 0 ? kv.slice(idx + 1) : ''
      if (k) query[k] = decodeURIComponent(v)
    })
  }
  return { path, query }
}

export function navigateTo(url) { return router.push(toRoute(url)) }
export function redirectTo(url) { return router.replace(toRoute(url)) }
export function switchTab(url) { return router.push(toRoute(url)) }
export function navigateBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/main') // 无上一页兜底回业委会主页（对齐 page-nav 兜底）
}

export default { navigateTo, redirectTo, switchTab, navigateBack, toRoute }
