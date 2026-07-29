<template>
  <div class="page learning-register">
    <PageNav :title="'登记' + catNoun + '结果'" />

    <main v-if="item" class="reg-body">
      <div class="reg-head">
        <span class="rh-title">{{ item.title }}</span>
        <span class="rh-sub">{{ item.date }} {{ fmtTime(item.time) }} · {{ item.location || '未填写地点' }}</span>
      </div>

      <!-- 实际参加人员：勾选到场 -->
      <section class="card" v-if="signList.length">
        <div class="card-head">
          <span class="card-title">实际参加人员</span>
          <span class="card-stat">{{ selectedAttendance.length }}/{{ signList.length }} 人</span>
        </div>
        <span class="card-hint">{{ catNoun }}结束后，勾选实际到场人员</span>
        <div class="att-list">
          <div v-for="s in signList" :key="s.name" class="att-row" :class="{ on: selectedAttendance.includes(s.name) }" @click="toggleAttendance(s.name)">
            <span class="att-name">{{ s.name }}</span>
            <span class="att-check">{{ selectedAttendance.includes(s.name) ? '✓ 已参加' : '未参加' }}</span>
          </div>
        </div>
        <button class="btn-line" type="button" @click="saveAttendance">保存参加情况</button>
      </section>

      <!-- 学习材料 -->
      <section class="card">
        <div class="card-head"><span class="card-title">{{ catNoun }}材料（{{ evidences.length }}）</span></div>
        <div v-if="evidences.length" class="ev-list">
          <div v-for="ev in evidences" :key="ev.id" class="ev-item">
            <img v-if="isImage(ev)" class="ev-thumb" :src="ev.fileUrl" @click="openFile(ev.fileUrl)" />
            <span v-else class="ev-name ev-link" @click="openFile(ev.fileUrl)">📄 {{ ev.fileName }}</span>
            <span v-if="isImage(ev)" class="ev-name">{{ ev.fileName }}</span>
            <span class="ev-del" @click="removeEvidence(ev.id)">×</span>
          </div>
        </div>
        <span v-else class="ev-empty">可上传{{ catNoun }}记录、课件、照片等</span>
        <button class="ev-add-btn" type="button" @click="addEvidence">＋ 上传材料</button>
      </section>

      <div class="reg-foot">
        <button class="btn-finish" type="button" :disabled="saving" @click="finish">完成留档</button>
      </div>
    </main>

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
import { pickAndUpload } from '@/utils/upload'

const route = useRoute()
const id = Number(route.query.id)
const item = ref(null)
const signList = ref([])
const evidences = ref([])
const selectedAttendance = ref([])
const saving = ref(false)
let advancing = false

const catNoun = computed(() => (item.value && item.value.category === 'external') ? '培训' : '学习')

function fmtTime(v) { return String(v || '').slice(0, 5) }
function isImage(ev) {
  const t = (ev.fileType || '').toLowerCase(), u = (ev.fileUrl || '').toLowerCase()
  return /(jpg|jpeg|png|gif|webp)/.test(t) || /\.(jpg|jpeg|png|gif|webp)(\?|$)/.test(u)
}
function openFile(url) { if (url) window.open(url, '_blank') }

