// 录音全生命周期测试矩阵：有状态模拟后端 + 逐场景断言
// 运行前提：h5 目录 npm run dev（:5173）；需要 playwright-core 与 Chromium。
// 用法：node tests/recording-suite.mjs
// 可用环境变量 PW_CHROMIUM 指定 Chromium 路径（默认走 playwright 自带解析）。
import pkg from 'playwright-core'
const { chromium } = pkg
const BASE = process.env.SUITE_BASE || 'http://localhost:5173'

const results = []
function t(name, cond, extra) {
  results.push({ name, pass: !!cond, extra: extra || '' })
  console.log((cond ? 'PASS' : 'FAIL') + ' | ' + name + (extra ? ' | ' + extra : ''))
}

// ── 有状态模拟后端 ──
function mkState(userView, opts = {}) {
  return Object.assign({
    userView,
    recordings: [],            // {id, durationSec, asrStatus, createdAt, uploaderName}
    nextRecId: 100,
    uploadFailRemaining: 0,    // 前 N 次上传返回失败
    statusSeq: ['done'],       // /recording/status 依次返回；末值重复
    statusIdx: 0,
    doneAs: 'done',            // 转写完成后段落状态: done | failed | empty
    transcriptSegments: [{ speaker: 'S1', startMs: 0, endMs: 5000, text: '讨论物业费方案，大家同意上调。' }],
    perRec: {},                // recordingId -> segments (单段转写)
    live: [],                  // recording-live 名单
    counters: { upload: 0, transcribe: 0, status: 0, del: 0 }
  }, opts)
}

function detailOf(st) {
  return {
    id: 5, title: '测试会议', stage: 'ongoing', meetingMethod: 'offline',
    meetingDate: '2026-07-21', meetingTime: '14:00', location: '活动室', userView: st.userView, materials: [],
    record: {
      topics: [
        { id: 11, title: '物业费调整方案', type: 'decision', decisionType: 'simple', voteRequired: true, sortOrder: 1 },
        { id: 12, title: '楼道整治通报', type: 'discussion', voteRequired: false, sortOrder: 2 }
      ],
      attendances: [
        { id: 1, name: '张建国', role: '主任', isSelf: st.userView === 'chair', signedIn: true, attendanceMode: st.selfRemote && st.userView !== 'chair' ? 'x' : 'onsite' },
        { id: 3, name: '王志强', role: '委员', isSelf: st.userView !== 'chair', signedIn: true, attendanceMode: st.selfRemote && st.userView !== 'chair' ? 'remote' : 'onsite' }
      ],
      recordings: st.recordings.slice().reverse() // 后端按 createdAt 倒序返回（新的在前）
    }
  }
}

function makeRoute(st) {
  return async (r) => {
    const u = new URL(r.request().url())
    const p = u.pathname
    const method = r.request().method()
    if (!p.startsWith('/api/')) return r.continue()
    let data = null, code = 200, message = 'ok'
    if (/committees\/5$/.test(p)) data = detailOf(st)
    else if (/^\/api\/committees$/.test(p)) data = [{ id: 5, title: '测试会议', stage: 'ongoing', meetingDate: '2026-07-21', meetingTime: '14:00', location: '活动室', minutesGenerated: false }]
    else if (p.includes('/quick/recording/upload')) {
      st.counters.upload++
      if (st.uploadFailRemaining > 0) { st.uploadFailRemaining--; code = 500; message = '模拟上传失败' }
      else {
        const id = st.nextRecId++
        st.recordings.push({ id, uploaderName: '测试', recordingUrl: '/r.mp3', fileName: 'r.mp3', fileSize: 100, durationSec: 8, asrStatus: null, createdAt: '2026-07-21T14:0' + (st.recordings.length) + ':00' })
        data = { recordingId: id, url: '/r.mp3', fileName: 'r.mp3', fileSize: 100 }
      }
    }
    else if (/recordings\/\d+\/transcribe$/.test(p)) {
      st.counters.transcribe++
      const rid = Number(p.match(/recordings\/(\d+)\/transcribe/)[1])
      const rec = st.recordings.find(x => x.id === rid)
      if (rec) rec.asrStatus = 'processing'
      st.statusIdx = 0
      st._lastRid = rid
      data = { taskId: 't' + rid, meetingId: 5, status: 'processing' }
    }
    else if (p.includes('/quick/recording/status')) {
      st.counters.status++
      const s = st.statusSeq[Math.min(st.statusIdx, st.statusSeq.length - 1)]
      st.statusIdx++
      const rec = st.recordings.find(x => x.id === st._lastRid)
      if (s === 'done' && rec) rec.asrStatus = st.doneAs
      if (s === 'failed' && rec) rec.asrStatus = 'failed'
      data = { taskId: u.searchParams.get('taskId'), meetingId: 5, status: s, message: s === 'failed' ? '模拟识别失败' : null }
    }
    else if (p.endsWith('/quick/transcript')) data = { meetingId: 5, durationSec: 10, segments: st.transcriptSegments }
    else if (p.endsWith('/quick/extract')) data = { presetTopicHits: [], candidateTopics: [] }
    else if (/recordings\/\d+\/transcript$/.test(p)) {
      const rid = Number(p.match(/recordings\/(\d+)\/transcript/)[1])
      data = { meetingId: 5, durationSec: 5, segments: st.perRec[rid] || [] }
    }
    else if (/recordings\/\d+$/.test(p) && method === 'DELETE') {
      st.counters.del++
      const rid = Number(p.match(/recordings\/(\d+)$/)[1])
      st.recordings = st.recordings.filter(x => x.id !== rid)
    }
    else if (p.endsWith('/recording-live')) data = st.live
    else if (p.includes('recording-live/beat')) data = null
    else if (p.includes('notifications')) data = { unread: 0 }
    else if (p.includes('minutes-status')) data = { status: 'none' }
    else if (p.includes('stats')) data = {}
    else if (p.includes('reception')) data = []
    await r.fulfill({ contentType: 'application/json', body: JSON.stringify({ code, message, data }) })
  }
}

