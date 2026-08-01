<template>
  <div class="page learning-initiate">
    <!-- 返回键用 PageNav 默认款（满高、居中）；back() 供取消按钮用 -->
    <PageNav title="发起内部学习" />

    <main class="create-body">
      <p v-if="draftRestored" class="draft-note">已恢复上次未完成的学习通知</p>
      <section class="form-card">
        <div class="form-group">
          <span class="form-label">学习主题 *</span>
          <input class="form-input large" v-model="form.title" />
        </div>

        <div class="form-group">
          <span class="form-label">日期与时间 *</span>
          <!-- 与发起会议同款日历/时间选择器（中文、点选即定） -->
          <PlanDateTimeField v-model:date="form.date" v-model:time="form.time" :min-today="true" />
        </div>

        <div class="form-group">
          <span class="form-label">地点 *</span>
          <!-- 与发起会议一致：常用地点点选，也可选「其他地点」手动输入 -->
          <div v-if="locationPreset === '__other__'" class="loc-other">
            <input class="form-input" v-model="form.location" placeholder="请输入学习地点" />
            <span class="loc-switch" @click="openLocPicker">选择常用地点</span>
          </div>
          <div v-else class="form-input loc-pick" @click="openLocPicker">
            <span :class="{ ph: !form.location }">{{ form.location || '选择地点' }}</span>
            <span class="loc-arrow">›</span>
          </div>
        </div>

        <div class="form-group">
          <span class="form-label">学习内容 *</span>
          <textarea class="form-textarea" v-model="form.description"></textarea>
        </div>
        <!-- 计划参加人员改到通知页选择（0728 用户定：与发起会议一致，通知页选通知对象） -->
      </section>

      <button v-if="hasDraft" class="clear-draft" type="button" @click="clearDraftAndReset">清空重填</button>

      <div class="create-actions">
        <button class="btn-ghost" type="button" @click="back">取消</button>
        <button class="btn-primary" type="button" :disabled="saving" @click="submit">{{ saving ? '发起中…' : '发起并去通知' }}</button>
      </div>
    </main>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import PlanDateTimeField from '@/components/PlanDateTimeField.vue'
import { toast, showActionSheet } from '@/utils/ui'
import { navigateBack } from '@/utils/navigate'

// 日期默认今天，且不早于今天（发起=面向未来的内部学习）
function todayStr() {
  const d = new Date()
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}
function nowHm() {
  const d = new Date()
  return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0')
}
const DEFAULT_FORM = () => ({ title: '', date: todayStr(), time: '14:00', location: '社区活动室', description: '' })
const DRAFT_KEY = 'learning_initiate_draft'
const form = reactive(DEFAULT_FORM())
const saving = ref(false)
const draftRestored = ref(false)
const hasDraft = ref(false)
const draftReady = ref(false)
let draftTimer = null

// 地点：常用地点点选 + 其他地点手动输入（与发起会议一致，去掉地图选点）
const commonLocations = ['社区活动室', '社区会议室']
const locationPreset = ref('社区活动室')
async function openLocPicker() {
  const res = await showActionSheet({ itemList: [...commonLocations, '其他地点（手动填写）'] })
  if (!res || res.tapIndex == null || res.tapIndex < 0) return
  const i = res.tapIndex
  if (i < commonLocations.length) { locationPreset.value = commonLocations[i]; form.location = commonLocations[i] }
  else { locationPreset.value = '__other__'; form.location = '' }
}

function draftIsMeaningful() {
  return !!(String(form.title || '').trim() || String(form.description || '').trim())
}

function removeDraft() {
  clearTimeout(draftTimer)
  localStorage.removeItem(DRAFT_KEY)
  hasDraft.value = false
  draftRestored.value = false
}

function saveDraft() {
  if (!draftReady.value || saving.value) return
  if (!draftIsMeaningful()) {
    removeDraft()
    return
  }
  localStorage.setItem(DRAFT_KEY, JSON.stringify({
    form: { ...form },
    locationPreset: locationPreset.value,
    savedAt: Date.now()
  }))
  hasDraft.value = true
}

function scheduleDraftSave() {
  if (!draftReady.value) return
  clearTimeout(draftTimer)
  draftTimer = setTimeout(saveDraft, 350)
}

function restoreDraft() {
  try {
    const draft = JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null')
    if (!draft || !draft.form) return false
    Object.assign(form, DEFAULT_FORM(), draft.form)
    locationPreset.value = draft.locationPreset || (commonLocations.includes(form.location) ? form.location : '__other__')
    draftRestored.value = true
    hasDraft.value = true
    return true
  } catch (_) {
    localStorage.removeItem(DRAFT_KEY)
    return false
  }
}

