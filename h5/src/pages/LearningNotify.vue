<template>
  <div class="page learning-notify">
    <PageNav title="学习通知" />

    <main class="notify-body" v-if="item">
      <!-- 通知内容预览：与会议通知同款（首行缩进、落款靠右） -->
      <section class="notice-card">
        <div class="nc-title">{{ item.title }}</div>
        <div class="nc-para">各位委员：现定于 {{ fmtCnDate(item.date) }} {{ fmtHm(item.time) }} 在{{ item.location || '业委会办公室' }}组织内部学习，主要内容：{{ item.description || item.title }}，请准时参加。</div>
        <div class="nc-sign">业主委员会</div>
      </section>

      <!-- 计划参加人员：业委会成员，默认全选，展开可单独勾选；通知时选定名单一并落库（0728 用户定：选择从发起页移到此处） -->
      <section class="member-section" v-if="!item.notified">
        <div class="ms-label">计划参加人员</div>
        <div class="member-card">
          <div class="member-head" @click="membersOpen = !membersOpen">
            <span class="member-title">业委会成员</span>
            <div class="member-right">
              <div class="mc-all" @click.stop="toggleAll">
                <div class="mc-check" :class="{ on: allChecked }">{{ allChecked ? '✓' : '' }}</div>
                <span class="mc-all-label">全选</span>
                <span class="mc-count">已选 {{ selectedCount }}/{{ members.length }} 人</span>
              </div>
              <span class="member-toggle" :class="{ open: membersOpen }">{{ membersOpen ? '收起' : '展开' }}<span class="mt-arr"></span></span>
            </div>
          </div>
          <div v-if="membersOpen" class="mc-list">
            <div v-for="m in members" :key="m.userRoleId" class="mc-item" @click="toggleMember(m.userRoleId)">
              <div class="mc-check" :class="{ on: m.checked }">{{ m.checked ? '✓' : '' }}</div>
              <div class="mc-person">
                <span class="mc-name">{{ m.name }}</span>
                <span v-if="m.role" class="mc-role">{{ m.role }}</span>
              </div>
            </div>
            <div v-if="!members.length" class="mc-empty">暂无业委会成员</div>
          </div>
        </div>
      </section>

      <!-- 已通知：提示 + 通知记录 + 进入详情（结束后在详情登记学习情况） -->
      <template v-if="item.notified">
        <div class="done-tip">
          <span class="dt-ic">✓</span>
          <span class="dt-text">{{ notifiedText }}</span>
        </div>
        <section class="rec-card">
          <div class="rec-title">通知记录</div>
          <div class="rec-row">
            <span class="rec-ic">✓</span>
            <span class="rec-text">{{ noticeRecordText }}</span>
          </div>
        </section>
      </template>
    </main>

    <!-- 底部主操作：未通知=二选一发通知；已通知=进入详情 + 可继续通知 -->
    <div class="notify-footer" v-if="item">
      <template v-if="!item.notified">
        <button class="nf-btn nf-app" :disabled="busy" @click="notifyApp">App内通知</button>
        <button class="nf-btn nf-wechat" :disabled="busy" @click="notifyWechat">去微信通知</button>
      </template>
      <div v-else class="nf-col">
        <button class="nf-detail-btn" @click="goDetail">进入详情（结束后登记学习情况）</button>
        <div class="nf-again">
          <button class="nf-again-btn" :disabled="busy" @click="notifyApp">再次 App 通知</button>
          <button class="nf-again-btn" :disabled="busy" @click="notifyWechat">再次微信通知</button>
        </div>
      </div>
    </div>

    <div v-if="!item" class="empty-state"><span>加载中…</span></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import { toast } from '@/utils/ui'

const route = useRoute()
const id = Number(route.query.id)
const item = ref(null)
const busy = ref(false)

const planNames = computed(() => String((item.value && item.value.attendees) || '').split(/[,，、\s]+/).filter(Boolean))
const notifiedText = computed(() => planNames.value.length
  ? ('已通知 ' + planNames.value.length + ' 位委员，学习结束后可在详情登记参加情况')
  : '通知已发出，学习结束后可在详情登记参加情况')
