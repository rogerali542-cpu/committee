// 录音切片落地（IndexedDB）：录音中每秒的音频块顺手写盘，页面被刷新/微信杀后台后，
// 重进会议页可发现"孤儿会话"并恢复上传——把录音丢失范围从"整场"压缩到"最后约1秒"。
// 设计约束：所有函数吞错误（返回 null/false/[]），存储故障绝不能反过来弄坏录音本身。
// 数据模型：sessions{key, meetingKey, mimeType, startedAt} + chunks{id自增, sessionKey, blob}
// （chunks 按自增 id 升序 = 时间顺序，拼接后与内存版 stop() 的产物一致）

const DB_NAME = 'ywh_rec_store'
const DB_VER = 1
const MAX_AGE_MS = 7 * 24 * 3600 * 1000 // 超 7 天的孤儿会话自动清理

let _dbPromise = null
function openDB() {
  if (_dbPromise) return _dbPromise
  _dbPromise = new Promise((resolve, reject) => {
    try {
      const req = indexedDB.open(DB_NAME, DB_VER)
      req.onupgradeneeded = () => {
        const db = req.result
        if (!db.objectStoreNames.contains('sessions')) db.createObjectStore('sessions', { keyPath: 'key' })
        if (!db.objectStoreNames.contains('chunks')) {
          const s = db.createObjectStore('chunks', { keyPath: 'id', autoIncrement: true })
          s.createIndex('bySession', 'sessionKey')
        }
      }
      req.onsuccess = () => resolve(req.result)
      req.onerror = () => reject(req.error)
    } catch (e) { reject(e) }
  })
  return _dbPromise
}

function prom(req) {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

export async function beginSession(key, meta) {
  try {
    const db = await openDB()
    db.transaction('sessions', 'readwrite').objectStore('sessions')
      .put(Object.assign({ key, startedAt: Date.now() }, meta || {}))
    return true
  } catch (e) { return false }
}

export async function appendChunk(sessionKey, blob) {
  try {
    const db = await openDB()
    db.transaction('chunks', 'readwrite').objectStore('chunks').add({ sessionKey, blob })
    return true
  } catch (e) { return false }
}

export async function clearSession(key) {
  try {
    const db = await openDB()
    db.transaction('sessions', 'readwrite').objectStore('sessions').delete(key)
    const idx = db.transaction('chunks', 'readwrite').objectStore('chunks').index('bySession')
    const ids = await prom(idx.getAllKeys(key))
    const store = db.transaction('chunks', 'readwrite').objectStore('chunks')
    ids.forEach((id) => store.delete(id))
    return true
  } catch (e) { return false }
}

// 列出某会议的孤儿录音会话（带切片数≈秒数），顺带清理过期会话
export async function listSessions(meetingKey) {
  try {
    const db = await openDB()
    const all = await prom(db.transaction('sessions', 'readonly').objectStore('sessions').getAll())
    const out = []
    for (const s of all) {
      if (Date.now() - (s.startedAt || 0) > MAX_AGE_MS) { clearSession(s.key); continue }
      if (meetingKey && s.meetingKey !== meetingKey) continue
      const n = await prom(db.transaction('chunks', 'readonly').objectStore('chunks').index('bySession').count(s.key))
      if (!n) { clearSession(s.key); continue } // 空会话（刚开录就被杀）直接清
      out.push({ key: s.key, meetingKey: s.meetingKey, mimeType: s.mimeType || 'audio/webm', startedAt: s.startedAt, chunkCount: n })
    }
    return out
  } catch (e) { return [] }
}

// 把一个会话的切片按时间序拼回完整音频（与内存版产物等价）
export async function assembleSession(key) {
  try {
    const db = await openDB()
    const sess = await prom(db.transaction('sessions', 'readonly').objectStore('sessions').get(key))
    const rows = await prom(db.transaction('chunks', 'readonly').objectStore('chunks').index('bySession').getAll(key))
    if (!rows || !rows.length) return null
    const type = (sess && sess.mimeType) || 'audio/webm'
    const blob = new Blob(rows.map(r => r.blob), { type })
    if (!blob.size) return null
    return { blob, mimeType: type, durationSec: rows.length } // 每秒一片 → 片数≈时长
  } catch (e) { return null }
}

export default { beginSession, appendChunk, clearSession, listSessions, assembleSession }
