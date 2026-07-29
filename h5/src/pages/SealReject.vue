<template>
  <div class="page seal-reject">
    <!-- 返回键用 PageNav 默认款（满高、居中），与全站一致；back() 仍给「取消」按钮用 -->
    <PageNav title="驳回用印申请" />

    <main class="create-body">
      <!-- 确认正在驳回哪条申请（信息由台账卡片带入，避免误驳） -->
      <section v-if="sealLabel || purpose" class="target-card">
        <span class="tc-label">正在驳回</span>
        <span class="tc-seal">{{ sealLabel || '用印申请' }}</span>
        <span v-if="purpose" class="tc-purpose">{{ purpose }}</span>
      </section>

      <section class="form-card">
        <div class="form-group">
          <span class="form-label">驳回理由（选填）</span>
          <textarea class="form-textarea" v-model="reason" placeholder="说明驳回原因，便于申请人修改后再申请"></textarea>
        </div>
      </section>

      <div class="create-actions">
        <button class="btn-ghost" type="button" @click="back">取消</button>
        <button class="btn-danger" type="button" :disabled="saving" @click="submit">{{ saving ? '提交中…' : '确认驳回' }}</button>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api'
import PageNav from '@/components/PageNav.vue'
import { toast } from '@/utils/ui'
import { navigateBack } from '@/utils/navigate'

const route = useRoute()
const id = route.query.id
const sealLabel = ref(route.query.seal || '')
const purpose = ref(route.query.purpose || '')
const reason = ref('')
const saving = ref(false)

function back() { navigateBack() }

async function submit() {
  if (!id) { toast({ title: '缺少申请信息', icon: 'none' }); return }
  if (saving.value) return
  saving.value = true
  try {
    await api.sealReject(id, (reason.value || '').trim())
    toast({ title: '已驳回', icon: 'none' })
    // replace:返回直接回印章台账（台账 onMounted 会重新拉取，状态即时更新）
    window.location.replace('/seal')
  } catch (e) {
    toast({ title: (e && e.message) || '驳回失败', icon: 'none' })
    saving.value = false
  }
}
</script>

<style scoped>
.seal-reject { min-height: 100vh; background: #f5f5f7; }
/* 顶栏统一为业委会首页同款深青灰（0729 用户定：印章模块整体走蓝/青灰） */
:deep(.page-nav) { background: #43546F; }
.create-body { padding: 24rpx 28rpx calc(40rpx + env(safe-area-inset-bottom)); }
.target-card {
  display: flex; flex-direction: column; gap: 8rpx;
  background: #fff; border-radius: 20rpx; padding: 24rpx 26rpx; margin-bottom: 20rpx;
  box-shadow: 0 6rpx 18rpx rgba(31, 45, 61, .06);
}
.tc-label { font-size: 24rpx; color: var(--c-text-weak); }
.tc-seal { font-size: 32rpx; font-weight: 750; color: var(--c-text-strong); }
.tc-purpose { font-size: 28rpx; color: var(--c-text-mid); line-height: 1.45; }
.form-card { background: #fff; border-radius: 24rpx; padding: 30rpx 28rpx 10rpx; box-shadow: 0 6rpx 18rpx rgba(31, 45, 61, .06); }
.form-group { margin-bottom: 28rpx; }
.form-label { display: block; margin-bottom: 14rpx; font-size: 28rpx; color: #4a5560; font-weight: 600; }
.form-textarea {
  width: 100%; box-sizing: border-box; border: 2rpx solid #DFE5E9; border-radius: 16rpx;
  background: #FCFDFD; color: #202833; font-size: 30rpx; padding: 16rpx 18rpx;
  min-height: 180rpx; height: 180rpx; resize: none; line-height: 1.5; outline: none;
}
.create-actions { display: flex; gap: 20rpx; margin-top: 32rpx; }
.btn-ghost { flex: 1; height: 92rpx; border: 2rpx solid #C9D0D6; border-radius: 20rpx; background: #fff; color: #5B6570; font-size: 32rpx; }
.btn-danger { flex: 2; height: 92rpx; border: 0; border-radius: 20rpx; background: var(--c-danger); color: #fff; font-size: 32rpx; font-weight: 700; }
.btn-danger:disabled { opacity: .6; }
</style>