// 通知记录：已由 谁 通过 App/微信 通知 · 时间
const noticeRecordText = computed(() => {
  const it = item.value || {}
  const via = it.notifiedChannel === 'wechat' ? '通过微信通知' : '通过App内通知'
  const who = it.notifiedByName ? ('已由 ' + it.notifiedByName + ' ' + via) : ('已' + via)
  return who + (it.notifiedAt ? ' · ' + fmtRecordTime(it.notifiedAt) : '')
})
function fmtRecordTime(s) {
  const m = String(s || '').match(/(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/)
  return m ? (Number(m[2]) + '月' + Number(m[3]) + '日 ' + m[4] + ':' + m[5]) : String(s || '')
}

// 参加人员选择（业委会成员，默认全选，可展开单独勾选）——通知时随通知落库
const members = ref([])          // [{ userRoleId, name, role, checked }]
const membersOpen = ref(false)
const selectedCount = computed(() => members.value.filter(m => m.checked).length)
const allChecked = computed(() => members.value.length > 0 && members.value.every(m => m.checked))
function toggleAll() { const t = !allChecked.value; members.value.forEach(m => { m.checked = t }) }
function toggleMember(mid) { const m = members.value.find(x => x.userRoleId === mid); if (m) m.checked = !m.checked }
function selectedNames() { return members.value.filter(m => m.checked).map(m => m.name) }
// 通知名单：未通知时用勾选结果；已通知后（成员选择器已隐藏）继续通知则沿用当前名单
function namesToNotify() {
  const sel = selectedNames()
  return sel.length ? sel : planNames.value
}
async function loadMembers() {
  try {
    const list = await api.committeeMembers()
    // 已有计划名单（如返回本页）则按其预勾选，否则默认全选
    const planned = new Set(planNames.value)
    members.value = (list || [])
      .map(m => ({ userRoleId: Number(m.userRoleId), name: m.name || '委员', role: m.role || '', checked: planned.size ? planned.has(m.name || '委员') : true }))
      .filter(m => m.userRoleId)
  } catch (e) { members.value = [] }
}

function load() {
  api.learningList('internal', null).then((list) => {
    const found = (list || []).find((i) => i.id === id)
    if (found) { item.value = found; if (!item.value.notified) loadMembers() }
    else toast({ title: '学习记录不存在', icon: 'none' })
  }).catch(() => toast({ title: '加载失败', icon: 'none' }))
}

async function notifyApp() {
  if (busy.value) return
  const names = namesToNotify()
  if (!names.length) { toast({ title: '请至少选择一位参加人员', icon: 'none' }); return }
  busy.value = true
  try {
    await api.learningNotifyAll(id, names, 'app')
    toast({ title: item.value && item.value.notified ? '已再次通知' : '已在 App 内通知', icon: 'success' })
    load()
  } catch (e) {
    toast({ title: (e && e.message) || '通知失败', icon: 'none' })
  } finally { busy.value = false }
}

async function notifyWechat() {
  if (busy.value) return
  const names = namesToNotify()
  if (!names.length) { toast({ title: '请至少选择一位参加人员', icon: 'none' }); return }
  busy.value = true
  try {
    await copyText(noticePlainText())
    await api.learningNotifyAll(id, names, 'wechat')
    toast({ title: '通知已复制，去微信群粘贴发送', icon: 'none' })
    load()
  } catch (e) {
    toast({ title: (e && e.message) || '操作失败', icon: 'none' })
  } finally { busy.value = false }
}

function goDetail() {
  window.location.replace('/learning-detail?id=' + id)
}

// 复制到剪贴板（带非 HTTPS/旧 WebView 降级），与会议通知一致
function copyText(text) {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    return navigator.clipboard.writeText(text)
  }
  return new Promise((resolve) => {
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta); ta.select()
    try { document.execCommand('copy') } catch (e) { /* 忽略 */ }
    document.body.removeChild(ta); resolve()
  })
}

function noticePlainText() {
  const it = item.value || {}
  return '各位委员：现定于 ' + fmtCnDate(it.date) + ' ' + fmtHm(it.time) + ' 在' + (it.location || '业委会办公室')
    + '组织内部学习，主要内容：' + (it.description || it.title) + '，请准时参加。\n——业主委员会'
}

// 2026-08-06 → 2026年8月6日
function fmtCnDate(s) {
  if (!s) return ''
  const m = String(s).match(/(\d{4})-(\d{2})-(\d{2})/)
  return m ? (Number(m[1]) + '年' + Number(m[2]) + '月' + Number(m[3]) + '日') : String(s)
}
function fmtHm(s) { return String(s || '').slice(0, 5) }

onMounted(load)
</script>

