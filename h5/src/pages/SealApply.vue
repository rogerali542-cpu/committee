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
          <div class="seal-select-wrap">
            <select class="seal-select" v-model="form.sealType">
              <option v-for="s in seals" :key="s.type" :value="s.type">{{ s.label }}</option>
            </select>
            <span class="seal-select-arr">⌄</span>
          </div>
        </div>

        <div class="form-group">
          <span class="form-label">用印事由 / 用途 *</span>
          <!-- 0728 用户定：不再单独填「关联文件」，文件名直接写进事由里 -->
          <span class="form-hint">请写清楚为哪份文件用印、用于什么事项，文件名称建议写全</span>
          <textarea class="form-textarea" v-model="form.purpose"></textarea>
        </div>

        <div class="form-group">
          <span class="form-label">上传附件（选填）</span>
          <span class="form-hint">盖章文件或签字登记表的照片 / PDF，供保管人核对并留档台账</span>
          <div v-if="atts.length" class="att-list">
            <div v-for="(a, i) in atts" :key="a.url" class="att-item">
              <img v-if="isImg(a)" class="att-thumb" :src="a.url" alt="" @click="viewAtt(a)" />
              <span v-else class="att-fileico" @click="viewAtt(a)">📄</span>
              <span class="att-name">{{ a.name || '附件' }}</span>
              <button type="button" class="att-del" aria-label="移除附件" @click="removeAtt(i)">×</button>
            </div>
          </div>
          <button type="button" class="att-add" :disabled="uploading" @click="addAtt">{{ uploading ? '上传中…' : '＋ 添加附件' }}</button>
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
import { pickFile, uploadAttachment } from '@/utils/upload'

// 印章固定三枚（依据《印章管理制度》）：本地兜底保证选择栏必齐，再以后端返回为准。
// 0728 用户定：下拉按使用频率排——业委会章、财务章常用在前，业主大会章频率低放最后，默认选业委会章。
const SEAL_ORDER = ['committee', 'finance', 'general_assembly']
function sortSeals(list) {
  const rank = (t) => { const i = SEAL_ORDER.indexOf(t); return i < 0 ? SEAL_ORDER.length : i }
  return [...list].sort((a, b) => rank(a.type) - rank(b.type))
}
const DEFAULT_SEALS = [
  { type: 'committee', label: '业委会章' },
  { type: 'finance', label: '财务专用章' },
  { type: 'general_assembly', label: '业主大会章' }
]
const seals = ref(DEFAULT_SEALS.slice())
const form = reactive({ sealType: DEFAULT_SEALS[0].type, purpose: '' })
const saving = ref(false)

// 附件（盖章文件/签字登记表照片、PDF）：选一件传一件，提交时随申请入库
const atts = ref([])
const uploading = ref(false)
async function addAtt() {
  if (uploading.value) return
  const f = await pickFile('image/*,application/pdf')
  if (!f) return
  uploading.value = true
  try {
    const r = await uploadAttachment(f)
    atts.value.push({ url: r.url, name: r.fileName || f.name || '附件', type: r.fileType || f.type || '', size: r.fileSize || f.size || 0 })
  } catch (e) {
    toast({ title: (e && e.message) || '上传失败，请重试', icon: 'none' })
  } finally { uploading.value = false }
}
function removeAtt(i) { atts.value.splice(i, 1) }
function isImg(a) {
  const s = ((a.type || '') + ' ' + (a.url || '')).toLowerCase()
  return s.indexOf('image') >= 0 || /\.(jpg|jpeg|png|gif|webp)(\?|$)/.test(s)
}
function viewAtt(a) { if (a.url) window.open(a.url, '_blank') }

// 导航新规(与学习/接待登记页一致)：返回=历史上一页（本页只从印章台账进入）
function back() { navigateBack() }

onMounted(async () => {
  try {
    const s = await api.sealList()
    if (Array.isArray(s) && s.length) seals.value = sortSeals(s)   // 后端返回则以其为准（重排常用在前），否则保留三枚兜底
    if (!seals.value.some((x) => x.type === form.sealType)) form.sealType = seals.value[0].type
  } catch (e) { /* 拉取失败保留本地兜底，选择栏仍完整 */ }
})