let browser
async function newPage(st, roleId) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, permissions: ['microphone'] })
  const page = await ctx.newPage()
  page.on('dialog', async d => { page._dialogs = (page._dialogs || []).concat(d.type()); await d.accept() })
  await page.route('**/*', makeRoute(st))
  await page.addInitScript((rid) => {
    if (!localStorage.getItem('activeRole')) localStorage.setItem('activeRole', JSON.stringify({ id: rid, role: rid === 1 ? '主任' : '委员', realName: rid === 1 ? '张建国' : '王志强', communityId: 1 }))
    navigator.mediaDevices.getUserMedia = async () => {
      const c = new AudioContext(); const o = c.createOscillator(); const d = c.createMediaStreamDestination()
      o.connect(d); o.start(); window.__audioCtx = c
      return d.stream
    }
  }, roleId)
  return { ctx, page }
}
async function enter(page) {
  await page.goto(BASE + '/meeting-live-quick?type=committee&meetingId=5')
  await page.waitForTimeout(1300)
  const e = page.locator('button', { hasText: /进入会议/ }).first()
  if (await e.isVisible().catch(() => false)) { await e.click(); await page.waitForTimeout(1200) }
}
const vis = (page, sel, text) => (text ? page.locator(sel, { hasText: text }) : page.locator(sel)).first().isVisible().catch(() => false)
const click = async (page, text) => { await page.locator('button:visible', { hasText: text }).first().click(); }
const modalText = async (page) => (await page.locator('.ui-modal').allTextContents().catch(() => [])).join(' ').trim()
const bodyText = async (page) => (await page.locator('.live-page').allTextContents().catch(() => [''])).join(' ')

// ════════ S1 主任正常流：录→暂停→继续→暂停→上传→转写(processing→done)→抽取 ════════
async function s1() {
  const st = mkState('chair', { statusSeq: ['processing', 'processing', 'done'] })
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await click(page, '开始录音'); await page.waitForTimeout(2000)
  t('S1 开录后显示暂停按钮', await vis(page, 'button', '暂停录音'))
  await click(page, '暂停录音'); await page.waitForTimeout(600)
  t('S1 暂停后显示 继续/上传', (await vis(page, 'button', '继续录音')) && (await vis(page, 'button', '上传录音')))
  await click(page, '继续录音'); await page.waitForTimeout(1500)
  await click(page, '暂停录音'); await page.waitForTimeout(600)
  await click(page, '上传录音'); await page.waitForTimeout(2500)
  t('S1 上传到达后端一次', st.counters.upload === 1, 'upload=' + st.counters.upload)
  t('S1 自动提交转写', st.counters.transcribe === 1, 'transcribe=' + st.counters.transcribe)
  await page.waitForTimeout(6000) // 轮询 processing×2 → done → finalize
  const body = await bodyText(page)
  t('S1 识别完成(段状态done+停止轮询)', st.recordings[0].asrStatus === 'done' && !body.includes('录音识别中'))
  t('S1 列表出现第1段', body.includes('第 1 段') || body.includes('已录 1 段'))
  await click(page, '结束现场会议'); await page.waitForTimeout(700)
  await click(page, '结束会议'); await page.waitForTimeout(1200)
  t('S1 会后整理页可生成纪要', await vis(page, '.end-review-primary', '生成纪要并完成会议'))
  await ctx.close()
}