function clearDraftAndReset() {
  draftReady.value = false
  removeDraft()
  Object.assign(form, DEFAULT_FORM())
  locationPreset.value = '社区活动室'
  draftReady.value = true
  toast({ title: '已清空，可重新填写', icon: 'none' })
}

function back() {
  saveDraft()
  navigateBack()
}

async function submit() {
  const missing = []
  if (!String(form.title).trim()) missing.push('主题')
  if (!form.date) missing.push('日期')
  if (!String(form.location).trim()) missing.push('地点')
  if (!String(form.description).trim()) missing.push('学习内容')
  if (missing.length) { toast({ title: '请补全：' + missing.join('、'), icon: 'none' }); return }
  if (form.date < todayStr()) { toast({ title: '日期不能早于今天', icon: 'none' }); return }
  // 今天的学习：时间不能早于当前（默认 14:00 未改且已过点时兜底拦截）
  if (form.date === todayStr() && form.time && form.time < nowHm()) {
    toast({ title: '时间不能早于当前时间', icon: 'none' }); return
  }
  if (saving.value) return
  saving.value = true
  try {
    // 固定内部学习：type=internal + category=internal，阶段由后端置 preparing
    const result = await api.learningCreate({
      title: form.title.trim(),
      date: form.date,
      time: form.time || '14:00',
      location: form.location.trim(),
      description: form.description.trim(),
      type: 'internal',
      category: 'internal'
    })
    draftReady.value = false
    removeDraft()
    // replace：不把表单页留在历史，通知页返回直接回学习列表
    window.location.replace('/learning-notify?id=' + result.id)
  } catch (e) {
    toast({ title: (e && e.message) || '发起失败', icon: 'none' })
    saving.value = false
  }
}

onMounted(() => {
  restoreDraft()
  draftReady.value = true
})

watch([form, locationPreset], scheduleDraftSave, { deep: true })

onBeforeUnmount(() => {
  clearTimeout(draftTimer)
  saveDraft()
})
</script>

<style scoped>
:deep(.page-nav) { background: #2a6b73; }  /* 学习模块页头（规范三色制） */
.learning-initiate { min-height: 100vh; background: #f5f5f7; }
.create-body { padding: 24rpx 28rpx calc(40rpx + env(safe-area-inset-bottom)); }
.draft-note { margin: -4rpx 4rpx 18rpx; color: #7A8594; font-size: 25rpx; line-height: 1.5; }
.form-card { background: #fff; border-radius: 24rpx; padding: 30rpx 28rpx 10rpx; box-shadow: 0 6rpx 18rpx rgba(31, 45, 61, .06); }
.form-group { margin-bottom: 28rpx; }
.form-label { display: block; margin-bottom: 12rpx; font-size: 28rpx; color: #4a5560; font-weight: 600; }
.form-input, .form-textarea { width: 100%; box-sizing: border-box; border: 2rpx solid #DFE5E9; border-radius: 16rpx; background: #FCFDFD; color: #202833; font-size: 30rpx; padding: 0 18rpx; height: 84rpx; outline: none; }
.form-input.large { font-size: 32rpx; }
.form-textarea { padding: 16rpx 18rpx; min-height: 160rpx; height: 160rpx; resize: none; line-height: 1.5; }

/* 地点：点选行（值+箭头）/ 其他地点手动输入 */
.loc-pick { display: flex; align-items: center; justify-content: space-between; }
.loc-pick .ph { color: #9AA2AB; }
.loc-arrow { flex-shrink: 0; color: #c4c8cd; font-size: 30rpx; line-height: 1; }
.loc-other { display: flex; flex-direction: column; gap: 12rpx; }
.loc-switch { align-self: flex-start; font-size: 25rpx; color: var(--c-primary-dark); }

.clear-draft { display: block; width: 100%; min-height: 82rpx; margin-top: 16rpx; padding: 0 12rpx; border: 0; border-bottom: 2rpx solid #DDE3E8; background: transparent; color: #8A5B54; font-size: 28rpx; text-align: left; }

.create-actions { display: flex; gap: 20rpx; margin-top: 32rpx; }
.btn-ghost { flex: 1; height: 92rpx; border: 2rpx solid #C9D0D6; border-radius: 20rpx; background: #fff; color: #5B6570; font-size: 32rpx; }
.btn-primary { flex: 2; height: 92rpx; border: 0; border-radius: 20rpx; background: var(--c-primary); color: #fff; font-size: 32rpx; font-weight: 700; }
.btn-primary:disabled { opacity: .6; }
</style>
