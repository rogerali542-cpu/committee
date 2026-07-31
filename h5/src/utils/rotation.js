// 接待轮值（0731 用户定）：接待人员按业委会成员顺序每周一人，本周场次结束自动轮到下一人。
// 不存「当前轮到谁」的指针——用「下一场接待所在周」距固定锚点的周数对成员数取模：
// 本周场次一结束，下一场落到下周，周数 +1，值班人自然换成下一个。零状态、刷新不乱、无需定时任务。

const DOW_MAP = { '一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6, '日': 0, '天': 0 }

// 接待人员=业委会成员（0725 用户定：主任/副主任/委员都算，不只委员），排除秘书等非成员岗
export const RECEPTION_ROLES = ['主任', '副主任', '委员']

// 轮值哨兵：后端 person 存这串（与种子数据同款），公告 PDF 原样打印、贴墙不过期
export const ROTATION = '当值委员轮值'

/** person 文本是否代表轮值模式：空或含「轮值」都算（轮值是默认态） */
export function isRotation(personText) {
  const t = String(personText || '').trim()
  return !t || t.includes('轮值')
}

/** 从成员名册筛出参与轮值的姓名（按名册顺序，即轮值顺序） */
export function rotationNames(roster) {
  return (roster || [])
    .filter(m => RECEPTION_ROLES.includes(String((m && m.role) || '').trim()))
    .map(m => m.name)
}

/** 下一场接待日期（当天场次已过结束时刻则算下周）。timeDesc 里解析不出周几返回 null */
export function nextSessionDate(timeDesc, now = new Date()) {
  const s = String(timeDesc || '')
  const m = s.match(/周([一二三四五六日天])/)
  if (!m) return null
  let days = (DOW_MAP[m[1]] - now.getDay() + 7) % 7
  if (days === 0) {
    const t = s.match(/[—–~-]\s*(\d{1,2}):(\d{2})/)
    const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), t ? Number(t[1]) : 20, t ? Number(t[2]) : 0)
    if (now > end) days = 7
  }
  return new Date(now.getFullYear(), now.getMonth(), now.getDate() + days)
}

/** 下一场接待的值班人姓名；成员为空/周几解析不出返回 '' */
export function dutyPersonFor(timeDesc, roster, now = new Date()) {
  const names = rotationNames(roster)
  if (!names.length) return ''
  const d = nextSessionDate(timeDesc, now)
  if (!d) return ''
  // 取该场次所在周的周一算周数：同一周内无论周几接待，结果一致
  const monday = new Date(d.getFullYear(), d.getMonth(), d.getDate() - ((d.getDay() + 6) % 7))
  const anchor = new Date(2026, 0, 5)   // 固定锚点：2026 年首个周一
  const weeks = Math.round((monday - anchor) / 604800000)
  return names[((weeks % names.length) + names.length) % names.length]
}