// ════════ S2 上传失败→留在已停未传态→重试上传成功 ════════
async function s2() {
  const st = mkState('chair', { uploadFailRemaining: 1, statusSeq: ['done'] })
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await click(page, '开始录音'); await page.waitForTimeout(2000)
  await click(page, '暂停录音'); await page.waitForTimeout(500)
  await click(page, '上传录音'); await page.waitForTimeout(2500)
  t('S2 首次上传失败常驻提示', (await bodyText(page)).includes('上传失败'), (await bodyText(page)).match(/上传失败[^ ]{0,20}/) ? '' : '无提示')
  t('S2 失败后仍有上传按钮(已停未传态)', await vis(page, 'button', '上传录音'))
  await click(page, '上传录音'); await page.waitForTimeout(2500)
  t('S2 重试上传成功', st.counters.upload === 2 && st.recordings.length === 1, 'upload=' + st.counters.upload)
  await ctx.close()
}

// ════════ S3 识别失败→「重新识别录音」按钮→重试成功 ════════
async function s3() {
  const st = mkState('chair', { statusSeq: ['failed'] })
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await click(page, '开始录音'); await page.waitForTimeout(2000)
  await click(page, '暂停录音'); await page.waitForTimeout(500)
  await click(page, '上传录音'); await page.waitForTimeout(5000)
  const body1 = await bodyText(page)
  t('S3 识别失败提示', body1.includes('识别失败') || body1.includes('转写') || body1.includes('⚠'), body1.slice(0, 60))
  t('S3 失败后出现重新识别按钮', await vis(page, 'button', '重新识别录音'))
  st.statusSeq = ['done']; st.statusIdx = 0
  await click(page, '重新识别录音'); await page.waitForTimeout(5000)
  t('S3 重试后识别成功', st.recordings[0] && st.recordings[0].asrStatus === 'done', 'asr=' + (st.recordings[0] || {}).asrStatus)
  t('S3 成功后按钮消失', !(await vis(page, 'button', '重新识别录音')))
  await ctx.close()
}

// ════════ S4 静音录音：识别 done 但无内容 → 明确弹窗，不出重试按钮 ════════
async function s4() {
  const st = mkState('chair', { statusSeq: ['done'], doneAs: 'empty', transcriptSegments: [] })
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await click(page, '开始录音'); await page.waitForTimeout(2000)
  await click(page, '暂停录音'); await page.waitForTimeout(500)
  await click(page, '上传录音'); await page.waitForTimeout(5000)
  const m = await modalText(page)
  t('S4 静音弹窗提示', m.includes('没有识别到语音') || m.includes('没有听到'), m.slice(0, 40))
  const know = page.locator('button:visible', { hasText: '知道了' }).first()
  if (await know.count()) await know.click()
  await page.waitForTimeout(500)
  t('S4 行内提示没识别到说话声', (await bodyText(page)).includes('没识别到说话声'))
  t('S4 静音不出重新识别按钮', !(await vis(page, 'button', '重新识别录音')))
  await ctx.close()
}

// ════════ S5 委员上传：不自动识别 ════════
async function s5() {
  const st = mkState('member', { statusSeq: ['done'] })
  const { ctx, page } = await newPage(st, 3)
  await enter(page)
  await click(page, '开始录音'); await page.waitForTimeout(2000)
  await click(page, '暂停录音'); await page.waitForTimeout(500)
  await click(page, '上传录音'); await page.waitForTimeout(2500)
  t('S5 委员上传成功', st.counters.upload === 1 && st.recordings.length === 1)
  t('S5 委员上传后不自动转写', st.counters.transcribe === 0, 'transcribe=' + st.counters.transcribe)
  await ctx.close()
}

