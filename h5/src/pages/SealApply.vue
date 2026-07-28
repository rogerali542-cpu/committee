<template>
  <div class="page seal-apply">
    <PageNav title="申请用印">
      <template #left>
        <button class="back-btn" type="button" aria-label="返回印章管理" @click="back">‹</button>
      </template>
    </PageNav>

    <main class="create-body">
      <section class="form-card">
        <div class="form-group">
          <span class="form-label">选择印章 *</span>
          <div class="seal-pick-row">
            <span
              v-for="s in seals"
              :key="s.type"
              class="seal-pick"
              :class="{ on: form.sealType === s.type }"
              @click="form.sealType = s.type"
            >{{ s.label }}</span>
          </div>
        </div>

        <div class="form-group">
          <span class="form-label">用印事由 / 用途 *</span>
          <textarea class="form-textarea" v-model="form.purpose"></textarea>
        </div>

        <div class="form-group">
          <span class="form-label">关联文件（选填）</span>
          <input class="form-input" v-model="form.documentName" />
        </div>
      </section>

      <div class="create-actions">
        <button class="btn-ghost" type="button" @click="back">取消</button>
        <button class="btn-primary" type="button" :disabled="saving" @click="submit">{{ saving ? '提交中…' : '提交申请' }}</button>
      </div>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import { toast } from '@/utils/ui'
import { navigateBack } from '@/utils/navigate'

// 印章固定三枚（依据《印章管理制度》）：本地兜底保证选择栏必齐，再以后端返回为准
const DEFAULT_SEALS = [
  { type: 'general_assembly', label: '业主大会章' },
  { type: 'committee', label: '业委会章' },
  { type: 'finance', label: '财务专用章' }
]
const seals = ref(DEFAULT_SEALS.slice())
const form = reactive({ sealType: DEFAULT_SEALS[0].type, purpose: '', documentName: '' })
const saving = ref(false)

// 导航新规(与学习/接待登记页一致)：返回=历史上一页（本页只从印章台账进入）
function back() { navigateBack() }

onMounted(async () => {
  try {
    const s = await api.sealList()
    if (Array.isArray(s) && s.length) seals.value = s   // 后端返回则以其为准，否则保留三枚兜底
    if (!form.sealType && seals.value.length) form.sealType = seals.value[0].type
  } catch (e) { /* 拉取失败保留本地兜底，选择栏仍完整 */ }
})

async function submit() {
  if (!form.sealType) { toast({ title: '请选择印章', icon: 'none' }); return }
  if (!String(form.purpose).trim()) { toast({ title: '请填写用印用途', icon: 'none' }); return }
  if (saving.value) return
  saving.value = true
  try {
    await api.sealApply({
      sealType: form.sealType,
      purpose: form.purpose.trim(),
      documentName: (form.documentName || '').trim()
    })
    toast({ title: '已提交用印申请', icon: 'success' })
    // replace:不把已提交的表单页留在历史里，返回直接回印章台账（台账 onMounted 会重新拉取）
    window.location.replace('/seal')
  } catch (e) {
    toast({ title: (e && e.message) || '提交失败', icon: 'none' })
    saving.value = false
  }
}
</script>

<style scoped>
.seal-apply { min-height: 100vh; background: #f5f5f7; }
.back-btn { width: 64rpx; height: 64rpx; border: 0; background: transparent; color: #fff; font-size: 54rpx; }
.create-body { padding: 24rpx 28rpx calc(40rpx + env(safe-area-inset-bottom)); }
.form-card { background: #fff; border-radius: 24rpx; padding: 30rpx 28rpx 10rpx; box-shadow: 0 6rpx 18rpx rgba(31, 45, 61, .06); }
.form-group { margin-bottom: 28rpx; }
.form-label { display: block; margin-bottom: 14rpx; font-size: 28rpx; color: #4a5560; font-weight: 600; }
.form-input, .form-textarea {
  width: 100%; box-sizing: border-box; border: 2rpx solid #DFE5E9; border-radius: 16rpx;
  background: #FCFDFD; color: #202833; font-size: 30rpx; padding: 0 18rpx; height: 84rpx; outline: none;
}
.form-textarea { padding: 16rpx 18rpx; min-height: 180rpx; height: 180rpx; resize: none; line-height: 1.5; }
.seal-pick-row { display: flex; flex-wrap: wrap; gap: 16rpx; }
.seal-pick {
  min-height: 60rpx; display: inline-flex; align-items: center; padding: 0 26rpx; border-radius: 999rpx;
  border: 2rpx solid #DCE1E6; background: #fff; color: #4E6076; font-size: 28rpx; font-weight: 600;
}
.seal-pick.on { border-color: #B0772E; background: #FBF4EB; color: #7E571C; }
.create-actions { display: flex; gap: 20rpx; margin-top: 32rpx; }
.btn-ghost { flex: 1; height: 92rpx; border: 2rpx solid #C9D0D6; border-radius: 20rpx; background: #fff; color: #5B6570; font-size: 32rpx; }
.btn-primary { flex: 2; height: 92rpx; border: 0; border-radius: 20rpx; background: #B0772E; color: #fff; font-size: 32rpx; font-weight: 700; }
.btn-primary:disabled { opacity: .6; }
</style>
