<template>
  <div class="page learning-register">
    <PageNav :title="'登记' + catNoun + '结果'" />

    <main v-if="item" class="reg-body">
      <div class="reg-tip">请确认以下信息无误，确认后本次{{ catNoun }}归档留存</div>

      <section class="card">
        <div class="row"><span class="k">{{ catNoun }}主题</span><span class="v strong">{{ item.title }}</span></div>
        <div class="row"><span class="k">时间</span><span class="v">{{ item.date }} {{ fmtTime(item.time) }}</span></div>
        <div class="row"><span class="k">地点</span><span class="v">{{ item.location || '未填写' }}</span></div>
        <div class="row"><span class="k">组织单位</span><span class="v">{{ organizer }}</span></div>
        <div class="row"><span class="k">参加人员</span><span class="v">{{ participants }}</span></div>
        <div class="row col"><span class="k">{{ catNoun }}内容</span><span class="v">{{ item.description || '未填写' }}</span></div>
      </section>

      <section class="card">
        <div class="card-title">{{ catNoun }}材料（{{ evidences.length }}）</div>
        <div v-if="evidences.length" class="ev-list">
          <div v-for="ev in evidences" :key="ev.id" class="ev-item">
            <img v-if="isImage(ev)" class="ev-thumb" :src="ev.fileUrl" @click="openFile(ev.fileUrl)" />
            <span v-else class="ev-name ev-link" @click="openFile(ev.fileUrl)">📄 {{ ev.fileName }}</span>
            <span v-if="isImage(ev)" class="ev-name">{{ ev.fileName }}</span>
          </div>
        </div>
        <span v-else class="ev-empty">暂无材料（如需附课件/照片，可返回详情页上传后再确认）</span>
      </section>
    </main>

    <div class="reg-foot" v-if="item">
      <button class="btn-finish" type="button" :disabled="saving" @click="confirm">确认（留档）</button>
    </div>

    <div v-else class="empty-state"><span>加载中…</span></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import perm from '@/utils/perm'
import { toast, showModal } from '@/utils/ui'
import { redirectTo } from '@/utils/navigate'
import { getStorage } from '@/utils/storage'

const route = useRoute()
const id = Number(route.query.id)
const item = ref(null)
const evidences = ref([])
const memberNames = ref([])
const saving = ref(false)

const catNoun = computed(() => (item.value && item.value.category === 'external') ? '培训' : '学习')
const plannedNames = computed(() => String((item.value && item.value.attendees) || '').split(/[,，、\s]+/).filter(Boolean))
const organizer = computed(() => {
  if (item.value && item.value.category === 'external') return (item.value && item.value.trainer) || '未填写'
  const r = getStorage('activeRole', null) || {}
  return r.communityName ? (r.communityName + '业委会') : '业主委员会'
})
const participants = computed(() => {
  const names = plannedNames.value
  if (!names.length) return '未填写'
  const all = memberNames.value
  if (all.length && names.length >= all.length && all.every(n => names.includes(n))) return '全体业委会成员'
  return names.join('、')
})

function fmtTime(v) { return String(v || '').slice(0, 5) }
function isImage(ev) {
  const t = (ev.fileType || '').toLowerCase(), u = (ev.fileUrl || '').toLowerCase()
  return /(jpg|jpeg|png|gif|webp)/.test(t) || /\.(jpg|jpeg|png|gif|webp)(\?|$)/.test(u)
}
function openFile(url) { if (url) window.open(url, '_blank') }

function findAndApply(list) {
  const found = (list || []).find((i) => i.id === id)
  if (!found) return false
  if (found.stage === 'ended') { redirectTo('/learning-detail?id=' + id); return true }
  evidences.value = found.evidences || []
  item.value = found
  return true
}
async function load() {
  try {
    const internal = await api.learningList('internal', null)
    if (findAndApply(internal)) return
    const training = await api.learningList('training', null)
    if (!findAndApply(training)) toast({ title: '学习记录不存在', icon: 'none' })
  } catch (e) { toast({ title: '加载失败', icon: 'none' }) }
}
async function loadMembers() {
  try { const list = await api.committeeMembers(); memberNames.value = (list || []).map(m => m && m.name).filter(Boolean) }
  catch (e) { memberNames.value = [] }
}

async function confirm() {
  const res = await showModal({ title: '确认留档', content: '确认本次' + catNoun.value + '信息无误并归档？归档后不可再修改。' })
  if (!res || !res.confirm) return
  if (saving.value) return
  saving.value = true
  try {
    // 确认＝计划参加人员即到场（简化流程，不逐个勾选）+ 归档
    const names = plannedNames.value
    if (names.length) await api.learningSetAttendance(id, names)
    await api.learningFinish(id)
    toast({ title: '已归档留存', icon: 'success' })
    redirectTo('/learning-detail?id=' + id)
  } catch (e) {
    toast({ title: e.message, icon: 'none' })
    saving.value = false
  }
}

onMounted(() => {
  if (!perm.can('learning.create')) { toast({ title: '仅主任/副主任可登记', icon: 'none' }); redirectTo('/learning-detail?id=' + id); return }
  load()
  loadMembers()
})
</script>

<style scoped>
.learning-register { min-height: 100vh; background: #f4f5f7; }
.reg-body { padding: 24rpx 24rpx calc(160rpx + env(safe-area-inset-bottom)); }
.reg-tip { margin: 0 4rpx 18rpx; font-size: 27rpx; color: #8A6B36; line-height: 1.5; }

.card { background: #fff; border-radius: 24rpx; padding: 14rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.card-title { font-size: 30rpx; font-weight: 700; color: #1f2329; padding: 10rpx 0; }
.row { display: flex; align-items: center; gap: 20rpx; padding: 14rpx 0; border-bottom: 2rpx solid #f5f5f5; }
.row:last-child { border-bottom: none; }
.row.col { align-items: flex-start; flex-direction: column; gap: 8rpx; }
.k { font-size: 28rpx; color: #666; width: 150rpx; flex-shrink: 0; line-height: 1.5; }
.v { font-size: 30rpx; color: #1f2329; flex: 1; line-height: 1.55; word-break: break-all; }
.v.strong { font-weight: 700; }
.row.col .v { width: 100%; }

.ev-list { display: flex; flex-direction: column; gap: 8rpx; }
.ev-item { display: flex; align-items: center; gap: 16rpx; padding: 14rpx 16rpx; background: #fafbfc; border-radius: 12rpx; font-size: 28rpx; color: #444; }
.ev-name { flex: 1; min-width: 0; word-break: break-all; }
.ev-link { color: #2980B9; }
.ev-thumb { width: 108rpx; height: 108rpx; object-fit: cover; border-radius: 8rpx; border: 1rpx solid #eee; flex-shrink: 0; }
.ev-empty { display: block; font-size: 26rpx; color: #8A94A6; padding: 8rpx 0 14rpx; line-height: 1.5; }

.reg-foot { position: fixed; left: 0; right: 0; bottom: 0; z-index: 30; padding: 18rpx 28rpx calc(20rpx + env(safe-area-inset-bottom)); background: #fff; box-shadow: 0 -6rpx 20rpx rgba(0,0,0,.06); }
.btn-finish { width: 100%; height: 92rpx; border: 0; border-radius: 46rpx; background: var(--c-primary-dark); color: #fff; font-size: 34rpx; font-weight: 700; }
.btn-finish:disabled { opacity: .6; }

.empty-state { padding: 100rpx 0; text-align: center; color: #8A94A6; font-size: 30rpx; }
</style>