// ════════ S6 主任进入：自动补识别委员传的待识别段 ════════
async function s6() {
  const st = mkState('chair', { statusSeq: ['done'] })
  st.recordings = [
    { id: 31, uploaderName: '王志强', recordingUrl: '/a.mp3', fileName: 'a', fileSize: 1, durationSec: 40, asrStatus: null, createdAt: '2026-07-21T14:01:00' },
    { id: 32, uploaderName: '王志强', recordingUrl: '/b.mp3', fileName: 'b', fileSize: 1, durationSec: 80, asrStatus: null, createdAt: '2026-07-21T14:02:00' }
  ]
  st.nextRecId = 33
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await page.waitForTimeout(6000)
  t('S6 主任进入自动识别两段', st.counters.transcribe === 2, 'transcribe=' + st.counters.transcribe)
  t('S6 无识别按钮出现', !(await vis(page, 'button', '识别已上传录音')))
  await ctx.close()
}

// ════════ S7 主任录音中：自动补识别让道(不抢跑)；上传后统一识别 ════════
async function s7() {
  const st = mkState('chair', { statusSeq: ['done'] })
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await click(page, '开始录音'); await page.waitForTimeout(1500)
  // 别人此刻传了一段
  st.recordings.push({ id: 41, uploaderName: '王志强', recordingUrl: '/c.mp3', fileName: 'c', fileSize: 1, durationSec: 30, asrStatus: null, createdAt: '2026-07-21T14:01:00' })
  await page.waitForTimeout(13500) // 等 12s 轻量刷新一轮
  t('S7 录音中不自动识别别人段', st.counters.transcribe === 0, 'transcribe=' + st.counters.transcribe)
  await click(page, '暂停录音'); await page.waitForTimeout(500)
  await click(page, '上传录音'); await page.waitForTimeout(9000)
  t('S7 上传后两段统一识别', st.counters.transcribe === 2, 'transcribe=' + st.counters.transcribe)
  await ctx.close()
}

// ════════ S8 他人正在录：开录前提示 ════════
async function s8() {
  const st = mkState('chair', { live: [{ roleId: 2, name: '李秀英' }] })
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await click(page, '开始录音'); await page.waitForTimeout(900)
  const m = await modalText(page)
  t('S8 弹「李秀英 正在录音」', m.includes('李秀英') && m.includes('正在录音'), m.slice(0, 40))
  t('S8 确认前未开录', !(await vis(page, 'button', '暂停录音')))
  await click(page, '仍要录音'); await page.waitForTimeout(1500)
  t('S8 确认后正常开录', await vis(page, 'button', '暂停录音'))
  await ctx.close()
}

// ════════ S9 结束会议状态1：录音中·无已上传 → 确认=放弃并进会后整理 ════════
async function s9() {
  const st = mkState('chair')
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await click(page, '开始录音'); await page.waitForTimeout(2000)
  await click(page, '结束现场会议'); await page.waitForTimeout(800)
  const m = await modalText(page)
  t('S9 状态1文案(放弃这段)', m.includes('放弃这段录音'), m.slice(0, 50))
  await click(page, '确认结束'); await page.waitForTimeout(1500)
  t('S9 确认后进入会后整理', await vis(page, '.end-review-primary'))
  t('S9 录音已停止', !(await vis(page, '.top-rec-status')))
  await ctx.close()
}

// ════════ S10 结束会议状态4 + 他人在录提醒 ════════
async function s10() {
  const st = mkState('chair', { live: [{ roleId: 2, name: '李秀英' }] })
  st.recordings = [{ id: 51, uploaderName: '张建国', recordingUrl: '/a.mp3', fileName: 'a', fileSize: 1, durationSec: 60, asrStatus: 'done', createdAt: '2026-07-21T14:01:00' }]
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await page.waitForTimeout(800)
  await click(page, '结束现场会议'); await page.waitForTimeout(800)
  const m = await modalText(page)
  t('S10 状态4普通确认+他人在录提醒', m.includes('李秀英') && m.includes('会后处理'), m.slice(0, 60))
  await ctx.close()
}