function findAndApply(list) {
  const found = (list || []).find((i) => i.id === id)
  if (!found) return false
  apply(found)
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
async function apply(found) {
  // 已结束直接回详情查看；准备阶段先推进到进行中（相当于"开始登记"）再展示
  if (found.stage === 'ended') { redirectTo('/learning-detail?id=' + id); return }
  if (found.stage === 'preparing' && !advancing) {
    advancing = true
    try { await api.learningStart(id) } catch (e) { /* 已推进或并发，忽略 */ }
    load(); return
  }
  const sl = []
  if (found.signIns) Object.keys(found.signIns).forEach((n) => sl.push({ name: n, signed: found.signIns[n] }))
  signList.value = sl
  selectedAttendance.value = sl.filter((s) => s.signed).map((s) => s.name)
  evidences.value = found.evidences || []
  item.value = found
}

function toggleAttendance(name) {
  const i = selectedAttendance.value.indexOf(name)
  if (i >= 0) selectedAttendance.value.splice(i, 1)
  else selectedAttendance.value.push(name)
}
async function saveAttendance() {
  try {
    await api.learningSetAttendance(id, selectedAttendance.value)
    toast({ title: '参加情况已保存', icon: 'success' })
    load()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}
async function addEvidence() {
  try {
    const r = await pickAndUpload('image/*,.pdf,.doc,.docx')
    if (!r) return
    await api.learningAddEvidence(id, r.fileName, r.fileType, r.url)
    toast({ title: '已上传', icon: 'success' })
    load()
  } catch (e) { toast({ title: e.message, icon: 'none' }) }
}
async function removeEvidence(evId) {
  try { await api.learningRemoveEvidence(id, evId); load() }
  catch (e) { toast({ title: e.message, icon: 'none' }) }
}
async function finish() {
  const res = await showModal({ title: '完成留档', content: '确认参加情况与材料已登记完整？完成后本次' + catNoun.value + '归档。' })
  if (!res || !res.confirm) return
  if (saving.value) return
  saving.value = true
  try {
    await api.learningFinish(id)
    toast({ title: '已完成留档', icon: 'success' })
    redirectTo('/learning-detail?id=' + id)
  } catch (e) {
    toast({ title: e.message, icon: 'none' })
    saving.value = false
  }
}

onMounted(() => {
  if (!perm.can('learning.create')) { toast({ title: '仅主任/副主任可登记', icon: 'none' }); redirectTo('/learning-detail?id=' + id); return }
  load()
})
</script>

<style scoped>
.learning-register { min-height: 100vh; background: #f4f5f7; }
.reg-body { padding: 24rpx 24rpx calc(160rpx + env(safe-area-inset-bottom)); }
.reg-head { background: #fff; border-radius: 24rpx; padding: 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.rh-title { display: block; font-size: 36rpx; font-weight: 700; color: #1f2329; line-height: 1.4; }
.rh-sub { display: block; margin-top: 10rpx; font-size: 26rpx; color: #808C99; }

.card { background: #fff; border-radius: 24rpx; padding: 24rpx 26rpx; margin-bottom: 20rpx; box-shadow: 0 8rpx 28rpx rgba(0,0,0,0.06); }
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12rpx; }
.card-title { font-size: 30rpx; font-weight: 700; color: #1f2329; }
.card-stat { font-size: 28rpx; color: #C77800; font-weight: 600; }
.card-hint { display: block; margin: -4rpx 0 14rpx; font-size: 25rpx; color: #8A94A6; }

.att-list { display: flex; flex-direction: column; gap: 6rpx; }
.att-row { display: flex; align-items: center; justify-content: space-between; padding: 16rpx 18rpx; border-radius: 12rpx; background: #F6F7F9; }
.att-row.on { background: #EAF5EE; }
.att-name { font-size: 30rpx; color: #1f2329; }
.att-check { font-size: 26rpx; color: #778292; }
.att-row.on .att-check { color: #2E7D50; font-weight: 600; }
.btn-line { width: 100%; height: 84rpx; margin-top: 16rpx; border-radius: 42rpx; background: #fff; color: #C77800; border: 2rpx solid #FFA800; font-size: 30rpx; font-weight: 600; }

.ev-list { display: flex; flex-direction: column; gap: 8rpx; }
.ev-item { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 16rpx 18rpx; background: #fafbfc; border-radius: 12rpx; font-size: 28rpx; color: #444; }
.ev-name { flex: 1; min-width: 0; word-break: break-all; }
.ev-link { color: #2980B9; }
.ev-thumb { width: 120rpx; height: 120rpx; object-fit: cover; border-radius: 8rpx; border: 1rpx solid #eee; flex-shrink: 0; }
.ev-del { font-size: 40rpx; color: #666; margin-left: 16rpx; flex-shrink: 0; padding: 0 8rpx; }
.ev-empty { display: block; font-size: 28rpx; color: #777; text-align: center; padding: 16rpx 0; }
.ev-add-btn { display: block; width: 66%; margin: 22rpx auto 4rpx; height: 88rpx; border: 2rpx solid var(--c-primary); border-radius: 20rpx; background: var(--c-primary-soft); color: var(--c-primary-dark); font-size: 30rpx; font-weight: 700; }

.reg-foot { position: fixed; left: 0; right: 0; bottom: 0; z-index: 30; padding: 18rpx 28rpx calc(20rpx + env(safe-area-inset-bottom)); background: #fff; box-shadow: 0 -6rpx 20rpx rgba(0,0,0,.06); }
.btn-finish { width: 100%; height: 92rpx; border: 0; border-radius: 46rpx; background: var(--c-primary-dark); color: #fff; font-size: 34rpx; font-weight: 700; }
.btn-finish:disabled { opacity: .6; }

.empty-state { padding: 100rpx 0; text-align: center; color: #8A94A6; font-size: 30rpx; }
</style>
