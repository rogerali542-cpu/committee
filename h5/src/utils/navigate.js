// 导航工具：替代 wx.navigateTo/redirectTo/switchTab/navigateBack。
// 迁移时小程序里的路径字符串 '/pages/xxx/xxx?a=1' 可原样传入，这里解析成路由。
import router from '@/router'
import { recordAttempt } from '@/utils/navstats'

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

export function navigateTo(url) { recordAttempt(toRoute(url).path); return router.push(toRoute(url)) }
export function redirectTo(url) { recordAttempt(toRoute(url).path); return router.replace(toRoute(url)) }
export function switchTab(url) { recordAttempt(toRoute(url).path); return router.push(toRoute(url)) }
export function navigateBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/main') // 无上一页兜底回业委会主页（对齐 page-nav 兜底）
}

// 首页按钮统一出口(0725 用户定):回业委会首页或接待首页的工作台版式。
// 硬导航保证必达;replace 不在历史里叠一层,首页落地后按返回是离开前的上一页。
export function goModuleHome(section) {
  try { localStorage.setItem('home_layout', JSON.stringify('tabs')) } catch (e) { /* 无痕模式等场景忽略 */ }
  window.location.replace(section === 'reception' ? '/reception-center' : '/main?home=tabs')
}

export default { navigateTo, redirectTo, switchTab, navigateBack, goModuleHome, toRoute }