// ════════ S11 处理议题：委员直接进；主任状态3弹；主任录音中直接进 ════════
async function s11() {
  // 委员
  let st = mkState('member')
  let h = await newPage(st, 3)
  await enter(h.page)
  await h.page.locator('button.meeting-stage-next').click(); await h.page.waitForTimeout(800)
  t('S11 委员无录音点处理议题直接进', (await vis(h.page, 'text=议题表决与意见确认')) && !(await modalText(h.page)))
  await h.ctx.close()
  // 主任状态3
  st = mkState('chair')
  h = await newPage(st, 1)
  await enter(h.page)
  await h.page.locator('button.meeting-stage-next').click(); await h.page.waitForTimeout(800)
  t('S11 主任状态3弹确认', (await modalText(h.page)).includes('还没有开始录音'))
  await click(h.page, '返回录音'); await h.page.waitForTimeout(400)
  t('S11 返回录音留在原页', !(await vis(h.page, 'text=议题表决与意见确认')))
  // 主任录音中（状态1）
  await click(h.page, '开始录音'); await h.page.waitForTimeout(1800)
  await h.page.locator('button.meeting-stage-next').click(); await h.page.waitForTimeout(800)
  t('S11 主任录音中点处理议题直接进', await vis(h.page, 'text=议题表决与意见确认'))
  t('S11 进表决页后录音仍在进行', await vis(h.page, '.top-rec-status'))
  await h.ctx.close()
}

// ════════ S12 两段转写高度相似 → 提醒 ════════
async function s12() {
  const longText = '今天我们讨论物业费调整方案，经过充分讨论大家一致同意按每平米上调百分之十执行，另外楼道堆物问题物业将在本月内组织集中清理，请各位委员配合宣传告知业主。'
  const st = mkState('chair', { statusSeq: ['done'] })
  st.recordings = [
    { id: 61, uploaderName: '张建国', recordingUrl: '/a.mp3', fileName: 'a', fileSize: 1, durationSec: 60, asrStatus: null, createdAt: '2026-07-21T14:01:00' },
    { id: 62, uploaderName: '李秀英', recordingUrl: '/b.mp3', fileName: 'b', fileSize: 1, durationSec: 62, asrStatus: null, createdAt: '2026-07-21T14:02:00' }
  ]
  st.perRec = {
    61: [{ speaker: 'S1', startMs: 0, endMs: 5000, text: longText }],
    62: [{ speaker: 'S1', startMs: 0, endMs: 5000, text: longText + '好的就这样。' }]
  }
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await page.waitForTimeout(8000) // 自动识别两段 → finalize → 相似检测
  const m = await modalText(page)
  t('S12 相似提醒弹出', m.includes('高度相似'), m.slice(0, 50))
  await ctx.close()
}

// ════════ S13 段序号按时间正序 ════════
async function s13() {
  const st = mkState('chair')
  st.recordings = [
    { id: 71, uploaderName: '张建国', recordingUrl: '/a.mp3', fileName: 'a', fileSize: 1, durationSec: 40, asrStatus: 'done', createdAt: '2026-07-21T14:01:00' },
    { id: 72, uploaderName: '张建国', recordingUrl: '/b.mp3', fileName: 'b', fileSize: 1, durationSec: 80, asrStatus: 'done', createdAt: '2026-07-21T14:05:00' }
  ]
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await page.waitForTimeout(800)
  const rows = await page.locator('.rec-summary-row').allTextContents()
  t('S13 第1段=最早(0:40)', rows[0] && rows[0].includes('第 1 段') && rows[0].includes('0:40'), JSON.stringify(rows))
  await ctx.close()
}

// ════════ S14 详情：查看这段转写(有内容/空) ════════
async function s14() {
  const st = mkState('chair')
  st.recordings = [
    { id: 81, uploaderName: '张建国', recordingUrl: '/a.mp3', fileName: 'a', fileSize: 1, durationSec: 40, asrStatus: 'done', createdAt: '2026-07-21T14:01:00' },
    { id: 82, uploaderName: '张建国', recordingUrl: '/b.mp3', fileName: 'b', fileSize: 1, durationSec: 30, asrStatus: 'done', createdAt: '2026-07-21T14:03:00' }
  ]
  st.perRec = { 81: [{ speaker: 'S1', startMs: 0, endMs: 3000, text: '第一段的内容。' }], 82: [] }
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await page.waitForTimeout(800)
  await page.locator('.rec-summary-more').first().click(); await page.waitForTimeout(500)
  t('S14 详情按钮=转写主+播放次(无删除)', (await page.locator('.recording-detail-actions button:visible').allTextContents()).join('|') === '查看这段转写|播放录音')
  await click(page, '查看这段转写'); await page.waitForTimeout(1200)
  t('S14 第1段转写有内容', (await page.locator('.qk-transcript-scroll').textContent()).includes('第一段的内容'))
  await page.locator('.qk-sheet-close').first().click(); await page.waitForTimeout(400)
  await page.locator('.rec-summary-more').nth(1).click(); await page.waitForTimeout(500)
  await click(page, '查看这段转写'); await page.waitForTimeout(1200)
  t('S14 第2段空转写给可读提示', (await page.locator('.qk-transcript-scroll').textContent()).includes('没有识别到内容'))
  await ctx.close()
}

