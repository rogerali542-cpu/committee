// API 统一入口：import api from '@/api' → api.committeeXxx() 等（扁平导出，对齐小程序 utils/api/index.js）
import core from '@/api/core'
import auth from '@/api/auth'
import dashboard from '@/api/dashboard'
import committee from '@/api/committee'
import reception from '@/api/reception'
import learning from '@/api/learning'
import publicInfo from '@/api/public-info'
import notice from '@/api/notice'
import notifications from '@/api/notifications'

export default {
  // 便捷方法
  get: core.get,
  post: core.post,
  put: core.put,
  delete: core.delete,
  // 各领域模块的方法扁平合并（方法名与小程序一致）
  ...auth,
  ...dashboard,
  ...committee,
  ...reception,
  ...learning,
  ...publicInfo,
  ...notice,
  ...notifications
}