async function submit() {
  if (!form.sealType) { toast({ title: '请选择印章', icon: 'none' }); return }
  if (!String(form.purpose).trim()) { toast({ title: '请填写用印用途', icon: 'none' }); return }
  if (uploading.value) { toast({ title: '附件上传中，请稍候', icon: 'none' }); return }
  if (saving.value) return
  saving.value = true
  try {
    await api.sealApply({
      sealType: form.sealType,
      purpose: form.purpose.trim(),
      attachments: atts.value
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
.form-hint { display: block; margin: -6rpx 0 14rpx; font-size: 25rpx; line-height: 1.5; color: #98A2AD; }
.form-input, .form-textarea {
  width: 100%; box-sizing: border-box; border: 2rpx solid #DFE5E9; border-radius: 16rpx;
  background: #FCFDFD; color: #202833; font-size: 30rpx; padding: 0 18rpx; height: 84rpx; outline: none;
}
.form-textarea { padding: 16rpx 18rpx; min-height: 180rpx; height: 180rpx; resize: none; line-height: 1.5; }
/* 选印章下拉：原生 select（系统选择器，无自制弹层），外观与其余输入框同族，右侧自绘箭头 */
.seal-select-wrap { position: relative; }
.seal-select {
  width: 100%; box-sizing: border-box; appearance: none; -webkit-appearance: none;
  border: 2rpx solid #DFE5E9; border-radius: 16rpx; background: #FCFDFD; color: #202833;
  font-size: 30rpx; font-weight: 600; padding: 0 64rpx 0 18rpx; height: 84rpx; outline: none;
}
.seal-select:focus { border-color: #B0772E; }
.seal-select-arr {
  position: absolute; right: 22rpx; top: 50%; transform: translateY(-62%);
  color: #8A94A0; font-size: 32rpx; line-height: 1; pointer-events: none;
}
/* 附件上传：已传列表（缩略图/文件图标 + 名称 + 移除）+ 虚线添加按钮 */
.att-list { display: flex; flex-direction: column; gap: 14rpx; margin-bottom: 16rpx; }
.att-item {
  display: flex; align-items: center; gap: 16rpx; padding: 12rpx 16rpx;
  border: 2rpx solid #EAE2D4; border-radius: 14rpx; background: #FCFAF6;
}
.att-thumb { width: 88rpx; height: 88rpx; border-radius: 10rpx; object-fit: cover; flex-shrink: 0; background: #EEE; }
.att-fileico {
  width: 88rpx; height: 88rpx; border-radius: 10rpx; background: #F3EDE2; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; font-size: 40rpx;
}
.att-name { flex: 1; min-width: 0; font-size: 27rpx; color: #4A5560; word-break: break-all; }
.att-del {
  flex-shrink: 0; width: 56rpx; height: 56rpx; border: 0; border-radius: 50%;
  background: #F1F3F5; color: #7A8590; font-size: 34rpx; line-height: 1;
}
.att-del:active { background: #E4E7EA; color: #C0392B; }
.att-add {
  width: 100%; height: 80rpx; border: 2rpx dashed #CFBD9E; border-radius: 14rpx;
  background: #FDFBF7; color: #8A6B36; font-size: 28rpx; font-weight: 600;
}
.att-add:active { background: #F5EFE3; }
.att-add:disabled { opacity: .6; }
.create-actions { display: flex; gap: 20rpx; margin-top: 32rpx; }
.btn-ghost { flex: 1; height: 92rpx; border: 2rpx solid #C9D0D6; border-radius: 20rpx; background: #fff; color: #5B6570; font-size: 32rpx; }
.btn-primary { flex: 2; height: 92rpx; border: 0; border-radius: 20rpx; background: #B0772E; color: #fff; font-size: 32rpx; font-weight: 700; }
.btn-primary:disabled { opacity: .6; }
</style>