// ════════ S15 端到端恢复：录音中硬跳首页→点卡片重进→孤儿恢复弹窗→恢复上传 ════════
async function s15() {
  const st = mkState('chair')
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await click(page, '开始录音'); await page.waitForTimeout(9000) // 录 ≥8 秒(切片≥5片才有恢复价值)
  // 软导航回首页（录音仍活），再点卡片=硬跳（SPA 重启，内存录音死，切片在 IndexedDB）
  await page.locator('button.nav-home', { hasText: '首页' }).first().click(); await page.waitForTimeout(1500)
  await page.locator('.big-btn').first().click(); await page.waitForTimeout(3000)
  const m = await modalText(page)
  t('S15 硬跳重进弹孤儿恢复', m.includes('发现未上传的录音'), m.slice(0, 50))
  await click(page, '恢复并上传'); await page.waitForTimeout(3500)
  t('S15 恢复上传成功', st.counters.upload >= 1 && st.recordings.length >= 1, 'upload=' + st.counters.upload)
  await ctx.close()
}

// ════════ S16 角色隔离：主任的页面状态不串给委员 ════════
async function s16() {
  const st = mkState('chair')
  st.recordings = [{ id: 91, uploaderName: '张建国', recordingUrl: '/a.mp3', fileName: 'a', fileSize: 1, durationSec: 60, asrStatus: 'done', createdAt: '2026-07-21T14:01:00' }]
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await page.locator('button.meeting-stage-next').click(); await page.waitForTimeout(800) // 主任进表决页(状态持久化)
  t('S16 主任已在表决页', await vis(page, 'text=议题表决与意见确认'))
  // 切成委员 + 硬重载（同一浏览器上下文，localStorage 共享）
  st.userView = 'member'
  await page.evaluate(() => localStorage.setItem('activeRole', JSON.stringify({ id: 3, role: '委员', realName: '王志强', communityId: 1 })))
  await page.goto(BASE + '/meeting-live-quick?type=committee&meetingId=5'); await page.waitForTimeout(1500)
  const e = page.locator('button', { hasText: /进入会议/ }).first()
  if (await e.isVisible().catch(() => false)) { await e.click(); await page.waitForTimeout(1000) }
  t('S16 委员进来不在表决页(状态未串)', !(await vis(page, 'text=议题表决与意见确认')))
  await ctx.close()
}

// ════════ S17 线上参会：无录音控件 ════════
async function s17() {
  const st = mkState('member', { selfRemote: true })
  const { ctx, page } = await newPage(st, 3)
  await enter(page)
  await page.waitForTimeout(600)
  t('S17 线上参会无开始录音按钮', !(await vis(page, 'button', '开始录音')))
  t('S17 显示线上参会提示', (await bodyText(page)).includes('线上方式参会'))
  await ctx.close()
}