<style scoped>
:deep(.page-nav) { background: #2a6b73; }  /* 学习模块页头（规范三色制） */
.learning-notify { min-height: 100vh; background: #f5f5f7; }
.notify-body { padding: 24rpx 28rpx calc(200rpx + env(safe-area-inset-bottom)); }

.notice-card { background: #fff; border-radius: 22rpx; padding: 30rpx 28rpx; box-shadow: 0 6rpx 18rpx rgba(31, 45, 61, .06); }
.nc-title { font-size: 34rpx; font-weight: 700; color: #1f2329; margin-bottom: 16rpx; }
.nc-para { font-size: 30rpx; line-height: 1.75; color: #2b323b; text-indent: 2em; }
.nc-sign { margin-top: 16rpx; text-align: right; font-size: 29rpx; color: #33373d; }

/* 参加人员选择卡（业委会成员，默认全选、可展开勾选） */
.member-section { margin-top: 20rpx; }
.ms-label { margin: 0 4rpx 12rpx; font-size: 28rpx; color: #4a5560; font-weight: 600; }
.member-card { border: 2rpx solid #E4E8EC; border-radius: 16rpx; background: #fff; overflow: hidden; box-shadow: 0 6rpx 18rpx rgba(31, 45, 61, .06); }
.member-head { display: flex; align-items: center; justify-content: space-between; gap: 10rpx; padding: 0 18rpx; min-height: 88rpx; box-sizing: border-box; }
.member-head:active { background: #FAFAFA; }
.member-title { font-size: 29rpx; color: #1f2329; font-weight: 700; }
.member-right { flex-shrink: 0; display: flex; align-items: center; gap: 14rpx; }
.mc-all { display: flex; align-items: center; gap: 8rpx; padding: 0 6rpx; }
.mc-all-label { font-size: 27rpx; color: #A85800; font-weight: 700; white-space: nowrap; }
.mc-count { font-size: 25rpx; color: #8A9099; white-space: nowrap; }
.member-toggle { flex-shrink: 0; display: inline-flex; align-items: center; gap: 10rpx; padding: 8rpx 18rpx; border-radius: 999rpx; background: var(--c-primary-soft); color: var(--c-primary-dark); font-size: 25rpx; font-weight: 700; }
.mt-arr { display: inline-block; width: 12rpx; height: 12rpx; border-right: 3rpx solid currentColor; border-bottom: 3rpx solid currentColor; transform: rotate(45deg); position: relative; top: -2rpx; transition: transform .18s ease, top .18s ease; }
.member-toggle.open .mt-arr { transform: rotate(-135deg); top: 2rpx; }
.mc-list { border-top: 2rpx solid #F0F2F4; }
.mc-item { display: flex; align-items: center; gap: 14rpx; padding: 16rpx 20rpx; border-bottom: 1px solid #f0f0f2; }
.mc-item:last-child { border-bottom: none; }
.mc-item:active { background: #fafafa; }
.mc-check { flex-shrink: 0; width: 38rpx; height: 38rpx; border-radius: 50%; border: 3rpx solid #cfd4da; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 24rpx; font-weight: 700; box-sizing: border-box; }
.mc-check.on { background: var(--c-primary); border-color: var(--c-primary); }
.mc-person { display: flex; flex-direction: column; gap: 2rpx; }
.mc-name { font-size: 28rpx; color: #1f2329; font-weight: 600; }
.mc-role { font-size: 20rpx; color: #9aa0a6; }
.mc-empty { text-align: center; color: #9aa0a6; font-size: 24rpx; padding: 28rpx 0; }

.done-tip { margin-top: 20rpx; display: flex; align-items: center; gap: 12rpx; padding: 20rpx 24rpx; background: #E8F5EE; border-radius: 18rpx; }
.dt-ic { color: #2E9E5B; font-weight: 700; font-size: 30rpx; }
.dt-text { font-size: 28rpx; color: #2E7D46; line-height: 1.5; font-weight: 600; }

/* 通知记录 */
.rec-card { margin-top: 20rpx; background: #fff; border-radius: 18rpx; padding: 22rpx 26rpx; box-shadow: 0 6rpx 18rpx rgba(31, 45, 61, .06); }
.rec-title { font-size: 30rpx; font-weight: 700; color: #1f2329; margin-bottom: 14rpx; }
.rec-row { display: flex; align-items: center; gap: 12rpx; }
.rec-ic { color: #2E9E5B; font-weight: 700; font-size: 27rpx; }
.rec-text { font-size: 27rpx; color: #2E7D46; font-weight: 600; line-height: 1.5; }

.notify-footer { position: fixed; left: 0; right: 0; bottom: 0; z-index: 30; display: flex; gap: 24rpx; padding: 18rpx 28rpx calc(20rpx + env(safe-area-inset-bottom)); background: #fff; box-shadow: 0 -6rpx 20rpx rgba(0,0,0,.06); }
.nf-btn { flex: 1; height: 92rpx; border: 0; border-radius: 20rpx; font-size: 32rpx; font-weight: 700; }
.nf-btn:disabled { opacity: .6; }
.nf-app { background: #fff; border: 2rpx solid var(--c-primary); color: var(--c-primary-dark); }
.nf-wechat { background: var(--c-primary); color: #fff; }
/* 已通知：进入详情主按钮 + 下方「再次通知」一行 */
.nf-col { flex: 1; display: flex; flex-direction: column; gap: 14rpx; }
.nf-detail-btn { width: 100%; height: 92rpx; border: 0; border-radius: 20rpx; background: var(--c-primary); color: #fff; font-size: 32rpx; font-weight: 700; }
.nf-again { display: flex; gap: 20rpx; }
.nf-again-btn { flex: 1; height: 72rpx; border: 2rpx solid #C9D0D6; border-radius: 16rpx; background: #fff; color: #5B6570; font-size: 27rpx; font-weight: 600; }
.nf-again-btn:disabled { opacity: .6; }

.empty-state { padding: 100rpx 0; text-align: center; color: #8A94A6; font-size: 30rpx; }
</style>