// ════════ S18 上一段识别中：新段上传被排队提示，不并发 ════════
async function s18() {
  const st = mkState('chair', { statusSeq: ['processing', 'processing', 'processing', 'processing', 'done'] })
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await click(page, '开始录音'); await page.waitForTimeout(2000)
  await click(page, '暂停录音'); await page.waitForTimeout(400)
  await click(page, '上传录音'); await page.waitForTimeout(2500) // 上传完，进入轮询(processing)
  // 识别中再录一段并尝试上传（已传过一段 → 头部按钮显示「继续录音」）
  const startBtn = page.locator('button:visible', { hasText: /继续录音|开始录音/ }).first()
  await startBtn.click(); await page.waitForTimeout(600)
  const m1 = await modalText(page)
  if (m1.includes('还没上传')) { await click(page, '丢弃并重新录'); await page.waitForTimeout(400) }
  await page.waitForTimeout(1500)
  await click(page, '暂停录音'); await page.waitForTimeout(400)
  const up2 = page.locator('button:visible', { hasText: '上传录音' }).first()
  t('S18 识别中上传按钮禁用', await up2.isDisabled().catch(() => false))
  t('S18 识别中状态条在', (await bodyText(page)).includes('录音识别中'))
  t('S18 未发生并发上传', st.counters.upload === 1, 'upload=' + st.counters.upload)
  // 等第一段识别完(processing×4→done ≈8s)，再传第二段
  await page.waitForTimeout(8000)
  await click(page, '上传录音'); await page.waitForTimeout(3000)
  t('S18 识别完成后第二段可上传', st.counters.upload === 2, 'upload=' + st.counters.upload)
  let ok18 = false
  for (let i = 0; i < 16; i++) { // 最多等 16s：第二段 submit 延迟 + 5 次轮询
    if (st.counters.transcribe === 2 && st.recordings.every(r => r.asrStatus === 'done')) { ok18 = true; break }
    await page.waitForTimeout(1000)
  }
  t('S18 第二段也完成识别', ok18, 'transcribe=' + st.counters.transcribe + ' statuses=' + st.recordings.map(r => r.asrStatus).join(','))
  await ctx.close()
}

// ════════ S19 删除录音 ════════
async function s19() {
  const st = mkState('chair')
  st.recordings = [
    { id: 95, uploaderName: '张建国', recordingUrl: '/a.mp3', fileName: 'a', fileSize: 1, durationSec: 40, asrStatus: 'done', createdAt: '2026-07-21T14:01:00' },
    { id: 96, uploaderName: '张建国', recordingUrl: '/b.mp3', fileName: 'b', fileSize: 1, durationSec: 80, asrStatus: 'done', createdAt: '2026-07-21T14:02:00' }
  ]
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await page.waitForTimeout(800)
  await page.locator('.rec-summary-del').first().click(); await page.waitForTimeout(600)
  t('S19 删除有确认弹窗', (await modalText(page)).includes('删除'))
  await click(page, '删除'); await page.waitForTimeout(1500)
  t('S19 删除到达后端且列表变1段', st.counters.del === 1 && (await bodyText(page)).includes('已录 1 段'))
  await ctx.close()
}

// ════════ S20 守卫：录音中本页刷新有拦截；离开本页后硬跳无拦截 ════════
async function s20() {
  const st = mkState('chair')
  const { ctx, page } = await newPage(st, 1)
  await enter(page)
  await click(page, '开始录音'); await page.waitForTimeout(2000)
  page._dialogs = []
  await page.reload().catch(() => {}) // beforeunload → dialog(已自动 accept)
  await page.waitForTimeout(2000)
  t('S20 录音中刷新触发离开确认', (page._dialogs || []).includes('beforeunload'), JSON.stringify(page._dialogs))
  // 重进（可能弹孤儿恢复，选稍后处理），开录，去首页，硬跳 → 应无 dialog
  const later = page.locator('button:visible', { hasText: '稍后处理' }).first()
  if (await later.count()) { await later.click(); await page.waitForTimeout(400) }
  const e = page.locator('button', { hasText: /进入会议/ }).first()
  if (await e.isVisible().catch(() => false)) { await e.click(); await page.waitForTimeout(1000) }
  await click(page, '开始录音'); await page.waitForTimeout(1500)
  const m1 = await modalText(page)
  if (m1.includes('还没上传')) { await click(page, '丢弃并重新录'); await page.waitForTimeout(800) }
  await page.locator('button.nav-home', { hasText: '首页' }).first().click(); await page.waitForTimeout(1200)
  page._dialogs = []
  await page.goto(BASE + '/main'); await page.waitForTimeout(1200)
  t('S20 离开本页后硬跳不再拦截', !(page._dialogs || []).length, JSON.stringify(page._dialogs))
  await ctx.close()
}

// ── 运行 ──
browser = await chromium.launch(process.env.PW_CHROMIUM ? { executablePath: process.env.PW_CHROMIUM } : {})
const suites = [s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19, s20]
for (const s of suites) {
  try { await s() } catch (e) { t(s.name + ' 场景执行异常', false, (e.message || '').split('\n')[0].slice(0, 120)) }
}
await browser.close()
const fails = results.filter(r => !r.pass)
console.log('\n===== 汇总: ' + (results.length - fails.length) + '/' + results.length + ' 通过 =====')
fails.forEach(f => console.log('FAILED: ' + f.name + ' | ' + f.extra))
